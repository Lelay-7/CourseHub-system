import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { preview } from 'vite'
import { chromium } from 'playwright'
import { THEMES } from '../src/utils/theme.js'

// Built production CSS and Vue pages with bounded HTTP fixtures; no real messages are sent.
const output = resolve('output/playwright/theme-browser')
await mkdir(output, { recursive: true })
const server = await preview({ preview: { host: '127.0.0.1', port: 0, open: false } })
const base = server.resolvedUrls.local[0]
const results = [], errors = [], ratios = []
const token = `e30.${Buffer.from(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 7200 })).toString('base64url')}.fixture`
const user = { user_id: 1, user_type: 1, username: 'theme_reader', nickname: '配色体验同学', balance: 82 }
let browser

async function fixture({ theme = 'blue', width = 1440, height = 900, authenticated = true } = {}) {
  const context = await browser.newContext({ viewport: { width, height } })
  await context.addInitScript(({ theme, token, user, authenticated }) => {
    if (!localStorage.getItem('coursehub-theme')) localStorage.setItem('coursehub-theme', theme)
    if (authenticated) {
      localStorage.setItem('access_token', token)
      localStorage.setItem('refresh_token', token)
      localStorage.setItem('user_info', JSON.stringify(user))
    }
  }, { theme, token, user, authenticated })
  const messages = ['同学你好，新的配色很清楚。', '一起复习课程🙂\n保留中文与换行。'].map((text, index) => ({
    message_id: String(index + 1), conversation_id: '1', sequence: String(index + 1), sender_id: index ? '1' : '2',
    client_message_id: `theme-${index}`, message_type: 'TEXT', content: { text }, created_at: '2026-10-22T10:00:00Z'
  }))
  const conversation = { conversation_id: '1', conversation_type: 'PRIVATE', peer: { user_id: '2', nickname: '同学乙', user_type: 1 }, last_message: messages[1], last_sequence: '2', last_read_sequence: '2', unread_count: 0, can_send: true }
  await context.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname
    let data = { list: [], total: 0 }
    if (path === '/api/auth/userinfo') data = user
    if (path === '/api/user/security') data = { recovery_email_verified: true, recovery_email_masked: 't***@example.test' }
    if (path === '/api/contact/auth/my') data = { contact_visible: 2, phone_visible: 1, email_visible: 2 }
    if (path === '/api/chat/unread') data = { unread_count: 0 }
    if (path === '/api/chat/conversations') data = { items: [conversation], next_cursor: null }
    if (path === '/api/chat/conversations/1') data = conversation
    if (path === '/api/chat/conversations/1/read') data = { last_read_sequence: '2', unread_count: 0 }
    if (path === '/api/chat/conversations/1/messages') data = { items: url.searchParams.has('after_sequence') ? [] : messages, has_more: false, next_before_sequence: '1', next_after_sequence: '2', latest_sequence: '2' }
    await route.fulfill({ json: { code: 200, message: 'ok', data } })
  })
  const page = await context.newPage()
  page.setDefaultTimeout(8000)
  page.on('pageerror', error => errors.push(error.message))
  return { context, page }
}

async function stable(page) {
  await page.addStyleTag({ content: '*,*::before,*::after{transition:none!important;animation:none!important}' })
}
async function checkTheme(page, theme) {
  const actual = await page.evaluate(() => ({ id: document.documentElement.dataset.theme, scheme: document.documentElement.style.colorScheme, dark: document.documentElement.classList.contains('dark'), stored: localStorage.getItem('coursehub-theme') }))
  assert.deepEqual(actual, { id: theme.id, scheme: theme.scheme, dark: theme.scheme === 'dark', stored: theme.id })
}
async function contrast(locator, label, theme) {
  const measured = await locator.evaluate(element => {
    const canvas = document.createElement('canvas'), ctx = canvas.getContext('2d')
    canvas.width = canvas.height = 1
    function rgb(color) { ctx.clearRect(0, 0, 1, 1); ctx.fillStyle = color; ctx.fillRect(0, 0, 1, 1); return Array.from(ctx.getImageData(0, 0, 1, 1).data) }
    function luminance(color) { return color.slice(0, 3).map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((total, value, index) => total + value * [.2126, .7152, .0722][index], 0) }
    const color = getComputedStyle(element).color
    let background = 'rgb(255, 255, 255)'
    for (let ancestor = element; ancestor; ancestor = ancestor.parentElement) {
      const candidate = getComputedStyle(ancestor).backgroundColor
      if (rgb(candidate)[3] === 255) { background = candidate; break }
    }
    const a = luminance(rgb(color)), b = luminance(rgb(background))
    return { color, background, ratio: (Math.max(a, b) + .05) / (Math.min(a, b) + .05) }
  })
  ratios.push({ theme, label, ...measured })
  assert.ok(measured.ratio >= 4.5, `${theme} ${label}: ${measured.ratio.toFixed(2)}:1 (${measured.color}/${measured.background})`)
}

try {
  browser = await chromium.launch({ headless: true })
  const desktop = await fixture()
  try {
    const { page } = desktop
    await page.goto(base + 'profile/settings')
    await page.getByText('找回邮箱已验证', { exact: false }).waitFor()
    await stable(page)
    assert.equal(await page.locator('#appearance input[type=radio]').count(), 6)
    const grid = await page.locator('#appearance .theme-option').evaluateAll(elements => ({ columns: new Set(elements.map(element => Math.round(element.getBoundingClientRect().left))).size, rows: new Set(elements.map(element => Math.round(element.getBoundingClientRect().top))).size }))
    assert.deepEqual(grid, { columns: 3, rows: 2 })
    for (const theme of THEMES) {
      await page.locator('#appearance').getByRole('radio', { name: new RegExp(theme.name) }).locator('..').click()
      await checkTheme(page, theme)
      await page.locator('#appearance h2').click()
      await page.locator('.theme-menu:visible').waitFor({ state: 'hidden' })
      await page.locator('#appearance').screenshot({ path: resolve(output, `settings-${theme.id}-desktop.png`) })
      await page.reload()
      await page.getByText('找回邮箱已验证', { exact: false }).waitFor()
      await stable(page)
      await checkTheme(page, theme)
      assert.equal(await page.evaluate(() => localStorage.getItem('access_token')), token)
      await page.getByRole('button', { name: /^切换配色/ }).click()
      await page.locator('.theme-menu:visible').waitFor()
      assert.equal(await page.locator('.theme-menu:visible input[type=radio]').count(), 6)
      await page.locator('.theme-menu:visible').getByRole('radio', { name: new RegExp(theme.name) }).locator('..').click()
      await checkTheme(page, theme)
      await page.locator('#appearance h2').click()
      await page.locator('.theme-menu:visible').waitFor({ state: 'hidden' })
      results.push(`${theme.id}: settings + header selection, refresh persistence, session retained`)
    }
  } finally { await desktop.context.close() }

  for (const viewport of [{ width: 320, height: 740 }, { width: 390, height: 844 }, { width: 800, height: 218 }]) {
    const f = await fixture({ ...viewport, theme: 'navy' })
    try {
      const { page } = f
      await page.goto(base + 'profile/settings#appearance')
      await page.getByText('找回邮箱已验证', { exact: false }).waitFor()
      await stable(page)
      for (const theme of THEMES) {
        const card = page.locator('#appearance').getByRole('radio', { name: new RegExp(theme.name) }).locator('..')
        await card.click()
        await checkTheme(page, theme)
      }
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      const columns = await page.locator('#appearance .theme-option').evaluateAll(elements => new Set(elements.map(element => Math.round(element.getBoundingClientRect().left))).size)
      assert.equal(columns, 1)
      await page.screenshot({ path: resolve(output, `settings-all-six-${viewport.width}x${viewport.height}.png`) })
      results.push(`${viewport.width}x${viewport.height}: all six settings choices reachable in one column`)
    } finally { await f.context.close() }
  }

  const short = await fixture({ theme: 'black', width: 901, height: 218 })
  try {
    const { page } = short
    await page.goto(base + 'profile/settings')
    await page.getByRole('button', { name: /^切换配色/ }).click()
    await stable(page)
    const menu = page.locator('.theme-menu:visible')
    await menu.waitFor()
    assert.ok(await menu.evaluate(element => element.scrollHeight > element.clientHeight), 'short popover must scroll')
    await menu.getByRole('radio', { name: /深海夜航/ }).locator('..').click()
    await checkTheme(page, THEMES.find(theme => theme.id === 'navy'))
    const bounds = await menu.locator('.theme-option').last().boundingBox()
    assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= 218, 'sixth theme fits the visible short landscape viewport')
    await page.screenshot({ path: resolve(output, 'popover-sixth-901x218.png') })
    await menu.getByRole('radio', { name: /曜石黑/ }).locator('..').click()
    await checkTheme(page, THEMES.find(theme => theme.id === 'black'))
    results.push('901x218: sixth popover choice scrolls into view and navy/black switch correctly')
  } finally { await short.context.close() }

  for (const theme of THEMES.filter(theme => ['sage', 'lavender', 'navy'].includes(theme.id))) {
    const f = await fixture({ theme: theme.id, width: 390, height: 844 })
    try {
      const { page } = f
      await page.goto(base + 'profile/settings')
      await page.getByText('找回邮箱已验证', { exact: false }).waitFor()
      await stable(page)
      await contrast(page.locator('.setting-row h3').first(), 'body heading', theme.id)
      await contrast(page.locator('.setting-row p').first(), 'secondary copy', theme.id)
      await contrast(page.locator('.el-radio-button.is-active .el-radio-button__inner'), 'selected radio', theme.id)
      await page.getByRole('radio', { name: '所有人', exact: true }).locator('..').click()
      const save = page.getByTestId('privacy-save')
      assert.equal(await save.isEnabled(), true)
      await contrast(save, 'primary button', theme.id)
      await save.hover()
      await contrast(save, 'primary button hover', theme.id)
      await page.getByRole('button', { name: '放弃修改', exact: true }).click()
      // Probe real compiled Element Plus semantic button classes, including plain hover.
      await page.evaluate(() => {
        const panel = document.createElement('div')
        panel.id = 'theme-status-probes'
        panel.style.cssText = 'padding:24px;background:var(--color-bg-card);display:grid;gap:12px'
        for (const type of ['success', 'warning', 'danger', 'info']) for (const plain of [false, true]) {
          const button = document.createElement('button')
          button.className = `el-button el-button--${type}${plain ? ' is-plain' : ''}`
          button.dataset.testid = `probe-${type}-${plain}`
          button.textContent = `${type} ${plain ? 'plain' : 'solid'}`
          panel.append(button)
        }
        document.body.append(panel)
      })
      for (const type of ['success', 'warning', 'danger', 'info']) for (const plain of [false, true]) {
        const button = page.getByTestId(`probe-${type}-${plain}`)
        await button.scrollIntoViewIfNeeded()
        await page.mouse.move(0, 0)
        await contrast(button, `${type} ${plain ? 'plain' : 'solid'}`, theme.id)
        await button.hover()
        await contrast(button, `${type} ${plain ? 'plain' : 'solid'} hover`, theme.id)
      }
      await page.locator('#theme-status-probes').screenshot({ path: resolve(output, `semantic-buttons-${theme.id}.png`) })
      await page.goto(base + 'messages/1')
      await page.getByRole('heading', { name: '同学乙', exact: true }).waitFor()
      await page.locator('.own .message-bubble').waitFor()
      await stable(page)
      await contrast(page.locator('.own .message-bubble'), 'outgoing chat bubble', theme.id)
      await contrast(page.locator('.message-row:not(.own) .message-bubble'), 'incoming chat bubble', theme.id)
      await page.screenshot({ path: resolve(output, `chat-${theme.id}-390.png`) })
      results.push(`${theme.id}: computed text/radio/button/status/chat contrast >= 4.5:1`)
    } finally { await f.context.close() }
    const guest = await fixture({ theme: theme.id, authenticated: false, width: 1440, height: 900 })
    try {
      await guest.page.goto(base + 'login')
      await guest.page.locator('.submit-btn').waitFor()
      await stable(guest.page)
      await checkTheme(guest.page, theme)
      await guest.page.screenshot({ path: resolve(output, `login-${theme.id}-desktop.png`) })
    } finally { await guest.context.close() }
  }

  for (const theme of THEMES) {
    const f = await fixture({ theme: theme.id, authenticated: false })
    try {
      // Block the app bundle: saved colors must already be restored by the HTML bootstrap.
      await f.context.route('**/assets/*.js', route => route.abort())
      await f.page.goto(base + 'login', { waitUntil: 'domcontentloaded' })
      await checkTheme(f.page, theme)
      const color = await f.page.evaluate(() => document.documentElement.style.backgroundColor)
      const expected = theme.colors[0].slice(1).match(/../g).map(value => parseInt(value, 16)).join(', ')
      assert.equal(color, `rgb(${expected})`)
      results.push(`${theme.id}: pre-application first-paint background and scheme`)
    } finally { await f.context.close() }
  }
  assert.deepEqual(errors, [], 'no uncaught application exceptions')
  console.log(JSON.stringify({ passed: results.length, results, measuredContrasts: ratios.length, minimumContrast: Math.min(...ratios.map(result => result.ratio)) }, null, 2))
} finally {
  await writeFile(resolve(output, 'results.json'), JSON.stringify({ results, errors, ratios }, null, 2), 'utf8')
  await browser?.close()
  await server.close()
}
