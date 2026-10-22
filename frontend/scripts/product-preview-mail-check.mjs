// One real mail round trip against the already-running, owned product preview.
// Creates its own random account; never changes the four human experience accounts.
import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { randomBytes } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const runtime = resolve(root, 'backend/target/product-preview')
const checks = []
let stage = 'ownership', proof = { status: 'failed', started_at: new Date().toISOString(), checks }
const mark = name => checks.push({ name, passed: true })
const ensure = (condition, message) => assert.ok(condition, message)
const timeout = () => AbortSignal.timeout(8000)

try {
  const config = JSON.parse(await readFile(resolve(runtime, 'config.json'), 'utf8'))
  ensure(/^[a-f0-9]{32}$/.test(config.run_id || ''), 'Invalid preview run identity')
  ensure(/^[a-f0-9]{32}$/.test(config.nonce || ''), 'Invalid preview ownership nonce')
  ensure(config.database === `coursehub_product_preview_${config.run_id}`, 'Refusing a non-preview database')
  ensure(config.web_port === 3001 && config.mailbox_port === 3002, 'Expected local product preview ports')
  const base = 'http://localhost:3001', mailbox = 'http://localhost:3002'
  // No registration, login or other API request occurs before this ownership check.
  const ownershipResponse = await fetch(`${base}/__preview`, { signal: timeout(), redirect: 'error' })
  ensure(ownershipResponse.status === 200, 'Preview ownership endpoint is unavailable')
  const ownership = await ownershipResponse.json()
  ensure(ownership.run_id === config.run_id && ownership.database === config.database && ownership.nonce === config.nonce,
    'Running preview does not match the local guarded configuration')
  proof.run_id = config.run_id
  proof.database = config.database
  mark('running-preview-ownership')

  async function api(path, { method = 'GET', data, token, expected = 200, http = 200 } = {}) {
    ensure(path.startsWith('/api/'), 'Only same-preview API paths are permitted')
    const response = await fetch(base + path, {
      method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(data === undefined ? {} : { body: JSON.stringify(data) }), signal: timeout(), redirect: 'error'
    })
    proof.last_response = { stage, http_status: response.status }
    ensure(response.status === http, `Unexpected HTTP status in ${stage}`)
    const body = await response.json()
    proof.last_response.business_code = body.code
    ensure(body.code === expected, `Unexpected business status in ${stage}`)
    return body.data
  }
  async function waitForMail(email, purpose) {
    ensure(/^[a-z0-9_]+@example\.test$/.test(email), 'Mail must stay within example.test')
    const deadline = Date.now() + 15000
    while (Date.now() < deadline) {
      const response = await fetch(`${mailbox}/api/messages`, { signal: timeout(), redirect: 'error' })
      ensure(response.status === 200, 'Local mailbox is unavailable')
      const messages = await response.json()
      const message = messages.find(row => row.purpose === purpose && row.recipients?.includes(email))
      if (message) {
        ensure(/^\d{6}$/.test(message.code), 'Local mail has no valid six-digit code')
        return message.code
      }
      await delay(250)
    }
    throw new Error(`No ${purpose} email reached the local inbox`)
  }
  const username = `mail_${randomBytes(6).toString('hex')}`
  const email = `${username}@example.test`
  const originalPassword = `Mail-${randomBytes(6).toString('hex')}!`
  const newPassword = `Reset-${randomBytes(6).toString('hex')}!`
  ensure(!config.accounts.some(account => account.username === username || account.email === email), 'Fixture must be independent of experience accounts')
  proof.fixture = { username, email }

  stage = 'register-and-login'
  await api('/api/auth/register', { method: 'POST', data: { username, password: originalPassword, nickname: '邮件闭环测试', department: '产品体验学院' } })
  const original = await api('/api/auth/login', { method: 'POST', data: { username, password: originalPassword } })
  ensure(original?.accessToken && original?.refreshToken, 'Registration login must issue credentials')
  const before = await api('/api/auth/userinfo', { token: original.accessToken })
  ensure(before.username === username && !config.accounts.some(account => Number(account.user_id) === Number(before.user_id)), 'Login must belong to the independent fixture account')
  proof.fixture.user_id = before.user_id
  mark('register-and-login')

  stage = 'send-binding-mail'
  const binding = await api('/api/user/security/email/code', { method: 'POST', token: original.accessToken, data: { old_password: originalPassword, email } })
  ensure(binding?.request_id, 'Binding must return its request ID')
  const bindingCode = await waitForMail(email, 'BIND')
  mark('binding-email-delivered-via-local-smtp')
  stage = 'verify-binding'
  await api('/api/user/security/email', { method: 'PUT', token: original.accessToken, data: { old_password: originalPassword, email, request_id: binding.request_id, code: bindingCode } })
  const security = await api('/api/user/security', { token: original.accessToken })
  ensure(security?.recovery_email_verified === true && security.recovery_email_masked?.endsWith('@example.test'), 'Bound recovery email must be verified')
  mark('binding-verified')

  stage = 'public-password-reset-mail'
  const reset = await api('/api/auth/password-reset/code', { method: 'POST', data: { username, email } })
  ensure(reset?.request_id, 'Public reset must return its request ID')
  const resetCode = await waitForMail(email, 'RESET')
  mark('public-reset-email-delivered-via-local-smtp')
  stage = 'reset-password'
  await api('/api/auth/password-reset', { method: 'POST', data: { username, email, request_id: reset.request_id, code: resetCode, new_password: newPassword } })
  mark('public-password-reset')

  stage = 'old-access-rejected'
  const revoked = await fetch(`${base}/api/auth/userinfo`, { headers: { Authorization: `Bearer ${original.accessToken}` }, signal: timeout(), redirect: 'error' })
  ensure(revoked.status === 401, 'The old access token must be revoked after password reset')
  await revoked.arrayBuffer()
  mark('old-access-token-revoked')
  stage = 'old-refresh-rejected'
  await api('/api/auth/refresh', { method: 'POST', data: { refreshToken: original.refreshToken }, expected: 401 })
  mark('old-refresh-token-revoked')
  stage = 'old-password-rejected'
  await api('/api/auth/login', { method: 'POST', data: { username, password: originalPassword }, expected: 400 })
  mark('old-password-rejected')
  stage = 'new-password-login'
  const current = await api('/api/auth/login', { method: 'POST', data: { username, password: newPassword } })
  ensure(current?.accessToken && current?.refreshToken, 'The new password must issue fresh credentials')
  const after = await api('/api/auth/userinfo', { token: current.accessToken })
  ensure(Number(after.user_id) === Number(before.user_id), 'The new session must belong to the same independent account')
  mark('new-password-login')
  proof.status = 'passed'
  proof.experience_accounts_modified = false
  delete proof.last_response
} catch (error) {
  // Do not serialize assertions, requests, payloads or stack traces containing credentials.
  proof.failed_stage = stage
  proof.failure = error instanceof assert.AssertionError ? error.message : error.name === 'TimeoutError' ? 'Local request timed out' : 'Local mail check failed; inspect the failed stage'
  process.exitCode = 1
} finally {
  proof.finished_at = new Date().toISOString()
  const name = `mail-check-${proof.started_at.replace(/[:.]/g, '-')}.json`
  await writeFile(resolve(runtime, name), JSON.stringify(proof, null, 2) + '\n', 'utf8')
  await writeFile(resolve(runtime, 'mail-check-latest.json'), JSON.stringify(proof, null, 2) + '\n', 'utf8')
  console.log(JSON.stringify({ status: proof.status, passed: checks.length, failed_stage: proof.failed_stage, evidence: `backend/target/product-preview/${name}` }))
}
