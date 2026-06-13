<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="我的收藏" :show-back="true" />

      <!-- Tab切换 -->
      <el-tabs v-model="favType" @tab-change="fetchList(true)">
        <el-tab-pane label="课程" name="course" />
        <el-tab-pane label="资料" name="material" />
      </el-tabs>

      <!-- 课程收藏 -->
      <div v-if="favType === 'course'" class="fav-list">
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
      <div v-else class="fav-list">
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

      <EmptyState v-if="favList.length === 0 && !loading" description="暂无收藏" />
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
import * as favoriteApi from '@/api/favorite'

const favType = ref('course')
const favList = ref([])
const loading = ref(false)

async function fetchList(reset = false) {
  loading.value = true
  try {
    const res = await favoriteApi.getFavoriteList({ type: favType.value })
    if (res.code === 200) {
      const data = res.data?.list || res.data?.records || res.data || []
      favList.value = Array.isArray(data) ? data.filter(item => item?.targetId) : []
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleRemove(item) {
  try {
    const targetId = item.targetId
    await favoriteApi.removeFavorite({ type: favType.value, target_id: targetId })
    await fetchList(true)
    ElMessage.success('已取消收藏')
  } catch { /* handled */ }
}

onMounted(() => { fetchList(true) })
</script>

<style scoped>
.fav-list { display: grid; gap: 10px; }
.fav-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-radius: 16px; background: var(--color-bg-card);
  border: 1px solid rgba(154,66,45,.08);
}
.fav-info strong { display: block; color: #333; }
.fav-info span { font-size: 12px; color: #999; }
.fav-actions { display: flex; gap: 8px; flex-shrink: 0; }
.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
</style>
