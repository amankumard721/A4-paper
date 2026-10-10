param([string]$PrivateDirectory = (Join-Path $PSScriptRoot '..\..\Play-Store-Release\private'))
$ErrorActionPreference = 'Stop'
$credentials = Get-Content -Raw -LiteralPath (Join-Path $PrivateDirectory 'upload-credentials.json') | ConvertFrom-Json
$env:LEKHAK_UPLOAD_STORE = (Resolve-Path -LiteralPath (Join-Path $PrivateDirectory 'a4-lekhak-upload.p12')).Path
$env:LEKHAK_UPLOAD_STORE_PASSWORD = $credentials.storePassword
$env:LEKHAK_UPLOAD_KEY_ALIAS = $credentials.alias
$env:LEKHAK_UPLOAD_KEY_PASSWORD = $credentials.storePassword
try {
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed; inspect the build log.' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:LEKHAK_UPLOAD_STORE,Env:LEKHAK_UPLOAD_STORE_PASSWORD,Env:LEKHAK_UPLOAD_KEY_ALIAS,Env:LEKHAK_UPLOAD_KEY_PASSWORD -ErrorAction SilentlyContinue
}
