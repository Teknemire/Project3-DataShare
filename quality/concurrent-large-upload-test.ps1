param(
    [string]$BaseUrl = "http://localhost:4200",
    [int]$ConcurrentUsers = 5,
    [string]$OutputPath = "$PSScriptRoot\concurrent-large-upload-result.json",
    [switch]$ConfirmLocalStorage,
    [switch]$KeepTemporaryFiles
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmLocalStorage) {
    throw "Ce test transfère plusieurs gigaoctets. Relancez-le avec -ConfirmLocalStorage après avoir vérifié que STORAGE_TYPE=local."
}
if ($ConcurrentUsers -lt 1) {
    throw "ConcurrentUsers doit être supérieur ou égal à 1."
}

$testCases = @(
    [ordered]@{ label = "100 MB"; bytes = 100000000L },
    [ordered]@{ label = "500 MB"; bytes = 500000000L },
    [ordered]@{ label = "1 GB"; bytes = 1000000000L }
)
$sourcePath = Join-Path $PSScriptRoot "concurrent-upload-source.txt"
$email = "concurrent-performance-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@datashare.test"
$password = "ConcurrentPerformance123!"
$accessToken = $null
$createdFileIds = New-Object System.Collections.Generic.List[string]
$temporaryPaths = New-Object System.Collections.Generic.List[string]
$result = [ordered]@{
    measuredAt = [DateTimeOffset]::UtcNow.ToString("o")
    baseUrl = $BaseUrl
    storage = "local"
    concurrentUsers = $ConcurrentUsers
    runs = @()
    cleanupCompleted = $false
}

function New-SizedFile([string]$Path, [long]$Length) {
    $stream = [System.IO.File]::Create($Path)
    try {
        $stream.SetLength($Length)
    }
    finally {
        $stream.Dispose()
    }
}

function Get-Percentile([double[]]$Values, [double]$Percentile) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Percentile * $sorted.Count) - 1
    return $sorted[[Math]::Max(0, $index)]
}

function Remove-CreatedFiles([hashtable]$Headers) {
    foreach ($fileId in @($createdFileIds)) {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/files/$fileId" -Method Delete -Headers $Headers | Out-Null
            $createdFileIds.Remove($fileId) | Out-Null
        }
        catch {
            Write-Warning "Impossible de supprimer le fichier de test $fileId : $($_.Exception.Message)"
        }
    }
}

try {
    Write-Host "Création et authentification du compte de performance..."
    $accountBody = @{ email = $email; password = $password } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $accountBody | Out-Null
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $accountBody
    $accessToken = $login.accessToken
    $headers = @{ Authorization = "Bearer $accessToken" }

    foreach ($testCase in $testCases) {
        Write-Host "Préparation de $($testCase.label) ($($testCase.bytes) octets)..."
        New-SizedFile -Path $sourcePath -Length $testCase.bytes

        $jobs = @()
        $responsePaths = @()
        $wallClock = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            for ($user = 1; $user -le $ConcurrentUsers; $user++) {
                $responsePath = Join-Path $PSScriptRoot "concurrent-$($testCase.bytes)-user-$user-response.json"
                $responsePaths += $responsePath
                $temporaryPaths.Add($responsePath)

                $jobs += Start-Job -ArgumentList $BaseUrl, $sourcePath, $responsePath, $accessToken, $user -ScriptBlock {
                    param($JobBaseUrl, $JobSourcePath, $JobResponsePath, $JobAccessToken, $JobUser)
                    $writeOut = '{"httpCode":%{http_code},"timeTotalSeconds":%{time_total},"sizeUpload":%{size_upload},"speedUpload":%{speed_upload}}'
                    $curlArguments = @(
                        '--silent',
                        '--show-error',
                        '--output', $JobResponsePath,
                        '--write-out', $writeOut,
                        '--header', "Authorization: Bearer $JobAccessToken",
                        '--form', "file=@$JobSourcePath;type=text/plain",
                        '--form', 'expirationDays=1',
                        '--form', "tags=PerformanceConcurrente,Utilisateur$JobUser",
                        "$JobBaseUrl/api/files"
                    )
                    $measurement = & curl.exe @curlArguments
                    if ($LASTEXITCODE -ne 0) {
                        throw "curl a échoué avec le code $LASTEXITCODE."
                    }
                    return $measurement
                }
            }

            Write-Host "Lancement de $ConcurrentUsers uploads simultanés de $($testCase.label)..."
            $jobs | Wait-Job | Out-Null
            $wallClock.Stop()

            $measurements = @()
            for ($index = 0; $index -lt $jobs.Count; $index++) {
                $rawMeasurement = Receive-Job -Job $jobs[$index] -ErrorAction Stop
                $measurement = ($rawMeasurement -join '') | ConvertFrom-Json
                $response = Get-Content $responsePaths[$index] -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($response.id) {
                    $createdFileIds.Add([string]$response.id)
                }
                $measurements += [pscustomobject]@{
                    user = $index + 1
                    httpCode = [int]$measurement.httpCode
                    timeTotalSeconds = [double]$measurement.timeTotalSeconds
                    sizeUpload = [long]$measurement.sizeUpload
                    speedUpload = [double]$measurement.speedUpload
                    fileId = $response.id
                    errorCode = $response.code
                }
            }

            $durations = @($measurements | ForEach-Object { $_.timeTotalSeconds })
            $successCount = @($measurements | Where-Object { $_.httpCode -eq 201 }).Count
            $wallSeconds = $wallClock.Elapsed.TotalSeconds
            $payloadBytes = [double]$testCase.bytes * $ConcurrentUsers
            $run = [ordered]@{
                label = $testCase.label
                fileBytes = $testCase.bytes
                concurrentUsers = $ConcurrentUsers
                successCount = $successCount
                failureCount = $ConcurrentUsers - $successCount
                wallTimeSeconds = [Math]::Round($wallSeconds, 6)
                averageRequestSeconds = [Math]::Round(($durations | Measure-Object -Average).Average, 6)
                p50RequestSeconds = [Math]::Round((Get-Percentile -Values $durations -Percentile 0.50), 6)
                p95RequestSeconds = [Math]::Round((Get-Percentile -Values $durations -Percentile 0.95), 6)
                minimumRequestSeconds = [Math]::Round(($durations | Measure-Object -Minimum).Minimum, 6)
                maximumRequestSeconds = [Math]::Round(($durations | Measure-Object -Maximum).Maximum, 6)
                aggregatePayloadBytesPerSecond = [Math]::Round($payloadBytes / $wallSeconds, 2)
                aggregatePayloadMegabytesPerSecond = [Math]::Round(($payloadBytes / $wallSeconds) / 1000000, 2)
                requests = $measurements
            }
            $result.runs += $run

            if ($successCount -ne $ConcurrentUsers) {
                throw "$successCount uploads sur $ConcurrentUsers ont réussi pour $($testCase.label)."
            }
        }
        finally {
            $wallClock.Stop()
            $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
            if ($accessToken) {
                Remove-CreatedFiles -Headers $headers
            }
            if (-not $KeepTemporaryFiles) {
                Remove-Item -LiteralPath $responsePaths -Force -ErrorAction SilentlyContinue
            }
        }
    }
}
finally {
    if ($accessToken) {
        $headers = @{ Authorization = "Bearer $accessToken" }
        Remove-CreatedFiles -Headers $headers
        try {
            $deletionBody = @{ password = $password } | ConvertTo-Json
            Invoke-RestMethod -Uri "$BaseUrl/api/users/me" -Method Delete -Headers $headers -ContentType "application/json" -Body $deletionBody | Out-Null
            $result.cleanupCompleted = $true
        }
        catch {
            Write-Warning "La suppression du compte de performance a échoué : $($_.Exception.Message)"
        }
    }

    if (-not $KeepTemporaryFiles) {
        Remove-Item -LiteralPath $sourcePath -Force -ErrorAction SilentlyContinue
        foreach ($temporaryPath in $temporaryPaths) {
            Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
        }
    }

    $result | ConvertTo-Json -Depth 8 | Set-Content -Path $OutputPath -Encoding UTF8
}

Write-Host "Test concurrent terminé. Résultat : $OutputPath"
