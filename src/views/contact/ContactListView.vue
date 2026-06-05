<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="通讯录" />

      <!-- Tab切换：教师 / 学生 -->
      <el-tabs v-model="contactType" @tab-change="handleTabChange">
        <el-tab-pane label="教师通讯录" name="teacher" />
        <el-tab-pane label="学生通讯录" name="student" />
      </el-tabs>

      <!-- 搜索 -->
      <el-input
        v-model="searchKeyword"
        placeholder="搜索姓名、学号/工号"
        :prefix-icon="Search"
        clearable
        class="search-input"
        @input="onSearchInput"
      />

      <!-- 教师列表（按院系分组） -->
      <div v-if="contactType === 'teacher'" class="contact-groups">
        <div v-for="group in groupedContacts" :key="group.department" class="contact-group">
          <h4 class="group-title">{{ group.department || '其他' }}</h4>
          <div
            v-for="contact in group.list"
            :key="contact.user_id"
            class="contact-item card card-hover"
            @click="goDetail(contact.user_id)"
          >
            <el-avatar :size="40" :src="contact.avatar_url">
              <el-icon :size="20"><User /></el-icon>
            </el-avatar>
            <div class="contact-info">
              <span class="contact-name">{{ contact.real_name }}</span>
              <span class="contact-desc">{{ contact.title }} · {{ contact.department }}</span>
            </div>
            <el-tag size="small" type="info">{{ contact.title || '教师' }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 学生列表 -->
      <div v-else class="student-list">
        <div
          v-for="contact in studentList"
          :key="contact.user_id"
          class="contact-item card card-hover"
          @click="goDetail(contact.user_id)"
        >
          <el-avatar :size="40" :src="contact.avatar_url">
            <el-icon :size="20"><User /></el-icon>
          </el-avatar>
          <div class="contact-info">
            <span class="contact-name">{{ contact.real_name }}</span>
            <span class="contact-desc">{{ contact.class_name }} · {{ contact.student_id }}</span>
          </div>
          <span v-if="!contact.is_authorized" class="unauthorized-tip">
            暂未开放联系方式
          </span>
        </div>
      </div>

      <EmptyState v-if="isEmpty && !loading" :description="searchKeyword ? '未找到匹配的联系人' : '暂无数据'" />
      <div v-if="loading" class="load-more"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Loading, User } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import * as contactApi from '@/api/contact'

const router = useRouter()

const contactType = ref('teacher')
const searchKeyword = ref('')
const allContacts = ref([])
const studentList = ref([])
const loading = ref(false)
const isEmpty = computed(() => !loading.value && allContacts.value.length === 0 && studentList.value.length === 0)

// 教师按院系分组
const groupedContacts = computed(() => {
  const map = {}
  for (const c of allContacts.value) {
    const dept = c.department || '其他'
    if (!map[dept]) map[dept] = []
    map[dept].push(c)
  }
  return Object.entries(map).map(([department, list]) => ({ department, list }))
})

async function fetchContacts() {
  loading.value = true
  try {
    const res = await contactApi.getContactList({
      type: contactType.value,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      if (contactType.value === 'teacher') {
        allContacts.value = data.list || data.records || data || []
      } else {
        studentList.value = data.list || data.records || data || []
      }
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function handleTabChange() {
  searchKeyword.value = ''
  allContacts.value = []
  studentList.value = []
  fetchContacts()
}

let searchTimer = null
function onSearchInput(val) {
  if (val && val.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(fetchContacts, 300)
}

function goDetail(id) {
  router.push(`/contact/${id}`)
}

onMounted(fetchContacts)
</script>

<style scoped>
.search-input { margin: var(--spacing-md) 0; }

.contact-groups { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.contact-group { }
.group-title {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  padding-left: var(--spacing-xs);
  border-left: 3px solid var(--color-primary);
}

.contact-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
}
.contact-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.contact-name { font-weight: 500; color: var(--color-text-primary); }
.contact-desc { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.unauthorized-tip {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
  font-style: italic;
}

.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
</style>
