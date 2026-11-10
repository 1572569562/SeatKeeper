#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Native','Docker')][string]$Mode = 'Native',
    [string]$Database = 'seatkeeper',
    [string]$HostName = '127.0.0.1', [int]$Port = 0,
    [string]$User, [string]$MysqlBin, [string]$EnvFile,
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$DemoDate = '2030-01-15',
    [switch]$ConfirmReset
)
. (Join-Path $PSScriptRoot 'mysql-common.ps1')
if (-not $ConfirmReset) { throw 'ConfirmResetRequired: pass -ConfirmReset to replace the seven business tables.' }
Assert-DedicatedDatabase $Database
$parsedDate = [DateTime]::MinValue
if (-not [DateTime]::TryParseExact($DemoDate, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$parsedDate)) { throw 'InvalidDemoDate' }
$connection = New-MySqlConnection $Mode $Database $HostName $Port $User $MysqlBin $EnvFile
$seedPath = Join-Path $script:ProjectRoot 'seatkeeper-service/src/main/resources/db/demo/V2__demo_data.sql'
if (-not (Test-Path -LiteralPath $seedPath)) { throw 'DemoSeedMissing: run the schema migration and keep the versioned demo seed in resources/db/demo.' }
$seed = [IO.File]::ReadAllText($seedPath, [Text.Encoding]::UTF8)
# 不清除 Flyway 历史、不禁用外键；七表业务行在同一事务中删除并重放。
$sql = @"
SET NAMES utf8mb4;
SET time_zone = '+08:00';
SET @demo_date = '$DemoDate';
START TRANSACTION;
DELETE FROM credit_log;
DELETE FROM notification;
DELETE FROM credit;
DELETE FROM reservation;
DELETE FROM slot;
DELETE FROM seat;
DELETE FROM app_user;
$seed
COMMIT;
SELECT 'reset_done' AS result, @demo_date AS demo_date;
"@
Invoke-MySqlProcess $connection -ToolArguments @('--binary-mode=1','--batch','--raw') -InputText $sql
Write-Output ('Reset completed: ' + $Database + '; fixed date ' + $DemoDate + '; Flyway history preserved.')
