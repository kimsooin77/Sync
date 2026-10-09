[CmdletBinding()]
param(
    [string] $ArchivePath = (Join-Path (Join-Path $PSScriptRoot '..') 'build/aws-bundle.zip')
)

$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path -LiteralPath $ArchivePath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
try {
    $entries = @($zip.Entries | ForEach-Object { $_.FullName })
}
finally {
    $zip.Dispose()
}

$expectedEntries = @('Dockerfile', 'application.jar')
$hasExactlyExpectedRootEntries = $entries.Count -eq $expectedEntries.Count
foreach ($expected in $expectedEntries) {
    if (-not ($entries | Where-Object { [string]::Equals($_, $expected, [System.StringComparison]::Ordinal) })) {
        $hasExactlyExpectedRootEntries = $false
    }
}

if (-not $hasExactlyExpectedRootEntries) {
    $actualDescription = if ($entries.Count -eq 0) { '<empty>' } else { $entries -join ', ' }
    throw "Elastic Beanstalk requires Dockerfile and application.jar at the ZIP root. Actual entries: $actualDescription"
}

Write-Output "EB root entries verified: $($entries -join ', ')"
