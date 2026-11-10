#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Native','Docker')][string]$Mode = 'Native',
    [string]$Database = 'seatkeeper', [string]$HostName = '127.0.0.1', [int]$Port = 0,
    [string]$User, [string]$MysqlBin, [string]$EnvFile,
    [switch]$ConfirmReset
)
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'mysql-common.ps1')
if (-not $ConfirmReset) { throw 'ConfirmResetRequired: this integration test resets business data in a dedicated database.' }
Assert-DedicatedDatabase $Database
$connection = New-MySqlConnection $Mode $Database $HostName $Port $User $MysqlBin $EnvFile
$parameters = @{ Mode = $Mode; Database = $Database; HostName = $HostName; Port = $Port; User = $User; MysqlBin = $MysqlBin; EnvFile = $EnvFile }
$artifacts = Join-Path $PSScriptRoot ('artifacts/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
$dataArgs = @('--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--no-create-info','--skip-comments','--skip-extended-insert','--order-by-primary','--skip-add-locks','--skip-disable-keys', ('--ignore-table=' + $Database + '.flyway_schema_history'))
$historySql = 'SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;'
$historyBefore = Invoke-MySqlProcess $connection -ToolArguments @('--batch','--raw') -InputText $historySql
& (Join-Path $script:ProjectRoot 'scripts/reset-demo.ps1') @parameters -DemoDate '2030-01-15' -ConfirmReset
$firstData = Invoke-MySqlProcess $connection -Tool mysqldump -ToolArguments $dataArgs
& (Join-Path $script:ProjectRoot 'scripts/reset-demo.ps1') @parameters -DemoDate '2030-01-15' -ConfirmReset
$secondData = Invoke-MySqlProcess $connection -Tool mysqldump -ToolArguments $dataArgs
if ($firstData -cne $secondData) { throw 'RepeatedResetMismatch: business data differs after two fixed-date resets.' }
$historyAfter = Invoke-MySqlProcess $connection -ToolArguments @('--batch','--raw') -InputText $historySql
if ($historyBefore -cne $historyAfter) { throw 'FlywayHistoryChanged: reset must preserve every history field.' }
Write-Output 'PASS repeated reset: seven-table SQL data identical; full Flyway history unchanged.'
$backup = Join-Path $artifacts 'demo-roundtrip.sql'
& (Join-Path $script:ProjectRoot 'scripts/backup.ps1') @parameters -OutputFile $backup
Invoke-MySqlProcess $connection -ToolArguments @('--batch','--raw') -InputText "UPDATE notification SET content='临时修改：恢复后必须撤销。' WHERE id=10001;" | Out-Null
& (Join-Path $script:ProjectRoot 'scripts/restore.ps1') @parameters -InputFile $backup -ConfirmRestore
$restoredData = Invoke-MySqlProcess $connection -Tool mysqldump -ToolArguments $dataArgs
if ($restoredData -cne $secondData) { throw 'BackupRestoreMismatch: restored business data differs from the fixed-date baseline.' }
$restoredHistory = Invoke-MySqlProcess $connection -ToolArguments @('--batch','--raw') -InputText $historySql
if ($historyBefore -cne $restoredHistory) { throw 'RestoredFlywayHistoryMismatch' }
Write-Output 'PASS backup/restore: all seven business tables and full Flyway history identical, including Chinese text.'
$counts = Invoke-MySqlProcess $connection -ToolArguments @('--batch','--raw') -InputText "SELECT 'app_user' AS table_name,COUNT(*) AS row_count FROM app_user UNION ALL SELECT 'seat',COUNT(*) FROM seat UNION ALL SELECT 'slot',COUNT(*) FROM slot UNION ALL SELECT 'reservation',COUNT(*) FROM reservation UNION ALL SELECT 'credit',COUNT(*) FROM credit UNION ALL SELECT 'credit_log',COUNT(*) FROM credit_log UNION ALL SELECT 'notification',COUNT(*) FROM notification;"
Write-Output $counts
Write-Output ('Artifacts: ' + $artifacts)
