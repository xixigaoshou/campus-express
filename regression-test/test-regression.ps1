# ============================================================
# 全系统回归测试脚本（用户/订单/消息/评价/钱包/通知/筛选/定时任务）
# 依赖：后端 localhost:8080、MySQL campus_express 库
# 测试账号：小李(2024001 发布者)、小张(2024011 接单者)、小熊(2024037 接单者)、小孙(2024014 低信誉)
# 结束后自动清理测试数据并恢复钱包/信誉分到初始状态
# ============================================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
$Base = "http://localhost:8080"
$Mysql = "D:\mysql\MySQL Server 8.0\bin\mysql.exe"
$results = @()
$testOrderIds = [System.Collections.ArrayList]@()

# ---------------- 基础设施 ----------------
function Call-Api {
    param([string]$Method, [string]$Path, [string]$Token = "", $BodyObj = $null)
    # HttpClient + UTF-8 显式解码，规避 PS5 对无 charset JSON 按 ANSI 解码导致中文乱码
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

# 多列查询：mysql -N 输出按 TAB 拼接成单个字符串，必须拆分后再按列取值（$row[0] 是首字符而非首列）
function Db-Cells {
    param([string]$Sql)
    $row = Db $Sql
    if ($null -eq $row) { return @() }
    return @($row -split "`t")
}

function Assert {
    param([string]$Name, [bool]$Cond, [string]$Detail)
    $tag = if ($Cond) { "PASS" } else { "FAIL" }
    $script:results += [pscustomobject]@{ Case = $Name; Result = $tag; Detail = $Detail }
    Write-Host ("[{0}] {1} :: {2}" -f $tag, $Name, $Detail)
}

Write-Host "==== 0. 登录测试账号 & 记录初始状态 ===="
$li = Login "2024001"     # 小李 发布者
$zhang = Login "2024011"  # 小张 接单者(优秀等级)
$xiong = Login "2024037"  # 小熊 接单者(普通等级)
$sun = Login "2024014"    # 小孙 信誉分75(低于80不可接单)
Write-Host "登录 OK：小李/小张/小熊/小孙"

$initWallet = @{}
foreach ($uid in 1, 11, 16, 14) {
    $cells = Db-Cells "SELECT balance, frozen_balance FROM t_wallet WHERE user_id = $uid"
    $initWallet[$uid] = @{ balance = [double]$cells[0]; frozen = [double]$cells[1] }
}
$initCredit = @{}
foreach ($uid in 1, 11, 16, 14) {
    $initCredit[$uid] = [int](Db "SELECT credit_score FROM t_user WHERE user_id = $uid")
}
Write-Host ("初始状态：小李 余额$($initWallet[1].balance)/冻结$($initWallet[1].frozen)/信誉$($initCredit[1])；" +
            "小张 余额$($initWallet[11].balance)/信誉$($initCredit[11])；小熊 余额$($initWallet[16].balance)/信誉$($initCredit[16])；小孙 信誉$($initCredit[14])")

# ============================================================
# A. 用户模块（临时账号 temp001 全流程）
# ============================================================
Write-Host ""
Write-Host "==== A. 用户模块（注册/登录/资料/密码/找回/信誉流水）===="
$tempNo = "temp001"
$tempPhone = "13900000001"
$rA1 = Call-Api "POST" "/api/user/register" "" @{ studentNo = $tempNo; username = "回归测试员"; phone = $tempPhone; password = "123456"; role = 1; dormArea = "A区1栋" }
Assert "A1-注册新账号成功" ($rA1.code -eq 200) "code=$($rA1.code)"
$rA2 = Call-Api "POST" "/api/user/register" "" @{ studentNo = $tempNo; username = "重复"; phone = "13900000002"; password = "123456"; role = 1; dormArea = "A区" }
Assert "A2-重复学号注册被拒" ($rA2.code -ne 200) "code=$($rA2.code) msg=$($rA2.message)"
$tA = (Call-Api "POST" "/api/user/login" "" @{ loginName = $tempNo; password = "123456" }).data.token
Assert "A3-学号登录成功" (-not [string]::IsNullOrEmpty($tA)) "token非空"
$tA2 = (Call-Api "POST" "/api/user/login" "" @{ loginName = $tempPhone; password = "123456" }).data.token
Assert "A4-手机号登录成功" (-not [string]::IsNullOrEmpty($tA2)) "token非空"
$rA5 = Call-Api "POST" "/api/user/login" "" @{ loginName = $tempNo; password = "wrongpass" }
Assert "A5-错误密码被拒" ($rA5.code -ne 200) "code=$($rA5.code)"
$rA6 = Call-Api "GET" "/api/user/info"
Assert "A6-无token访问受保护接口返回401" ($rA6.code -eq 401) "code=$($rA6.code) msg=$($rA6.message)"
# UpdateUserDTO.phone 为 @NotBlank 必填，须随 dormArea 一起提交
$rA7 = Call-Api "PUT" "/api/user/info" $tA @{ phone = $tempPhone; dormArea = "B区3栋" }
$dormDb = Db "SELECT dorm_area FROM t_user WHERE student_no = '$tempNo'"
Assert "A7-修改资料成功" ($rA7.code -eq 200 -and $dormDb -eq "B区3栋") "dorm=$dormDb"
$rA8 = Call-Api "PUT" "/api/user/password" $tA @{ oldPassword = "wrongold"; newPassword = "newpass123" }
Assert "A8-原密码错误修改被拒" ($rA8.code -ne 200) "code=$($rA8.code)"
$rA9 = Call-Api "PUT" "/api/user/password" $tA @{ oldPassword = "123456"; newPassword = "newpass123" }
Assert "A9-修改密码成功" ($rA9.code -eq 200) "code=$($rA9.code)"
$tNew = (Call-Api "POST" "/api/user/login" "" @{ loginName = $tempNo; password = "newpass123" }).data.token
$rOld = Call-Api "POST" "/api/user/login" "" @{ loginName = $tempNo; password = "123456" }
Assert "A10-新密码可登录且旧密码失效" (-not [string]::IsNullOrEmpty($tNew) -and $rOld.code -ne 200) "new=$(-not [string]::IsNullOrEmpty($tNew)) old=$($rOld.code)"
$rA11 = Call-Api "POST" "/api/user/password/reset" "" @{ studentNo = $tempNo; phone = "13911111111"; newPassword = "resetpass456" }
Assert "A11-找回密码手机号不匹配统一提示" ($rA11.code -ne 200 -and $rA11.message -like "*信息不匹配*") "msg=$($rA11.message)"
$rA12 = Call-Api "POST" "/api/user/password/reset" "" @{ studentNo = $tempNo; phone = $tempPhone; newPassword = "resetpass456" }
$tReset = (Call-Api "POST" "/api/user/login" "" @{ loginName = $tempNo; password = "resetpass456" }).data.token
Assert "A12-找回密码成功且可登录" ($rA12.code -eq 200 -and -not [string]::IsNullOrEmpty($tReset)) "code=$($rA12.code) login=$(-not [string]::IsNullOrEmpty($tReset))"
$rA13 = Call-Api "GET" "/api/user/credit-logs?pageNum=1&pageSize=10" $li.token
Assert "A13-信誉分流水分页" ($rA13.code -eq 200 -and $null -ne $rA13.data.records) "total=$($rA13.data.total)"

# ============================================================
# B. 订单模块
# ============================================================
Write-Host ""
Write-Host "==== B. 订单模块（发布/筛选/抢单/状态机/取消/催单/模板/分页）===="
$rE1 = Call-Api "POST" "/api/wallet/recharge" $li.token @{ rechargeAmount = 100 }
Assert "E1-小李充值100备资" ($rE1.code -eq 200) "code=$($rE1.code)"

# --- 单A 主流程：发布 → 抢单 → 状态流转 → 确认收货 → 结算 ---
$wA0 = Get-Wallet $li.token
$oidA = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "回归测试专线"; expressNo = "RT001"; pickupLocation = "菜鸟驿站"; deliverLocation = "1号楼"; expectTime = "2026-10-05 10:00:00"; remark = "回归测试主流程"; rewardAmount = 4 }).data
$testOrderIds.Add($oidA) | Out-Null
$wA1 = Get-Wallet $li.token
Assert "B1-发布订单成功并冻结报酬4" ($oidA -gt 0 -and $wA1.frozenBalance -eq ($wA0.frozenBalance + 4) -and $wA1.balance -eq ($wA0.balance - 4)) "orderId=$oidA frozen=$($wA1.frozenBalance) balance=$($wA1.balance)"

$kw = [uri]::EscapeDataString("回归测试专线")
$hall1 = (Call-Api "GET" "/api/order/hall-page?pageNum=1&pageSize=50&keyword=$kw" $li.token).data
Assert "B2-需求大厅关键词命中" (@($hall1.records | Where-Object { $_.orderId -eq $oidA }).Count -eq 1) "hit=$(@($hall1.records | Where-Object { $_.orderId -eq $oidA }).Count)"
$kwBad = [uri]::EscapeDataString("不存在的关键词xyz")
$hall2 = (Call-Api "GET" "/api/order/hall-page?pageNum=1&pageSize=50&keyword=$kwBad" $li.token).data
Assert "B2b-无关关键词不命中" (@($hall2.records | Where-Object { $_.orderId -eq $oidA }).Count -eq 0) "hit=$(@($hall2.records | Where-Object { $_.orderId -eq $oidA }).Count)"
$hall3 = (Call-Api "GET" "/api/order/hall-page?pageNum=1&pageSize=50&minReward=4&maxReward=4" $li.token).data
Assert "B3-报酬范围过滤命中" (@($hall3.records | Where-Object { $_.orderId -eq $oidA }).Count -eq 1) "hit=$(@($hall3.records | Where-Object { $_.orderId -eq $oidA }).Count)"
$hall4 = (Call-Api "GET" "/api/order/hall-page?pageNum=1&pageSize=50&minReward=10" $li.token).data
Assert "B3b-高报酬范围排除低报酬单" (@($hall4.records | Where-Object { $_.orderId -eq $oidA }).Count -eq 0) "hit=$(@($hall4.records | Where-Object { $_.orderId -eq $oidA }).Count)"

$rB4 = Call-Api "PUT" "/api/order/take-order?orderId=$oidA" $li.token
Assert "B4-发布者角色不可抢单" ($rB4.code -ne 200) "code=$($rB4.code) msg=$($rB4.message)"
$rB5 = Call-Api "PUT" "/api/order/take-order?orderId=$oidA" $zhang.token
$stA = Db "SELECT status FROM t_order WHERE order_id = $oidA"
$histA = [int](Db "SELECT COUNT(*) FROM t_order_status_history WHERE order_id = $oidA")
Assert "B5-小张抢单成功进入已接单" ($rB5.code -eq 200 -and $stA -eq "已接单" -and $histA -ge 2) "status=$stA history=$histA"
$rB6 = Call-Api "PUT" "/api/order/take-order?orderId=$oidA" $xiong.token
Assert "B6-已被接单订单重复抢单被拒" ($rB6.code -ne 200) "code=$($rB6.code)"
$rB7 = Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidA; targetStatus = "配送中"; changeNote = "跳级测试" }
Assert "B7-状态跨跳(已接单→配送中)被拒" ($rB7.code -ne 200) "code=$($rB7.code) msg=$($rB7.message)"
$rB8 = Call-Api "PUT" "/api/order/status" $xiong.token @{ orderId = $oidA; targetStatus = "取件中"; changeNote = "非接单者" }
Assert "B8-非接单者更新状态被拒" ($rB8.code -ne 200) "code=$($rB8.code)"
$rB9 = Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidA; targetStatus = "取件中"; changeNote = "已取件" }
Assert "B9-更新取件中成功" ($rB9.code -eq 200) "code=$($rB9.code)"
$rB10 = Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidA; targetStatus = "配送中"; changeNote = "配送中" }
Assert "B10-更新配送中成功" ($rB10.code -eq 200) "code=$($rB10.code)"

# ============================================================
# C. 消息模块（单A 配送中阶段，订单进行中）
# ============================================================
Write-Host ""
Write-Host "==== C. 消息模块（留言/会话/已读/角标）===="
$rC1 = Call-Api "POST" "/api/message/send" $zhang.token @{ orderId = $oidA; content = "我马上去取件，大概半小时到" }
Assert "C1-接单者发送留言" ($rC1.code -eq 200) "code=$($rC1.code)"
$rC2 = Call-Api "POST" "/api/message/send" $li.token @{ orderId = $oidA; content = "好的，麻烦你了" }
Assert "C2-发布者回复留言" ($rC2.code -eq 200) "code=$($rC2.code)"
# 注意：$oidA 后紧跟 ? 会被 PS 解析成变量名的一部分，必须用 ${oidA} 显式分隔
$msgPage = (Call-Api "GET" "/api/message/order-page/${oidA}?pageNum=1&pageSize=20" $zhang.token).data
Assert "C3-会话消息分页共2条" ($msgPage.total -eq 2) "total=$($msgPage.total)"
$rC4 = Call-Api "GET" "/api/message/order-page/${oidA}?pageNum=1&pageSize=20" $xiong.token
Assert "C4-非订单参与者查看会话被拒" ($rC4.code -ne 200) "code=$($rC4.code)"
$liUnread1 = (Call-Api "GET" "/api/message/unread-count" $li.token).data
Assert "C5-发布者未读消息>0" ($liUnread1 -ge 1) "unread=$liUnread1"
Call-Api "PUT" "/api/message/mark-read/$oidA" $li.token | Out-Null
$liUnread2 = (Call-Api "GET" "/api/message/unread-count" $li.token).data
Assert "C6-标记已读后未读归零" ($liUnread2 -eq 0) "unread=$liUnread2"
$convs = (Call-Api "GET" "/api/message/conversations" $li.token).data
$convA = @($convs | Where-Object { $_.orderId -eq $oidA })[0]
Assert "C7-会话列表含单A且对方为小张" ($null -ne $convA -and $convA.otherName -eq "小张") "other=$($convA.otherName)"

# --- 催单 ---
$rB11 = Call-Api "POST" "/api/order/$oidA/urge" $li.token
Assert "B11-发布者催单成功" ($rB11.code -eq 200) "code=$($rB11.code)"
$rB12 = Call-Api "POST" "/api/order/$oidA/urge" $li.token
Assert "B12-10分钟内重复催单被拒" ($rB12.code -ne 200) "code=$($rB12.code) msg=$($rB12.message)"

# --- 确认送达 / 确认收货 ---
$rB13 = Call-Api "PUT" "/api/order/confirm-arrival?orderId=$oidA" $zhang.token
$stA2 = Db "SELECT status FROM t_order WHERE order_id = $oidA"
Assert "B13-接单者确认送达进入待收货" ($rB13.code -eq 200 -and $stA2 -eq "待收货") "status=$stA2"
$rB14 = Call-Api "PUT" "/api/order/confirm-finish?orderId=$oidA" $zhang.token
Assert "B14-非发布者确认收货被拒" ($rB14.code -ne 200) "code=$($rB14.code)"
$rD6 = Call-Api "POST" "/api/evaluation/create" $li.token @{ orderId = $oidA; starLevel = 5; content = "订单未完成不可评价" }
Assert "D6-未完成订单不可评价" ($rD6.code -ne 200) "code=$($rD6.code) msg=$($rD6.message)"

$wB15a = Get-Wallet $li.token
$wB15z = Get-Wallet $zhang.token
$rB15 = Call-Api "PUT" "/api/order/confirm-finish?orderId=$oidA" $li.token
$stA3 = Db "SELECT status FROM t_order WHERE order_id = $oidA"
Assert "B15-发布者确认收货订单已完成" ($rB15.code -eq 200 -and $stA3 -eq "已完成") "status=$stA3"
$wB15b = Get-Wallet $li.token
$wB15zb = Get-Wallet $zhang.token
Assert "B16-结算：发布者冻结扣4接单者入账4" ($wB15b.frozenBalance -eq ($wB15a.frozenBalance - 4) -and $wB15zb.balance -eq ($wB15z.balance + 4)) "liFrozen=$($wB15b.frozenBalance) zhangBal=$($wB15zb.balance)"
$liPay = @((Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50&logType=5" $li.token).data.records | Where-Object { $_.relatedOrderId -eq $oidA })
$zhIn = @((Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50&logType=4" $zhang.token).data.records | Where-Object { $_.relatedOrderId -eq $oidA })
Assert "B16b-生成支出与收入两条结算流水" ($liPay.Count -eq 1 -and $zhIn.Count -eq 1) "pay=$($liPay.Count) income=$($zhIn.Count)"

# --- 再次发布模板 ---
$tpl = (Call-Api "GET" "/api/order/$oidA/reuse-template" $li.token).data
Assert "B17-再次发布模板返回包裹信息" ($tpl.expressCompany -eq "回归测试专线" -and $tpl.deliverLocation -eq "1号楼") "company=$($tpl.expressCompany)"
$rB17b = Call-Api "GET" "/api/order/$oidA/reuse-template" $zhang.token
Assert "B17b-非发布者查看模板被拒" ($rB17b.code -ne 200) "code=$($rB17b.code)"

# --- 修改待接单订单（单C）--- 
$oidC = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "中通"; pickupLocation = "菜鸟驿站"; deliverLocation = "2号楼"; expectTime = "2026-10-05 12:00:00"; rewardAmount = 3 }).data
$testOrderIds.Add($oidC) | Out-Null
$rB18 = Call-Api "PUT" "/api/order/update-wait" $li.token @{ orderId = $oidC; expressCompany = "中通"; pickupLocation = "菜鸟驿站"; deliverLocation = "3号楼"; expectTime = "2026-10-05 12:00:00"; rewardAmount = 3 }
$locC = Db "SELECT deliver_location FROM t_order WHERE order_id = $oidC"
Assert "B18-发布者修改待接单订单" ($rB18.code -eq 200 -and $locC -eq "3号楼") "loc=$locC"
$rB18b = Call-Api "PUT" "/api/order/update-wait" $zhang.token @{ orderId = $oidC; expressCompany = "中通"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"; expectTime = "2026-10-05 12:00:00"; rewardAmount = 3 }
Assert "B18b-非发布者修改订单被拒" ($rB18b.code -ne 200) "code=$($rB18b.code)"

# --- 发布者取消待接单订单（单C，解冻）---
$wC0 = Get-Wallet $li.token
$rB19 = Call-Api "PUT" "/api/order/cancel?orderId=$oidC&cancelReason=$([uri]::EscapeDataString('等太久没人接'))" $li.token
$stC = Db "SELECT status FROM t_order WHERE order_id = $oidC"
$wC1 = Get-Wallet $li.token
Assert "B19-发布者取消待接单订单" ($rB19.code -eq 200 -and $stC -eq "已取消") "status=$stC"
Assert "B19b-取消后冻结解冻返还" ($wC1.frozenBalance -eq ($wC0.frozenBalance - 3)) "frozen=$($wC1.frozenBalance)"

# --- 接单者取消已接单订单（单B，小熊，扣5分退回待接单）---
$oidB = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "圆通"; pickupLocation = "菜鸟驿站"; deliverLocation = "4号楼"; expectTime = "2026-10-05 14:00:00"; rewardAmount = 3 }).data
$testOrderIds.Add($oidB) | Out-Null
Call-Api "PUT" "/api/order/take-order?orderId=$oidB" $xiong.token | Out-Null
$creditXB0 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 16")
$rB20 = Call-Api "PUT" "/api/order/cancel?orderId=$oidB&cancelReason=$([uri]::EscapeDataString('临时有事/时间冲突'))" $xiong.token
$stBrow = Db-Cells "SELECT status, IFNULL(taker_id,0) FROM t_order WHERE order_id = $oidB"
$creditXB1 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 16")
Assert "B20-接单者取消已接单订单" ($rB20.code -eq 200 -and $stBrow[0] -eq "待接单") "status=$($stBrow[0])"
Assert "B20b-接单者被扣5信誉分" ($creditXB1 -eq ($creditXB0 - 5)) "credit=$creditXB1"
Assert "B20c-接单者已清空退回待接单" ($stBrow[1] -eq "0") "taker=$($stBrow[1])"
$wB20 = Get-Wallet $li.token
$rB21 = Call-Api "PUT" "/api/order/cancel?orderId=$oidB&cancelReason=$([uri]::EscapeDataString('其他原因'))" $li.token
$stB2 = Db "SELECT status FROM t_order WHERE order_id = $oidB"
Assert "B21-发布者取消退回订单" ($rB21.code -eq 200 -and $stB2 -eq "已取消") "status=$stB2"

# --- 信誉分<80禁止接单（单D2，小孙）---
$oidD2 = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "邮政"; pickupLocation = "快递点"; deliverLocation = "5号楼"; expectTime = "2026-10-05 16:00:00"; rewardAmount = 1 }).data
$testOrderIds.Add($oidD2) | Out-Null
$rB22 = Call-Api "PUT" "/api/order/take-order?orderId=$oidD2" $sun.token
Assert "B22-信誉分<80接单被拒" ($rB22.code -ne 200) "code=$($rB22.code) msg=$($rB22.message)"
# 小熊接走并取消，保持测试场景完整
Call-Api "PUT" "/api/order/take-order?orderId=$oidD2" $xiong.token | Out-Null
Call-Api "PUT" "/api/order/cancel?orderId=$oidD2&cancelReason=$([uri]::EscapeDataString('其他原因'))" $xiong.token | Out-Null

# --- 我的订单分页（进行中/历史过滤）---
$pubHis = (Call-Api "GET" "/api/order/my-publish-page?pageNum=1&pageSize=50&statusList=$([uri]::EscapeDataString('历史'))" $li.token).data
$pubAct = (Call-Api "GET" "/api/order/my-publish-page?pageNum=1&pageSize=50&statusList=$([uri]::EscapeDataString('进行中'))" $li.token).data
Assert "B23-我的发布分页(历史/进行中)" ($pubHis.total -ge 2 -and $pubAct.total -ge 1) "history=$($pubHis.total) active=$($pubAct.total)"
$takeHis = (Call-Api "GET" "/api/order/my-take-page?pageNum=1&pageSize=50&statusList=$([uri]::EscapeDataString('历史'))" $zhang.token).data
$takeAct = (Call-Api "GET" "/api/order/my-take-page?pageNum=1&pageSize=50&statusList=$([uri]::EscapeDataString('进行中'))" $zhang.token).data
Assert "B24-我的接单分页(历史/进行中)" ($takeHis.total -ge 1 -and $takeAct.total -ge 1) "history=$($takeHis.total) active=$($takeAct.total)"

# ============================================================
# D. 评价模块（单A 完成后）
# ============================================================
Write-Host ""
Write-Host "==== D. 评价模块（互评/信誉分联动/防重复）===="
$creditZ0 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 11")
$rD1 = Call-Api "POST" "/api/evaluation/create" $li.token @{ orderId = $oidA; starLevel = 5; content = "取件准时，服务很好" }
$creditZ1 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 11")
Assert "D1-发布者5星好评接单者+1分" ($rD1.code -eq 200 -and $creditZ1 -eq ($creditZ0 + 1)) "credit=$creditZ1"
$creditL0 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 1")
$rD2 = Call-Api "POST" "/api/evaluation/create" $zhang.token @{ orderId = $oidA; starLevel = 3; content = "取件等得稍久" }
$creditL1 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 1")
Assert "D2-接单者3星差评发布者-2分" ($rD2.code -eq 200 -and $creditL1 -eq ($creditL0 - 2)) "credit=$creditL1"
$rD3 = Call-Api "POST" "/api/evaluation/create" $li.token @{ orderId = $oidA; starLevel = 5; content = "重复评价" }
Assert "D3-同一订单重复评价被拒" ($rD3.code -ne 200) "code=$($rD3.code)"
$evals = (Call-Api "GET" "/api/evaluation/list/$oidA" $li.token).data
Assert "D4-订单双向评价列表2条" ($evals.Count -eq 2) "count=$($evals.Count)"
$zRecv = (Call-Api "GET" "/api/evaluation/page?pageNum=1&pageSize=10" $zhang.token).data
Assert "D5-小张收到评价分页含该订单" (@($zRecv.records | Where-Object { $_.orderId -eq $oidA }).Count -ge 1) "count=$(@($zRecv.records | Where-Object { $_.orderId -eq $oidA }).Count)"

# ============================================================
# E. 钱包模块（流水过滤）
# ============================================================
Write-Host ""
Write-Host "==== E. 钱包模块（充值/流水类型过滤）===="
$reLogs = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50&logType=1" $li.token).data.records
Assert "E2-资金流水logType=1仅充值" (@($reLogs | Where-Object { $_.logTypeText -ne "充值" }).Count -eq 0 -and $reLogs.Count -ge 1) "count=$($reLogs.Count)"
$allLogs = (Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=100" $li.token).data.records
$hasFreeze = @($allLogs | Where-Object { $_.logTypeText -eq "订单报酬冻结" }).Count -ge 1
$hasUnfreeze = @($allLogs | Where-Object { $_.logTypeText -eq "订单解冻退回" }).Count -ge 1
Assert "E3-流水含冻结与解冻记录" ($hasFreeze -and $hasUnfreeze) "freeze=$hasFreeze unfreeze=$hasUnfreeze"

# ============================================================
# F. 通知模块
# ============================================================
Write-Host ""
Write-Host "==== F. 通知模块（分页/未读/已读/角标）===="
$notPage = (Call-Api "GET" "/api/notification/page?pageNum=1&pageSize=50" $zhang.token).data
Assert "F1-小张通知分页有记录" ($notPage.total -ge 1) "total=$($notPage.total)"
$unreadN1 = (Call-Api "GET" "/api/notification/unread-count" $zhang.token).data
Assert "F2-小张存在未读通知" ($unreadN1 -ge 1) "unread=$unreadN1"
$urgeNot = @($notPage.records | Where-Object { $_.content -like "*提醒*" })
Assert "F2b-通知含催单提醒内容" ($urgeNot.Count -ge 1) "count=$($urgeNot.Count)"
$firstNot = $notPage.records[0]
Call-Api "PUT" "/api/notification/read/$($firstNot.notificationId)" $zhang.token | Out-Null
$unreadN2 = (Call-Api "GET" "/api/notification/unread-count" $zhang.token).data
Assert "F3-单条标记已读后未读减少" ($unreadN2 -lt $unreadN1) "unread=$unreadN2"
Call-Api "PUT" "/api/notification/read-all" $zhang.token | Out-Null
$unreadN3 = (Call-Api "GET" "/api/notification/unread-count" $zhang.token).data
Assert "F4-全部已读后未读归零" ($unreadN3 -eq 0) "unread=$unreadN3"
$unreadOnly = (Call-Api "GET" "/api/notification/page?pageNum=1&pageSize=50&isRead=0" $zhang.token).data
Assert "F5-未读过滤(isRead=0)结果为空" ($unreadOnly.total -eq 0) "total=$($unreadOnly.total)"

# ============================================================
# G. 定时任务验证（构造超时场景，等待真实定时任务扫描）
# ============================================================
Write-Host ""
Write-Host "==== G. 定时任务①②③（构造场景，等待扫描）===="
# G1-任务①：单D 发布24h无人接单提醒（create_time 改 25h 前）
$oidD = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "任务一专线"; pickupLocation = "驿站"; deliverLocation = "6号楼"; expectTime = "2026-10-05 18:00:00"; rewardAmount = 2 }).data
$testOrderIds.Add($oidD) | Out-Null
Db "UPDATE t_order SET create_time = DATE_SUB(NOW(), INTERVAL 25 HOUR) WHERE order_id = $oidD" | Out-Null

# G2-任务②：单E 接单后2h未取件自动取消（小熊接单，update_time 改 3h 前）
$oidE = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "任务二专线"; pickupLocation = "驿站"; deliverLocation = "7号楼"; expectTime = "2026-10-05 18:00:00"; rewardAmount = 2 }).data
$testOrderIds.Add($oidE) | Out-Null
Call-Api "PUT" "/api/order/take-order?orderId=$oidE" $xiong.token | Out-Null
$creditXE0 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 16")
Db "UPDATE t_order SET update_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE order_id = $oidE" | Out-Null

# G3-任务③正常结算：单F 待收货2h自动确认（confirm_arrival_time 改 3h 前）
$oidF = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "任务三专线"; pickupLocation = "驿站"; deliverLocation = "8号楼"; expectTime = "2026-10-05 18:00:00"; rewardAmount = 2 }).data
$testOrderIds.Add($oidF) | Out-Null
Call-Api "PUT" "/api/order/take-order?orderId=$oidF" $zhang.token | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidF; targetStatus = "取件中"; changeNote = "取件" } | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidF; targetStatus = "配送中"; changeNote = "配送" } | Out-Null
Call-Api "PUT" "/api/order/confirm-arrival?orderId=$oidF" $zhang.token | Out-Null
$wFz0 = Get-Wallet $zhang.token
Db "UPDATE t_order SET confirm_arrival_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE order_id = $oidF" | Out-Null

# G4-任务③防御：单G 结算失败不阻塞订单完成（发布者冻结清零模拟余额不足）
$oidG = (Call-Api "POST" "/api/order/publish" $li.token @{ expressCompany = "防御专线"; pickupLocation = "驿站"; deliverLocation = "9号楼"; expectTime = "2026-10-05 18:00:00"; rewardAmount = 4 }).data
$testOrderIds.Add($oidG) | Out-Null
Call-Api "PUT" "/api/order/take-order?orderId=$oidG" $zhang.token | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidG; targetStatus = "取件中"; changeNote = "取件" } | Out-Null
Call-Api "PUT" "/api/order/status" $zhang.token @{ orderId = $oidG; targetStatus = "配送中"; changeNote = "配送" } | Out-Null
Call-Api "PUT" "/api/order/confirm-arrival?orderId=$oidG" $zhang.token | Out-Null
Db "UPDATE t_order SET confirm_arrival_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE order_id = $oidG" | Out-Null
# 防御：将发布者冻结余额压到 2.00（仅够单F结算），单G(4元)结算必然失败——验证"结算失败不阻塞订单完成"
Db "UPDATE t_wallet SET frozen_balance = 2.00 WHERE user_id = 1" | Out-Null

Write-Host "等待定时任务扫描（最多 120 秒，覆盖 2 次扫描验证幂等）..."
$stE = $null; $stF = $null; $stG = $null
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
    $stE = Db "SELECT status FROM t_order WHERE order_id = $oidE"
    $stF = Db "SELECT status FROM t_order WHERE order_id = $oidF"
    $stG = Db "SELECT status FROM t_order WHERE order_id = $oidG"
    if ($stE -eq "待接单" -and $stF -eq "已完成" -and $stG -eq "已完成") { break }
    Start-Sleep -Seconds 5
}

# G1 断言：任务① 24h无人接单提醒（幂等：2次扫描窗口内仅1条）
$liNotD = @((Call-Api "GET" "/api/notification/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.orderId -eq $oidD -and $_.content -like "*无人接单*" })
Assert "G1-任务①24h无人接单提醒已发送(幂等1条)" ($liNotD.Count -eq 1) "count=$($liNotD.Count)"

# G2 断言：任务② 接单后2h未取件自动取消
$stE2 = Db "SELECT status FROM t_order WHERE order_id = $oidE"
$tkE = Db "SELECT IFNULL(taker_id,0) FROM t_order WHERE order_id = $oidE"
$creditXE1 = [int](Db "SELECT credit_score FROM t_user WHERE user_id = 16")
$histE = [int](Db "SELECT COUNT(*) FROM t_order_status_history WHERE order_id = $oidE")
$notE = @((Call-Api "GET" "/api/notification/page?pageNum=1&pageSize=50" $li.token).data.records | Where-Object { $_.orderId -eq $oidE -and $_.content -like "*自动退回*" })
Assert "G2-任务②自动取消退回待接单" ($stE2 -eq "待接单") "status=$stE2"
Assert "G2b-任务②接单者已清空" ($tkE -eq "0") "taker=$tkE"
Assert "G2c-任务②接单者扣5信誉分" ($creditXE1 -eq ($creditXE0 - 5)) "credit=$creditXE1"
Assert "G2d-任务②状态历史写入自动取消" ($histE -ge 3) "history=$histE"
Assert "G2e-任务②发布者收到退回通知" ($notE.Count -ge 1) "count=$($notE.Count)"

# G3 断言：任务③ 正常自动确认收货并结算
$wFz1 = Get-Wallet $zhang.token
Assert "G3-任务③自动确认收货订单已完成" ($stF -eq "已完成") "status=$stF"
Assert "G3b-任务③接单者报酬+2到账" ($wFz1.balance -eq ($wFz0.balance + 2)) "balance=$($wFz1.balance)"

# G4 断言：任务③ 防御逻辑（结算失败订单照常完成；无 G 的结算收入流水证明未支付报酬）
Assert "G4-防御场景结算失败订单仍已完成" ($stG -eq "已完成") "status=$stG"
$zhInG = @((Call-Api "GET" "/api/wallet/log/page?pageNum=1&pageSize=50&logType=4" $zhang.token).data.records | Where-Object { $_.relatedOrderId -eq $oidG })
Assert "G4b-防御场景结算未执行(无G收入流水)" ($zhInG.Count -eq 0) "income=$($zhInG.Count)"

# ============================================================
# 清理：删除测试数据 & 恢复初始钱包/信誉分
# ============================================================
Write-Host ""
Write-Host "==== 清理测试数据并恢复初始状态 ===="
$ids = ($testOrderIds | ForEach-Object { "$_" }) -join ","
if ($ids) {
    Db "DELETE FROM t_order_status_history WHERE order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_message WHERE order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_evaluation WHERE order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_notification WHERE order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_credit_log WHERE related_order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_wallet_log WHERE related_order_id IN ($ids)" | Out-Null
    Db "DELETE FROM t_order WHERE order_id IN ($ids)" | Out-Null
    Write-Host "已删除测试订单及关联数据（订单ID: $ids）"
}
foreach ($uid in $initWallet.Keys) {
    Db "UPDATE t_wallet SET balance = $($initWallet[$uid].balance), frozen_balance = $($initWallet[$uid].frozen) WHERE user_id = $uid" | Out-Null
}
foreach ($uid in $initCredit.Keys) {
    Db "UPDATE t_user SET credit_score = $($initCredit[$uid]) WHERE user_id = $uid" | Out-Null
}
Write-Host "钱包与信誉分已恢复初始状态"
$tmpUid = Db "SELECT user_id FROM t_user WHERE student_no = '$tempNo'"
if ($tmpUid) {
    Db "DELETE FROM t_wallet WHERE user_id = $tmpUid" | Out-Null
    Db "DELETE FROM t_user WHERE user_id = $tmpUid" | Out-Null
    Write-Host "已删除临时注册账号 $tempNo"
}

# 恢复后校验（两侧均转数值比较，避免 "100.00" 与 100 的字符串比较陷阱）
$chk1 = Db-Cells "SELECT balance, frozen_balance FROM t_wallet WHERE user_id = 1"
$chk11 = Db "SELECT credit_score FROM t_user WHERE user_id = 11"
Assert "清理-小李钱包已恢复" ([double]$chk1[0] -eq $initWallet[1].balance -and [double]$chk1[1] -eq $initWallet[1].frozen) "balance=$($chk1[0]) frozen=$($chk1[1])"
Assert "清理-小张信誉分已恢复" ([int]$chk11 -eq $initCredit[11]) "credit=$chk11"

# ============================================================
# 汇总
# ============================================================
Write-Host ""
Write-Host "==== 回归测试汇总 ===="
$fail = @($results | Where-Object { $_.Result -eq "FAIL" })
$results | Format-Table Case, Result, Detail -AutoSize | Out-String -Width 200 | Write-Host
Write-Host ("总计 {0} 项断言，PASS {1}，FAIL {2}" -f $results.Count, ($results.Count - $fail.Count), $fail.Count)
if ($fail.Count -gt 0) {
    Write-Host "--- FAIL 明细 ---"
    $fail | ForEach-Object { Write-Host ("  {0} :: {1}" -f $_.Case, $_.Detail) }
}
