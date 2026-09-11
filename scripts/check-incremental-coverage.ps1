<#
.SYNOPSIS
    增量行覆盖率门禁:只卡"本次改动引入的、可执行却没有被测试覆盖"的代码行。

.DESCRIPTION
    为什么是"增量"而不是"全量":

    存量代码的行覆盖率目前只有 22% 左右(common 46% / core 36% / datasource 13.5% / redis 14.9%)。
    如果门禁直接卡全量 60%,构建会立刻永久为红 —— 而一个永远为红的门禁等于没有门禁,
    它只会让所有人学会忽略它(路线图风险 R2)。

    增量门禁换了个约束方向:不追究历史旧账,但要求"从今天起新增的代码必须被覆盖"。
    这样门禁引入当天即是绿的,同时能持续阻止覆盖率继续劣化。

    判定口径:
      - 只看本次改动【新增或修改】的行(来自 `git diff -U0`),删除行不计入分母;
      - 只看【可执行】的行(JaCoCo XML 里出现 <line> 元素的行);
        改注释、改空行、改 import 不会拉低覆盖率,也就不会误伤;
      - 只统计 src/main/java 下的代码,测试代码本身不计入。

    本脚本是纯 PowerShell 实现,不依赖 Python/diff-cover:门禁本身必须能在本地
    和 CI 上被反复验证,否则"门禁是否真的在拦"就无法回答(路线图风险 R1「自指漏洞」)。

.PARAMETER BaseRef
    比较基线(git ref / commit sha)。例如 origin/dev、HEAD~1、或 CI 传入的
    github.event.before。

    比较对象是【工作区】(不是 HEAD 提交):CI 上工作区 == HEAD,行为一致;
    本地则可以在 commit 之前先跑一遍,把问题挡在提交前。

.PARAMETER Threshold
    增量行覆盖率下限,0.80 表示 80%。与 backend/pom.xml 的 jacoco.incremental.floor 保持一致。

.PARAMETER ShowDetail
    打印每个文件的逐行统计,便于本地排查。

.EXAMPLE
    # 与上一个提交比较
    ./scripts/check-incremental-coverage.ps1 -BaseRef HEAD~1

.EXAMPLE
    # 与 dev 分支远端比较
    ./scripts/check-incremental-coverage.ps1 -BaseRef origin/dev -ShowDetail

.NOTES
    前置条件:先跑过 `mvn verify`(或 `mvn test` + `mvn jacoco:report`),
    使 backend/*/target/site/jacoco/jacoco.xml 存在。

    文件编码必须是 UTF-8 with BOM:Windows PowerShell 5.1 对无 BOM 的 .ps1
    会按系统 ANSI(中文环境为 GBK)读取,中文紧邻收尾引号时会吞掉引号,
    造成大面积语法错误。用 PS 7 或 CI 也一样能正确读取带 BOM 的文件。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseRef,

    [double]$Threshold = 0.80,

    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),

    [string]$ModulesRelativePath = 'backend',

    [switch]$ShowDetail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Header {
    param([string]$Text)
    Write-Host ''
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

# ---------------------------------------------------------------------------
# 1. 取本次改动的新增行号
# ---------------------------------------------------------------------------
function Get-ChangedLinesByFile {
    param(
        [string]$Base,
        [string]$Root,
        [string]$SubPath
    )

    $previousErrorAction = $ErrorActionPreference
    Push-Location $Root
    try {
        # -U0:不输出上下文,这样 @@ 头里的行段就精确等于"新增/修改的行"
        # --diff-filter=ACMR:只看新增/修改/重命名,删除文件不产生新增行,天然被排除
        #
        # 刻意不写 HEAD:比较的是【工作区】而不是"HEAD 这个提交"。
        # CI 上 checkout 之后工作区就等于 HEAD,两者结果一致;
        # 本地则因此可以在 commit 之前就跑门禁,问题在提交前暴露(左移)。
        #
        # 原生命令往 stderr 写内容时,PS 5.1 在 ErrorActionPreference=Stop 下会抛
        # NativeCommandError —— git 那句 "LF will be replaced by CRLF" 提示就会触发。
        # 这里局部放宽,并把 stderr 丢弃;git 的换行符处理保持仓库自身配置
        # (本仓库 core.autocrlf=true),不覆盖 —— 一旦强行 -c core.autocrlf=false,
        # CRLF 工作区里"改了 1 行"会被算成"整个文件都变了",门禁立刻失真。
        $ErrorActionPreference = 'Continue'
        $diff = & git diff --unified=0 --no-color --diff-filter=ACMR "$Base" -- $SubPath 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
        Pop-Location
    }

    if ($exitCode -ne 0) {
        throw "git diff 失败(BaseRef=$Base)。请确认该 ref 存在且已 fetch(CI 上需要 fetch-depth: 0)。"
    }

    # 用 [regex]::Match 的具名组,而不是 `-match` 的自动变量 $Matches:
    # $Matches 的键类型在 Windows PowerShell 5.1 与 PowerShell 7 之间并不一致
    # (5.1 里 ContainsKey('2') 恒为 False),会让 hunk 行数被静默当成 1 ——
    # 这是本脚本踩到的第三个"自指漏洞"。具名组 + Groups[...].Success 与版本无关。
    $hunkPattern = [regex]'^@@\s+-\d+(?:,\d+)?\s+\+(?<start>\d+)(?:,(?<count>\d+))?\s+@@'

    $result = [ordered]@{}
    $currentFile = $null

    foreach ($line in $diff) {
        if ($line.StartsWith('+++ b/')) {
            $currentFile = $line.Substring(6).Trim()
            if (-not $result.Contains($currentFile)) {
                $result[$currentFile] = New-Object System.Collections.Generic.List[int]
            }
            continue
        }

        if ($null -eq $currentFile) { continue }

        $hunk = $hunkPattern.Match($line)
        if ($hunk.Success) {
            $start = [int]$hunk.Groups['start'].Value
            # 没有 ",<count>" 时表示该段只有 1 行(git 对单行段省略计数)
            $count = if ($hunk.Groups['count'].Success) { [int]$hunk.Groups['count'].Value } else { 1 }
            for ($i = 0; $i -lt $count; $i++) {
                $result[$currentFile].Add($start + $i)
            }
        }
    }

    return $result
}

# ---------------------------------------------------------------------------
# 2. 读 JaCoCo XML,得到 每个源文件 -> (行号 -> 是否被覆盖)
# ---------------------------------------------------------------------------
function Get-CoverageBySourceFile {
    param([string]$XmlPath)

    $table = @{}
    if (-not (Test-Path $XmlPath)) { return $table }

    # 用 XmlDocument.Load + SelectNodes,而不是 PowerShell 的 XML 属性导航
    # ($doc.report.package)。后者在 Set-StrictMode -Version Latest 下,一旦某个
    # 元素没有对应子节点就会抛 PropertyNotFoundException,把整个读取打断 —— 这个
    # 坑在开发期真的踩到过:门禁静默失效并对外返回"通过"。
    # 显式 XPath 还有一个好处:对几 MB 的 jacoco.xml,不必把整棵 DOM 包装成
    # PSObject,解析更快。
    #
    # 必须显式关掉 DTD 解析:jacoco.xml 首行带
    #   <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
    # 而 report.dtd 并不随报告一起产出。XmlDocument.Load(path) 默认会去解析这个
    # 外部 DTD,找不到文件就抛异常 —— 全量扫描恰好不碰 java 文件时看不出问题,
    # 一旦真有 java 改动就整体失败。这里用 DtdProcessing=Ignore + XmlResolver=null,
    # 既不读 DTD 也不触发任何网络/磁盘解析(第二个自指漏洞)。
    $readerSettings = New-Object System.Xml.XmlReaderSettings
    $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
    $readerSettings.XmlResolver = $null

    $reader = [System.Xml.XmlReader]::Create((Resolve-Path -LiteralPath $XmlPath).Path, $readerSettings)
    try {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
    }
    finally {
        $reader.Dispose()
    }
    $root = $document.DocumentElement

    if ($null -eq $root) { return $table }

    foreach ($packageNode in $root.SelectNodes('package')) {
        $packageName = $packageNode.GetAttribute('name')

        foreach ($sourceFileNode in $packageNode.SelectNodes('sourcefile')) {
            $key = "$packageName/$($sourceFileNode.GetAttribute('name'))"
            $lineStates = @{}

            foreach ($lineNode in $sourceFileNode.SelectNodes('line')) {
                $nr = [int]$lineNode.GetAttribute('nr')
                $ci = [int]$lineNode.GetAttribute('ci')
                # 行是否被覆盖 = 该行是否含"已执行的指令"(ci > 0)
                $lineStates[$nr] = ($ci -gt 0)
            }

            $table[$key] = $lineStates
        }
    }

    return $table
}

# ---------------------------------------------------------------------------
# 3. 主流程
# ---------------------------------------------------------------------------
Write-Header '增量行覆盖率门禁'
Write-Host "基线(BaseRef)   : $BaseRef"
Write-Host "阈值(Threshold): $([math]::Round($Threshold * 100, 1))%"
Write-Host "仓库根          : $RepoRoot"

$changed = Get-ChangedLinesByFile -Base $BaseRef -Root $RepoRoot -SubPath $ModulesRelativePath

# backend/<module>/src/main/java/<package...>/<File>.java
$javaPathPattern = '^' + [regex]::Escape($ModulesRelativePath) +
                   '/(?<module>[^/]+)/src/main/java/(?<package>.+)/(?<file>[^/]+\.java)$'

$coverageCache = @{}
$perFile = New-Object System.Collections.Generic.List[object]
$totalCovered = 0
$totalExecutable = 0

foreach ($file in $changed.Keys) {
    $match = [regex]::Match($file, $javaPathPattern)
    if (-not $match.Success) { continue }

    $module = $match.Groups['module'].Value
    $package = $match.Groups['package'].Value
    $fileName = $match.Groups['file'].Value

    if (-not $coverageCache.ContainsKey($module)) {
        $xmlPath = Join-Path $RepoRoot "$ModulesRelativePath/$module/target/site/jacoco/jacoco.xml"
        $coverageCache[$module] = Get-CoverageBySourceFile -XmlPath $xmlPath
    }

    $table = $coverageCache[$module]
    $key = "$package/$fileName"
    if (-not $table.ContainsKey($key)) {
        # 该文件没有进入覆盖率报告(可能该模块本次没跑测试,或被排除)
        if ($ShowDetail) {
            Write-Host "  [skip] $file —— 覆盖率报告中无此类(非可执行或模块未跑测试)" -ForegroundColor DarkGray
        }
        continue
    }

    $lineStates = $table[$key]
    $covered = 0
    $executable = 0
    $uncoveredLines = New-Object System.Collections.Generic.List[int]

    foreach ($lineNumber in ($changed[$file] | Sort-Object -Unique)) {
        if (-not $lineStates.ContainsKey($lineNumber)) { continue }  # 非可执行行(注释/空行)不计
        $executable++
        if ($lineStates[$lineNumber]) {
            $covered++
        }
        else {
            $uncoveredLines.Add($lineNumber)
        }
    }

    if ($executable -eq 0) { continue }

    $totalCovered += $covered
    $totalExecutable += $executable

    $perFile.Add([pscustomobject]@{
            File       = $file
            Covered    = $covered
            Executable = $executable
            Ratio      = $covered / $executable
            Uncovered  = $uncoveredLines
        })
}

Write-Header '统计结果'

if ($totalExecutable -eq 0) {
    Write-Host '本次改动没有触及任何可执行的代码行(仅注释/空行/import/测试代码或纯配置),' -ForegroundColor Yellow
    Write-Host '增量覆盖率门禁无判定对象 —— 按通过处理。' -ForegroundColor Yellow
    exit 0
}

foreach ($item in ($perFile | Sort-Object File)) {
    $pct = [math]::Round($item.Ratio * 100, 1)
    $color = if ($item.Ratio -ge $Threshold) { 'Green' } else { 'Red' }
    Write-Host ("  {0,6:N1}%  ({1}/{2})  {3}" -f $pct, $item.Covered, $item.Executable, $item.File) -ForegroundColor $color

    if ($ShowDetail -and $item.Uncovered.Count -gt 0) {
        Write-Host "          未覆盖行: $($item.Uncovered -join ', ')" -ForegroundColor DarkGray
    }
}

$overall = $totalCovered / $totalExecutable
$overallPct = [math]::Round($overall * 100, 1)

Write-Host ''
Write-Host ("增量行覆盖: {0}%  ({1}/{2})" -f $overallPct, $totalCovered, $totalExecutable) `
    -ForegroundColor $(if ($overall -ge $Threshold) { 'Green' } else { 'Red' })

if ($overall -lt $Threshold) {
    Write-Host ''
    Write-Host ("门禁未通过:增量行覆盖率 {0}% 低于要求的 {1}%。" -f $overallPct, [math]::Round($Threshold * 100, 1)) -ForegroundColor Red
    Write-Host '请为本次改动的可执行行补上测试,或用 -ShowDetail 查看具体未覆盖的行号。' -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host '门禁通过。' -ForegroundColor Green
exit 0
