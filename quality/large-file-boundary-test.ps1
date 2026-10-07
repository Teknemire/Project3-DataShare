param(
    [string]$BaseUrl = "http://localhost:4200",
    [string]$OutputPath = "$PSScriptRoot\large-file-boundary-result.json",
    [switch]$ConfirmLocalStorage,
    [switch]$KeepTemporaryFiles
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmLocalStorage) {
    throw "Ce test transfère environ 3 Go. Relancez-le avec -ConfirmLocalStorage après avoir vérifié que STORAGE_TYPE=local."
}
$boundaryBytes = 1000000000L
$sourcePath = Join-Path $PSScriptRoot "boundary-1gb.txt"
$downloadPath = Join-Path $PSScriptRoot "boundary-1gb-downloaded.txt"
$uploadResponsePath = Join-Path $PSScriptRoot "boundary-upload-response.json"
$overLimitResponsePath = Join-Path $PSScriptRoot "over-limit-upload-response.json"
$email = "large-file-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@datashare.test"
$password = "LargeFile123!"
$accessToken = $null
$uploadedFileId = $null
$result = [ordered]@{
    measuredAt = [DateTimeOffset]::UtcNow.ToString("o")
    baseUrl = $BaseUrl
    storage = "local"
    boundaryBytes = $boundaryBytes
    exactBoundary = $null
    overBoundary = $null
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

function Invoke-MeasuredUpload(
    [string]$FilePath,
    [string]$ResponsePath,
    [string]$Token
) {
    $writeOut = '{"httpCode":%{http_code},"timeTotalSeconds":%{time_total},"sizeUpload":%{size_upload},"speedUpload":%{speed_upload}}'
    $curlArguments = @(
        '--silent',
        '--show-error',
        '--output', $ResponsePath,
        '--write-out', $writeOut,
        '--header', "Authorization: Bearer $Token",
        '--form', "file=@$FilePath;type=text/plain",
        '--form', 'expirationDays=1',
        '--form', 'tags=Limite1Go',
        "$BaseUrl/api/files"
    )

    $measurement = & curl.exe @curlArguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl a échoué pendant l'upload avec le code $LASTEXITCODE."
    }
    return $measurement | ConvertFrom-Json
}

function Invoke-MeasuredDownload([string]$Url, [string]$Destination) {
    $writeOut = '{"httpCode":%{http_code},"timeTotalSeconds":%{time_total},"sizeDownload":%{size_download},"speedDownload":%{speed_download}}'
    $curlArguments = @(
        '--silent',
        '--show-error',
        '--output', $Destination,
        '--write-out', $writeOut,
        $Url
    )

    $measurement = & curl.exe @curlArguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl a échoué pendant le téléchargement avec le code $LASTEXITCODE."
    }
    return $measurement | ConvertFrom-Json
}

try {
    Write-Host "Création du compte de test..."
    $registrationBody = @{ email = $email; password = $password } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $registrationBody | Out-Null
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $registrationBody
    $accessToken = $login.accessToken
    $headers = @{ Authorization = "Bearer $accessToken" }

    Write-Host "Création du fichier exact de $boundaryBytes octets..."
    New-SizedFile -Path $sourcePath -Length $boundaryBytes

    Write-Host "Upload à la limite exacte..."
    $exactUpload = Invoke-MeasuredUpload -FilePath $sourcePath -ResponsePath $uploadResponsePath -Token $accessToken
    $uploadResponse = Get-Content $uploadResponsePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $uploadedFileId = $uploadResponse.id
    if ($exactUpload.httpCode -ne 201 -or -not $uploadedFileId) {
        throw "L'upload à la limite devait retourner 201. Réponse: $(Get-Content $uploadResponsePath -Raw)"
    }

    $shareToken = $uploadResponse.shareUrl.Substring($uploadResponse.shareUrl.LastIndexOf('/') + 1)
    $downloadAccess = Invoke-RestMethod -Uri "$BaseUrl/api/shares/$shareToken/download" -Method Post -ContentType "application/json" -Body '{}'
    $downloadUrl = $downloadAccess.downloadUrl
    if ($downloadUrl.StartsWith('/')) {
        $downloadUrl = "$BaseUrl$downloadUrl"
    }

    Write-Host "Téléchargement en flux et vérification SHA-256..."
    $download = Invoke-MeasuredDownload -Url $downloadUrl -Destination $downloadPath
    $sourceHash = (Get-FileHash -Path $sourcePath -Algorithm SHA256).Hash
    $downloadHash = (Get-FileHash -Path $downloadPath -Algorithm SHA256).Hash
    $downloadedBytes = (Get-Item $downloadPath).Length
    $hashesMatch = $sourceHash -eq $downloadHash

    if ($download.httpCode -ne 200 -or $downloadedBytes -ne $boundaryBytes -or -not $hashesMatch) {
        throw "Le téléchargement ne correspond pas au fichier envoyé."
    }

    $result.exactBoundary = [ordered]@{
        accepted = $true
        uploadHttpStatus = $exactUpload.httpCode
        uploadSeconds = $exactUpload.timeTotalSeconds
        multipartBytesSent = $exactUpload.sizeUpload
        uploadBytesPerSecond = $exactUpload.speedUpload
        downloadHttpStatus = $download.httpCode
        downloadSeconds = $download.timeTotalSeconds
        downloadedBytes = $downloadedBytes
        downloadBytesPerSecond = $download.speedDownload
        sha256 = $sourceHash
        hashesMatch = $hashesMatch
    }

    Invoke-RestMethod -Uri "$BaseUrl/api/files/$uploadedFileId" -Method Delete -Headers $headers | Out-Null
    $uploadedFileId = $null

    Write-Host "Redimensionnement à $($boundaryBytes + 1) octets..."
    New-SizedFile -Path $sourcePath -Length ($boundaryBytes + 1)

    Write-Host "Upload juste au-dessus de la limite..."
    $overUpload = Invoke-MeasuredUpload -FilePath $sourcePath -ResponsePath $overLimitResponsePath -Token $accessToken
    $overResponse = Get-Content $overLimitResponsePath -Raw -Encoding UTF8 | ConvertFrom-Json

    if ($overUpload.httpCode -ne 413 -or $overResponse.code -ne 'FILE_TOO_LARGE') {
        throw "Le fichier au-dessus de la limite devait retourner 413 FILE_TOO_LARGE."
    }

    $result.overBoundary = [ordered]@{
        rejected = $true
        fileBytes = $boundaryBytes + 1
        httpStatus = $overUpload.httpCode
        errorCode = $overResponse.code
        uploadSeconds = $overUpload.timeTotalSeconds
        multipartBytesSent = $overUpload.sizeUpload
    }
}
finally {
    if ($accessToken) {
        $headers = @{ Authorization = "Bearer $accessToken" }
        if ($uploadedFileId) {
            try {
                Invoke-RestMethod -Uri "$BaseUrl/api/files/$uploadedFileId" -Method Delete -Headers $headers | Out-Null
            }
            catch {
                Write-Warning "Le nettoyage explicite du fichier a échoué ; la suppression du compte va être tentée."
            }
        }
        try {
            $deletionBody = @{ password = $password } | ConvertTo-Json
            Invoke-RestMethod -Uri "$BaseUrl/api/users/me" -Method Delete -Headers $headers -ContentType "application/json" -Body $deletionBody | Out-Null
            $result.cleanupCompleted = $true
        }
        catch {
            Write-Warning "La suppression du compte de test a échoué : $($_.Exception.Message)"
        }
    }

    if (-not $KeepTemporaryFiles) {
        Remove-Item -LiteralPath $sourcePath, $downloadPath, $uploadResponsePath, $overLimitResponsePath -Force -ErrorAction SilentlyContinue
    }

    $result | ConvertTo-Json -Depth 5 | Set-Content -Path $OutputPath -Encoding UTF8
}

Write-Host "Test terminé. Résultat : $OutputPath"
