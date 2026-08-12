param(
    [switch]$ValidateOnly,
    [string[]]$Targets = @('mc1.21.1', 'mc1.20.1', 'mc1.16.5', 'mc1.12.2')
)

$ErrorActionPreference = 'Stop'
$matrixPath = Join-Path $PSScriptRoot '..\versions\minecraft-ae2.properties'
$matrixPath = [System.IO.Path]::GetFullPath($matrixPath)
$matrix = [System.Collections.Specialized.OrderedDictionary]::new()
$previousJavaHome = $env:JAVA_HOME

foreach ($line in Get-Content -Encoding utf8 $matrixPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }
    $parts = $line.Split('=', 2)
    if ($parts.Count -ne 2) {
        throw "Invalid version matrix line: $line"
    }
    $key = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($key -notmatch '^(mc\d+\.\d+\.\d+)\.(loader|minecraft|ae2|java)$') {
        throw "Invalid version matrix key: $key"
    }
    $target = $Matches[1]
    $field = $Matches[2]
    if (-not $matrix.Contains($target)) {
        $matrix[$target] = @{}
    }
    $matrix[$target][$field] = $value
}

try {
    foreach ($target in $Targets) {
        if (-not $matrix.Contains($target)) {
            throw "Unknown target '$target'"
        }
        $entry = $matrix[$target]
        foreach ($field in @('loader', 'minecraft', 'ae2', 'java')) {
            if (-not $entry.ContainsKey($field) -or [string]::IsNullOrWhiteSpace($entry[$field])) {
                throw "$target is missing '$field' in the version matrix"
            }
        }
        $jdkPath = $entry['java'].Replace('/', '\')
        if (-not (Test-Path -LiteralPath $jdkPath -PathType Container)) {
            throw "$target JDK does not exist: $jdkPath"
        }
        $javaExecutable = Join-Path $jdkPath 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            throw "$target JDK has no java executable: $javaExecutable"
        }
        Write-Host "${target}: Minecraft $($entry['minecraft']), AE2 $($entry['ae2']), $($entry['loader']), JDK $jdkPath"

        $platformRoot = Join-Path $PSScriptRoot "..\platforms\$target-$($entry['loader'])"
        $projectRoot = [System.IO.Path]::GetFullPath($platformRoot)
        if (-not (Test-Path -LiteralPath $projectRoot -PathType Container)) {
            throw "$target platform project does not exist: $projectRoot"
        }
        $gradle = Join-Path $projectRoot 'gradlew.bat'
        $gradle = [System.IO.Path]::GetFullPath($gradle)
        if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
            throw "$target platform has no Gradle wrapper: $gradle"
        }
        $projectPropertiesPath = Join-Path $projectRoot 'gradle.properties'
        if (-not (Test-Path -LiteralPath $projectPropertiesPath -PathType Leaf)) {
            throw "$target platform has no gradle.properties: $projectPropertiesPath"
        }
        $projectProperties = ConvertFrom-StringData (Get-Content -LiteralPath $projectPropertiesPath -Raw)
        foreach ($mapping in @(
                @{ Matrix = 'minecraft'; Gradle = 'minecraft_version' },
                @{ Matrix = 'ae2'; Gradle = 'ae2_version' }
        )) {
            $actual = $projectProperties[$mapping.Gradle]
            $expected = $entry[$mapping.Matrix]
            if ($actual -ne $expected) {
                throw "$target $($mapping.Gradle) is '$actual', expected '$expected' from the version matrix"
            }
        }

        if ($ValidateOnly) {
            continue
        }

        $env:JAVA_HOME = $jdkPath
        $gradleArguments = @('-p', $projectRoot, 'build')
        if ($target -ne 'mc1.12.2') {
            $gradleArguments += '--no-configuration-cache'
        }
        & $gradle $gradleArguments
        if ($LASTEXITCODE -ne 0) {
            throw "$target build failed with exit code $LASTEXITCODE"
        }
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
