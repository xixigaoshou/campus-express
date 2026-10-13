# 订单图片凭证功能 端到端验证脚本（ASCII-only，中文用 \uXXXX 转义）
$ErrorActionPreference = "Continue"
$base = "http://localhost:8080/api"
$root = "C:\Users\xmlin\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6ab0ed45647a63983f854f91"
$img  = "$root\campus-express-frontend\src\assets\login-bg.jpg"

$script:pass = 0
$script:fail = 0
function Check($name, $cond, $extra) {
  if ($cond) { $script:pass++; Write-Output "[PASS] $name" }
  else { $script:fail++; Write-Output "[FAIL] $name  => $extra" }
}
function Login($loginName) {
  $body = @{ loginName = $loginName; password = "123456" } | ConvertTo-Json -Compress
  return Invoke-RestMethod -Uri "$base/user/login" -Method Post -ContentType "application/json" -Body $body
}
function PublishOrder($tokenP) {
  $expect = (Get-Date).AddHours(3).ToString("yyyy-MM-dd HH:mm:ss")
  $body = @{
    expressNo = "SF" + (Get-Random -Minimum 1000000 -Maximum 9999999)
    expressCompany = "SF"
    pickupLocation = "Building A"
    deliverLocation = "Dorm 3"
    expectTime = $expect
    remark = "photo credential test"
    rewardAmount = 1.00
  } | ConvertTo-Json -Compress
  return (Invoke-RestMethod -Uri "$base/order/publish" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $tokenP" } -Body $body).data
}
function SetStatus($tokenT, $oid, $targetStatus) {
  # 手工拼接 JSON 避免 ConvertTo-Json 对 \uXXXX 二次转义；正文以 UTF-8 字节发送
  $json = '{"orderId":' + $oid + ',"targetStatus":"' + $targetStatus + '","changeNote":"test"}'
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
  return Invoke-RestMethod -Uri "$base/order/status" -Method Put -ContentType "application/json; charset=utf-8" -Headers @{ Authorization = "Bearer $tokenT" } -Body $bytes
}

Write-Output "=== 登录 ==="
$pubLogin = Login "2024001"
$takerLogin = Login "2024002"
$tokenP = $pubLogin.data.token
$tokenT = $takerLogin.data.token
Check "publisher login (xiaoli)" ($null -ne $tokenP) $pubLogin.message
Check "taker login (xiaowang)" ($null -ne $tokenT) $takerLogin.message

Write-Output "`n=== 场景A：正常流程（取件选填 + 送达必填）==="
$oid = PublishOrder $tokenP
Check "publish order" ($oid -gt 0) $oid
Invoke-RestMethod -Uri "$base/order/take-order?orderId=$oid" -Method Put -Headers @{ Authorization = "Bearer $tokenT" } | Out-Null

# 已接单状态下上传取件照片（允许）
$up1 = & curl.exe -s -X POST "$base/order/$oid/photo" -H "Authorization: Bearer $tokenT" -F "type=pickup" -F "file=@$img"
$up1j = $up1 | ConvertFrom-Json
Check "upload pickup photo at ACCEPTED (allowed)" ($up1j.code -eq 200) $up1

# 已接单状态下上传送达照片（应拒绝，仅配送中可传）
$upBad = & curl.exe -s -X POST "$base/order/$oid/photo" -H "Authorization: Bearer $tokenT" -F "type=deliver" -F "file=@$img"
$upBadj = $upBad | ConvertFrom-Json
Check "upload deliver photo at ACCEPTED (rejected)" ($upBadj.code -ne 200) $upBad

$st1 = SetStatus $tokenT $oid "\u53d6\u4ef6\u4e2d"   # 取件中
Check "status -> 取件中" ($st1.code -eq 200) $st1.message
$st2 = SetStatus $tokenT $oid "\u914d\u9001\u4e2d"   # 配送中
Check "status -> 配送中" ($st2.code -eq 200) $st2.message

# 配送中：未传送达照片且 skipPhoto=false -> 应拒绝
$c1 = & curl.exe -s -X PUT "$base/order/confirm-arrival?orderId=$oid&skipPhoto=false" -H "Authorization: Bearer $tokenT"
$c1j = $c1 | ConvertFrom-Json
Check "confirm-arrival blocked without deliver photo" ($c1j.code -ne 200) $c1

# 上传送达照片
$up2 = & curl.exe -s -X POST "$base/order/$oid/photo" -H "Authorization: Bearer $tokenT" -F "type=deliver" -F "file=@$img"
$up2j = $up2 | ConvertFrom-Json
Check "upload deliver photo at DELIVERING (allowed)" ($up2j.code -eq 200) $up2

# 有送达照片 -> 确认送达成功
$c2 = & curl.exe -s -X PUT "$base/order/confirm-arrival?orderId=$oid&skipPhoto=false" -H "Authorization: Bearer $tokenT"
$c2j = $c2 | ConvertFrom-Json
Check "confirm-arrival succeeds with deliver photo" ($c2j.code -eq 200) $c2

# 发布者确认收货 -> 已完成
$fin = Invoke-RestMethod -Uri "$base/order/confirm-finish?orderId=$oid" -Method Put -Headers @{ Authorization = "Bearer $tokenP" }
Check "publisher confirm-finish -> COMPLETED" ($fin.code -eq 200) $fin.message

Write-Output "`n=== 场景B：无法拍照跳过（skipPhoto=true）==="
$oid2 = PublishOrder $tokenP
Check "publish order2" ($oid2 -gt 0) $oid2
Invoke-RestMethod -Uri "$base/order/take-order?orderId=$oid2" -Method Put -Headers @{ Authorization = "Bearer $tokenT" } | Out-Null
SetStatus $tokenT $oid2 "\u53d6\u4ef6\u4e2d" | Out-Null
SetStatus $tokenT $oid2 "\u914d\u9001\u4e2d" | Out-Null
$c3 = & curl.exe -s -X PUT "$base/order/confirm-arrival?orderId=$oid2&skipPhoto=true" -H "Authorization: Bearer $tokenT"
$c3j = $c3 | ConvertFrom-Json
Check "confirm-arrival succeeds with skipPhoto=true" ($c3j.code -eq 200) $c3
$fin2 = Invoke-RestMethod -Uri "$base/order/confirm-finish?orderId=$oid2" -Method Put -Headers @{ Authorization = "Bearer $tokenP" }
Check "publisher confirm-finish order2 -> COMPLETED" ($fin2.code -eq 200) $fin2.message

Write-Output "`n=== 数据库回读 ==="
$env:MYSQL_PWD = "123456"
$row1 = & "D:\mysql\MySQL Server 8.0\bin\mysql.exe" -uroot -N -e "SELECT status, pickup_photo, deliver_photo FROM campus_express.t_order WHERE order_id=$oid;"
Write-Output "order#$oid => $row1"
$row2 = & "D:\mysql\MySQL Server 8.0\bin\mysql.exe" -uroot -N -e "SELECT status, IFNULL(pickup_photo,'NULL'), IFNULL(deliver_photo,'NULL') FROM campus_express.t_order WHERE order_id=$oid2;"
Write-Output "order#$oid2 => $row2"
Check "order1 has pickup+deliver photo" (($row1 -match "/uploads/order/") -and ($row1 -split '\s+' | Where-Object { $_ -match "uploads" }).Count -ge 2) $row1

Write-Output "`n=== 结果：PASS=$($script:pass) FAIL=$($script:fail) ==="
Write-Output "TEST_ORDER_IDS=$oid,$oid2"