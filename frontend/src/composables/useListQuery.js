import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { getAuthSessionId, subscribeAuthSessionChange } from '@/utils/auth'

// Requests belong to both the current query and the login session that issued them.
export function useRequestGate(onIdentityChange = () => {}) {
  let version = 0, disposed = false
  const unsubscribe = subscribeAuthSessionChange(() => { version++; onIdentityChange() })
  onBeforeUnmount(() => { disposed = true; version++; unsubscribe() })
  return {
    begin() { return { version: ++version, owner: getAuthSessionId() } },
    valid(ticket) { return !disposed && ticket.version === version && ticket.owner === getAuthSessionId() },
    invalidate() { version++ }
  }
}

export function usePagedList(request, parameters = () => ({}), size = 20) {
  const list = ref([]), loading = ref(false), error = ref(''), page = ref(1), total = ref(0)
  const pageSize = size
  const gate = useRequestGate(() => { list.value = []; total.value = 0; error.value = ''; loading.value = false })
  async function fetchList() {
    const ticket = gate.begin()
    loading.value = true; error.value = ''
    try {
      const res = await request({ ...parameters(), page: page.value, pageSize })
      if (!gate.valid(ticket)) return
      const data = res.data || {}
      total.value = Number(data.total || 0)
      const lastPage = Math.max(1, Math.ceil(total.value / pageSize))
      if (page.value > lastPage) { page.value = lastPage; await fetchList(); return }
      list.value = Array.isArray(data.list) ? data.list : []
    } catch (err) {
      if (gate.valid(ticket) && !err.sessionChanged) { error.value = '加载失败，请重试'; list.value = [] }
    } finally { if (gate.valid(ticket)) loading.value = false }
  }
  function resetAndFetch() { page.value = 1; return fetchList() }
  function handlePageChange(value) { page.value = value; return fetchList() }
  return { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange }
}

const memories = new Map()
let memoryOwner
// Memory is transient and keyed by the full route and login identity, never shared across accounts.
export function useListMemory(read, restore, onRouteChange = () => {}) {
  const route = useRoute(), owner = getAuthSessionId()
  let key = route.fullPath, updateTarget = null
  if (memoryOwner !== owner) { memories.clear(); memoryOwner = owner }
  let memory = memories.get(key)
  if (memory) restore(JSON.parse(JSON.stringify(memory.state)))
  let needsScroll = !!memory
  function save(path) {
    if (owner === getAuthSessionId()) memories.set(path, { state: JSON.parse(JSON.stringify(read())), top: window.scrollY })
  }
  onBeforeRouteLeave((_to, from) => save(from.fullPath))
  onBeforeRouteUpdate((to, from) => {
    save(from.fullPath)
    updateTarget = to.fullPath
  })
  // Restore after navigation commits, before fetching the new route's list.
  // The callback initializes URL filters only when that URL has no saved state.
  watch(() => route.fullPath, path => {
    if (path !== updateTarget || owner !== getAuthSessionId()) return
    updateTarget = null; key = path; memory = memories.get(key); needsScroll = !!memory
    if (memory) restore(JSON.parse(JSON.stringify(memory.state)))
    onRouteChange(route, !!memory)
  })
  return async () => {
    if (!needsScroll || owner !== getAuthSessionId()) return
    const saved = memory, restoredKey = key
    needsScroll = false
    await nextTick()
    requestAnimationFrame(() => { if (owner === getAuthSessionId() && route.fullPath === restoredKey) window.scrollTo({ top: saved.top, behavior: 'instant' }) })
  }
}
