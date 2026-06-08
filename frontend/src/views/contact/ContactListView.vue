<template>
  <AppLayout>
    <PageContainer compact>
      <PageHeader
        title="师生通讯录"
        subtitle="查看课程相关教师、同学和学院联系人。"
      />

      <Toolbar>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索姓名、学号、工号、课程名"
          :prefix-icon="Search"
          clearable
          @input="onSearchInput"
        />
        <el-select v-model="filters.department" placeholder="学院" clearable>
          <el-option v-for="dept in departments" :key="dept" :label="dept" :value="dept" />
        </el-select>
        <el-select v-model="filters.title" placeholder="职称" clearable>
          <el-option label="教授" value="教授" />
          <el-option label="副教授" value="副教授" />
          <el-option label="讲师" value="讲师" />
        </el-select>
        <el-select v-model="filters.course" placeholder="关联课程" clearable>
          <el-option v-for="course in courseOptions" :key="course" :label="course" :value="course" />
        </el-select>
      </Toolbar>

      <el-tabs v-model="contactType" class="contact-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="教师通讯录" name="teacher" />
        <el-tab-pane label="学生通讯录" name="student" />
      </el-tabs>

      <div v-if="groupedContacts.length" class="contact-groups">
        <section v-for="group in groupedContacts" :key="group.department" class="contact-group">
          <SectionTitle :title="group.department || '其他学院'" />
          <div class="contact-grid">
            <article v-for="contact in group.list" :key="contact.userId || contact.user_id" class="contact-card">
              <el-avatar :size="54" :src="contact.avatarUrl || contact.avatar_url">
                <el-icon :size="24"><User /></el-icon>
              </el-avatar>
              <div class="contact-info">
                <h3>{{ contact.realName || contact.real_name }}</h3>
                <p v-if="contactType === 'teacher'">{{ contact.title || '教师' }} · {{ contact.department }}</p>
                <p v-else>{{ contact.department }} · {{ contact.student_id || contact.class_name }}</p>
                <div class="contact-course">
                  {{ contactType === 'teacher' ? teacherCourse(contact) : studentCourse(contact) }}
                </div>
              </div>
              <div class="contact-actions">
                <el-button plain @click="$router.push(contactType === 'teacher' ? '/course' : '/course')">
                  {{ contactType === 'teacher' ? '查看课程' : '查看共同课程' }}
                </el-button>
                <el-button type="primary" @click="$router.push(`/contact/${contact.userId || contact.user_id}`)">
                  {{ contactType === 'teacher' ? '联系老师' : '联系同学' }}
                </el-button>
              </div>
            </article>
          </div>
        </section>
      </div>

      <EmptyState v-else-if="!loading" :description="searchKeyword ? '未找到匹配联系人' : '暂无联系人'" />
      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon>
        加载中...
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Loading, User } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import * as contactApi from '@/api/contact'

const contactType = ref('teacher')
const searchKeyword = ref('')
const contactList = ref([])
const loading = ref(false)
const filters = reactive({
  department: '',
  title: '',
  course: ''
})

const courseOptions = ['算法与数据结构', 'Web 前端工程实践', '数据库系统原理', '软件工程与项目管理']
const departments = computed(() => [...new Set(contactList.value.map(item => item.department).filter(Boolean))])

const filteredContacts = computed(() => {
  const q = searchKeyword.value.trim().toLowerCase()
  return contactList.value.filter(item => {
    const text = `${item.realName || item.real_name} ${item.studentId || item.student_id || ''} ${item.department || ''} ${item.title || ''} ${teacherCourse(item)}`.toLowerCase()
    return (!q || text.includes(q)) &&
      (!filters.department || item.department === filters.department) &&
      (!filters.title || item.title === filters.title) &&
      (!filters.course || teacherCourse(item).includes(filters.course) || studentCourse(item).includes(filters.course))
  })
})

const groupedContacts = computed(() => {
  const map = {}
  for (const contact of filteredContacts.value) {
    const dept = contact.department || '其他学院'
    if (!map[dept]) map[dept] = []
    map[dept].push(contact)
  }
  return Object.entries(map).map(([department, list]) => ({ department, list }))
})

function teacherCourse(contact) {
  const map = {
    陈明: '算法与数据结构',
    刘青: 'Web 前端工程实践',
    王雪: '数据库系统原理'
  }
  return map[contact.real_name] || '课程待确认'
}

function studentCourse(contact) {
  const map = {
    李欣: '共同课程：数据库系统原理、Web 前端工程实践',
    周可: '共同课程：算法与数据结构'
  }
  return map[contact.real_name] || '共同课程待确认'
}

async function fetchContacts() {
  loading.value = true
  try {
    const res = await contactApi.getContactList({
      type: contactType.value,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      contactList.value = data.list || data.records || data || []
    }
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  contactList.value = []
  fetchContacts()
}

let searchTimer = null
function onSearchInput(value) {
  if (value && value.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(fetchContacts, 260)
}

onMounted(fetchContacts)
</script>

<style scoped>
.contact-tabs {
  margin-bottom: var(--spacing-lg);
}

.contact-tabs :deep(.el-tabs__active-bar) {
  background: var(--color-primary);
}

.contact-tabs :deep(.el-tabs__item.is-active),
.contact-tabs :deep(.el-tabs__item:hover) {
  color: var(--color-primary);
}

.contact-groups {
  display: grid;
  gap: var(--spacing-lg);
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.contact-card {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
}

.contact-info {
  min-width: 0;
}

.contact-info h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 900;
}

.contact-info p {
  margin: 5px 0;
  color: var(--color-text-secondary);
}

.contact-course {
  color: var(--color-text-regular);
  line-height: 1.6;
}

.contact-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.load-more {
  text-align: center;
  padding: var(--spacing-lg);
  color: var(--color-text-secondary);
}

@media (max-width: 900px) {
  .contact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
