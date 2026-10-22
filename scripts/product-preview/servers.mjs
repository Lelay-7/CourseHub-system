// Loopback-only static preview, API proxy and a local recovery-email inbox.
import http from 'node:http'
import net from 'node:net'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import { decodeRecoveryMime } from '../../frontend/scripts/account-recovery-smtp.mjs'

const configFile = path.resolve(process.argv[2])
const config = JSON.parse(fs.readFileSync(configFile, 'utf8'))
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const dist = path.join(root, 'frontend/dist')
const inboxFile = path.join(path.dirname(configFile), 'mailbox.json')
let messages = fs.existsSync(inboxFile) ? JSON.parse(fs.readFileSync(inboxFile, 'utf8')) : []
const allowedHost = (req, port) => [`localhost:${port}`, `127.0.0.1:${port}`].includes(req.headers.host)
const json = (res, code, value) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)) }

const smtp = net.createServer(socket => {
  socket.setTimeout(30_000, () => socket.destroy())
  let input = '', data = false, lines = [], sender = false, recipients = [], bytes = 0
  const reset = () => { data = false; lines = []; sender = false; recipients = []; bytes = 0 }
  const reply = text => socket.write(text + '\r\n')
  reply('220 localhost CourseHub product preview SMTP')
  socket.on('data', chunk => {
    input += chunk.toString('latin1')
    if (input.length > 1_048_576) { socket.destroy(); return }
    let end
    while ((end = input.indexOf('\r\n')) >= 0) {
      const line = input.slice(0, end); input = input.slice(end + 2)
      if (data) {
        if (line === '.') {
          try {
            const decoded = decodeRecoveryMime(lines.join('\r\n'))
            messages.unshift({ id: randomUUID(), received_at: new Date().toISOString(), recipients: [...recipients], ...decoded })
            messages = messages.slice(0, 100)
            fs.writeFileSync(inboxFile + '.tmp', JSON.stringify(messages, null, 2), 'utf8')
            fs.renameSync(inboxFile + '.tmp', inboxFile)
            reset(); reply('250 local message accepted')
          } catch { reset(); reply('554 expected CourseHub recovery MIME') }
        } else {
          bytes += line.length + 2
          if (bytes > 1_048_576) { socket.destroy(); return }
          lines.push(line.startsWith('..') ? line.slice(1) : line)
        }
        continue
      }
      if (/^(EHLO|HELO)\s+\S+/i.test(line)) { reset(); reply('250-localhost\r\n250 8BITMIME') }
      else if (/^MAIL FROM:/i.test(line)) { reset(); sender = true; reply('250 accepted') }
      else if (/^RCPT TO:/i.test(line)) {
        const recipient = /^RCPT TO:\s*<([^>]+)>\s*(?:[A-Z].*)?$/i.exec(line)?.[1]?.toLowerCase()
        if (!sender) reply('503 MAIL required')
        else if (!recipient || !/^[a-z0-9._+-]+@example\.test$/i.test(recipient)) reply('550 only local example.test recipients are accepted')
        else { recipients.push(recipient); reply('250 accepted') }
      } else if (/^DATA$/i.test(line)) {
        if (!sender || !recipients.length) reply('503 MAIL and RCPT required')
        else { data = true; reply('354 end with CRLF dot CRLF') }
      } else if (/^RSET$/i.test(line)) { reset(); reply('250 reset') }
      else if (/^NOOP$/i.test(line)) reply('250 ok')
      else if (/^QUIT$/i.test(line)) { reply('221 bye'); socket.end() }
      else reply('502 unsupported command')
    }
  })
  socket.on('error', () => {})
})

const inboxHtml = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CourseHub 本地测试收件箱</title><style>body{font:16px/1.6 system-ui,sans-serif;background:#f4f6f8;color:#173443;margin:0}main{max-width:880px;margin:40px auto;padding:24px}h1{font-size:28px}p{color:#536575}article{background:white;border:1px solid #dce4e8;border-radius:14px;padding:18px;margin:14px 0}code{font-size:28px;letter-spacing:5px;font-weight:700}button{padding:10px 20px;background:#173443;color:white;border:0;border-radius:8px;cursor:pointer}.meta{font-size:14px;color:#536575}</style><main><h1>本地测试收件箱</h1><p>仅用于本次体验环境，接收 example.test 测试邮箱。验证码不会发往外部邮箱。绑定邮箱时可输入一个新的 @example.test 地址。</p><button id="refresh">刷新邮件</button><section id="messages" aria-live="polite"></section></main><script>async function refresh(){const response=await fetch('/api/messages');const rows=await response.json();const host=document.querySelector('#messages');host.replaceChildren();if(!rows.length){host.textContent='暂无邮件。请先在网站发送绑定或找回验证码。';return;}for(const row of rows){const card=document.createElement('article');for(const [tag,text] of [['strong',row.purpose==='BIND'?'绑定找回邮箱':'找回密码'],['div',row.recipients.join(', ')],['code',row.code],['div',new Date(row.received_at).toLocaleString()]]){const child=document.createElement(tag);child.textContent=text;card.appendChild(child);}host.appendChild(card);}}document.querySelector('#refresh').onclick=refresh;refresh();setInterval(refresh,3000);</script></html>`
const mailbox = http.createServer((req, res) => {
  if (!allowedHost(req, config.mailbox_port)) return json(res, 403, { error: 'Loopback Host required' })
  if (req.method !== 'GET') return json(res, 405, { error: 'Read-only inbox' })
  if (req.url === '/health') return json(res, 200, { ready: true })
  if (req.url === '/api/messages') return json(res, 200, messages)
  if (req.url !== '/') return json(res, 404, { error: 'Not found' })
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(inboxHtml)
})

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.woff2': 'font/woff2', '.ico': 'image/x-icon' }
const web = http.createServer((req, res) => {
  if (!allowedHost(req, config.web_port)) return json(res, 403, { error: 'Loopback Host required' })
  if (req.url === '/__preview' && req.method === 'GET') return json(res, 200, { run_id: config.run_id, database: config.database, nonce: config.nonce })
  if (req.url === '/api' || req.url.startsWith('/api/')) {
    const proxy = http.request({ hostname: '127.0.0.1', port: config.api_port, path: req.url, method: req.method, headers: { ...req.headers, host: `127.0.0.1:${config.api_port}` } }, upstream => {
      res.writeHead(upstream.statusCode, upstream.headers); upstream.pipe(res)
    })
    proxy.setTimeout(30_000, () => proxy.destroy())
    proxy.on('error', () => { if (!res.headersSent) json(res, 502, { code: 502, message: '体验后端尚未就绪，请稍后重试' }); else res.destroy() })
    req.on('aborted', () => proxy.destroy()); req.pipe(proxy); return
  }
  if (!['GET', 'HEAD'].includes(req.method)) return json(res, 405, { error: 'Unsupported method' })
  let pathname
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname) } catch { return json(res, 400, { error: 'Invalid URL' }) }
  let target = path.resolve(dist, '.' + pathname)
  if (target !== dist && !target.startsWith(dist + path.sep)) return json(res, 403, { error: 'Invalid path' })
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
    if (path.extname(pathname)) return json(res, 404, { error: 'Not found' })
    target = path.join(dist, 'index.html')
  }
  if (!fs.existsSync(target)) return json(res, 503, { error: 'Frontend build pending' })
  res.writeHead(200, { 'Content-Type': types[path.extname(target)] || 'application/octet-stream', 'Cache-Control': path.basename(target) === 'index.html' ? 'no-store' : 'public, max-age=3600', 'X-Content-Type-Options': 'nosniff' })
  if (req.method === 'HEAD') return res.end()
  fs.createReadStream(target).on('error', () => res.destroy()).pipe(res)
})
for (const [server, port] of [[smtp, config.smtp_port], [mailbox, config.mailbox_port], [web, config.web_port]]) {
  server.on('error', error => { console.error(error.message); process.exit(1) })
  await new Promise(resolve => server.listen(port, '127.0.0.1', resolve))
}
console.log('Local web, mailbox and SMTP ready. No external delivery.')
