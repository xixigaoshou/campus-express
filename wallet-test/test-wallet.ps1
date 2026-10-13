# 虚拟钱包模块 6 个测试用例（R-25 ~ R-28）
# 依赖：后端运行于 localhost:8080，MySQL 已导入钱包表与测试账号
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
$Base = "http://localhost:8080"
$Mysql = "D:\mysql\MySQL Server 8.0\bin\mysql.exe"
$results = @()

function Call-Api {
    param([string]$Method, [string]$Path, [string]$Token = "", $BodyObj = $null, [switch]$AllowFail)
    # 使用 HttpClient + UTF-8 显式解码：PS5 的 Invoke-RestMethod 对无 charset 的 JSON 响应按 ANSI 解码，
    # 会导致响应中的中文乱码、字符串比较失败
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
        $obj = $text | ConvertFrom-Json
        if (-not $AllowFail -and $obj.code -ne 200) { throw ("API 失败 " + $Path + " -> code=" + $obj.code + " msg=" + $obj.message) }
        return $obj
    } finally {
        $client.Dispose()
    }
}

function Login {
    param([string]$Name)
    $r = Call-Api "POST" "/api/user/login" "" @{ loginName = $Name; password = "123456" }
    return @{ token = $r.data.token; user = $r.data.user }
}

function Get-Wallet {
    param([string]$Token)
    return (Call-Api "GET" "/api/wallet/info" $Token).data
}

function Db {
    param([string]$Sql)
    $env:MYSQL_PWD = "123456"
    $out = & $Mysql -uroot --default-character-set=utf8mb4 -N -e "USE campus_express; $Sql" 2>$null
    return $out
}

function Assert {
    param([string]$Name, [bool]$Cond, [string]$Detail)
    $tag = if ($Cond) { "PASS" } else { "FAIL" }
    $script:results += [pscustomobject]@{ Case = $Name; Result = $tag; Detail = $Detail }
    Write-Host ("[{0}] {1} :: {2}" -f $tag, $Name, $Detail)
}

Write-Host "==== 用例准备：登录测试账号 ===="
$li = Login "2024001"   # 小李 发布者 余额100
$zhang = Login "2024011" # 小张 接单者 余额50
$chen = Login "2024003"  # 小陈 接单者 余额5
Write-Host "小李 token OK, 小张 token OK, 小陈 token OK"

$liWallet = Get-Wallet $li.token
Assert "前置-小李钱包初始化" ($liWallet.balance -eq 100.0 -and $liWallet.frozenBalance -eq 0.0) ("balance=$($liWallet.balance) frozen=$($liWallet.frozenBalance)")

Write-Host ""
Write-Host "==== 用例1：余额充足发布订单（R-25/R-26）===="
$pubBody = @{ expressCompany = "顺丰速运"; pickupLocation = "菜鸟驿站"; deliverLocation = "1号楼"; expectTime = "2026-10-04 10:00:00"; remark = "钱包测试"; rewardAmount = 10 }
$r1 = Call-Api "POST" "/api/order/publish" $li.token $pubBody
$oid1 = $r1.data
Assert "用例1-发布成功返回订单ID" ($oid1 -gt 0) "orderId=$oid1"
$w1 = Get-Wallet $li.token
Assert "用例1-可用减10冻结增10" ($w1.balance -eq 90.0 -and $w1.frozenBalance -eq 10.0) ("balance=$($w1.balance) frozen=$($w1.frozenBalance)")
$frozenLog = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.logTypeText -eq "订单报酬冻结" -and $_.amount -eq -10.0 }
Assert "用例1-生成冻结流水" ($null -ne $frozenLog) ("amount=$($frozenLog.amount) order=$($frozenLog.relatedOrderId) remark=$($frozenLog.remark)")

Write-Host ""
Write-Host "==== 用例2：余额不足发布订单失败（R-25）===="
$wBefore2 = Get-Wallet $li.token
# 临时置零小李可用余额，模拟余额不足（测试后恢复）
Db "UPDATE t_wallet SET balance = 0.00 WHERE user_id = 1" | Out-Null
$pubBody2 = @{ expressCompany = "圆通"; pickupLocation = "菜鸟驿站"; deliverLocation = "2号楼"; expectTime = "2026-10-04 10:00:00"; rewardAmount = 10 }
$r2 = Call-Api "POST" "/api/order/publish" $li.token $pubBody2 -AllowFail
Assert "用例2-余额不足发布被拒绝" ($r2.code -ne 200) ("code=$($r2.code) msg=$($r2.message)")
$before = (Db "SELECT COUNT(*) FROM t_order WHERE publisher_id = 1")
$r2b = Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "圆通"; pickupLocation = "菜鸟驿站"; deliverLocation = "2号楼"; expectTime = "2026-10-04 10:00:00"; rewardAmount = 10 } -AllowFail
$after = (Db "SELECT COUNT(*) FROM t_order WHERE publisher_id = 1")
Assert "用例2-订单未创建（事务回滚）" ($after -eq $before) ("before=$before after=$after")
$w2 = Get-Wallet $li.token
Assert "用例2-钱包未变动（冻结不变）" ($w2.balance -eq 0.0 -and $w2.frozenBalance -eq $wBefore2.frozenBalance) ("balance=$($w2.balance) frozen=$($w2.frozenBalance)")
# 恢复小李可用余额（还原用例1发布后的状态）
Db "UPDATE t_wallet SET balance = $($wBefore2.balance) WHERE user_id = 1" | Out-Null

Write-Host ""
Write-Host "==== 用例3：待接单发布者取消（R-27 解冻）===="
$r3 = Call-Api "PUT" "/api/order/cancel?orderId=$oid1" $li.token
$w3 = Get-Wallet $li.token
Assert "用例3-余额恢复100冻结归零" ($w3.balance -eq 100.0 -and $w3.frozenBalance -eq 0.0) ("balance=$($w3.balance) frozen=$($w3.frozenBalance)")
$unfrozenLog = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.logTypeText -eq "订单解冻退回" -and $_.relatedOrderId -eq $oid1 }
Assert "用例3-生成解冻流水" ($null -ne $unfrozenLog) ("amount=$($unfrozenLog.amount) order=$($unfrozenLog.relatedOrderId)")
$status3 = Db "SELECT status FROM t_order WHERE order_id = $oid1"
Assert "用例3-订单状态已取消" ($status3 -eq "已取消") "status=$status3"

Write-Host ""
Write-Host "==== 用例4：已接单接单者取消（R-12+R-27）===="
$pubBody4 = @{ expressCompany = "中通"; pickupLocation = "菜鸟驿站"; deliverLocation = "3号楼"; expectTime = "2026-10-04 10:00:00"; remark = "钱包测试接单取消"; rewardAmount = 10 }
$oid4 = (Call-Api "POST" "/api/order/publish" $li.token $pubBody4).data
Call-Api "PUT" "/api/order/take-order?orderId=$oid4" $zhang.token | Out-Null
$creditBefore = (Db "SELECT credit_score FROM t_user WHERE user_id = 11")
$r4 = Call-Api "PUT" "/api/order/cancel?orderId=$oid4" $zhang.token
$creditAfter = (Db "SELECT credit_score FROM t_user WHERE user_id = 11")
Assert "用例4-接单者扣5信誉分" (($creditBefore - $creditAfter) -eq 5) ("before=$creditBefore after=$creditAfter")
$w4 = Get-Wallet $li.token
Assert "用例4-发布者资金解冻返还" ($w4.balance -eq 100.0 -and $w4.frozenBalance -eq 0.0) ("balance=$($w4.balance) frozen=$($w4.frozenBalance)")
$status4 = Db "SELECT status FROM t_order WHERE order_id = $oid4"
Assert "用例4-订单退回待接单" ($status4 -eq "待接单") "status=$status4"

Write-Host ""
Write-Host "==== 用例5：手动确认收货结算（R-28）===="
$pubBody5 = @{ expressCompany = "京东"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"; expectTime = "2026-10-04 10:00:00"; remark = "钱包测试手动结算"; rewardAmount = 10 }
$oid5 = (Call-Api "POST" "/api/order/publish" $li.token $pubBody5).data
Call-Api "PUT" "/api/order/take-order?orderId=$oid5" $zhang.token | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oid5; targetStatus = "取件中"; changeNote = "已取件" } | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oid5; targetStatus = "配送中"; changeNote = "配送中" } | Out-Null
Call-Api "PUT" "/api/order/confirm-arrival?orderId=$oid5" $zhang.token | Out-Null
$w5a = Get-Wallet $li.token
Assert "用例5-确认收货前发布者冻结10" ($w5a.frozenBalance -eq 10.0) "frozen=$($w5a.frozenBalance)"
Call-Api "PUT" "/api/order/confirm-finish?orderId=$oid5" $li.token | Out-Null
$w5li = Get-Wallet $li.token
$w5zh = Get-Wallet $zhang.token
Assert "用例5-发布者冻结扣除至0" ($w5li.frozenBalance -eq 0.0 -and $w5li.balance -eq 90.0) ("balance=$($w5li.balance) frozen=$($w5li.frozenBalance)")
Assert "用例5-接单者入账10" ($w5zh.balance -eq 60.0) "balance=$($w5zh.balance)"
$payLog = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.logTypeText -eq "订单支出扣减" -and $_.relatedOrderId -eq $oid5 }
$incomeLog = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $zhang.token).data.records | Where-Object { $_.logTypeText -eq "订单结算收入" -and $_.relatedOrderId -eq $oid5 }
Assert "用例5-生成两条结算流水" ($null -ne $payLog -and $null -ne $incomeLog) ("发布者支出=$($payLog.amount) 接单者收入=$($incomeLog.amount)")
$status5 = Db "SELECT status FROM t_order WHERE order_id = $oid5"
Assert "用例5-订单已完成" ($status5 -eq "已完成") "status=$status5"

Write-Host ""
Write-Host "==== 用例6：定时任务自动确认收货结算（R-16+R-28）===="
$pubBody6 = @{ expressCompany = "韵达"; pickupLocation = "菜鸟驿站"; deliverLocation = "5号楼"; expectTime = "2026-10-04 10:00:00"; remark = "钱包测试定时结算"; rewardAmount = 10 }
$oid6 = (Call-Api "POST" "/api/order/publish" $li.token $pubBody6).data
Call-Api "PUT" "/api/order/take-order?orderId=$oid6" $zhang.token | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oid6; targetStatus = "取件中"; changeNote = "已取件" } | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oid6; targetStatus = "配送中"; changeNote = "配送中" } | Out-Null
Call-Api "PUT" "/api/order/confirm-arrival?orderId=$oid6" $zhang.token | Out-Null
# 把确认送达时间改成 3 小时前，模拟超时未确认收货，等待定时任务（每60秒）自动结算
Db "UPDATE t_order SET confirm_arrival_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE order_id = $oid6" | Out-Null
$w6before = Get-Wallet $zhang.token
Write-Host "等待定时任务扫描（最多 90 秒）..."
$status6 = $null
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline) {
    $status6 = Db "SELECT status FROM t_order WHERE order_id = $oid6"
    if ($status6 -eq "已完成") { break }
    Start-Sleep -Seconds 5
}
Assert "用例6-订单被自动确认收货" ($status6 -eq "已完成") "status=$status6"
$w6li = Get-Wallet $li.token
$w6zh = Get-Wallet $zhang.token
Assert "用例6-发布者冻结扣除" ($w6li.frozenBalance -eq 0.0) "frozen=$($w6li.frozenBalance)"
Assert "用例6-接单者报酬到账" ($w6zh.balance -eq ($w6before.balance + 10)) ("before=$($w6before.balance) after=$($w6zh.balance)")
$autoPay = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.logTypeText -eq "订单支出扣减" -and $_.relatedOrderId -eq $oid6 }
$autoIncome = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50" $zhang.token).data.records | Where-Object { $_.logTypeText -eq "订单结算收入" -and $_.relatedOrderId -eq $oid6 }
Assert "用例6-生成两条结算流水" ($null -ne $autoPay -and $null -ne $autoIncome) ("发布者支出=$($autoPay.amount) 接单者收入=$($autoIncome.amount)")

Write-Host ""
Write-Host "==== 汇总 ===="
$fail = @($results | Where-Object { $_.Result -eq "FAIL" })
$results | Format-Table Case, Result, Detail -AutoSize
Write-Host ("总计 {0} 项断言，PASS {1}，FAIL {2}" -f $results.Count, ($results.Count - $fail.Count), $fail.Count)
