<template>
  <div class="app-shell" :class="{ 'is-mobile': appStore.isMobile }">
    <AppSidebar v-if="isAdminRoute && !appStore.isMobile" />
    <div class="app-shell__main">
      <div v-if="showAdminBanner" class="admin-banner">
        <span>🔧 你正在以管理员身份查看前台页面</span>
        <button @click="router.push('/admin/dashboard')">返回后台</button>
      </div>
      <AppNavbar />
      <div class="app-content">
        <slot />
      </div>
    </div>
    <MobileTabbar v-if="appStore.isMobile" class="mobile-only" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import AppSidebar from './AppSidebar.vue'
import AppNavbar from './AppNavbar.vue'
import MobileTabbar from './MobileTabbar.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const frontendPaths = ['/home', '/course', '/square', '/material', '/contact', '/mall']
const showAdminBanner = computed(() =>
  userStore.isAdmin && frontendPaths.some(p => route.path.startsWith(p))
)
</script>

<style scoped>
.admin-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 20px;
  background: var(--color-bg-soft);
  color: var(--color-text-regular);
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.admin-banner button {
  padding: 4px 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-bg-card);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s;
}
.admin-banner button:hover {
  background: var(--color-bg-soft);
}

.app-shell {
  display: flex;
  width: 100%;
  min-height: 100dvh;
  background: var(--color-bg-page);
}

.app-shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-content {
  flex: 1;
  min-width: 0;
  background: var(--color-bg-page);
}

.app-shell.is-mobile .app-shell__main {
  width: 100%;
}
</style>
