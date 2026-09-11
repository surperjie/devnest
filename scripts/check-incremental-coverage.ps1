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
      - 只统计 src/main/java 下的代码,测试代码本身不计入;
      - 新增但尚未 `git add` 的文件整体纳入判定(经 `git ls-files --others`
        单独补齐)。否则本地"先跑门禁再提交"这一最该生效的场景里,
        新文件根本不在 git diff 中,会被当成"无判定对象"静默放行。

    本脚本是纯 PowerShell 实现,不依赖 Python/diff-cover:门禁本身必须能在本地
    和 CI 上被反复验证,否则"门禁是否真的在拦"就无法回答(路线图风险 R1「自指漏洞」)。

    基线过远保护:
      基线取 CI 的 github.event.before,正常情形它就是"上一次推送前的分支顶端",
      增量 = 本次推送真正引入的改动。但当基线离 HEAD 过远时 —— 典型场景是把
      长期分支【快进合并】进 main,此时 before 还停在几十个提交之前 —— "本次改动"
      会退化成"整个分支历史",增量门禁随之退化成【存量全量门禁】。

      实测该退化情形的结果是 22.6% 增量覆盖(存量本来就只有 ~22%),必然长期为红,
      正是路线图 R2 警告的"永远为红的门禁等于没有门禁"。因此脚本在判定前先检查
      基线是否过远(MaxBaselineCommits / MaxBaselineFiles),命中则回退到 HEAD~1
      并显式打印 notice:回退是【被宣布的降级】,既不静默放行,也不让门禁永久为红。

    未被判定的文件必须可见:
      若某模块本次没有产出覆盖率报告(典型原因:该模块一个测试都没有),该模块下
      的改动文件会被跳过。脚本会把"有多少个文件、分别是哪个模块"显式打印出来 ——
      否则"门禁对这批改动没有任何约束力"这件事就是静默的,又是一个自指漏洞。

.PARAMETER BaseRef
    比较基线(git ref / commit sha)。例如 origin/dev、HEAD~1、或 CI 传入的
    github.event.before。

    比较对象是【工作区】(不是 HEAD 提交):CI 上工作区 == HEAD,行为一致;
    本地则可以在 commit 之前先跑一遍,把问题挡在提交前。

    注意:git diff 看不见"未跟踪"的新文件,脚本会另外用 git ls-files --others
    把它们整体当作新增行纳入判定,因此本地没 add 的文件也不会被漏判。

.PARAMETER Threshold
    增量行覆盖率下限,0.80 表示 80%。与 backend/pom.xml 的 jacoco.incremental.floor 保持一致。

.PARAMETER MaxBaselineCommits
    基线过远判定(提交维度):BaseRef..HEAD 之间的提交数超过该值即认为基线过远。
    常规推送是 1~3 个提交;一次分支晋升是几十个,两者区分度很高。
    设为 0 表示关闭该项判定。

.PARAMETER MaxBaselineFiles
    基线过远判定(文件维度):backend 下改动的文件数超过该值即认为基线过远。
    用于兜住"提交数不多、但一次改了几百个文件"(整体格式化 / 批量搬迁)的情形。
    与 MaxBaselineCommits 是【或】关系,任一命中即回退。设为 0 表示关闭该项判定。

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

    [int]$MaxBaselineCommits = 15,

    [int]$MaxBaselineFiles = 60,

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
# 1b. 取"未跟踪"的新文件(git diff 看不见它们)
# ---------------------------------------------------------------------------
function Get-UntrackedFiles {
    param(
        [string]$Root,
        [string]$SubPath
    )

    $previousErrorAction = $ErrorActionPreference
    Push-Location $Root
    try {
        # --others --exclude-standard:未跟踪、且不被 .gitignore 忽略的文件。
        # 输出是仓库根相对路径(正斜杠),与 git diff 的 "+++ b/..." 口径一致,
        # 也与下面的 $javaPathPattern 对得上。
        #
        # 为什么必须单独取一次:git diff 只比较"已被跟踪"的文件。本地新增了
        # src/main/java 下的文件但还没 git add 时,该文件不在 diff 里,门禁会
        # 返回"无判定对象 -> 通过" —— 恰好把"提交前先跑一遍"这个最该生效的
        # 场景漏掉,是典型的自指漏洞。
        $ErrorActionPreference = 'Continue'
        $untracked = & git ls-files --others --exclude-standard -- $SubPath 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
        Pop-Location
    }

    if ($exitCode -ne 0) {
        throw "git ls-files 失败。请确认 $Root 是 git 仓库。"
    }

    return @($untracked | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

# ---------------------------------------------------------------------------
# 1c. 基线过远保护:把【分支晋升】与【增量提交】区分开
# ---------------------------------------------------------------------------
function Resolve-EffectiveBase {
    param(
        [string]$Base,
        [string]$Root,
        [string]$SubPath,
        [int]$MaxCommits,
        [int]$MaxFiles
    )

    $previousErrorAction = $ErrorActionPreference
    Push-Location $Root
    try {
        $ErrorActionPreference = 'Continue'

        $rawCount = & git rev-list --count "$Base..HEAD" 2>$null
        $commitCount = if ($LASTEXITCODE -eq 0 -and $rawCount) { [int]$rawCount } else { -1 }

        $rawFiles = @(& git diff --name-only --diff-filter=ACMR --no-color "$Base" -- $SubPath 2>$null)
        $fileCount = if ($LASTEXITCODE -eq 0) {
            @($rawFiles | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
        }
        else { -1 }
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
        Pop-Location
    }

    # 取不到就不擅自回退:让后面的 git diff 去暴露真实错误(例如 ref 不存在)。
    if ($commitCount -lt 0 -or $fileCount -lt 0) {
        return [pscustomobject]@{
            Base = $Base; Fallback = $false; Reason = ''; Commits = -1; Files = -1
        }
    }

    $reasons = New-Object System.Collections.Generic.List[string]
    if ($MaxCommits -gt 0 -and $commitCount -gt $MaxCommits) {
        $reasons.Add("提交跨度 $commitCount 个 > 上限 $MaxCommits")
    }
    if ($MaxFiles -gt 0 -and $fileCount -gt $MaxFiles) {
        $reasons.Add("$SubPath 下改动文件 $fileCount 个 > 上限 $MaxFiles")
    }

    $tripped = $reasons.Count -gt 0
    return [pscustomobject]@{
        Base     = $(if ($tripped) { 'HEAD~1' } else { $Base })
        Fallback = $tripped
        Reason   = ($reasons -join '; ')
        Commits  = $commitCount
        Files    = $fileCount
    }
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

# 基线过远保护:见 Resolve-EffectiveBase 的注释。
$effective = Resolve-EffectiveBase -Base $BaseRef -Root $RepoRoot -SubPath $ModulesRelativePath `
    -MaxCommits $MaxBaselineCommits -MaxFiles $MaxBaselineFiles

# 门禁的输入必须能被回答:$BaseRef 到底圈住了多大范围,要一直可见。
if ($effective.Commits -ge 0) {
    Write-Host "基线范围        : $($effective.Commits) 个提交, $ModulesRelativePath 下改动文件 $($effective.Files) 个" `
        -ForegroundColor DarkGray
}

if ($effective.Fallback) {
    Write-Host ''
    Write-Host '基线过远,已回退到 HEAD~1 —— 本次按"增量提交"判定,而不是"整个分支历史"。' -ForegroundColor Yellow
    Write-Host "  原因    : $($effective.Reason)" -ForegroundColor Yellow
    Write-Host "  实际基线: $($effective.Base)" -ForegroundColor Yellow
    Write-Host "::notice title=增量覆盖率基线已回退::BaseRef=$BaseRef 距离 HEAD 过远($($effective.Reason)); 已改用 HEAD~1 判定,避免增量门禁退化成存量全量门禁。"
}

$changed = Get-ChangedLinesByFile -Base $effective.Base -Root $RepoRoot -SubPath $ModulesRelativePath
$untrackedFiles = @(Get-UntrackedFiles -Root $RepoRoot -SubPath $ModulesRelativePath)

# 判定对象 = diff 得到的"改动行" + 未跟踪的新文件(值为 $null 表示"整个文件都是新增")。
$targets = @{}
foreach ($file in $changed.Keys) { $targets[$file] = $changed[$file] }
foreach ($file in $untrackedFiles) {
    if (-not $targets.ContainsKey($file)) { $targets[$file] = $null }
}

if ($ShowDetail -and $untrackedFiles.Count -gt 0) {
    Write-Host "未跟踪(尚未 git add)的文件 $($untrackedFiles.Count) 个,已整体纳入判定:" -ForegroundColor DarkGray
    foreach ($file in $untrackedFiles) { Write-Host "  [new] $file" -ForegroundColor DarkGray }
}

# backend/<module>/src/main/java/<package...>/<File>.java
$javaPathPattern = '^' + [regex]::Escape($ModulesRelativePath) +
                   '/(?<module>[^/]+)/src/main/java/(?<package>.+)/(?<file>[^/]+\.java)$'

$coverageCache = @{}
$notJudgedByModule = @{}
$perFile = New-Object System.Collections.Generic.List[object]
$totalCovered = 0
$totalExecutable = 0

foreach ($file in $targets.Keys) {
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
        # 该文件没有进入覆盖率报告(可能该模块本次没跑测试,或被排除)。
        # 这里必须先记账,最后统一打印出来:否则"门禁对这批改动没有约束力"
        # 就变成静默事实 —— 加一堆无测试模块下的代码,门禁照样绿。
        if ($ShowDetail) {
            Write-Host "  [skip] $file —— 覆盖率报告中无此类(非可执行或模块未跑测试)" -ForegroundColor DarkGray
        }
        if ($notJudgedByModule.ContainsKey($module)) {
            $notJudgedByModule[$module] = $notJudgedByModule[$module] + 1
        }
        else {
            $notJudgedByModule[$module] = 1
        }
        continue
    }

    $lineStates = $table[$key]
    $covered = 0
    $executable = 0
    $uncoveredLines = New-Object System.Collections.Generic.List[int]

    # $null 表示"整个文件都是新增":候选行直接取 JaCoCo 报告里该文件的全部行,
    # 既不依赖读文件行数,也不会因为工作区内容与报告不同步而错位。
    $candidateLines = if ($null -eq $targets[$file]) { @($lineStates.Keys) } else { @($targets[$file]) }

    foreach ($lineNumber in ($candidateLines | Sort-Object -Unique)) {
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

# 未被判定的文件必须显式可见:门禁对它们没有约束力,这不能是个静默的事实。
$notJudgedTotal = 0
foreach ($moduleName in $notJudgedByModule.Keys) { $notJudgedTotal += $notJudgedByModule[$moduleName] }

if ($notJudgedTotal -gt 0) {
    Write-Host ''
    Write-Host "注意:有 $notJudgedTotal 个改动文件未纳入判定 —— 其所在模块本次没有产出覆盖率报告" -ForegroundColor Yellow
    Write-Host '(通常是该模块没有任何测试,JaCoCo 无执行数据可分析;也可能是该文件不含可执行行。)' -ForegroundColor Yellow
    foreach ($moduleName in ($notJudgedByModule.Keys | Sort-Object)) {
        Write-Host ("  {0}: {1} 个" -f $moduleName, $notJudgedByModule[$moduleName]) -ForegroundColor DarkGray
    }
    Write-Host '这些改动既不加分也不扣分 —— 门禁对它们没有约束力。' -ForegroundColor Yellow
}

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
