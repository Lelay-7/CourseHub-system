<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import bgImage from '@/assets/images/beijing.png'

const appStore = useAppStore()

onMounted(() => {
  // 监听窗口尺寸变化，自动切换布局模式
  appStore.checkViewport()
  window.addEventListener('resize', () => {
    appStore.checkViewport()
  })

  // 设置全局背景图
  document.body.style.background = `url(${bgImage}) center / cover no-repeat fixed`
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
