# ============================================================
#  Builds the Windows app image / installer with jpackage.
#
#  Usage:
#    powershell -ExecutionPolicy Bypass -File package.ps1
#        -> portable app image in dist\Rental Management System
#
#    powershell -ExecutionPolicy Bypass -File package.ps1 -Type exe
#        -> .exe installer (requires WiX Toolset 3.x)
#
#    powershell -ExecutionPolicy Bypass -File package.ps1 -Type msi
#        -> .msi installer (requires WiX Toolset 3.x)
#
#  The application stores its database in
#  %APPDATA%\RentalManagementSystem so the app image is fully
#  portable and the installed version can write its data.
# ============================================================

param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Tools\jdk-21.0.5+11"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$Maven = "C:\Tools\apache-maven-3.9.9\bin\mvn.cmd"
if (-not (Test-Path $Maven)) {
    $mavenCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mavenCmd) { throw "Maven not found. Set MAVEN_HOME or add mvn.cmd to PATH." }
    $Maven = $mavenCmd.Source
}

$Jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $Jpackage)) { throw "jpackage not found in $env:JAVA_HOME\bin" }

$AppName = "Rental Management System"
$MainJar = "rental-management-system-1.0-SNAPSHOT.jar"
$InputDir = Join-Path $ProjectRoot "target\package-input"
$Icon = Join-Path $ProjectRoot "packaging\icon.ico"

if ($Type -ne "app-image") {
    $wix = @(
        "C:\Program Files (x86)\WiX Toolset v3.11\bin\candle.exe",
        "C:\Program Files\WiX Toolset v3.11\bin\candle.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $wix) {
        throw "WiX Toolset 3.x is required for '$Type' packages. Install it from https://github.com/wixtoolset/wix3/releases and run this script again. Use -Type app-image for a portable build without WiX."
    }
}

if (-not $SkipBuild) {
    Write-Host "Building the application..." -ForegroundColor Cyan
    & $Maven clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
}

if (-not (Test-Path (Join-Path $ProjectRoot "target\$MainJar"))) {
    throw "Main jar not found. Run without -SkipBuild first."
}

Write-Host "Collecting runtime dependencies..." -ForegroundColor Cyan
if (Test-Path $InputDir) { Remove-Item -Recurse -Force $InputDir }
New-Item -ItemType Directory -Path $InputDir | Out-Null

Copy-Item (Join-Path $ProjectRoot "target\$MainJar") $InputDir
& $Maven dependency:copy-dependencies "-DoutputDirectory=$InputDir" "-DincludeScope=runtime" -q
if ($LASTEXITCODE -ne 0) { throw "Could not copy dependencies." }

$DistDir = Join-Path $ProjectRoot "dist"
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

if ($Type -eq "app-image") {
    $appOutput = Join-Path $DistDir $AppName
    if (Test-Path $appOutput) { Remove-Item -Recurse -Force $appOutput }
}

$jpackageArgs = @(
    "--type", $Type,
    "--name", $AppName,
    "--app-version", "1.0.0",
    "--vendor", "RentalSys",
    "--description", "Rental property, tenant and payment management",
    "--copyright", "Copyright (c) 2026 RentalSys",
    "--input", $InputDir,
    "--main-jar", $MainJar,
    "--main-class", "com.rentals.Launcher",
    "--dest", $DistDir,
    "--java-options", "-Dfile.encoding=UTF-8"
)

if (Test-Path $Icon) { $jpackageArgs += @("--icon", $Icon) }

if ($Type -ne "app-image") {
    $jpackageArgs += @(
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install"
    )
}

Write-Host "Running jpackage ($Type)..." -ForegroundColor Cyan
& $Jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

Write-Host ""
if ($Type -eq "app-image") {
    Write-Host "Portable app image created:" -ForegroundColor Green
    Write-Host "  $DistDir\$AppName\$AppName.exe"
    Write-Host ""
    Write-Host "The folder can be copied to another PC. Data is stored in:"
    Write-Host "  %APPDATA%\RentalManagementSystem"
} else {
    Write-Host "Installer created in: $DistDir" -ForegroundColor Green
}
