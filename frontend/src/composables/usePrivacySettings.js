import { computed, reactive, ref } from 'vue'

const defaults = () => ({ contact_visible: 2, phone_visible: 2, email_visible: 2 })
const fields = Object.keys(defaults())
const copy = value => Object.fromEntries(fields.map(key => [key, value[key]]))
const equals = (a, b) => Boolean(a && b) && fields.every(key => a[key] === b[key])

/** Privacy state belongs to one login session, not to a token that can renew. */
export function usePrivacySettings({ getIdentity, read, update }) {
  const draft = reactive(defaults())
  const baseline = ref(null)
  const state = reactive({ owner: getIdentity(), loading: false, loaded: false, saving: false, loadError: false, saveError: false })
  let generation = 0
  let alive = true
  const owns = (owner, request) => alive && Boolean(owner) && owner === state.owner && owner === getIdentity() && request === generation
  const dirty = computed(() => state.loaded && !equals(draft, baseline.value))
  const editable = computed(() => state.loaded && !state.loading && !state.loadError && !state.saving && Boolean(state.owner))
  const canSave = computed(() => editable.value && dirty.value)

  function resetForSession() {
    generation++
    state.owner = getIdentity()
    Object.assign(state, { loading: false, loaded: false, saving: false, loadError: false, saveError: false })
    Object.assign(draft, defaults())
    baseline.value = null
  }

  async function load() {
    if (state.owner !== getIdentity()) resetForSession()
    const owner = state.owner, request = ++generation
    if (!owner || !alive) return false
    state.loading = true
    state.loadError = false
    state.loaded = false
    try {
      const response = await read()
      if (!owns(owner, request)) return false
      if (!response?.data) throw new Error('隐私设置响应不完整')
      const data = response.data
      const values = {
        contact_visible: data.contact_visible ?? data.contactVisible ?? 2,
        phone_visible: data.phone_visible ?? data.phoneVisible ?? 2,
        email_visible: data.email_visible ?? data.emailVisible ?? 2
      }
      if (![1, 2, 3].includes(values.contact_visible) || ![1, 2].includes(values.phone_visible) || ![1, 2].includes(values.email_visible)) throw new Error('隐私设置无效')
      Object.assign(draft, values)
      baseline.value = copy(values)
      state.loaded = true
      return true
    } catch {
      if (owns(owner, request)) state.loadError = true
      return false
    } finally { if (owns(owner, request)) state.loading = false }
  }

  async function save() {
    if (state.owner !== getIdentity()) { resetForSession(); return false }
    if (!canSave.value || !alive) return false
    const owner = state.owner, request = generation, values = copy(draft)
    state.saving = true
    state.saveError = false
    try {
      await update(values)
      if (!owns(owner, request)) return false
      baseline.value = values
      return true
    } catch {
      if (owns(owner, request)) state.saveError = true
      return false
    } finally { if (owns(owner, request)) state.saving = false }
  }

  function discard() {
    if (state.owner === getIdentity() && baseline.value && !state.saving) {
      Object.assign(draft, baseline.value)
      state.saveError = false
    }
  }
  function needsLeaveConfirmation() { return alive && state.owner === getIdentity() && Boolean(state.owner) && dirty.value }
  function dispose() { resetForSession(); alive = false }
  return { draft, state, dirty, editable, canSave, load, save, discard, resetForSession, needsLeaveConfirmation, dispose }
}
