# DevNest one-click build script: backend jar + frontend Tauri exe
#
# Usage: .\build-app.ps1
# Output: frontend-ui\src-tauri\target\release\bundle\
#
# Requirements:
#   1. GraalVM JDK 21 installed (default: C:\Users\jie\.jdks\graalvm-jdk-21.0.7)
#   2. Node.js + npm
#   3. Rust (tauri build dependency)
#   4. DM JDBC driver installed via mvn install (optional)

param(
    [string]$JavaHome = "C:\Users\jie\.jdks\graalvm-jdk-21.0.7",
    [switch]$SkipBackend
)

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend-ui"
$ResourcesDir = Join-Path $FrontendDir "src-tauri\resources"
$JarSource = Join-Path $BackendDir "devnest-boot\target\devnest-boot-1.0.0.jar"
$JarDest = Join-Path $ResourcesDir "devnest-boot.jar"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " DevNest Build Start" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# 1. Check environment
Write-Host "`n[1/5] Checking environment..." -ForegroundColor Yellow
if (-not (Test-Path $JavaHome)) {
    Write-Error "JAVA_HOME not found: $JavaHome`nUse -JavaHome to specify correct JDK 21 path"
    exit 1
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
Write-Host "  JAVA_HOME = $JavaHome"
java -version

# 2. Build backend
if ($SkipBackend) {
    Write-Host "`n[2/5] Skipping backend build" -ForegroundColor Yellow
} else {
    Write-Host "`n[2/5] Building backend Spring Boot jar..." -ForegroundColor Yellow
    Set-Location $BackendDir
    mvn package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Backend build failed"
        exit 1
    }
    Write-Host "  Backend build done" -ForegroundColor Green
}

# 3. Copy jar to resources
Write-Host "`n[3/5] Copying backend jar to Tauri resources..." -ForegroundColor Yellow
if (-not (Test-Path $JarSource)) {
    Write-Error "Backend jar not found: $JarSource`nPlease build backend first"
    exit 1
}
if (-not (Test-Path $ResourcesDir)) {
    New-Item -ItemType Directory -Path $ResourcesDir -Force | Out-Null
}
Copy-Item $JarSource $JarDest -Force
$jarSize = (Get-Item $JarDest).Length / 1MB
Write-Host "  Copied devnest-boot.jar ($([math]::Round($jarSize, 1)) MB)" -ForegroundColor Green

# 4. Build bundled JRE (shared script: scripts/build-jre.ps1)
#    Module list includes jdk.unsupported (required by Spring AOP/CGLIB),
#    otherwise @Transactional services fail at startup with "Unexpected AOP exception".
Write-Host "`n[4/5] Building bundled JRE (jlink, incl. jdk.unsupported)..." -ForegroundColor Yellow
$BuildJreScript = Join-Path $ProjectRoot "scripts\build-jre.ps1"
& $BuildJreScript -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) {
    Write-Error "Bundled JRE build failed"
    exit 1
}

# 5. Tauri build
Write-Host "`n[5/5] Tauri build exe (first run is slow, compiling Rust)..." -ForegroundColor Yellow
Set-Location $FrontendDir
npm run tauri build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Tauri build failed"
    exit 1
}

Write-Host "`n============================================" -ForegroundColor Green
Write-Host " Build Complete!" -ForegroundColor Green
Write-Host " Output: frontend-ui\src-tauri\target\release\bundle\" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
