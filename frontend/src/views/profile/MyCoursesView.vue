<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" title="我的选课" :subtitle="`已选 ${selectionStore.count} 门课程`" />
      <div class="toolbar-row" v-if="list.length > 0">
        <el-badge :value="compareCourses.length" :hidden="compareCourses.length === 0">
          <el-button :type="compareCourses.length ? 'warning' : 'default'" plain @click="showCompareDrawer = true">
            课程对比
          </el-button>
        </el-badge>
      </div>
      <div v-if="list.length > 0" class="course-grid-pro" v-loading="loading">
        <CourseCard
          v-for="course in list"
          :key="course.course_id"
          :course="course"
          :is-selected="true"
          :is-compared="compareCourses.some(c => c.course_id === course.course_id)"
          @compare-toggle="toggleCompare"
          @select-course="handleRemove(course)"
        />
      </div>
      <EmptyState v-else description="还没有选择课程，去选课中心看看吧">
        <el-button type="primary" @click="$router.push('/course')">去选课中心</el-button>
      </EmptyState>

      <el-drawer v-model="showCompareDrawer" title="课程对比" size="720px">
        <div v-if="compareCourses.length" class="compare-list">
          <div v-for="item in compareCourses" :key="item.course_id" class="compare-item">
            <strong>{{ item.course_name }}</strong>
            <span>{{ item.teacher_name }} · {{ item.credit }} 学分 · {{ item.schedule }}</span>
            <el-button text type="danger" @click="removeCompare(item.course_id)">移除</el-button>
          </div>
        </div>
        <EmptyState v-else description="还没有加入对比的课程" />
      </el-drawer>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { useSelectionStore } from '@/stores/selection'
import * as courseApi from '@/api/course'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import CourseCard from '@/components/CourseCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const selectionStore = useSelectionStore()
const loading = ref(false)
const list = ref([])
const compareCourses = ref([])
const showCompareDrawer = ref(false)

async function fetchMyCourses() {
  loading.value = true
  try {
    const res = await courseApi.getSelectedCourses()
    list.value = res.data?.list || res.data || []
    await selectionStore.refresh()
  } catch { list.value = [] }
  finally { loading.value = false }
}

async function handleRemove(course) {
  try {
    await selectionStore.drop(course.course_id)
    ElMessage.success(`已取消 ${course.course_name}`)
    list.value = list.value.filter(c => c.course_id !== course.course_id)
  } catch { /* handled */ }
}

function toggleCompare(course) {
  const exists = compareCourses.value.some(c => c.course_id === course.course_id)
  if (exists) { removeCompare(course.course_id); return }
  if (compareCourses.value.length >= 3) { ElMessage.warning('最多同时对比 3 门课程'); return }
  compareCourses.value.push(course)
}

function removeCompare(id) {
  compareCourses.value = compareCourses.value.filter(c => c.course_id !== id)
}

onMounted(fetchMyCourses)
onActivated(fetchMyCourses)
</script>

<style scoped>
.toolbar-row { margin-bottom: var(--spacing-md); }
.course-grid-pro {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
}
.compare-list { display: grid; gap: var(--spacing-sm); }
.compare-item { padding: var(--spacing-md); border-radius: 18px; background: var(--color-bg-soft); }
.compare-item strong, .compare-item span { display: block; }
.compare-item span { margin-top: 4px; color: var(--color-text-secondary); }
</style>
