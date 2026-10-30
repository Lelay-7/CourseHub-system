import assert from 'node:assert/strict'
import { mkdir, writeFile, realpath } from 'node:fs/promises'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// Exercise the actual routed Vue view; only HTTP data is substituted.
const output = resolve('output/playwright/product-order-detail')
await mkdir(output, { recursive: true })
const server = await createServer({ server: { host: '127.0.0.1', port: 0, open: false, fs: { allow: [resolve('.'), await realpath('node_modules')] } }, define: { 'import.meta.env.VITE_USE_MOCK': 'false' }, logLevel: 'error' })
await server.listen()
const base = server.resolvedUrls.local[0]
let browser
const results = [], pageErrors = []
const token = `e30.${Buffer.from(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 7200 })).toString('base64url')}.fixture`
const order = id => ({ order_id: Number(id), order_no: `ORDER-${id}`, goods_name: `订单商品 ${id}`, status: id === '3' ? 3 : 1, score_amount: 10, create_time: '2026-10-22T12:00:00' })
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
async function fixture() {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const state = { user: { user_id: 1, user_type: 1, nickname: '订单同学甲', balance: 50 }, failRead: false, failCancel: false, delayedRead: null, delayedCancel: null, delayedDelete: null, reads: [], cancels: [], deletes: [] }
  await context.addInitScript(({ token, user }) => {
    localStorage.setItem('access_token', token); localStorage.setItem('refresh_token', token)
    localStorage.setItem('user_info', JSON.stringify(user)); localStorage.setItem('coursehub-theme', 'black')
  }, { token, user: state.user })
  await context.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    let data = { list: [], total: 0 }
    if (path === '/api/auth/userinfo') data = state.user
    if (path.endsWith('/unread')) data = { unread_count: 0 }
    if (path === '/api/chat/conversations') data = { items: [], next_cursor: null }
    const match = path.match(/^\/api\/order\/(\d+)(\/cancel)?$/)
    if (match) {
      const id = match[1]
      if (req.method() === 'GET') {
        state.reads.push(id)
        if (state.failRead) return route.fulfill({ status: 503, json: { code: 503, message: '测试注入：订单读取失败' } })
        if (state.delayedRead?.id === id) await state.delayedRead.promise
        data = order(id)
      } else if (req.method() === 'POST') {
        state.cancels.push(id)
        if (state.delayedCancel) await state.delayedCancel.promise
        if (state.failCancel) return route.fulfill({ status: 503, json: { code: 503, message: '测试注入：订单取消失败' } })
        data = { score_balance: 60 }
      } else if (req.method() === 'DELETE') {
        state.deletes.push(id)
        if (state.delayedDelete) await state.delayedDelete.promise
        data = null
      }
    }
    await route.fulfill({ json: { code: 200, data } })
  })
  const page = await context.newPage(); page.setDefaultTimeout(2500)
  page.on('pageerror', error => pageErrors.push(error.message))
  const go = path => page.evaluate(async path => { const { default: router } = await import('/src/router/index.js'); await router.push(path) }, path)
  const balance = () => page.evaluate(async () => { const { useUserStore } = await import('/src/stores/user.js'); return useUserStore().scoreBalance })
  return { context, page, state, go, balance }
}
async function test(name, run) {
  const f = await fixture()
  try { await run(f); results.push({ name, status: 'pass' }); console.log('PASS', name) }
  catch (error) { results.push({ name, status: 'fail', error: error.message }); console.error('FAIL', name, error.message.split('\n')[0]); await f.page.screenshot({ path: resolve(output, `failure-${results.length}.png`), fullPage: true }).catch(() => {}) }
  finally { f.state.delayedRead?.resolve(); f.state.delayedCancel?.resolve(); f.state.delayedDelete?.resolve(); await f.context.close() }
}
const loaded = (f, id) => f.page.getByText(`ORDER-${id}`, { exact: true }).waitFor()
const open = async (f, id = '1') => {
  await f.page.goto(base + 'profile/orders/' + id); await loaded(f, id)
  await f.page.evaluate(async () => { const { useUserStore } = await import('/src/stores/user.js'); await useUserStore().fetchUserInfo() })
}
const cancel = async f => {
  await f.page.getByRole('button', { name: '取消订单', exact: true }).click()
  const sent = f.page.waitForRequest(request => request.method() === 'POST' && /\/api\/order\/\d+\/cancel$/.test(new URL(request.url()).pathname))
  await f.page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click(); await sent
}
try {
  browser = await chromium.launch({ headless: true })
  await test('503 shows a retryable loading error and retry restores the order', async f => {
    f.state.failRead = true
    await f.page.goto(base + 'profile/orders/1')
    await f.page.getByText('订单加载失败，请重试', { exact: true }).waitFor()
    assert.equal(await f.page.getByRole('button', { name: '取消订单', exact: true }).count(), 0)
    f.state.failRead = false
    await f.page.getByRole('button', { name: '重试', exact: true }).click(); await loaded(f, '1')
    assert.equal(f.state.reads.length, 2)
  })
  await test('reused route clears the old order and rejects an older ID response', async f => {
    await open(f); f.state.delayedRead = { id: '2', ...deferred() }
    const slowRead = f.page.waitForRequest(request => new URL(request.url()).pathname === '/api/order/2')
    await f.go('/profile/orders/2'); await slowRead
    await f.page.waitForFunction(() => !document.body.textContent.includes('ORDER-1'))
    await f.go('/profile/orders/3'); await loaded(f, '3')
    f.state.delayedRead.resolve(); await f.page.waitForTimeout(100)
    assert.equal(await f.page.getByText('ORDER-2', { exact: true }).count(), 0)
    await f.page.getByText('历史订单未记录交付说明', { exact: true }).waitFor()
  })
  await test('cancel is pending during confirmation and request, submits once and syncs balance', async f => {
    await open(f); f.state.delayedCancel = deferred()
    await f.page.getByRole('button', { name: '取消订单', exact: true }).evaluate(el => { el.click(); el.click() })
    await f.page.getByRole('dialog').waitFor()
    assert.equal(await f.page.locator('.order-actions').getByRole('button', { name: '取消订单', exact: true }).isDisabled(), true)
    assert.equal(await f.page.getByRole('dialog').count(), 1)
    await f.page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
    await f.page.waitForTimeout(100); assert.equal(f.state.cancels.length, 1)
    await f.page.locator('.order-actions').getByRole('button', { name: '取消订单', exact: true }).evaluate(el => el.click())
    assert.equal(f.state.cancels.length, 1)
    f.state.delayedCancel.resolve()
    await f.page.getByText('订单已取消，积分已退回', { exact: true }).first().waitFor()
    assert.equal(await f.balance(), 60)
    await f.page.locator('.order-info').getByText('已取消', { exact: true }).waitFor()
  })
  await test('cancel failure retains order and supports an explicit retry', async f => {
    await open(f); f.state.failCancel = true; await cancel(f)
    await f.page.getByText('取消失败，订单未更新，请重试', { exact: true }).waitFor()
    assert.equal(await f.page.getByRole('button', { name: '取消订单', exact: true }).isDisabled(), false)
    assert.equal(await f.balance(), 50)
    f.state.failCancel = false; await cancel(f)
    await f.page.locator('.order-info').getByText('已取消', { exact: true }).waitFor()
    assert.equal(f.state.cancels.length, 2); assert.equal(await f.balance(), 60)
  })
  await test('cancel result after changing order cannot change current order or balance', async f => {
    await open(f); f.state.delayedCancel = deferred(); await cancel(f)
    await f.go('/profile/orders/3'); await loaded(f, '3')
    f.state.delayedCancel.resolve(); await f.page.waitForTimeout(100)
    assert.equal(await f.balance(), 50)
    await f.page.locator('.order-info').getByText('已完成', { exact: true }).waitFor()
  })
  await test('cancel for A cannot change B balance or retain A order', async f => {
    await open(f); f.state.delayedCancel = deferred(); await cancel(f)
    f.state.user = { user_id: 2, user_type: 1, nickname: '订单同学乙', balance: 200 }
    await f.page.evaluate(async ({ token, user }) => {
      const { useUserStore } = await import('/src/stores/user.js'), auth = await import('/src/utils/auth.js'), store = useUserStore()
      store.logout(); auth.saveTokens(token, token); store.accessToken = token; store.refreshToken = token; store.updateUserInfo(user); await store.updateScoreBalance(200)
    }, { token, user: f.state.user })
    f.state.delayedCancel.resolve(); await f.page.waitForTimeout(100)
    assert.equal(await f.balance(), 200)
    assert.equal(await f.page.getByText('ORDER-1', { exact: true }).count(), 0)
    assert.equal(await f.page.getByText('订单已取消，积分已退回', { exact: true }).count(), 0)
  })
  await test('confirmation belongs to its original order and cannot cancel another route', async f => {
    await open(f)
    await f.page.getByRole('button', { name: '取消订单', exact: true }).click()
    await f.page.getByRole('dialog').waitFor(); await f.go('/profile/orders/3'); await loaded(f, '3')
    await f.page.getByRole('dialog').waitFor({ state: 'hidden' })
    await f.page.waitForTimeout(100); assert.equal(f.state.cancels.length, 0)
  })
  await test('late delete response cannot navigate away from another order', async f => {
    await open(f, '3'); f.state.delayedDelete = deferred()
    await f.page.getByRole('button', { name: '删除记录', exact: true }).evaluate(el => { el.click(); el.click() })
    await f.page.getByRole('dialog').waitFor()
    assert.equal(await f.page.locator('.order-actions').getByRole('button', { name: '删除记录', exact: true }).isDisabled(), true)
    const sent = f.page.waitForRequest(request => request.method() === 'DELETE' && new URL(request.url()).pathname === '/api/order/3')
    await f.page.getByRole('dialog').getByRole('button', { name: '确认删除', exact: true }).click()
    await sent
    await f.go('/profile/orders/2'); await loaded(f, '2')
    f.state.delayedDelete.resolve(); await f.page.waitForTimeout(100)
    assert.equal(f.state.deletes.length, 1); assert.match(f.page.url(), /\/profile\/orders\/2$/)
  })
  assert.deepEqual(pageErrors, [])
} finally {
  await writeFile(resolve(output, 'results.json'), JSON.stringify({ results, pageErrors }, null, 2), 'utf8')
  await browser?.close(); await server.close()
}
console.log(JSON.stringify({ passed: results.filter(r => r.status === 'pass').length, total: results.length }))
if (results.some(r => r.status === 'fail')) process.exitCode = 1
