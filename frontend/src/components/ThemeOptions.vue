<template>
  <fieldset class="theme-options" :class="{ compact }">
    <legend class="sr-only">选择界面配色</legend>
    <label v-for="theme in THEMES" :key="theme.id" class="theme-option" :class="{ selected: appStore.theme === theme.id }">
      <input type="radio" :name="groupName" :value="theme.id" :checked="appStore.theme === theme.id" @change="appStore.setTheme(theme.id)" />
      <span class="theme-swatches" aria-hidden="true">
        <span v-for="color in theme.colors" :key="color" :style="{ backgroundColor: color }" />
      </span>
      <span class="theme-copy"><strong>{{ theme.name }}</strong><small>{{ theme.description }}</small></span>
      <el-icon v-if="appStore.theme === theme.id" class="theme-check"><Check /></el-icon>
    </label>
  </fieldset>
</template>

<script setup>
import { getCurrentInstance } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { THEMES } from '@/utils/theme'
defineProps({ compact: Boolean })
const appStore = useAppStore()
const groupName = `theme-${getCurrentInstance().uid}`
</script>

<style scoped>
.theme-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; min-width: 0; padding: 0; margin: 0; border: 0; }
.theme-options.compact { grid-template-columns: 1fr; gap: 6px; }
.theme-option { position: relative; display: flex; align-items: center; gap: 12px; padding: 15px 12px; border: 1px solid var(--color-border); border-radius: 8px; cursor: pointer; background: var(--color-bg-card); }
.theme-option:hover, .theme-option.selected { border-color: var(--color-primary); background: var(--color-bg-soft); }
.theme-option input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.theme-swatches { display: flex; flex: 0 0 auto; overflow: hidden; border: 1px solid var(--color-border); border-radius: 5px; }
.theme-swatches > span { width: 12px; height: 32px; }
.theme-option input:focus-visible + .theme-swatches { outline: 2px solid var(--color-primary); outline-offset: 4px; }
.theme-copy { min-width: 0; flex: 1; }
.theme-copy strong { display: block; color: var(--color-text-primary); font-size: 13px; font-weight: 650; }
.theme-copy small { display: block; margin-top: 4px; color: var(--color-text-secondary); font-size: 11px; line-height: 1.5; }
.theme-check { flex: 0 0 auto; color: var(--color-primary); }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@media (max-width: 900px) { .theme-options { grid-template-columns: 1fr; } }
</style>
