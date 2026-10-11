import assert from 'node:assert/strict'
import net from 'node:net'
import readline from 'node:readline'
import { test } from 'node:test'
import { createRecoverySmtpReceiver, decodeRecoveryMime } from './account-recovery-smtp.mjs'

const fixtureText = '绑定找回邮箱的验证码：123456。10分钟内有效。'
const mime = encoding => `Content-Type: text/plain;\r\n charset=UTF-8\r\nContent-Transfer-Encoding: ${encoding}\r\n\r\n` + (
  encoding === 'base64' ? Buffer.from(fixtureText).toString('base64') : encoding === 'quoted-printable'
    ? [...Buffer.from(fixtureText)].map(byte => '=' + byte.toString(16).padStart(2, '0')).join('').replace(/(.{60})/g, '$1=\r\n')
    : Buffer.from(fixtureText).toString('latin1'))

test('JavaMail style UTF-8 MIME decodes 8bit, base64 and quoted-printable with folded headers', () => {
  for (const encoding of ['8bit', 'base64', 'quoted-printable']) assert.deepEqual(decodeRecoveryMime(mime(encoding)), { code: '123456', purpose: 'BIND' })
  const multipart = `Content-Type: multipart/alternative; boundary="test-boundary"\r\n\r\n--test-boundary\r\n${mime('base64')}\r\n--test-boundary--\r\n`
  assert.deepEqual(decodeRecoveryMime(multipart), { code: '123456', purpose: 'BIND' })
})

async function client(receiver) {
  const socket = net.createConnection({ host: '127.0.0.1', port: receiver.port })
  const lines = readline.createInterface({ input: socket, crlfDelay: Infinity })[Symbol.asyncIterator]()
  async function reply(expected) {
    let line
    do { line = (await lines.next()).value; assert.ok(line, 'SMTP response required') } while (/^\d{3}-/.test(line))
    assert.equal(Number(line.slice(0, 3)), expected)
  }
  async function send(command, expected) { socket.write(command + '\r\n', 'latin1'); await reply(expected) }
  await reply(220)
  return { socket, reply, send }
}

test('local SMTP processes fragmented commands and MIME DATA without disk artifacts', async t => {
  const receiver = await createRecoverySmtpReceiver()
  let connection
  t.after(async () => { connection?.socket.destroy(); await receiver.close() })
  connection = await client(receiver)
  await connection.send('EHLO local-test', 250)
  await connection.send('MAIL FROM:<sender@example.test>', 250)
  await connection.send('RCPT TO:<safe@example.test>', 250)
  await connection.send('DATA', 354)
  const bytes = Buffer.from(mime('8bit') + '\r\n.\r\n', 'latin1')
  for (let offset = 0; offset < bytes.length; offset += 7) connection.socket.write(bytes.subarray(offset, offset + 7))
  await connection.reply(250)
  assert.equal(await receiver.waitForMail('BIND', 'safe@example.test'), '123456')
  receiver.assertHealthy()
  await connection.send('RSET', 250)
  await connection.send('QUIT', 221)
})

test('unexpected external SMTP recipient fails the acceptance sink', async t => {
  const receiver = await createRecoverySmtpReceiver()
  let connection
  t.after(async () => { connection?.socket.destroy(); await receiver.close() })
  connection = await client(receiver)
  await connection.send('HELO local-test', 250)
  await connection.send('MAIL FROM:<sender@example.test>', 250)
  await connection.send('RCPT TO:<unsafe@example.com>', 550)
  assert.throws(() => receiver.assertHealthy(), /recipient failure/)
  assert.equal(receiver.checkpoint(), 0)
})
