import assert from 'node:assert/strict'
import test from 'node:test'
import fs from 'node:fs/promises'
import vm from 'node:vm'

async function view(file, api = {}) {
  const source = await fs.readFile(new URL(`../src/views/${file}`, import.meta.url), 'utf8')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from\s+['"][^'"]+['"]\s*$/gm, '')
  const calls = [], notices = [], navigation = [], mounted = [], cleanup = []
  const state = { identity: 'account-A', logouts: 0 }
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value,
    computed: fn => ({ get value() { return fn() } }), watch() {},
    onMounted: fn => mounted.push(fn), onBeforeUnmount: fn => cleanup.push(fn),
    getAuthSessionId: () => state.identity,
    useUserStore: () => ({ userInfo: { email: 'contact@example.com' }, logout() { state.logouts++ } }),
    useRouter: () => ({ replace: target => navigation.push(target) }),
    ElMessage: { success: text => notices.push(text), warning: text => notices.push(text) },
    formRules: { password: [] }, setInterval: () => 1, clearInterval() {},
    securityApi: {
      getSecurity: async () => ({ data: { recovery_email_masked: null, recovery_email_verified: false } }),
      sendBindingCode: async data => { calls.push(data); return { data: { request_id: 'new-request' } } },
      bindEmail: async data => { calls.push(data) },
      changePassword: async data => { calls.push(data) },
      sendResetCode: async data => { calls.push(data); return { data: { request_id: 'reset-request', message: '如果账号与邮箱匹配，验证码将发送至该邮箱' } } },
      resetPassword: async data => { calls.push(data) }, ...api
    }
  })
  vm.runInContext(script, context)
  const run = expression => vm.runInContext(expression, context)
  return { run, state, calls, notices, navigation, mounted, cleanup }
}
function setupSecurity(v) {
  v.run(`bindingFormRef.value = {validate: async()=>true, validateField: async()=>true}; passwordFormRef.value = {validate: async()=>true};
    bindingForm.old_password='old-pass'; bindingForm.email='safe@example.com';
    passwordForm.old_password='old-pass'; passwordForm.new_password='new-pass'; passwordForm.confirm_password='new-pass'`)
}
test('a late password change for account A cannot log out account B', async () => {
  let complete
  const v=await view('profile/AccountSecurityView.vue', { changePassword: () => new Promise(resolve => { complete=resolve }) })
  setupSecurity(v)
  const pending=v.run('changePassword()')
  await new Promise(resolve => setImmediate(resolve))
  v.state.identity='account-B'; complete({ code:200 }); await pending
  assert.equal(v.state.logouts,0); assert.equal(v.navigation.length,0)
})
test('password change blocks repeat submissions and clears only the current session on success', async () => {
  let complete, count=0
  const v=await view('profile/AccountSecurityView.vue', { changePassword: data => { count++; return new Promise(resolve => { complete=resolve }) } })
  setupSecurity(v)
  const first=v.run('changePassword()'); const second=v.run('changePassword()')
  await new Promise(resolve => setImmediate(resolve)); assert.equal(count,1)
  complete({ code:200 }); await Promise.all([first,second])
  assert.equal(v.state.logouts,1); assert.equal(v.navigation[0],'/login')
})
test('binding failures preserve email and password and release the form', async () => {
  const v=await view('profile/AccountSecurityView.vue', { sendBindingCode: async () => { throw new Error('mail failed') } })
  setupSecurity(v); await v.run('sendBindingCode()')
  assert.equal(v.run('bindingForm.email'),'safe@example.com')
  assert.equal(v.run('bindingForm.old_password'),'old-pass')
  assert.equal(v.run('busy.value'),'')
})
test('a sent binding request is used once with snake_case fields', async () => {
  const v=await view('profile/AccountSecurityView.vue'); setupSecurity(v)
  await v.run('sendBindingCode()'); v.run("bindingForm.code='123456'"); await v.run('bindEmail()')
  assert.equal(v.calls[1].request_id,'new-request')
  assert.equal(v.calls[1].old_password,'old-pass')
  assert.equal(v.run('bindingRequestId.value'),'')
})
test('public recovery uses the returned request ID without logging in or clearing another session', async () => {
  const v=await view('login/ForgotPasswordView.vue')
  v.run(`formRef.value={validate:async()=>true,validateField:async()=>true}; form.username='student01'; form.email='safe@example.com';
    form.code='123456'; form.new_password='new-pass'; form.confirm_password='new-pass'`)
  await v.run('sendCode()'); await v.run('resetPassword()')
  assert.equal(v.calls[1].request_id,'reset-request')
  assert.equal(v.calls[1].new_password,'new-pass')
  assert.equal(v.state.logouts,0); assert.equal(v.navigation[0],'/login')
})
