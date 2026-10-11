export function createChatState() {
  return { conversations: [], messagesByConversation: {}, draftsByConversation: {}, pendingByClientId: {}, selectedId: null, unreadCount: 0, paginationByConversation: {}, errors: {} }
}

export function normalizeChatText(text) {
  if (typeof text !== 'string') throw new Error('请输入消息')
  for (let i = 0; i < text.length; i++) {
    const cp = text.charCodeAt(i)
    if (cp >= 0xd800 && cp <= 0xdbff) {
      const next = text.charCodeAt(++i)
      if (!(next >= 0xdc00 && next <= 0xdfff)) throw new Error('消息包含无效字符')
    } else if (cp >= 0xdc00 && cp <= 0xdfff) throw new Error('消息包含无效字符')
  }
  const normalized = text.replace(/\r\n?/g, '\n')
  if (Array.from(normalized).length < 1 || Array.from(normalized).length > 2000) throw new Error('消息应为 1–2000 个字符')
  if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/u.test(normalized)) throw new Error('消息包含不支持的控制字符')
  if (/^\p{White_Space}*$/u.test(normalized)) throw new Error('消息不能只有空白')
  return normalized
}

const order = (a, b) => a < b ? -1 : a > b ? 1 : 0
export function compareChatSequence(a, b) { return order(BigInt(a), BigInt(b)) }
const errorText = error => error.response?.data?.message || error.message || '网络异常，请重试'

export function createChatSession({ state, api, getSessionId, newClientMessageId = () => crypto.randomUUID() }) {
  let generation = 0, selection = 0
  const controllers = new Set()
  const epochs = new Map()
  function ticket(kind, selected = true, config = {}) {
    const epoch = (epochs.get(kind) || 0) + 1
    epochs.set(kind, epoch)
    const identity = getSessionId(), gen = generation, sel = selection, id = state.selectedId
    const controller = new AbortController()
    controller.chatSelected = selected
    controllers.add(controller)
    const abort = () => controller.abort()
    if (config.signal?.aborted) abort()
    config.signal?.addEventListener('abort', abort, { once: true })
    return {
      id, config: { ...config, signal: controller.signal, silentError: true },
      valid: () => !controller.signal.aborted && identity !== null && getSessionId() === identity && gen === generation && (!selected || (sel === selection && id === state.selectedId)) && epochs.get(kind) === epoch,
      finish() { controllers.delete(controller); config.signal?.removeEventListener('abort', abort) }
    }
  }
  function merge(id, incoming) {
    const byKey = new Map()
    for (const row of [...(state.messagesByConversation[id] || []), ...incoming]) {
      const key = row.sequence ? 'sequence:' + row.sequence : 'client:' + row.client_message_id
      if (row.sequence) {
        for (const [k, value] of byKey) if (value.client_message_id === row.client_message_id) byKey.delete(k)
        delete state.pendingByClientId[row.client_message_id]
      }
      byKey.set(key, row.sequence ? { ...row, delivery_state: 'SENT' } : row)
    }
    state.messagesByConversation[id] = [...byKey.values()].sort((a, b) => a.sequence && b.sequence ? compareChatSequence(a.sequence, b.sequence) : a.sequence ? -1 : b.sequence ? 1 : order(a.created_at, b.created_at))
  }
  function updateConversation(row) {
    const index = state.conversations.findIndex(c => c.conversation_id === row.conversation_id)
    if (index < 0) state.conversations.push(row)
    else state.conversations[index] = row
  }
  function reset() {
    generation++; selection++
    for (const controller of controllers) controller.abort()
    controllers.clear(); epochs.clear()
    Object.assign(state, createChatState())
  }
  async function selectConversation(id) {
    selection++
    for (const controller of controllers) if (controller.chatSelected) controller.abort()
    // A cancelled response might already have committed. Retry retains its original UUID.
    for (const pending of Object.values(state.pendingByClientId)) if (pending.delivery_state === 'SENDING') pending.delivery_state = 'FAILED'
    state.selectedId = id == null ? null : String(id)
    if (state.selectedId === null) return
    const t = ticket('select')
    state.errors[t.id] = null
    state.paginationByConversation[t.id] = { ...(state.paginationByConversation[t.id] || {}), loading: true, loadingPrevious: false }
    try {
      const detail = await api.getConversation(t.id, t.config)
      if (!t.valid()) return
      updateConversation(detail)
      const page = await api.getMessages(t.id, {}, t.config)
      if (!t.valid()) return
      merge(t.id, page.items)
      state.paginationByConversation[t.id] = { before: page.next_before_sequence, after: page.next_after_sequence || '0', hasPrevious: page.has_more, loading: false }
    } catch (error) { if (t.valid()) state.errors[t.id] = errorText(error) }
    finally { if (t.valid()) state.paginationByConversation[t.id].loading = false; t.finish() }
  }
  async function loadPrevious(config = {}) {
    const id = state.selectedId, pagination = state.paginationByConversation[id]
    if (!id || !pagination?.hasPrevious || pagination.loadingPrevious) return
    const t = ticket('previous', true, config); pagination.loadingPrevious = true
    try {
      const page = await api.getMessages(id, { before_sequence: pagination.before }, t.config)
      if (!t.valid()) return
      merge(id, page.items); pagination.before = page.next_before_sequence; pagination.hasPrevious = page.has_more; state.errors[id] = null
    } catch (error) { if (t.valid()) state.errors[id] = errorText(error); throw error }
    finally { pagination.loadingPrevious = false; t.finish() }
  }
  async function pullNew(config = {}) {
    const id = state.selectedId
    if (!id || !state.paginationByConversation[id] || state.paginationByConversation[id].loading) return
    const t = ticket('new', true, config)
    try {
      const detail = await api.getConversation(id, t.config)
      if (!t.valid()) return
      updateConversation(detail)
      let after = state.paginationByConversation[id].after || '0'
      for (;;) {
        const page = await api.getMessages(id, { after_sequence: after }, t.config)
        if (!t.valid()) return
        merge(id, page.items)
        const last = page.items.at(-1)?.sequence || after
        state.paginationByConversation[id].after = last
        if (!page.has_more) break
        if (compareChatSequence(last, after) <= 0) throw new Error('消息分页没有前进，请重试')
        after = last
      }
      state.errors[id] = null
    } catch (error) { if (t.valid()) state.errors[id] = errorText(error); throw error }
    finally { t.finish() }
  }
  async function deliver(pending) {
    const id = pending.conversation_id
    if (id !== state.selectedId || pending.delivery_state === 'SENDING') return
    const conversation = state.conversations.find(c => c.conversation_id === id)
    if (!conversation?.can_send) return
    const t = ticket('send:' + pending.client_message_id)
    pending.delivery_state = 'SENDING'
    try {
      const row = await api.sendMessage(id, { client_message_id: pending.client_message_id, message_type: 'TEXT', text: pending.content.text }, t.config)
      if (!t.valid()) return
      merge(id, [row]); state.errors[id] = null
      // Sending may race with incoming messages. The pull cursor must advance through history only.
      return state.messagesByConversation[id].find(m => m.client_message_id === row.client_message_id)
    } catch (error) {
      if (t.valid()) { pending.delivery_state = 'FAILED'; pending.error = errorText(error); state.errors[id] = pending.error }
      return t.valid() ? pending : undefined
    } finally { t.finish() }
  }
  async function sendText(text) {
    const id = state.selectedId
    if (!id || !state.conversations.find(c => c.conversation_id === id)?.can_send) return
    let normalized
    try { normalized = normalizeChatText(text) } catch (error) { state.errors[id] = error.message; return }
    if (Object.values(state.pendingByClientId).some(p => p.conversation_id === id && p.delivery_state === 'SENDING')) return
    const clientId = newClientMessageId()
    const row = { message_id: null, sequence: null, conversation_id: id, sender_id: null, client_message_id: clientId, message_type: 'TEXT', content: { text: normalized }, created_at: new Date().toISOString(), delivery_state: 'FAILED' }
    state.pendingByClientId[clientId] = row
    state.messagesByConversation[id] = [...(state.messagesByConversation[id] || []), row]
    state.draftsByConversation[id] = ''
    return deliver(state.pendingByClientId[clientId])
  }
  async function retrySend(clientId) { const pending = state.pendingByClientId[clientId]; return pending ? deliver(pending) : undefined }
  async function markVisibleRead({ visible, atBottom }) {
    const id = state.selectedId
    if (!id || !visible || !atBottom || state.paginationByConversation[id]?.loading) return
    const highestLoaded = (state.messagesByConversation[id] || []).filter(m => m.sequence).at(-1)?.sequence
    // A send acknowledgement may be ahead of incoming messages not fetched yet.
    const fetched = state.paginationByConversation[id]?.after || '0'
    const loaded = highestLoaded && compareChatSequence(highestLoaded, fetched) > 0 ? fetched : highestLoaded
    const conversation = state.conversations.find(c => c.conversation_id === id)
    if (!loaded || compareChatSequence(loaded, conversation?.last_read_sequence || '0') <= 0) return
    const t = ticket('read')
    try {
      const result = await api.markRead(id, loaded, t.config)
      if (!t.valid()) return
      const currentConversation = state.conversations.find(c => c.conversation_id === id)
      if (currentConversation && compareChatSequence(result.last_read_sequence, currentConversation.last_read_sequence || '0') >= 0) {
        currentConversation.last_read_sequence = result.last_read_sequence
        currentConversation.unread_count = result.unread_count
      }
      await refreshUnread()
    } catch (error) { if (t.valid()) state.errors[id] = errorText(error) }
    finally { t.finish() }
  }
  async function refreshConversations(config = {}) {
    const t = ticket('list', false, config)
    try {
      const page = await api.listConversations({ limit: 30, ...(config.cursor ? { cursor: config.cursor } : {}) }, t.config)
      if (!t.valid()) return
      const selected = state.conversations.find(c => c.conversation_id === state.selectedId)
      const rows = config.cursor ? [...state.conversations, ...page.items] : page.items
      state.conversations = [...new Map(rows.map(row => [row.conversation_id, row])).values()]
      if (selected && !state.conversations.some(c => c.conversation_id === selected.conversation_id)) state.conversations.push(selected)
      state.paginationByConversation._list = { nextCursor: page.next_cursor }
      state.errors._list = null
    } catch (error) { if (t.valid()) state.errors._list = errorText(error); throw error }
    finally { t.finish() }
  }
  async function refreshUnread(config = {}) {
    const t = ticket('unread', false, config)
    try { const result = await api.getUnread(t.config); if (t.valid()) { state.unreadCount = result.unread_count; state.errors._unread = null } }
    catch (error) { if (t.valid()) state.errors._unread = errorText(error); throw error }
    finally { t.finish() }
  }
  return { selectConversation, loadPrevious, pullNew, sendText, retrySend, markVisibleRead, refreshConversations, refreshUnread, reset }
}
