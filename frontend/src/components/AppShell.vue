<template>
  <div class="app-shell" :class="{ 'is-mobile': appStore.isMobile }">
    <AppSidebar v-if="!appStore.isMobile" />
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
  background: linear-gradient(135deg, #e7664a, #d1432a);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.admin-banner button {
  padding: 4px 16px;
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 12px;
  background: rgba(255,255,255,0.15);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s;
}
.admin-banner button:hover {
  background: rgba(255,255,255,0.28);
}

.app-shell {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: transparent;
}

.app-shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: transparent;
}

.app-shell.is-mobile .app-shell__main {
  width: 100%;
}
</style>
