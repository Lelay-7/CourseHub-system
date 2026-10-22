import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { randomUUID } from 'node:crypto'
import { chromium } from 'playwright'

// Writes only to the nonce-verified, isolated local product preview. No production target option.
const config = JSON.parse(await readFile(resolve('../backend/target/product-preview/config.json'), 'utf8'))
assert.match(config.database, /^coursehub_product_preview_[a-f0-9]{32}$/)
const base = `http://127.0.0.1:${config.web_port}`
const ownership = await (await fetch(base + '/__preview')).json()
assert.equal(ownership.nonce, config.nonce); assert.equal(ownership.database, config.database)
const output = resolve('output/playwright/product-live')
await mkdir(output, { recursive: true })
const results = [], stamp = Date.now().toString().slice(-8)
async function raw(path, token, { method = 'GET', body } = {}) {
  return fetch(base + path, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) },
  body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body) })
}
async function api(path, token, options, expected = 200) {
  const response = await raw(path, token, options), json = await response.json()
  assert.equal(json.code, expected, `${path}: ${json.message}`)
  return json.data
}
async function check(name, fn) {
  if (results.some(r => r.name === name && r.status === 'pass')) { console.log('ALREADY VERIFIED ' + name); return }
  await fn(); results.push({ name, status: 'pass' }); console.log('PASS ' + name)
}
const sessions = []
for (const account of config.accounts) {
  const pair = await api('/api/auth/login', null, { method: 'POST', body: { username: account.username, password: account.password } })
  const user = await api('/api/auth/userinfo', pair.accessToken)
  sessions.push({ ...account, ...pair, user })
}
const [a,b] = sessions.filter(s => s.user.user_type === 1), teacher = sessions.find(s => s.user.user_type === 2), admin = sessions.find(s => s.user.user_type === 3)
assert.ok(a && b && teacher && admin)
const at = a.accessToken, bt = b.accessToken, mt = admin.accessToken
let materialId, orderId, conversationId, courseId, goodsId
const routesOnly = process.argv.includes('--routes-only')
if (routesOnly || process.argv.includes('--resume')) {
  const previous = JSON.parse(await readFile(resolve(output,'summary-api.json'),'utf8'))
  assert.equal(previous.run_id, config.run_id)
  ;({materialId,orderId,conversationId,courseId,goodsId} = previous.fixtures)
  if (!routesOnly) results.push(...previous.results)
}
try {
  if (!routesOnly) {
  await check('admin report status filter matches records and count', async () => {
    const total = await api('/api/admin/reports?page=1&pageSize=100', mt)
    let sum = 0
    for (const status of [0,1,2]) {
      const page = await api(`/api/admin/reports?page=1&pageSize=100&status=${status}`,mt)
      assert.ok(page.list.every(row=>row.status===status)); assert.equal(page.list.length,page.total); sum += page.total
    }
    assert.equal(sum,total.total)
  })
  await check('complete pagination and server ordering', async () => {
    const c = await api('/api/course/list?page=1&pageSize=100', at); assert.ok(c.total > 20); courseId = c.list[0].course_id
    const g = await api('/api/mall/goods?page=1&pageSize=20&sortBy=price_score', at)
    const next = await api('/api/mall/goods?page=2&pageSize=20&sortBy=price_score', at)
    assert.ok(g.total > 20 && next.list.length); assert.ok(g.list.at(-1).price_score <= next.list[0].price_score)
    goodsId = g.list.find(x => x.stock > 2 && x.price_score > 0)?.goods_id || g.list[0].goods_id
    for (const path of ['courses','materials','reviews','users','orders','goods','reports','square','feedback']) {
      const rows = await api(`/api/admin/${path}?page=2&pageSize=10`, mt)
      assert.ok(Array.isArray(rows.list) && Number.isFinite(rows.total), path)
    }
    const teachers = await api('/api/course/teachers', at); assert.ok(teachers.some(t => t.user_id === teacher.user.user_id))
  })
  await check('material upload, controlled inspection, reject, replace, stale review, concurrent approve and download', async () => {
    const before = (await api('/api/score/balance', at)).balance
    const form = (text, revision) => {
      const f = new FormData(); f.append('file', new Blob([text], { type: 'text/plain' }), `closure-${stamp}.txt`)
      f.append('materialName', `闭环验证 ${stamp}`); f.append('materialType', '1'); f.append('courseId', String(courseId)); f.append('downloadScore','2'); f.append('description','隔离环境的资料版本验证')
      if (revision) f.append('expected_revision', String(revision))
      return f
    }
    const uploaded = await api('/api/material/upload', at, { method:'POST', body:form('version one') }); materialId = uploaded.material.material_id
    const unauthorised = await raw(`/api/admin/materials/${materialId}/file`, bt); assert.equal(unauthorised.status,403)
    const detail = await api(`/api/admin/materials/${materialId}`, mt); assert.equal(detail.revision,1)
    assert.equal(await (await raw(`/api/admin/materials/${materialId}/file`, mt)).text(),'version one')
    await api(`/api/admin/materials/${materialId}/reject`, mt, { method:'PUT', body:{ expected_revision:1, reason:'请补充第二版文件' } })
    const owner = await api(`/api/material/${materialId}`, at); assert.equal(owner.material.reject_reason,'请补充第二版文件')
    const denied = await (await raw(`/api/material/${materialId}/resubmit`, bt,{ method:'PUT', body:form('illegal replacement',1) })).json(); assert.notEqual(denied.code,200)
    await api(`/api/material/${materialId}/resubmit`, at,{ method:'PUT', body:form('version two',1) })
    await api(`/api/admin/materials/${materialId}/approve`, mt,{ method:'PUT', body:{ expected_revision:1 } },409)
    const approvals = await Promise.all([1,2].map(() => raw(`/api/admin/materials/${materialId}/approve`, mt,{ method:'PUT',body:{expected_revision:2} }).then(r=>r.json())))
    assert.deepEqual(approvals.map(x=>x.code).sort(),[200,409]); assert.equal((await api('/api/score/balance',at)).balance,before+2)
    assert.equal(await (await raw(`/api/admin/materials/${materialId}/file?revision=1`,mt)).text(),'version one')
    const balanceB = (await api('/api/score/balance',bt)).balance
    assert.equal(await (await raw(`/api/material/${materialId}/download`,bt)).text(),'version two')
    assert.equal(await (await raw(`/api/material/${materialId}/download`,bt)).text(),'version two')
    assert.equal((await api('/api/score/balance',bt)).balance,balanceB-2)
    const audit = await api(`/api/admin/materials/${materialId}`,mt); assert.equal(audit.revisions.length,2); assert.equal(audit.audit_history.length,2)
  })
  await check('report decision persists and is private to its reporter', async () => {
    const report = await api(`/api/material/${materialId}/report`,bt,{method:'POST',body:{reason:`隔离举报 ${stamp}`}})
    await api(`/api/admin/reports/${report.report_id}/handle`,mt,{method:'PUT',body:{action:'dismiss',reason:'已核实：本地验证资料，不予下架'}})
    const mine = await api('/api/reports/my?page=1&pageSize=100',bt), other = await api('/api/reports/my?page=1&pageSize=100',at)
    const record = mine.list.find(r=>r.report_id===report.report_id); assert.equal(record.resolution_action,'dismiss'); assert.ok(record.resolution_reason)
    assert.ok(!other.list.some(r=>r.report_id===report.report_id))
    const again = await (await raw(`/api/admin/reports/${report.report_id}/handle`,mt,{method:'PUT',body:{action:'dismiss',reason:'重复'}})).json(); assert.notEqual(again.code,200)
  })
  await check('exchange delivery explanation, ownership, refund and repeated cancel', async () => {
    const order = await api('/api/mall/exchange',at,{method:'POST',body:{goodsId}}); orderId=order.orderId
    await api(`/api/admin/orders/${orderId}/complete`,mt,{method:'PUT',body:{fulfillment_note:`凭订单到测试服务台领取 ${stamp}`}})
    const own = await api(`/api/order/${orderId}`,at); assert.equal(own.status,3); assert.match(own.fulfillment_note,/测试服务台/)
    const other = await (await raw(`/api/order/${orderId}`,bt)).json(); assert.notEqual(other.code,200)
    const before = (await api('/api/score/balance',bt)).balance
    const cancel = await api('/api/mall/exchange',bt,{method:'POST',body:{goodsId}})
    const refunded = await api(`/api/order/${cancel.orderId}/cancel`,bt,{method:'POST'}); assert.equal(refunded.score_balance,before)
    const again = await (await raw(`/api/order/${cancel.orderId}/cancel`,bt,{method:'POST'})).json(); assert.notEqual(again.code,200)
    assert.equal((await api('/api/score/balance',bt)).balance,before)
  })
  await check('stable course context, feedback reply and settings privacy', async () => {
    await api('/api/square',at,{method:'POST',body:{content:`课程关联验证 ${stamp}`,course_id:courseId,course_name:'client name must not win'}})
    const posts = await api(`/api/square?courseId=${courseId}`,at); assert.ok(posts.list.length); assert.ok(posts.list.every(p=>String(p.course_id)===String(courseId))); assert.notEqual(posts.list[0].course_name,'client name must not win')
    await api('/api/feedback',at,{method:'POST',body:{type:'功能建议',content:`体验反馈 ${stamp}`}})
    const mine = await api('/api/feedback/my',at), row = mine.find(x=>x.content===`体验反馈 ${stamp}`)
    await api(`/api/admin/feedback/${row.feedback_id}/reply`,mt,{method:'PUT',body:{reply:'已记录，本地体验回复'}})
    assert.equal((await api('/api/feedback/my',at)).find(x=>x.feedback_id===row.feedback_id).status,1)
    const original = await api('/api/contact/auth/my',at)
    try {
      await api('/api/contact/auth/update',at,{method:'PUT',body:{contact_visible:3,phone_visible:2,email_visible:2}})
      assert.equal((await api('/api/contact/auth/my',at)).contact_visible,3)
      const card = await api(`/api/contact/${a.user.user_id}`,bt); assert.ok(!card.phone && !card.email)
    } finally { await api('/api/contact/auth/update',at,{method:'PUT',body:original}) }
  })
  await check('private chat send, receive and unread readback', async () => {
    const convo = await api('/api/chat/conversations/private',at,{method:'POST',body:{target_user_id:b.user.user_id}}); conversationId=convo.conversation_id
    const text=`网页组合验证 ${stamp}🙂\n第二行`
    const sent = await api(`/api/chat/conversations/${conversationId}/messages`,at,{method:'POST',body:{client_message_id:randomUUID(),message_type:'TEXT',text}})
    const received=await api(`/api/chat/conversations/${conversationId}/messages`,bt); assert.ok(received.items.some(m=>m.content.text===text))
    const read=await api(`/api/chat/conversations/${conversationId}/read`,bt,{method:'PUT',body:{last_read_sequence:sent.sequence}}); assert.equal(read.unread_count,0)
  })
  }
  if (!process.argv.includes('--api-only')) {
  const browser = await chromium.launch({headless:true})
  try {
    const common = ['/home','/course',`/course/${courseId}`,'/square','/material',`/material/${materialId}`,'/contact',`/contact/${b.user.user_id}`,'/mall',`/mall/${goodsId}`,'/profile','/profile/courses','/profile/downloads','/profile/favorites','/profile/edit','/profile/settings','/profile/security','/profile/feedback','/profile/support','/profile/about','/profile/reviews','/messages',`/messages/${conversationId}`]
    const adminRoutes = ['dashboard','courses','materials','reviews','users','banners','square','orders','goods','reports'].map(p=>'/admin/'+p)
    for (const session of [a,teacher,admin]) {
      const ctx=await browser.newContext({viewport:{width:1440,height:1000}})
      await ctx.addInitScript(({accessToken,refreshToken,user})=>{localStorage.setItem('access_token',accessToken);localStorage.setItem('refresh_token',refreshToken);localStorage.setItem('user_info',JSON.stringify(user));localStorage.setItem('coursehub-theme','blue')},session)
      const page=await ctx.newPage(), errors=[]; page.on('pageerror',e=>errors.push(e.message))
      page.on('response',async response=>{
        if (!new URL(response.url()).pathname.startsWith('/api/')) return
        try { const result=await response.json(); if (result.code !== undefined && result.code !== 200) errors.push(`${new URL(response.url()).pathname}: ${result.code} ${result.message}`) } catch {}
      })
      const paths=[...common.filter(p=>!p.startsWith('/messages/') || session===a),...(session===admin?adminRoutes:['/profile/score','/profile/orders']),...(session===a?[`/profile/orders/${orderId}`]:[])]
      for (const path of paths) await check(`route role=${session.user.user_type} ${path}`,async()=>{
        const offset=errors.length
        await page.goto(base+path); await page.waitForLoadState('networkidle',{timeout:6000}).catch(()=>{})
        assert.equal(new URL(page.url()).pathname,path)
        assert.ok((await page.locator('body').innerText()).trim().length>30)
        assert.equal(errors.length,offset,errors.slice(offset).join('\n'))
        if (['/profile/settings','/profile','/admin/dashboard','/square'].includes(path)) await page.screenshot({path:resolve(output,`role${session.user.user_type}-${path.replaceAll('/','_')}.png`),fullPage:true})
      })
      await ctx.close()
    }
    const publicPage=await browser.newPage()
    for (const path of ['/login','/forgot-password']) await check('public '+path, async()=>{await publicPage.goto(base+path);assert.equal(new URL(publicPage.url()).pathname,path)})
  } finally { await browser.close() }
  }
} finally {
  await writeFile(resolve(output, routesOnly ? 'summary-routes.json' : 'summary-api.json'),JSON.stringify({run_id:config.run_id,at:new Date().toISOString(),results,fixtures:{materialId,orderId,conversationId,courseId,goodsId}},null,2))
}
