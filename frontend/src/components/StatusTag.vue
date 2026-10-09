<template>
  <span class="status-tag" :class="tone">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  tone: { type: String, default: 'default' }
})
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: var(--color-bg-soft);
  font-size: var(--font-size-xs);
  font-weight: 900;
  white-space: nowrap;
}

.status-tag.success {
  color: var(--color-success);
  background: var(--color-success-light);
}

.status-tag.warning {
  color: var(--color-warning);
  background: var(--color-warning-light);
}

.status-tag.danger {
  color: var(--color-danger);
  background: var(--color-danger-light);
}

.status-tag.info {
  color: var(--color-text-secondary);
  background: var(--color-bg-soft);
}
</style>
