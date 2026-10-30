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

          <el-radio-button value="price_score">积分从低到高</el-radio-button>
          <el-radio-button value="create_time">最新</el-radio-button>
        </el-radio-group>
      </Toolbar>

      <ErrorState v-if="error" :title="error" @retry="fetchGoods" />
      <div v-else v-loading="loading" class="goods-grid-pro">
        <GoodsCard v-for="goods in goodsList" :key="goods.goods_id" :goods="goods" />
      </div>
      <EmptyState v-if="!error && !loading && goodsList.length === 0" description="暂无商品" />

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
import { ref, watch, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useUserStore } from '@/stores/user'
import { usePagedList, useListMemory } from '@/composables/useListQuery'
import { getGoodsList } from '@/api/mall'
const userStore = useUserStore(), category = ref(null), sortBy = ref('create_time')
const { list: goodsList, loading, error, total, pageSize, page: currentPage, fetchList, resetAndFetch, handlePageChange } = usePagedList(getGoodsList, () => ({ categoryId: category.value, sortBy: sortBy.value }), 12)
const restoreScroll = useListMemory(() => ({ category: category.value, sort: sortBy.value, page: currentPage.value }), state => { category.value = state.category; sortBy.value = state.sort; currentPage.value = state.page })
async function fetchGoods() { await fetchList(); await restoreScroll() }
watch([category, sortBy], resetAndFetch)
onMounted(fetchGoods)
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
  grid-template-columns: repeat(auto-fill, minmax(min(250px, 100%), 1fr));
  gap: var(--spacing-lg);
}

.goods-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}
</style>
