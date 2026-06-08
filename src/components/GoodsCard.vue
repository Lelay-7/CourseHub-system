<template>
  <article class="goods-card-pro" :class="{ soldout: goods.stock <= 0 }">
    <div class="goods-card-pro__icon">
      <el-icon :size="34"><component :is="icon" /></el-icon>
    </div>
    <h3>{{ goods.name }}</h3>
    <p>{{ goods.description || '适用于选课、资料下载和校园学习场景的积分权益。' }}</p>
    <div class="goods-card-pro__meta">
      <strong>{{ goods.exchange_score }} 积分</strong>
      <StatusTag :tone="goods.stock > 0 ? 'success' : 'info'">
        {{ goods.stock > 0 ? `库存 ${goods.stock}` : '已售罄' }}
      </StatusTag>
    </div>
    <el-button type="primary" :disabled="goods.stock <= 0" @click="$router.push(`/mall/${goods.goods_id}`)">
      立即兑换
    </el-button>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { Tickets, Reading, Service, FolderOpened, Medal, Present, Document } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'

const props = defineProps({
  goods: { type: Object, required: true }
})

const icons = [Tickets, Reading, Service, FolderOpened, Present, Document, Medal]
const icon = computed(() => icons[(props.goods.goods_id - 1) % icons.length])
</script>

<style scoped>
.goods-card-pro {
  min-height: 276px;
  display: flex;
  flex-direction: column;
  padding: var(--spacing-lg);
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), opacity var(--transition-fast);
}

.goods-card-pro:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.goods-card-pro.soldout {
  opacity: 0.62;
}

.goods-card-pro__icon {
  width: 72px;
  height: 72px;
  border-radius: 24px;
  color: #fff;
  background:
    radial-gradient(circle at 30% 20%, rgba(255,255,255,.24), transparent 32%),
    linear-gradient(135deg, #f07462, #f8b25f);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-md);
}

.goods-card-pro h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 900;
}

.goods-card-pro p {
  flex: 1;
  margin: 10px 0 var(--spacing-md);
  color: var(--color-text-regular);
  line-height: 1.7;
}

.goods-card-pro__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.goods-card-pro__meta strong {
  color: var(--color-primary-dark);
  font-size: var(--font-size-lg);
}
</style>
