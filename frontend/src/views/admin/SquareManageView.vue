<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="广场管理" subtitle="管理同学广场的帖子和评论" />
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <el-table v-else :data="list" border stripe v-loading="loading">
        <el-table-column prop="post_id" label="ID" width="60" />
        <el-table-column prop="nickname" label="用户" width="100" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="course_name" label="关联课程" width="120" />
        <el-table-column prop="tags" label="标签" width="120" />
        <el-table-column prop="create_time" label="发布时间" width="160" />
        <el-table-column label="操作" fixed="right" width="170">
          <template #default="{ row }">
            <el-button size="small" @click="showComments(row)">评论</el-button>
            <el-popconfirm title="确定删除该帖子？" @confirm="handleDelete(row.post_id)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!error && !loading && list.length === 0" description="暂无数据" />
      <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />

      <el-dialog v-model="commentsVisible" title="评论管理" width="min(640px, 94vw)">
        <ErrorState v-if="commentsError" :title="commentsError" @retry="showComments({ post_id: currentPostId })" />
        <el-table v-else :data="comments" border stripe v-loading="commentsLoading">
          <el-table-column prop="nickname" label="用户" width="120" />
          <el-table-column prop="content" label="评论内容" min-width="260" />
          <el-table-column prop="create_time" label="评论时间" width="160" />
          <el-table-column label="操作" fixed="right" width="90">
            <template #default="{ row }">
              <el-popconfirm title="确定删除该评论？" @confirm="handleDeleteComment(row.comment_id)">
                <template #reference><el-button size="small" type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-if="!commentsError && !commentsLoading && comments.length === 0" description="暂无评论" />
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { usePagedList } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listSquarePosts, deleteSquarePost, listSquareComments, deleteSquareComment } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const commentsVisible = ref(false)
const commentsLoading = ref(false)
const comments = ref([]), commentsError = ref('')
const currentPostId = ref(null)

const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(listSquarePosts, () => ({}))

async function handleDelete(id) { try { await deleteSquarePost(id); ElMessage.success('已删除'); fetchList() } catch { /* request displays failure */ } }
async function showComments(row) {
  currentPostId.value = row.post_id
  commentsVisible.value = true
  commentsLoading.value = true
  comments.value = []; commentsError.value = ''
  try {
    const res = await listSquareComments(row.post_id)
    const data = res.data?.list || res.data || []
    comments.value = Array.isArray(data) ? data : []
  } catch { commentsError.value = '评论加载失败' } finally {
    commentsLoading.value = false
  }
}
async function handleDeleteComment(id) {
  try { await deleteSquareComment(id); ElMessage.success('评论已删除'); await showComments({ post_id: currentPostId.value }) } catch { /* request displays failure */ }
}
onMounted(fetchList)
</script>

<style scoped>
.toolbar, .filters { flex-wrap: wrap; gap: 12px; }
.filters .el-input { max-width: 100%; }
@media (max-width: 767px) { .toolbar { align-items: stretch; } .filters { width: 100%; } .filters .el-input, .filters .el-select { width: 100%; max-width: 100%; } }
</style>
