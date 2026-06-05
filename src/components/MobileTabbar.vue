<template>
  <div class="mobile-tabbar">
    <div
      v-for="item in tabs"
      :key="item.path"
      class="tabbar-item"
      :class="{ active: isActive(item.path) }"
      @click="navigate(item.path)"
    >
      <el-icon :size="20">
        <component :is="item.icon" />
      </el-icon>
      <span class="tabbar-label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, Reading, FolderOpened, UserFilled, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/course', label: '课程', icon: Reading },
  { path: '/material', label: '资料', icon: FolderOpened },
  { path: '/contact', label: '通讯录', icon: UserFilled },
  { path: '/profile', label: '我的', icon: User }
]

function isActive(path) {
  if (path === '/home') return route.path === '/home'
  return route.path.startsWith(path)
}

function navigate(path) {
  router.push(path)
}
</script>

<style scoped>
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: var(--tabbar-height);
  display: flex;
  align-items: center;
  background: var(--color-bg-card);
  border-top: 1px solid var(--color-border-light);
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color var(--transition-fast);
}
.tabbar-item.active {
  color: var(--color-primary);
}

.tabbar-label {
  font-size: var(--font-size-xs);
}
</style>
