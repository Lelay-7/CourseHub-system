import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import vm from 'node:vm'
import { usePrivacySettings } from '../src/composables/usePrivacySettings.js'

async function loadView(relativePath, overrides = {}) {
  const source = await fs.readFile(new URL(relativePath, import.meta.url), 'utf8')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from\s+['"][^'"]+['"]\s*$/gm, '')
  const mounted = []
  const messages = []
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value,
    computed: callback => ({ get value() { return callback() } }),
    onMounted: callback => mounted.push(callback), onUnmounted: () => {}, onBeforeUnmount: () => {}, watch: () => {},
    onBeforeRouteLeave: () => {}, nextTick: async () => {},
    useUserStore: () => ({ userInfo: { user_id: 1 }, isAdmin: false, isTeacher: false }),
    getAuthSessionId: () => 'contact-test-A', subscribeAuthSessionChange: () => () => {},
    usePrivacySettings,
    getSecurity: async () => ({ data: { recovery_email_verified: false } }),
    useRoute: () => ({ query: {}, params: {} }),
    useRouter: () => ({ push: async () => {} }),
    ElMessage: { success: text => messages.push(text), error: text => messages.push(text), warning: () => {} },
    ElMessageBox: { confirm: async () => {} },
    window: { addEventListener: () => {}, removeEventListener: () => {} },
    document: { getElementById: () => null },
    localStorage: { getItem: () => null, setItem: () => {} },
    getCacheSizeFormatted: () => '0 B',
    setTimeout, clearTimeout, console,
    ...overrides
  })
  vm.runInContext(script, context)
  return { context, mounted, get: expression => vm.runInContext(expression, context) }
}

// Runs the actual SettingsView script against the live backend JSON contract.
const writes = []
const settings = await loadView('../src/views/profile/SettingsView.vue', {
  getMyAuth: async () => ({ code: 200, data: { phone_visible: 2, email_visible: 1, contact_visible: 3 } }),
  updateMyAuth: async payload => writes.push(JSON.parse(JSON.stringify(payload)))
})
await settings.get('load()')
assert.deepEqual(JSON.parse(settings.get('JSON.stringify(draft)')), { contact_visible: 3, phone_visible: 2, email_visible: 1 },
  'Settings must load all three backend privacy values without replacing them with defaults')
await settings.get('savePrivacy()')
assert.equal(writes.length, 0, 'An unchanged privacy section must not issue a save')
settings.get('draft.contact_visible = 2')
await settings.get('savePrivacy()')
assert.deepEqual(writes[0], { contact_visible: 2, phone_visible: 2, email_visible: 1 },
  'The independent privacy save must use snake_case fields and preserve the untouched phone/email choices')

const pending = []
const search = await loadView('../src/views/contact/ContactListView.vue', {
  request: { get: () => new Promise(resolve => pending.push(resolve)) }
})
const first = search.get('doSearch("first")')
const second = search.get('doSearch("second")')
pending[1]({ data: [{ user_id: 2, nickname: 'second' }] })
await second
pending[0]({ data: [{ user_id: 1, nickname: 'first' }] })
await first
assert.equal(search.get('searchResults.value[0].user_id'), 2,
  'An older contact search response must not replace the newest results')

const cleared = search.get('doSearch("before-clear")')
search.get('keyword.value = ""; onSearchInput()')
pending[2]({ data: [{ user_id: 3 }] })
await cleared
assert.equal(search.get('searchResults.value.length'), 0,
  'Clearing contact search must also invalidate a request already in flight')

console.log('Contact privacy payload and search race regressions passed')
