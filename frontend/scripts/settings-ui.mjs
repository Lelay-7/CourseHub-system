import assert from 'node:assert/strict'
import { mkdir, writeFile, realpath } from 'node:fs/promises'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// Production views, bounded HTTP fixtures. Database acceptance is run separately.
const output = resolve('output/playwright/settings-ui')
await mkdir(output, { recursive: true })
const server = await createServer({ server: { host: '127.0.0.1', port: 0, open: false, fs: { allow: [resolve('.'), await realpath('node_modules')] } }, define: { 'import.meta.env.VITE_USE_MOCK': 'false' } })
await server.listen()
const base = server.resolvedUrls.local[0]
const results = [], errors = []
let browser
const token = `e30.${Buffer.from(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 7200 })).toString('base64url')}.fixture`

async function fixture({ theme = 'blue', width = 1440, role = 1, failRead = false, failSave = false, authenticated = true } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 900 } })
  const user = { user_id: 1, user_type: role, username: 'settings_reader', nickname: '设置体验同学', email: 'contact@example.test', balance: 82 }
  const state = { user, reads: 0, writes: [], failRead, failSave, failSecurity: false, privacy: { contact_visible: 2, phone_visible: 1, email_visible: 2 } }
  await context.addInitScript(({ token, theme, user, authenticated }) => {
    if (!authenticated) { localStorage.setItem('coursehub-theme', theme); return }
    if (!localStorage.getItem('access_token')) {
      localStorage.setItem('access_token', token)
      localStorage.setItem('refresh_token', token)
      localStorage.setItem('user_info', JSON.stringify(user))
      localStorage.setItem('coursehub-theme', theme)
    }
  }, { token, theme, user, authenticated })
  await context.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    let data = { list: [], total: 0 }
    if (path === '/api/auth/userinfo') data = state.user
    if (path === '/api/chat/unread') data = { unread_count: 0 }
    if (path === '/api/chat/conversations') data = { items: [], next_cursor: null }
    if (path === '/api/user/security') {
      if (state.failSecurity) return route.fulfill({ status: 503, json: { code: 503, message: '测试注入：安全信息读取失败' } })
      data = { recovery_email_verified: true, recovery_email_masked: 's***@example.test' }
    }
    if (path === '/api/score/summary') data = { earned: 24, spent: 12, refunded: 5 }
    const course = { course_id: 1, course_name: 'Web 前端工程实践', credit: 3, teacher_name: '李老师', category_name: '软件开发', category_id: 1, course_type: 2, description: '通过课程实践学习网页设计与实现。', selected_count: 12, avg_rating: 4.5 }
    if (path === '/api/course/list') data = { list: [course], total: 1 }
    if (path === '/api/course/categories') data = [{ category_id: 1, category_name: '软件开发' }]
    if (path === '/api/course/1') data = { course, reviews: [], is_selected: false, is_favorited: false }
    if (path === '/api/contact/auth/my') {
      state.reads++
      if (state.failRead) return route.fulfill({ status: 503, json: { code: 503, message: '测试注入：读取失败' } })
      data = state.privacy
    }
    if (path === '/api/contact/auth/update') {
      state.writes.push(request.postDataJSON())
      if (state.failSave) return route.fulfill({ status: 503, json: { code: 503, message: '测试注入：保存失败' } })
      state.privacy = request.postDataJSON()
      data = null
    }
    await route.fulfill({ json: { code: 200, message: 'ok', data } })
  })
  const page = await context.newPage()
  page.setDefaultTimeout(8000)
  page.on('pageerror', error => errors.push(error.message))
  page.on('requestfailed', request => console.error('request failed', request.url(), request.failure()))
  page.on('console', message => { if (message.type() === 'error') console.error(message.text()) })
  return { context, page, state }
}

try {
  browser = await chromium.launch({ headless: true })
  if (!process.argv.includes('--layout') && !process.argv.includes('--ancillary')) {
  const broken = await fixture({ failRead: true })
  try {
    await broken.page.goto(base + 'profile/settings')
    await broken.page.getByText('隐私设置加载失败', { exact: true }).waitFor()
    const switches = broken.page.getByRole('switch')
    assert.ok(await switches.count() > 0)
    for (const control of await switches.all()) assert.equal(await control.isDisabled(), true, 'failed initial privacy read must disable editing')
    broken.state.failRead = false
    await broken.page.getByRole('button', { name: /重试/ }).first().click()
    await broken.page.getByRole('switch', { name: '展示手机号', exact: true }).waitFor({ state: 'attached' })
    await broken.page.waitForFunction(() => !document.querySelector('[aria-label="展示手机号"]').disabled)
    results.push('读取失败禁用编辑，重试成功恢复')
  } finally { await broken.page.screenshot({ path: resolve(output, 'load-retry.png'), fullPage: true, animations: 'disabled' }); await broken.context.close() }

  const f = await fixture({ failSave: true })
  try {
    const { page, state } = f
    await page.goto(base + 'profile/settings')
    const save = page.getByRole('button', { name: '保存隐私设置', exact: true })
    await page.getByText('找回邮箱已验证', { exact: false }).waitFor()
    assert.equal(await save.isDisabled(), true)
    await page.getByRole('switch', { name: '展示邮箱', exact: true }).locator('..').click()
    assert.equal(state.writes.length, 0, 'privacy is not autosaved')
    await page.locator('#appearance').getByRole('radio', { name: /曜石黑/ }).locator('..').click()
    assert.equal(state.writes.length, 0, 'theme must not submit privacy draft')
    await save.click()
    await page.getByText('保存失败，请重试；你的修改已保留。', { exact: true }).waitFor()
    assert.equal(await page.getByRole('switch', { name: '展示邮箱', exact: true }).isChecked(), true)
    assert.equal(await save.isEnabled(), true)
    state.failSave = false
    await save.click()
    await page.waitForFunction(() => document.querySelector('[data-testid="privacy-save"]').disabled)
    assert.deepEqual(state.writes.at(-1), { contact_visible: 2, phone_visible: 1, email_visible: 1 })
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'black')
    await page.getByRole('radio', { name: '仅自己', exact: true }).locator('..').click()
    assert.equal(await page.getByRole('switch', { name: '展示手机号', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('switch', { name: '展示手机号', exact: true }).isChecked(), true, 'scope keeps field choice')
    await page.getByRole('radio', { name: '仅好友', exact: true }).locator('..').click()
    assert.equal(await save.isDisabled(), true, 'switching back restores saved values')
    await page.getByRole('switch', { name: '展示邮箱', exact: true }).locator('..').click()
    await page.getByRole('link', { name: '编辑个人资料', exact: true }).click()
    await page.getByRole('button', { name: '留在此页', exact: true }).click()
    assert.ok(page.url().endsWith('/profile/settings'))
    await page.getByRole('button', { name: '放弃修改', exact: true }).click()
    await page.getByRole('link', { name: '编辑个人资料', exact: true }).click()
    await page.getByRole('link', { name: '管理通讯录隐私', exact: true }).waitFor()
    assert.equal(await page.getByRole('switch').count(), 0, 'profile editor has no second privacy editor')
    await page.getByRole('link', { name: '管理通讯录隐私', exact: true }).click()
    await page.getByRole('heading', { name: '隐私与可见性', exact: true }).waitFor()
    assert.ok(page.url().endsWith('#privacy'))
    const before = await page.evaluate(() => ({ token: localStorage.getItem('access_token'), theme: localStorage.getItem('coursehub-theme') }))
    await page.getByRole('button', { name: '重新加载页面', exact: true }).click()
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await page.getByRole('heading', { name: '账号与安全', exact: true }).waitFor()
    assert.deepEqual(await page.evaluate(() => ({ token: localStorage.getItem('access_token'), theme: localStorage.getItem('coursehub-theme') })), before)
    results.push('隐私独立保存/失败保留/父子选项/主题独立/离开提示/单入口/刷新保留登录')
  } finally { await f.page.screenshot({ path: resolve(output, 'settings-interaction.png'), fullPage: true, animations: 'disabled' }); await f.context.close() }

  for (const role of [1, 2, 3]) for (const theme of ['blue', 'wine', 'black']) {
    const f = await fixture({ role, theme, width: role === 3 ? 1440 : 390 })
    try {
      await f.page.goto(base + 'profile/settings')
      await f.page.getByText('找回邮箱已验证', { exact: false }).waitFor()
      assert.equal(await f.page.locator('html').getAttribute('data-theme'), theme)
      assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await f.page.screenshot({ path: resolve(output, `settings-${role}-${theme}.png`), fullPage: true, animations: 'disabled' })
      results.push(`settings role ${role} theme ${theme}`)
    } finally { await f.context.close() }
  }
  }
  if (!process.argv.includes('--settings') && !process.argv.includes('--ancillary')) {
    for (const theme of ['blue', 'wine', 'black']) {
      const auth = await fixture({ theme }), guest = await fixture({ theme, authenticated: false })
      try {
        for (const width of [1440, 1024, 768, 390, 320]) {
          await guest.page.setViewportSize({ width, height: 844 })
          await guest.page.goto(base + 'login')
          await guest.page.locator('.submit-btn').waitFor()
          if (width <= 768) {
            const button = await guest.page.locator('.submit-btn').boundingBox()
            assert.ok(button.y >= 0 && button.y + button.height <= 844, 'login action stays in first viewport ' + width)
          }
          assert.ok(await guest.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
          await guest.page.screenshot({ path: resolve(output, `login-${theme}-${width}.png`), fullPage: true, animations: 'disabled' })
          await auth.page.setViewportSize({ width, height: 844 })
          await auth.page.goto(base + 'profile')
          await auth.page.locator('.month-stats').waitFor()
          assert.equal(await auth.page.locator('.month-stats dd').allTextContents().then(values => values.join('/')), '24/12/5')
          assert.ok(await auth.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
          await auth.page.screenshot({ path: resolve(output, `profile-${theme}-${width}.png`), fullPage: true, animations: 'disabled' })
          await auth.page.goto(base + 'home')
          await auth.page.locator('.discovery-course').first().waitFor()
          assert.ok(await auth.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
          await auth.page.screenshot({ path: resolve(output, `home-${theme}-${width}.png`), fullPage: true, animations: 'disabled' })
          await auth.page.locator('.discovery-course').first().click()
          if (width <= 850) { await auth.page.waitForURL('**/course/1'); assert.equal(await auth.page.locator('.course-preview').count(), 0) }
          else { assert.ok(auth.page.url().endsWith('/home')); assert.equal(await auth.page.locator('.course-preview').isVisible(), true) }
          results.push(`layout login/profile/home ${theme} ${width}`)
        }
      } finally { await auth.context.close(); await guest.context.close() }
    }
    for (const width of [1440, 768, 390, 320]) {
      const f = await fixture({ theme: 'black', role: 3, width })
      try {
        await f.page.goto(base + 'admin/dashboard')
        await f.page.getByRole('button', { name: '后台导航', exact: true }).waitFor()
        assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'admin shell fits ' + width)
        await f.page.getByRole('button', { name: '后台导航', exact: true }).click()
        await f.page.getByRole('menuitem', { name: '资料审核', exact: true }).waitFor()
        await f.page.screenshot({ path: resolve(output, `admin-navigation-${width}.png`), fullPage: true, animations: 'disabled' })
        results.push(`admin text navigation ${width}`)
      } finally { await f.context.close() }
    }
    const narrow = await fixture({ theme: 'black', width: 320 })
    try {
      for (const route of ['profile/settings', 'profile/support', 'profile/about']) {
        await narrow.page.goto(base + route)
        await narrow.page.locator('.page-header').waitFor()
        assert.ok(await narrow.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
        await narrow.page.screenshot({ path: resolve(output, `${route.replace('/', '-')}-320.png`), fullPage: true, animations: 'disabled' })
      }
      await narrow.page.goto(base + 'profile/settings')
      await narrow.page.getByText('找回邮箱已验证', { exact: false }).waitFor()
      await narrow.page.waitForFunction(() => !document.querySelector('.privacy-fields').disabled)
      await narrow.page.screenshot({ path: resolve(output, 'black-selected-radio.png'), animations: 'disabled', fullPage: true })
      const contrast = await narrow.page.locator('.el-radio-button.is-active .el-radio-button__inner').evaluate(el => {
        const css = getComputedStyle(el), rgb = color => color.match(/[\d.]+/g).slice(0, 3).map(Number)
        const luminance = color => rgb(color).map(v => { v /= 255; return v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4 }).reduce((s, v, i) => s + v * [.2126, .7152, .0722][i], 0)
        const a = luminance(css.color), b = luminance(css.backgroundColor)
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
      })
      assert.ok(contrast >= 4.5, 'black selected radio contrast >= 4.5, got ' + contrast)
      results.push(`black selected radio contrast ${contrast.toFixed(2)}`)
    } finally { await narrow.context.close() }
  }
  if (process.argv.includes('--ancillary')) {
    const f = await fixture({ width: 320, theme: 'black' })
    try {
      f.state.failSecurity = true
      await f.page.goto(base + 'profile/security')
      await f.page.getByText('账号安全信息加载失败', { exact: true }).waitFor()
      assert.equal(await f.page.getByRole('button', { name: '发送验证码', exact: true }).isDisabled(), true)
      assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'security failure fits narrow viewport')
      await f.page.screenshot({ path: resolve(output, 'security-error-320.png'), fullPage: true, animations: 'disabled' })
      f.state.failSecurity = false
      await f.page.getByRole('button', { name: '重试', exact: true }).click()
      await f.page.getByTestId('recovery-status').filter({ hasText: '已验证' }).waitFor()
      assert.equal(await f.page.getByRole('button', { name: '发送验证码', exact: true }).isDisabled(), false)
      await f.page.screenshot({ path: resolve(output, 'security-320.png'), fullPage: true, animations: 'disabled' })
      await f.page.goto(base + 'profile/edit')
      await f.page.getByRole('button', { name: '保存修改', exact: true }).waitFor()
      assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'edit profile fits narrow viewport')
      await f.page.screenshot({ path: resolve(output, 'edit-profile-320.png'), fullPage: true, animations: 'disabled' })
      results.push('账号安全读取失败禁用绑定、重试恢复；320px 安全与编辑资料无横溢')
    } finally { await f.context.close() }
    const admin = await fixture({ role: 3, width: 390 })
    try {
      await admin.page.goto(base + 'profile/support')
      await admin.page.getByRole('link', { name: /处理兑换订单/ }).waitFor()
      assert.equal(await admin.page.locator('a[href="/profile/orders"], a[href="/profile/score"]').count(), 0)
      await admin.page.getByRole('button', { name: '打开个人菜单', exact: true }).click()
      await admin.page.getByRole('menuitem', { name: '退出登录', exact: true }).waitFor()
      assert.equal(await admin.page.getByRole('menuitem', { name: '我的订单', exact: true }).count(), 0)
      await admin.page.screenshot({ path: resolve(output, 'admin-help-390.png'), fullPage: true, animations: 'disabled' })
      await admin.page.goto(base + 'profile/about')
      await admin.page.getByRole('link', { name: /反馈与处理记录/ }).waitFor()
      assert.ok(await admin.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      results.push('帮助和个人菜单遵守管理员订单权限；关于页反馈入口可见')
    } finally { await admin.context.close() }
  }
  assert.deepEqual(errors, [], 'no uncaught page errors')
  console.log(JSON.stringify({ passed: results.length, results }, null, 2))
} finally {
  await writeFile(resolve(output, 'results.json'), JSON.stringify({ results, errors }, null, 2), 'utf8')
  await browser?.close()
  await server.close()
}
