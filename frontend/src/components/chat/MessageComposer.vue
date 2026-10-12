<template>
  <form class="message-composer" @submit.prevent="send">
    <label class="sr-only" for="chat-message">消息内容</label>
    <textarea id="chat-message" :value="modelValue" :disabled="disabled" placeholder="和好友聊聊课程与学习…" rows="2" @input="$emit('update:modelValue',$event.target.value)" @compositionstart="composing=true" @compositionend="composing=false" @keydown="keydown" />
    <div class="composer-footer">
      <span class="composer-hint">{{ disabled?'当前会话只读':'Enter 发送 · Shift+Enter 换行' }}</span>
      <span class="character-count" :class="{invalid:count>maxCodePoints}" aria-live="polite">{{ count }} / {{ maxCodePoints }}</span>
      <button type="submit" :disabled="disabled || sending || !valid">{{ sending?'发送中':'发送' }}</button>
    </div>
  </form>
</template>
<script setup>
import { ref,computed } from 'vue'
import { normalizeChatText } from '@/utils/chat-state'
const props=defineProps({modelValue:{type:String,default:''},disabled:Boolean,sending:Boolean,maxCodePoints:{type:Number,default:2000}})
const emit=defineEmits(['update:modelValue','send'])
const composing=ref(false)
const count=computed(()=>Array.from(props.modelValue.replace(/\r\n?/g,'\n')).length)
const valid=computed(()=>{try{normalizeChatText(props.modelValue);return count.value<=props.maxCodePoints}catch{return false}})
function send(){if(!props.disabled&&!props.sending&&valid.value)emit('send',props.modelValue)}
function keydown(event){if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing&&!composing.value&&event.keyCode!==229){event.preventDefault();send()}}
</script>
<style scoped>
.message-composer{padding:16px 20px max(16px,env(safe-area-inset-bottom));border-top:1px solid var(--color-border-light);background:var(--color-bg-card);flex-shrink:0}
textarea{display:block;box-sizing:border-box;width:100%;resize:vertical;min-height:72px;max-height:180px;padding:12px;border:1px solid var(--color-border);border-radius:var(--radius-sm);background:var(--color-bg-soft);color:var(--color-text-primary);font:inherit;line-height:1.5}
textarea:disabled{opacity:.6;resize:none}
textarea:focus-visible,button:focus-visible{outline:2px solid var(--color-primary);outline-offset:2px}
.composer-footer{display:flex;align-items:center;gap:12px;margin-top:8px;min-width:0}
.composer-hint{flex:1;font-size:11px;color:var(--color-text-secondary)}
.character-count{font-size:11px;color:var(--color-text-secondary);white-space:nowrap}.character-count.invalid{color:var(--color-danger)}
button{height:44px;min-width:80px;padding:0 16px;border:0;border-radius:var(--radius-sm);background:var(--color-primary);color:var(--color-on-primary);font:inherit;font-size:13px;font-weight:700;cursor:pointer}
button:disabled{opacity:.45;cursor:default}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}
@media(max-width:767px){.message-composer{padding:12px 12px max(12px,env(safe-area-inset-bottom))}.composer-hint{display:none}.character-count{flex:1}}
@media(max-width:1023px) and (max-height:500px){
.message-composer{display:flex;align-items:center;gap:12px;padding:8px 12px max(8px,env(safe-area-inset-bottom))}
textarea{flex:1;min-width:0;min-height:44px;height:60px;max-height:88px;padding:6px 10px;font-size:13px}
.composer-footer{flex-shrink:0;margin-top:0;gap:8px}.composer-hint{display:none}
}
.minimal-composer{display:flex;align-items:center;gap:8px;padding:1px 8px}
.minimal-composer textarea{flex:1;min-width:0;min-height:0;height:44px;max-height:44px;resize:none;padding:6px 10px}
.minimal-composer .composer-footer{flex-shrink:0;margin-top:0;gap:0}
.minimal-composer .composer-hint,.minimal-composer .character-count{display:none}
</style>
