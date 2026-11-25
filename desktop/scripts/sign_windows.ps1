# Signs Windows release artifacts (MSI installer + manager EXE) with an
# Authenticode certificate provided through CI secrets.
#
# Secrets:
#   WINDOWS_SIGNING_CERT_PFX       base64-encoded PFX/PKCS#12 certificate bundle
#   WINDOWS_SIGNING_CERT_PASSWORD  PFX private key password
#
# When the secrets are absent the script exits successfully with a warning, so
# release builds keep working until a certificate is configured. Once a real
# (OV or EV) certificate is configured, SmartScreen warnings disappear for
# downloads that have built up enough reputation - the signature itself removes
# the "Unknown publisher" state immediately.
#
# Usage: ./sign_windows.ps1 -Files <file1> [file2] ...

param(
    [Parameter(Mandatory = $true)][string[]]$Files
)

$ErrorActionPreference = "Stop"

$certBase64 = $env:WINDOWS_SIGNING_CERT_PFX
$certPassword = $env:WINDOWS_SIGNING_CERT_PASSWORD

if ([string]::IsNullOrWhiteSpace($certBase64) -or [string]::IsNullOrWhiteSpace($certPassword)) {
    Write-Host "::warning::WINDOWS_SIGNING_CERT_PFX / WINDOWS_SIGNING_CERT_PASSWORD secrets are not set - shipping UNSIGNED Windows artifacts."
    Write-Host "::warning::Until signed, Windows SmartScreen shows 'More info > Run anyway' for Muzik installers."
    exit 0
}

foreach ($file in $Files) {
    if (-not (Test-Path $file)) {
        throw "Signing input not found: $file"
    }
}

# Locate the newest signtool.exe shipped with the Windows SDK on the runner.
$signtool =
    Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin" -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*\x64\*" } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
if (-not $signtool) {
    throw "signtool.exe not found under the Windows SDK directory"
}
Write-Host "Using signtool: $($signtool.FullName)"

# Decode the certificate to a temporary PFX and remove it right after signing.
$pfxPath = Join-Path ([System.IO.Path]::GetTempPath()) ("muzik-signing-{0}.pfx" -f [Guid]::NewGuid().ToString("N"))
try {
    # Strip whitespace so multi-line base64 secrets work too.
    $certBase64 = ($certBase64 -replace "\s", "")
    [System.IO.File]::WriteAllBytes($pfxPath, [Convert]::FromBase64String($certBase64))

    foreach ($file in $Files) {
        Write-Host "Signing $file"
        & $signtool.FullName sign `
            /fd SHA256 `
            /tr http://timestamp.digicert.com /td SHA256 `
            /f $pfxPath /p $certPassword `
            $file
        if ($LASTEXITCODE -ne 0) {
            throw "signtool failed for $file (exit code $LASTEXITCODE)"
        }
        # Verify the signature actually stuck.
        & $signtool.FullName verify /pa /all $file
        if ($LASTEXITCODE -ne 0) {
            throw "signtool verification failed for $file (exit code $LASTEXITCODE)"
        }
    }
    Write-Host "All artifacts signed successfully."
} finally {
    if (Test-Path $pfxPath) {
        Remove-Item $pfxPath -Force
    }
}
