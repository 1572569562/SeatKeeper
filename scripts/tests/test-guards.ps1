#requires -Version 7.2
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $root 'scripts/mysql-common.ps1')
# 固定项目库名无需演示后缀，但测试库仍必须严格以测试后缀结尾。
foreach ($database in @('seatkeeper', 'seatkeeper_test', 'team14_test')) {
    Assert-DedicatedDatabase $database
    Write-Output ('PASS allowed project/test database: ' + $database)
}
# 放开固定项目库名不等于放开任意库，也不接受旧演示名和伪装测试库名。
foreach ($database in @('seatkeeper_demo', 'seatkeeper_backup', 'production', 'seatkeeper_test_backup')) {
    try {
        Assert-DedicatedDatabase $database
        throw ('UnexpectedDatabaseAllowed: ' + $database)
    } catch {
        if ($_.Exception.Message -notlike '*DedicatedDatabaseRequired*') { throw }
    }
    Write-Output ('PASS refused unrelated database: ' + $database)
}
$cases = @(
    @{ Script = 'reset-demo.ps1'; Arguments = @('-Database', 'production', '-ConfirmReset'); Message = 'DedicatedDatabaseRequired' },
    @{ Script = 'reset-demo.ps1'; Arguments = @('-Database', 'seatkeeper'); Message = 'ConfirmResetRequired' },
    @{ Script = 'restore.ps1'; Arguments = @('-Database', 'production', '-ConfirmRestore', '-InputFile', 'missing.sql'); Message = 'DedicatedDatabaseRequired' },
    @{ Script = 'restore.ps1'; Arguments = @('-Database', 'seatkeeper_test', '-InputFile', 'missing.sql'); Message = 'ConfirmRestoreRequired' },
    @{ Script = 'backup.ps1'; Arguments = @('-Database', 'x;DROP_DATABASE', '-OutputFile', 'unused.sql'); Message = 'InvalidDatabaseName' }
)
foreach ($case in $cases) {
    $script = Join-Path $root ('scripts/' + $case.Script)
    if (-not (Test-Path -LiteralPath $script)) { throw ('Missing implementation: ' + $case.Script) }
    $arguments = @('-NoProfile', '-File', $script) + $case.Arguments
    $output = & (Get-Process -Id $PID).Path @arguments 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0 -or $output -notlike ('*' + $case.Message + '*')) {
        throw ('Guard failed: ' + $case.Script + ' expected ' + $case.Message + '. ' + $output)
    }
    Write-Output ('PASS ' + $case.Script + ': ' + $case.Message)
}
Write-Output ('PASS ' + $cases.Count + ' safety guard cases; no database connection needed.')
