#requires -Version 7.2
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixture = Join-Path $PSScriptRoot ('scope-' + [Guid]::NewGuid().ToString('N') + '.sql')
$cases = @(
    @{ Sql = '-- MySQL dump 10.13' + "`n" + 'DROP TABLE production.seat;'; Expected = 'RestoreScopeRejected' },
    @{ Sql = '-- MySQL dump 10.13' + "`n" + 'SET @x=1; USE production;'; Expected = 'RestoreScopeRejected' },
    @{ Sql = '-- MySQL dump 10.13' + "`n" + '/*!50000 DROP DATABASE production */;'; Expected = 'RestoreScopeRejected' },
    @{ Sql = '-- MySQL dump 10.13' + "`n" + 'DROP TABLE `production`.`seat`;'; Expected = 'RestoreScopeRejected' }
)
try {
    foreach ($case in $cases) {
        [IO.File]::WriteAllText($fixture, $case.Sql, [Text.UTF8Encoding]::new($false))
        $output = & (Get-Process -Id $PID).Path -NoProfile -File (Join-Path $root 'scripts/restore.ps1') -Database seatkeeper -ConfirmRestore -InputFile $fixture -MysqlBin 'definitely-missing-native-bin' 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -or $output -notlike ('*' + $case.Expected + '*')) { throw ('RestoreScopeGuardFailed: ' + $case.Sql + ' returned ' + $output) }
        Write-Output 'PASS restore rejects cross-database SQL before launching a client.'
    }
} finally { Remove-Item -LiteralPath $fixture }
