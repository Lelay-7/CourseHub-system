<template>
  <component :is="tag" class="ui-card" :class="{ hover }">
    <slot />
  </component>
</template>

<script setup>
defineProps({
  tag: { type: String, default: 'section' },
  hover: { type: Boolean, default: false }
})
</script>

<style scoped>
.ui-card {
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
}

.ui-card.hover {
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.ui-card.hover:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
</style>
