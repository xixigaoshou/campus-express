# ============================================================
# 复现脚本：B20/B21 取消链 + G2 抢单 真实响应抓取
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
function Login {
    param([string]$Name)
    $r = Call-Api "POST" "/api/user/login" "" @{ loginName = $Name; password = "123456" }
    if ($r.code -ne 200) { Write-Host "登录失败 $Name : $($r.message)"; exit 1 }
    return $r.data.token
}

$li = Login "2024001"
$xiong = Login "2024037"

# ---------- 复现 B20/B21 ----------
Write-Host "`n=== B20/B21 复现 ==="
$oidB = (Call-Api "POST" "/api/order/publish" $li @{ expressCompany = "复现专线"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"; expectTime = "2026-10-06 14:00:00"; rewardAmount = 2 }).data
Write-Host "发布单B: orderId=$oidB"

$rTake = Call-Api "PUT" "/api/order/take-order?orderId=$oidB" $xiong
Write-Host "小熊抢单B: code=$($rTake.code) msg=$($rTake.message)"

$rB20 = Call-Api "PUT" "/api/order/cancel?orderId=$oidB&cancelReason=$([uri]::EscapeDataString('临时有事/时间冲突'))" $xiong
Write-Host "小熊取消B: code=$($rB20.code) msg=$($rB20.message)"

$rB21 = Call-Api "PUT" "/api/order/cancel?orderId=$oidB&cancelReason=$([uri]::EscapeDataString('其他原因'))" $li
Write-Host "小李取消B: code=$($rB21.code) msg=$($rB21.message)"

# 清理：若未取消成功，先恢复现场
$stNow = Call-Api "GET" "/api/order/detail/$oidB" $li
Write-Host "单B当前: status=$($stNow.data.status) taker=$($stNow.data.takerId)"
if ($stNow.data.status -ne "已取消") {
    $clean = Call-Api "PUT" "/api/order/cancel?orderId=$oidB&cancelReason=$([uri]::EscapeDataString('其他原因'))" $li
    Write-Host "清理单B: code=$($clean.code) msg=$($clean.message)"
    $stNow = Call-Api "GET" "/api/order/detail/$oidB" $li
    Write-Host "清理后: status=$($stNow.data.status)"
}

# ---------- 复现 G2 抢单 ----------
Write-Host "`n=== G2 抢单复现 ==="
$oidE = (Call-Api "POST" "/api/order/publish" $li @{ expressCompany = "任务二专线"; pickupLocation = "驿站"; deliverLocation = "7号楼"; expectTime = "2026-10-06 18:00:00"; rewardAmount = 2 }).data
Write-Host "发布单E: orderId=$oidE"
$rTakeE = Call-Api "PUT" "/api/order/take-order?orderId=$oidE" $xiong
Write-Host "小熊抢单E: code=$($rTakeE.code) msg=$($rTakeE.message)"
$stE = Call-Api "GET" "/api/order/detail/$oidE" $li
Write-Host "单E状态: status=$($stE.data.status) taker=$($stE.data.takerId)"

# 清理单E
if ($stE.data.status -eq "已接单") {
    $c1 = Call-Api "PUT" "/api/order/cancel?orderId=$oidE&cancelReason=$([uri]::EscapeDataString('临时有事/时间冲突'))" $xiong
    Write-Host "清理单E(小熊取消): code=$($c1.code) msg=$($c1.message)"
}
$c2 = Call-Api "PUT" "/api/order/cancel?orderId=$oidE&cancelReason=$([uri]::EscapeDataString('其他原因'))" $li
Write-Host "清理单E(小李取消): code=$($c2.code) msg=$($c2.message)"
Write-Host "`n复现完成"
