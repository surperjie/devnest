<#
.SYNOPSIS
    手动启动后端(开发调试用)。自动把当前控制台切成 UTF-8,避免中文日志在 GBK
    控制台(代码页 936)下乱码;自动定位 JDK 21,以 dev profile 运行 devnest-boot.jar。

.DESCRIPTION
    乱码原因:后端日志按 UTF-8 输出(JDK 18+ 默认),而 Windows 简体中文控制台默认按
    GBK(代码页 936)解码 UTF-8 字节,于是中文变成 "鏃犻渶"/"锟斤拷" 一类乱码。
    本脚本在启动前执行 chcp 65001 并把控制台输出编码设为 UTF-8,从根上解决;
    UI 内部拉起的后端已由前端直接落盘 UTF-8 日志(%TEMP%\devnest-backend.log),不受影响。

    Java 定位优先级:仓库内打包 jre21 > 本机 $HOME\.jdks(JDK≥21)> Program Files\Java
    > PATH 中的 java(要求 JDK≥21)。

.PARAMETER Jar
    后端 jar 路径,默认 backend\devnest-boot\target\devnest-boot.jar。
    不存在时提示先构建。

.EXAMPLE
    .\scripts\start-backend.ps1
    .\scripts\start-backend.ps1 -Jar E:\mygithubexe\DevNest\resources\devnest-boot.jar
#>
param(
    [string]$Jar = ''
)
$ErrorActionPreference = 'Stop'

# 1) 控制台切 UTF-8:先 chcp,再设 PowerShell 5.1 的输出编码(两者都做,双保险)
try { chcp 65001 | Out-Null } catch { }
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Repo = Split-Path -Parent $PSScriptRoot

# 2) 定位后端 jar
if (-not $Jar) { $Jar = Join-Path $Repo 'backend\devnest-boot\target\devnest-boot.jar' }
$Jar = [System.IO.Path]::GetFullPath($Jar)
if (-not (Test-Path $Jar)) {
    Write-Host "[start-backend] 未找到后端 jar:`n  $Jar`n请先构建(需要 JDK 21):  .\build-app.ps1 -SkipFrontend" -ForegroundColor Yellow
    exit 1
}

# 3) 定位 JDK 21 的 java
function Test-Jdk21([string]$javaExe) {
    if (-not $javaExe -or -not (Test-Path $javaExe)) { return $false }
    try {
        $v = & $javaExe -version 2>&1 | Out-String
        return ($v -match 'version "(\d+)') -and ([int]$Matches[1] -ge 21)
    } catch { return $false }
}
$cands = [System.Collections.Generic.List[string]]::new()
$cands.Add((Join-Path $Repo 'frontend-ui\src-tauri\resources\jre21\bin\java.exe'))
$cands.Add((Join-Path (Split-Path -Parent $Repo) 'DevNest\resources\jre21\bin\java.exe'))
$jdksRoot = Join-Path $HOME '.jdks'
if (Test-Path $jdksRoot) {
    Get-ChildItem $jdksRoot -Recurse -Filter 'java.exe' -Depth 3 -ErrorAction SilentlyContinue |
        ForEach-Object { $cands.Add($_.FullName) }
}
if (Test-Path 'C:\Program Files\Java') {
    Get-ChildItem 'C:\Program Files\Java' -Recurse -Filter 'java.exe' -Depth 4 -ErrorAction SilentlyContinue |
        ForEach-Object { $cands.Add($_.FullName) }
}
$cands.Add((Get-Command java -ErrorAction SilentlyContinue).Source)

$java = $null
foreach ($c in ($cands | Select-Object -Unique)) {
    if (Test-Jdk21 $c) { $java = $c; break }
}
if (-not $java) {
    Write-Host '[start-backend] 未找到 JDK 21(需要 jlink 同款 JDK)。请安装 JDK 21 后重试。' -ForegroundColor Yellow
    exit 1
}

$verLine = (& $java -version 2>&1 | Select-Object -First 1)
Write-Host "[start-backend] Java : $verLine" -ForegroundColor Cyan
Write-Host "[start-backend] Jar  : $Jar" -ForegroundColor Cyan
Write-Host "[start-backend] 启动中(Ctrl+C 停止),日志中文应正常显示..." -ForegroundColor Cyan

# 4) 前台运行(同一控制台,可 Ctrl+C 优雅停止)
& $java -jar $Jar --spring.profiles.active=dev
exit $LASTEXITCODE
