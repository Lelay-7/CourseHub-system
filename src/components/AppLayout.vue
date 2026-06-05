<template>
  <!-- 主布局：桌面端侧边栏 + 移动端TabBar -->
  <div class="app-layout" :class="{ 'is-mobile': appStore.isMobile }">
    <!-- 桌面端侧边栏 -->
    <AppSidebar v-if="!appStore.isMobile" class="desktop-only" />

    <div class="app-main">
      <!-- 顶部导航栏 -->
      <AppNavbar />

      <!-- 内容区（keep-alive 缓存列表页状态） -->
      <div class="app-content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </div>
    </div>

    <!-- 移动端底部TabBar -->
    <MobileTabbar v-if="appStore.isMobile" class="mobile-only" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import AppSidebar from './AppSidebar.vue'
import AppNavbar from './AppNavbar.vue'
import MobileTabbar from './MobileTabbar.vue'

const appStore = useAppStore()

// 需要缓存的组件名（匹配路由 meta.keepAlive）
const cachedViews = computed(() => ['CourseList', 'MaterialList', 'ContactList', 'MallList'])
</script>

<style scoped>
.app-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.app-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--color-bg-page);
}

/* 移动端 */
.app-layout.is-mobile .app-main {
  width: 100%;
}
.app-layout.is-mobile .app-content {
  padding-bottom: 0;
}
</style>
