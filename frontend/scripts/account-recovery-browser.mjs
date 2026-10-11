import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { preview } from 'vite'
import { chromium } from 'playwright'

// Local production build plus intercepted HTTP fixtures; never sends mail or touches a real backend.
const output = resolve('output/playwright/account-recovery')
await mkdir(output, { recursive: true })
const server = await preview({ preview: { host: '127.0.0.1', port: 0, open: false } })
let browser
const errors = []
const user = { user_id: 1, user_type: 1, nickname: '同学甲', email: 'contact@example.com' }
const token = `e30.${Buffer.from(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 7200, credential_version: 0 })).toString('base64url')}.fixture`
async function context(theme, viewport, authenticated) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(({ theme, authenticated, token, user }) => {
    localStorage.setItem('coursehub-theme', theme)
    if (authenticated) {
      localStorage.setItem('access_token', token)
      localStorage.setItem('refresh_token', token)
      localStorage.setItem('user_info', JSON.stringify(user))
    }
  }, { theme, authenticated, token, user })
  const requests = []
  await ctx.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname
    if (req.method() !== 'GET') requests.push({ path, body: req.postDataJSON() })
    let data = {}
    if (path === '/api/user/security') data = { recovery_email_masked: 's***@example.com', recovery_email_verified: true }
    if (path.endsWith('/code')) data = { request_id: '11111111-1111-4111-8111-111111111111', message: '如果账号与邮箱匹配，验证码将发送至该邮箱' }
    if (path === '/api/auth/userinfo') data = user
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
  })
  const page = await ctx.newPage()
  page.on('pageerror', error => errors.push(error.message))
  return { ctx, page, requests }
}
async function noOverflow(page, width) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth) <= width + 1, 'Page must fit the viewport')
}
try {
  browser = await chromium.launch({ headless: true })
  const base = server.resolvedUrls.local[0]
  for (const theme of ['blue', 'wine', 'black']) {
    for (const [size, viewport] of [['desktop', { width: 1280, height: 920 }], ['mobile', { width: 390, height: 844 }]]) {
      for (const [name, path, authenticated] of [['security', '/profile/security', true], ['forgot', '/forgot-password', false]]) {
        const { ctx, page } = await context(theme, viewport, authenticated)
        try {
          await page.goto(base + path.slice(1))
          await page.getByRole('heading', { name: name === 'security' ? '账号安全' : '找回密码', exact: true }).waitFor()
          if (authenticated) await page.getByTestId('recovery-status').filter({ hasText: '已验证' }).waitFor()
          assert.equal(await page.locator('html').getAttribute('data-theme'), theme)
          await noOverflow(page, viewport.width)
          await page.screenshot({ path: resolve(output, `${name}-${theme}-${size}.png`), fullPage: true })
        } finally { await ctx.close() }
      }
    }
  }
  const forgot = await context('blue', { width: 1280, height: 920 }, false)
  try {
    const { page, requests } = forgot
    await page.goto(base + 'forgot-password')
    await page.getByLabel('登录账号', { exact: true }).fill('student01')
    await page.getByLabel('已绑定的找回邮箱', { exact: true }).fill('safe@example.com')
    await page.getByRole('button', { name: '申请验证码', exact: true }).click()
    await page.getByRole('status').filter({ hasText: '如果账号与邮箱匹配' }).waitFor()
    await page.getByLabel('邮箱验证码', { exact: true }).fill('123456')
    await page.getByLabel('新密码', { exact: true }).fill('new-pass')
    await page.getByLabel('确认新密码', { exact: true }).fill('wrong-pass')
    await page.getByRole('button', { name: '重设密码', exact: true }).click()
    await page.getByText('两次输入的密码不一致', { exact: true }).waitFor()
    assert.equal(requests.filter(r => r.path === '/api/auth/password-reset').length, 0)
    await page.getByLabel('确认新密码', { exact: true }).fill('new-pass')
    await page.getByRole('button', { name: '重设密码', exact: true }).click()
    await page.waitForURL('**/login')
    const body = requests.find(r => r.path === '/api/auth/password-reset').body
    assert.equal(body.request_id, '11111111-1111-4111-8111-111111111111')
    assert.equal(body.new_password, 'new-pass')
    assert.equal(await page.evaluate(() => localStorage.getItem('access_token')), null)
  } finally { await forgot.ctx.close() }
  const security = await context('black', { width: 390, height: 844 }, true)
  try {
    const { page, requests } = security
    await page.goto(base + 'profile/security')
    const form = page.locator('.security-panels form').nth(1)
    await form.getByLabel('原密码', { exact: true }).fill('old-pass')
    await form.getByLabel('新密码', { exact: true }).fill('new-pass')
    await form.getByLabel('确认新密码', { exact: true }).fill('new-pass')
    await form.getByRole('button', { name: '修改密码并重新登录', exact: true }).click()
    await page.waitForURL('**/login')
    assert.equal(requests.filter(r => r.path === '/api/user/security/password').length, 1)
    assert.equal(await page.evaluate(() => localStorage.getItem('access_token')), null)
    assert.equal(await page.evaluate(() => localStorage.getItem('refresh_token')), null)
  } finally { await security.ctx.close() }
  assert.deepEqual(errors, [])
  console.log('Recovery browser checks passed: 12 theme/viewport views, password confirmation, reset payload, session cleanup; no page errors.')
} finally {
  await browser?.close()
  await new Promise(resolveClose => server.httpServer.close(resolveClose))
}
