# ============================================================
#  Reproducible backend verification for the current feature set.
#  Uses an isolated temporary data folder; it never touches the
#  real database in %APPDATA%\RentalManagementSystem.
#
#  Usage:
#    powershell -ExecutionPolicy Bypass -File verify.ps1
# ============================================================

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
    if (-not $mavenCmd) { throw "Maven not found. Add mvn.cmd to PATH." }
    $Maven = $mavenCmd.Source
}

Write-Host "Compiling..." -ForegroundColor Cyan
& $Maven -q compile
if ($LASTEXITCODE -ne 0) { throw "Compilation failed." }

& $Maven -q dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
if ($LASTEXITCODE -ne 0) { throw "Could not build the dependency classpath." }

$ClassPath = "target\classes;" + (Get-Content "target\cp.txt" -Raw)
$OutDir = "target\test-classes"
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

Write-Host "Compiling smoke test..." -ForegroundColor Cyan
& javac -cp $ClassPath -d $OutDir "tools\NowPhaseSmokeTest.java"
if ($LASTEXITCODE -ne 0) { throw "Smoke test compilation failed." }

Write-Host "Running smoke test..." -ForegroundColor Cyan
& java -cp "$OutDir;$ClassPath" NowPhaseSmokeTest
exit $LASTEXITCODE
