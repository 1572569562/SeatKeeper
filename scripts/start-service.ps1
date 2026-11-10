#requires -Version 7.2
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME, [string]$EnvFile, [switch]$Demo)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'mysql-common.ps1')
[void](Import-ProjectEnvironment $EnvFile)
$taskJava = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$taskVersion = (& $taskJava --version) -join "`n"
if ($LASTEXITCODE -ne 0 -or $taskVersion -notmatch '^(?:java|openjdk) 17(?:\.| )') {
    throw '本项目统一使用JDK17，请通过-JavaHome指定JDK17目录。'
}
$taskJar = Join-Path $script:ProjectRoot 'seatkeeper-service/target/seatkeeper-service-1.0.0-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $taskJar)) { throw '尚未打包，请先在项目根目录执行mvn clean verify。' }
$taskArguments = @('-jar', $taskJar)
if ($Demo) { $taskArguments += '--spring.profiles.active=demo' }
& $taskJava @taskArguments
exit $LASTEXITCODE
