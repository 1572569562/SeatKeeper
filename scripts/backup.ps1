#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Native','Docker')][string]$Mode = 'Native',
    [string]$Database, [string]$HostName = '127.0.0.1', [int]$Port = 0,
    [string]$User, [string]$MysqlBin, [string]$EnvFile,
    [Parameter(Mandatory)][string]$OutputFile
)
. (Join-Path $PSScriptRoot 'mysql-common.ps1')
if ($Database) { Assert-DatabaseName $Database }
$connection = New-MySqlConnection $Mode $Database $HostName $Port $User $MysqlBin $EnvFile
$target = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputFile)
if (Test-Path -LiteralPath $target) { throw 'BackupAlreadyExists: choose a new output path.' }
$directory = [IO.Path]::GetDirectoryName($target)
if (-not (Test-Path -LiteralPath $directory -PathType Container)) { throw 'BackupDirectoryMissing: create the destination directory first.' }
$temporary = Join-Path $directory ('.seatkeeper-backup-' + [Guid]::NewGuid().ToString('N') + '.partial')
try {
    # 不使用 --databases，恢复时不带 CREATE/USE DATABASE，也不自动选择其他库。
    Invoke-MySqlProcess $connection -Tool mysqldump -ToolArguments @('--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--skip-dump-date','--hex-blob','--routines=false','--triggers=false','--events=false') -OutputFile $temporary
    [IO.File]::Move($temporary, $target)
    Write-Output ('Backup created: ' + $target)
    Get-FileHash -LiteralPath $target -Algorithm SHA256 | Select-Object Algorithm, Hash, Path
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
