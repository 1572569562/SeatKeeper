<#
.SYNOPSIS
    SeatKeeper 接口冒烟测试。默认只测“不需要账号”的部分，可在拿到演示账号后追加登录与预约链路。

.DESCRIPTION
    为什么先测这些：
      1. 服务能不能起来、数据库通不通 —— 健康检查
      2. 三个只读接口能不能返回 —— 座位、时段
      3. 鉴权是不是真的在拦 —— 未登录、错误密码、内部接口
    这几条不需要种子账号，任何人拉下代码都能跑。

.EXAMPLE
    pwsh -File scripts/smoke.ps1

.EXAMPLE
    pwsh -File scripts/smoke.ps1 -BaseUrl http://192.168.10.10:8080

.EXAMPLE
    # 拿到演示账号后，连预约链路一起测
    pwsh -File scripts/smoke.ps1 -StudentNo 20230101 -Password Passw0rd!
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$InternalToken = '',
    [string]$StudentNo = '',
    [string]$Password = '',
    [int]$TimeoutSec = 5
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0
$script:skipped = 0

function Write-Step([string]$name) {
    Write-Host ""
    Write-Host "==> $name" -ForegroundColor Cyan
}

function Assert-Code([string]$name, [scriptblock]$action, [int[]]$expect) {
    try {
        $resp = & $action
        $code = [int]$resp.StatusCode
        if ($expect -contains $code) {
            Write-Host ("  [PASS] {0} -> {1}" -f $name, $code) -ForegroundColor Green
            $script:passed++
        }
        else {
            Write-Host ("  [FAIL] {0} -> {1}，期望 {2}" -f $name, $code, ($expect -join '/')) -ForegroundColor Red
            $script:failed++
        }
        return $resp
    }
    catch {
        # PowerShell 5.1 与 7 在非 2xx 时的异常结构不同，两种都兼容
        $code = 0
        $resp = $_.Exception.Response
        if ($resp) {
            try { $code = [int]$resp.StatusCode } catch { }
            if ($code -eq 0) {
                try { $code = [int]$resp.StatusCode.value__ } catch { }
            }
        }
        if ($expect -contains $code) {
            Write-Host ("  [PASS] {0} -> {1}" -f $name, $code) -ForegroundColor Green
            $script:passed++
        }
        else {
            Write-Host ("  [FAIL] {0} -> 异常：{1}" -f $name, $_.Exception.Message) -ForegroundColor Red
            $script:failed++
        }
        return $null
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = '',
        [string]$Internal = ''
    )
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($Internal) { $headers['X-Internal-Token'] = $Internal }

    $params = @{
        Uri        = "$BaseUrl$Path"
        Method     = $Method
        Headers    = $headers
        TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = ($Body | ConvertTo-Json -Compress -Depth 5)
    }
    Invoke-WebRequest @params
}

Write-Host "SeatKeeper 冒烟测试" -ForegroundColor White
Write-Host "BaseUrl = $BaseUrl"

# ---------------------------------------------------------------- 1. 存活
Write-Step '1. 服务存活'
$health = Assert-Code 'GET /api/v1/health' { Invoke-Api GET '/api/v1/health' } @(200)
if ($health -and $health.Content) {
    $json = $health.Content | ConvertFrom-Json
    $dbState = $json.data.database
    if ($dbState) {
        if ($dbState -eq 'UP') {
            Write-Host "  [PASS] 数据库连接 = UP" -ForegroundColor Green
            $script:passed++
        }
        else {
            Write-Host "  [FAIL] 数据库连接 = $dbState" -ForegroundColor Red
            $script:failed++
        }
    }
}

# ---------------------------------------------------------------- 2. 只读接口
Write-Step '2. 只读查询（不需要登录）'
Assert-Code 'GET /api/v1/seats' { Invoke-Api GET '/api/v1/seats?room=&floor=' } @(200) | Out-Null
Assert-Code 'GET /api/v1/slots' { Invoke-Api GET '/api/v1/slots' } @(200) | Out-Null

# ---------------------------------------------------------------- 3. 鉴权
Write-Step '3. 鉴权是否生效'
Assert-Code '未带 token 访问 /reservations' { Invoke-Api GET '/api/v1/reservations' } @(401, 403) | Out-Null
Assert-Code '错误密码登录' {
    Invoke-Api POST '/api/v1/auth/login' -Body @{ studentNo = 'NOT_EXIST'; password = 'wrong' }
} @(401, 403) | Out-Null

$internalExpect = @(401, 403)
Assert-Code '内部接口无 Token' {
    Invoke-Api POST '/api/v1/internal/reservations/expire-batch' -Body @{ limit = 10 }
} $internalExpect | Out-Null

# ---------------------------------------------------------------- 4. 可选：登录 + 预约链路
if ($StudentNo -and $Password) {
    Write-Step '4. 登录与预约链路'
    $login = Assert-Code '登录' {
        Invoke-Api POST '/api/v1/auth/login' -Body @{ studentNo = $StudentNo; password = $Password }
    } @(200)

    $token = $null
    if ($login) {
        $obj = $login.Content | ConvertFrom-Json
        $token = $obj.data.token
        if (-not $token) { $token = $obj.data.accessToken }
    }

    if (-not $token) {
        Write-Host '  [SKIP] 没拿到 token，跳过后续链路' -ForegroundColor Yellow
        $script:skipped += 2
    }
    else {
        Assert-Code '带 token 查我的预约' { Invoke-Api GET '/api/v1/reservations' -Token $token } @(200) | Out-Null
        Assert-Code '带 token 查通知' { Invoke-Api GET '/api/v1/notifications' -Token $token } @(200) | Out-Null
    }

    if ($InternalToken) {
        Write-Step '4b. 内部接口（带 Token）'
        Assert-Code 'expire-batch' {
            Invoke-Api POST '/api/v1/internal/reservations/expire-batch' -Body @{ limit = 10 } -Internal $InternalToken
        } @(200) | Out-Null
    }
    else {
        Write-Host "  [SKIP] 未提供 -InternalToken，跳过内部接口正向测试" -ForegroundColor Yellow
        $script:skipped++
    }
}
else {
    Write-Host ""
    Write-Host "  [SKIP] 未提供 -StudentNo / -Password，跳过登录与预约链路" -ForegroundColor Yellow
    $script:skipped += 3
}

# ---------------------------------------------------------------- 汇总
Write-Host ""
Write-Host ("结果：通过 {0}，失败 {1}，跳过 {2}" -f $script:passed, $script:failed, $script:skipped) -ForegroundColor White

if ($script:failed -gt 0) {
    Write-Host '冒烟测试未通过。' -ForegroundColor Red
    exit 1
}
Write-Host '冒烟测试通过。' -ForegroundColor Green
exit 0
