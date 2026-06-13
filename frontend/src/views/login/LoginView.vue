<template>
  <main
    ref="pageRef"
    class="login-page"
    @mousemove="handleMouseMove"
    @mouseleave="handleMouseLeave"
  >
    <div class="ambient-layer" aria-hidden="true">
      <span class="bg-halo bg-halo--one" :style="haloStyle(0.65)" />
      <span class="bg-halo bg-halo--two" :style="haloStyle(-0.35)" />
      <span class="bg-halo bg-halo--three" :style="haloStyle(0.25)" />
      <span v-for="dot in dots" :key="dot.id" class="soft-dot" :style="dot.style" />

      <svg class="campus-sketch" viewBox="0 0 620 220" fill="none">
        <path d="M36 164h548" />
        <path d="M98 164V94l58-32 58 32v70" />
        <path d="M122 164v-42h68v42" />
        <path d="M268 164V84h168v80" />
        <path d="M292 108h28M342 108h28M392 108h28M292 136h28M342 136h28M392 136h28" />
        <path d="M488 164V68h62v96" />
        <path d="M508 68c2-32 20-42 20-42s18 10 20 42" />
        <path d="M528 26v-18" />
        <path d="M56 184c76-36 164-38 258-6 86 30 164 26 250-10" />
      </svg>

      <svg class="flow-bridge" viewBox="0 0 1280 720" fill="none">
        <path class="flow-path flow-path--one" d="M454 260C604 176 742 180 898 276C976 324 1042 328 1136 274" />
        <path class="flow-path flow-path--two" d="M382 420C548 356 734 392 884 486C966 538 1048 536 1162 470" />
        <path class="flow-path flow-path--three" d="M514 536C656 506 752 552 858 612C946 662 1048 646 1168 580" />
      </svg>
    </div>

    <div class="content-row">
      <section ref="leftPanel" class="left-panel">
        <div class="brand-group">
          <p class="brand-kicker">Course Companion</p>
          <h1>不高山选课通</h1>
          <p class="brand-subtitle">课程 · 资料 · 讨论 · 通讯录 · 积分激励</p>
          <p class="brand-line">让选课更清晰，让学习更轻松</p>
        </div>

        <div class="ecosystem" :style="parallaxVars(0.24, 0.2)">
          <svg class="ecosystem-lines" viewBox="0 0 640 380" fill="none">
            <path class="eco-link eco-link--one" d="M320 190C264 140 224 96 168 76" />
            <path class="eco-link eco-link--two" d="M320 190C390 130 444 92 514 86" />
            <path class="eco-link eco-link--three" d="M320 190C238 230 174 270 112 304" />
            <path class="eco-link eco-link--four" d="M320 190C394 236 462 284 538 298" />
          </svg>

          <div class="entry-node">
            <span class="entry-pulse" />
            <el-icon><School /></el-icon>
            <strong>学习入口</strong>
            <small>一站式汇聚</small>
          </div>

          <div class="eco-orbit eco-orbit--course" :style="parallaxVars(-0.54, -0.34)">
            <article class="eco-card eco-card--a">
              <span class="eco-icon eco-icon--course"><el-icon><Reading /></el-icon></span>
              <div>
                <strong>课程</strong>
                <p>智能筛选</p>
              </div>
            </article>
          </div>

          <div class="eco-orbit eco-orbit--material" :style="parallaxVars(0.62, -0.42)">
            <article class="eco-card eco-card--b">
              <span class="eco-icon eco-icon--material"><el-icon><FolderOpened /></el-icon></span>
              <div>
                <strong>资料</strong>
                <p>高效复习</p>
              </div>
            </article>
          </div>

          <div class="eco-orbit eco-orbit--discuss" :style="parallaxVars(-0.7, 0.42)">
            <article class="eco-card eco-card--c">
              <span class="eco-icon eco-icon--discuss"><el-icon><ChatDotRound /></el-icon></span>
              <div>
                <strong>讨论</strong>
                <p>经验互助</p>
              </div>
            </article>
          </div>

          <div class="eco-orbit eco-orbit--points" :style="parallaxVars(0.48, 0.56)">
            <article class="eco-card eco-card--d">
              <span class="eco-icon eco-icon--points"><el-icon><Star /></el-icon></span>
              <div>
                <strong>积分</strong>
                <p>激励成长</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="right-panel">
        <div class="card-glow" aria-hidden="true" :style="haloStyle(0.18)" />
        <article class="login-card">
          <div class="login-card__head">
            <span class="card-mark"><el-icon><School /></el-icon></span>
            <div>
              <p>欢迎回来</p>
              <h2>登录选课通</h2>
            </div>
          </div>

          <div class="mode-tabs" :class="`mode-tabs--${loginMode}`">
            <button type="button" :class="{ active: loginMode === 'login' }" @click="loginMode = 'login'">
              登录
            </button>
            <button type="button" :class="{ active: loginMode === 'register' }" @click="loginMode = 'register'">
              注册
            </button>
            <span class="mode-tabs__slider" />
          </div>

          <transition name="form-fade" mode="out-in">
            <el-form
              v-if="loginMode === 'login'"
              ref="passwordFormRef"
              key="login"
              class="auth-form"
              :model="passwordForm"
              :rules="passwordRules"
              @keyup.enter="handlePasswordLogin"
            >
              <el-form-item prop="username">
                <el-input
                  v-model.trim="passwordForm.username"
                  size="large"
                  :prefix-icon="User"
                  placeholder="请输入用户名"
                  autocomplete="username"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="passwordForm.password"
                  size="large"
                  :prefix-icon="Lock"
                  placeholder="请输入密码"
                  type="password"
                  show-password
                  autocomplete="current-password"
                />
              </el-form-item>
              <el-button class="submit-btn" size="large" type="primary" :loading="loading" @click="handlePasswordLogin">
                登录
              </el-button>
            </el-form>

            <el-form
              v-else
              ref="registerFormRef"
              key="register"
              class="auth-form"
              :model="registerForm"
              :rules="registerRules"
              @keyup.enter="handleRegister"
            >
              <el-form-item prop="username">
                <el-input
                  v-model.trim="registerForm.username"
                  size="large"
                  :prefix-icon="User"
                  placeholder="设置用户名"
                  autocomplete="username"
                />
              </el-form-item>
              <el-form-item prop="nickname">
                <el-input
                  v-model.trim="registerForm.nickname"
                  size="large"
                  :prefix-icon="User"
                  placeholder="设置昵称"
                  autocomplete="nickname"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="registerForm.password"
                  size="large"
                  :prefix-icon="Lock"
                  placeholder="设置密码"
                  type="password"
                  show-password
                  autocomplete="new-password"
                />
              </el-form-item>
              <el-button class="submit-btn" size="large" type="primary" :loading="loading" @click="handleRegister">
                创建账号
              </el-button>
            </el-form>
          </transition>

          <div v-if="false" class="demo-note">
            演示环境可使用任意账号密码登录，系统会自动进入体验数据。
          </div>
        </article>
      </section>
    </div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  FolderOpened,
  Lock,
  Reading,
  School,
  Star,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { formRules } from '@/utils/validator'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginMode = ref('login')
const loading = ref(false)
const pageRef = ref(null)
const leftPanel = ref(null)
const mouseX = ref(0)
const mouseY = ref(0)
const isHovering = ref(false)

const passwordFormRef = ref(null)
const passwordForm = reactive({
  username: '',
  password: ''
})
const passwordRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: formRules.password
}

const registerFormRef = ref(null)
const registerForm = reactive({
  username: '',
  nickname: '',
  password: ''
})
const registerRules = {
  username: [{ required: true, message: '请设置用户名', trigger: 'blur' }],
  nickname: formRules.nickname,
  password: formRules.password
}

const dots = Array.from({ length: 16 }, (_, index) => ({
  id: index,
  style: {
    left: `${12 + ((index * 17) % 76)}%`,
    top: `${14 + ((index * 23) % 72)}%`,
    opacity: 0.12 + (index % 4) * 0.04,
    animationDelay: `${index * 0.42}s`
  }
}))

function handleMouseMove(event) {
  const rect = leftPanel.value?.getBoundingClientRect() || pageRef.value?.getBoundingClientRect()
  if (!rect) return

  isHovering.value = true
  const nextX = ((event.clientX - rect.left) / rect.width - 0.5) * 2
  const nextY = ((event.clientY - rect.top) / rect.height - 0.5) * 2
  mouseX.value = Math.max(-1, Math.min(1, nextX))
  mouseY.value = Math.max(-1, Math.min(1, nextY))
}

function handleMouseLeave() {
  isHovering.value = false
  mouseX.value = 0
  mouseY.value = 0
}

function parallaxVars(xDepth = 1, yDepth = 1) {
  return {
    '--parallax-x': `${(mouseX.value * 10 * xDepth).toFixed(2)}px`,
    '--parallax-y': `${(mouseY.value * 10 * yDepth).toFixed(2)}px`
  }
}

function haloStyle(depth = 1) {
  return {
    transform: `translate3d(${(mouseX.value * 16 * depth).toFixed(2)}px, ${(mouseY.value * 16 * depth).toFixed(2)}px, 0)`
  }
}

function redirectAfterAuth() {
  router.replace(route.query.redirect || '/home')
}

async function handlePasswordLogin() {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
    loading.value = true
    await userStore.loginByPassword({
      username: passwordForm.username,
      password: passwordForm.password
    })
    ElMessage.success('登录成功')
    redirectAfterAuth()
  } catch {
    // 表单校验失败或登录失败，不做额外处理
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerFormRef.value) return
  try {
    await registerFormRef.value.validate()
    loading.value = true
    await userStore.register({
      username: registerForm.username,
      nickname: registerForm.nickname,
      password: registerForm.password
    })
    ElMessage.success('注册成功')
    redirectAfterAuth()
  } catch {
    // 表单校验失败或注册失败，不做额外处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #302520;
  background:
    radial-gradient(circle at 20% 16%, rgba(255, 130, 105, 0.2), transparent 28%),
    radial-gradient(circle at 84% 72%, rgba(247, 178, 101, 0.22), transparent 30%),
    linear-gradient(135deg, #fff3e8 0%, #f9dfcf 48%, #fff7ed 100%);
}

.ambient-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-halo {
  position: absolute;
  border-radius: 999px;
  filter: blur(2px);
  transition: transform 260ms ease-out;
}

.bg-halo--one {
  width: 360px;
  height: 360px;
  left: -90px;
  top: -86px;
  background: rgba(244, 111, 91, 0.22);
}

.bg-halo--two {
  width: 280px;
  height: 280px;
  right: 8%;
  top: 12%;
  background: rgba(255, 207, 129, 0.18);
}

.bg-halo--three {
  width: 420px;
  height: 420px;
  left: 36%;
  bottom: -210px;
  background: rgba(255, 143, 112, 0.12);
}

.soft-dot {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(181, 92, 70, 0.28);
  animation: dotFloat 8s ease-in-out infinite;
}

.campus-sketch {
  position: absolute;
  left: 3%;
  bottom: 3%;
  width: 48%;
  min-width: 520px;
  opacity: 0.065;
}

.campus-sketch path {
  stroke: #5a4035;
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.flow-bridge {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.62;
}

.flow-path {
  stroke: rgba(226, 101, 80, 0.18);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 10 22;
  animation: flowDash 11s linear infinite;
}

.flow-path--two {
  stroke: rgba(181, 105, 163, 0.13);
  animation-duration: 14s;
}

.flow-path--three {
  stroke: rgba(240, 165, 86, 0.14);
  animation-duration: 16s;
}

.content-row {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.38fr) minmax(400px, 1fr);
  align-items: center;
  gap: clamp(28px, 4vw, 64px);
  width: min(1280px, calc(100vw - 80px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 56px 0;
}

.left-panel {
  position: relative;
  min-height: 650px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 34px 16px 34px 0;
}

.brand-group {
  position: relative;
  z-index: 4;
  max-width: 560px;
  margin-bottom: 26px;
}

.brand-kicker {
  margin: 0 0 16px;
  color: #c86b4f;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.brand-group h1 {
  margin: 0;
  font-size: clamp(44px, 5vw, 72px);
  line-height: 1.06;
  font-weight: 900;
  color: #2e2420;
}

.brand-subtitle {
  margin: 18px 0 0;
  color: #8d7368;
  font-size: 20px;
  font-weight: 700;
}

.brand-line {
  display: inline-flex;
  margin: 22px 0 0;
  padding: 12px 20px;
  color: #9f4e3b;
  font-size: 18px;
  font-weight: 800;
  background: rgba(255, 251, 245, 0.6);
  border: 1px solid rgba(224, 135, 105, 0.16);
  border-radius: 999px;
  box-shadow: 0 18px 46px rgba(156, 90, 67, 0.09);
  backdrop-filter: blur(14px);
}

.ecosystem {
  position: relative;
  z-index: 2;
  width: min(640px, 100%);
  height: 380px;
  transform: translate3d(var(--parallax-x, 0), var(--parallax-y, 0), 0);
  transition: transform 260ms ease-out;
}

.ecosystem::before {
  content: "";
  position: absolute;
  inset: 40px 70px;
  border-radius: 999px;
  background:
    radial-gradient(circle, rgba(255, 255, 255, 0.62), transparent 54%),
    radial-gradient(circle at 68% 36%, rgba(238, 118, 91, 0.13), transparent 36%);
  filter: blur(4px);
}

.ecosystem-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.eco-link {
  stroke: rgba(171, 89, 67, 0.18);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 7 16;
  animation: flowDash 10s linear infinite;
}

.eco-link--two,
.eco-link--four {
  stroke: rgba(202, 109, 93, 0.14);
  animation-duration: 13s;
}

.entry-node {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 138px;
  height: 138px;
  color: #9e4e3b;
  background: rgba(255, 251, 246, 0.82);
  border: 1px solid rgba(229, 139, 105, 0.18);
  border-radius: 50%;
  box-shadow: 0 24px 70px rgba(146, 82, 62, 0.12);
  transform: translate(-50%, -50%);
  backdrop-filter: blur(18px);
}

.entry-node .el-icon {
  font-size: 30px;
}

.entry-node strong {
  margin-top: -4px;
  font-size: 18px;
}

.entry-node small {
  margin-top: -12px;
  color: #a8897d;
  font-size: 12px;
  font-weight: 700;
}

.entry-pulse {
  position: absolute;
  inset: -12px;
  border: 1px solid rgba(220, 112, 89, 0.16);
  border-radius: 50%;
  animation: pulseRing 4.8s ease-out infinite;
}

.eco-orbit {
  position: absolute;
  z-index: 3;
  transform: translate3d(var(--parallax-x, 0), var(--parallax-y, 0), 0);
  transition: transform 260ms ease-out;
}

.eco-orbit--course {
  left: 54px;
  top: 24px;
}

.eco-orbit--material {
  right: 36px;
  top: 42px;
}

.eco-orbit--discuss {
  left: 8px;
  bottom: 42px;
}

.eco-orbit--points {
  right: 80px;
  bottom: 20px;
}

.eco-card {
  display: grid;
  grid-template-columns: 48px 1fr;
  align-items: center;
  gap: 14px;
  width: 184px;
  min-height: 88px;
  padding: 16px;
  background: rgba(255, 251, 245, 0.72);
  border: 1px solid rgba(222, 137, 103, 0.16);
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(130, 78, 55, 0.11);
  backdrop-filter: blur(18px);
}

.eco-card--a {
  animation: cardFloatA 5.4s ease-in-out infinite;
}

.eco-card--b {
  animation: cardFloatB 6.8s ease-in-out infinite;
}

.eco-card--c {
  animation: cardFloatC 6s ease-in-out infinite;
}

.eco-card--d {
  animation: cardFloatD 7s ease-in-out infinite;
}

.eco-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: #fff;
  border-radius: 16px;
  box-shadow: 0 14px 30px rgba(217, 111, 83, 0.18);
}

.eco-icon .el-icon {
  font-size: 24px;
}

.eco-icon--course {
  background: linear-gradient(135deg, #ff7d6a, #f4a35d);
}

.eco-icon--material {
  background: linear-gradient(135deg, #68cdb7, #76aee8);
}

.eco-icon--discuss {
  background: linear-gradient(135deg, #f69a7d, #d888bd);
}

.eco-icon--points {
  background: linear-gradient(135deg, #f3b552, #f07462);
}

.eco-card strong {
  display: block;
  color: #352721;
  font-size: 18px;
  font-weight: 900;
}

.eco-card p {
  margin: 6px 0 0;
  color: #8d756b;
  font-size: 13px;
  font-weight: 700;
}

.right-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 650px;
}

.card-glow {
  position: absolute;
  width: 430px;
  height: 520px;
  border-radius: 44px;
  background:
    radial-gradient(circle at 45% 25%, rgba(255, 162, 116, 0.26), transparent 40%),
    linear-gradient(145deg, rgba(255, 118, 98, 0.16), rgba(255, 211, 150, 0.12));
  filter: blur(28px);
  transition: transform 260ms ease-out;
}

.login-card {
  position: relative;
  width: min(414px, 100%);
  padding: 34px;
  background: rgba(255, 251, 245, 0.94);
  border: 1px solid rgba(221, 139, 105, 0.18);
  border-radius: 30px;
  box-shadow: 0 28px 80px rgba(126, 76, 54, 0.16);
  backdrop-filter: blur(22px);
  animation: cardIn 560ms ease-out both;
}

.login-card__head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}

.card-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  background: linear-gradient(135deg, #ff7b68, #f3a25f);
  border-radius: 18px;
  box-shadow: 0 16px 34px rgba(234, 102, 79, 0.24);
}

.card-mark .el-icon {
  font-size: 26px;
}

.login-card__head p {
  margin: 0 0 4px;
  color: #a08276;
  font-size: 13px;
  font-weight: 800;
}

.login-card__head h2 {
  margin: 0;
  color: #2f241f;
  font-size: 28px;
  font-weight: 900;
}

.mode-tabs {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 46px;
  padding: 4px;
  margin-bottom: 22px;
  background: rgba(251, 236, 224, 0.8);
  border: 1px solid rgba(222, 143, 105, 0.16);
  border-radius: 999px;
}

.mode-tabs button {
  position: relative;
  z-index: 1;
  border: 0;
  color: #9b7d71;
  font-size: 15px;
  font-weight: 900;
  background: transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: color 200ms ease;
}

.mode-tabs button.active {
  color: #fff;
}

.mode-tabs__slider {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: linear-gradient(135deg, #ff7b68, #ef654f);
  border-radius: 999px;
  box-shadow: 0 12px 24px rgba(230, 98, 78, 0.24);
  transition: transform 260ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.mode-tabs--register .mode-tabs__slider {
  transform: translateX(100%);
}

.auth-form {
  display: grid;
  gap: 14px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.auth-form :deep(.el-input__wrapper) {
  height: 48px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(222, 143, 105, 0.14);
  border-radius: 16px;
  box-shadow: none;
  transition: border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.auth-form :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.88);
  border-color: rgba(234, 116, 86, 0.28);
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  border-color: rgba(234, 103, 80, 0.66);
  box-shadow: 0 0 0 4px rgba(234, 103, 80, 0.12);
}

.auth-form :deep(.el-input__inner) {
  color: #342722;
  font-weight: 700;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: #b9a79f;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  height: 50px;
  margin-top: 2px;
  border: 0;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 900;
  background: linear-gradient(135deg, #ff7a68, #ef624c);
  box-shadow: 0 18px 38px rgba(232, 97, 75, 0.24);
  transition: transform 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 46px rgba(232, 97, 75, 0.3);
  filter: saturate(1.05);
}

.demo-note {
  margin-top: 18px;
  padding: 12px 14px;
  color: rgba(125, 100, 90, 0.62);
  font-size: 12px;
  line-height: 1.7;
  background: rgba(255, 250, 244, 0.42);
  border: 1px dashed rgba(217, 136, 106, 0.18);
  border-radius: 14px;
}

.form-fade-enter-active,
.form-fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.form-fade-enter-from,
.form-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes dotFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes flowDash {
  to {
    stroke-dashoffset: -80;
  }
}

@keyframes pulseRing {
  0% {
    opacity: 0.52;
    transform: scale(0.94);
  }
  80%,
  100% {
    opacity: 0;
    transform: scale(1.24);
  }
}

@keyframes cardFloatA {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

@keyframes cardFloatB {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(7px) rotate(0.6deg);
  }
}

@keyframes cardFloatC {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(9px);
  }
}

@keyframes cardFloatD {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-6px) rotate(-0.6deg);
  }
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1080px) {
  .content-row {
    grid-template-columns: 1fr;
    width: min(760px, calc(100vw - 40px));
    padding: 36px 0 48px;
  }

  .left-panel,
  .right-panel {
    min-height: auto;
  }

  .ecosystem {
    height: 330px;
  }

  .flow-bridge {
    opacity: 0.26;
  }
}

@media (max-width: 680px) {
  .content-row {
    width: min(100% - 28px, 520px);
  }

  .brand-group h1 {
    font-size: 40px;
  }

  .brand-subtitle,
  .brand-line {
    font-size: 15px;
  }

  .ecosystem {
    height: auto;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    padding-top: 22px;
  }

  .ecosystem::before,
  .ecosystem-lines,
  .entry-node {
    display: none;
  }

  .eco-orbit {
    position: static;
  }

  .eco-card {
    width: auto;
  }

  .login-card {
    padding: 26px;
    border-radius: 26px;
  }

  .campus-sketch {
    min-width: 360px;
    opacity: 0.04;
  }
}
</style>
