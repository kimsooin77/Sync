[CmdletBinding()]
param(
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendDirectory = Join-Path $repositoryRoot 'frontend'
$frontendDist = Join-Path $frontendDirectory 'dist'
$buildDirectory = Join-Path $repositoryRoot 'build'
$bundleDirectory = Join-Path $buildDirectory 'aws-bundle'
$archivePath = Join-Path $buildDirectory 'aws-bundle.zip'
$awsDockerfile = Join-Path $repositoryRoot 'Dockerfile.aws'

if (-not (Test-Path -LiteralPath $awsDockerfile -PathType Leaf)) {
    throw "AWS runtime Dockerfile not found: $awsDockerfile"
}

if (-not $SkipBuild) {
    $stagingRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("employee-sync-aws-build-" + [guid]::NewGuid().ToString('N'))
    $frontendStagingDirectory = Join-Path $stagingRoot 'frontend'
    $repositoryStagingDirectory = Join-Path $stagingRoot 'repository'
    New-Item -ItemType Directory -Path $frontendStagingDirectory,$repositoryStagingDirectory -Force | Out-Null
    try {
        Get-ChildItem -LiteralPath $frontendDirectory -Force |
            Where-Object { $_.Name -notin @('node_modules', 'dist') } |
            Copy-Item -Destination $frontendStagingDirectory -Recurse -Force

        $excludedNames = @('.git', '.gradle', 'build', 'frontend', '.env', '.vscode', '.idea', '.aws', '.codex', '.agents')
        Get-ChildItem -LiteralPath $repositoryRoot -Force |
            Where-Object { $_.Name -notin $excludedNames -and $_.Name -notlike '.env.*' } |
            Copy-Item -Destination $repositoryStagingDirectory -Recurse -Force
        $frontendSourceStaging = Join-Path $repositoryStagingDirectory 'frontend'
        New-Item -ItemType Directory -Path $frontendSourceStaging -Force | Out-Null
        Get-ChildItem -LiteralPath $frontendDirectory -Force |
            Where-Object { $_.Name -notin @('node_modules', 'dist') } |
            Copy-Item -Destination $frontendSourceStaging -Recurse -Force

        $frontendDist = Join-Path $frontendStagingDirectory 'dist'
        Push-Location $frontendStagingDirectory
        $npmCommand = if ($IsWindows -or $env:OS -eq 'Windows_NT') { 'npm.cmd' } else { 'npm' }
        & $npmCommand ci
        if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
        & $npmCommand test
        if ($LASTEXITCODE -ne 0) { throw "frontend tests failed with exit code $LASTEXITCODE" }
        & $npmCommand run build
        if ($LASTEXITCODE -ne 0) { throw "frontend build failed with exit code $LASTEXITCODE" }
        Pop-Location

        $gradleWrapperName = if ($IsWindows -or $env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
        $gradleWrapper = Join-Path $repositoryStagingDirectory $gradleWrapperName
        if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
            throw "Gradle wrapper not found: $gradleWrapper"
        }
        if (-not ($IsWindows -or $env:OS -eq 'Windows_NT')) {
            & chmod +x $gradleWrapper
            if ($LASTEXITCODE -ne 0) { throw "Could not make Gradle wrapper executable: $LASTEXITCODE" }
        }

        Push-Location $repositoryStagingDirectory
        try {
            & $gradleWrapper clean build "-PfrontendDistDir=$frontendDist"
            if ($LASTEXITCODE -ne 0) { throw "Gradle clean build failed with exit code $LASTEXITCODE" }
        }
        finally {
            Pop-Location
        }

        $stagedJarCandidates = @(Get-ChildItem -LiteralPath (Join-Path $repositoryStagingDirectory 'build/libs') -Filter '*.jar' -File |
            Where-Object { $_.Name -notlike '*-plain.jar' })
        if ($stagedJarCandidates.Count -ne 1) {
            throw "Expected exactly one executable bootJar from isolated clean build, found $($stagedJarCandidates.Count)."
        }
        $repositoryLibs = Join-Path $buildDirectory 'libs'
        New-Item -ItemType Directory -Path $repositoryLibs -Force | Out-Null
        Copy-Item -LiteralPath $stagedJarCandidates[0].FullName -Destination (Join-Path $repositoryLibs $stagedJarCandidates[0].Name) -Force
    }
    finally {
        if ((Get-Location).Path.StartsWith($stagingRoot, [System.StringComparison]::OrdinalIgnoreCase)) { Pop-Location }
        if (Test-Path -LiteralPath $stagingRoot) {
            Remove-Item -LiteralPath $stagingRoot -Recurse -Force
        }
    }
}

$jarCandidates = @(Get-ChildItem -LiteralPath (Join-Path $buildDirectory 'libs') -Filter '*.jar' -File |
    Where-Object { $_.Name -notlike '*-plain.jar' })
if ($jarCandidates.Count -ne 1) {
    throw "Expected exactly one executable bootJar, found $($jarCandidates.Count). Run a clean build first."
}
$applicationJar = $jarCandidates[0]

$jarCommand = Get-Command 'jar.exe' -ErrorAction SilentlyContinue
if (-not $jarCommand) { $jarCommand = Get-Command 'jar' -ErrorAction SilentlyContinue }
if (-not $jarCommand) { throw 'JDK jar command is required to verify the bootJar contents.' }
$jarEntries = @(& $jarCommand.Source tf $applicationJar.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Could not list bootJar contents.' }

if ($jarEntries -notcontains 'BOOT-INF/classes/static/index.html') {
    throw 'bootJar has no React index.html. Refusing to create an incomplete AWS bundle.'
}
if (-not ($jarEntries | Where-Object { $_ -match '^BOOT-INF/classes/static/assets/.+' })) {
    throw 'bootJar has no built frontend assets under BOOT-INF/classes/static/assets/.'
}
foreach ($migration in @('V1__', 'V6__')) {
    if (-not ($jarEntries | Where-Object { $_ -match "^BOOT-INF/classes/db/migration/$migration.+\.sql$" })) {
        throw "bootJar is missing expected Flyway migration $migration*.sql."
    }
}

New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
$pathComparison = if ($IsWindows -or $env:OS -eq 'Windows_NT') { [System.StringComparison]::OrdinalIgnoreCase } else { [System.StringComparison]::Ordinal }
$resolvedBuild = (Resolve-Path -LiteralPath $buildDirectory).Path.TrimEnd([char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)) + [System.IO.Path]::DirectorySeparatorChar
$resolvedBundleDirectory = [System.IO.Path]::GetFullPath($bundleDirectory)
if ($resolvedBundleDirectory.StartsWith($resolvedBuild, $pathComparison)) {
    if (Test-Path -LiteralPath $bundleDirectory) {
        Remove-Item -LiteralPath $bundleDirectory -Recurse -Force
    }
}
else {
    throw 'Bundle output path escaped the repository build directory.'
}
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
    Remove-Item -LiteralPath $archivePath -Force
}

New-Item -ItemType Directory -Path $bundleDirectory -Force | Out-Null
Copy-Item -LiteralPath $awsDockerfile -Destination (Join-Path $bundleDirectory 'Dockerfile')
Copy-Item -LiteralPath $applicationJar.FullName -Destination (Join-Path $bundleDirectory 'application.jar')

$expectedFiles = @('Dockerfile', 'application.jar')
$actualFiles = @(Get-ChildItem -LiteralPath $bundleDirectory -File -Recurse |
    ForEach-Object { $_.FullName.Substring($bundleDirectory.TrimEnd([char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)).Length + 1).Replace([System.IO.Path]::DirectorySeparatorChar, '/') } |
    Sort-Object)
if (Compare-Object ($expectedFiles | Sort-Object) $actualFiles) {
    throw "AWS bundle contains unexpected or missing files: $($actualFiles -join ', ')"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($bundleDirectory, $archivePath)
$zip = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    $zipEntries = @($zip.Entries | ForEach-Object { $_.FullName } | Sort-Object)
    if (Compare-Object ($expectedFiles | Sort-Object) $zipEntries) {
        throw "AWS ZIP contains unexpected or missing entries: $($zipEntries -join ', ')"
    }

    $zipDockerfile = $zip.GetEntry('Dockerfile')
    $dockerfileReader = [System.IO.StreamReader]::new($zipDockerfile.Open())
    try { $dockerfileContent = $dockerfileReader.ReadToEnd() }
    finally { $dockerfileReader.Dispose() }
    $sourceDockerfileContent = Get-Content -LiteralPath $awsDockerfile -Raw
    if ($dockerfileContent -ne $sourceDockerfileContent) {
        throw 'Dockerfile in the AWS ZIP differs from Dockerfile.aws.'
    }

    $zipJar = $zip.GetEntry('application.jar')
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $jarStream = $zipJar.Open()
    try {
        $zipJarHash = [System.BitConverter]::ToString($sha256.ComputeHash($jarStream)).Replace('-', '')
    }
    finally {
        $jarStream.Dispose()
        $sha256.Dispose()
    }
    $sourceJarHash = (Get-FileHash -LiteralPath $applicationJar.FullName -Algorithm SHA256).Hash
    if ($zipJarHash -ne $sourceJarHash) {
        throw 'application.jar in the AWS ZIP differs from the verified bootJar.'
    }
}
finally {
    $zip.Dispose()
}

& (Join-Path $PSScriptRoot 'Test-AwsBundle.ps1') -ArchivePath $archivePath

Write-Output "Verified bootJar: $($applicationJar.FullName)"
Write-Output "AWS bundle directory: $bundleDirectory"
Write-Output "AWS bundle archive: $archivePath"
Write-Output 'Bundle entries: Dockerfile, application.jar'
