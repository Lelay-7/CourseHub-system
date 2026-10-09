<template>
  <section class="toolbar">
    <slot />
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  border-radius: 26px;
  background: color-mix(in srgb, var(--color-bg-card) 94%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-primary) 8%, transparent);
  box-shadow: var(--shadow-sm);
}

.toolbar :deep(.el-input) {
  flex: 1 1 280px;
}

.toolbar :deep(.el-select) {
  width: 150px;
}

.toolbar :deep(.el-radio-button__inner) {
  border-color: var(--color-border-light);
}

.toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: -1px 0 0 0 var(--color-primary);
}
</style>
