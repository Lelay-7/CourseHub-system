import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { preview } from 'vite'
import { chromium } from 'playwright'

// Production UI with bounded HTTP fixtures. Real HTTP/MySQL acceptance is a separate runner.
const output=resolve('output/playwright/private-chat-ui'); await mkdir(output,{recursive:true})
const server=await preview({preview:{host:'127.0.0.1',port:0,open:false}})
const errors=[]; let browser
const user={user_id:1,user_type:1,nickname:'同学甲'}
const token=`e30.${Buffer.from(JSON.stringify({sub:'1',exp:Math.floor(Date.now()/1000)+7200})).toString('base64url')}.fixture`
const base=server.resolvedUrls.local[0]
async function setup(theme,width,{readonly=false,empty=false,fail=false,role=1}={}) {
 const context=await browser.newContext({viewport:{width,height:900}})
 await context.addInitScript(({theme,token,user})=>{localStorage.setItem('coursehub-theme',theme);localStorage.setItem('access_token',token);localStorage.setItem('refresh_token',token);localStorage.setItem('user_info',JSON.stringify(user))},{theme,token,user:{...user,user_type:role}})
 const writes=[], reads=[], messages=empty?[]:Array.from({length:45},(_,i)=>({message_id:''+(i+1),conversation_id:'1',sequence:''+(i+1),sender_id:i%2?'1':'2',client_message_id:'server-'+i,message_type:'TEXT',content:{text:i===44?'https://example.test/'+ 'long'.repeat(100):'课程讨论 '+(i+1)+'\n这周一起复习'},created_at:'2026-10-11T10:00:00Z'}))
 const conversation=()=>({conversation_id:'1',conversation_type:'PRIVATE',peer:{user_id:'2',nickname:'同学乙',avatar_url:null,user_type:2,department:'计算机学院'},last_message:messages.at(-1)||null,last_sequence:''+messages.length,last_read_sequence:'0',unread_count:5,can_send:!readonly,send_disabled_reason:readonly?'NOT_FRIENDS':null})
 let lost=false
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;let data={}
  if(path==='/api/auth/userinfo') data=user
  if(path==='/api/chat/unread')data={unread_count:5}
  if(path==='/api/chat/conversations')data={items:empty?[]:[conversation()],next_cursor:null}
  if(path==='/api/chat/conversations/1')data=conversation()
  if(path==='/api/chat/conversations/1/read'){reads.push(req.postDataJSON());data={last_read_sequence:req.postDataJSON().last_read_sequence,unread_count:0}}
  if(path==='/api/chat/conversations/1/messages'){
   if(req.method()==='POST'){
    const body=req.postDataJSON();writes.push(body);data=messages.find(m=>m.client_message_id===body.client_message_id)
    if(!data){data={message_id:''+(messages.length+1),conversation_id:'1',sequence:''+(messages.length+1),sender_id:'1',client_message_id:body.client_message_id,message_type:'TEXT',content:{text:body.text},created_at:new Date().toISOString()};messages.push(data)}
    if(fail&&!lost){lost=true;await route.abort('failed');return}
   } else {
    const before=url.searchParams.get('before_sequence'),after=url.searchParams.get('after_sequence'),limit=Number(url.searchParams.get('limit')||30)
    const rows=messages.filter(m=>(before===null||BigInt(m.sequence)<BigInt(before))&&(after===null||BigInt(m.sequence)>BigInt(after)))
    const items=after===null?rows.slice(-limit):rows.slice(0,limit)
    data={items,has_more:rows.length>limit,next_before_sequence:items[0]?.sequence||null,next_after_sequence:items.at(-1)?.sequence||after||'0',latest_sequence:''+messages.length}
   }
  }
  await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,message:'ok',data})})
 })
 const page=await context.newPage();page.setDefaultTimeout(5000);page.on('pageerror',error=>errors.push(error.message))
 return {context,page,writes,reads,messages}
}
try {
 browser=await chromium.launch({headless:true})
 for(const theme of ['blue','wine','black'])for(const width of [1440,768,390,320]) {
  const {context,page}=await setup(theme,width)
  try{
   await page.goto(base+'messages/1');await page.getByRole('heading',{name:'同学乙',exact:true}).waitFor();await page.getByRole('textbox',{name:'消息内容'}).waitFor()
   assert.equal(await page.locator('html').getAttribute('data-theme'),theme)
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth)<=width+1,'chat must fit '+width)
   await page.screenshot({path:resolve(output,`${theme}-${width}.png`),fullPage:true})
  }finally{await context.close()}
 }
 const f=await setup('blue',1440,{fail:true})
 try{
  const {page,writes}=f;await page.goto(base+'messages/1');const input=page.getByRole('textbox',{name:'消息内容'});await input.fill('<img src=x onerror=alert(1)>')
  await input.dispatchEvent('compositionstart');await input.press('Enter');assert.equal(writes.length,0)
  await input.dispatchEvent('compositionend');await input.press('Shift+Enter');assert.equal(writes.length,0)
  await page.getByRole('button',{name:'发送',exact:true}).click();await page.getByRole('button',{name:'重试发送'}).click()
  await page.getByText('已发送',{exact:true}).last().waitFor();assert.equal(writes.length,2);assert.equal(writes[0].client_message_id,writes[1].client_message_id)
  assert.equal(await page.locator('.text-message img').count(),0);assert.equal(await page.getByText('<img src=x onerror=alert(1)>',{exact:true}).count(),1)
  const timeline=page.locator('.message-timeline');await timeline.evaluate(el=>el.scrollTop=100);await timeline.dispatchEvent('scroll')
  const readsBefore=f.reads.length
  f.messages.push({message_id:'47',conversation_id:'1',sequence:'47',sender_id:'2',client_message_id:'incoming',message_type:'TEXT',content:{text:'新到达消息'},created_at:new Date().toISOString()})
  await page.getByRole('button',{name:'刷新消息'}).click();await page.getByRole('button',{name:'有新消息，前往最新'}).waitFor();assert.equal(f.reads.length,readsBefore)
  await page.getByRole('button',{name:'加载更早消息'}).click()
  await page.getByText('课程讨论 1\n这周一起复习',{exact:true}).waitFor()
  await page.waitForFunction(()=>document.querySelector('.message-timeline').scrollTop>100)
  assert.ok(await timeline.evaluate(el=>el.scrollTop)>100,'history preserves scroll anchor')
  await page.getByRole('button',{name:'有新消息，前往最新'}).click()
 }finally{await f.page.screenshot({path:resolve(output,'interaction-final.png'),fullPage:true});await f.context.close()}
 const ro=await setup('wine',320,{readonly:true})
 try{await ro.page.goto(base+'messages/1');await ro.page.getByText('已解除好友，历史消息仍可查看',{exact:true}).waitFor();assert.equal(await ro.page.getByRole('textbox',{name:'消息内容'}).isDisabled(),true)}finally{await ro.context.close()}
 const empty=await setup('black',390,{empty:true})
 try{await empty.page.goto(base+'messages');await empty.page.getByText('还没有会话',{exact:true}).waitFor();await empty.page.goto(base+'messages/1');await empty.page.getByLabel('消息历史').getByText('发送第一条消息',{exact:true}).waitFor();await empty.page.setViewportSize({width:390,height:420});await empty.page.waitForFunction(()=>document.querySelector('.message-composer button').getBoundingClientRect().bottom<=window.innerHeight);assert.ok(await empty.page.getByRole('button',{name:'发送',exact:true}).isVisible())}finally{await empty.context.close()}
 const admin=await setup('blue',320,{role:3})
 try{await admin.page.goto(base+'messages/1');await admin.page.getByRole('heading',{name:'同学乙',exact:true}).waitFor();await admin.page.waitForFunction(()=>document.querySelector('.message-composer button').getBoundingClientRect().bottom<=window.innerHeight)}finally{await admin.page.screenshot({path:resolve(output,'admin-mobile.png'),fullPage:true});await admin.context.close()}
 assert.deepEqual(errors,[]);console.log('Private chat UI fixtures passed: 12 theme/viewport combinations, IME, text safety, retry, history anchor, readonly, empty and admin mobile viewport states')
}catch(error){console.error(error);throw error}finally{await browser?.close();await server.httpServer?.close()}
