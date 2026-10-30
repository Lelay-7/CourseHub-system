import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { randomBytes } from 'node:crypto'
import { chromium } from 'playwright'

// Chromium touch/viewport emulation against the owned local preview, not a real phone.
// Real form login and HTTP data; no mocked responses or injected authentication.
// Does not send chat/mail, place orders, or alter existing account credentials.
// Material mutations belong to a newly registered mobile-test account only.
const { previewRuntime } = await import('./preview-runtime.mjs')
const config = JSON.parse(await readFile(resolve(previewRuntime, 'config.json'), 'utf8'))
assert.match(config.database, /^coursehub_product_preview_[a-f0-9]{32}$/)
const base = `http://localhost:${config.web_port}`
const ownership = await (await fetch(base + '/__preview')).json()
assert.equal(ownership.nonce, config.nonce)
assert.equal(ownership.database, config.database)
const baseline = process.argv.includes('--baseline')
const flowsOnly = process.argv.includes('--flows-only')
const supplement = process.argv.includes('--supplement')
const only = process.argv.find(argument => argument.startsWith('--only='))?.slice(7)
const themes = baseline ? ['blue'] : ['blue', 'wine', 'black', 'sage', 'lavender', 'navy']
const output = resolve('output/playwright/product-mobile' + (supplement ? '-supplement' : baseline ? '-baseline' : ''))
await mkdir(output, { recursive: true })
const results = [], sessions = [], errors = new Map(), pendingChecks = new Set(), writes = []
const browser = await chromium.launch({ headless: true })
const slug = value => value.replace(/[^a-zA-Z0-9-]/g, '_')

async function settled(page) {
  await page.waitForLoadState('networkidle', { timeout: 9000 })
  await Promise.all([...pendingChecks])
}

async function check(name, page, fn) {
  if (only && !name.includes(only) && !name.startsWith('touch form login')) return
  const before = errors.get(page)?.length || 0
  try {
    await fn()
    await Promise.all([...pendingChecks])
    assert.deepEqual(errors.get(page)?.slice(before) || [], [], 'Browser/API errors')
    results.push({ name, status: 'pass' })
    console.log('PASS ' + name)
  } catch (error) {
    const screenshot = slug(name).slice(0, 140) + '-failed.png'
    await page?.screenshot({ path: resolve(output, screenshot), animations: 'disabled' }).catch(() => {})
    results.push({ name, status: 'fail', error: error.message, stack: error.stack, screenshot })
    console.error('FAIL ' + name + ': ' + error.stack)
  }
}

async function api(page, path, options = {}) {
  const result = await page.evaluate(async ({ path, options }) => {
    const response = await fetch(path, {
      method: options.method || 'GET',
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}`, ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      body: options.body ? JSON.stringify(options.body) : undefined
    })
    return { status: response.status, json: await response.json() }
  }, { path, options })
  assert.equal(result.status, 200, path)
  assert.equal(result.json.code, 200, path + ': ' + result.json.message)
  return result.json.data
}

async function layout(page) {
  await page.waitForFunction(() => [...document.querySelectorAll('.el-drawer')].filter(el => el.getClientRects().length)
    .every(el => { const r = el.getBoundingClientRect(); return r.left >= -1 && r.right <= innerWidth + 1 }))
  const geometry = await page.evaluate(() => {
    const width = window.innerWidth
    const escaped = [...document.querySelectorAll('main, .page-container, .el-drawer, .el-dialog, .settings-section, .message-composer')]
      .filter(el => el.getClientRects().length)
      .map(el => ({ selector: el.className, left: el.getBoundingClientRect().left, right: el.getBoundingClientRect().right }))
      .filter(r => r.left < -1 || r.right > width + 1)
    return { width, screen: document.documentElement.clientWidth, content: document.documentElement.scrollWidth, escaped }
  })
  assert.ok(geometry.content <= geometry.screen + 1, 'Horizontal document overflow: ' + JSON.stringify(geometry))
  assert.deepEqual(geometry.escaped, [], 'Main content escapes viewport')
}

async function reachable(locator) {
  await locator.scrollIntoViewIfNeeded()
  const geometry = await locator.evaluate(el => {
    const r = el.getBoundingClientRect(), hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
    return { width: r.width, height: r.height, top: r.top, bottom: r.bottom,
      viewport: innerHeight, hit: !!hit && (el.contains(hit) || hit.contains(el)) }
  })
  assert.ok(geometry.width >= 24 && geometry.height >= 24, 'Control has insufficient touch area: ' + JSON.stringify(geometry))
  assert.ok(geometry.top >= 0 && geometry.bottom <= geometry.viewport + 1 && geometry.hit, 'Control obscured: ' + JSON.stringify(geometry))
}

async function visit(page, path) {
  await page.goto(base + path)
  await settled(page)
  assert.equal(new URL(page.url()).pathname, path.split('?')[0].split('#')[0])
  assert.ok((await page.locator('body').innerText()).trim().length > 30)
  await layout(page)
}

async function setTheme(page, theme) {
  await visit(page, '/profile/settings#appearance')
  const choice = page.locator(`#appearance input[value="${theme}"]`).locator('..')
  await choice.tap()
  assert.equal(await page.locator('html').getAttribute('data-theme'), theme)
  const saved = await page.evaluate(() => localStorage.getItem('coursehub-theme'))
  assert.ok(saved === theme || (theme === 'blue' && saved === null), 'Selected theme is persisted, or is the unchanged initial default')
}

async function submitAndWait(page, locator, path, method) {
  await reachable(locator)
  const pending = page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === method)
  await locator.tap()
  const response = await pending, body = await response.json()
  assert.equal(response.status(), 200, path)
  assert.equal(body.code, 200, path + ': ' + body.message)
  return body.data
}

function pdfFile(revision) {
  const content = `BT /F1 14 Tf 40 740 Td (Mobile preview material revision ${revision}) Tj ET`
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`]
  let pdf = '%PDF-1.4\n', offsets = [0]
  objects.forEach((obj, i) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${i + 1} 0 obj\n${obj}\nendobj\n` })
  const xref = Buffer.byteLength(pdf)
  pdf += `xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset => String(offset).padStart(10, '0') + ' 00000 n ').join('\n')}\ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return { name: `mobile-review-v${revision}.pdf`, mimeType: 'application/pdf', buffer: Buffer.from(pdf, 'ascii') }
}

try {
  for (const account of config.accounts) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 })
    const page = await context.newPage()
    page.setDefaultTimeout(8000)
    errors.set(page, [])
    page.on('pageerror', e => errors.get(page).push(e.message))
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (path.startsWith('/api/') && !['GET', 'OPTIONS'].includes(request.method())) writes.push({ role: account.user_type, path, method: request.method() })
    })
    page.on('response', response => {
      const path = new URL(response.url()).pathname
      if (!path.startsWith('/api/')) return
      const task = (async () => {
        try {
          const json = await response.json()
          if (response.status() >= 400 || (json.code !== undefined && json.code !== 200)) errors.get(page).push(`${path}: HTTP ${response.status()} code ${json.code}`)
        } catch { if (response.status() >= 400) errors.get(page).push(`${path}: HTTP ${response.status()}`) }
      })()
      pendingChecks.add(task)
      task.finally(() => pendingChecks.delete(task))
    })
    let authenticated = false
    await check('touch form login ' + account.username, page, async () => {
      await visit(page, '/login')
      await page.getByPlaceholder('请输入登录账号').fill(account.username)
      await page.getByPlaceholder('请输入密码', { exact: true }).fill(account.password)
      const login = page.locator('.submit-btn').filter({ hasText: '登录' })
      await reachable(login)
      const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST')
      await login.tap()
      assert.equal((await response).status(), 200)
      await page.waitForURL(url => url.pathname !== '/login')
      await settled(page)
      await page.reload(); await settled(page)
      assert.notEqual(new URL(page.url()).pathname, '/login')
      authenticated = true
    })
    if (!authenticated) { await context.close(); continue }
    sessions.push({ context, page, account, user: await api(page, '/api/auth/userinfo') })
  }
  assert.equal(sessions.length, config.accounts.length, 'Every preview account must log in through its mobile page')
  const students = sessions.filter(s => s.user.user_type === 1), student = students[0]
  const teacher = sessions.find(s => s.user.user_type === 2), admin = sessions.find(s => s.user.user_type === 3)
  assert.ok(student && students[1] && teacher && admin)
  const courses = await api(student.page, '/api/course/list?page=1&pageSize=20')
  const goods = await api(student.page, '/api/mall/goods?page=1&pageSize=20')
  const materials = await api(student.page, '/api/material/list?page=1&pageSize=20')
  const conversations = await api(student.page, '/api/chat/conversations')
  const courseId = courses.list[0].course_id, goodsId = goods.list[0].goods_id, materialId = materials.list[0].material_id
  const conversationId = conversations.items[0]?.conversation_id
  assert.ok(conversationId, 'A seeded private conversation is required; this runner never creates or sends one')
  if (supplement) {
    const page = student.page
    await setTheme(page, 'navy')
    for (const width of [360, 375, 768]) {
      await page.setViewportSize({ width, height: width === 768 ? 1024 : 812 })
      for (const path of ['/home', '/profile/settings', `/messages/${conversationId}`]) {
        await check(`supplement navy ${width} ${path}`, page, async () => {
          await visit(page, path)
          assert.equal(await page.locator('html').getAttribute('data-theme'), 'navy')
          if (path.startsWith('/messages/')) await reachable(page.getByRole('button', { name: '发送', exact: true }))
          await page.screenshot({ path: resolve(output, `navy-${width}-${slug(path)}.png`), animations: 'disabled' })
        })
      }
    }
    await check('supplement offline course error retry restores session theme and unsent chat draft', page, async () => {
      await page.setViewportSize({ width: 390, height: 844 })
      await visit(page, `/messages/${conversationId}`)
      const draft = '离线恢复仍保留的草稿🙂'
      await page.getByRole('textbox', { name: '消息内容' }).fill(draft)
      const tokenBefore = await page.evaluate(() => localStorage.getItem('access_token'))
      await page.getByRole('button', { name: '返回会话列表', exact: true }).tap()
      await page.waitForURL('**/messages')
      await page.locator('.mobile-tabbar').getByRole('button', { name: '首页', exact: true }).tap()
      await page.waitForURL('**/home'); await settled(page)
      // This exercises API outage/recovery of a loaded route, not fetching new JS while offline.
      await page.locator('.mobile-tabbar').getByRole('button', { name: '选课', exact: true }).tap()
      await page.waitForURL('**/course'); await page.locator('.course-card-pro').first().waitFor(); await settled(page)
      await page.locator('.mobile-tabbar').getByRole('button', { name: '首页', exact: true }).tap()
      await page.waitForURL('**/home'); await settled(page)
      try {
        await student.context.setOffline(true)
        await page.locator('.mobile-tabbar').getByRole('button', { name: '选课', exact: true }).tap()
        await page.waitForURL('**/course')
        await page.getByText('课程加载失败', { exact: true }).waitFor()
        await page.screenshot({ path: resolve(output, 'offline-course.png'), animations: 'disabled' })
      } finally { await student.context.setOffline(false) }
      const retry = page.locator('.error-state').filter({ hasText: '课程加载失败' }).getByRole('button', { name: '重试', exact: true })
      // A visible element at the viewport edge can sit behind the fixed tab bar;
      // scroll it into the reading area, as a person would, before checking the hit target.
      await retry.evaluate(el => el.scrollIntoView({ block: 'center' }))
      await reachable(retry); await retry.tap()
      await page.locator('.course-card-pro').first().waitFor()
      assert.equal(await page.evaluate(() => localStorage.getItem('access_token')), tokenBefore)
      assert.equal(await page.locator('html').getAttribute('data-theme'), 'navy')
      await page.getByRole('button', { name: '查看消息', exact: true }).tap()
      await page.waitForURL('**/messages')
      await page.getByRole('button', { name: '与' + conversations.items[0].peer.nickname + '的会话', exact: true }).tap()
      await page.waitForURL('**/messages/' + conversationId)
      assert.equal(await page.getByRole('textbox', { name: '消息内容' }).inputValue(), draft)
      await page.getByRole('textbox', { name: '消息内容' }).fill('')
      await page.screenshot({ path: resolve(output, 'offline-recovered.png'), animations: 'disabled' })
    })
  } else {
  const common = ['/home', '/course', `/course/${courseId}`, '/square', '/material', `/material/${materialId}`, '/contact',
    `/contact/${students[1].user.user_id}`, '/mall', `/mall/${goodsId}`, '/profile', '/profile/courses', '/profile/downloads',
    '/profile/favorites', '/profile/edit', '/profile/settings', '/profile/security', '/profile/feedback', '/profile/support', '/profile/about',
    '/profile/reviews', '/profile/score', '/profile/orders', '/messages', `/messages/${conversationId}`]
  const adminPaths = ['dashboard', 'courses', 'materials', 'reviews', 'users', 'banners', 'square', 'orders', 'goods', 'reports'].map(s => '/admin/' + s)
  for (const [session, paths, width] of (flowsOnly ? [] : [[student, common, 320], [teacher, ['/home', '/course', '/profile/edit', '/profile/settings', '/profile/score'], 390],
    [students[1], ['/home', '/profile/settings', '/material', '/messages'], 430], [admin, [...adminPaths, '/profile/settings', '/profile/security', '/profile/feedback'], 390]])) {
    await session.page.setViewportSize({ width, height: 844 })
    for (const path of paths) await check(`route ${session.account.username} ${width} ${path}`, session.page, () => visit(session.page, path))
  }

  const page = student.page
  await page.setViewportSize({ width: 390, height: 844 })
  await check('touch navigation search pagination and detail return', page, async () => {
    await visit(page, '/home')
    await page.locator('.mobile-tabbar').getByRole('button', { name: '选课', exact: true }).tap()
    await page.waitForURL('**/course'); await settled(page)
    const next = page.locator('.el-pagination .btn-next')
    await reachable(next)
    const request = page.waitForResponse(r => new URL(r.url()).pathname === '/api/course/list' && new URL(r.url()).searchParams.get('page') === '2')
    await next.tap(); await request; await settled(page)
    const secondPageTitle = await page.locator('.course-card-pro h3').first().innerText()
    await page.locator('.course-card-pro').first().getByRole('button', { name: '查看详情', exact: true }).tap()
    await page.waitForURL(/\/course\/\d+$/)
    await page.goBack(); await settled(page)
    assert.equal(await page.locator('.course-card-pro h3').first().innerText(), secondPageTitle)
    const search = page.getByPlaceholder('搜索课程名称、课程代码、授课教师')
    await search.fill('__mobile_no_matching_course__')
    await page.getByText('暂无符合条件的课程', { exact: true }).waitFor()
    await search.fill('')
    await page.locator('.course-card-pro').first().waitFor()
    await layout(page)
  })

  await check('touch privacy independent save restore and reload retains login', page, async () => {
    await visit(page, '/profile/settings')
    const original = await api(page, '/api/contact/auth/my')
    const originalScope = Number(original.contact_visible)
    const target = originalScope === 3 ? 2 : 3
    const labels = { 1: '所有人', 2: '仅好友', 3: '仅自己' }
    try {
      await page.getByRole('radio', { name: labels[target], exact: true }).locator('..').tap()
      assert.equal((await api(page, '/api/contact/auth/my')).contact_visible, original.contact_visible, 'Editing alone must not save')
      const save = page.getByRole('button', { name: '保存隐私设置', exact: true })
      await submitAndWait(page, save, '/api/contact/auth/update', 'PUT')
      await page.waitForFunction(() => document.querySelector('[data-testid="privacy-save"]').disabled)
      assert.equal(Number((await api(page, '/api/contact/auth/my')).contact_visible), target)
      await page.getByRole('radio', { name: labels[originalScope], exact: true }).locator('..').tap()
      await submitAndWait(page, save, '/api/contact/auth/update', 'PUT')
      await page.waitForFunction(() => document.querySelector('[data-testid="privacy-save"]').disabled)
      const before = await page.evaluate(() => ({ token: localStorage.getItem('access_token'), theme: localStorage.getItem('coursehub-theme') }))
      const previousNavigation = await page.evaluate(() => performance.timeOrigin)
      await page.getByRole('button', { name: '重新加载页面', exact: true }).tap()
      await page.getByRole('button', { name: '重新加载', exact: true }).tap()
      await page.waitForFunction(previous => performance.timeOrigin !== previous, previousNavigation)
      await settled(page)
      assert.deepEqual(await page.evaluate(() => ({ token: localStorage.getItem('access_token'), theme: localStorage.getItem('coursehub-theme') })), before)
    } finally {
      // Restore exact prior values even if a UI assertion interrupts this case.
      await api(page, '/api/contact/auth/update', { method: 'PUT', body: original })
      await page.reload(); await settled(page)
    }
  })

  await check('mobile account form validation does not send empty credentials', page, async () => {
    await visit(page, '/profile/security')
    const before = writes.length
    const button = page.getByRole('button', { name: '修改密码并重新登录', exact: true })
    await reachable(button); await button.tap()
    await page.locator('.el-form-item__error').first().waitFor()
    assert.equal(writes.length, before)
    await layout(page)
  })

  await check('admin material inspection drawer touch close without decision', admin.page, async () => {
    await visit(admin.page, '/admin/materials')
    const inspect = admin.page.getByRole('button', { name: '检查与处理', exact: true }).first()
    await reachable(inspect); await inspect.tap()
    await admin.page.getByRole('heading', { name: '提交版本', exact: true }).waitFor()
    await layout(admin.page)
    const close = admin.page.locator('.el-drawer__close-btn')
    await reachable(close); await close.tap()
    await admin.page.locator('.el-drawer').waitFor({ state: 'hidden' })
  })

  if (!baseline) {
    const newContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 })
    const newPage = await newContext.newPage()
    newPage.setDefaultTimeout(8000)
    errors.set(newPage, [])
    newPage.on('pageerror', error => errors.get(newPage).push(error.message))
    const username = 'mobile_' + Date.now().toString().slice(-10)
    let createdMaterialId
    await check('mobile registration creates a dedicated test account', newPage, async () => {
      await visit(newPage, '/login')
      await newPage.getByRole('button', { name: '注册', exact: true }).tap()
      await newPage.getByPlaceholder('设置登录账号（3–20个字符）').fill(username)
      await newPage.getByPlaceholder('设置昵称（显示名称）').fill('移动闭环验收')
      await newPage.getByPlaceholder('设置密码', { exact: true }).fill('M-' + randomBytes(8).toString('hex'))
      await submitAndWait(newPage, newPage.getByRole('button', { name: '创建账号', exact: true }), '/api/auth/register', 'POST')
      await newPage.waitForURL(url => url.pathname !== '/login')
      await settled(newPage)
      assert.equal((await api(newPage, '/api/auth/userinfo')).username, username)
    })
    await check('mobile course selection and cancellation persist for the new account', newPage, async () => {
      await visit(newPage, `/course/${courseId}`)
      try {
        await submitAndWait(newPage, newPage.locator('.page-header').getByRole('button', { name: '选择课程', exact: true }), `/api/course/${courseId}/select`, 'POST')
        await newPage.getByRole('button', { name: '已选择，点击退选', exact: true }).waitFor()
        await newPage.reload(); await settled(newPage)
        await submitAndWait(newPage, newPage.getByRole('button', { name: '已选择，点击退选', exact: true }), `/api/course/${courseId}/select`, 'DELETE')
        await newPage.locator('.page-header').getByRole('button', { name: '选择课程', exact: true }).waitFor()
      } finally {
        const selected = await api(newPage, '/api/course/selected')
        const list = selected.list || selected
        if (list.some(item => String(item.course_id) === String(courseId))) await api(newPage, `/api/course/${courseId}/select`, { method: 'DELETE' })
      }
    })
    await check('mobile PDF upload reject CSV replacement resubmit approve and download via real pages', newPage, async () => {
      const name = '移动端资料闭环 ' + username
      const file = revision => revision === 1 ? pdfFile(1) : { name: 'mobile-review-v2.csv', mimeType: 'text/csv', buffer: Buffer.from('revision,content\n2,mobile-preview-only\n', 'utf8') }
      await visit(newPage, '/material')
      await newPage.getByRole('button', { name: '上传资料', exact: true }).tap()
      const dialog = newPage.getByRole('dialog', { name: '上传学习资料', exact: true })
      await dialog.locator('input[type=file]').setInputFiles(file(1))
      await dialog.locator('.el-form-item').filter({ hasText: '资料名称' }).locator('input').fill(name)
      await layout(newPage)
      const uploaded = await submitAndWait(newPage, dialog.getByRole('button', { name: '提交上传', exact: true }), '/api/material/upload', 'POST')
      createdMaterialId = uploaded.material.material_id
      await dialog.waitFor({ state: 'hidden' })
      // Only this exact newly-created ID is opened for administrator review.
      await visit(admin.page, `/admin/materials?materialId=${createdMaterialId}`)
      await admin.page.getByRole('heading', { name: '提交版本', exact: true }).waitFor()
      await layout(admin.page)
      await admin.page.getByPlaceholder('驳回或下架时必填，请说明需要修改的内容').fill('移动端验收：请替换第二版文件')
      await submitAndWait(admin.page, admin.page.getByRole('button', { name: '驳回并反馈原因', exact: true }), `/api/admin/materials/${createdMaterialId}/reject`, 'PUT')
      await visit(newPage, `/material/${createdMaterialId}`)
      await newPage.getByText('资料被驳回：移动端验收：请替换第二版文件', { exact: true }).waitFor()
      await newPage.getByRole('button', { name: '修改并重提', exact: true }).tap()
      const replacement = newPage.getByRole('dialog', { name: '修改资料并重新提交', exact: true })
      await replacement.locator('input[type=file]').setInputFiles(file(2))
      await submitAndWait(newPage, replacement.getByRole('button', { name: '保存修改并提交审核', exact: true }), `/api/material/${createdMaterialId}/resubmit`, 'PUT')
      await replacement.waitFor({ state: 'hidden' })
      await newPage.getByRole('heading', { name: '提交与审核记录 · V2', exact: true }).waitFor()
      await visit(admin.page, `/admin/materials?materialId=${createdMaterialId}`)
      await admin.page.getByRole('heading', { name: '提交版本', exact: true }).waitFor()
      await layout(admin.page)
      const inspectDownload = admin.page.waitForEvent('download')
      await admin.page.getByRole('button', { name: '下载当前文件检查', exact: true }).tap()
      const inspection = await inspectDownload
      const inspectionPath = resolve(output, 'admin-v2-' + createdMaterialId + '.csv')
      await inspection.saveAs(inspectionPath)
      assert.equal(await readFile(inspectionPath, 'utf8'), file(2).buffer.toString('utf8'))
      await submitAndWait(admin.page, admin.page.getByRole('button', { name: '通过当前版本', exact: true }), `/api/admin/materials/${createdMaterialId}/approve`, 'PUT')
      await visit(newPage, `/material/${createdMaterialId}`)
      const download = newPage.getByRole('button', { name: '下载资料', exact: true })
      await reachable(download)
      const pendingDownload = newPage.waitForEvent('download')
      await download.tap()
      const downloaded = await pendingDownload, downloadedPath = resolve(output, 'student-v2-' + createdMaterialId + '.csv')
      await downloaded.saveAs(downloadedPath)
      assert.equal(await readFile(downloadedPath, 'utf8'), file(2).buffer.toString('utf8'))
      const detail = await api(newPage, `/api/material/${createdMaterialId}`)
      assert.equal(detail.material.status, 1)
      assert.equal(detail.material.revision, 2)
      assert.equal(detail.material.material_type, 3)
      assert.equal(detail.audit_history.length, 2)
      await newPage.screenshot({ path: resolve(output, 'new-material-closure.png'), fullPage: true })
      results.push({ name: 'isolated material evidence', kind: 'evidence', status: 'pass', username, material_id: createdMaterialId, revision: 2,
        submitted: ['registration', 'selection', 'cancellation', 'PDF upload', 'reject', 'CSV replace/resubmit', 'approve', 'CSV download'] })
    })
    await newContext.close()
  }

  await check('mobile existing order detail and cancel confirmation remain reachable without mutation', page, async () => {
    const orders = await api(page, '/api/order/list?page=1&pageSize=100')
    const order = orders.list.find(item => item.status === 1)
    assert.ok(order, 'A pending isolated preview order is required for confirmation-only coverage')
    await visit(page, `/profile/orders/${order.order_id}`)
    await page.getByText('交付说明', { exact: true }).waitFor()
    const cancel = page.getByRole('button', { name: '取消订单', exact: true })
    await reachable(cancel); await cancel.tap()
    const keep = page.getByRole('button', { name: '再想想', exact: true })
    await reachable(keep); await keep.tap()
    assert.equal((await api(page, `/api/order/${order.order_id}`)).status, 1)
  })

  for (const theme of flowsOnly ? [] : themes) {
    await check('theme touch selection and reload ' + theme, page, async () => {
      await setTheme(page, theme)
      await page.reload(); await settled(page)
      assert.equal(await page.locator('html').getAttribute('data-theme'), theme)
      assert.equal(await page.locator('html').evaluate(el => el.classList.contains('dark')), ['black', 'navy'].includes(theme))
    })
    for (const viewport of [{ width: 320, height: 740 }, { width: 430, height: 932 }, { width: 844, height: 390 }]) {
      await page.setViewportSize(viewport)
      for (const path of ['/profile/settings#appearance', '/course', `/messages/${conversationId}`]) {
        await check(`theme ${theme} ${viewport.width}x${viewport.height} ${path}`, page, async () => {
          await visit(page, path)
          assert.equal(await page.locator('html').getAttribute('data-theme'), theme)
          if (path.startsWith('/messages/')) {
            await page.getByRole('textbox', { name: '消息内容' }).waitFor()
            await reachable(page.getByRole('button', { name: '发送', exact: true }))
            await reachable(page.getByRole('button', { name: '返回会话列表', exact: true }))
          }
          await page.screenshot({ path: resolve(output, `${theme}-${viewport.width}-${slug(path)}.png`), animations: 'disabled' })
        })
      }
    }
  }

  await check('portrait landscape restore preserves an unsent multiline chat draft', page, async () => {
    await page.setViewportSize({ width: 390, height: 844 })
    await visit(page, `/messages/${conversationId}`)
    const input = page.getByRole('textbox', { name: '消息内容' })
    const draft = '仅检查草稿🙂\n第二行不会发送'
    await input.tap(); await input.fill(draft)
    for (const size of [{ width: 844, height: 390 }, { width: 900, height: 240 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(size)
      await page.waitForFunction(() => document.querySelector('.message-composer button').getBoundingClientRect().bottom <= innerHeight + 1)
      await reachable(input)
      await reachable(page.getByRole('button', { name: '发送', exact: true }))
      await layout(page)
      assert.equal(await input.inputValue(), draft)
    }
    await input.fill('')
    await page.getByRole('button', { name: '返回会话列表', exact: true }).tap()
    await page.waitForURL('**/messages')
    assert.ok(await page.locator('.mobile-tabbar').isVisible())
  })
  }
  assert.ok(!writes.some(w => w.method === 'POST' && /\/chat\/conversations\/[^/]+\/messages$/.test(w.path)), 'No real chat messages may be sent')
} finally {
  await Promise.all([...pendingChecks])
  await browser.close()
  const summary = { run_id: config.run_id, at: new Date().toISOString(), verification: 'Chromium mobile/touch emulation with real local HTTP; not a physical device',
    browser_version: browser.version(), emulation: { engine: 'chromium', isMobile: true, hasTouch: true, deviceScaleFactor: 1,
      route_viewports: supplement ? ['360x812', '375x812', '768x1024', '390x844'] : ['320x844', '390x844', '430x844'], theme_viewports: supplement ? [] : ['320x740', '430x932', '844x390'],
      rotation_viewports: ['390x844', '844x390', '900x240', '390x844'], method: 'setViewportSize, not physical device rotation' },
    keyboard: 'OS keyboard is not exercised here; mocked visualViewport coverage is recorded separately',
    file_picker: 'Files are supplied through browser automation; the Android system file picker is not exercised',
    not_exercised_here: ['physical safe-area insets/notches', 'real OS keyboard and input method', 'real device rotation', ...(!supplement ? ['offline/reconnect recovery'] : []), 'physical Android file picker'],
    mode: { baseline, supplement, flows_only: flowsOnly, name_filter: only || null }, themes: supplement ? ['navy'] : flowsOnly || only ? [] : themes,
    passed: results.filter(r => r.status === 'pass' && r.kind !== 'evidence').length, failed: results.filter(r => r.status === 'fail').length, record_count: results.length, results, writes }
  await writeFile(resolve(output, 'results.json'), JSON.stringify(summary, null, 2))
}
assert.ok(results.every(r => r.status === 'pass'), 'Mobile acceptance failures are recorded in ' + output)
console.log(`Mobile preview acceptance passed: ${results.filter(r => r.kind !== 'evidence').length} checks; no real-device claim`)
