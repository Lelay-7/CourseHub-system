<template>
  <div class="star-rating">
    <span
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ filled: star <= modelValue, half: isHalfStar(star) }"
      @click="readonly ? null : $emit('update:modelValue', star)"
    >
      <el-icon :size="size">
        <StarFilled v-if="star <= modelValue" />
        <SemiSelect v-else-if="isHalfStar(star)" />
        <Star v-else />
      </el-icon>
    </span>
    <span v-if="showText" class="rating-text">{{ displayText }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Star, StarFilled, SemiSelect } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
  size: { type: [Number, String], default: 16 },
  showText: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])

function isHalfStar(star) {
  return props.modelValue >= star - 0.5 && props.modelValue < star
}

const displayText = computed(() => {
  if (props.modelValue >= 4.5) return '非常好'
  if (props.modelValue >= 3.5) return '好'
  if (props.modelValue >= 2.5) return '一般'
  if (props.modelValue >= 1.5) return '较差'
  return props.modelValue > 0 ? '很差' : '暂无评分'
})
</script>

<style scoped>
.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.star {
  cursor: pointer;
  color: var(--color-border);
  transition: color var(--transition-fast);
  display: inline-flex;
}
.star.filled {
  color: var(--color-warning);
}
.star.half {
  color: var(--color-warning);
}

.rating-text {
  margin-left: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}
</style>
