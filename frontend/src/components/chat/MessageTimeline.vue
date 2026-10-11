<template>
  <div class="timeline-wrap">
    <div ref="scroller" class="message-timeline" aria-label="消息历史" @scroll="scroll">
      <div v-if="loading && !messages.length" class="timeline-loading" role="status">正在加载消息…</div>
      <ErrorState v-else-if="error && !messages.length" title="会话暂不可访问" :sub-title="error" @retry="$emit('retry')" />
      <template v-else>
        <button v-if="hasPrevious" class="previous-button" :disabled="loadingPrevious" @click="previous">{{ loadingPrevious?'加载中…':'加载更早消息' }}</button>
        <EmptyState v-if="!messages.length" description="发送第一条消息" />
        <ol class="message-rows">
          <li v-for="(message,index) in messages" :key="message.message_id || message.client_message_id" :data-message-id="message.message_id || message.client_message_id">
            <div v-if="index===0 || day(message.created_at)!==day(messages[index-1].created_at)" class="date-divider">{{ day(message.created_at) }}</div>
            <div class="message-row" :class="{own:isOwn(message)}">
              <el-avatar v-if="!isOwn(message)" :size="34" :src="peer?.avatar_url">{{ Array.from(peer?.nickname || '?')[0] }}</el-avatar>
              <div class="message-block">
                <time>{{ time(message.created_at) }}</time>
                <div class="message-bubble">
                  <TextMessage v-if="message.message_type==='TEXT'" :message="message" :own="isOwn(message)" @retry="$emit('retry-send',$event)" />
                  <span v-else>暂不支持的消息类型</span>
                </div>
              </div>
            </div>
          </li>
        </ol>
      </template>
    </div>
    <button v-if="newMessages" class="new-message-button" aria-label="有新消息，前往最新" @click="scrollToLatest">有新消息 ↓</button>
    <span class="sr-only" aria-live="polite">{{ announcement }}</span>
  </div>
</template>
<script setup>
import { ref,watch,nextTick,onMounted } from 'vue'
import TextMessage from './TextMessage.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
const props=defineProps({messages:{type:Array,default:()=>[]},peer:Object,loading:Boolean,error:String,hasPrevious:Boolean,myUserId:String})
const emit=defineEmits(['load-previous','at-bottom','retry','retry-send'])
const scroller=ref(null),atBottom=ref(true),newMessages=ref(false),loadingPrevious=ref(false),announcement=ref('')
const isOwn=message=>message.sender_id===props.myUserId || message.sender_id===null
function day(value){return new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'long',day:'numeric'}).format(new Date(value))}
function time(value){return new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit'}).format(new Date(value))}
function scroll(){const el=scroller.value;if(!el)return;atBottom.value=el.scrollHeight-el.scrollTop-el.clientHeight<40;if(atBottom.value)newMessages.value=false;emit('at-bottom',atBottom.value)}
async function scrollToLatest(){await nextTick();const el=scroller.value;if(el){el.scrollTop=el.scrollHeight;newMessages.value=false;scroll()}}
function previous(){
 const el=scroller.value;if(!el||loadingPrevious.value)return
 const height=el.scrollHeight,top=el.scrollTop;loadingPrevious.value=true
 emit('load-previous',async()=>{await nextTick();el.scrollTop=top+el.scrollHeight-height;loadingPrevious.value=false;scroll()})
}
watch(()=>props.messages.map(m=>m.message_id || m.client_message_id).join(','),async(value,old)=>{
 if(!value || loadingPrevious.value)return
 const latest=props.messages.at(-1)
 if(!old || atBottom.value || latest?.sender_id===null){await scrollToLatest()}
 else {newMessages.value=true;announcement.value='有新消息'}
},{flush:'post'})
onMounted(scroll)
defineExpose({scrollToLatest})
</script>
<style scoped>
.timeline-wrap{position:relative;flex:1;min-height:0;background:var(--color-bg-page)}
.message-timeline{height:100%;overflow-y:auto;overscroll-behavior:contain;overflow-anchor:none;padding:20px 24px;box-sizing:border-box}
.message-rows{list-style:none;margin:0;padding:0}.date-divider{text-align:center;color:var(--color-text-secondary);font-size:11px;margin:12px 0 24px}
.message-row{display:flex;align-items:flex-start;gap:10px;margin:0 0 20px;min-width:0}.message-row.own{justify-content:flex-end}
.message-block{max-width:min(78%,560px);min-width:0}.message-block>time{display:block;margin:0 0 5px;color:var(--color-text-secondary);font-size:10px}
.own .message-block>time{text-align:right}
.message-bubble{padding:12px 16px;border-radius:var(--radius-md);border:1px solid var(--color-border-light);background:var(--color-bg-card);color:var(--color-text-primary);box-shadow:var(--shadow-sm)}
.own .message-bubble{background:var(--color-primary);color:var(--color-on-primary);border-color:transparent}
.previous-button{display:block;min-height:44px;margin:0 auto 12px;padding:0 12px;border:0;background:transparent;color:var(--color-primary);cursor:pointer}
.new-message-button{position:absolute;right:20px;bottom:16px;min-height:44px;padding:0 16px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--color-bg-card);color:var(--color-primary);box-shadow:var(--shadow-md);cursor:pointer}
button:focus-visible{outline:2px solid var(--color-primary);outline-offset:3px}.timeline-loading{padding:24px;color:var(--color-text-secondary)}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}
@media(max-width:767px){.message-timeline{padding:16px 12px}.message-block{max-width:85%}.message-bubble{padding:10px 12px}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}
</style>
