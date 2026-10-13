$ErrorActionPreference = "Stop"
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/user/login" -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes('{"loginName":"2024001","password":"123456"}')) -ContentType "application/json"
$token = $login.data.token
$r = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/log/page?pageNum=1&pageSize=50" -Headers @{ Authorization = "Bearer $token" }
$recs = $r.data.records
Write-Host "records count: $($recs.Count)"
foreach ($rec in $recs) {
    Write-Host ("type=[{0}] order=[{1}] amount=[{2}] match解冻=[{3}]" -f $rec.logTypeText, $rec.relatedOrderId, $rec.amount, ($rec.logTypeText -eq "订单解冻退回"))
}
$m = $recs | Where-Object { $_.logTypeText -eq "订单解冻退回" }
Write-Host "matched: $($null -ne $m)"
