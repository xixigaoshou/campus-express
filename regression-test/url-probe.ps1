# 抓取 Call-Api 实际发出的请求（本地监听 8090）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:8090/")
$listener.Start()

$client = [System.Net.Http.HttpClient]::new()
$method = [System.Net.Http.HttpMethod]::new("GET")
$oidA = 52
$path = "/api/message/order-page/$oidA?pageNum=1&pageSize=20"
Write-Host ("客户端URL串: {0}" -f "http://localhost:8090$path")

# 异步发送，同时接收
$task = $client.SendAsync([System.Net.Http.HttpRequestMessage]::new($method, "http://localhost:8090$path"))
$ctx = $listener.GetContext()
Write-Host ("服务端收到的 RawUrl: [{0}]" -f $ctx.Request.RawUrl)
Write-Host ("服务端收到的 Url: [{0}]" -f $ctx.Request.Url.AbsoluteUri)
$resp = $task.Result
Write-Host ("客户端收到 HTTP: {0}" -f $resp.StatusCode)
$ctx.Response.StatusCode = 200
$ctx.Response.Close()
$client.Dispose()
$listener.Stop()
