#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Native','Docker')][string]$Mode = 'Native',
    [string]$Database, [string]$HostName = '127.0.0.1', [int]$Port = 0,
    [string]$User, [string]$MysqlBin, [string]$EnvFile,
    [long]$UserId = 10001, [long]$ReservationId = 10002,
    [long]$LastNotificationId = 0,
    [string]$OutputFile
)
. (Join-Path $PSScriptRoot 'mysql-common.ps1')
$connection = New-MySqlConnection $Mode $Database $HostName $Port $User $MysqlBin $EnvFile
if ($UserId -le 0 -or $ReservationId -le 0 -or $LastNotificationId -lt 0) { throw 'InvalidExplainId' }
$sql = @"
SET NAMES utf8mb4;
SET time_zone = '+08:00';
SELECT VERSION() AS mysql_version, DATABASE() AS database_name, NOW(6) AS checked_at;
SELECT 'catalog_seats_room_floor' AS query_name;
EXPLAIN SELECT id, room, floor, seat_no, status FROM seat WHERE room = '第一阅览室' AND floor = 1 ORDER BY room, floor, seat_no, id;
SELECT 'catalog_seats_all' AS query_name;
EXPLAIN SELECT id, room, floor, seat_no, status FROM seat ORDER BY room, floor, seat_no, id;
SELECT 'seat_filter' AS query_name;
EXPLAIN SELECT id, room, floor, seat_no, status FROM seat WHERE room='第一阅览室' AND floor=1 AND status='AVAILABLE' ORDER BY id LIMIT 50;
SELECT 'active_reservation' AS query_name;
EXPLAIN SELECT id, user_id, seat_id, slot_start, slot_end, status FROM reservation WHERE active_seat_id=10001 AND slot_start='2030-01-15 08:00:00';
SELECT 'notification_cursor' AS query_name;
EXPLAIN SELECT id, user_id, type, content, read_flag, created_at FROM notification WHERE user_id=$UserId AND id>$LastNotificationId ORDER BY id LIMIT 50;
SELECT 'credit_idempotency' AS query_name;
EXPLAIN SELECT id, user_id, reservation_id, delta, reason, balance_after FROM credit_log WHERE reservation_id=$ReservationId AND reason='NO_SHOW';
"@
$result = Invoke-MySqlProcess $connection -ToolArguments @('--table','--batch','--raw') -InputText $sql
if ($OutputFile) {
    $target = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputFile)
    if (Test-Path -LiteralPath $target) { throw 'ExplainOutputAlreadyExists: choose a new output path.' }
    [IO.File]::WriteAllText($target, $result, [Text.UTF8Encoding]::new($false))
}
Write-Output $result
