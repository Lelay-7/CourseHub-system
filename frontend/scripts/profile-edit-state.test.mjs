import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

async function view(update) {
  const source = await readFile(new URL('../src/views/profile/EditProfileView.vue', import.meta.url), 'utf8')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?from\s+['"][^'"]+['"]\s*$/gm, '')
  const state = { identity: 'A', writes: [], notices: [] }, mounted = []
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, onMounted: fn => mounted.push(fn), onBeforeUnmount() {},
    getAuthSessionId: () => state.identity,
    useUserStore: () => ({ userInfo: { nickname: '旧昵称' }, updateUserInfo: value => state.writes.push({ ...value }) }),
    request: { put: update }, authApi: {},
    ElMessage: { success: message => state.notices.push(message), warning() {} }
  })
  vm.runInContext(script, context)
  mounted.forEach(fn => fn())
  return { state, run: expression => vm.runInContext(expression, context) }
}

test('profile save failure leaves typed fields editable without an unhandled rejection', async () => {
  const v = await view(async () => { throw Error('offline') })
  v.run("form.nickname = '未保存昵称'")
  await assert.doesNotReject(v.run('handleSave()'))
  assert.equal(v.run('form.nickname'), '未保存昵称')
  assert.equal(v.run('saving.value'), false)
  assert.equal(v.state.notices.length, 0)
})

test('profile save for A cannot update the local profile of B', async () => {
  let resolve
  const v = await view(() => new Promise(done => { resolve = done }))
  v.run("form.nickname = '账号A修改'")
  const pending = v.run('handleSave()')
  v.state.identity = 'B'; resolve({ code: 200 }); await pending
  assert.equal(v.state.writes.length, 0)
  assert.equal(v.state.notices.length, 0)
})

test('profile save sends one immutable snapshot while a request is pending', async () => {
  const calls = []; let resolve
  const v = await view((path, data) => { calls.push(data); return new Promise(done => { resolve = done }) })
  v.run("form.nickname = '提交昵称'")
  const one = v.run('handleSave()'), two = v.run('handleSave()')
  v.run("form.nickname = '后续修改'")
  assert.equal(calls.length, 1)
  assert.equal(calls[0].nickname, '提交昵称')
  resolve({ code: 200 }); await Promise.all([one, two])
  assert.equal(v.state.writes[0].nickname, '提交昵称')
})
