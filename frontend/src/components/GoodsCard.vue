<template>
  <div class="goods-card card card-hover" @click="goDetail">
    <div class="goods-image">
      <img :src="goods.cover_url || defaultImg" :alt="goods.name" class="img-cover" />
      <!-- 库存不足遮罩 -->
      <div v-if="goods.stock <= 0" class="sold-out-mask">
        <span>已售罄</span>
      </div>
    </div>

    <div class="goods-info">
      <h4 class="goods-name text-ellipsis-2">{{ goods.name }}</h4>
      <div class="goods-footer">
        <span class="goods-score">
          <el-icon><StarFilled /></el-icon>
          {{ goods.exchange_score }} 积分
        </span>
        <el-tag size="small" :type="goods.stock > 0 ? 'success' : 'info'">
          {{ goods.stock > 0 ? `库存 ${goods.stock}` : '已售罄' }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { StarFilled } from '@element-plus/icons-vue'

const props = defineProps({
  goods: { type: Object, required: true }
})

const router = useRouter()
const defaultImg = new URL('@/assets/images/default-cover.svg', import.meta.url).href

function goDetail() {
  router.push(`/mall/${props.goods.goods_id}`)
}
</script>

<style scoped>
.goods-card {
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.goods-image {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: var(--color-bg-page);
  overflow: hidden;
  padding-top: 0;
}

.goods-image img {
  position: absolute;
  inset: 0;
  transition: transform var(--transition-slow);
}

.goods-card:hover .goods-image img {
  transform: scale(1.05);
}

.sold-out-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.goods-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-height: 112px;
}

.goods-name {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.4;
  min-height: 2.8em;
}

.goods-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.goods-score {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #9b6419;
  font-size: var(--font-size-md);
  font-weight: 600;
}
</style>
