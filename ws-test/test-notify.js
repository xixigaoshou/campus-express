// 通知推送测试：小李(发布者#1)连接WS → 小熊抢单#1 → 断言小李收到 new_notification
const fs = require('fs')
const http = require('http')

let raw = fs.readFileSync(process.env.TEMP + '/ws-test-tokens.json', 'utf8')
if (raw.charCodeAt(0) === 0xfeff) raw = raw.slice(1)
const tokens = JSON.parse(raw)

const ws = new WebSocket(`ws://localhost:8080/ws?token=${tokens.token1}`)

ws.addEventListener('open', () => {
  console.log('WS connected (publisher 小李)')
  const req = http.request({
    host: 'localhost', port: 8080, path: '/api/order/take-order?orderId=1', method: 'PUT',
    headers: { 'Authorization': 'Bearer ' + tokens.token16 }
  }, (res) => {
    let b = ''
    res.on('data', (c) => (b += c))
    res.on('end', () => console.log('take order http=' + res.statusCode + ' body=' + b))
  })
  req.end()
})

ws.addEventListener('message', (event) => {
  const text = event.data.toString()
  console.log('WS received: ' + text)
  if (text.includes('new_notification')) {
    console.log('PASS: notification push works')
    process.exit(0)
  } else if (text.includes('new_message')) {
    console.log('INFO: got message push (order #1 conversation), still waiting for notification...')
  }
})

ws.addEventListener('error', (e) => { console.log('WS error: ' + e.message); process.exit(1) })
setTimeout(() => { console.log('FAIL: timeout, no notification push'); process.exit(1) }, 8000)
