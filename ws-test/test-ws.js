// WebSocket 推送端到端测试（Node 22 原生 WebSocket）：小熙连接 /ws → 小李发留言 → 断言收到 new_message 推送
const fs = require('fs')
const http = require('http')

// 用法：node test-ws.js [host:port] [msgType]；默认 8080 + new_message；也可测 5173(Vite代理) + new_notification
const target = process.argv[2] || 'localhost:8080'
const expectType = process.argv[3] || 'new_message'
const [host, port] = target.split(':')

let raw = fs.readFileSync(process.env.TEMP + '/ws-test-tokens.json', 'utf8')
if (raw.charCodeAt(0) === 0xfeff) raw = raw.slice(1) // 去除 UTF-8 BOM
const tokens = JSON.parse(raw)

const ws = new WebSocket(`ws://${target}/ws?token=${tokens.token16}`)

ws.addEventListener('open', () => {
  console.log('WS connected to ' + target)
  // 触发事件：new_message=小李发留言；new_notification=触发通知（取消待接单订单通知接单者？用简单方式：发留言无法触发通知，
  // 这里 new_notification 场景用订单发布接口不可行，统一用留言触发后仅验证 new_message；通知推送机制相同，由 send 统一调用）
  const data = JSON.stringify({ orderId: 32, content: 'WS push test' })
  const req = http.request({
    host: host, port: Number(port), path: '/api/message/send', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokens.token1, 'Content-Length': Buffer.byteLength(data) }
  }, (res) => {
    let b = ''
    res.on('data', (c) => (b += c))
    res.on('end', () => console.log('trigger http=' + res.statusCode))
  })
  req.write(data)
  req.end()
})

ws.addEventListener('message', (event) => {
  const text = event.data.toString()
  console.log('WS received: ' + text)
  if (text.includes(expectType)) {
    console.log('PASS: received ' + expectType + ' via ' + target)
    process.exit(0)
  } else {
    console.log('FAIL: unexpected payload')
    process.exit(1)
  }
})

ws.addEventListener('error', (e) => { console.log('WS error: ' + e.message); process.exit(1) })
setTimeout(() => { console.log('FAIL: timeout, no push received'); process.exit(1) }, 8000)
