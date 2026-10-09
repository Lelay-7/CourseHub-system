<template>
  <el-popover placement="bottom-end" trigger="click" :width="300">
    <template #reference>
      <button type="button" class="theme-trigger" :aria-label="`切换配色，当前${appStore.currentTheme.name}`">
        <el-icon :size="20"><Brush /></el-icon><span>配色</span>
      </button>
    </template>
    <p class="theme-heading">界面配色</p>
    <ThemeOptions compact />
    <p class="theme-hint">立即生效，自动记住你的选择</p>
  </el-popover>
</template>

<script setup>
import { Brush } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import ThemeOptions from './ThemeOptions.vue'
const appStore = useAppStore()
</script>

<style scoped>
.theme-trigger { display: inline-flex; align-items: center; gap: 7px; min-height: 40px; padding: 0 8px; border: 0; border-radius: 6px; color: var(--color-text-regular); background: transparent; cursor: pointer; font: inherit; font-size: 13px; }
.theme-trigger:hover { color: var(--color-text-primary); background: var(--color-bg-soft); }
.theme-trigger:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.theme-heading { margin: 0 0 12px; color: var(--color-text-primary); font-weight: 650; }
.theme-hint { margin: 12px 0 0; color: var(--color-text-secondary); font-size: 11px; }
@media (max-width: 900px) { .theme-trigger span { display: none; } }
</style>
