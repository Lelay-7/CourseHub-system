<template>
  <main class="page-container" :class="{ compact }">
    <slot />
  </main>
</template>

<script setup>
defineProps({
  compact: { type: Boolean, default: false }
})
</script>

<style scoped>
.page-container {
  width: min(calc(100% - 64px), 1440px);
  margin: 0 auto;
  padding: 40px 0 var(--spacing-xxl);
}

.page-container.compact {
  width: min(calc(100% - 64px), 1120px);
}

@media (max-width: 767px) {
  .page-container,
  .page-container.compact {
    width: calc(100% - 24px);
    padding-top: var(--spacing-md);
    padding-bottom: calc(var(--tabbar-height) + var(--spacing-md));
  }
}
</style>
