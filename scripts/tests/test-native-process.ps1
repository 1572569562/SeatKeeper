#requires -Version 7.2
[CmdletBinding()]
param([string]$MysqlBin)
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'mysql-common.ps1')
$connection = New-MySqlConnection -Mode Native -Database seatkeeper -HostName 127.0.0.1 -Port 33416 -User root -MysqlBin $MysqlBin
foreach ($tool in @('mysql','mysqldump')) {
    # --version 不建立数据库连接；返回值必须只有工具的 UTF-8 stdout 文本。
    $output = Invoke-MySqlProcess $connection -Tool $tool -ToolArguments @('--version')
    if ($output -isnot [string] -or $output -notmatch '8\.0\.' -or $output -match 'VoidTaskResult') { throw ('UnexpectedProcessOutput: ' + $tool + ' must return one stdout string.') }
    Write-Output ('PASS process stdout ' + $tool)
}
