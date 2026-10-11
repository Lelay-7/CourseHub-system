import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import net from 'node:net'

// A deliberately local, ephemeral SMTP sink. Raw MIME and codes never leave memory.
export function decodeRecoveryMime(raw) {
  function textParts(part) {
    const divider = part.indexOf('\r\n\r\n')
    assert.ok(divider >= 0, 'SMTP message must have MIME headers')
    const header = part.slice(0, divider).replace(/\r\n[ \t]+/g, ' ')
    const body = part.slice(divider + 4)
    const contentType = /^Content-Type:\s*(.+)$/im.exec(header)?.[1] || 'text/plain'
    if (/^multipart\//i.test(contentType)) {
      const boundary = /boundary\s*=\s*(?:"([^"]+)"|([^;\s]+))/i.exec(contentType)
      assert.ok(boundary, 'MIME multipart boundary required')
      return body.split(`--${boundary[1] || boundary[2]}`).slice(1)
        .filter(value => !value.startsWith('--'))
        .flatMap(value => textParts(value.replace(/^\r\n/, '').replace(/\r\n$/, '')))
    }
    if (!/^text\/plain(?:\s*;|$)/i.test(contentType)) return []
    const charset = /charset\s*=\s*"?([^;"\s]+)/i.exec(contentType)?.[1]?.toLowerCase() || 'utf-8'
    assert.ok(['utf-8', 'utf8', 'us-ascii', 'ascii'].includes(charset), 'Unexpected MIME charset')
    const encoding = /^Content-Transfer-Encoding:\s*([^\s]+)/im.exec(header)?.[1]?.toLowerCase()
    let bytes
    if (encoding === 'base64') bytes = Buffer.from(body.replace(/\s/g, ''), 'base64')
    else if (encoding === 'quoted-printable') {
      const unfolded = body.replace(/=\r\n/g, '')
      const output = []
      for (let index = 0; index < unfolded.length; index++) {
        if (unfolded[index] === '=' && /^[0-9a-f]{2}$/i.test(unfolded.slice(index + 1, index + 3))) {
          output.push(Number.parseInt(unfolded.slice(index + 1, index + 3), 16)); index += 2
        } else output.push(unfolded.charCodeAt(index) & 255)
      }
      bytes = Buffer.from(output)
    } else {
      assert.ok(!encoding || ['7bit', '8bit', 'binary'].includes(encoding), 'Unexpected MIME encoding')
      bytes = Buffer.from(body, 'latin1')
    }
    return [bytes.toString('utf8')]
  }
  const text = textParts(raw).join('\n')
  const code = /验证码\s*[:：]\s*(\d{6})(?!\d)/u.exec(text)?.[1]
  assert.ok(code, 'Recovery verification code missing from SMTP MIME')
  const purpose = text.includes('绑定找回邮箱') ? 'BIND' : text.includes('找回密码') ? 'RESET' : null
  assert.ok(purpose, 'Recovery purpose missing from SMTP MIME')
  return { code, purpose }
}

export async function createRecoverySmtpReceiver() {
  const events = new EventEmitter(), sockets = new Set(), messages = []
  let fatal = false, closing = false
  const fail = () => { fatal = true; events.emit('change') }
  const server = net.createServer(socket => {
    sockets.add(socket)
    socket.setTimeout(20_000, () => { if (!closing) fail(); socket.destroy() })
    let input = '', data = false, lines = [], sender = false, recipients = [], size = 0
    const reset = () => { data = false; lines = []; sender = false; recipients = []; size = 0 }
    const reply = value => { if (!socket.destroyed) socket.write(value + '\r\n') }
    reply('220 localhost CourseHub local test SMTP')
    socket.on('data', chunk => {
      input += chunk.toString('latin1')
      if (input.length > 1_048_576) { fail(); socket.destroy(); return }
      let end
      while ((end = input.indexOf('\r\n')) >= 0) {
        const line = input.slice(0, end); input = input.slice(end + 2)
        if (data) {
          if (line === '.') {
            try {
              const decoded = decodeRecoveryMime(lines.join('\r\n'))
              messages.push({ ...decoded, recipients: [...recipients] })
              reset(); reply('250 message accepted'); events.emit('change')
            } catch { fail(); reset(); reply('554 invalid recovery MIME') }
          } else {
            size += line.length + 2
            if (size > 1_048_576) { fail(); socket.destroy(); return }
            lines.push(line.startsWith('..') ? line.slice(1) : line)
          }
          continue
        }
        if (/^(EHLO|HELO)\s+\S+/i.test(line)) { reset(); reply('250-localhost\r\n250 8BITMIME') }
        else if (/^MAIL FROM:/i.test(line)) { reset(); sender = true; reply('250 sender accepted') }
        else if (/^RCPT TO:/i.test(line)) {
          const recipient = /^RCPT TO:\s*<([^>]+)>\s*(?:[A-Z].*)?$/i.exec(line)?.[1]?.toLowerCase()
          if (!sender) reply('503 MAIL required')
          else if (!recipient || !/^[^\s@]+@example\.test$/i.test(recipient)) {
            fail(); reply('550 only example.test test recipients are accepted')
          } else { recipients.push(recipient); reply('250 recipient accepted') }
        } else if (/^DATA$/i.test(line)) {
          if (!sender || !recipients.length) reply('503 MAIL and RCPT required')
          else { data = true; reply('354 end with CRLF dot CRLF') }
        } else if (/^RSET$/i.test(line)) { reset(); reply('250 reset') }
        else if (/^NOOP$/i.test(line)) reply('250 ok')
        else if (/^QUIT$/i.test(line)) { reply('221 bye'); socket.end() }
        else { fail(); reply('502 unsupported test SMTP command') }
      }
    })
    socket.on('error', () => { if (!closing) fail() })
    socket.on('close', () => sockets.delete(socket))
  })
  server.on('error', () => { if (!closing) fail() })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => { server.removeListener('error', reject); resolve() })
  })
  return {
    port: server.address().port,
    checkpoint: () => messages.length,
    assertHealthy: () => assert.ok(!fatal, 'Local SMTP protocol or recipient failure'),
    async waitForMail(purpose, recipient, after = 0, { timeout = 10_000, signal } = {}) {
      return new Promise((resolve, reject) => {
        const clean = () => { clearTimeout(timer); events.removeListener('change', inspect); signal?.removeEventListener('abort', cancel) }
        const cancel = () => { clean(); reject(new Error('Local SMTP wait cancelled')) }
        const inspect = () => {
          if (fatal) { clean(); reject(new Error('Local SMTP protocol or recipient failure')); return }
          const mail = messages.slice(after).find(value => value.purpose === purpose && value.recipients.includes(recipient))
          if (mail) { clean(); resolve(mail.code) }
        }
        const timer = setTimeout(() => { clean(); reject(new Error('Local SMTP mail deadline')) }, timeout)
        events.on('change', inspect); signal?.addEventListener('abort', cancel, { once: true })
        if (signal?.aborted) cancel(); else inspect()
      })
    },
    async close() {
      closing = true
      for (const socket of sockets) socket.destroy()
      messages.length = 0
      events.removeAllListeners()
      await new Promise(resolve => server.close(resolve))
    }
  }
}
