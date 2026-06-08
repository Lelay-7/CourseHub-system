<template>
  <div class="loading-skeleton">
    <el-skeleton animated v-if="type === 'card'">
      <template #template>
        <div class="skeleton-card-grid">
          <div v-for="i in count" :key="i" class="skeleton-card">
            <el-skeleton-item variant="image" style="width: 100%; height: 140px" />
            <div style="padding: 14px">
              <el-skeleton-item variant="text" style="width: 80%; margin-bottom: 8px" />
              <el-skeleton-item variant="text" style="width: 50%" />
            </div>
          </div>
        </div>
      </template>
    </el-skeleton>

    <el-skeleton v-else-if="type === 'list'" animated :rows="rows" />

    <el-skeleton v-else animated :rows="2" />
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'card' },  // card | list
  count: { type: Number, default: 6 },
  rows: { type: Number, default: 5 }
})
</script>

<style scoped>
.loading-skeleton {
  padding: var(--spacing-md);
}

.skeleton-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

.skeleton-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}
</style>
