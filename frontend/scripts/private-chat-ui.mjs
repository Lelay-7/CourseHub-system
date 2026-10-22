import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { preview } from 'vite'
import { chromium } from 'playwright'
import { THEMES } from '../src/utils/theme.js'

// Production UI with bounded HTTP fixtures. Real HTTP/MySQL acceptance is a separate runner.
const output=resolve('output/playwright/private-chat-ui'); await mkdir(output,{recursive:true})
const server=await preview({preview:{host:'127.0.0.1',port:0,open:false}})
const errors=[]; let browser
const user={user_id:1,user_type:1,nickname:'同学甲'}
const token=`e30.${Buffer.from(JSON.stringify({sub:'1',exp:Math.floor(Date.now()/1000)+7200})).toString('base64url')}.fixture`
const base=server.resolvedUrls.local[0]
async function setup(theme,width,{readonly=false,empty=false,fail=false,role=1,height=900}={}) {
 const context=await browser.newContext({viewport:{width,height}})
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
 for(const { id: theme } of THEMES)for(const width of [1440,768,390,320]) {
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
 const landscape=await setup('black',390)
 try{
  const {page}=landscape
  await page.goto(base+'messages/1')
  await page.getByRole('textbox',{name:'消息内容'}).fill('旋转后保留的草稿🙂')
  await page.setViewportSize({width:900,height:240})
  await page.waitForFunction(()=>document.querySelector('.message-page').style.getPropertyValue('--chat-viewport-height')===window.visualViewport.height+'px')
  const geometry=await page.evaluate(()=>{
   const rect=selector=>{const r=document.querySelector(selector).getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height}}
   return {heading:rect('.chat-heading'),input:rect('#chat-message'),send:rect('.message-composer button'),timeline:rect('.message-timeline'),height:window.innerHeight}
  })
  assert.ok(geometry.heading.top>=0&&geometry.heading.bottom<=geometry.height,'landscape conversation heading stays visible: '+JSON.stringify(geometry))
  assert.ok(geometry.input.top>=0&&geometry.send.bottom<=geometry.height,'landscape input and send stay inside the viewport: '+JSON.stringify(geometry))
  assert.ok(geometry.timeline.height>=40,'landscape leaves room for messages')
  assert.equal(await page.locator('.chat-list').isVisible(),false,'short landscape keeps the conversation in a single column')
  assert.ok(await page.getByRole('button',{name:'返回会话列表'}).isVisible(),'landscape keeps a reachable back button')
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth)<=900,'landscape has no horizontal overflow')
  assert.equal(await page.getByRole('textbox',{name:'消息内容'}).inputValue(),'旋转后保留的草稿🙂')
  assert.equal(landscape.writes.length,0,'rotation must not submit the draft')
  await page.screenshot({path:resolve(output,'short-landscape.png')})
  await page.setViewportSize({width:390,height:900})
  await page.waitForFunction(()=>Math.abs(document.querySelector('.chat-frame').getBoundingClientRect().bottom-window.innerHeight)<=1)
  assert.ok(await page.locator('.app-navbar').isVisible(),'portrait restores the app navbar')
  assert.equal(await page.getByRole('textbox',{name:'消息内容'}).inputValue(),'旋转后保留的草稿🙂')
 }finally{await landscape.context.close()}
 const minimal=await setup('navy',800,{height:218})
 try{
  await minimal.context.addInitScript(()=>{
   const viewport=new EventTarget()
   Object.assign(viewport,{height:window.innerHeight,offsetTop:0})
   Object.defineProperty(window,'visualViewport',{value:viewport,configurable:true})
   window.setKeyboardViewport=(height,offsetTop)=>{Object.assign(viewport,{height,offsetTop});viewport.dispatchEvent(new Event('resize'));viewport.dispatchEvent(new Event('scroll'))}
  })
  const {page}=minimal
  await page.goto(base+'messages/1')
  await page.getByRole('textbox',{name:'消息内容'}).fill('横屏键盘草稿🙂')
  for(const offsetTop of [0,150]){
   await page.evaluate(offset=>window.setKeyboardViewport(47,offset),offsetTop)
   await page.waitForFunction(()=>document.querySelector('.chat-frame').getBoundingClientRect().height<=47)
   const geometry=await page.evaluate(()=>{
    const rect=selector=>{const r=document.querySelector(selector).getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height}}
    return {input:rect('#chat-message'),send:rect('.message-composer button'),top:visualViewport.offsetTop,bottom:visualViewport.offsetTop+visualViewport.height}
   })
   assert.ok(geometry.input.top>=geometry.top&&geometry.input.bottom<=geometry.bottom,'47px viewport keeps the entire input visible: '+JSON.stringify(geometry))
   assert.ok(geometry.send.top>=geometry.top&&geometry.send.bottom<=geometry.bottom,'47px viewport keeps the entire Send button visible: '+JSON.stringify(geometry))
   assert.ok(geometry.send.height>=44,'Send retains a 44px touch target')
   assert.equal(await page.getByRole('textbox',{name:'消息内容'}).inputValue(),'横屏键盘草稿🙂')
   assert.equal(minimal.writes.length,0,'keyboard opening and panning must not submit the draft')
  }
  await page.screenshot({path:resolve(output,'minimal-landscape-keyboard.png'),clip:{x:0,y:150,width:800,height:47}})
  const readsBefore=minimal.reads.length
  minimal.messages.push({message_id:'46',conversation_id:'1',sequence:'46',sender_id:'2',client_message_id:'minimal-incoming',message_type:'TEXT',content:{text:'极小视口下尚未显示的新消息'},created_at:new Date().toISOString()})
  await page.locator('[data-message-id="46"]').waitFor({state:'attached',timeout:8000})
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
  assert.equal(minimal.reads.length,readsBefore,'messages hidden by the tiny keyboard viewport must not be marked read')
  await page.evaluate(()=>window.setKeyboardViewport(218,0))
  await page.waitForFunction(()=>document.querySelector('.chat-heading').getBoundingClientRect().height>=44)
  assert.ok(await page.getByRole('heading',{name:'同学乙',exact:true}).isVisible(),'closing the keyboard restores conversation context')
  assert.ok(await page.locator('.message-timeline').evaluate(el=>el.getBoundingClientRect().height)>=40,'closing the keyboard restores message history')
  await page.waitForFunction(()=>document.querySelector('.message-timeline').scrollHeight-document.querySelector('.message-timeline').scrollTop-document.querySelector('.message-timeline').clientHeight<1)
  assert.equal(await page.getByRole('textbox',{name:'消息内容'}).inputValue(),'横屏键盘草稿🙂')
  await page.setViewportSize({width:800,height:900})
  await page.evaluate(()=>window.setKeyboardViewport(47,150))
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
  const tallLayout=await page.evaluate(()=>{
   const input=document.querySelector('#chat-message').getBoundingClientRect(),send=document.querySelector('.message-composer button').getBoundingClientRect()
   return {inputTop:input.top,inputBottom:input.bottom,sendTop:send.top,sendBottom:send.bottom,top:visualViewport.offsetTop,bottom:visualViewport.offsetTop+visualViewport.height}
  })
  assert.ok(tallLayout.inputTop>=tallLayout.top&&tallLayout.inputBottom<=tallLayout.bottom&&tallLayout.sendTop>=tallLayout.top&&tallLayout.sendBottom<=tallLayout.bottom,'tiny visual viewport fits the editor even when the layout viewport remains tall: '+JSON.stringify(tallLayout))
  await page.evaluate(()=>window.setKeyboardViewport(900,0))
  await page.getByRole('heading',{name:'同学乙',exact:true}).waitFor()
  assert.ok(await page.locator('.chat-list').isVisible(),'large layout returns to desktop columns after keyboard closes')
 }finally{await minimal.context.close()}
 const keyboard=await setup('sage',390)
 try{
  await keyboard.context.addInitScript(()=>{
   const viewport=new EventTarget()
   Object.assign(viewport,{height:window.innerHeight,offsetTop:0})
   Object.defineProperty(window,'visualViewport',{value:viewport,configurable:true})
   window.setKeyboardViewport=(height,offsetTop)=>{Object.assign(viewport,{height,offsetTop});viewport.dispatchEvent(new Event('resize'));viewport.dispatchEvent(new Event('scroll'))}
  })
  const page=keyboard.page
  await page.goto(base+'messages/1')
  await page.getByRole('textbox',{name:'消息内容'}).fill('手机键盘验收草稿')
  await page.evaluate(()=>{document.body.style.minHeight='1800px';window.scrollTo(0,320);window.setKeyboardViewport(400,180)})
  await page.waitForFunction(()=>document.querySelector('.chat-frame').getBoundingClientRect().height<500)
  const geometry=await page.evaluate(()=>{
   const rect=selector=>{const r=document.querySelector(selector).getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height}}
   return {frame:rect('.chat-frame'),heading:rect('.chat-heading'),input:rect('#chat-message'),send:rect('.message-composer button'),timeline:rect('.message-timeline'),top:visualViewport.offsetTop,bottom:visualViewport.offsetTop+visualViewport.height}
  })
  assert.ok(Math.abs(geometry.frame.bottom-geometry.bottom)<=1,'keyboard pan must not leave a gap below the composer: '+JSON.stringify(geometry))
  assert.ok(geometry.heading.top>=geometry.top&&geometry.heading.bottom<=geometry.bottom,'conversation heading stays inside the visible viewport')
  assert.ok(geometry.input.top>=geometry.top&&geometry.send.bottom<=geometry.bottom,'input and send remain visible above the keyboard')
  assert.ok(geometry.bottom-geometry.send.bottom<=24,'send stays near the keyboard edge')
  assert.ok(geometry.timeline.height>=40,'keyboard leaves space for conversation messages')
  await page.waitForFunction(()=>{const el=document.querySelector('.message-timeline');return el.scrollHeight-el.scrollTop-el.clientHeight<=1})
  assert.equal(await page.getByRole('textbox',{name:'消息内容'}).inputValue(),'手机键盘验收草稿')
  assert.equal(keyboard.writes.length,0,'viewport movement must not submit the draft')
  await page.screenshot({path:resolve(output,'keyboard-pan.png'),clip:{x:0,y:180,width:390,height:400}})
  await page.evaluate(()=>{document.body.style.minHeight='';window.setKeyboardViewport(window.innerHeight,0)})
  await page.waitForFunction(()=>Math.abs(document.querySelector('.chat-frame').getBoundingClientRect().bottom-window.innerHeight)<=1)
  await page.getByRole('button',{name:'返回会话列表'}).click()
  await page.waitForURL('**/messages')
  assert.notEqual(await page.locator('.chat-frame').evaluate(el=>getComputedStyle(el).position),'fixed','conversation list returns to normal page flow')
 }finally{await keyboard.context.close()}
 const admin=await setup('blue',320,{role:3})
 try{await admin.page.goto(base+'messages/1');await admin.page.getByRole('heading',{name:'同学乙',exact:true}).waitFor();await admin.page.waitForFunction(()=>document.querySelector('.message-composer button').getBoundingClientRect().bottom<=window.innerHeight)}finally{await admin.page.screenshot({path:resolve(output,'admin-mobile.png'),fullPage:true});await admin.context.close()}
 assert.deepEqual(errors,[]);console.log(`Private chat UI fixtures passed: ${THEMES.length * 4} theme/viewport combinations, IME, text safety, retry, history anchor, readonly, empty, short landscape/portrait restore, 47px keyboard and hidden-history unread protection, keyboard pan/restore and admin mobile viewport states`)
}catch(error){console.error(error);throw error}finally{await browser?.close();await server.httpServer?.close()}
