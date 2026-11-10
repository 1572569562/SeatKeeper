#requires -Version 7.2
$ErrorActionPreference = 'Stop'
$fixtureName = 'relative-' + [Guid]::NewGuid().ToString('N')
$fixturePath = Join-Path $PSScriptRoot $fixtureName
$filePath = Join-Path $fixturePath 'existing.sql'
New-Item -ItemType Directory -Path $fixturePath | Out-Null
[IO.File]::WriteAllText($filePath, 'existing backup', [Text.UTF8Encoding]::new($false))
try {
    Push-Location $PSScriptRoot
    try {
        $caught = ''
        try { & (Join-Path (Split-Path $PSScriptRoot -Parent) 'backup.ps1') -Database seatkeeper -OutputFile ($fixtureName + '/existing.sql') }
        catch { $caught = $_.Exception.Message }
        if ($caught -notlike '*BackupAlreadyExists*') { throw ('RelativePathResolutionFailed: expected BackupAlreadyExists, got ' + $caught) }
        Write-Output 'PASS backup resolves relative output against the PowerShell working location.'
    } finally { Pop-Location }
} finally {
    Remove-Item -LiteralPath $filePath
    Remove-Item -LiteralPath $fixturePath
}
