<template>
  <aside class="conversation-list" aria-label="私聊会话">
    <div class="conversation-search"><label class="sr-only" for="chat-search">搜索会话</label><input id="chat-search" v-model="query" placeholder="搜索已加载的会话" @input="$emit('search', query)" /></div>
    <div v-if="loading && !items.length" class="list-loading" role="status">正在加载会话…</div>
    <ErrorState v-else-if="error && !items.length" title="会话加载失败" :sub-title="error" @retry="$emit('retry')" />
    <EmptyState v-else-if="!items.length" description="还没有会话"><router-link class="contact-link" to="/contact?tab=friends">去通讯录找好友</router-link></EmptyState>
    <div v-else class="conversation-rows">
      <p v-if="error" class="list-error" role="status">{{ error }} <button @click="$emit('retry')">重试</button></p>
      <button v-for="item in filtered" :key="item.conversation_id" class="conversation-row" :class="{selected:item.conversation_id===selectedId}" :aria-current="item.conversation_id===selectedId?'true':undefined" :aria-label="'与'+item.peer.nickname+'的会话'" @click="$emit('select',item.conversation_id)">
        <el-avatar :size="44" :src="item.peer.avatar_url">{{ Array.from(item.peer.nickname || '?')[0] }}</el-avatar>
        <span class="conversation-copy"><span class="conversation-title"><strong>{{ item.peer.nickname }}</strong><time>{{ time(item.last_message?.created_at) }}</time></span><span class="conversation-preview"><span>{{ item.last_message?.content?.text || '发送第一条消息' }}</span><b v-if="item.unread_count" aria-label="未读消息">{{ item.unread_count>99?'99+':item.unread_count }}</b></span></span>
      </button>
      <p v-if="!filtered.length" class="no-match">没有匹配的会话</p>
      <slot name="more"><button v-if="hasMore" class="load-more" @click="$emit('load-more')">加载更多会话</button></slot>
    </div>
  </aside>
</template>
<script setup>
import { ref, computed } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
const props=defineProps({items:{type:Array,default:()=>[]},selectedId:String,loading:Boolean,error:String,hasMore:Boolean})
defineEmits(['select','search','load-more','retry'])
const query=ref('')
const filtered=computed(()=>props.items.filter(item=>item.peer.nickname.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
function time(value){if(!value)return '';return new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit'}).format(new Date(value))}
</script>
<style scoped>
.conversation-list{display:flex;flex-direction:column;min-height:0;border-right:1px solid var(--color-border-light);background:var(--color-bg-card)}
.conversation-search{padding:20px 16px;border-bottom:1px solid var(--color-border-light)}
.conversation-search input{box-sizing:border-box;width:100%;height:44px;padding:0 12px;border:1px solid var(--color-border);border-radius:var(--radius-sm);background:var(--color-bg-soft);color:var(--color-text-primary);font:inherit;font-size:13px}
.conversation-rows{overflow-y:auto;min-height:0;padding:8px}
.conversation-row{display:flex;align-items:center;gap:12px;width:100%;min-height:86px;padding:14px 10px;margin:4px 0;border:1px solid transparent;border-radius:var(--radius-sm);background:transparent;color:var(--color-text-primary);text-align:left;cursor:pointer}
.conversation-row:hover,.conversation-row.selected{background:var(--color-bg-soft);border-color:var(--color-border)}
.conversation-copy{min-width:0;flex:1}.conversation-title,.conversation-preview{display:flex;align-items:center;justify-content:space-between;gap:8px}
.conversation-title strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}
.conversation-title time{flex-shrink:0;font-size:11px;color:var(--color-text-secondary)}
.conversation-preview{margin-top:7px;color:var(--color-text-secondary);font-size:12px}
.conversation-preview>span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.conversation-preview b{flex-shrink:0;min-width:20px;padding:3px;border-radius:20px;text-align:center;background:var(--color-primary);color:var(--color-on-primary);font-size:10px}
.list-loading,.no-match{padding:24px;color:var(--color-text-secondary)}
.load-more,.list-error button,.contact-link{min-height:44px;border:0;color:var(--color-primary);background:transparent;cursor:pointer}
.load-more{width:100%}.list-error{padding:8px;font-size:12px}
button:focus-visible,input:focus-visible{outline:2px solid var(--color-primary);outline-offset:2px}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}
</style>
