# ============================================================
# 验证：发布需求时预期取件时间必须晚于当前时间
# 场景1：过去时间（昨天）→ 应被拒绝
# 场景2：未来时间（明天）→ 应成功发布
# 场景3：发布成功后按发布者取消清理订单（避免污染需求大厅）
# ============================================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
$Base = "http://localhost:8080"

function Call-Api {
    param([string]$Method, [string]$Path, [string]$Token = "", $BodyObj = $null)
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$Base$Path")
        if ($Token) { $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token) }
        if ($null -ne $BodyObj) {
            $json = $BodyObj | ConvertTo-Json -Depth 6
            $req.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
        }
        $resp = $client.SendAsync($req).Result
        $bytes = $resp.Content.ReadAsByteArrayAsync().Result
        $text = [System.Text.Encoding]::UTF8.GetString($bytes)
        try { $obj = $text | ConvertFrom-Json } catch { $obj = @{ code = 999; message = $text } }
        return $obj
    } finally {
        $client.Dispose()
    }
}

$r = Call-Api "POST" "/api/user/login" "" @{ loginName = "2024001"; password = "123456" }
if ($r.code -ne 200) { Write-Host "登录失败: $($r.message)"; exit 1 }
$token = $r.data.token
Write-Host "登录成功：小李(发布者)"

# ---------- 场景1：过去时间应被拒 ----------
$past = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd HH:mm:ss")
$r1 = Call-Api "POST" "/api/order/publish" $token @{
    expressCompany = "校验专用"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"
    expectTime = $past; rewardAmount = 2
}
Write-Host "场景1 过去时间($past)发布: code=$($r1.code) msg=$($r1.message)"
if ($r1.code -eq 400 -and $r1.message -match "晚于当前时间") {
    Write-Host "[PASS] 过去时间发布被拒绝"
} else {
    Write-Host "[FAIL] 过去时间未被拦截！"
}

# ---------- 场景2：刚好等于当前时间应被拒 ----------
$nowStr = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$r2 = Call-Api "POST" "/api/order/publish" $token @{
    expressCompany = "校验专用"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"
    expectTime = $nowStr; rewardAmount = 2
}
Write-Host "场景2 当前时间($nowStr)发布: code=$($r2.code) msg=$($r2.message)"
if ($r2.code -eq 400 -and $r2.message -match "晚于当前时间") {
    Write-Host "[PASS] 当前时间发布被拒绝"
} else {
    Write-Host "[FAIL] 当前时间未被拦截！"
}

# ---------- 场景3：未来时间应成功 ----------
$future = (Get-Date).AddDays(1).ToString("yyyy-MM-dd HH:mm:ss")
$r3 = Call-Api "POST" "/api/order/publish" $token @{
    expressCompany = "校验专用"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"
    expectTime = $future; rewardAmount = 2
}
Write-Host "场景3 未来时间($future)发布: code=$($r3.code) msg=$($r3.message) data=$($r3.data)"
if ($r3.code -eq 200 -and $null -ne $r3.data) {
    Write-Host "[PASS] 未来时间发布成功 orderId=$($r3.data)"
    # 清理：发布者取消订单
    $clean = Call-Api "PUT" "/api/order/cancel?orderId=$($r3.data)&cancelReason=$([uri]::EscapeDataString('其他原因'))" $token
    Write-Host "清理订单$($r3.data): code=$($clean.code) msg=$($clean.message)"
    $st = Call-Api "GET" "/api/order/detail/$($r3.data)" $token
    Write-Host "清理后状态: $($st.data.status)"
} else {
    Write-Host "[FAIL] 未来时间发布被误拦截！"
}
Write-Host "`n验证完成"
