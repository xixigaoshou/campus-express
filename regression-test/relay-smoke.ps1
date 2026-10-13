# ============================================================
# 转单接力链路冒烟测试（针对临时实例 8095）
# 流程：发布订单 → 小王接单 → 小王发起转单 → 小陈在接力专区看到 → 小陈接力接单
#        → 小陈再发起转单 → 小陈撤回转单
# ============================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = 'http://localhost:8095/api'

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Method           = $Method
        Uri              = "$base$Path"
        Headers          = $headers
        ContentType      = 'application/json; charset=utf-8'
        UseBasicParsing  = $true
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 6 -Compress
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    $resp = Invoke-WebRequest @params
    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}

function Login {
    param([string]$LoginName)
    $r = Invoke-Api -Method 'POST' -Path '/user/login' -Body @{ loginName = $LoginName; password = '123456' }
    if ($r.code -ne 200) { throw "登录失败 $LoginName : $($r.message)" }
    return $r.data.token
}

$tPublisher = Login '2024001'   # 小李（发布者）
$tWang      = Login '2024002'   # 小王（接单者）
$tChen      = Login '2024003'   # 小陈（接单者）
Write-Host "[OK] 三个账号登录成功"

# 1. 发布订单
$expect = (Get-Date).AddHours(3).ToString('yyyy-MM-dd HH:mm:ss')
$pub = Invoke-Api -Method 'POST' -Path '/order/publish' -Token $tPublisher -Body @{
    expressNo      = 'SF' + (Get-Random -Minimum 100000 -Maximum 999999)
    expressCompany = '顺丰速运'
    pickupLocation = '菜鸟驿站(转单测试)'
    deliverLocation = '1号楼305'
    expectTime     = $expect
    remark         = '转单接力链路测试'
    rewardAmount   = 6.5
}
if ($pub.code -ne 200) { throw "发布失败: $($pub.message)" }
$oid = $pub.data
Write-Host "[OK] 发布订单 #$oid"

# 2. 小王接单
$take = Invoke-Api -Method 'PUT' -Path "/order/take-order?orderId=$oid" -Token $tWang
if ($take.code -ne 200) { throw "接单失败: $($take.message)" }
Write-Host "[OK] 小王接单成功"

# 3. 小王发起转单
$tr = Invoke-Api -Method 'POST' -Path '/order/transfer' -Token $tWang -Body @{ orderId = $oid; reason = '临时有事/时间冲突' }
if ($tr.code -ne 200) { throw "发起转单失败: $($tr.message)" }
Write-Host "[OK] 小王发起转单：$($tr.message)"

# 3.1 重复发起应被拒绝
$dup = Invoke-Api -Method 'POST' -Path '/order/transfer' -Token $tWang -Body @{ orderId = $oid; reason = '临时有事/时间冲突' }
Write-Host "[CHK] 重复发起 -> code=$($dup.code) msg=$($dup.message)"

# 4. 小陈查看接力专区
$page = Invoke-Api -Method 'GET' -Path '/order/relay-page?pageNum=1&pageSize=20' -Token $tChen
$hit = @($page.data.records | Where-Object { $_.orderId -eq $oid })
if ($hit.Count -lt 1) { throw "接力专区未查询到订单 #$oid" }
Write-Host "[OK] 接力专区命中订单，转单原因=$($hit[0].transferReason) 原接单者=$($hit[0].takerName)"

# 4.1 小王本人应无法接力自己的转单
$self = Invoke-Api -Method 'PUT' -Path "/order/relay-take?orderId=$oid" -Token $tWang
Write-Host "[CHK] 本人接力自己的转单 -> code=$($self.code) msg=$($self.message)"

# 5. 小陈接力接单
$relay = Invoke-Api -Method 'PUT' -Path "/order/relay-take?orderId=$oid" -Token $tChen
if ($relay.code -ne 200) { throw "接力失败: $($relay.message)" }
Write-Host "[OK] 小陈接力接单成功：$($relay.message)"

# 5.1 接力后订单应离开接力专区
$page2 = Invoke-Api -Method 'GET' -Path '/order/relay-page?pageNum=1&pageSize=20' -Token $tChen
$still = @($page2.data.records | Where-Object { $_.orderId -eq $oid })
Write-Host "[CHK] 接力后仍在接力专区条数=$($still.Count)（应为0）"

# 6. 小陈再发起转单并撤回
$tr2 = Invoke-Api -Method 'POST' -Path '/order/transfer' -Token $tChen -Body @{ orderId = $oid; reason = '天气原因/下雨等' }
if ($tr2.code -ne 200) { throw "二次发起转单失败: $($tr2.message)" }
$wd = Invoke-Api -Method 'PUT' -Path "/order/transfer/cancel?orderId=$oid" -Token $tChen
if ($wd.code -ne 200) { throw "撤回转单失败: $($wd.message)" }
Write-Host "[OK] 小陈撤回转单成功"

# 7. 详情校验：taker 应为小陈、transfer_status 应为空
$detail = Invoke-Api -Method 'GET' -Path "/order/$oid" -Token $tChen
Write-Host "[CHK] 详情：状态=$($detail.data.order.status) 接单者=$($detail.data.order.takerName) 接力状态=$($detail.data.order.transferStatus)"

Write-Host "ORDER_ID=$oid"
Write-Host "ALL_DONE"