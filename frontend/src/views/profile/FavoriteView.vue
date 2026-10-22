<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="我的收藏" :show-back="true" />

      <!-- Tab切换 -->
      <el-tabs v-model="favType" @tab-change="resetAndFetch">
        <el-tab-pane label="课程" name="course" />
        <el-tab-pane label="资料" name="material" />
      </el-tabs>

      <!-- 课程收藏 -->
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <div v-else-if="favType === 'course'" class="fav-list" v-loading="loading">
        <div v-for="item in favList" :key="item.favoriteId" class="fav-row">
          <div class="fav-info">
            <strong>{{ item.courseName || '内容已失效' }}</strong>
            <span>{{ item.courseCode || '-' }} · {{ item.credit ?? '-' }} 学分 · {{ item.avgRating ?? 0 }} 分</span>
          </div>
          <div class="fav-actions">
            <el-button size="small" @click="$router.push(`/course/${item.targetId}`)">查看</el-button>
            <el-button size="small" type="danger" plain :icon="Delete" @click="handleRemove(item)">取消收藏</el-button>
          </div>
        </div>
      </div>

      <!-- 资料收藏 -->
      <div v-else class="fav-list" v-loading="loading">
        <div v-for="item in favList" :key="item.favoriteId" class="fav-row">
          <div class="fav-info">
            <strong>{{ item.materialName || '内容已失效' }}</strong>
            <span>下载 {{ item.downloadCount || 0 }} 次</span>
          </div>
          <div class="fav-actions">
            <el-button size="small" @click="$router.push(`/material/${item.targetId}`)">查看</el-button>
            <el-button size="small" type="danger" plain :icon="Delete" @click="handleRemove(item)">取消收藏</el-button>
          </div>
        </div>
      </div>

      <EmptyState v-if="favList.length === 0 && !loading && !error" description="暂无收藏" />
      <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Delete } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
import { usePagedList, useListMemory } from '@/composables/useListQuery'
import * as favoriteApi from '@/api/favorite'

const favType = ref('course')
const { list: favList, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(async params => {
  const res = await favoriteApi.getFavoriteList({ type: params.type })
  const data = res.data?.list || res.data?.records || res.data || []
  const items = Array.isArray(data) ? data.filter(item => item?.targetId) : []
  return { ...res, data: { list: items.slice((params.page - 1) * params.pageSize, params.page * params.pageSize), total: items.length } }
}, () => ({ type: favType.value }))
const restoreScroll = useListMemory(() => ({ type: favType.value, page: page.value }), state => { favType.value = state.type; page.value = state.page })
const removing = new Set()

async function handleRemove(item) {
  const type = favType.value, key = type + ':' + item.targetId
  if (removing.has(key)) return
  removing.add(key)
  try {
    const targetId = item.targetId
    await favoriteApi.removeFavorite({ type, target_id: targetId })
    if (type === favType.value) await fetchList()
    ElMessage.success('已取消收藏')
  } catch { /* handled */ } finally { removing.delete(key) }
}

onMounted(async () => { await fetchList(); await restoreScroll() })
</script>

<style scoped>
.fav-list { display: grid; gap: 10px; }
.fav-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-radius: 16px; background: var(--color-bg-card);
  border: 1px solid color-mix(in srgb, var(--color-primary) 8%, transparent);
}
.fav-info strong { display: block; color: var(--color-text-primary); }
.fav-info span { font-size: 12px; color: var(--color-text-secondary); }
.fav-actions { display: flex; gap: 8px; flex-shrink: 0; }
.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
.fav-info { min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 600px) { .fav-row { align-items: stretch; flex-direction: column; gap: 14px; } .fav-actions { flex-wrap: wrap; } }
</style>
