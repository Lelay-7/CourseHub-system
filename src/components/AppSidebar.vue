<template>
  <div class="app-sidebar" :style="{ width: appStore.sidebarWidth }">
    <div class="sidebar-logo">
      <el-icon v-if="appStore.sidebarCollapsed" :size="24">
        <School />
      </el-icon>
      <span v-else class="logo-text">不高山选课通</span>
    </div>

    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :collapse-transition="false"
      router
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      class="sidebar-menu"
    >
      <el-menu-item index="/home">
        <el-icon><HomeFilled /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <el-menu-item index="/course">
        <el-icon><Reading /></el-icon>
        <template #title>课程</template>
      </el-menu-item>

      <el-menu-item index="/material">
        <el-icon><FolderOpened /></el-icon>
        <template #title>资料</template>
      </el-menu-item>

      <el-menu-item index="/contact">
        <el-icon><UserFilled /></el-icon>
        <template #title>通讯录</template>
      </el-menu-item>

      <el-menu-item index="/mall">
        <el-icon><ShoppingCart /></el-icon>
        <template #title>积分商城</template>
      </el-menu-item>

      <el-menu-item index="/profile">
        <el-icon><User /></el-icon>
        <template #title>个人中心</template>
      </el-menu-item>
    </el-menu>

    <!-- 折叠按钮 -->
    <div class="sidebar-toggle" @click="appStore.toggleSidebar">
      <el-icon :size="18">
        <ArrowLeft v-if="!appStore.sidebarCollapsed" />
        <ArrowRight v-else />
      </el-icon>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/course')) return '/course'
  if (path.startsWith('/material')) return '/material'
  if (path.startsWith('/contact')) return '/contact'
  if (path.startsWith('/mall')) return '/mall'
  if (path.startsWith('/profile')) return '/profile'
  return path
})
</script>

<style scoped>
.app-sidebar {
  position: relative;
  height: 100vh;
  background: #304156;
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  flex-shrink: 0;
}

.sidebar-logo {
  height: var(--navbar-height);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-md);
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-text {
  font-size: var(--font-size-md);
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-toggle {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #bfcbd9;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  transition: background var(--transition-fast);
}
.sidebar-toggle:hover {
  background: rgba(255, 255, 255, 0.05);
}
</style>
