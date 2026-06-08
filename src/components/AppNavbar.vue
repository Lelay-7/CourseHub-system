<template>
  <header class="app-navbar">
    <div class="navbar-left">
      <span class="navbar-title">{{ pageTitle }}</span>
    </div>

    <div class="navbar-right">
      <el-badge
        v-if="appStore.showOrderBadge"
        :value="appStore.orderBadgeText"
        :max="99"
        class="order-badge"
      >
        <el-button :icon="Bell" text @click="$router.push('/profile/orders')" />
      </el-badge>

      <span class="score-display">
        <el-icon><Star /></el-icon>
        {{ userStore.scoreBalance }} 积分
      </span>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :src="userStore.userInfo?.avatar_url" :size="34">
            <el-icon :size="18"><User /></el-icon>
          </el-avatar>
          <span class="username">{{ userStore.userInfo?.nickname || '用户' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="orders">
              <el-icon><Tickets /></el-icon>
              我的订单
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>
              设置
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  Bell,
  Star,
  User,
  ArrowDown,
  Setting,
  Tickets,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const fallbackTitles = {
  '/home': '首页',
  '/course': '选课中心',
  '/square': '同学广场',
  '/material': '学习资料',
  '/contact': '师生通讯录',
  '/mall': '积分商城',
  '/profile': '个人中心'
}

const pageTitle = computed(() => {
  const matched = Object.entries(fallbackTitles).find(([path]) => route.path.startsWith(path))
  return route.meta.title || matched?.[1] || '不高山选课通'
})

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录吗？', '操作确认', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      })
      userStore.logout()
      router.push('/login')
    } catch {
      // user cancelled
    }
  } else if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'orders') {
    router.push('/profile/orders')
  } else if (command === 'settings') {
    router.push('/profile/settings')
  }
}
</script>

<style scoped>
.app-navbar {
  height: 64px;
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-xl);
  background: rgba(255, 250, 243, 0.9);
  border-bottom: 1px solid rgba(154, 66, 45, 0.08);
  backdrop-filter: blur(18px);
}

.navbar-title {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: 900;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.score-display {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 34px;
  padding: 0 13px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: var(--color-accent-light);
  border: 1px solid rgba(216, 147, 52, 0.24);
  font-size: var(--font-size-sm);
  font-weight: 900;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  min-height: 42px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.user-info:hover {
  background: var(--color-bg-soft);
}

.username {
  max-width: 100px;
  overflow: hidden;
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .app-navbar {
    padding: 0 12px;
  }

  .username {
    display: none;
  }
}
</style>
