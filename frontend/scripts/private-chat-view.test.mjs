import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
function fixture(overrides={}) {
  const route={params:{id:'1'}},watchers=[],scrolled=[],marked=[]
  const chat={state:{conversations:[],messagesByConversation:{},draftsByConversation:{},pendingByClientId:{},paginationByConversation:{},errors:{}},sessionId:'session-a',selectConversation:async()=>{},sendText:async()=>{},loadPrevious:async()=>{},pullNew:async()=>{},markVisibleRead:async()=>{marked.push(route.params.id)},...overrides}
  const context=vm.createContext({console,document:{visibilityState:'visible'},useRoute:()=>route,useRouter:()=>({push(){}}),useChatStore:()=>chat,useUserStore:()=>({userInfo:{user_id:1}}),useAppStore:()=>({isMobile:false}),useChatPolling(){},ref:value=>({value}),computed:fn=>({get value(){return typeof fn==='function'?fn():fn.get()}}),watch:(source,callback)=>watchers.push(callback),nextTick:async()=>{},onMounted(){},onUnmounted(){}})
  const source=readFileSync(new URL('../src/views/chat/MessageCenterView.vue',import.meta.url),'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'')
  vm.runInContext(source+'\nglobalThis.view={send,previous,refreshDetail,timeline};',context)
  context.view.timeline.value={scrollToLatest:async()=>{scrolled.push(route.params.id)}}
  return {route,chat,watchers,scrolled,marked,view:context.view}
}
test('a late selection continuation cannot scroll another conversation',async()=>{
  const slow=deferred();const f=fixture({selectConversation:id=>id==='1'?slow.promise:Promise.resolve()})
  const previous=f.watchers[0]('1');f.route.params.id='2';await f.watchers[0]('2');f.scrolled.length=0
  slow.resolve();await previous;assert.deepEqual(f.scrolled,[])
})
test('a late send continuation cannot scroll or mark another conversation',async()=>{
  const slow=deferred();const f=fixture({sendText:()=>slow.promise})
  const send=f.view.send('hello');f.route.params.id='2';await f.watchers[0]('2');f.scrolled.length=0;f.marked.length=0
  slow.resolve();await send;assert.deepEqual(f.scrolled,[]);assert.deepEqual(f.marked,[])
})
test('a late history completion cannot restore scroll in another conversation',async()=>{
  const slow=deferred();const f=fixture({loadPrevious:()=>slow.promise});const completed=[]
  const history=f.view.previous(async()=>completed.push(f.route.params.id))
  f.route.params.id='2';await f.watchers[0]('2');slow.resolve();await history;assert.deepEqual(completed,[])
})
test('a late manual refresh cannot mark another conversation read',async()=>{
  const slow=deferred();const f=fixture({pullNew:()=>slow.promise});const refresh=f.view.refreshDetail()
  f.route.params.id='2';await f.watchers[0]('2');f.marked.length=0;slow.resolve();await refresh;assert.deepEqual(f.marked,[])
})
