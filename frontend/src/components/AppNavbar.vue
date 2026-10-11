<template>
  <header class="app-navbar">
    <router-link to="/home" class="brand" aria-label="CourseHub 首页">
      <svg class="brand-mark" viewBox="0 0 40 40" aria-hidden="true">
        <path d="M20 2 30 8 30 20 20 26 10 20 10 8Z" fill="currentColor" opacity=".65" />
        <path d="M10 15 20 21 20 33 10 39 0 33 0 21Z" fill="currentColor" />
        <path d="M30 15 40 21 40 33 30 39 20 33 20 21Z" fill="currentColor" opacity=".85" />
      </svg>
      <span class="brand-copy"><strong>CourseHub</strong><small>不高山选课通</small></span>
    </router-link>
    <nav class="primary-nav" aria-label="主导航">
      <router-link v-for="item in navigation" :key="item.path" :to="item.path" :class="{ active: isActive(item.path) }" :aria-current="isActive(item.path) ? 'page' : undefined">{{ item.label }}</router-link>
      <el-dropdown trigger="click" @command="navigate">
        <button class="more-trigger" type="button">更多<el-icon><ArrowDown /></el-icon></button>
        <template #dropdown><el-dropdown-menu>
          <el-dropdown-item command="/contact">通讯录</el-dropdown-item>
          <el-dropdown-item command="/mall">积分商城</el-dropdown-item>
          <el-dropdown-item v-if="userStore.isAdmin" command="/admin/dashboard">管理后台</el-dropdown-item>
        </el-dropdown-menu></template>
      </el-dropdown>
    </nav>
    <div class="navbar-right">
      <div class="navbar-theme"><ThemeSwitcher /></div>
      <el-badge :value="chat.state.unreadCount>99?'99+':chat.state.unreadCount" :hidden="!chat.state.unreadCount">
        <el-button class="message-entry" :icon="ChatDotRound" text aria-label="查看消息" @click="navigate('/messages')" />
      </el-badge>
      <el-badge v-if="appStore.showOrderBadge" class="order-entry" :value="appStore.orderBadgeText" :max="99">
        <el-button :icon="Bell" text aria-label="查看订单" @click="navigate('/profile/orders')" />
      </el-badge>
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="user-info" type="button" aria-label="打开个人菜单">
          <el-avatar :src="userStore.userInfo?.avatar_url" :size="32"><el-icon><User /></el-icon></el-avatar>
          <el-icon class="user-arrow"><ArrowDown /></el-icon>
        </button>
        <template #dropdown><el-dropdown-menu>
          <el-dropdown-item disabled>{{ userStore.userInfo?.nickname || '同学' }} · {{ userStore.scoreBalance }} 积分</el-dropdown-item>
          <el-dropdown-item divided command="/profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="/profile/courses">我的选课</el-dropdown-item>
          <el-dropdown-item command="/messages">我的消息</el-dropdown-item>
          <el-dropdown-item command="/profile/orders">我的订单</el-dropdown-item>
          <el-dropdown-item command="/profile/settings">设置</el-dropdown-item>
          <el-dropdown-item command="/contact">通讯录</el-dropdown-item>
          <el-dropdown-item command="/mall">积分商城</el-dropdown-item>
          <el-dropdown-item v-if="userStore.isAdmin" command="/admin/dashboard">管理后台</el-dropdown-item>
          <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu></template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Bell, User, ArrowDown, ChatDotRound } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import ThemeSwitcher from './ThemeSwitcher.vue'
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const chat = useChatStore()
const navigation = [
  { path: '/home', label: '首页' }, { path: '/course', label: '课程' },
  { path: '/material', label: '资料' }, { path: '/square', label: '广场' }, { path: '/profile', label: '我的' }
]
const isActive = path => path === '/home' ? route.path === path : route.path.startsWith(path)
const navigate = path => router.push(path)
async function handleCommand(command) {
  if (command !== 'logout') { navigate(command); return }
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '操作确认', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    userStore.logout()
    navigate('/login')
  } catch { /* User cancelled. */ }
}
</script>

<style scoped>
.app-navbar { position: sticky; top: 0; z-index: 30; display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 80px; padding: 0 max(32px, calc((100vw - 1440px) / 2)); background: var(--color-bg-page); border-bottom: 1px solid var(--color-border-light); }
.brand { display: inline-flex; align-items: center; gap: 12px; flex: 0 0 auto; color: var(--color-primary); text-decoration: none; }
.brand-mark { width: 36px; height: 36px; }
.brand-copy strong { display: block; color: var(--color-text-primary); font-size: 22px; line-height: 1.2; font-weight: 750; letter-spacing: -.6px; }
.brand-copy small { display: block; color: var(--color-text-secondary); font-size: 11px; margin-top: 4px; letter-spacing: 1px; }
.primary-nav { display: flex; align-items: center; align-self: stretch; gap: clamp(16px, 2.4vw, 36px); }
.primary-nav > a { display: flex; align-items: center; position: relative; color: var(--color-text-secondary); font-size: 15px; font-weight: 550; text-decoration: none; white-space: nowrap; }
.primary-nav > a:hover, .primary-nav > a.active { color: var(--color-primary); }
.primary-nav > a.active { font-weight: 750; }
.primary-nav > a.active::after { content: ''; position: absolute; bottom: 9px; left: 0; right: 0; height: 3px; border-radius: 2px; background: var(--color-primary); }
.more-trigger { display: inline-flex; align-items: center; gap: 5px; border: 0; padding: 10px 0; background: transparent; color: var(--color-text-secondary); font: inherit; font-size: 13px; cursor: pointer; }
.navbar-right { display: flex; align-items: center; gap: 14px; flex: 0 0 auto; }
.user-info { display: flex; align-items: center; gap: 9px; border: 0; padding: 4px; border-radius: 6px; color: var(--color-text-secondary); background: transparent; cursor: pointer; }
.user-info:hover { background: var(--color-bg-soft); }
.message-entry{min-width:44px;min-height:44px}
@media (min-width:768px) and (max-width:900px){.navbar-theme{display:none}}
.brand:focus-visible, .primary-nav a:focus-visible, .user-info:focus-visible, .more-trigger:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 4px; }
@media (max-width: 1050px) { .app-navbar { padding: 0 24px; gap: 16px; } .primary-nav { gap: 18px; } .brand-copy strong { font-size: 19px; } .navbar-right { gap: 8px; } }
@media (max-width: 767px) { .app-navbar { min-height: 66px; padding: 0 16px; } .primary-nav { display: none; } .brand-mark { width: 28px; height: 28px; } .brand-copy strong { font-size: 18px; } .brand-copy small { font-size: 10px; } .user-arrow { display: none; } }
@media (max-width: 767px) { .navbar-theme,.order-entry{display:none}.navbar-right{gap:6px}.user-info{min-width:44px;min-height:44px;box-sizing:border-box} }
</style>
