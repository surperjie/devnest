<#
.SYNOPSIS
    把 docs/wiki/ 下的页面同步发布到本仓库的 GitHub Wiki。

.DESCRIPTION
    为什么要有这个脚本:
      Wiki 内容一旦只在 GitHub 网页上编辑,它就脱离了代码评审 —— 改了什么、谁改的、
      为什么改都没有记录,下次有人再编辑还会互相覆盖。

      所以权威副本放在仓库里的 docs/wiki/,Wiki 只是它的【发布产物】:
        - 改 Wiki = 改 docs/wiki/*.md 并提 PR(走正常评审)
        - 发布   = 跑一次本脚本
      这样 Wiki 的每次变动在仓库里都可见、可回溯、可回滚。

    同步语义是【镜像 .md 页面】:
      - docs/wiki/ 里每个 .md 都会覆盖到 Wiki 根目录
        (Home / _Sidebar / _Footer 有特殊含义,文件名即页面名);
      - Wiki 上存在、但 docs/wiki/ 里已不存在的 .md 会被删除
        (否则页面改名后会留下永远不再同步的孤儿页);
      - 非 .md 文件(例如图片)不动,避免误删 Wiki 上手工上传的附件。

.NOTES
    前提:仓库的 Wiki 功能已启用,且已手工创建过第一个页面。
      GitHub 只有在"启用 Wiki + 建立首页"之后才会生成 <repo>.wiki.git 仓库,
      在那之前任何推送都会失败。脚本检测到这种情况会直接给出操作指引。

    本脚本所有 git 调用都经 Invoke-Git 封装:因为 $ErrorActionPreference='Stop'
    会把 git 写到 stderr 的【预期失败】(比如仓库不存在)变成终止错误,导致
    报错信息盖掉脚本自己准备的排查提示。封装后统一按退出码判断。

.EXAMPLE
    # 先看会同步什么(不写任何远端)
    .\scripts\sync-wiki.ps1 -WhatIf

.EXAMPLE
    # 正式发布
    .\scripts\sync-wiki.ps1

.EXAMPLE
    # 保留临时工作区,便于检查推送前的状态
    .\scripts\sync-wiki.ps1 -KeepWorkDir

.PARAMETER RepoRoot
    仓库根目录,默认取本脚本的上一级。

.PARAMETER WikiDir
    Wiki 页面源目录(相对 RepoRoot),默认 docs/wiki。

.PARAMETER WikiRemote
    Wiki 的 git 地址。默认从 origin 推断:git@github.com:o/r.git → https://github.com/o/r.wiki.git。

.PARAMETER WorkDir
    临时工作区。默认在系统临时目录下新建一个,结束后清理。

.PARAMETER Message
    自定义提交信息。默认自动带上源提交 sha,便于把 Wiki 改动追回到仓库提交。

.PARAMETER KeepWorkDir
    保留临时工作区(默认会删除)。

.PARAMETER WhatIf
    只打印计划,不做任何写操作(由 SupportsShouldProcess 提供)。
#>
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),

    [string]$WikiDir = 'docs/wiki',

    [string]$WikiRemote,

    [string]$WorkDir,

    [string]$Message,

    [switch]$KeepWorkDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Header {
    param([string]$Text)
    Write-Host ''
    Write-Host ('=' * 64) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ('=' * 64) -ForegroundColor Cyan
}

# 统一的 git 调用入口:返回退出码 + 文本行,不因 stderr 而抛异常。
function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [string]$WorkingDirectory
    )

    $prefix = @()
    if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $prefix = @('-C', $WorkingDirectory)
    }

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = & git @prefix @Arguments 2>&1
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }

    return [pscustomobject]@{
        ExitCode = $code
        Lines    = @($raw | ForEach-Object { [string]$_ })
    }
}

# ---------------------------------------------------------------------------
# 1. 定位页面源目录 / 推断 Wiki 远程地址
# ---------------------------------------------------------------------------
Write-Header 'DevNest Wiki 发布'

$sourceDir = Join-Path $RepoRoot $WikiDir
if (-not (Test-Path $sourceDir)) {
    throw "找不到 Wiki 源目录:$sourceDir"
}
$sourceDir = (Resolve-Path $sourceDir).Path

$pages = @(Get-ChildItem -Path $sourceDir -Filter '*.md' -File | Sort-Object Name)
if ($pages.Count -eq 0) {
    throw "Wiki 源目录里没有 .md 页面:$sourceDir"
}

function Resolve-WikiRemoteFromOrigin {
    param([string]$Root)

    $result = Invoke-Git -Arguments @('remote', 'get-url', 'origin') -WorkingDirectory $Root
    if ($result.ExitCode -ne 0 -or $result.Lines.Count -eq 0) {
        throw '取不到 origin 远程地址,请用 -WikiRemote 显式指定 Wiki 的 git 地址。'
    }
    $origin = $result.Lines[0].Trim()

    # git@github.com:owner/repo.git
    $m = [regex]::Match($origin, '^git@(?<host>[^:]+):(?<path>.+?)(\.git)?$')
    if ($m.Success) {
        return ("https://{0}/{1}.wiki.git" -f $m.Groups['host'].Value, $m.Groups['path'].Value)
    }

    # https://github.com/owner/repo.git
    $m = [regex]::Match($origin, '^(?<base>https?://[^/]+/.+?)(\.git)?$')
    if ($m.Success) {
        return ("{0}.wiki.git" -f $m.Groups['base'].Value)
    }

    throw "无法从 origin 推断 Wiki 地址:$origin (请用 -WikiRemote 指定)"
}

$remote = if ([string]::IsNullOrWhiteSpace($WikiRemote)) {
    Resolve-WikiRemoteFromOrigin -Root $RepoRoot
}
else { $WikiRemote }

Write-Host "仓库根       : $RepoRoot"
Write-Host "页面源目录   : $sourceDir"
Write-Host "页面数量     : $($pages.Count)"
Write-Host "Wiki 远程    : $remote"
Write-Host "页面清单     : $((@($pages | ForEach-Object { $_.BaseName }) -join ', '))"

# ---------------------------------------------------------------------------
# 2. 确认 Wiki 仓库存在(只读检查,所以 -WhatIf 下也照跑)
# ---------------------------------------------------------------------------
$probe = Invoke-Git -Arguments @('ls-remote', '--exit-code', $remote, 'HEAD')
if ($probe.ExitCode -ne 0) {
    $info = [regex]::Match($remote, '^https?://(?<host>[^/]+)/(?<owner>[^/]+)/(?<repo>.+?)\.wiki\.git$')
    $hint = if ($info.Success) {
        $wikiHost = $info.Groups['host'].Value
        $ownerName = $info.Groups['owner'].Value
        $repoName = $info.Groups['repo'].Value
        @"
  1) 创建首页:https://$wikiHost/$ownerName/$repoName/wiki
     —— 点 Create the first page,标题填 Home,保存即可(内容随后会被本脚本覆盖)
     Wiki 仓库是【懒创建】的:一个页面都没有时,GitHub 根本不会建 .wiki.git,
     所以这一步只能走网页 —— REST API 没有任何创建 wiki 页面的端点。
  2) 若上一步找不到入口,再确认 Wiki 功能是开的:https://$wikiHost/$ownerName/$repoName/settings
     —— Settings -> Features -> 勾选 Wikis(新建仓库默认就是开的)

完成后重新运行本脚本即可。
"@
    }
    else {
        "  请确认 Wiki 功能已启用,并已创建过第一个页面。`n"
    }

    throw @"
Wiki 仓库不存在或不可访问:
  $remote

git 返回:$($probe.Lines -join ' / ')

GitHub 只有在【启用 Wiki + 建好首页】之后才会生成 .wiki.git 仓库,在那之前推送必然失败:

$hint
"@
}

Write-Host 'Wiki 仓库可访问:是' -ForegroundColor DarkGray

# ---------------------------------------------------------------------------
# 3. 一道闸门:确认后才做写操作(-WhatIf / -Confirm 在此生效)
# ---------------------------------------------------------------------------
if (-not $PSCmdlet.ShouldProcess("$remote ($($pages.Count) 个页面)", '同步 docs/wiki 到 GitHub Wiki')) {
    Write-Host ''
    Write-Host '已跳过(WhatIf):未做任何写操作。将会同步以下页面:' -ForegroundColor Yellow
    foreach ($p in $pages) { Write-Host "    $($p.Name)" -ForegroundColor DarkGray }
    return
}

# ---------------------------------------------------------------------------
# 4. 克隆 Wiki 仓库到临时工作区
# ---------------------------------------------------------------------------
$workRepo = if ([string]::IsNullOrWhiteSpace($WorkDir)) {
    Join-Path ([System.IO.Path]::GetTempPath()) ('devnest-wiki-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
}
else { $WorkDir }

if (Test-Path $workRepo) { Remove-Item -Recurse -Force $workRepo }

try {
    Write-Host ''
    Write-Host "克隆 Wiki 到临时工作区:$workRepo"
    $clone = Invoke-Git -Arguments @('clone', '--quiet', $remote, $workRepo)
    if ($clone.ExitCode -ne 0) {
        throw "git clone 失败:$($clone.Lines -join ' / ')"
    }

    # -----------------------------------------------------------------------
    # 5. 镜像 .md 页面
    # -----------------------------------------------------------------------
    $sourceNames = @($pages | ForEach-Object { $_.Name })

    $orphans = @(Get-ChildItem -Path $workRepo -Filter '*.md' -File |
        Where-Object { $sourceNames -notcontains $_.Name })

    foreach ($orphan in $orphans) {
        Write-Host "  删除孤儿页面:$($orphan.Name)" -ForegroundColor Yellow
        Remove-Item $orphan.FullName -Force
    }

    foreach ($page in $pages) {
        Write-Host "  同步页面:$($page.Name)"
        Copy-Item $page.FullName (Join-Path $workRepo $page.Name) -Force
    }

    # -----------------------------------------------------------------------
    # 6. 提交
    # -----------------------------------------------------------------------
    $add = Invoke-Git -Arguments @('add', '--all') -WorkingDirectory $workRepo
    if ($add.ExitCode -ne 0) {
        throw "git add 失败:$($add.Lines -join ' / ')"
    }

    $status = Invoke-Git -Arguments @('status', '--porcelain') -WorkingDirectory $workRepo
    if ($status.ExitCode -ne 0) {
        throw "git status 失败:$($status.Lines -join ' / ')"
    }

    if ($status.Lines.Count -eq 0) {
        Write-Host ''
        Write-Host 'Wiki 已是最新,无需提交。' -ForegroundColor Green
        return
    }

    if ([string]::IsNullOrWhiteSpace($Message)) {
        $shaResult = Invoke-Git -Arguments @('rev-parse', '--short', 'HEAD') -WorkingDirectory $RepoRoot
        $subjectResult = Invoke-Git -Arguments @('log', '-1', '--pretty=%s') -WorkingDirectory $RepoRoot
        $sha = if ($shaResult.ExitCode -eq 0) { ($shaResult.Lines -join '').Trim() } else { 'unknown' }
        $subject = if ($subjectResult.ExitCode -eq 0) { ($subjectResult.Lines -join '').Trim() } else { '' }

        $Message = @"
docs(wiki): 同步 docs/wiki 到 Wiki ($($pages.Count) 个页面)

源提交: $sha $subject
由 scripts/sync-wiki.ps1 生成,请勿在 GitHub 网页上直接编辑 Wiki 页面。
"@
    }

    Write-Host ''
    Write-Host '变更文件:'
    $status.Lines | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }

    # 提交信息是多行文本,用临时文件传给 git,避免跨平台的引号 / 换行问题。
    # 必须写成【不带 BOM】的 UTF-8:PS 5.1 的 Set-Content -Encoding UTF8 会加 BOM,
    # 而 BOM 会被带进 commit message 正文里。
    $msgFile = Join-Path ([System.IO.Path]::GetTempPath()) ('wiki-msg-' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '.txt')
    [System.IO.File]::WriteAllText($msgFile, $Message, (New-Object System.Text.UTF8Encoding($false)))

    try {
        $commit = Invoke-Git -Arguments @('commit', '--quiet', '--file', $msgFile) -WorkingDirectory $workRepo
        if ($commit.ExitCode -ne 0) {
            throw "git commit 失败:$($commit.Lines -join ' / ')"
        }
    }
    finally {
        Remove-Item $msgFile -Force -ErrorAction SilentlyContinue
    }

    # -----------------------------------------------------------------------
    # 7. 推送
    # -----------------------------------------------------------------------
    Write-Host ''
    Write-Host '推送到 GitHub Wiki...'
    $push = Invoke-Git -Arguments @('push', '--quiet') -WorkingDirectory $workRepo
    if ($push.ExitCode -ne 0) {
        throw "git push 失败(网络问题可重跑本脚本):$($push.Lines -join ' / ')"
    }

    Write-Host ''
    Write-Host 'Wiki 发布完成。' -ForegroundColor Green
}
finally {
    if ($KeepWorkDir) {
        Write-Host "临时工作区已保留:$workRepo" -ForegroundColor DarkGray
    }
    elseif (Test-Path $workRepo) {
        Remove-Item -Recurse -Force $workRepo -ErrorAction SilentlyContinue
    }
}
