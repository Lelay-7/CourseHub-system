<template>
  <div class="app-navbar">
    <div class="navbar-left">
      <!-- 移动端菜单按钮 -->
      <el-button
        v-if="appStore.isMobile"
        :icon="Menu"
        text
        @click="showDrawer = true"
        class="mobile-only"
      />
      <span class="navbar-title">{{ pageTitle }}</span>
    </div>

    <div class="navbar-right">
      <!-- 待处理订单徽章 -->
      <el-badge
        v-if="appStore.showOrderBadge"
        :value="appStore.orderBadgeText"
        :max="99"
        class="order-badge"
      >
        <el-button :icon="Bell" text @click="$router.push('/profile/orders')" />
      </el-badge>

      <!-- 积分余额 -->
      <span class="score-display">
        <el-icon><Star /></el-icon>
        {{ userStore.scoreBalance }} 积分
      </span>

      <!-- 用户下拉菜单 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :src="userStore.userInfo?.avatar_url" :size="32">
            <el-icon :size="18"><User /></el-icon>
          </el-avatar>
          <span class="username desktop-only">
            {{ userStore.userInfo?.nickname || '用户' }}
          </span>
          <el-icon class="desktop-only"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon> 个人中心
            </el-dropdown-item>
            <el-dropdown-item command="orders">
              <el-icon><Tickets /></el-icon> 我的订单
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon> 设置
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 移动端侧滑菜单 -->
    <el-drawer
      v-model="showDrawer"
      direction="ltr"
      size="240px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-logo">不高山选课通</div>
      <el-menu
        :default-active="activeMenu"
        router
        @select="showDrawer = false"
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon> 首页
        </el-menu-item>
        <el-menu-item index="/course">
          <el-icon><Reading /></el-icon> 课程
        </el-menu-item>
        <el-menu-item index="/material">
          <el-icon><FolderOpened /></el-icon> 资料
        </el-menu-item>
        <el-menu-item index="/contact">
          <el-icon><UserFilled /></el-icon> 通讯录
        </el-menu-item>
        <el-menu-item index="/mall">
          <el-icon><ShoppingCart /></el-icon> 积分商城
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon> 个人中心
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  HomeFilled, Reading, FolderOpened, UserFilled, User,
  ShoppingCart, Bell, Star, ArrowDown, Setting, Tickets,
  SwitchButton, Menu
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const showDrawer = ref(false)

const pageTitle = computed(() => route.meta.title || '不高山选课通')
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/course')) return '/course'
  if (path.startsWith('/material')) return '/material'
  if (path.startsWith('/contact')) return '/contact'
  if (path.startsWith('/mall')) return '/mall'
  if (path.startsWith('/profile')) return '/profile'
  return '/home'
})

/** 处理下拉菜单命令 */
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
      // 用户取消
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
  height: var(--navbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.navbar-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.score-display {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-warning);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}
.user-info:hover {
  background: var(--color-bg-page);
}
.username {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-badge {
  margin-right: var(--spacing-xs);
}

.drawer-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-primary);
  border-bottom: 1px solid var(--color-border-light);
}
</style>
