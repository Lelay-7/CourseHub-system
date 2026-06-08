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
  color: #5d7f45;
  background: #edf7e5;
}

.status-tag.warning {
  color: #ad6b1a;
  background: #fff0cf;
}

.status-tag.danger {
  color: #b84f45;
  background: #fff0ec;
}

.status-tag.info {
  color: var(--color-text-secondary);
  background: #f6eee7;
}
</style>
