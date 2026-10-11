import { onMounted, onUnmounted, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { createChatPoller } from '@/utils/chat-polling'
export function useChatPolling({ detail = false } = {}) {
  const chat = useChatStore()
  const poller = createChatPoller({
    intervalMs: detail ? 5000 : 15000,
    isVisible: () => document.visibilityState === 'visible',
    task: async signal => {
      if (detail) await chat.pullNew({signal})
      else await Promise.all([chat.refreshUnread({signal}),chat.refreshConversations({signal})])
    }
  })
  let mounted = false
  function sync() {
    poller.stop()
    if (mounted && chat.sessionId && (!detail || chat.state.selectedId)) poller.start()
  }
  watch(() => [chat.sessionId, detail ? chat.state.selectedId : null],sync,{flush:'sync'})
  onMounted(() => { mounted=true; document.addEventListener('visibilitychange',poller.visibilityChanged); sync() })
  onUnmounted(() => { mounted=false; poller.stop(); document.removeEventListener('visibilitychange',poller.visibilityChanged) })
  return poller
}
