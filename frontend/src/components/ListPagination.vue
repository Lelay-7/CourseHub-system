<template>
  <div v-if="total > 0" class="list-pagination">
    <span>共 {{ total }} 条</span>
    <el-pagination v-if="total > pageSize" background :current-page="page" :page-size="pageSize" :total="total" :pager-count="5" layout="prev, pager, next" @current-change="$emit('change', $event)" />
  </div>
</template>
<script setup>
defineProps({ page: Number, pageSize: Number, total: Number })
defineEmits(['change'])
</script>
<style scoped>
.list-pagination { display: flex; justify-content: center; align-items: center; gap: 16px; flex-wrap: wrap; padding: 20px 0; color: var(--color-text-secondary); }
</style>
