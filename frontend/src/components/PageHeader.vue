<template>
  <header class="page-header">
    <div class="page-header__main">
      <div class="page-header__title-row">
        <el-button
          v-if="showBack"
          :icon="ArrowLeft"
          text
          class="back-btn"
          @click="$router.back()"
        />
        <h1>{{ title }}</h1>
      </div>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.extra || $slots.default" class="page-header__extra">
      <slot name="extra" />
      <slot />
    </div>
  </header>
</template>

<script setup>
import { ArrowLeft } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  showBack: { type: Boolean, default: false }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.page-header__main {
  min-width: 0;
}

.page-header__title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.back-btn {
  flex-shrink: 0;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 28px;
  font-weight: 900;
  line-height: 1.25;
}

.page-header p {
  max-width: 620px;
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-base);
  line-height: 1.7;
}

.page-header__extra {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

@media (max-width: 767px) {
  .page-header {
    flex-direction: column;
  }

  .page-header__extra {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
