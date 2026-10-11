import assert from 'node:assert/strict'
import { mkdir,realpath,writeFile } from 'node:fs/promises'
import { resolve,dirname,basename,relative,isAbsolute } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { preview } from 'vite'
import { chromium } from 'playwright'

// Real Spring Boot + disposable MySQL. No API stubs, SMTP or external network.
const nonce=process.env.COURSEHUB_CHAT_E2E_NONCE
const api=process.env.COURSEHUB_CHAT_E2E_API
const coordinator=resolve(process.env.COURSEHUB_CHAT_E2E_COORDINATOR || '')
const output=resolve('output/playwright/private-chat-e2e',nonce || 'invalid')
const checks=[],errors=[],external=[];let server,browser,stage='setup',base
const contexts=[];const shutdown=new AbortController()
process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{if(text.includes('shutdown'))shutdown.abort()})
process.on('SIGTERM',()=>shutdown.abort());process.on('SIGINT',()=>shutdown.abort())
function check(name,condition=true){assert.ok(condition,name);checks.push({name,passed:true})}
async function call(pair,path,method='GET',body){
 const response=await fetch(api+path,{method,headers:{...(pair?{Authorization:'Bearer '+pair.access}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.any([shutdown.signal,AbortSignal.timeout(8000)])})
 const result=await response.json();return {status:response.status,...result}
}
async function pair(page){return page.evaluate(()=>({access:localStorage.getItem('access_token'),refresh:localStorage.getItem('refresh_token')}))}
async function session(username){
 const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block'});contexts.push(context)
 const page=await context.newPage();page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));page.on('request',request=>{const url=new URL(request.url());if(['http:','https:'].includes(url.protocol)&&url.origin!==new URL(base).origin)external.push(url.origin)})
 await page.goto(base+'login?redirect=%2Fmessages');await page.getByPlaceholder('请输入登录账号',{exact:true}).fill(username);await page.getByPlaceholder('请输入密码',{exact:true}).fill('Chat-old-123');await page.locator('form.auth-form').getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/messages')
 return {context,page,pair:await pair(page)}
}
async function waitFor(fn){const end=Date.now()+12000;for(;;){shutdown.signal.throwIfAborted();if(await fn())return;if(Date.now()>end)throw new Error('Timed out at '+stage);await delay(100,undefined,{signal:shutdown.signal})}}
try{
 assert.match(nonce || '',/^[a-f0-9]{32}$/);const url=new URL(api);assert.equal(url.hostname,'127.0.0.1');assert.equal(url.protocol,'http:');assert.ok(Number(url.port)>=1024)
 const parent=await realpath(dirname(coordinator)),target=await realpath(resolve('../backend/target')),rel=relative(target,parent);assert.equal(basename(coordinator),'e2e-coordinator.json');assert.ok(rel&&!isAbsolute(rel)&&!rel.startsWith('..'));assert.equal(basename(parent),'coursehub-chat-mysql-'+new URL(process.env.COURSEHUB_CHAT_MYSQL_URL.slice(5)).pathname.slice('/coursehub_chat_check_'.length))
 await mkdir(output,{recursive:true})
 server=await preview({logLevel:'silent',preview:{host:'127.0.0.1',port:0,open:false,proxy:{'/api':{target:api,changeOrigin:false}}}});base=server.resolvedUrls.local[0]
 assert.equal(server.config.preview.proxy['/api'].target,api);await writeFile(coordinator,JSON.stringify({nonce})+'\n',{encoding:'utf8',flag:'wx'})
 stage='backend-readiness';const deadline=Date.now()+60000;let ready=false
 while(Date.now()<deadline){try{const response=await fetch(api+'/api/auth/userinfo',{signal:AbortSignal.any([shutdown.signal,AbortSignal.timeout(1000)])});ready=response.status===401;await response.arrayBuffer();if(ready)break}catch{shutdown.signal.throwIfAborted()}await delay(200,undefined,{signal:shutdown.signal})}assert.ok(ready)
 browser=await chromium.launch({headless:true});stage='two-logins';const a=await session('chat_a'),b=await session('chat_b');check('two independent browser accounts logged in through production authentication')
 const aInfo=await call(a.pair,'/api/auth/userinfo'),bInfo=await call(b.pair,'/api/auth/userinfo');const aId=aInfo.data.user_id,bId=bInfo.data.user_id
 stage='friend-application';await a.page.goto(base+'contact');await a.page.getByPlaceholder('搜索昵称、姓名、账号/学号、院系...').fill('chat_b');await a.page.getByRole('button',{name:'添加好友',exact:true}).click();await b.page.goto(base+'contact?tab=pending');await b.page.getByRole('button',{name:'同意',exact:true}).click()
 await a.page.goto(base+'contact?tab=friends');await a.page.getByRole('button',{name:'发消息',exact:true}).click();await a.page.waitForURL('**/messages/*');const id=new URL(a.page.url()).pathname.split('/').at(-1)
 await b.page.goto(base+'contact?tab=friends');await b.page.getByRole('button',{name:'发消息',exact:true}).click();await b.page.waitForURL('**/messages/'+id);check('friend UI creates and reuses the same private conversation')
 await b.page.goto(base+'messages');stage='send-and-unread';const content='<img src=x onerror=alert(1)> 真实私聊 😀'
 await a.page.getByRole('textbox',{name:'消息内容'}).fill(content);await a.page.getByRole('button',{name:'发送',exact:true}).click();await a.page.getByText('已发送',{exact:true}).waitFor()
 await waitFor(async()=> (await call(b.pair,'/api/chat/unread')).data.unread_count===1)
 await b.page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await b.page.getByRole('button',{name:'与同学甲的会话'}).waitFor();check('incoming unread is computed by the database')
 await b.page.goto(base+'messages/'+id);await b.page.getByText(content,{exact:true}).waitFor();assert.equal(await b.page.locator('.text-message img').count(),0)
 await waitFor(async()=> (await call(b.pair,'/api/chat/unread')).data.unread_count===0);check('real receive renders literal text and advances read cursor at the visible bottom')
 stage='history';for(let i=0;i<35;i++){const response=await call(a.pair,'/api/chat/conversations/'+id+'/messages','POST',{client_message_id:crypto.randomUUID(),message_type:'TEXT',text:'历史消息 '+i});assert.equal(response.status,200)}
 await b.page.reload();await b.page.getByRole('button',{name:'加载更早消息'}).click();await b.page.getByText(content,{exact:true}).waitFor();check('history cursor loads all messages across the default 30-row boundary')
 stage='admin-nonmember';const admin=await call(null,'/api/auth/login','POST',{username:'chat_admin',password:'Chat-old-123'});const adminPair={access:admin.data.accessToken};const hidden=await call(adminPair,'/api/chat/conversations/'+id+'/messages');assert.equal(hidden.status,404);assert.equal(hidden.code,404);check('administrator receives uniform nonmember 404')
 stage='unfriend';const friends=await call(a.pair,'/api/contact/my-friends');const friend=friends.data.find(row=>String(row.user_id)===String(bId));assert.ok(friend?.friend_id);await call(a.pair,'/api/contact/remove/'+friend.friend_id,'DELETE');await a.page.getByRole('button',{name:'刷新消息'}).click();await a.page.getByText('已解除好友，历史消息仍可查看',{exact:true}).waitFor();assert.equal(await a.page.getByRole('textbox',{name:'消息内容'}).isDisabled(),true)
 const denied=await call(a.pair,'/api/chat/conversations/'+id+'/messages','POST',{client_message_id:crypto.randomUUID(),message_type:'TEXT',text:'blocked'});assert.equal(denied.status,403);assert.equal((await call(b.pair,'/api/chat/conversations/'+id+'/messages')).status,200);check('unfriend blocks writes while retaining member history')
 await call(a.pair,'/api/contact/add/'+bId,'POST');const pending=await call(b.pair,'/api/contact/pending');await call(b.pair,'/api/contact/accept/'+pending.data.find(row=>String(row.user_id)===String(aId)).friend_id,'POST');const reused=await call(a.pair,'/api/chat/conversations/private','POST',{target_user_id:bId});assert.equal(reused.data.conversation_id,id);await a.page.getByRole('button',{name:'刷新消息'}).click();check('refriending restores the original conversation')
 stage='themes-and-widths';for(const theme of ['blue','wine','black'])for(const width of [1440,390,320]){await a.page.evaluate(theme=>localStorage.setItem('coursehub-theme',theme),theme);await a.page.setViewportSize({width,height:900});await a.page.reload();await a.page.getByRole('heading',{name:'同学乙',exact:true}).waitFor();assert.ok(await a.page.evaluate(()=>document.documentElement.scrollWidth)<=width+1);await a.page.screenshot({path:resolve(output,theme+'-'+width+'.png'),fullPage:true})}check('three themes fit desktop and both mobile widths on real HTTP data')
 stage='password-change';const old=await session('chat_a');await old.page.goto(base+'messages/'+id);await old.page.getByRole('textbox',{name:'消息内容'}).fill('旧会话草稿')
 await a.page.goto(base+'profile/security');const form=a.page.locator('.security-panels form').nth(1);await form.getByLabel('原密码',{exact:true}).fill('Chat-old-123');await form.getByLabel('新密码',{exact:true}).fill('Chat-new-456');await form.getByLabel('确认新密码',{exact:true}).fill('Chat-new-456');await form.getByRole('button',{name:'修改密码并重新登录',exact:true}).click();await a.page.waitForURL('**/login')
 assert.equal((await call(old.pair,'/api/chat/conversations/'+id+'/messages')).status,401);assert.equal((await call(old.pair,'/api/chat/conversations/'+id+'/messages','POST',{client_message_id:crypto.randomUUID(),message_type:'TEXT',text:'stale'})).status,401)
 const rejected=await call(null,'/api/auth/refresh','POST',{refreshToken:old.pair.refresh});assert.equal(rejected.code,401);check('password change invalidates old access for read/write and old refresh credentials')
 await old.page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));await old.page.waitForURL('**/login');assert.equal(await old.page.evaluate(()=>localStorage.getItem('access_token')),null)
 const cleared=await old.page.evaluate(()=>{const chat=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('chat');return Object.keys(chat.state.messagesByConversation).length===0&&Object.keys(chat.state.draftsByConversation).length===0&&chat.state.unreadCount===0});assert.equal(cleared,true);check('401 synchronously clears private messages drafts unread and polling session')
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);check('no browser runtime errors or external requests')
 await writeFile(resolve(output,'summary.json'),JSON.stringify({nonce,passed:true,checks},null,2),'utf8');console.log('Private chat real browser acceptance passed: '+checks.length+' checks')
}catch(error){await mkdir(output,{recursive:true});for(let i=0;i<contexts.length;i++){for(const page of contexts[i].pages())try{await page.screenshot({path:resolve(output,'failure-'+i+'.png'),fullPage:true})}catch{}}await writeFile(resolve(output,'summary.json'),JSON.stringify({nonce,passed:false,stage,checks,error:error.message},null,2),'utf8');console.error('Private chat E2E failed at '+stage+': '+error.message);process.exitCode=1}
finally{for(const context of contexts)await context.close();await browser?.close();if(server)await new Promise(resolve=>server.httpServer.close(resolve));process.stdin.destroy()}
