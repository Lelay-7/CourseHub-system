<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="我的收藏" :show-back="true" />

      <!-- Tab切换 -->
      <el-tabs v-model="favType" @tab-change="fetchList(true)">
        <el-tab-pane label="课程" name="course" />
        <el-tab-pane label="资料" name="material" />
      </el-tabs>

      <!-- 课程收藏 -->
      <div v-if="favType === 'course'" class="course-grid">
        <div v-for="item in favList" :key="item.target_id" class="fav-card-wrapper">
          <CourseCard :course="item" />
          <el-button
            class="remove-fav"
            type="danger"
            plain
            size="small"
            :icon="Delete"
            @click="handleRemove(item)"
          >
            取消收藏
          </el-button>
        </div>
      </div>

      <!-- 资料收藏 -->
      <div v-else class="material-list">
        <div v-for="item in favList" :key="item.target_id" class="fav-item">
          <MaterialCard :material="item" />
          <el-button
            type="danger" plain size="small" :icon="Delete"
            @click="handleRemove(item)"
          >
            取消收藏
          </el-button>
        </div>
      </div>

      <EmptyState v-if="favList.length === 0 && !loading" description="暂无收藏" />
      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Delete } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import CourseCard from '@/components/CourseCard.vue'
import MaterialCard from '@/components/MaterialCard.vue'
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
      favList.value = (res.data?.list || res.data?.records || res.data || [])
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleRemove(item) {
  try {
    await favoriteApi.removeFavorite({
      type: favType.value,
      target_id: item.target_id || item.course_id || item.material_id
    })
    favList.value = favList.value.filter(f =>
      (f.target_id || f.course_id || f.material_id) !==
      (item.target_id || item.course_id || item.material_id)
    )
    ElMessage.success('已取消收藏')
  } catch { /* handled */ }
}

onMounted(() => { fetchList(true) })
</script>

<style scoped>
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}
.fav-card-wrapper { position: relative; }
.remove-fav { margin-top: var(--spacing-sm); width: 100%; }

.material-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.fav-item { display: flex; flex-direction: column; gap: var(--spacing-xs); }

.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
</style>
