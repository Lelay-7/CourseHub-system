import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'
import { previewRuntime } from './preview-runtime.mjs'

// Real production page, real local backend; no route interception or injected login state.
const config = JSON.parse(await readFile(resolve(previewRuntime, 'config.json'),'utf8'))
const base = `http://localhost:${config.web_port}`
const identity = await (await fetch(base+'/__preview')).json()
assert.equal(identity.nonce,config.nonce); assert.equal(identity.database,config.database)
const output = resolve('output/playwright/product-login')
await mkdir(output,{recursive:true})
const browser=await chromium.launch({headless:true}), results=[]
try {
  for (const account of config.accounts) {
    const context=await browser.newContext({viewport:{width:1440,height:1000}})
    const page=await context.newPage(), errors=[]
    page.on('pageerror', error=>errors.push(error.message))
    try {
      await page.goto(base+'/login')
      await page.getByPlaceholder('请输入登录账号').fill(account.username)
      await page.getByPlaceholder('请输入密码',{exact:true}).fill(account.password)
      const responsePromise=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/login'&&r.request().method()==='POST')
      await page.locator('.submit-btn').filter({hasText:'登录'}).click()
      const response=await responsePromise
      const status=response.status()
      assert.equal(status,200,`Browser form login returned HTTP ${status}`)
      assert.equal((await response.json()).code,200)
      await page.waitForURL(url=>url.pathname!=='/login')
      await page.waitForLoadState('networkidle')
      assert.deepEqual(errors,[])
      assert.ok(await page.evaluate(()=>!!localStorage.getItem('access_token')))
      await page.reload(); await page.waitForLoadState('networkidle')
      assert.notEqual(new URL(page.url()).pathname,'/login')
      await page.screenshot({path:resolve(output,account.username+'.png')})
      results.push({role:account.user_type,username:account.username,status:'pass',form_login:200,refresh_keeps_login:true})
      console.log('PASS browser form login and refresh: '+account.username)
    } finally { await context.close() }
  }
} finally {
  await browser.close()
  await writeFile(resolve(output,'results.json'),JSON.stringify({run_id:config.run_id,at:new Date().toISOString(),results},null,2))
}
