import assert from 'node:assert/strict'
import { mkdir, realpath, writeFile } from 'node:fs/promises'
import { basename, dirname, isAbsolute, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { preview } from 'vite'
import { chromium } from 'playwright'
import { createRecoverySmtpReceiver } from './account-recovery-smtp.mjs'

// Run from frontend after a real production build. The coordinator starts Spring Boot
// only after this process writes {nonce,smtpPort}. No HTTP fixtures or browser routes.
const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const nonce = process.env.COURSEHUB_RECOVERY_E2E_NONCE
const checks = []
const shutdown = new AbortController()
let stage = 'environment', output, smtp, server, browser, timer, base
let failureReason, failureName, lastResponse, lastAction, failureClass
const assertionMessages = new Set()
const safeErrorMessages = new Set(['Expected actual backend JSON'])
const check = (condition, message) => { assertionMessages.add(message); assert.ok(condition, message) }
let pageErrors = 0, nonlocalRequests = 0
const contexts = new Set()
const fixture = { username: 'e2e_student', oldPassword: 'E2e-old-123', email: 'safe@example.test', contact: 'contact@example.test' }
const resetPassword = 'E2e-reset-123', changedPassword = 'E2e-change-123'
const status = (response, body) => ({ httpStatus: response.status(), ...(Number.isInteger(body?.code) ? { code: body.code } : {}) })
const passed = (name, statuses = []) => checks.push({ name, passed: true, ...(statuses.length ? { statuses } : {}) })
const stop = () => shutdown.abort()
let stdin = ''
const onInput = chunk => {
  stdin += chunk
  let end
  while ((end = stdin.indexOf('\n')) >= 0) {
    if (stdin.slice(0, end).trim() === 'shutdown') stop()
    stdin = stdin.slice(end + 1)
  }
  if (stdin.length > 1000) stdin = ''
}
process.stdin.setEncoding('utf8')
process.stdin.on('data', onInput)
process.on('SIGINT', stop)
process.on('SIGTERM', stop)

async function responseBody(response) {
  const header = response.headers()['content-type'] || ''
  const contentType = /^application\/json(?:\s*;|$)/i.test(header) ? 'application/json'
    : /^text\/html(?:\s*;|$)/i.test(header) ? 'text/html'
      : /^text\/plain(?:\s*;|$)/i.test(header) ? 'text/plain' : header ? 'other' : 'missing'
  lastResponse = { stage, httpStatus: response.status(), contentType }
  try { return await response.json() } catch { throw new Error('Expected actual backend JSON') }
}
function waitResponse(page, path, method) {
  const pending = page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === method)
  // Keep the original promise for assertions while handling its rejection immediately.
  // A sibling action may fail before this promise is awaited, then cleanup closes the page.
  pending.catch(() => {})
  return pending
}
async function request(page, path, method, action, expectedCode = 200) {
  const parentStage = stage
  stage = `${parentStage}-action`
  const responsePromise = waitResponse(page, path, method)
  await action()
  stage = `${parentStage}-wait-response`
  const response = await responsePromise
  stage = `${parentStage}-parse-json`
  const body = await responseBody(response)
  lastResponse = { ...lastResponse, ...status(response, body) }
  stage = `${parentStage}-assert-response`
  check(response.status() === 200 && body.code === expectedCode, 'Unexpected real backend response')
  stage = parentStage
  return { response, body, state: status(response, body) }
}
async function showMessage(page, text) {
  await page.locator('.el-message').filter({ hasText: text }).first().waitFor({ state: 'visible' })
}
async function assertCleared(page) {
  await page.waitForFunction(() => {
    const names = ['access_token', 'refresh_token', 'auth_session_id', 'user_info']
    if (names.some(key => localStorage.getItem(key) !== null || sessionStorage.getItem(key) !== null)) return false
    for (const key of Object.keys(localStorage)) {
      if (!key.startsWith('user-store') && !key.startsWith('user_store')) continue
      try {
        const value = JSON.parse(localStorage.getItem(key))
        if (value?.accessToken || value?.refreshToken || value?.userInfo || value?.scoreBalance) return false
      } catch { return false }
    }
    return true
  })
}
async function newSession() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 920 }, serviceWorkers: 'block' })
  contexts.add(ctx)
  const page = await ctx.newPage()
  page.setDefaultTimeout(10_000)
  page.setDefaultNavigationTimeout(15_000)
  page.on('pageerror', () => pageErrors++)
  page.on('request', req => {
    const url = new URL(req.url())
    if (['http:', 'https:'].includes(url.protocol) && url.origin !== new URL(base).origin) nonlocalRequests++
  })
  return { ctx, page }
}
async function login(session, password, succeeds = true, label = '') {
  const { page } = session
  const parentStage = stage, prefix = label ? `${parentStage}-${label}` : parentStage
  stage = `${prefix}-open-login`
  await page.goto(base + 'login?redirect=%2Fprofile%2Fsecurity')
  stage = `${prefix}-fill-login`
  await page.getByPlaceholder('请输入登录账号', { exact: true }).fill(fixture.username)
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password)
  const infoPromise = succeeds ? waitResponse(page, '/api/auth/userinfo', 'GET') : null
  stage = `${prefix}-login-button-count`
  const loginButton = page.locator('form.auth-form').getByRole('button', { name: '登录', exact: true })
  lastAction = { stage, buttonCount: await loginButton.count() }
  check(lastAction.buttonCount === 1, 'Exactly one login submit button must exist inside the auth form')
  stage = `${prefix}-login-response`
  const result = await request(page, '/api/auth/login', 'POST', () => loginButton.click(), succeeds ? 200 : 400)
  if (!succeeds) {
    stage = `${prefix}-rejection-message-and-session`
    await showMessage(page, '账号或密码错误')
    check(new URL(page.url()).pathname === '/login', 'Rejected old password must remain on login')
    await assertCleared(page)
    stage = parentStage
    return [result.state]
  }
  stage = `${prefix}-userinfo-response`
  const infoResponse = await infoPromise
  const info = await responseBody(infoResponse)
  lastResponse = { stage, ...status(infoResponse, info) }
  check(infoResponse.status() === 200 && info.code === 200 && info.data?.email === fixture.contact, 'Real login must load fixture user and independent contact email')
  stage = `${prefix}-security-navigation-and-message`
  await page.waitForURL('**/profile/security')
  await page.getByTestId('recovery-status').waitFor()
  await showMessage(page, '登录成功')
  stage = `${prefix}-issued-session-storage`
  const tokens = await page.evaluate(() => ({ access: localStorage.getItem('access_token'), refresh: localStorage.getItem('refresh_token') }))
  check(tokens.access && tokens.refresh, 'Real browser must persist both issued credentials')
  stage = parentStage
  return [result.state, status(infoResponse, info)]
}
async function oldCredentialsRejected(pair, api) {
  const accessResponse = await fetch(api + '/api/auth/userinfo', { headers: { Authorization: `Bearer ${pair.access}` }, signal: shutdown.signal })
  lastResponse = { stage, httpStatus: accessResponse.status }
  check(accessResponse.status === 401, 'Revoked access must be rejected by security filter')
  await accessResponse.arrayBuffer()
  const refreshResponse = await fetch(api + '/api/auth/refresh', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: pair.refresh }), signal: shutdown.signal
  })
  const refresh = await refreshResponse.json()
  lastResponse = { stage, httpStatus: refreshResponse.status, ...(Number.isInteger(refresh.code) ? { code: refresh.code } : {}) }
  // The existing public-auth contract wraps business failures in HTTP 200.
  check(refreshResponse.status === 200 && refresh.code === 401 && !refresh.data?.accessToken && !refresh.data?.refreshToken, 'Revoked refresh must reject credential version and issue no tokens')
  return [{ httpStatus: accessResponse.status }, { httpStatus: refreshResponse.status, code: refresh.code }]
}
async function closeSession(session) { contexts.delete(session.ctx); await session.ctx.close() }
async function run() {
  check(/^[a-f0-9]{32}$/.test(nonce || ''), 'A fresh 32hex nonce is required')
  output = resolve(frontend, 'output/playwright/account-recovery-e2e', nonce)
  await mkdir(output, { recursive: true })
  const apiUrl = new URL(process.env.COURSEHUB_RECOVERY_E2E_API || '')
  check(apiUrl.protocol === 'http:' && apiUrl.hostname === '127.0.0.1' && Number(apiUrl.port) > 0
    && apiUrl.pathname === '/' && !apiUrl.username && !apiUrl.password && !apiUrl.search && !apiUrl.hash, 'Only explicit local backend URLs are allowed')
  const api = apiUrl.origin
  const coordinator = resolve(process.env.COURSEHUB_RECOVERY_E2E_COORDINATOR || '')
  const backendTarget = await realpath(resolve(frontend, '../backend/target'))
  const parent = await realpath(dirname(coordinator))
  const coordinatorParent = relative(backendTarget, parent)
  check(basename(coordinator) === 'e2e-coordinator.json' && coordinatorParent && !isAbsolute(coordinatorParent)
    && coordinatorParent !== '..' && !coordinatorParent.startsWith('..\\') && !coordinatorParent.startsWith('../'), 'Coordinator must stay inside the current backend target run')
  stage = 'smtp-startup'
  smtp = await createRecoverySmtpReceiver()
  shutdown.signal.throwIfAborted()
  stage = 'preview-startup'
  server = await preview({ root: frontend, logLevel: 'silent', preview: {
    host: '127.0.0.1', port: 0, open: false,
    // Preserve the browser Host so Spring sees the same origin as a local reverse proxy.
    proxy: { '/api': { target: api, changeOrigin: false } }
  } })
  shutdown.signal.throwIfAborted()
  check(server.config.preview.proxy?.['/api']?.target === api, 'Resolved preview proxy must target the coordinated real backend')
  base = server.resolvedUrls.local[0]
  check(new URL(base).hostname === '127.0.0.1', 'Preview must listen only on loopback')
  stage = 'coordinator'
  await writeFile(coordinator, JSON.stringify({ nonce, smtpPort: smtp.port }) + '\n', { encoding: 'utf8', flag: 'wx' })
  stage = 'backend-readiness'
  const readyDeadline = Date.now() + 60_000
  let ready = false
  while (Date.now() < readyDeadline) {
    shutdown.signal.throwIfAborted()
    try {
      const response = await fetch(api + '/api/auth/userinfo', { signal: AbortSignal.any([shutdown.signal, AbortSignal.timeout(1000)]) })
      await response.arrayBuffer()
      if (response.status === 401) { ready = true; break }
    } catch { shutdown.signal.throwIfAborted() }
    await delay(200, undefined, { signal: shutdown.signal })
  }
  check(ready, 'Real protected endpoint must reach 401 within readiness deadline')
  passed(stage, [{ httpStatus: 401 }])
  stage = 'real-preview-proxy-readiness'
  const proxyResponse = await fetch(base + 'api/auth/userinfo', { signal: AbortSignal.any([shutdown.signal, AbortSignal.timeout(3000)]) })
  lastResponse = { stage, httpStatus: proxyResponse.status }
  await proxyResponse.arrayBuffer()
  check(proxyResponse.status === 401, 'Production preview proxy must reach the real protected endpoint')
  passed(stage, [{ httpStatus: proxyResponse.status }])
  stage = 'browser-startup'
  browser = await chromium.launch({ headless: true, timeout: 10_000 })
  shutdown.signal.throwIfAborted()

  stage = 'two-real-initial-logins'
  const initialA = await newSession(), initialB = await newSession()
  const initialStates = [...await login(initialA, fixture.oldPassword, true, 'a'), ...await login(initialB, fixture.oldPassword, true, 'b')]
  check(await initialA.page.getByTestId('recovery-status').innerText().then(text => text.includes('尚未绑定')), 'Fixture must start with independent unbound recovery email')
  passed(stage, initialStates)
  const resetOldPairs = []
  for (const session of [initialA, initialB]) resetOldPairs.push(await session.page.evaluate(() => ({
    access: localStorage.getItem('access_token'), refresh: localStorage.getItem('refresh_token')
  })))

  stage = 'binding-real-smtp'
  const binding = initialA.page.locator('.security-panels form').nth(0)
  await binding.getByLabel('原密码', { exact: true }).fill(fixture.oldPassword)
  await binding.getByLabel('待验证的找回邮箱', { exact: true }).fill(fixture.email)
  const bindCheckpoint = smtp.checkpoint()
  const sent = await request(initialA.page, '/api/user/security/email/code', 'POST', () => binding.getByRole('button', { name: '发送验证码', exact: true }).click())
  check(typeof sent.body.data?.request_id === 'string', 'Binding must return real request ID')
  await showMessage(initialA.page, '验证码已提交发送，请查看邮箱')
  const bindCode = await smtp.waitForMail('BIND', fixture.email, bindCheckpoint, { signal: shutdown.signal })
  passed(stage, [sent.state])

  stage = 'wrong-binding-code-preserves-form'
  const wrongCode = bindCode === '000000' ? '111111' : '000000'
  await binding.getByLabel('邮箱验证码', { exact: true }).fill(wrongCode)
  const wrong = await request(initialA.page, '/api/user/security/email', 'PUT', () => binding.getByRole('button', { name: '验证并保存找回邮箱', exact: true }).click(), 400)
  check(wrong.body.message?.includes('验证码错误'), 'Backend must specifically reject wrong code')
  await showMessage(initialA.page, '验证码错误')
  check(await binding.getByLabel('原密码', { exact: true }).inputValue() === fixture.oldPassword
    && await binding.getByLabel('待验证的找回邮箱', { exact: true }).inputValue() === fixture.email
    && await binding.getByLabel('邮箱验证码', { exact: true }).inputValue() === wrongCode, 'Rejected code must preserve browser form')
  passed(stage, [wrong.state])

  stage = 'correct-binding-persists-state'
  await binding.getByLabel('邮箱验证码', { exact: true }).fill(bindCode)
  const securityPromise = waitResponse(initialA.page, '/api/user/security', 'GET')
  const bound = await request(initialA.page, '/api/user/security/email', 'PUT', () => binding.getByRole('button', { name: '验证并保存找回邮箱', exact: true }).click())
  const securityResponse = await securityPromise, security = await responseBody(securityResponse)
  check(securityResponse.status() === 200 && security.code === 200 && security.data?.recovery_email_verified === true
    && security.data?.recovery_email_masked === 's***@example.test', 'Saved recovery state must come from backend')
  await initialA.page.getByTestId('recovery-status').filter({ hasText: '已验证：s***@example.test' }).waitFor()
  await showMessage(initialA.page, '找回邮箱已验证并保存')
  check(await binding.getByLabel('原密码', { exact: true }).inputValue() === ''
    && await binding.getByLabel('邮箱验证码', { exact: true }).inputValue() === '', 'Successful binding must clear secrets')
  passed(stage, [bound.state, status(securityResponse, security)])

  stage = 'public-reset-real-smtp'
  const publicSession = await newSession(), forgot = publicSession.page
  await forgot.goto(base + 'forgot-password')
  await forgot.getByLabel('登录账号', { exact: true }).fill(fixture.username)
  await forgot.getByLabel('已绑定的找回邮箱', { exact: true }).fill(fixture.email)
  const resetCheckpoint = smtp.checkpoint()
  const resetSent = await request(forgot, '/api/auth/password-reset/code', 'POST', () => forgot.getByRole('button', { name: '申请验证码', exact: true }).click())
  check(typeof resetSent.body.data?.request_id === 'string', 'Public reset must return real request ID')
  await forgot.getByRole('status').filter({ hasText: '如果账号与邮箱匹配，验证码将发送至该邮箱' }).waitFor()
  const resetCode = await smtp.waitForMail('RESET', fixture.email, resetCheckpoint, { signal: shutdown.signal })
  passed(stage, [resetSent.state])

  stage = 'public-reset-success'
  await forgot.getByLabel('邮箱验证码', { exact: true }).fill(resetCode)
  await forgot.getByLabel('新密码', { exact: true }).fill(resetPassword)
  await forgot.getByLabel('确认新密码', { exact: true }).fill(resetPassword)
  const reset = await request(forgot, '/api/auth/password-reset', 'POST', () => forgot.getByRole('button', { name: '重设密码', exact: true }).click())
  await forgot.waitForURL('**/login')
  await showMessage(forgot, '密码已重设，请使用新密码登录')
  await assertCleared(forgot)
  passed(stage, [reset.state])
  stage = 'public-reset-revokes-both-initial-sessions'
  const resetRejections = []
  for (const pair of resetOldPairs) resetRejections.push(...await oldCredentialsRejected(pair, api))
  passed(stage, resetRejections)
  await closeSession(initialA); await closeSession(initialB); await closeSession(publicSession)

  stage = 'reset-old-password-rejected'
  const oldLogin = await newSession()
  passed(stage, await login(oldLogin, fixture.oldPassword, false))
  await closeSession(oldLogin)

  stage = 'two-real-reset-password-logins'
  const changeA = await newSession(), changeB = await newSession()
  passed(stage, [...await login(changeA, resetPassword, true, 'a'), ...await login(changeB, resetPassword, true, 'b')])
  const oldPairs = []
  for (const session of [changeA, changeB]) oldPairs.push(await session.page.evaluate(() => ({
    access: localStorage.getItem('access_token'), refresh: localStorage.getItem('refresh_token')
  })))
  check(oldPairs.every(pair => pair.access && pair.refresh), 'Both real sessions must hold old credentials')

  stage = 'original-password-change-logs-out-initiator'
  const passwordForm = changeA.page.locator('.security-panels form').nth(1)
  await passwordForm.getByLabel('原密码', { exact: true }).fill(resetPassword)
  await passwordForm.getByLabel('新密码', { exact: true }).fill(changedPassword)
  await passwordForm.getByLabel('确认新密码', { exact: true }).fill(changedPassword)
  const changed = await request(changeA.page, '/api/user/security/password', 'PUT', () => passwordForm.getByRole('button', { name: '修改密码并重新登录', exact: true }).click())
  await changeA.page.waitForURL('**/login')
  await showMessage(changeA.page, '密码已修改，请使用新密码登录')
  await assertCleared(changeA.page)
  passed(stage, [changed.state])

  stage = 'both-old-access-and-refresh-rejected'
  const rejectionStates = []
  for (const pair of oldPairs) rejectionStates.push(...await oldCredentialsRejected(pair, api))
  passed(stage, rejectionStates)

  stage = 'other-session-protected-request-refresh-and-cleanup'
  const protectedPromise = waitResponse(changeB.page, '/api/user/security', 'GET')
  const refreshPromise = waitResponse(changeB.page, '/api/auth/refresh', 'POST')
  await changeB.page.reload()
  const protectedResponse = await protectedPromise
  check(protectedResponse.status() === 401, 'Other browser must make a real protected request with revoked access')
  const refreshed = await refreshPromise, refreshBody = await responseBody(refreshed)
  check(refreshed.status() === 200 && refreshBody.code === 401 && !refreshBody.data?.accessToken, 'Browser refresh must also be rejected by backend')
  await changeB.page.waitForURL('**/login')
  await showMessage(changeB.page, '登录已过期，请重新登录')
  await assertCleared(changeB.page)
  passed(stage, [{ httpStatus: protectedResponse.status() }, status(refreshed, refreshBody)])

  stage = 'changed-password-login-success'
  const finalLogin = await newSession()
  passed(stage, await login(finalLogin, changedPassword))
  stage = 'browser-and-smtp-integrity'
  smtp.assertHealthy()
  check(smtp.checkpoint() === 2, 'Exactly two real recovery messages expected')
  check(pageErrors === 0 && nonlocalRequests === 0, 'Browser must have no page errors or remote requests')
  passed(stage)
}

let success = false
// Closing an active browser interrupts pending Playwright waits. A launch in progress
// finishes within its own bound, checks the aborted signal, then reaches cleanup.
const onAbort = () => { browser?.close().catch(() => {}) }
shutdown.signal.addEventListener('abort', onAbort, { once: true })
try {
  timer = setTimeout(stop, 120_000)
  await run()
  success = true
} catch (error) {
  // Playwright exception text can contain filled passwords/codes. Do not log it.
  if (error instanceof assert.AssertionError && assertionMessages.has(error.message)) failureReason = error.message
  else if (safeErrorMessages.has(error?.message)) failureReason = error.message
  if (/strict mode violation/i.test(error?.message || '')) failureClass = 'strict-selector'
  failureName = ['AssertionError', 'TimeoutError', 'Error', 'AbortError', 'TypeError', 'SyntaxError'].includes(error?.name) ? error.name : 'Error'
  process.exitCode = 1
} finally {
  clearTimeout(timer)
  shutdown.signal.removeEventListener('abort', onAbort)
  shutdown.abort()
  process.stdin.removeListener('data', onInput); process.stdin.pause()
  process.removeListener('SIGINT', stop); process.removeListener('SIGTERM', stop)
  const cleanup = await Promise.allSettled([browser?.close(), smtp?.close(), server?.close()])
  if (cleanup.some(result => result.status === 'rejected')) {
    success = false; process.exitCode = 1; stage = 'resource-cleanup'
  }
  if (output) {
    await writeFile(resolve(output, 'summary.json'), JSON.stringify({ nonce, passed: success, checks,
      ...(!success ? { failure: { stage, passed: false, ...(failureName ? { errorName: failureName } : {}),
        ...(failureReason ? { reason: failureReason } : {}), ...(failureClass ? { errorClass: failureClass } : {}),
        ...(lastAction ? { lastAction } : {}), ...(lastResponse ? { lastResponse } : {}) } } : {}) }, null, 2) + '\n', 'utf8')
  }
  console.log(success ? 'Recovery real-backend browser checks passed.' : `Recovery real-backend browser checks failed at stage: ${stage}.`)
}
