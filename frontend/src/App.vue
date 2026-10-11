<template>
  <router-view />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { THEME_STORAGE_KEY, readTheme } from '@/utils/theme'
import { useChatPolling } from '@/composables/useChatPolling'

useChatPolling()

const appStore = useAppStore()
const checkViewport = () => appStore.checkViewport()
function syncTheme(event) {
  if (event.key === THEME_STORAGE_KEY || event.key === null) {
    appStore.setTheme(readTheme(), { persist: false })
  }
}

onMounted(() => {
  // 监听窗口尺寸变化，自动切换布局模式
  appStore.checkViewport()
  window.addEventListener('resize', checkViewport)
  window.addEventListener('storage', syncTheme)
})
onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
  window.removeEventListener('storage', syncTheme)
})
</script>

<style>
/* 全局重置 */
html, body, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans', 'PingFang SC', 'Microsoft YaHei',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
