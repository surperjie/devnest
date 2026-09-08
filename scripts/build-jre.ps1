<#
.SYNOPSIS
    Generate the minimal bundled JRE 21 (frontend-ui/src-tauri/resources/jre21) via jlink.

.DESCRIPTION
    Single source of truth for the jlink module list. Used by:
      - local build : .\build-app.ps1  (step 4)
      - CI pipeline : .github/workflows/build-app.yml (step "Create minimal JRE 21 with jlink")

    IMPORTANT — jdk.unsupported is REQUIRED:
    Spring Boot AOP (CGLIB / Objenesis) calls sun.misc.Unsafe at runtime. If this
    module is trimmed away, every @Transactional / @Async service fails at startup with:
        NoClassDefFoundError: sun/misc/Unsafe
    which Spring wraps as "Unexpected AOP exception" when creating the bean
    (observed in com.devnest.tunnel.service.impl.SshTunnelServiceImpl). Therefore the
    module list below MUST always contain jdk.unsupported, and a self-check verifies
    it is actually present in the generated runtime.

.PARAMETER JavaHome
    Path to a JDK 21 that ships jlink + jmods (default: $env:JAVA_HOME).

.PARAMETER Force
    Rebuild even if resources/jre21 already exists.

.EXAMPLE
    .\scripts\build-jre.ps1
    .\scripts\build-jre.ps1 -JavaHome C:\Users\jie\.jdks\graalvm-jdk-21.0.7 -Force
#>
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# jdk.unsupported 是 Spring AOP(CGLIB/Objenesis) 运行所必需，切勿删减 —— 见 .DESCRIPTION
$Modules = @(
    'java.base',
    'java.desktop',
    'java.management',
    'java.naming',
    'java.sql',
    'java.instrument',
    'java.net.http',
    'java.scripting',
    'java.transaction.xa',
    'java.compiler',
    'java.xml',
    'java.security.jgss',
    'java.rmi',
    'jdk.unsupported'
)

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    Write-Error "JavaHome is not set. Pass -JavaHome <jdk21> or set JAVA_HOME."
    exit 1
}

$Jlink = Join-Path $JavaHome 'bin\jlink.exe'
if (-not (Test-Path $Jlink)) {
    Write-Error "jlink not found: $Jlink (JavaHome=$JavaHome)"
    exit 1
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot   # scripts/ -> repo root
$JreDir      = Join-Path $ProjectRoot 'frontend-ui\src-tauri\resources\jre21'

if ((Test-Path $JreDir) -and -not $Force) {
    Write-Host "[build-jre] resources/jre21 already exists, skipping jlink (use -Force to rebuild)"
    exit 0
}

# Ensure parent resources dir exists (tauri resource glob target)
$resParent = Split-Path -Parent $JreDir
if (-not (Test-Path $resParent)) {
    New-Item -Path $resParent -ItemType Directory -Force | Out-Null
}
if (Test-Path $JreDir) {
    Remove-Item -Recurse -Force $JreDir
}

Write-Host "[build-jre] Creating minimal JRE 21 via jlink: $($Modules -join ',')"
& $Jlink `
    --add-modules ($Modules -join ',') `
    --output $JreDir `
    --no-header-files `
    --no-man-pages `
    --compress=zip-6
if ($LASTEXITCODE -ne 0) {
    Write-Error "jlink failed (exit $LASTEXITCODE)"
    exit 1
}

$JavaExe = Join-Path $JreDir 'bin\java.exe'
if (-not (Test-Path $JavaExe)) {
    Write-Error "jlink output missing bin/java.exe: $JavaExe"
    exit 1
}

# Regression guard: the generated JRE must actually contain jdk.unsupported.
# Note: 对数组用 -match 取"命中的行"，而非 -notmatch(取所有未命中的行)。
$listed = & $JavaExe --list-modules 2>&1
if (($LASTEXITCODE -ne 0) -or -not ($listed -match '^jdk\.unsupported')) {
    Write-Error "jdk.unsupported is MISSING from the bundled JRE. Startup would fail (AOP/CGLIB). Aborting."
    exit 1
}

$size = [math]::Round((Get-ChildItem -Recurse $JreDir | Measure-Object -Property Length -Sum).Sum / 1MB, 1)
Write-Host "[build-jre] OK: JRE 21 ($size MB) -> $JreDir (jdk.unsupported included)"
