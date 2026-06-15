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
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="1">实物</el-radio-button>
          <el-radio-button :value="2">虚拟</el-radio-button>
          <el-radio-button :value="3">服务</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="sortBy" size="small">
          <el-radio-button value="hot">热度</el-radio-button>
          <el-radio-button value="score">积分</el-radio-button>
          <el-radio-button value="new">最新</el-radio-button>
        </el-radio-group>
      </Toolbar>

      <div v-loading="loading" class="goods-grid-pro">
        <GoodsCard v-for="goods in visibleGoods" :key="goods.goods_id" :goods="goods" />
      </div>
      <EmptyState v-if="!loading && visibleGoods.length === 0" description="暂无商品" />

      <div v-if="total > pageSize" class="goods-pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="handlePageChange"
        />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, ref, watch, onMounted, onActivated } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import request from '@/api/request'

const userStore = useUserStore()
const category = ref(null)
const sortBy = ref('hot')
const goodsList = ref([])
const loading = ref(false)
const total = ref(0)
const pageSize = ref(12)
const currentPage = ref(1)

async function fetchGoods() {
  loading.value = true
  try {
    const res = await request.get('/api/mall/goods', { params: { page: currentPage.value, pageSize: pageSize.value, categoryId: category.value } })
    goodsList.value = res.data?.list || []
    total.value = res.data?.total ?? res.data?.totalCount ?? 0
  } catch { goodsList.value = [] }
  finally { loading.value = false }
}

function handlePageChange(page) {
  currentPage.value = page
  fetchGoods()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const visibleGoods = computed(() => {
  let list = [...goodsList.value]
  // 分类筛选
  if (category.value != null) {
    list = list.filter(g => g.category === category.value)
  }
  // 排序
  if (sortBy.value === 'score') list = list.sort((a, b) => (a.price_score ?? 0) - (b.price_score ?? 0))
  if (sortBy.value === 'new') list = list.sort((a, b) => b.goods_id - a.goods_id)
  return list
})

watch([category, sortBy], () => { currentPage.value = 1; fetchGoods() })

onMounted(fetchGoods)
onActivated(fetchGoods)
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

.goods-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}
</style>
