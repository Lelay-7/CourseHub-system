<template>
  <div class="error-state">
    <el-result
      :icon="icon"
      :title="title"
      :sub-title="subTitle"
    >
      <template #extra>
        <el-button type="primary" :loading="loading" @click="$emit('retry')">
          重试
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: 'error' },        // error | warning | info | success
  title: { type: String, default: '加载失败' },
  subTitle: { type: String, default: '网络异常，请稍后重试' },
  loading: { type: Boolean, default: false }
})

defineEmits(['retry'])
</script>

<style scoped>
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: var(--spacing-xl);
}
</style>
