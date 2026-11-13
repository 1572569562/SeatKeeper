#requires -Version 7.2
<#
.SYNOPSIS
    成员 3 服务端自检脚本：登录、查询、预约、签到、批处理、管理端一次跑完。
.DESCRIPTION
    需要服务已启动（默认 http://127.0.0.1:8080）。脚本只读+写业务数据各一次，
    预约冲突、签到窗口未到等情况会记为 SKIP 而不是 FAIL。
.PARAMETER BaseUrl
    服务地址，默认本机 8080。
.PARAMETER StudentNo
    学生学号，默认演示账号 20230101。
.PARAMETER Password
    学生密码，默认 Demo@123456。
.PARAMETER AdminNo
    管理员学号，默认 90000001。
.PARAMETER InternalToken
    内部接口共享密钥，默认取环境变量 INTERNAL_TOKEN，再退回开发默认值。
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$StudentNo = '20230101',
    [string]$Password = 'Demo@123456',
    [string]$AdminNo = '90000001',
    [string]$InternalToken = $(if ($env:INTERNAL_TOKEN) { $env:INTERNAL_TOKEN } else { 'dev-internal-token-change-me' })
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Results = [System.Collections.Generic.List[object]]::new()

function Add-Result {
    param([string]$Name, [string]$Status, [string]$Detail)
    $script:Results.Add([pscustomobject]@{ Name = $Name; Status = $Status; Detail = $Detail })
    $color = switch ($Status) { 'PASS' { 'Green' } 'SKIP' { 'Yellow' } default { 'Red' } }
    Write-Host ("[{0}] {1} — {2}" -f $Status, $Name, $Detail) -ForegroundColor $color
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body,
        [hashtable]$ExtraHeaders
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($ExtraHeaders) { foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] } }
    $parameters = @{
        Uri = $BaseUrl + $Path
        Method = $Method
        Headers = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters['ContentType'] = 'application/json; charset=utf-8'
        $parameters['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 5))
    }
    $response = Invoke-WebRequest @parameters
    $parsed = $null
    if ($response.Content) {
        try { $parsed = $response.Content | ConvertFrom-Json } catch { $parsed = $null }
    }
    [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $parsed }
}

function Get-Code {
    param($Response)
    if ($null -eq $Response.Body) { return '' }
    if ($Response.Body.PSObject.Properties.Name -contains 'code') { return [string]$Response.Body.code }
    return ''
}

Write-Host "=== SeatKeeper 成员3 自检：$BaseUrl ===" -ForegroundColor Cyan

# 1. 健康检查（公开）
$health = Invoke-Api -Method Get -Path '/api/v1/health'
if ($health.Status -eq 200 -and (Get-Code $health) -eq 'OK') {
    Add-Result '健康检查' 'PASS' "database=$($health.Body.data.database)"
} else {
    Add-Result '健康检查' 'FAIL' "HTTP $($health.Status)"
}

# 2. 未带令牌必须 401
$anonymous = Invoke-Api -Method Get -Path '/api/v1/seats'
if ($anonymous.Status -eq 401) {
    Add-Result '未认证拦截' 'PASS' "code=$(Get-Code $anonymous)"
} else {
    Add-Result '未认证拦截' 'FAIL' "期望 401，实际 $($anonymous.Status)"
}

# 3. 学生登录
$login = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ studentNo = $StudentNo; password = $Password }
$studentToken = $null
if ($login.Status -eq 200 -and (Get-Code $login) -eq 'OK') {
    $studentToken = $login.Body.data.token
    Add-Result '学生登录' 'PASS' "role=$($login.Body.data.role) credit=$($login.Body.data.credit)"
} else {
    Add-Result '学生登录' 'FAIL' "HTTP $($login.Status)"
}

if (-not $studentToken) {
    Write-Host '登录失败，后续用例跳过。' -ForegroundColor Red
} else {
    # 4. 座位列表
    $seats = Invoke-Api -Method Get -Path '/api/v1/seats' -Token $studentToken
    if ($seats.Status -eq 200) {
        Add-Result '座位查询' 'PASS' "count=$(@($seats.Body.data).Count)"
    } else {
        Add-Result '座位查询' 'FAIL' "HTTP $($seats.Status)"
    }

    # 5. 找一个尚未开始的时段
    $slot = $null
    $date = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
    foreach ($candidateDate in @((Get-Date).ToString('yyyy-MM-dd'), $date)) {
        $slots = Invoke-Api -Method Get -Path "/api/v1/slots?date=$candidateDate" -Token $studentToken
        if ($slots.Status -eq 200) {
            $slot = $slots.Body.data | Where-Object { [datetimeoffset]$_.startTime -gt [datetimeoffset]::Now } | Select-Object -First 1
            if ($slot) { break }
        }
    }
    if (-not $slot) {
        Add-Result '时段查询' 'SKIP' '没有未来时段（先跑 scripts/reset-demo.ps1 造数据）'
    } else {
        Add-Result '时段查询' 'PASS' "$($slot.startTime)"

        # 6. 预约一个可用座位：按顺序尝试，跳过已被占用的
        $slotStartText = ([datetimeoffset]$slot.startTime).ToString('yyyy-MM-ddTHH:mm:sszzz')
        $available = @($seats.Body.data | Where-Object { $_.status -eq 'AVAILABLE' })
        $create = $null
        $seat = $null
        foreach ($candidate in ($available | Select-Object -First 5)) {
            $seat = $candidate
            $create = Invoke-Api -Method Post -Path '/api/v1/reservations' -Token $studentToken -Body @{ seatId = $candidate.id; slotStart = $slotStartText }
            if ($create.Status -eq 201 -or $create.Status -ne 409) { break }
        }
        if (-not $seat) {
            Add-Result '创建预约' 'SKIP' '没有可用座位'
        } elseif ($create.Status -eq 201) {
            Add-Result '创建预约' 'PASS' "id=$($create.Body.data.id) seat=$($create.Body.data.seatNo)"
            $reservationId = $create.Body.data.id

            # 7. 同座位重复预约必须 409
            $duplicate = Invoke-Api -Method Post -Path '/api/v1/reservations' -Token $studentToken -Body @{ seatId = $seat.id; slotStart = $slotStartText }
            if ($duplicate.Status -eq 409 -and (Get-Code $duplicate) -eq 'SEAT_TAKEN') {
                Add-Result '重复预约返回 409' 'PASS' 'SEAT_TAKEN'
            } else {
                Add-Result '重复预约返回 409' 'FAIL' "HTTP $($duplicate.Status) code=$(Get-Code $duplicate)"
            }

            # 8. 签到（窗口外跳过）
            $startAt = [datetimeoffset]$create.Body.data.slotStart
            $deadline = [datetimeoffset]$create.Body.data.checkInDeadline
            $now = [datetimeoffset]::Now
            $code = $create.Body.data.seatNo
            $checkIn = Invoke-Api -Method Post -Path "/api/v1/reservations/$reservationId/check-in" -Token $studentToken -Body @{ seatCode = $code }
            if ($checkIn.Status -eq 200) {
                Add-Result '座位码签到' 'PASS' "status=$($checkIn.Body.data.status)"
            } elseif ($checkIn.Status -eq 422 -and (Get-Code $checkIn) -eq 'CHECKIN_WINDOW') {
                Add-Result '座位码签到' 'SKIP' "不在窗口内（$startAt ~ $deadline，现在 $now）"
            } else {
                Add-Result '座位码签到' 'FAIL' "HTTP $($checkIn.Status) code=$(Get-Code $checkIn)"
            }

            # 9. 清理：取消本次创建的预约，保证脚本可重复运行
            $cancel = Invoke-Api -Method Post -Path "/api/v1/reservations/$reservationId/cancel" -Token $studentToken
            if ($cancel.Status -eq 200) {
                Add-Result '清理测试预约' 'PASS' "id=$reservationId 已取消"
            } else {
                Add-Result '清理测试预约' 'SKIP' "HTTP $($cancel.Status)（可能已签到，需管理员处理）"
            }
        } elseif ($create.Status -eq 409) {
            Add-Result '创建预约' 'SKIP' "座位已被占用：$(Get-Code $create)"
        } else {
            Add-Result '创建预约' 'FAIL' "HTTP $($create.Status) code=$(Get-Code $create)"
        }
    }

    # 9. 通知增量
    $notifications = Invoke-Api -Method Get -Path '/api/v1/notifications?afterId=0&limit=5' -Token $studentToken
    if ($notifications.Status -eq 200) {
        Add-Result '通知增量' 'PASS' "count=$(@($notifications.Body.data.items).Count) lastId=$($notifications.Body.data.lastId)"
    } else {
        Add-Result '通知增量' 'FAIL' "HTTP $($notifications.Status)"
    }

    # 10. 内部批处理（带共享密钥）
    $internal = @{ 'X-Internal-Token' = $InternalToken }
    $expire = Invoke-Api -Method Post -Path '/api/v1/internal/reservations/expire-batch' -Token $null -Body @{ limit = 10 } -ExtraHeaders $internal
    if ($expire.Status -eq 200) {
        Add-Result '过期批处理' 'PASS' "processed=$($expire.Body.data.processed)"
    } else {
        Add-Result '过期批处理' 'FAIL' "HTTP $($expire.Status) code=$(Get-Code $expire)"
    }
    $expireAgain = Invoke-Api -Method Post -Path '/api/v1/internal/reservations/expire-batch' -Token $null -Body @{ limit = 10 } -ExtraHeaders $internal
    if ($expireAgain.Status -eq 200 -and $expireAgain.Body.data.processed -eq 0) {
        Add-Result '批处理幂等' 'PASS' '第二次 processed=0'
    } else {
        Add-Result '批处理幂等' 'FAIL' "processed=$($expireAgain.Body.data.processed)"
    }

    # 11. 管理员登录与统计
    $admin = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ studentNo = $AdminNo; password = $Password }
    if ($admin.Status -eq 200 -and $admin.Body.data.role -eq 'ADMIN') {
        $stats = Invoke-Api -Method Get -Path '/api/v1/admin/stats' -Token $admin.Body.data.token
        if ($stats.Status -eq 200) {
            Add-Result '管理端统计' 'PASS' "total=$($stats.Body.data.totalReservations)"
        } else {
            Add-Result '管理端统计' 'FAIL' "HTTP $($stats.Status)"
        }
    } else {
        Add-Result '管理员登录' 'FAIL' "HTTP $($admin.Status)"
    }

    # 12. 学生令牌调管理端必须 403
    $forbidden = Invoke-Api -Method Get -Path '/api/v1/admin/seats' -Token $studentToken
    if ($forbidden.Status -eq 403) {
        Add-Result '角色越权拦截' 'PASS' 'FORBIDDEN'
    } else {
        Add-Result '角色越权拦截' 'FAIL' "HTTP $($forbidden.Status)"
    }
}

$passed = @($script:Results | Where-Object Status -eq 'PASS').Count
$skipped = @($script:Results | Where-Object Status -eq 'SKIP').Count
$failed = @($script:Results | Where-Object Status -eq 'FAIL').Count
Write-Host ''
Write-Host ("汇总：PASS {0} / SKIP {1} / FAIL {2}" -f $passed, $skipped, $failed) -ForegroundColor Cyan
if ($failed -gt 0) { exit 1 } else { exit 0 }