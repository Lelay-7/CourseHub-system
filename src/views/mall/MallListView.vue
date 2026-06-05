<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="积分商城">
        <template #extra>
          <div class="score-bar">
            <el-icon><StarFilled /></el-icon>
            <span>我的积分：<strong>{{ userStore.scoreBalance }}</strong></span>
          </div>
        </template>
      </PageHeader>

      <!-- 筛选 -->
      <div class="filter-bar">
        <el-select
          v-model="filters.scoreRange"
          placeholder="积分区间"
          clearable
          style="width: 150px"
          @change="applyFilters"
        >
          <el-option v-for="r in SCORE_RANGE" :key="r.label" :label="r.label" :value="r.label" />
        </el-select>
        <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
          <el-radio-button value="popularity">热度</el-radio-button>
          <el-radio-button value="score">积分</el-radio-button>
          <el-radio-button value="newest">最新</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 左侧分类 + 右侧商品布局 -->
      <div class="mall-layout">
        <div class="mall-categories desktop-only">
          <el-menu :default-active="String(filters.categoryId || '')" @select="onCategorySelect">
            <el-menu-item index="">
              <el-icon><Grid /></el-icon> 全部
            </el-menu-item>
            <el-menu-item v-for="(label, val) in GOODS_TYPE_LABEL" :key="val" :index="String(val)">
              {{ label }}
            </el-menu-item>
          </el-menu>
        </div>

        <div class="goods-grid-wrapper">
          <div v-if="goodsList.length > 0" class="goods-grid">
            <GoodsCard
              v-for="goods in goodsList"
              :key="goods.goods_id"
              :goods="goods"
            />
          </div>
          <EmptyState v-else-if="!loading" description="暂无商品" />

          <div v-if="loading" class="load-more">
            <el-icon class="is-loading"><Loading /></el-icon> 加载中...
          </div>
          <div v-else-if="noMore && goodsList.length > 0" class="load-more no-more">
            没有更多了
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Loading, StarFilled, Grid } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { GOODS_TYPE_LABEL, SCORE_RANGE } from '@/utils/constants'
import * as mallApi from '@/api/mall'

const userStore = useUserStore()

const goodsList = ref([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
const filters = reactive({
  categoryId: null,
  scoreRange: '',
  sortBy: 'popularity'
})

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; goodsList.value = []; noMore.value = false }
  loading.value = true
  try {
    const res = await mallApi.getGoodsList({ page: page.value, pageSize: 20, ...filters })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) goodsList.value = list
      else goodsList.value.push(...list)
      noMore.value = goodsList.value.length >= (data.total || 0)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function applyFilters() { fetchList(true) }

function onCategorySelect(index) {
  filters.categoryId = index ? Number(index) : null
  applyFilters()
}

// 滚动加载
function onScroll() {
  const el = document.querySelector('.app-content')
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 200) {
    if (!noMore.value && !loading.value) { page.value++; fetchList(false) }
  }
}

onMounted(() => {
  fetchList(true)
  const el = document.querySelector('.app-content')
  el?.addEventListener('scroll', onScroll)
})
</script>

<style scoped>
.score-bar {
  display: flex; align-items: center; gap: 4px;
  color: var(--color-warning); font-size: var(--font-size-base);
}
.score-bar strong { font-size: var(--font-size-lg); }

.filter-bar {
  display: flex; gap: var(--spacing-md); align-items: center;
  margin-bottom: var(--spacing-md); flex-wrap: wrap;
}

.mall-layout { display: flex; gap: var(--spacing-md); }
.mall-categories {
  width: 160px; flex-shrink: 0;
  background: var(--color-bg-card); border-radius: var(--radius-md); overflow: hidden;
}
.goods-grid-wrapper { flex: 1; min-width: 0; }

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--spacing-md);
}

.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
.load-more.no-more { color: var(--color-text-placeholder); }

@media (max-width: 767px) {
  .goods-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
