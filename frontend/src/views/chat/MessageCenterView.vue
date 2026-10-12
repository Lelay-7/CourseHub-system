<template>
  <AppLayout :class="{'chat-layout-detail':!!route.params.id,'chat-layout-minimal':minimalViewport}">
    <PageContainer class="message-page" :class="{detail:!!route.params.id,'minimal-viewport':minimalViewport}" :style="viewportStyle">
      <PageHeader v-if="!app.isMobile || !route.params.id" title="消息" subtitle="和好友聊聊课程与学习"><router-link class="contact-link" to="/contact?tab=friends">去通讯录</router-link></PageHeader>
      <section class="chat-frame" :class="{'has-detail':!!route.params.id}">
        <ConversationList class="chat-list" :items="chat.state.conversations" :selected-id="chat.state.selectedId" :loading="listLoading" :error="chat.state.errors._list" :has-more="!!chat.state.paginationByConversation._list?.nextCursor" @select="select" @retry="refreshList" @load-more="more" />
        <div class="chat-detail">
          <template v-if="route.params.id">
            <header class="chat-heading">
              <button class="back-chat mobile-only" aria-label="返回会话列表" @click="router.push('/messages')"><el-icon><ArrowLeft /></el-icon></button>
              <el-avatar :size="40" :src="conversation?.peer.avatar_url">{{ Array.from(conversation?.peer.nickname || '?')[0] }}</el-avatar>
              <div class="peer-heading"><h2>{{ conversation?.peer.nickname || '正在加载…' }}</h2><p>{{ conversation?.peer.department || '私聊' }}<span v-if="conversation?.can_send"> · 好友</span></p></div>
              <button class="heading-action" aria-label="刷新消息" @click="refreshDetail"><el-icon><Refresh /></el-icon></button>
              <router-link v-if="conversation?.peer.user_id && conversation.send_disabled_reason!=='PEER_UNAVAILABLE'" class="card-link" :to="'/contact/'+conversation.peer.user_id">查看名片</router-link>
            </header>
            <div v-if="conversation && !conversation.can_send" class="readonly-notice" role="status">{{ conversation.send_disabled_reason==='NOT_FRIENDS'?'已解除好友，历史消息仍可查看':'对方账号不可用，历史消息仍可查看' }}</div>
            <div v-if="chat.state.errors[id] && messages.length" class="inline-error" role="status">{{ chat.state.errors[id] }} <button @click="refreshDetail">重试</button></div>
            <MessageTimeline ref="timeline" :conversation-id="id" :messages="messages" :peer="conversation?.peer" :my-user-id="myUserId" :loading="pagination.loading" :error="chat.state.errors[id]" :has-previous="pagination.hasPrevious" :can-send="conversation?.can_send" @load-previous="previous" @retry="reload" @retry-send="chat.retrySend" @at-bottom="bottom" />
            <MessageComposer v-model="draft" :class="{'minimal-composer':minimalViewport}" :disabled="!conversation?.can_send || !!pagination.loading" :sending="sending" @send="send" />
          </template>
          <EmptyState v-else description="选择好友，开始聊天"><router-link class="contact-link" to="/contact?tab=friends">去通讯录</router-link></EmptyState>
        </div>
      </section>
    </PageContainer>
  </AppLayout>
</template>
<script setup>
import { ref,computed,watch,nextTick,onMounted,onUnmounted } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ArrowLeft,Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import MessageTimeline from '@/components/chat/MessageTimeline.vue'
import MessageComposer from '@/components/chat/MessageComposer.vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useChatPolling } from '@/composables/useChatPolling'
const route=useRoute(),router=useRouter(),chat=useChatStore(),user=useUserStore(),app=useAppStore()
const timeline=ref(null),atBottom=ref(true),viewportStyle=ref({}),minimalViewport=ref(false)
const id=computed(()=>route.params.id?String(route.params.id):null)
const conversation=computed(()=>chat.state.conversations.find(row=>row.conversation_id===id.value))
const messages=computed(()=>chat.state.messagesByConversation[id.value] || [])
const pagination=computed(()=>chat.state.paginationByConversation[id.value] || {})
const myUserId=computed(()=>String(user.userInfo?.user_id ?? user.userInfo?.userId ?? ''))
const draft=computed({get:()=>chat.state.draftsByConversation[id.value] || '',set:value=>{if(id.value)chat.state.draftsByConversation[id.value]=value}})
const sending=computed(()=>Object.values(chat.state.pendingByClientId).some(row=>row.conversation_id===id.value && row.delivery_state==='SENDING'))
const listLoading=computed(()=>!chat.state.paginationByConversation._list&&!chat.state.errors._list)
useChatPolling({detail:true})
let viewGeneration=0,disposed=false
const viewTicket=()=>({generation:viewGeneration,id:id.value,session:chat.sessionId})
const current=ticket=>!disposed && ticket.generation===viewGeneration && ticket.id===id.value && ticket.session===chat.sessionId
watch(id,async value=>{viewGeneration++;const ticket=viewTicket();atBottom.value=true;await chat.selectConversation(value);if(!current(ticket))return;await nextTick();if(value&&current(ticket))await timeline.value?.scrollToLatest()},{immediate:true})
watch(()=>messages.value.map(m=>m.sequence).join(','),()=>{void markRead()},{flush:'post'})
function select(value){router.push('/messages/'+value)}
async function reload(){await chat.selectConversation(id.value)}
async function refreshList(){try{await chat.refreshConversations()}catch{}}
async function more(){try{await chat.refreshConversations({cursor:chat.state.paginationByConversation._list?.nextCursor})}catch{}}
async function refreshDetail(){const ticket=viewTicket();try{await chat.pullNew();if(current(ticket))await markRead()}catch{}}
async function previous(done){const ticket=viewTicket();try{await chat.loadPrevious()}catch{}finally{if(current(ticket))await done()}}
async function send(value){const ticket=viewTicket();await chat.sendText(value);if(!current(ticket))return;await timeline.value?.scrollToLatest();if(current(ticket))await markRead()}
function bottom(value){if(minimalViewport.value)return;atBottom.value=value;void markRead()}
async function markRead(){await chat.markVisibleRead({visible:document.visibilityState==='visible'&&!minimalViewport.value,atBottom:atBottom.value})}
function measureViewport(){
 const viewport=window.visualViewport,navbar=document.querySelector('.app-navbar')
 const height=viewport?.height || window.innerHeight,offsetTop=Math.max(0,viewport?.offsetTop || 0)
 const minimal=window.innerWidth<=1023&&height<160
 if(minimalViewport.value!==minimal){minimalViewport.value=minimal;void nextTick().then(()=>{if(!disposed)measureViewport()})}
 const navbarBottom=Math.max(0,navbar?.getBoundingClientRect().bottom ?? 66)
 const top=Math.max(offsetTop,navbarBottom)
 // A mobile keyboard can pan the visual viewport or scroll the document after focus.
 // Keep the detail frame inside the visible viewport rather than in document flow.
 viewportStyle.value={'--chat-viewport-height':height+'px','--chat-navbar-height':navbarBottom+'px','--chat-viewport-top':top+'px','--chat-detail-height':Math.max(0,offsetTop+height-top)+'px'}
 if(id.value&&atBottom.value&&!minimalViewport.value){const ticket=viewTicket();void nextTick().then(()=>{if(current(ticket)&&atBottom.value&&!minimalViewport.value)void timeline.value?.scrollToLatest()})}
}
function visible(){if(document.visibilityState==='visible')void markRead()}
onMounted(()=>{measureViewport();window.visualViewport?.addEventListener('resize',measureViewport);window.visualViewport?.addEventListener('scroll',measureViewport);window.addEventListener('resize',measureViewport);document.addEventListener('visibilitychange',visible)})
onUnmounted(()=>{disposed=true;viewGeneration++;void chat.selectConversation(null);window.visualViewport?.removeEventListener('resize',measureViewport);window.visualViewport?.removeEventListener('scroll',measureViewport);window.removeEventListener('resize',measureViewport);document.removeEventListener('visibilitychange',visible)})
</script>
<style scoped>
.message-page{max-width:1180px}
.chat-frame{display:grid;grid-template-columns:300px minmax(0,1fr);height:min(760px,calc(100dvh - 240px));min-height:480px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--color-bg-card);box-shadow:var(--shadow-sm);overflow:hidden}
.chat-detail{display:flex;flex-direction:column;min-width:0;min-height:0}
.chat-heading{display:flex;align-items:center;gap:12px;min-height:82px;padding:16px 24px;box-sizing:border-box;border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.peer-heading{flex:1;min-width:0}.peer-heading h2{margin:0;font-size:16px;font-weight:750;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.peer-heading p{margin:5px 0 0;color:var(--color-text-secondary);font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.contact-link,.card-link{display:inline-flex;align-items:center;min-height:44px;color:var(--color-primary);font-size:13px;text-decoration:none;white-space:nowrap}
.heading-action,.back-chat{display:flex;align-items:center;justify-content:center;flex-shrink:0;width:44px;height:44px;border:0;border-radius:var(--radius-sm);background:transparent;color:var(--color-primary);cursor:pointer;font-size:18px}
.back-chat{display:none}
.chat-layout-detail.chat-layout-minimal :deep(.app-navbar){display:none}
.minimal-viewport.detail{width:100%;padding:0;max-width:100%}
.minimal-viewport.detail .chat-frame{display:block;position:fixed;top:var(--chat-viewport-top,0px);left:0;right:0;z-index:20;height:var(--chat-detail-height);min-height:0;border:0;border-radius:0;box-shadow:none}
.minimal-viewport.detail .page-header,.minimal-viewport.detail .chat-list{display:none}
.minimal-viewport.detail .chat-heading{display:none}
.minimal-viewport.detail .timeline-wrap{visibility:hidden;flex:0;height:0;overflow:hidden}
.minimal-viewport.detail .chat-detail{display:flex;height:100%;justify-content:flex-end}
.readonly-notice,.inline-error{padding:10px 20px;background:var(--color-bg-soft);font-size:12px;color:var(--color-text-secondary);flex-shrink:0}
.inline-error{display:flex;align-items:center;justify-content:space-between;gap:8px}.inline-error button{min-height:44px;background:transparent;border:0;color:var(--color-primary);cursor:pointer}
button:focus-visible,a:focus-visible{outline:2px solid var(--color-primary);outline-offset:2px}
@media(min-width:768px) and (max-width:1050px){.chat-frame{grid-template-columns:260px minmax(0,1fr)}}
@media(max-width:767px), (max-width:1023px) and (max-height:500px){
.message-page.detail{width:100%;padding:0;max-width:100%}
.detail .page-header{display:none}
.chat-frame{display:block;height:calc(var(--chat-viewport-height,100dvh) - var(--chat-navbar-height,66px) - 170px);min-height:350px}
.chat-list{height:100%}.chat-detail{display:none}
.detail .chat-frame{position:fixed;top:var(--chat-viewport-top,66px);left:0;right:0;z-index:20;height:var(--chat-detail-height,calc(100dvh - 66px));min-height:0;border:0;border-radius:0;box-shadow:none}
.detail .chat-list{display:none}.detail .chat-detail{display:flex;height:100%}
.chat-heading{min-height:72px;padding:10px 12px;gap:8px}.chat-heading .el-avatar{display:none}
.back-chat{display:flex}
.card-link{font-size:11px}.peer-heading h2{font-size:14px}.heading-action{width:44px}
}
@media(max-width:1023px) and (max-height:500px){
/* A short phone viewport needs its conversation controls, with room left for messages. */
.chat-layout-detail :deep(.app-navbar){display:none}
.chat-heading{min-height:44px;padding:0 12px}.peer-heading p{display:none}
}
</style>
