<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <el-icon :size="48" color="#409EFF"><School /></el-icon>
        <h1>不高山选课通</h1>
        <p>高校学习服务应用</p>
      </div>

      <!-- Tab切换：账号密码 / 手机验证码 -->
      <el-tabs v-model="loginMode" class="login-tabs" stretch>
        <el-tab-pane label="账号密码登录" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="passwordForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="passwordForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handlePasswordLogin"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                style="width: 100%"
                @click="handlePasswordLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="手机验证码登录" name="sms">
          <el-form
            ref="smsFormRef"
            :model="smsForm"
            :rules="smsRules"
            label-position="top"
          >
            <el-form-item label="手机号" prop="phone">
              <el-input
                v-model="smsForm.phone"
                placeholder="请输入手机号"
                :prefix-icon="Phone"
                size="large"
              />
            </el-form-item>
            <el-form-item label="验证码" prop="code">
              <div class="sms-row">
                <el-input
                  v-model="smsForm.code"
                  placeholder="请输入验证码"
                  :prefix-icon="Message"
                  size="large"
                />
                <el-button
                  :disabled="smsCountdown > 0"
                  size="large"
                  @click="sendSms"
                >
                  {{ smsCountdown > 0 ? `${smsCountdown}s后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                style="width: 100%"
                @click="handleSmsLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="login-footer">
      <span>© 2026 不高山选课通 V{{ appVersion }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Phone, Message, School } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { formRules } from '@/utils/validator'
import * as authApi from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const appVersion = import.meta.env.VITE_APP_VERSION || '3.0.0'
const loginMode = ref('password')
const loading = ref(false)

// ========== 账号密码登录 ==========
const passwordFormRef = ref(null)
const passwordForm = reactive({
  username: '',
  password: ''
})
const passwordRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: formRules.password
}

async function handlePasswordLogin() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginByPassword({
      username: passwordForm.username,
      password: passwordForm.password
    })
    ElMessage.success('登录成功')
    goRedirect()
  } catch {
    // axios 拦截器已处理错误提示
  } finally {
    loading.value = false
  }
}

// ========== 手机验证码登录 ==========
const smsFormRef = ref(null)
const smsForm = reactive({ phone: '', code: '' })
const smsCountdown = ref(0)
const smsRules = {
  phone: formRules.phone,
  code: formRules.smsCode
}

let countdownTimer = null
async function sendSms() {
  // 简单校验手机号
  if (!/^1[3-9]\d{9}$/.test(smsForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  try {
    await authApi.sendSmsCode(smsForm.phone)
    ElMessage.success('验证码已发送')
    // 60秒倒计时
    smsCountdown.value = 60
    countdownTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
  } catch {
    // 错误已由拦截器处理
  }
}

async function handleSmsLogin() {
  const valid = await smsFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginBySms({
      phone: smsForm.phone,
      code: smsForm.code
    })
    ElMessage.success('登录成功')
    goRedirect()
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

/** 登录成功后跳转 */
function goRedirect() {
  const redirect = route.query.redirect || '/home'
  router.replace(redirect)
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: var(--spacing-md);
}

.login-container {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-lg);
}

.login-header {
  text-align: center;
  margin-bottom: var(--spacing-lg);
}
.login-header h1 {
  margin: var(--spacing-sm) 0 4px;
  font-size: var(--font-size-xxl);
  color: var(--color-text-primary);
}
.login-header p {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.login-tabs {
  margin-top: var(--spacing-md);
}

.sms-row {
  display: flex;
  gap: var(--spacing-sm);
  width: 100%;
}
.sms-row .el-button {
  flex-shrink: 0;
}

.login-footer {
  margin-top: var(--spacing-xl);
  color: rgba(255, 255, 255, 0.7);
  font-size: var(--font-size-xs);
}
</style>
