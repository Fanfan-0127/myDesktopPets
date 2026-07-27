[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath"
    }
}

$projectRoot = $PSScriptRoot
$targetDir = Join-Path $projectRoot "target"
$runtimeDir = Join-Path $targetDir "jre"
$packageInputDir = Join-Path $targetDir "package-input"
$releaseRoot = Join-Path $targetDir "release"
$appDir = Join-Path $releaseRoot "DesktopPet"
$jarPath = Join-Path $targetDir "desktoppet-1.0.jar"
$modelsDir = Join-Path $projectRoot "models"

Push-Location $projectRoot
try {
    $javac = Get-Command "javac" -ErrorAction Stop
    $maven = Get-Command "mvn" -ErrorAction Stop
    $jdkBin = Split-Path -Parent $javac.Source
    $jdeps = Join-Path $jdkBin "jdeps.exe"
    $jlink = Join-Path $jdkBin "jlink.exe"
    $jpackage = Join-Path $jdkBin "jpackage.exe"

    if (
        !(Test-Path -LiteralPath $jdeps) -or
        !(Test-Path -LiteralPath $jlink) -or
        !(Test-Path -LiteralPath $jpackage)
    ) {
        throw "The active JDK does not provide jdeps.exe, jlink.exe, and jpackage.exe: $jdkBin"
    }

    $javacOutput = (& $javac.Source -version 2>&1 | Out-String).Trim()
    $versionMatch = [regex]::Match($javacOutput, "javac\s+(\d+)(?:\.\S+)?")
    if ($LASTEXITCODE -ne 0 -or !$versionMatch.Success) {
        throw "Unable to determine the active JDK version."
    }
    $javacVersion = $versionMatch.Value
    if ([int]$versionMatch.Groups[1].Value -lt 21) {
        throw "JDK 21 or newer is required. Found: $javacVersion"
    }

    Write-Host "========================================"
    Write-Host "  DesktopPet Windows Release Builder"
    Write-Host "========================================"
    Write-Host "[INFO] $javacVersion"

    $runningPackagedApp = @(Get-Process -Name "DesktopPet" -ErrorAction SilentlyContinue | Where-Object {
        try {
            $_.Path -and $_.Path.StartsWith($appDir, [StringComparison]::OrdinalIgnoreCase)
        } catch {
            $false
        }
    })
    if ($runningPackagedApp.Count -gt 0) {
        throw "Close the packaged DesktopPet application before rebuilding: $appDir"
    }

    if (Test-Path -LiteralPath $runtimeDir) {
        Remove-Item -LiteralPath $runtimeDir -Recurse -Force
    }
    if (Test-Path -LiteralPath $packageInputDir) {
        Remove-Item -LiteralPath $packageInputDir -Recurse -Force
    }
    if (Test-Path -LiteralPath $releaseRoot) {
        Remove-Item -LiteralPath $releaseRoot -Recurse -Force
    }

    Write-Host "[1/4] Building the application JAR..."
    Invoke-CheckedCommand -FilePath $maven.Source -ArgumentList @(
        "package",
        "-DskipTests",
        "-q"
    )
    if (!(Test-Path -LiteralPath $jarPath)) {
        throw "Application JAR was not produced: $jarPath"
    }
    New-Item -ItemType Directory -Path $packageInputDir -Force | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination $packageInputDir

    $jdepsArguments = @(
        "--ignore-missing-deps",
        "--multi-release", "21",
        "--print-module-deps",
        $jarPath
    )
    $moduleDeps = (& $jdeps @jdepsArguments | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $moduleDeps -notmatch "^[a-zA-Z0-9.,]+$") {
        throw "Unable to determine the runtime module dependencies. Output: $moduleDeps"
    }
    Write-Host "[INFO] Runtime modules: $moduleDeps"

    Write-Host "[2/4] Creating the bundled Java runtime..."
    Invoke-CheckedCommand -FilePath $jlink -ArgumentList @(
        "--add-modules", $moduleDeps,
        "--strip-debug",
        "--no-man-pages",
        "--no-header-files",
        "--compress=zip-6",
        "--output", $runtimeDir
    )

    Write-Host "[3/4] Creating the native Windows application..."
    Invoke-CheckedCommand -FilePath $jpackage -ArgumentList @(
        "--type", "app-image",
        "--name", "DesktopPet",
        "--app-version", "1.0",
        "--description", "Desktop Pet",
        "--vendor", "DesktopPet",
        "--input", $packageInputDir,
        "--main-jar", "desktoppet-1.0.jar",
        "--main-class", "pet.Main",
        "--dest", $releaseRoot,
        "--runtime-image", $runtimeDir,
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx256m"
    )

    Write-Host "[4/4] Copying model assets..."
    if (Test-Path -LiteralPath $modelsDir) {
        $modelsDestination = Join-Path $appDir "models"
        New-Item -ItemType Directory -Path $modelsDestination -Force | Out-Null
        Get-ChildItem -LiteralPath $modelsDir | Copy-Item -Destination $modelsDestination -Recurse -Force
    } else {
        Write-Warning "Model directory not found: $modelsDir"
    }

    $launcher = Join-Path $appDir "DesktopPet.exe"
    if (!(Test-Path -LiteralPath $launcher)) {
        throw "Native launcher was not produced: $launcher"
    }

    Write-Host ""
    Write-Host "Build complete: $appDir"
    Write-Host "Launcher: $launcher"
} finally {
    Pop-Location
}
