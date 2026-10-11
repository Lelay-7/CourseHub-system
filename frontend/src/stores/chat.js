import { defineStore } from 'pinia'
import { reactive, ref, onScopeDispose } from 'vue'
import * as api from '@/api/chat'
import { createChatState, createChatSession } from '@/utils/chat-state'
import { getAuthSessionId, subscribeAuthSessionChange } from '@/utils/auth'
export const useChatStore = defineStore('chat', () => {
  const state = reactive(createChatState())
  const sessionId = ref(getAuthSessionId())
  const session = createChatSession({state,api,getSessionId:getAuthSessionId})
  const unsubscribe = subscribeAuthSessionChange(id => {
    session.reset()
    sessionId.value = id
  })
  onScopeDispose(unsubscribe)
  return { state, sessionId, ...session }
})
