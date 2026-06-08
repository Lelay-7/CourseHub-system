<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <div class="login-mark">
          <el-icon :size="34"><School /></el-icon>
        </div>
        <h1>不高山选课通</h1>
        <p>课程、资料、通讯录和积分激励的一站式学习入口</p>
      </div>

      <!-- Tab切换：账号密码登录 / 注册账号 -->
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

        <el-tab-pane label="注册账号" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input
                v-model="registerForm.nickname"
                placeholder="请输入昵称"
                :prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                style="width: 100%"
                @click="handleRegister"
              >
                注册并进入
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="demo-hint">
        演示模式：账号任意填写，密码不少于 6 位即可进入。
      </div>
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
import { User, Lock, School } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { formRules } from '@/utils/validator'

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

// ========== 注册账号 ==========
const registerFormRef = ref(null)
const registerForm = reactive({
  username: '',
  nickname: '',
  password: ''
})
const registerRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: formRules.nickname,
  password: formRules.password
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.register({
      username: registerForm.username,
      nickname: registerForm.nickname,
      password: registerForm.password
    })
    ElMessage.success('注册成功')
    goRedirect()
  } catch { /* handled */ }
  finally { loading.value = false }
}

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
  background: transparent;
  padding: var(--spacing-md);
}

.login-container {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 250, 243, 0.94);
  border: 1px solid rgba(154, 66, 45, 0.12);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(18px);
}

.login-header {
  text-align: center;
  margin-bottom: var(--spacing-lg);
}

.login-mark {
  width: 64px;
  height: 64px;
  margin: 0 auto;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  box-shadow: 0 14px 30px rgba(154, 66, 45, 0.22);
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
  line-height: 1.6;
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
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.demo-hint {
  margin-top: var(--spacing-md);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--color-primary-dark);
  background: var(--color-bg-soft);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}
</style>
