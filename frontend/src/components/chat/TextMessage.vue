<template>
  <div class="message-content">
    <div class="text-message">{{ message.content?.text }}</div>
    <div v-if="own" class="delivery-status">
      <span v-if="message.delivery_state==='SENDING'">发送中</span>
      <button v-else-if="message.delivery_state==='FAILED' && canSend" aria-label="重试发送" @click="$emit('retry',message.client_message_id)">发送失败，重试</button>
      <span v-else-if="message.delivery_state==='FAILED'">发送失败，当前会话无法重试</span>
      <span v-else>已发送</span>
    </div>
  </div>
</template>
<script setup>
defineProps({message:{type:Object,required:true},own:Boolean,canSend:Boolean})
defineEmits(['retry'])
</script>
<style scoped>
.text-message{white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;line-height:1.75;font-size:14px}
.delivery-status{display:flex;justify-content:flex-end;align-items:center;font-size:11px;margin-top:6px;opacity:.9}
.delivery-status button{min-height:44px;padding:0;border:0;background:transparent;color:inherit;font:inherit;cursor:pointer;text-decoration:underline}
.delivery-status button:focus-visible{outline:2px solid currentColor;outline-offset:3px}
</style>
