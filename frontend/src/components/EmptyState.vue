<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <slot>
        <el-button v-if="showRefresh" type="primary" @click="$emit('refresh')">
          重新加载
        </el-button>
      </slot>
    </el-empty>
  </div>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  imageSize: { type: Number, default: 120 },
  showRefresh: { type: Boolean, default: false }
})

defineEmits(['refresh'])
</script>

<style scoped>
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: var(--spacing-xl);
}
</style>
