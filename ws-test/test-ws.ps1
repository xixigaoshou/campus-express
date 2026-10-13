Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Net.WebSockets

$ErrorActionPreference = "Stop"
$tokens = Get-Content "$env:TEMP\ws-test-tokens.json" | ConvertFrom-Json
$cts = [System.Threading.CancellationToken]::new([int64]5000)  # 5s timeout

# 1. 小熙建立 WebSocket 连接
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$uri = "ws://localhost:8080/ws?token=$($tokens.token16)"
$ws.ConnectAsync([Uri]$uri, $cts).GetAwaiter().GetResult()
Write-Output "WS connected, state=$($ws.State)"

# 2. 小李向订单#32 发送留言（接收者为小熙）
$client = [System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $tokens.token1)
$body = [System.Net.Http.StringContent]::new('{"orderId":32,"content":"WS push test"}',[Text.Encoding]::UTF8,"application/json")
$r = $client.PostAsync("http://localhost:8080/api/message/send", $body).Result
Write-Output "send msg http=$($r.StatusCode) body=$([Text.Encoding]::UTF8.GetString($r.Content.ReadAsByteArrayAsync().Result))"

# 3. 等待并读取 WebSocket 推送
$buffer = [byte[]]::new(2048)
$seg = [ArraySegment[byte]]::new($buffer)
$recvTask = $ws.ReceiveAsync($seg, [System.Threading.CancellationToken]::None)
if ($recvTask.Wait(5000)) {
    $msg = [Text.Encoding]::UTF8.GetString($buffer, 0, $recvTask.Result.Count)
    Write-Output "WS received: $msg"
    if ($msg -match 'new_message') { Write-Output "PASS: realtime push works" }
    else { Write-Output "FAIL: unexpected payload" }
} else {
    Write-Output "FAIL: no push received within 5s"
}

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", $cts).GetAwaiter().GetResult()
