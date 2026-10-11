import test from 'node:test'
import assert from 'node:assert/strict'
import { createChatState, createChatSession, normalizeChatText } from '../src/utils/chat-state.js'
import { createChatPoller } from '../src/utils/chat-polling.js'
const deferred = () => { let resolve, reject; const promise = new Promise((a,b)=>{resolve=a;reject=b}); return {promise,resolve,reject} }
const message = (sequence,client='server-'+sequence) => ({message_id:sequence,conversation_id:'1',sequence,sender_id:'2',client_message_id:client,message_type:'TEXT',content:{text:'text '+sequence},created_at:'2026-10-11T10:00:00Z'})
function fixture(overrides={}) {
  const state=createChatState(); let identity='session-a'; const calls=[]
  const api={ getConversation:async id=>({conversation_id:id,peer:{user_id:'2',nickname:'乙'},can_send:true,unread_count:0,last_sequence:'0'}),
    getMessages:async()=>({items:[],has_more:false,next_before_sequence:null,next_after_sequence:'0',latest_sequence:'0'}),
    sendMessage:async(id,data)=>{calls.push({id,data});return {...message('1',data.client_message_id),sender_id:'1',content:{text:data.text}}},
    markRead:async(id,seq)=>({last_read_sequence:seq,unread_count:0}), listConversations:async()=>({items:[],next_cursor:null}),getUnread:async()=>({unread_count:0}),...overrides }
  const chat=createChatSession({state,api,getSessionId:()=>identity,newClientMessageId:()=> '11111111-1111-4111-8111-111111111111'})
  return {state,api,chat,calls,setIdentity:value=>identity=value}
}
test('lost response retries the original UUID and leaves one persisted bubble',async()=>{
  let n=0;const f=fixture(); const send=f.api.sendMessage
  f.api.sendMessage=async(...args)=>{const result=await send(...args); if(!n++) throw new Error('response lost'); return result}
  await f.chat.selectConversation('1'); f.state.draftsByConversation['1']='hello'
  const failed=await f.chat.sendText('hello'); assert.equal(failed?.delivery_state,'FAILED')
  await f.chat.retrySend(f.calls[0].data.client_message_id)
  assert.equal(f.calls[1].data.client_message_id,f.calls[0].data.client_message_id)
  assert.equal(f.state.messagesByConversation['1'].length,1)
  assert.equal(f.state.messagesByConversation['1'][0].delivery_state,'SENT')
})
test('incremental pages advance by actual last sequence above Number precision',async()=>{
  const after=[]
  const f=fixture({getMessages:async(id,params)=>{
    if(params.after_sequence===undefined) return {items:[message('9007199254740993')],has_more:false,next_after_sequence:'9007199254740993'}
    after.push(params.after_sequence)
    return params.after_sequence==='9007199254740993'
      ? {items:[message('9007199254740994')],has_more:true,next_after_sequence:'9007199254740994',latest_sequence:'9007199254740996'}
      : {items:[message('9007199254740995'),message('9007199254740996')],has_more:false,next_after_sequence:'9007199254740996'}
  }})
  await f.chat.selectConversation('1'); await f.chat.pullNew()
  assert.deepEqual(after,['9007199254740993','9007199254740994'])
  assert.deepEqual(f.state.messagesByConversation['1'].map(m=>m.sequence),['9007199254740993','9007199254740994','9007199254740995','9007199254740996'])
})
test('late detail response cannot select a previous conversation or write after reset',async()=>{
  const slow=deferred(); const f=fixture({getConversation:id=>id==='1'?slow.promise:Promise.resolve({conversation_id:id,can_send:true,peer:{user_id:'3'}})})
  const old=f.chat.selectConversation('1'); await f.chat.selectConversation('2')
  slow.resolve({conversation_id:'1',can_send:true}); await old; assert.equal(f.state.selectedId,'2')
  f.state.draftsByConversation['2']='draft'; f.chat.reset()
  assert.deepEqual(f.state.messagesByConversation,{}); assert.deepEqual(f.state.draftsByConversation,{})
  const unread=deferred(); f.api.getUnread=()=>unread.promise; const refresh=f.chat.refreshUnread(); f.setIdentity('session-b')
  unread.resolve({unread_count:99}); await refresh; assert.equal(f.state.unreadCount,0)
})
test('history merging deduplicates and only visible bottom marks loaded sequence',async()=>{
  const marked=[];const f=fixture({getMessages:async(id,p)=>p.before_sequence?{items:[message('1'),message('2')],has_more:false,next_before_sequence:'1'}:{items:[message('2'),message('3')],has_more:true,next_before_sequence:'2',next_after_sequence:'3'},markRead:async(id,seq)=>{marked.push(seq);return {unread_count:0,last_read_sequence:seq}}})
  await f.chat.selectConversation('1'); await f.chat.loadPrevious()
  assert.deepEqual(f.state.messagesByConversation['1'].map(m=>m.sequence),['1','2','3'])
  await f.chat.markVisibleRead({visible:false,atBottom:true});await f.chat.markVisibleRead({visible:true,atBottom:false});assert.deepEqual(marked,[])
  await f.chat.markVisibleRead({visible:true,atBottom:true}); assert.deepEqual(marked,['3'])
})

test('a send acknowledgement cannot mark unfetched intervening messages read',async()=>{
  const marked=[];const f=fixture({
    getMessages:async(id,p)=>p.after_sequence?{items:[message('2'),message('3'),message('4','11111111-1111-4111-8111-111111111111')],has_more:false,next_after_sequence:'4'}:{items:[message('1')],has_more:false,next_after_sequence:'1'},
    sendMessage:async(id,data)=>({...message('4',data.client_message_id),sender_id:'1'}),
    markRead:async(id,seq)=>{marked.push(seq);return {last_read_sequence:seq,unread_count:seq==='1'?2:0}}
  })
  await f.chat.selectConversation('1');await f.chat.sendText('outgoing')
  assert.deepEqual(f.state.messagesByConversation['1'].map(m=>m.sequence),['1','4'])
  await f.chat.markVisibleRead({visible:true,atBottom:true});assert.deepEqual(marked,['1'])
  await f.chat.pullNew();await f.chat.markVisibleRead({visible:true,atBottom:true});assert.deepEqual(marked,['1','4'])
})

for(const refresh of ['list','detail']) test(`read completion updates the current conversation after a ${refresh} refresh`,async()=>{
  const read=deferred()
  const row={conversation_id:'1',peer:{user_id:'2',nickname:'乙'},can_send:true,last_sequence:'3',last_read_sequence:'0',unread_count:3}
  const f=fixture({
    getConversation:async()=>({...row}),listConversations:async()=>({items:[{...row}],next_cursor:null}),
    getMessages:async()=>({items:[message('1'),message('2'),message('3')],has_more:false,next_after_sequence:'3'}),
    markRead:()=>read.promise
  })
  await f.chat.selectConversation('1');const old=f.state.conversations[0]
  const marking=f.chat.markVisibleRead({visible:true,atBottom:true})
  if(refresh==='list')await f.chat.refreshConversations();else await f.chat.pullNew()
  assert.notEqual(f.state.conversations[0],old)
  read.resolve({last_read_sequence:'3',unread_count:0});await marking
  assert.equal(f.state.conversations[0].last_read_sequence,'3')
  assert.equal(f.state.conversations[0].unread_count,0)
})

test('an older read completion cannot overwrite a newer refreshed read cursor',async()=>{
  const read=deferred();let cursor='0'
  const f=fixture({
    getConversation:async()=>({conversation_id:'1',can_send:true,last_read_sequence:cursor,unread_count:cursor==='0'?3:1}),
    getMessages:async()=>({items:[message('1'),message('2'),message('3')],has_more:false,next_after_sequence:'3'}),
    markRead:()=>read.promise
  })
  await f.chat.selectConversation('1');const marking=f.chat.markVisibleRead({visible:true,atBottom:true})
  cursor='4';await f.chat.pullNew();read.resolve({last_read_sequence:'3',unread_count:2});await marking
  assert.equal(f.state.conversations[0].last_read_sequence,'4')
  assert.equal(f.state.conversations[0].unread_count,1)
})

test('cancelled history cannot keep pagination busy after failed reselection and recovery',async()=>{
  const old=deferred();let beforeCalls=0,hold=true,failInitial=false
  const f=fixture({getMessages:async(id,p)=>{
    if(p.before_sequence){beforeCalls++;return hold?old.promise:{items:[message('1')],has_more:false,next_before_sequence:'1'}}
    if(!p.after_sequence&&failInitial&&id==='1'){failInitial=false;throw new Error('temporary initial history failure')}
    return {items:[message('2'),message('3')],has_more:true,next_before_sequence:'2',next_after_sequence:'3'}
  }})
  await f.chat.selectConversation('1');const previous=f.chat.loadPrevious()
  await f.chat.selectConversation('2');failInitial=true;await f.chat.selectConversation('1')
  hold=false;f.api.getMessages=async(id,p)=>{
    if(p.before_sequence){beforeCalls++;return {items:[message('1')],has_more:false,next_before_sequence:'1'}}
    return {items:[],has_more:false,next_after_sequence:'3'}
  }
  await f.chat.pullNew();await f.chat.loadPrevious()
  assert.equal(beforeCalls,2,'recovered current history must be requestable')
  old.resolve({items:[],has_more:false});await previous
  assert.equal(f.state.paginationByConversation['1'].loadingPrevious,false)
})
test('Unicode validation uses code points, normalizes newlines and rejects invalid whitespace/control',()=>{
  assert.equal(normalizeChatText('😀'.repeat(2000)).length,4000)
  assert.equal(normalizeChatText('a\r\nb\rc'),'a\nb\nc')
  for(const value of ['😀'.repeat(2001),'\u00a0\u3000','a\u0000','\ud800']) assert.throws(()=>normalizeChatText(value))
})
test('poller schedules after completion, aborts hidden work and backs off without overlap',async()=>{
  let visible=true;let pending=deferred();const timers=[];const signals=[];const errors=[]
  const poller=createChatPoller({task:signal=>{signals.push(signal);return pending.promise},intervalMs:5000,isVisible:()=>visible,setTimeout:(fn,ms)=>{timers.push({fn,ms});return timers.length},clearTimeout:()=>{},onError:e=>errors.push(e)})
  poller.start(); assert.equal(signals.length,1); assert.equal(timers.length,0)
  visible=false;poller.visibilityChanged();assert.equal(signals[0].aborted,true)
  visible=true;poller.visibilityChanged();assert.equal(signals.length,1,'do not overlap an abort-ignoring request')
  const old=pending; pending=deferred();old.resolve();await new Promise(resolve=>setImmediate(resolve));assert.equal(signals.length,2)
  poller.stop();pending.resolve();await new Promise(resolve=>setImmediate(resolve))
  assert.ok(signals.every(signal=>signal.aborted));assert.equal(errors.length,0)
})
test('poller failure backoff is 10/20/40/60 seconds and success restores interval',async()=>{
  const timers=[];let fails=4;const poller=createChatPoller({task:async()=>{if(fails-->0) throw new Error('offline')},intervalMs:15000,isVisible:()=>true,setTimeout:(fn,ms)=>{timers.push({fn,ms});return timers.length},clearTimeout:()=>{},onError:()=>{}})
  poller.start();for(let i=0;i<5;i++){await new Promise(r=>setImmediate(r));if(i<4)timers[i].fn()}
  assert.deepEqual(timers.map(t=>t.ms),[10000,20000,40000,60000,15000]);poller.stop()
})
