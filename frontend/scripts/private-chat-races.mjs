import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { preview } from 'vite'
import { chromium } from 'playwright'
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
let browser,server,base
before(async()=>{await mkdir('output/playwright/private-chat-races',{recursive:true});server=await preview({preview:{host:'127.0.0.1',port:0,open:false}});base=server.resolvedUrls.local[0];browser=await chromium.launch({headless:true})})
after(async()=>{await browser?.close();if(server?.httpServer)await new Promise(r=>server.httpServer.close(r))})
async function fixture({failSend=false,delayHistory=false}={}) {
  const context=await browser.newContext({viewport:{width:1440,height:900}}),historyRequested=deferred(),historyRelease=deferred(),reads=[],errors=[]
  const messages=Array.from({length:45},(_,i)=>({message_id:''+(i+1),sequence:''+(i+1),conversation_id:'1',sender_id:'2',client_message_id:'server-'+i,message_type:'TEXT',content:{text:'历史消息 '+(i+1)+'\n复习笔记'},created_at:'2026-10-11T10:00:00Z'}))
  const token=`e30.${Buffer.from(JSON.stringify({sub:'1',exp:Math.floor(Date.now()/1000)+7200})).toString('base64url')}.fixture`
  await context.addInitScript(token=>{localStorage.setItem('access_token',token);localStorage.setItem('refresh_token',token);localStorage.setItem('user_info',JSON.stringify({user_id:1,user_type:1,nickname:'甲'}))},token)
  const conversation=()=>({conversation_id:'1',conversation_type:'PRIVATE',peer:{user_id:'2',nickname:'乙'},last_message:messages.at(-1),last_sequence:messages.at(-1).sequence,last_read_sequence:reads.at(-1)?.last_read_sequence||'0',unread_count:0,can_send:true})
  await context.route('**/api/**',async route=>{
    const req=route.request(),url=new URL(req.url()),path=url.pathname;let data={}
    if(path==='/api/auth/userinfo')data={user_id:1,user_type:1,nickname:'甲'}
    if(path==='/api/chat/unread')data={unread_count:0}
    if(path==='/api/chat/conversations')data={items:[conversation()],next_cursor:null}
    if(path==='/api/chat/conversations/1')data=conversation()
    if(path.endsWith('/read')){reads.push(req.postDataJSON());data={last_read_sequence:req.postDataJSON().last_read_sequence,unread_count:0}}
    if(path==='/api/chat/conversations/1/messages'){
      if(req.method()==='POST'&&failSend){await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,message:'temporary send failure'})});return}
      const before=url.searchParams.get('before_sequence'),after=url.searchParams.get('after_sequence')
      if(before&&delayHistory){historyRequested.resolve();await historyRelease.promise}
      const rows=messages.filter(m=>(!before||BigInt(m.sequence)<BigInt(before))&&(!after||BigInt(m.sequence)>BigInt(after)))
      const items=after?rows.slice(0,30):rows.slice(-30)
      data={items,has_more:rows.length>30,next_before_sequence:items[0]?.sequence||null,next_after_sequence:items.at(-1)?.sequence||after||'0',latest_sequence:messages.at(-1).sequence}
    }
    await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})}).catch(()=>{})
  })
  const page=await context.newPage();page.setDefaultTimeout(5000);page.on('pageerror',e=>errors.push(e.message))
  const initialRead=page.waitForResponse(r=>r.url().endsWith('/api/chat/conversations/1/read'))
  await page.goto(base+'messages/1');await initialRead;await page.getByRole('textbox',{name:'消息内容'}).waitFor()
  const addIncoming=()=>messages.push({...messages.at(-1),message_id:'46',sequence:'46',client_message_id:'incoming-46',content:{text:'新到达消息\n'+('新增笔记\n'.repeat(12))}})
  return {context,page,messages,reads,errors,historyRequested,historyRelease,addIncoming}
}
async function older(page){await page.locator('.message-timeline').evaluate(el=>{el.scrollTop=100;el.dispatchEvent(new Event('scroll'))})}
async function anchor(page){return page.locator('.message-timeline').evaluate(el=>{const rect=el.getBoundingClientRect();const row=[...el.querySelectorAll('li[data-message-id]')].find(row=>row.getBoundingClientRect().bottom>rect.top&&row.getBoundingClientRect().top<rect.bottom);return {id:row.dataset.messageId,offset:row.getBoundingClientRect().top-rect.top}})}
test('a retained failed bubble does not scroll old history or acknowledge incoming messages',{timeout:20000},async()=>{
  const f=await fixture({failSend:true})
  try{
    await f.page.getByRole('textbox',{name:'消息内容'}).fill('未提交的消息');await f.page.getByRole('button',{name:'发送',exact:true}).click();await f.page.getByRole('button',{name:'重试发送'}).waitFor()
    await older(f.page);const before=f.reads.length;f.addIncoming();await f.page.getByRole('button',{name:'刷新消息'}).click();await f.page.getByLabel('消息历史').getByText(/^新到达消息/).waitFor()
    assert.equal(await f.page.locator('.message-timeline').evaluate(el=>el.scrollTop),100,'an old failed bubble must not force scroll')
    assert.equal(f.reads.length,before);assert.equal(await f.page.getByRole('button',{name:'有新消息，前往最新'}).isVisible(),true);assert.deepEqual(f.errors,[])
  }finally{await f.page.screenshot({path:resolve('output/playwright/private-chat-races/failed-incoming.png')});await f.context.close()}
})
test('incoming messages during a history request preserve the visible message anchor',{timeout:20000},async()=>{
  const f=await fixture({delayHistory:true})
  try{
    await older(f.page);await f.page.getByRole('button',{name:'加载更早消息'}).evaluate(el=>el.click());await f.historyRequested.promise
    const before=await anchor(f.page);f.addIncoming();await f.page.getByRole('button',{name:'刷新消息'}).click();await f.page.getByLabel('消息历史').getByText(/^新到达消息/).waitFor()
    f.historyRelease.resolve();await f.page.getByText('历史消息 1\n复习笔记',{exact:true}).waitFor();await f.page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
    const offset=await f.page.locator(`li[data-message-id="${before.id}"]`).evaluate(el=>el.getBoundingClientRect().top-el.closest('.message-timeline').getBoundingClientRect().top)
    assert.ok(Math.abs(offset-before.offset)<2,`history anchor moved by ${offset-before.offset}px`)
    assert.equal(await f.page.getByRole('button',{name:'有新消息，前往最新'}).isVisible(),true);assert.deepEqual(f.errors,[])
  }finally{f.historyRelease.resolve();await f.page.screenshot({path:resolve('output/playwright/private-chat-races/history-incoming.png')});await f.context.close()}
})
