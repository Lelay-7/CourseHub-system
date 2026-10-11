<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="账号安全" subtitle="保护账号，设置用于找回密码的邮箱" :show-back="true" />
      <div class="security-panels">
        <Card>
          <h2>找回邮箱</h2>
          <ErrorState v-if="loadError" title="账号安全信息加载失败" @retry="loadSecurity" />
          <p v-else-if="securityLoading" class="security-hint">正在读取绑定状态…</p>
          <p v-else-if="security.recovery_email_verified" class="security-hint" data-testid="recovery-status">
            已验证：{{ security.recovery_email_masked }}
          </p>
          <p v-else class="security-hint" data-testid="recovery-status">尚未绑定找回邮箱，请先完成验证，以便忘记密码时找回账号。</p>
          <p class="security-hint">找回邮箱与个人资料中的联系邮箱独立。更换后，只有新验证的邮箱可以找回密码。</p>
          <el-form ref="bindingFormRef" :model="bindingForm" :rules="bindingRules" label-position="top"
            :disabled="!!busy || securityLoading || loadError" @submit.prevent="bindEmail">
            <el-form-item label="原密码" prop="old_password">
              <el-input v-model="bindingForm.old_password" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="待验证的找回邮箱" prop="email">
              <el-input v-model.trim="bindingForm.email" type="email" autocomplete="email" maxlength="254" placeholder="输入你可以收信的邮箱" />
            </el-form-item>
            <el-form-item label="邮箱验证码" prop="code">
              <div class="code-row">
                <el-input v-model.trim="bindingForm.code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="六位验证码" />
                <el-button :loading="busy === 'send'" :disabled="cooldown > 0" native-type="button" @click="sendBindingCode">
                  {{ cooldown > 0 ? `${cooldown}秒后重发` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="busy === 'bind'" :disabled="!bindingRequestId">
              验证并保存找回邮箱
            </el-button>
          </el-form>
        </Card>
        <Card>
          <h2>修改密码</h2>
          <p class="security-hint">密码长度为6–20个字符。修改成功后，所有已登录会话都会退出，请使用新密码重新登录。</p>
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top"
            :disabled="!!busy" @submit.prevent="changePassword">
            <el-form-item label="原密码" prop="old_password">
              <el-input v-model="passwordForm.old_password" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="new_password">
              <el-input v-model="passwordForm.new_password" type="password" show-password autocomplete="new-password" maxlength="20" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirm_password">
              <el-input v-model="passwordForm.confirm_password" type="password" show-password autocomplete="new-password" maxlength="20" />
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="busy === 'password'">修改密码并重新登录</el-button>
          </el-form>
        </Card>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useUserStore } from '@/stores/user'
import { getAuthSessionId } from '@/utils/auth'
import { formRules } from '@/utils/validator'
import * as securityApi from '@/api/security'

const router = useRouter()
const userStore = useUserStore()
const ownerIdentity = getAuthSessionId()
let alive = true
const ownsSession = () => alive && ownerIdentity === getAuthSessionId()
const busy = ref('')
const securityLoading = ref(true)
const loadError = ref(false)
const security = reactive({ recovery_email_masked: null, recovery_email_verified: false })
const bindingFormRef = ref(null)
const passwordFormRef = ref(null)
const bindingRequestId = ref('')
const cooldown = ref(0)
let timer
const bindingForm = reactive({ old_password: '', email: userStore.userInfo?.email || '', code: '' })
const passwordForm = reactive({ old_password: '', new_password: '', confirm_password: '' })
const bindingRules = {
  old_password: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  email: [{ required: true, message: '请输入找回邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  code: [{ required: true, pattern: /^\d{6}$/, message: '请输入六位验证码', trigger: 'blur' }]
}
const passwordRules = {
  old_password: bindingRules.old_password,
  new_password: formRules.password,
  confirm_password: [{ validator: (_, value, done) => done(value && value === passwordForm.new_password ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }]
}
watch(() => bindingForm.email, () => { bindingRequestId.value = ''; bindingForm.code = '' })
function startCooldown() {
  clearInterval(timer)
  cooldown.value = 60
  timer = setInterval(() => { if (--cooldown.value <= 0) clearInterval(timer) }, 1000)
}
async function loadSecurity() {
  if (!ownsSession()) return
  securityLoading.value = true
  loadError.value = false
  try {
    const res = await securityApi.getSecurity()
    if (ownsSession()) Object.assign(security, res.data)
  } catch { if (ownsSession()) loadError.value = true }
  finally { if (ownsSession()) securityLoading.value = false }
}
async function sendBindingCode() {
  if (busy.value || cooldown.value > 0 || !ownsSession()) return
  busy.value = 'send'
  try {
    await bindingFormRef.value.validateField(['old_password', 'email'])
    if (!ownsSession()) return
    bindingRequestId.value = ''
    const payload = { old_password: bindingForm.old_password, email: bindingForm.email }
    const res = await securityApi.sendBindingCode(payload)
    if (!ownsSession() || bindingForm.email !== payload.email) return
    bindingRequestId.value = res.data.request_id
    startCooldown()
    ElMessage.success('验证码已提交发送，请查看邮箱')
  } catch { /* Validation and request layer report errors; preserve the form for retry. */ }
  finally { if (ownsSession()) busy.value = '' }
}
async function bindEmail() {
  if (busy.value || !bindingRequestId.value || !ownsSession()) return
  busy.value = 'bind'
  try {
    await bindingFormRef.value.validate()
    if (!ownsSession()) return
    await securityApi.bindEmail({ ...bindingForm, request_id: bindingRequestId.value })
    if (!ownsSession()) return
    bindingRequestId.value = ''
    bindingForm.old_password = ''
    bindingForm.code = ''
    ElMessage.success('找回邮箱已验证并保存')
    await loadSecurity()
  } catch { /* Keep input after failures. */ }
  finally { if (ownsSession()) busy.value = '' }
}
async function changePassword() {
  if (busy.value || !ownsSession()) return
  busy.value = 'password'
  try {
    await passwordFormRef.value.validate()
    if (!ownsSession()) return
    await securityApi.changePassword({ old_password: passwordForm.old_password, new_password: passwordForm.new_password })
    if (!ownsSession()) return
    userStore.logout()
    ElMessage.success('密码已修改，请使用新密码登录')
    router.replace('/login')
  } catch { /* Keep input after failures. */ }
  finally { if (ownsSession()) busy.value = '' }
}
onMounted(loadSecurity)
onBeforeUnmount(() => { alive = false; clearInterval(timer) })
</script>

<style scoped>
.security-panels { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--spacing-lg); }
.security-panels :deep(.ui-card) { padding: var(--spacing-lg); }
h2 { margin: 0 0 var(--spacing-md); font-size: var(--font-size-lg); color: var(--color-text-primary); }
.security-hint { color: var(--color-text-secondary); line-height: 1.7; margin-bottom: var(--spacing-md); }
.code-row { display: flex; width: 100%; gap: var(--spacing-sm); }
.code-row :deep(.el-input) { min-width: 0; flex: 1; }
@media (max-width: 768px) {
  .security-panels { grid-template-columns: 1fr; }
  .code-row { flex-direction: column; }
  .el-button { max-width: 100%; white-space: normal; }
}
</style>
