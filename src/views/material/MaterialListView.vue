<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="学习资料" />

      <!-- 搜索 + 筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名、上传者、关键词"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @input="onSearchInput"
        />
        <el-select
          v-model="filters.materialType"
          placeholder="文件类型"
          clearable
          style="width: 130px"
          @change="applyFilters"
        >
          <el-option v-for="(label, val) in MATERIAL_TYPE_LABEL" :key="val" :label="label" :value="Number(val)" />
        </el-select>
        <el-select
          v-model="filters.timeRange"
          placeholder="上传时间"
          clearable
          style="width: 120px"
          @change="applyFilters"
        >
          <el-option v-for="t in MATERIAL_TIME_FILTER" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>

      <!-- 排序 -->
      <div class="sort-bar flex-between">
        <span class="total-count">共 {{ totalCount }} 份资料</span>
        <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
          <el-radio-button value="download_count">下载量</el-radio-button>
          <el-radio-button value="create_time">最新</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 资料列表 -->
      <div v-if="materialList.length > 0" class="material-list">
        <MaterialCard
          v-for="item in materialList"
          :key="item.material_id"
          :material="item"
          @favorite="handleFavorite"
        />
      </div>
      <EmptyState v-else-if="!loading" description="暂无资料" />

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
      <div v-else-if="noMore && materialList.length > 0" class="load-more no-more">
        没有更多了
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated, nextTick } from 'vue'
import { Search, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import MaterialCard from '@/components/MaterialCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useMaterialStore } from '@/stores/material'
import { MATERIAL_TYPE_LABEL, MATERIAL_TIME_FILTER } from '@/utils/constants'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'

const materialStore = useMaterialStore()

const materialList = ref([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
const totalCount = ref(0)
const searchKeyword = ref('')
const filters = reactive({ ...materialStore.filters })

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; materialList.value = []; noMore.value = false }
  loading.value = true
  try {
    const res = await materialApi.getMaterialList({
      page: page.value,
      pageSize: 20,
      ...filters,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) materialList.value = list
      else materialList.value.push(...list)
      totalCount.value = data.total || 0
      noMore.value = materialList.value.length >= totalCount.value
      materialStore.setMaterialList(materialList.value, totalCount.value)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function applyFilters() {
  Object.assign(materialStore.filters, filters)
  fetchList(true)
}

let searchTimer = null
function onSearchInput(val) {
  if (val && val.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { filters.keyword = val; applyFilters() }, 300)
}

async function handleFavorite(material) {
  try {
    if (material.is_favorited) {
      await favoriteApi.removeFavorite({ type: 'material', target_id: material.material_id })
      material.is_favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addFavorite({ type: 'material', target_id: material.material_id })
      material.is_favorited = true
      ElMessage.success('收藏成功')
    }
  } catch { /* handled */ }
}

function onScroll() {
  const el = document.querySelector('.app-content')
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 200) {
    if (!noMore.value && !loading.value) { page.value++; fetchList(false) }
  }
  materialStore.saveScrollTop(scrollTop)
}

onMounted(() => {
  fetchList(true)
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && materialStore.scrollTop) el.scrollTop = materialStore.scrollTop
    el?.addEventListener('scroll', onScroll)
  })
})

onActivated(() => {
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && materialStore.scrollTop) el.scrollTop = materialStore.scrollTop
  })
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}
.search-input { flex: 1; min-width: 200px; }
.sort-bar { margin-bottom: var(--spacing-md); }
.total-count { font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.material-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
.load-more.no-more { color: var(--color-text-placeholder); }
</style>
