<template>
  <div class="app-shell" :class="{ 'is-mobile': appStore.isMobile }">
    <AppSidebar v-if="!appStore.isMobile" />
    <div class="app-shell__main">
      <AppNavbar />
      <div class="app-content">
        <slot />
      </div>
    </div>
    <MobileTabbar v-if="appStore.isMobile" class="mobile-only" />
  </div>
</template>

<script setup>
import { useAppStore } from '@/stores/app'
import AppSidebar from './AppSidebar.vue'
import AppNavbar from './AppNavbar.vue'
import MobileTabbar from './MobileTabbar.vue'

const appStore = useAppStore()
</script>

<style scoped>
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
