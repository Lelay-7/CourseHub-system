import assert from 'node:assert/strict'
import test from 'node:test'
import { build } from 'esbuild'
import { mkdirSync, unlinkSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'

const root = resolve(import.meta.dirname, '..')
const auth = await import(pathToFileURL(resolve(root, 'src/utils/auth.js')).href)
let sequence = 0
const jwt = seconds => `e30.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds })).toString('base64url')}.signature`

test('auth subscribers clear synchronously on logout and new identity but not refresh', () => {
  fixture(jwt(7200))
  const initial = auth.getAuthSessionId()
  const changes = []
  const unsubscribe = auth.subscribeAuthSessionChange(id => changes.push(id))
  auth.saveTokens(jwt(8000),jwt(16000))
  assert.equal(auth.getAuthSessionId(), initial)
  assert.deepEqual(changes, [])
  auth.clearTokens()
  assert.deepEqual(changes, [null])
  auth.saveTokens(jwt(7200),jwt(14400))
  assert.equal(changes.length,2)
  assert.notEqual(changes[1],initial)
  unsubscribe()
})
test('cross-tab identity replacement notifies while token renewal keeps chat identity', () => {
  fixture(jwt(7200))
  const events=new EventTarget();globalThis.window=events
  const changes=[];const unsubscribe=auth.subscribeAuthSessionChange(id=>changes.push(id))
  const event=key=>{const value=new Event('storage');value.key=key;events.dispatchEvent(value)}
  localStorage.setItem('access_token',jwt(8000));event('access_token');assert.deepEqual(changes,[])
  localStorage.setItem('auth_session_id','another-tab-session');event('auth_session_id');assert.deepEqual(changes,['another-tab-session'])
  auth.clearTokens();assert.equal(changes.at(-1),null);unsubscribe();delete globalThis.window
})

test('silent background failure has no toast while unauthorized still clears credentials', async () => {
  const context = fixture(jwt(7200))
  context.adapter = async config => { throw Object.assign(new Error('offline'),{config}) }
  const { default: request } = await load('src/api/request.js')
  await assert.rejects(request.get('/api/chat/unread',{silentError:true}))
  assert.deepEqual(context.notices,[])
  context.adapter = async config => { throw Object.assign(new Error('unauthorized'),{config,response:{status:401,data:{message:'expired'}}}) }
  await assert.rejects(request.get('/api/chat/unread',{silentError:true}))
  assert.equal(localStorage.getItem('access_token'),null)
  assert.equal(context.navigation.at(-1),'/login')
})

async function load(entry, env = {}) {
  const target = resolve(root, `output/playwright/auth-test-${process.pid}-${++sequence}.mjs`)
  mkdirSync(resolve(root, 'output/playwright'), { recursive: true })
  await build({
    entryPoints: [resolve(root, entry)], outfile: target, bundle: true,
    platform: 'node', format: 'esm', packages: 'external',
    define: { 'import.meta.env': JSON.stringify({ VITE_API_BASE_URL: '/api', VITE_USE_MOCK: 'true', ...env }) },
    plugins: [{ name: 'controlled-boundaries', setup(builder) {
      builder.onResolve({ filter: /^@\/|^element-plus$|^\.\.?\/.*mock$/ }, ({ path }) => {
        if (path === '../api/mock') return { path: './mock', namespace: 'fixture' }
        if (['element-plus', '@/router', '@/stores/user', '@/stores/selection', '@/api/auth', './mock'].includes(path)) return { path, namespace: 'fixture' }
        if (path.startsWith('@/')) return { path: resolve(root, 'src', path.slice(2) + '.js') }
      })
      builder.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => {
        const sources = {
          'element-plus': 'export const ElMessage = {error: message => globalThis.__authTest.notices.push(message)}',
          '@/router': 'export default {currentRoute:{value:{path:"/home",fullPath:"/home"}},push: target => globalThis.__authTest.navigation.push(target)}',
          '@/stores/user': 'export const useUserStore = () => globalThis.__authTest.store',
          '@/stores/selection': 'export const useSelectionStore = () => ({setCurrentUser(){}})',
          '@/api/auth': 'export const loginByPassword = data => globalThis.__authTest.login(data); export const loginBySms = loginByPassword; export const register = loginByPassword; export const getUserInfo = () => globalThis.__authTest.userInfo()',
          './mock': 'export const mockAdapter = config => globalThis.__authTest.adapter(config)'
        }
        return { contents: sources[path], loader: 'js' }
      })
    }}]
  })
  try { return await import(pathToFileURL(target).href) } finally { unlinkSync(target) }
}

function fixture(access = jwt(-600), refresh = jwt(3600)) {
  const values = new Map([['access_token', access], ['refresh_token', refresh]])
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key), key: index => [...values.keys()][index], get length() { return values.size }
  }
  const context = globalThis.__authTest = { calls: [], notices: [], navigation: [], store: { logout() {}, accessToken: access, refreshToken: refresh } }
  context.adapter = async config => {
    context.calls.push(config)
    const path = config.url.replace(/^\/api/, '')
    const data = path === '/auth/refresh'
      ? { accessToken: jwt(7200), refreshToken: jwt(14400) }
      : { result: 'ok' }
    return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
  }
  return context
}

async function bounded(promise) {
  let timer
  try {
    return await Promise.race([promise, new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error('request stalled while refreshing its own session')), 800)
    })])
  } finally { clearTimeout(timer) }
}

test('expired production requests share one refresh and both complete', async () => {
  const context = fixture()
  const { default: request } = await load('src/api/request.js')
  const responses = await bounded(Promise.all([request.get('/api/course/list'), request.get('/api/material/list')]))
  assert.equal(responses.length, 2)
  assert.equal(context.calls.filter(call => call.url.endsWith('/auth/refresh')).length, 1)
  assert.ok(context.calls.filter(call => !call.url.endsWith('/auth/refresh')).every(call => call.headers.Authorization === `Bearer ${localStorage.getItem('access_token')}`))
})

test('a new password login bypasses an obsolete session', async () => {
  const context = fixture()
  const { default: request } = await load('src/api/request.js')
  await bounded(request.post('/api/auth/login', { username: 'student01', password: '123456' }))
  assert.equal(context.calls.length, 1)
  assert.equal(context.calls[0].url, '/auth/login')
  assert.equal(context.calls[0].headers.Authorization, undefined)
})

test('public password recovery bypasses expired credentials and never refreshes', async () => {
  const context = fixture()
  const { default: request } = await load('src/api/request.js')
  await bounded(request.post('/api/auth/password-reset/code', { username: 'student01', email: 'safe@example.com' }))
  await bounded(request.post('/api/auth/password-reset', { request_id: 'request', code: '123456', new_password: 'new-pass' }))
  assert.equal(context.calls.length, 2)
  assert.ok(context.calls.every(call => call.headers.Authorization === undefined))
  assert.ok(context.calls.every(call => !call.url.endsWith('/auth/refresh')))
})

test('a rejected refresh fails promptly and clears the expired session', async () => {
  const context = fixture()
  context.adapter = async config => {
    context.calls.push(config)
    throw Object.assign(new Error('refresh rejected'), { config, response: { status: 401, data: { message: 'invalid refresh' } } })
  }
  const { default: request } = await load('src/api/request.js')
  await assert.rejects(bounded(request.get('/api/course/list')), /refresh rejected/)
  assert.equal(context.calls.length, 1)
  assert.equal(localStorage.getItem('access_token'), null)
  assert.equal(localStorage.getItem('refresh_token'), null)
})

for (const stage of ['expired access','rejected access']) for (const failure of ['offline','timeout','server']) {
  test(`temporary ${failure} refresh failure after ${stage} preserves identity and allows retry`,async()=>{
    const context=fixture(stage==='expired access'?jwt(-600):jwt(7200));const identity=auth.getAuthSessionId()
    const access=localStorage.getItem('access_token'),refresh=localStorage.getItem('refresh_token'),normal=context.adapter
    let failing=true
    context.adapter=async config=>{
      if(config.url.endsWith('/auth/refresh')&&failing) throw Object.assign(new Error(failure),{config,...(failure==='timeout'?{code:'ECONNABORTED'}:{}),...(failure==='server'?{response:{status:503,data:{message:'unavailable'}}}:{})})
      if(!config.url.endsWith('/auth/refresh')&&stage==='rejected access'&&failing) throw Object.assign(new Error('unauthorized'),{config,response:{status:401,data:{}}})
      return normal(config)
    }
    const {default:request}=await load('src/api/request.js')
    await assert.rejects(bounded(request.get('/api/chat/unread',{silentError:true})))
    assert.equal(localStorage.getItem('access_token'),access);assert.equal(localStorage.getItem('refresh_token'),refresh)
    assert.equal(auth.getAuthSessionId(),identity);assert.deepEqual(context.navigation,[]);assert.deepEqual(context.notices,[])
    failing=false;await bounded(request.get('/api/chat/unread',{silentError:true}));assert.equal(auth.getAuthSessionId(),identity)
  })
}

test('business-code rejected refresh still clears credentials for password invalidation',async()=>{
  const context=fixture();context.adapter=async config=>({config,status:200,headers:{},data:{code:401,message:'登录已失效'}})
  const {default:request}=await load('src/api/request.js');await assert.rejects(request.get('/api/chat/unread',{silentError:true}))
  assert.equal(localStorage.getItem('access_token'),null);assert.equal(localStorage.getItem('refresh_token'),null);assert.equal(context.navigation.at(-1),'/login')
})
test('navigation session restoration preserves credentials on a temporary refresh failure',async()=>{
  const context=fixture();const identity=auth.getAuthSessionId();context.adapter=async config=>{throw Object.assign(new Error('offline'),{config})}
  const api=await load('src/api/request.js');await assert.rejects(api.ensureSession(),/offline/);assert.equal(auth.getAuthSessionId(),identity)
})

test('a valid refresh token restores the session used by navigation', async () => {
  fixture()
  const api = await load('src/api/request.js')
  assert.equal(typeof api.ensureSession, 'function', 'request and router must share session restoration')
  assert.equal(await bounded(api.ensureSession()), true)
  assert.ok(localStorage.getItem('access_token'))
})

test('an empty development base URL also renews expired requests', async () => {
  const context = fixture()
  const { default: request } = await load('src/api/request.js', { VITE_API_BASE_URL: '' })
  await bounded(request.get('/api/course/list'))
  assert.equal(context.calls[0].url, '/api/auth/refresh')
  assert.equal(context.calls[1].url, '/api/course/list')
})

test('a server-rejected access token renews and retries the original request once', async () => {
  const context = fixture(jwt(7200))
  const adapter = context.adapter
  let requests = 0
  context.adapter = async config => {
    if (config.url.endsWith('/course/list') && requests++ === 0) {
      context.calls.push(config)
      throw Object.assign(new Error('unauthorized'), { config, response: { status: 401, data: { code: 401 } } })
    }
    return adapter(config)
  }
  const { default: request } = await load('src/api/request.js')
  const result = await bounded(request.get('/api/course/list'))
  assert.equal(result.data.result, 'ok')
  assert.equal(context.calls.filter(call => call.url.endsWith('/auth/refresh')).length, 1)
  assert.equal(requests, 2)
})

test('an old refresh response cannot overwrite a different logged-in account', async () => {
  const context = fixture()
  let deliver
  context.adapter = config => new Promise(resolve => { deliver = data => resolve({ status: 200, headers: {}, config, data }) })
  const { default: request } = await load('src/api/request.js')
  const pending = request.get('/api/course/list')
  await new Promise(resolve => setTimeout(resolve, 0))
  const nextAccess = jwt(9000)
  const nextRefresh = jwt(18000)
  localStorage.setItem('access_token', nextAccess)
  localStorage.setItem('refresh_token', nextRefresh)
  deliver({ code: 200, data: { accessToken: jwt(3600), refreshToken: jwt(7200) } })
  await assert.rejects(bounded(pending), /登录状态已经改变/)
  assert.equal(localStorage.getItem('access_token'), nextAccess)
  assert.equal(localStorage.getItem('refresh_token'), nextRefresh)
})

test('a previous accounts rejected write cannot be retried as the new account', async () => {
  const context = fixture(jwt(7200))
  let rejectRequest
  let original
  context.adapter = config => {
    context.calls.push(config)
    original ||= config
    return new Promise((_, reject) => { rejectRequest = reject })
  }
  const { default: request } = await load('src/api/request.js')
  const pending = request.put('/api/user/profile', { nickname: 'old-account-edit' })
  await new Promise(resolve => setTimeout(resolve, 0))
  auth.clearTokens()
  const nextAccess = jwt(9000)
  auth.saveTokens(nextAccess, jwt(18000))
  rejectRequest(Object.assign(new Error('old unauthorized'), { config: original, response: { status: 401, data: {} } }))
  await assert.rejects(bounded(pending), /登录状态已经改变/)
  assert.equal(context.calls.length, 1)
  assert.equal(localStorage.getItem('access_token'), nextAccess)
})

test('a previous accounts successful response cannot populate the new account', async () => {
  fixture(jwt(7200))
  let deliver
  let original
  globalThis.__authTest.adapter = config => {
    original = config
    return new Promise(resolve => { deliver = resolve })
  }
  const { default: request } = await load('src/api/request.js')
  const pending = request.get('/api/user/profile')
  await new Promise(resolve => setTimeout(resolve, 0))
  auth.clearTokens()
  auth.saveTokens(jwt(9000), jwt(18000))
  deliver({ config: original, status: 200, headers: {}, data: { code: 200, data: { nickname: 'previous' } } })
  await assert.rejects(bounded(pending), /登录状态已经改变/)
})

test('a previous accounts failed refresh cannot clear the new session', async () => {
  fixture()
  let rejectRefresh
  let original
  globalThis.__authTest.adapter = config => {
    original = config
    return new Promise((_, reject) => { rejectRefresh = reject })
  }
  const { default: request } = await load('src/api/request.js')
  const pending = request.get('/api/course/list')
  await new Promise(resolve => setTimeout(resolve, 0))
  auth.clearTokens()
  const nextAccess = jwt(9000)
  auth.saveTokens(nextAccess, jwt(18000))
  rejectRefresh(Object.assign(new Error('old refresh rejected'), { config: original, response: { status: 401, data: {} } }))
  await assert.rejects(bounded(pending), /登录状态已经改变/)
  assert.equal(localStorage.getItem('access_token'), nextAccess)
})

test('failed userinfo during a new login cannot reuse the previous user', async () => {
  const context = fixture(jwt(7200))
  localStorage.setItem('user_info', JSON.stringify({ user_id: 1, nickname: 'previous' }))
  context.login = async () => ({ code: 200, data: { accessToken: jwt(8000), refreshToken: jwt(16000) } })
  context.userInfo = async () => { throw new Error('userinfo unavailable') }
  setActivePinia(createPinia())
  const { useUserStore } = await load('src/stores/user.js')
  const store = useUserStore()
  await assert.rejects(store.loginByPassword({ username: 'nextuser', password: '123456' }))
  assert.equal(store.userInfo, null)
  assert.equal(localStorage.getItem('access_token'), null)
})

test('a delayed userinfo response cannot restore a logged-out user', async () => {
  const context = fixture(jwt(7200))
  let deliver
  context.userInfo = () => new Promise(resolve => { deliver = resolve })
  setActivePinia(createPinia())
  const { useUserStore } = await load('src/stores/user.js')
  const store = useUserStore()
  const pending = store.fetchUserInfo()
  store.logout()
  deliver({ code: 200, data: { user_id: 1, nickname: 'previous' } })
  await pending
  assert.equal(store.userInfo, null)
  assert.equal(localStorage.getItem('user_info'), null)
})
