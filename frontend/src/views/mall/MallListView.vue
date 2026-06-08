<template>
  <AppLayout>
    <PageContainer>
      <PageHeader
        title="积分商城"
        subtitle="使用积分兑换学习资料、校园权益和服务。"
      >
        <template #extra>
          <div class="mall-score">我的积分 <strong>{{ userStore.scoreBalance }}</strong></div>
        </template>
      </PageHeader>

      <Toolbar>
        <el-radio-group v-model="category" size="small">
          <el-radio-button v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="sortBy" size="small">
          <el-radio-button value="hot">热度</el-radio-button>
          <el-radio-button value="score">积分</el-radio-button>
          <el-radio-button value="new">最新</el-radio-button>
        </el-radio-group>
      </Toolbar>

      <div class="goods-grid-pro">
        <GoodsCard v-for="goods in visibleGoods" :key="goods.goods_id" :goods="goods" />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const category = ref('全部')
const sortBy = ref('hot')
const categories = ['全部', '资料权益', '校园权益', '学习服务', '实物周边']

const goodsList = [
  { goods_id: 1, category: '资料权益', name: '资料下载券', description: '可抵扣一次积分资料下载，适合期末集中复习。', exchange_score: 60, stock: 42 },
  { goods_id: 2, category: '资料权益', name: '课程评价查看券', description: '查看课程评价摘要，帮助你快速判断课程风格。', exchange_score: 80, stock: 28 },
  { goods_id: 3, category: '学习服务', name: '老师答疑优先卡', description: '用于课程答疑预约优先排队，适合项目和考前问题。', exchange_score: 220, stock: 8 },
  { goods_id: 4, category: '校园权益', name: '图书馆预约券', description: '优先预约研修间 2 小时，方便小组讨论。', exchange_score: 120, stock: 16 },
  { goods_id: 5, category: '校园权益', name: '打印优惠券', description: '用于打印复习提纲、实验报告和答辩材料。', exchange_score: 45, stock: 80 },
  { goods_id: 6, category: '校园权益', name: '校园咖啡券', description: '复习周补给权益，可在合作点位核销。', exchange_score: 88, stock: 24 },
  { goods_id: 7, category: '实物周边', name: '复习资料礼包', description: '包含重点课程复习提纲、错题页和计划卡。', exchange_score: 260, stock: 0 }
]

const visibleGoods = computed(() => {
  let list = category.value === '全部' ? [...goodsList] : goodsList.filter(item => item.category === category.value)
  if (sortBy.value === 'score') list = list.sort((a, b) => a.exchange_score - b.exchange_score)
  if (sortBy.value === 'new') list = list.sort((a, b) => b.goods_id - a.goods_id)
  return list
})
</script>

<style scoped>
.mall-score {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: var(--color-accent-light);
  font-weight: 900;
}

.mall-score strong {
  font-size: var(--font-size-lg);
}

.goods-grid-pro {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: var(--spacing-lg);
}
</style>
