#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Native','Docker')][string]$Mode = 'Native',
    [Parameter(Mandatory)][string]$Database,
    [string]$HostName = '127.0.0.1', [int]$Port = 0,
    [string]$User, [string]$MysqlBin, [string]$EnvFile,
    [Parameter(Mandatory)][string]$InputFile,
    [switch]$ConfirmRestore
)
. (Join-Path $PSScriptRoot 'mysql-common.ps1')
if (-not $ConfirmRestore) { throw 'ConfirmRestoreRequired: pass -ConfirmRestore to replace tables in the dedicated database.' }
Assert-DedicatedDatabase $Database
if (-not (Test-Path -LiteralPath $InputFile -PathType Leaf)) { throw 'RestoreInputMissing' }
$connection = New-MySqlConnection $Mode $Database $HostName $Port $User $MysqlBin $EnvFile
# 只接受本项目单库备份；拒绝库切换、系统管理或客户端命令，避免误恢复跨库脚本。
$sql = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $InputFile), [Text.UTF8Encoding]::new($false, $true))
if ($sql -notmatch '(?m)^-- MySQL dump ') { throw 'RestoreFormatRejected: expected a trusted backup.ps1 mysqldump file.' }
# 屏蔽数据字符串和普通注释，但保留 MySQL 可执行注释的 SQL；避免把通知内容当命令。
$tokenPattern = '(?s)''(?:\\.|''''|[^''\\])*''|"(?:\\.|""|[^"\\])*"|/\*!\d*\s*(.*?)\*/|/\*.*?\*/|(?m:--[ \t][^\r\n]*|#[^\r\n]*)'
$scopeSql = [regex]::Replace($sql, $tokenPattern, [Text.RegularExpressions.MatchEvaluator]{
    param($match)
    if ($match.Groups[1].Success) { return $match.Groups[1].Value }
    return ' '
})
$scopeSql = [regex]::Replace($scopeSql, '(?s)''(?:\\.|''''|[^''\\])*''|"(?:\\.|""|[^"\\])*"', ' ')
# 会话变量允许 @@SESSION.name；其余带点的标识符都是此单库备份不需要的跨库引用。
$scopeSql = [regex]::Replace($scopeSql, '(?i)@@(?:SESSION|GLOBAL|LOCAL)\s*\.\s*[A-Za-z_][A-Za-z0-9_]*', ' ')
$adminPattern = '(?im)(?:^|;)\s*(USE\b|(?:CREATE|DROP|ALTER)\s+(?:DATABASE|SCHEMA|USER)\b|GRANT\b|REVOKE\b|SOURCE\b|SYSTEM\b|PREPARE\b|EXECUTE\b|DELIMITER\b|\\[.!])'
$qualifiedPattern = '(?<![@A-Za-z0-9_$])(?:`(?:``|[^`])+`|[A-Za-z_][A-Za-z0-9_$]*)\s*\.\s*(?:`|[A-Za-z_])'
if ($scopeSql -match $adminPattern -or $scopeSql -match $qualifiedPattern) {
    throw 'RestoreScopeRejected: database switching and cross-schema statements are not accepted.'
}
# 直接复制 UTF-8 文件原始字节至 stdin；不用 Get-Content | mysql。
Invoke-MySqlProcess $connection -ToolArguments @('--binary-mode=1','--batch','--raw') -InputFile $InputFile | Out-Null
Write-Output ('Restore completed in dedicated database: ' + $Database)
