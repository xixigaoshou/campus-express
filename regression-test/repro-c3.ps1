# C3/C4 复现：观察 /api/message/order-page/{id} 完整响应
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
$Base = "http://localhost:8080"

function Call-Raw {
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
        Write-Host ("HTTP {0}" -f $resp.StatusCode)
        return $text
    } finally { $client.Dispose() }
}

function Login {
    param([string]$Name)
    $r = (Call-Raw "POST" "/api/user/login" "" @{ loginName = $Name; password = "123456" }) | ConvertFrom-Json
    return $r.data.token
}

$li = Login "2024001"
$zhang = Login "2024011"
$xiong = Login "2024037"

$oid = (Call-Raw "POST" "/api/order/publish" $li @{ expressCompany = "复现专线"; pickupLocation = "驿站"; deliverLocation = "1号楼"; expectTime = "2026-10-05 10:00:00"; rewardAmount = 2 } | ConvertFrom-Json).data
Write-Host "发布订单 $oid"
Call-Raw "PUT" "/api/order/take-order?orderId=$oid" $zhang | Out-Null
Call-Raw "POST" "/api/message/send" $zhang @{ orderId = $oid; content = "第一条留言" } | Out-Null
Call-Raw "POST" "/api/message/send" $li @{ orderId = $oid; content = "第二条留言" } | Out-Null

Write-Host "--- 小张(接单者) 查看会话 ---"
Call-Raw "GET" "/api/message/order-page/$oid?pageNum=1&pageSize=20" $zhang
Write-Host ""
Write-Host "--- 小熊(非参与者) 查看会话 ---"
Call-Raw "GET" "/api/message/order-page/$oid?pageNum=1&pageSize=20" $xiong

# 清理复现数据
$env:MYSQL_PWD = "123456"
& "D:\mysql\MySQL Server 8.0\bin\mysql.exe" -uroot --default-character-set=utf8mb4 -N -e "USE campus_express; DELETE FROM t_message WHERE order_id=$oid; DELETE FROM t_order_status_history WHERE order_id=$oid; DELETE FROM t_order WHERE order_id=$oid; UPDATE t_wallet SET balance=100.00, frozen_balance=0.00 WHERE user_id=1;" | Out-Null
Write-Host "已清理复现数据"
