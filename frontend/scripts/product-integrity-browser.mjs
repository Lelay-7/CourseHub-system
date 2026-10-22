import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// Real Vue pages with deterministic HTTP fixtures. No business database is contacted.
const output = resolve('output/playwright/product-integrity')
await mkdir(output, { recursive: true })
const server = await createServer({ server: { host: '127.0.0.1', port: 5197, strictPort: true }, logLevel: 'error' })
await server.listen()
const base = 'http://127.0.0.1:5197'
const browser = await chromium.launch({ headless: true })
const results = []
const group = process.argv.find(arg => arg.startsWith('--group='))?.split('=')[1]
const courses = Array.from({ length: 51 }, (_, i) => ({ course_id: i + 1, course_name: i ? `课程 ${i + 1}` : 'Web 前端实践', course_code: `C${i + 1}`, course_type: 1, teacher_id: 3, teacher_name: '陈老师', category_id: 1, credit: 3, status: 1, selection_count: 1, schedule: '周一', avg_rating: 4 }))
const material = { material_id: 1, material_name: '复习资料', uploader_name: '同学甲', uploader_id: 1, revision: 2, material_type: 1, course_id: 1, download_score: 0, status: 0, has_file: true }
const paginate = (items, url) => { const p = Number(url.searchParams.get('page') || 1), size = Number(url.searchParams.get('pageSize') || 20); return { list: items.slice((p - 1) * size, p * size), total: items.length } }
async function fixture(role = 1, override = () => undefined) {
  const user = { user_id: 1, user_type: role, nickname: '同学甲', score_balance: 50 }
  const token = `e30.${Buffer.from(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 7200 })).toString('base64url')}.fixture`
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 920 } })
  await ctx.addInitScript(({ token, user }) => {
    localStorage.setItem('access_token', token); localStorage.setItem('refresh_token', token)
    localStorage.setItem('user_info', JSON.stringify(user)); localStorage.setItem('coursehub-theme', 'black')
  }, { token, user })
  const requests = [], errors = []
  await ctx.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    requests.push({ path, method: req.method(), query: Object.fromEntries(url.searchParams), body: req.postData() })
    const custom = await override({ route, req, url, path, requests })
    if (custom === true) return
    let data = {}
    if (path === '/api/auth/userinfo') data = user
    else if (path === '/api/course/categories') data = [{ category_id: 1, category_name: '计算机' }]
    else if (path === '/api/course/teachers') data = [{ user_id: 3, teacher_name: '陈老师' }]
    else if (path === '/api/course/selected') data = []
    else if (path === '/api/course/list' || path === '/api/admin/courses') data = paginate(courses.filter(c => !url.searchParams.get('keyword') || c.course_name.includes(url.searchParams.get('keyword'))), url)
    else if (/^\/api\/course\/\d+$/.test(path)) data = { course: courses[0], reviews: [], is_selected: false }
    else if (path === '/api/admin/banners') data = [{ banner_id: 1, title: '旧横幅', image_url: '/old.jpg', sort_order: 1 }]
    else if (path === '/api/score/details') data = paginate(Array.from({ length: 40 }, (_, i) => ({ record_id: i + 1, source_desc: `流水 ${i + 1}`, change_amount: 2 })), url)
    else if (path === '/api/order/list') data = paginate(Array.from({ length: 40 }, (_, i) => ({ order_id: i + 1, order_no: `订单${i + 1}`, goods_name: '学习用品', status: 1, score_amount: 5 })), url)
    else if (path === '/api/admin/materials') data = paginate(Array.from({ length: 21 }, (_, i) => ({ ...material, material_id: i + 1 })), url)
    else if (/^\/api\/admin\/materials\/\d+$/.test(path)) data = { ...material, audit_history: [], revisions: [{ revision: 2 }] }
    else if (path === '/api/material/list') data = { list: [{ ...material, status: 1 }], total: 1 }
    else if (path === '/api/material/my') data = [{ ...material, status: 2, reject_reason: '请更正文件' }]
    else if (/^\/api\/material\/\d+$/.test(path)) data = { material: { ...material, status: 2 }, audit_history: [], revisions: [] }
    else if (path === '/api/mall/goods') data = paginate(Array.from({ length: 25 }, (_, i) => ({ goods_id: i + 1, goods_name: `商品${i + 1}`, price_score: i + 1, stock: 5, status: 1 })), url)
    else if (path.endsWith('/unread')) data = { unread_count: 0 }
    else if (path.endsWith('/list')) data = { list: [], total: 0 }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
  })
  const page = await ctx.newPage(); page.setDefaultTimeout(4000)
  page.on('pageerror', error => { errors.push(error.message); console.log('PAGEERROR', error.message) })
  return { ctx, page, requests, errors }
}
async function test(name, bucket, fn) {
  if (group && group !== bucket && group !== 'all') return
  try { await fn(); results.push({ name, status: 'pass' }); console.log(`PASS ${name}`) }
  catch (error) { results.push({ name, status: 'fail', message: error.message }); console.log(`FAIL ${name}: ${error.message.split('\n')[0]}`) }
}
try {
  await test('F01 cancel course edit then add starts a new empty form', 'list-state', async () => {
    const f = await fixture(3)
    try {
      await f.page.goto(base + '/admin/courses'); await f.page.getByRole('button', { name: '编辑', exact: true }).first().click()
      await f.page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
      await f.page.getByRole('button', { name: '新增课程', exact: true }).click()
      assert.match(await f.page.getByRole('dialog').innerText(), /新增课程/)
      assert.equal(await f.page.getByRole('dialog').locator('input').first().inputValue(), '')
    } finally { await f.ctx.close() }
  })
  for (const [path, api] of [['/profile/score', '/api/score/details'], ['/profile/orders', '/api/order/list']]) {
    await test(`F02 page two remains page two: ${path}`, 'list-state', async () => {
      const f = await fixture()
      try {
        await f.page.goto(base + path); await f.page.locator('.el-pager li').filter({ hasText: /^2$/ }).click()
        await f.page.waitForTimeout(250)
        assert.equal(f.requests.filter(r => r.path === api).at(-1).query.page, '2')
      } finally { await f.ctx.close() }
    })
  }
  await test('F06 older course query cannot replace newer result', 'list-state', async () => {
    const f = await fixture(1, async ({ route, url, path }) => {
      if (path !== '/api/course/list' || !url.searchParams.get('keyword')) return
      const keyword = url.searchParams.get('keyword')
      await new Promise(r => setTimeout(r, keyword === '算法' ? 900 : 20))
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { list: [{ ...courses[0], course_name: keyword + '课程' }], total: 1 } }) }); return true
    })
    try {
      await f.page.goto(base + '/course'); const input = f.page.getByPlaceholder('搜索课程名称、课程代码、授课教师')
      await input.fill('算法'); await input.press('Enter'); await f.page.waitForTimeout(300)
      await input.fill('Web'); await input.press('Enter'); await f.page.waitForTimeout(1200)
      assert.match(await f.page.locator('.course-grid-pro').innerText(), /Web课程/)
      assert.doesNotMatch(await f.page.locator('.course-grid-pro').innerText(), /算法课程/)
    } finally { await f.ctx.close() }
  })
  await test('F07 back from course detail restores search', 'list-state', async () => {
    const f = await fixture()
    try {
      await f.page.goto(base + '/course'); const input = f.page.getByPlaceholder('搜索课程名称、课程代码、授课教师')
      await input.fill('Web'); await input.press('Enter'); await f.page.waitForTimeout(400)
      await f.page.getByRole('button', { name: '查看详情', exact: true }).first().click(); await f.page.waitForURL('**/course/1')
      await f.page.goBack(); await input.waitFor(); assert.equal(await input.inputValue(), 'Web')
    } finally { await f.ctx.close() }
  })
  await test('F03 all 21 admin materials are reachable', 'list-contracts', async () => {
    const f = await fixture(3)
    try {
      await f.page.goto(base + '/admin/materials'); await f.page.locator('.el-pager li').filter({ hasText: /^2$/ }).click()
      await f.page.waitForTimeout(250); assert.equal(f.requests.filter(r => r.path === '/api/admin/materials').at(-1).query.page, '2')
      assert.match(await f.page.locator('.el-table__body-wrapper').innerText(), /21/)
    } finally { await f.ctx.close() }
  })
  await test('F09 material failure is retryable and distinct from empty', 'list-contracts', async () => {
    let failed = false
    const f = await fixture(1, async ({ route, path }) => {
      if (path !== '/api/material/list' || failed) return
      failed = true; await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '测试注入失败' }) }); return true
    })
    try {
      await f.page.goto(base + '/material'); await f.page.getByRole('button', { name: '重试', exact: true }).click()
      await f.page.getByText('复习资料', { exact: true }).waitFor(); assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  await test('F08 course links preserve the stable course identifier', 'closures', async () => {
    const f = await fixture()
    try { await f.page.goto(base + '/course'); await f.page.getByRole('button', { name: '查看资料', exact: true }).first().click(); await f.page.waitForURL('**/material**'); assert.equal(new URL(f.page.url()).searchParams.get('courseId'), '1') }
    finally { await f.ctx.close() }
  })
  await test('reporter can read their handling result', 'closures', async () => {
    const f = await fixture(1, async ({ path, route }) => {
      if (path !== '/api/reports/my') return
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { list: [{ report_id: 1, material_id: 1, material_name: '复习资料', status: 1, reason: '内容有误', resolution_reason: '核实后已下架' }], total: 1 } }) }); return true
    })
    try { await f.page.goto(base + '/profile/feedback'); await f.page.getByText('我的举报', { exact: true }).click(); await f.page.getByText('处理结论：核实后已下架', { exact: true }).waitFor() }
    finally { await f.ctx.close() }
  })
  await test('order detail displays persisted fulfillment instructions', 'closures', async () => {
    const f = await fixture(1, async ({ path, route }) => {
      if (path !== '/api/order/1') return
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { order_id: 1, order_no: 'O1', goods_id: 1, goods_name: '学习用品', status: 3, fulfillment_note: '明天到教学楼领取', score_amount: 10 } }) }); return true
    })
    try { await f.page.goto(base + '/profile/orders/1'); await f.page.getByText('明天到教学楼领取', { exact: true }).waitFor() }
    finally { await f.ctx.close() }
  })
  await test('F11 mall sort is sent to the server', 'list-contracts', async () => {
    const f = await fixture()
    try { await f.page.goto(base + '/mall'); await f.page.getByText('积分从低到高', { exact: true }).click(); await f.page.waitForTimeout(200); assert.equal(f.requests.filter(r => r.path === '/api/mall/goods').at(-1).query.sortBy, 'price_score') }
    finally { await f.ctx.close() }
  })
  await test('owner edits rejected metadata and submits its exact version', 'material-review', async () => {
    const f = await fixture()
    try {
      await f.page.goto(base + '/material?tab=my'); await f.page.getByRole('button', { name: '修改重提', exact: true }).click()
      await f.page.getByRole('dialog').getByLabel('资料名称', { exact: true }).fill('修正后的复习资料')
      await f.page.getByRole('button', { name: '保存修改并提交审核', exact: true }).click()
      await f.page.getByRole('dialog').waitFor({ state: 'hidden' })
      const request = f.requests.find(r => r.path === '/api/material/1/resubmit')
      assert.match(request.body, /name="expected_revision"\r\n\r\n2/)
      assert.match(request.body, /修正后的复习资料/); assert.doesNotMatch(request.body, /name="file"/)
    } finally { await f.ctx.close() }
  })
  await test('admin checks protected file then approves the inspected version', 'material-review', async () => {
    const f = await fixture(3, async ({ path, route }) => {
      if (path !== '/api/admin/materials/1/file') return
      await route.fulfill({ contentType: 'application/pdf', headers: { 'Content-Disposition': 'attachment; filename="fixture.pdf"' }, body: '%PDF-1.4\nfixture' }); return true
    })
    try {
      await f.page.goto(base + '/admin/materials'); await f.page.getByRole('button', { name: '检查与处理', exact: true }).first().click()
      const approve = f.page.getByRole('button', { name: '通过当前版本', exact: true })
      assert.equal(await approve.isDisabled(), true)
      const downloaded = f.page.waitForEvent('download'); await f.page.getByRole('button', { name: '下载当前文件检查', exact: true }).click(); await downloaded
      await approve.click(); await f.page.waitForTimeout(200)
      assert.deepEqual(JSON.parse(f.requests.find(r => r.path.endsWith('/approve')).body), { expected_revision: 2 })
      assert.equal(f.requests.filter(r => r.path.endsWith('/download')).length, 0)
      assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  await test('reject needs a reason and stale-version failure preserves draft', 'material-review', async () => {
    const f = await fixture(3, async ({ path, route }) => {
      if (!path.endsWith('/reject')) return
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 409, message: '资料版本已改变，请重新检查' }) }); return true
    })
    try {
      await f.page.goto(base + '/admin/materials'); await f.page.getByRole('button', { name: '检查与处理', exact: true }).first().click()
      const reject = f.page.getByRole('button', { name: '驳回并反馈原因', exact: true })
      await reject.click(); assert.equal(f.requests.filter(r => r.path.endsWith('/reject')).length, 0)
      const input = f.page.getByPlaceholder('驳回或下架时必填，请说明需要修改的内容'); await input.fill('请修正错误内容'); await reject.click()
      await f.page.locator('.action-error').filter({ hasText: '资料版本已改变' }).waitFor()
      assert.equal(await input.inputValue(), '请修正错误内容')
      assert.deepEqual(JSON.parse(f.requests.find(r => r.path.endsWith('/reject')).body), { reason: '请修正错误内容', expected_revision: 2 })
    } finally { await f.ctx.close() }
  })
  await test('old account delayed course response cannot repopulate cleared content', 'list-state', async () => {
    const f = await fixture(1, async ({ path, route }) => {
      if (path !== '/api/course/list') return
      await new Promise(r => setTimeout(r, 650)); await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { list: [{ ...courses[0], course_name: '旧账号结果' }], total: 1 } }) }); return true
    })
    try {
      await f.page.goto(base + '/course'); await f.page.getByPlaceholder('搜索课程名称、课程代码、授课教师').waitFor()
      await f.page.evaluate(async () => { const auth = await import('/src/utils/auth.js'); auth.clearAllAuth() })
      await f.page.waitForTimeout(850); assert.doesNotMatch(await f.page.locator('body').innerText(), /旧账号结果/)
      assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  await test('admin feedback repeat click sends one reply and preserves failed draft', 'closures', async () => {
    const f = await fixture(3, async ({ path, route }) => {
      if (path === '/api/admin/feedback') { await route.fulfill({ json: { code: 200, data: { list: [{ feedback_id: 1, content: '需要帮助', status: 0 }], total: 1 } } }); return true }
      if (path.endsWith('/reply')) { await new Promise(r => setTimeout(r, 500)); await route.fulfill({ json: { code: 500, message: '稍后重试' } }); return true }
    })
    try {
      await f.page.goto(base + '/profile/feedback'); await f.page.getByRole('button', { name: '查看与回复', exact: true }).click(); const input = f.page.getByPlaceholder('输入回复...'); await input.fill('请重新打开页面')
      await f.page.getByRole('button', { name: '回复', exact: true }).evaluate(button => { button.click(); button.click() })
      await f.page.waitForTimeout(750); assert.equal(f.requests.filter(r => r.path.endsWith('/reply')).length, 1); assert.equal(await input.inputValue(), '请重新打开页面'); assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  await test('admin report requires a reason and sends the selected resolution', 'closures', async () => {
    const f = await fixture(3, async ({ path, route }) => {
      if (path !== '/api/admin/reports') return
      await route.fulfill({ json: { code: 200, data: { list: [{ report_id: 7, material_id: 1, material_name: '复习资料', reason: '内容错误', status: 0 }], total: 1 } } }); return true
    })
    try {
      await f.page.goto(base + '/admin/reports'); await f.page.getByRole('button', { name: '处理', exact: true }).click(); const dialog = f.page.getByRole('dialog')
      await dialog.getByRole('button', { name: '保存处理结果', exact: true }).click(); assert.equal(f.requests.filter(r => r.path.endsWith('/handle')).length, 0)
      await dialog.locator('textarea').fill('核查后内容正确'); await dialog.getByRole('button', { name: '保存处理结果', exact: true }).click(); await dialog.waitFor({ state: 'hidden' })
      assert.deepEqual(JSON.parse(f.requests.find(r => r.path.endsWith('/handle')).body), { action: 'dismiss', reason: '核查后内容正确' })
    } finally { await f.ctx.close() }
  })
  await test('admin order requires fulfillment instructions and retains failed draft', 'closures', async () => {
    const order = { order_id: 1, order_no: 'O1', goods_name: '学习用品', status: 1 }
    const f = await fixture(3, async ({ path, route }) => {
      if (path === '/api/admin/orders') { await route.fulfill({ json: { code: 200, data: { list: [order], total: 1 } } }); return true }
      if (path === '/api/admin/orders/1') { await route.fulfill({ json: { code: 200, data: order } }); return true }
      if (path.endsWith('/complete')) { await route.fulfill({ json: { code: 409, message: '订单已更新，请重试' } }); return true }
    })
    try {
      await f.page.goto(base + '/admin/orders'); await f.page.getByRole('button', { name: '详情与处理', exact: true }).click(); const dialog = f.page.getByRole('dialog')
      await dialog.getByRole('button', { name: '确认完成', exact: true }).click(); assert.equal(f.requests.filter(r => r.path.endsWith('/complete')).length, 0)
      await dialog.locator('textarea').fill('明天下午图书馆领取'); await dialog.getByRole('button', { name: '确认完成', exact: true }).click(); await dialog.getByText('订单已更新，请重试', { exact: true }).waitFor()
      assert.equal(await dialog.locator('textarea').inputValue(), '明天下午图书馆领取'); assert.deepEqual(JSON.parse(f.requests.find(r => r.path.endsWith('/complete')).body), { fulfillment_note: '明天下午图书馆领取' })
    } finally { await f.ctx.close() }
  })
  await test('deleting the last item of the last page falls back one page', 'list-contracts', async () => {
    let reviews = Array.from({ length: 21 }, (_, i) => ({ review_id: i + 1, content: '评价 ' + (i + 1), rating: 4 }))
    const f = await fixture(3, async ({ path, route, req, url }) => {
      if (path === '/api/admin/reviews') { await route.fulfill({ json: { code: 200, data: paginate(reviews, url) } }); return true }
      if (path === '/api/admin/reviews/21' && req.method() === 'DELETE') { reviews.pop(); await route.fulfill({ json: { code: 200, data: {} } }); return true }
    })
    try {
      await f.page.goto(base + '/admin/reviews'); await f.page.locator('.el-pager li').filter({ hasText: /^2$/ }).click(); await f.page.getByText('评价 21', { exact: true }).waitFor()
      await f.page.getByRole('button', { name: '删除', exact: true }).click(); await f.page.locator('.el-popconfirm__action').getByRole('button').last().click(); await f.page.getByText('评价 1', { exact: true }).waitFor()
      assert.equal(f.requests.filter(r => r.path === '/api/admin/reviews').at(-1).query.page, '1'); assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  for (const [path, endpoint, content] of [['/profile/favorites', '/api/favorite/list', '暂无收藏'], ['/profile/courses', '/api/course/selected', '还没有选择课程'], ['/profile/reviews', '/api/course/reviews/my', '暂无评价记录']]) {
    await test(`private list failure retries without reporting empty: ${path}`, 'private-lists', async () => {
      let fail = true
      const f = await fixture(1, async ({ path: requestPath, route }) => {
        if (requestPath !== endpoint) return
        await route.fulfill(fail ? { status: 503, json: { code: 503, message: '暂时不可用' } } : { json: { code: 200, data: [] } }); return true
      })
      try {
        await f.page.goto(base + path); const retry = f.page.getByRole('button', { name: '重试', exact: true }); await retry.waitFor()
        assert.doesNotMatch(await f.page.locator('body').innerText(), new RegExp(content)); fail = false; await retry.click(); await retry.waitFor({ state: 'hidden' })
        await f.page.getByText(new RegExp(content)).waitFor(); assert.match(await f.page.locator('body').innerText(), new RegExp(content)); assert.deepEqual(f.errors, [])
      } finally { await f.ctx.close() }
    })
  }
  await test('favorite late course response does not replace selected material tab', 'private-lists', async () => {
    const f = await fixture(1, async ({ path, route, url }) => {
      if (path !== '/api/favorite/list') return
      const isCourse = url.searchParams.get('type') === 'course'; await new Promise(r => setTimeout(r, isCourse ? 800 : 20))
      await route.fulfill({ json: { code: 200, data: [{ favoriteId: 1, targetId: 1, courseName: '旧课程收藏', materialName: isCourse ? undefined : '最新资料收藏' }] } }); return true
    })
    try { await f.page.goto(base + '/profile/favorites'); await f.page.getByRole('tab', { name: '资料', exact: true }).click(); await f.page.waitForTimeout(1100); assert.match(await f.page.locator('.fav-list').innerText(), /最新资料收藏/); assert.doesNotMatch(await f.page.locator('.fav-list').innerText(), /内容已失效/); assert.deepEqual(f.errors, []) }
    finally { await f.ctx.close() }
  })
  await test('my uploads at 390px exposes status version rejection and correction on summary cards', 'upload-layout', async () => {
    const f = await fixture()
    try {
      await f.page.setViewportSize({ width: 390, height: 920 }); await f.page.goto(base + '/material?tab=my'); const card = f.page.locator('.upload-summary').first(); await card.waitFor()
      assert.match(await card.innerText(), /已驳回/); assert.match(await card.innerText(), /V2/); assert.match(await card.innerText(), /请更正文件/)
      assert.equal(await f.page.locator('.upload-table').isVisible(), false); assert.ok(await f.page.evaluate(() => document.documentElement.scrollWidth) <= 391)
      await f.page.screenshot({ path: resolve(output, 'my-uploads-390.png'), fullPage: true }); await card.getByRole('button', { name: '修改重提', exact: true }).click(); await f.page.getByRole('dialog').waitFor(); assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  await test('same list instance stores and restores each URL after query navigation', 'route-memory', async () => {
    const f = await fixture()
    try {
      await f.page.goto(base + '/course?keyword=Web'); const input = f.page.getByPlaceholder('搜索课程名称、课程代码、授课教师'); await input.waitFor(); assert.equal(await input.inputValue(), 'Web')
      await f.page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '课程', exact: true }).click(); await f.page.waitForURL(base + '/course')
      await input.fill('课程'); await input.press('Enter'); await f.page.locator('.el-pager li').filter({ hasText: /^2$/ }).click(); await f.page.waitForTimeout(200)
      await f.page.getByRole('button', { name: '查看详情', exact: true }).first().click(); await f.page.waitForURL(/\/course\/\d+$/); await f.page.goBack(); await input.waitFor()
      assert.equal(await input.inputValue(), '课程'); assert.equal(await f.page.locator('.el-pager li.is-active').innerText(), '2')
      await f.page.goBack(); await f.page.waitForURL('**/course?keyword=Web'); await f.page.getByRole('button', { name: '查看详情', exact: true }).first().waitFor(); assert.equal(await input.inputValue(), 'Web')
      await f.page.goForward(); await f.page.waitForURL(base + '/course'); await f.page.waitForTimeout(200); assert.equal(await input.inputValue(), '课程'); assert.equal(await f.page.locator('.el-pager li.is-active').innerText(), '2'); assert.deepEqual(f.errors, [])
    } finally { await f.ctx.close() }
  })
  if (group === 'layouts') {
    for (const width of [320, 390, 768, 1024, 1440]) {
      for (const [path, role] of [['/course', 1], ['/material', 1], ['/square', 1], ['/mall', 1], ['/admin/dashboard', 3], ['/admin/courses', 3], ['/admin/materials', 3]]) {
        await test(`layout ${path} at ${width}px`, 'layouts', async () => {
          const f = await fixture(role)
          try {
            await f.page.setViewportSize({ width, height: 920 }); await f.page.goto(base + path); await f.page.locator('.page-header').waitFor(); await f.page.waitForTimeout(250)
            const actual = await f.page.evaluate(() => document.documentElement.scrollWidth)
            await f.page.screenshot({ path: resolve(output, `${path.slice(1).replaceAll('/', '-')}-${width}.png`), fullPage: true })
            assert.ok(actual <= width + 1, `${path}: document ${actual}px exceeds ${width}px`); assert.deepEqual(f.errors, [])
          } finally { await f.ctx.close() }
        })
      }
    }
  }
} finally {
  await browser.close(); await server.close()
  await writeFile(resolve(output, `results-${group || 'all'}.json`), JSON.stringify(results, null, 2), 'utf8')
}
if (results.some(r => r.status !== 'pass')) process.exitCode = 1
