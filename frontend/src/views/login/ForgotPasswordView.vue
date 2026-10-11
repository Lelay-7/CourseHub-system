<template>
  <main class="recovery-page">
    <PageContainer>
      <PageHeader title="找回密码" subtitle="使用已验证的找回邮箱重设密码" />
      <Card>
        <p class="recovery-hint">请输入登录账号和已绑定的找回邮箱。个人资料中的联系邮箱不能直接用于找回。</p>
        <p class="recovery-hint">如果尚未绑定找回邮箱，请先使用原密码登录，再到“我的 → 账号安全”完成绑定。</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="!!busy" @submit.prevent="resetPassword">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model.trim="form.username" autocomplete="username" maxlength="20" />
          </el-form-item>
          <el-form-item label="已绑定的找回邮箱" prop="email">
            <el-input v-model.trim="form.email" type="email" autocomplete="email" maxlength="254" />
          </el-form-item>
          <el-form-item label="邮箱验证码" prop="code">
            <div class="code-row">
              <el-input v-model.trim="form.code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="六位验证码" />
              <el-button :loading="busy === 'send'" :disabled="cooldown > 0" native-type="button" @click="sendCode">
                {{ cooldown > 0 ? `${cooldown}秒后重发` : '申请验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <p v-if="requestMessage" class="recovery-hint" role="status">{{ requestMessage }}</p>
          <el-form-item label="新密码" prop="new_password">
            <el-input v-model="form.new_password" type="password" show-password autocomplete="new-password" maxlength="20" placeholder="6–20个字符" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm_password">
            <el-input v-model="form.confirm_password" type="password" show-password autocomplete="new-password" maxlength="20" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="busy === 'reset'" :disabled="!requestId">重设密码</el-button>
          <router-link class="back-login" to="/login">返回登录</router-link>
        </el-form>
      </Card>
    </PageContainer>
  </main>
</template>

<script setup>
import { ref, reactive, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import { formRules } from '@/utils/validator'
import { getAuthSessionId } from '@/utils/auth'
import * as securityApi from '@/api/security'

const router = useRouter()
const ownerIdentity = getAuthSessionId()
let alive = true
const current = () => alive && ownerIdentity === getAuthSessionId()
const formRef = ref(null)
const busy = ref('')
const requestId = ref('')
const requestMessage = ref('')
const cooldown = ref(0)
let timer
const form = reactive({ username: '', email: '', code: '', new_password: '', confirm_password: '' })
const rules = {
  username: [{ required: true, min: 3, max: 20, message: '请输入3–20个字符的登录账号', trigger: 'blur' }],
  email: [{ required: true, message: '请输入找回邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  code: [{ required: true, pattern: /^\d{6}$/, message: '请输入六位验证码', trigger: 'blur' }],
  new_password: formRules.password,
  confirm_password: [{ validator: (_, value, done) => done(value && value === form.new_password ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }]
}
watch(() => [form.username, form.email], () => { requestId.value = ''; requestMessage.value = ''; form.code = '' })
function startCooldown() {
  clearInterval(timer)
  cooldown.value = 60
  timer = setInterval(() => { if (--cooldown.value <= 0) clearInterval(timer) }, 1000)
}
async function sendCode() {
  if (busy.value || cooldown.value > 0 || !current()) return
  busy.value = 'send'
  try {
    await formRef.value.validateField(['username', 'email'])
    if (!current()) return
    requestId.value = ''
    const payload = { username: form.username, email: form.email }
    const res = await securityApi.sendResetCode(payload)
    if (!current() || form.username !== payload.username || form.email !== payload.email) return
    requestId.value = res.data.request_id
    requestMessage.value = res.data.message
    startCooldown()
  } catch { /* Preserve form; request layer reports errors. */ }
  finally { if (current()) busy.value = '' }
}
async function resetPassword() {
  if (busy.value || !requestId.value || !current()) return
  busy.value = 'reset'
  try {
    await formRef.value.validate()
    if (!current()) return
    await securityApi.resetPassword({ username: form.username, email: form.email, code: form.code,
      request_id: requestId.value, new_password: form.new_password })
    if (!current()) return
    requestId.value = ''
    form.new_password = ''
    form.confirm_password = ''
    ElMessage.success('密码已重设，请使用新密码登录')
    router.replace('/login')
  } catch { /* Preserve form after a rejected code or network error. */ }
  finally { if (current()) busy.value = '' }
}
onBeforeUnmount(() => { alive = false; clearInterval(timer) })
</script>

<style scoped>
.recovery-page { min-height: 100vh; padding: 32px 0; background: var(--color-bg-page); }
.recovery-page :deep(.page-container) { max-width: 600px; }
.recovery-page :deep(.ui-card) { padding: var(--spacing-lg); }
.recovery-hint { color: var(--color-text-secondary); line-height: 1.7; margin-bottom: var(--spacing-md); }
.code-row { display: flex; width: 100%; gap: var(--spacing-sm); }
.code-row :deep(.el-input) { flex: 1; min-width: 0; }
.back-login { display: inline-block; margin-left: var(--spacing-md); color: var(--color-primary); }
@media (max-width: 480px) { .code-row { flex-direction: column; } .recovery-page { padding-top: 16px; } }
</style>
