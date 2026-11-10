#requires -Version 7.2
# 共用连接层：密码仅传入子进程环境，不写进参数、日志或 SQL。
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path $PSScriptRoot -Parent

# 读取项目环境文件的字面值，保留已有进程变量，不执行文件中的表达式或命令。
function Import-ProjectEnvironment {
    param([string]$EnvFile)
    if (-not $EnvFile) { $EnvFile = Join-Path $script:ProjectRoot '.env' }
    if (Test-Path -LiteralPath $EnvFile) {
        foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile), [Text.Encoding]::UTF8)) {
            $line = $line.Trim()
            if (-not $line -or $line.StartsWith('#')) { continue }
            if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'InvalidDotEnvLine: use KEY=value without shell expansion.' }
            $key = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            # 已有环境变量优先；dotenv 只接受字面量，不执行变量替换和命令。
            if ($null -eq [Environment]::GetEnvironmentVariable($key, 'Process')) {
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
        return (Resolve-Path -LiteralPath $EnvFile).Path
    }
    return $null
}

# 校验数据库标识符格式，阻止名称拼接产生越界访问或注入。
function Assert-DatabaseName {
    param([string]$Database)
    if ($Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') { throw 'InvalidDatabaseName: use 1-64 ASCII letters, digits and underscores; start with a letter.' }
}

# 恢复和重置只接受本项目固定库名或专用测试库，拒绝其他业务库名称。
function Assert-DedicatedDatabase {
    param([string]$Database)
    Assert-DatabaseName $Database
    if ($Database -cne 'seatkeeper' -and $Database -cnotmatch '_test$') { throw 'DedicatedDatabaseRequired: restore/reset only accept seatkeeper or names ending in _test.' }
}

# 合并环境与显式参数，校验账号、端口和库名，构造统一的连接配置对象。
function New-MySqlConnection {
    param([string]$Mode, [string]$Database, [string]$HostName, [int]$Port, [string]$User, [string]$MysqlBin, [string]$EnvFile)
    $resolvedEnv = Import-ProjectEnvironment $EnvFile
    if (-not $Database) { $Database = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'seatkeeper' } }
    Assert-DatabaseName $Database
    if (-not $User) { $User = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'seatkeeper' } }
    if ($User -notmatch '^[A-Za-z0-9_]+$') { throw 'InvalidMySqlUser: use ASCII letters, digits and underscores.' }
    if ($Port -eq 0) { $Port = if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 } }
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'InvalidPort: expected 1..65535.' }
    if (-not $MysqlBin) { $MysqlBin = $env:MYSQL_BIN }
    return [pscustomobject]@{ Mode = $Mode; Database = $Database; HostName = $HostName; Port = $Port; User = $User; MysqlBin = $MysqlBin; EnvFile = $resolvedEnv }
}

# 以本机或容器方式调用数据库工具，密码通过环境传递，输入输出按UTF-8字节处理。
function Invoke-MySqlProcess {
    param($Connection, [ValidateSet('mysql','mysqldump')][string]$Tool = 'mysql', [string[]]$ToolArguments = @(), [string]$InputText, [string]$InputFile, [string]$OutputFile)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    $info.WorkingDirectory = $script:ProjectRoot
    $args = [Collections.Generic.List[string]]::new()
    if ($Connection.Mode -eq 'Docker') {
        $info.FileName = (Get-Command docker -ErrorAction Stop).Source
        $args.Add('compose')
        if ($Connection.EnvFile) { $args.Add('--env-file'); $args.Add($Connection.EnvFile) }
        $args.Add('-f'); $args.Add((Join-Path $script:ProjectRoot 'deploy/compose.mysql.yml'))
        $args.Add('exec'); $args.Add('-T'); $args.Add('mysql'); $args.Add('sh'); $args.Add('-c')
        # 容器内使用它现有的环境变量；密码不经过宿主命令参数。
        $args.Add('if [ "$1" = root ]; then export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; else export MYSQL_PWD="$MYSQL_PASSWORD"; fi; shift; exec "$@"')
        $args.Add('sh'); $args.Add($Connection.User); $args.Add($Tool)
        $args.Add('--host=127.0.0.1'); $args.Add('--port=3306')
    } else {
        $info.FileName = if ($Connection.MysqlBin) { Join-Path $Connection.MysqlBin ($Tool + '.exe') } else { (Get-Command $Tool -ErrorAction Stop).Source }
        $args.Add('--host=' + $Connection.HostName); $args.Add('--port=' + $Connection.Port)
        $password = if ($null -ne $env:MYSQL_PWD) { $env:MYSQL_PWD } elseif ($Connection.User -eq 'root') { $env:MYSQL_ROOT_PASSWORD } else { $env:MYSQL_PASSWORD }
        if ($null -ne $password) { $info.Environment['MYSQL_PWD'] = $password } else { $info.Environment.Remove('MYSQL_PWD') | Out-Null }
    }
    $args.Add('--protocol=TCP'); $args.Add('--user=' + $Connection.User); $args.Add('--default-character-set=utf8mb4')
    foreach ($arg in $ToolArguments) { $args.Add($arg) }
    $args.Add($Connection.Database)
    foreach ($arg in $args) { $info.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $output = $null
    $inputStream = $null
    try {
        if ($OutputFile) { $output = [IO.File]::Open($OutputFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None) }
        else { $output = [IO.MemoryStream]::new() }
        if (-not $process.Start()) { throw 'MySqlProcessStartFailed' }
        # stdout 使用原始字节异步复制，避免 PowerShell 管道改写编码或死锁。
        $copyTask = $process.StandardOutput.BaseStream.CopyToAsync($output)
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($InputFile) {
            $inputStream = [IO.File]::OpenRead((Resolve-Path -LiteralPath $InputFile))
            $inputStream.CopyTo($process.StandardInput.BaseStream)
        } elseif ($null -ne $InputText) {
            $bytes = [Text.UTF8Encoding]::new($false).GetBytes($InputText)
            $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
        }
        $process.StandardInput.Close()
        $process.WaitForExit()
        $copyTask.GetAwaiter().GetResult() | Out-Null
        $errors = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw ('MySqlCommandFailed (' + $process.ExitCode + '): ' + $errors.Trim()) }
        if ($errors.Trim()) { Write-Warning $errors.Trim() }
        if (-not $OutputFile) { return [Text.Encoding]::UTF8.GetString($output.ToArray()) }
    } finally {
        if ($inputStream) { $inputStream.Dispose() }
        if ($output) { $output.Dispose() }
        $process.Dispose()
    }
}
