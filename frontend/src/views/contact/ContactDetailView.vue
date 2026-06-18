<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="联系人名片" :show-back="true" />
      <div v-if="loading" class="detail-loading"><LoadingSkeleton type="list" :rows="3" /></div>

      <template v-else-if="contactId">
        <Card>
          <div class="card-header">
            <span class="card-avatar">
              <img
                v-if="contact.avatarUrl && !avatarError"
                :src="contact.avatarUrl"
                :alt="contact.nickname"
                class="avatar-img"
                @error="avatarError = true"
              />
              <span v-else>{{ avatarInitial }}</span>
            </span>
            <div>
              <h2>{{ contact.nickname || contact.real_name || contact.username }}</h2>
              <p>
                <template v-if="contactUsername">{{ contactUsername }} · </template>
                {{ contactRole || USER_TYPE_LABEL[contact.user_type] || '用户' }}
                <template v-if="contact.department"> · {{ contact.department }}</template>
              </p>
            </div>
          </div>
          <el-descriptions :column="2" border style="margin-top:16px">
            <el-descriptions-item v-if="contactUsername" label="账号">{{ contactUsername }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ contact.nickname || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="contact.realName || contact.real_name" label="姓名">{{ contact.realName || contact.real_name }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ contactRole || USER_TYPE_LABEL[contact.user_type] || '-' }}</el-descriptions-item>
            <el-descriptions-item label="院系">{{ contact.department || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="contact.title" label="职称">{{ contact.title }}</el-descriptions-item>
            <el-descriptions-item v-if="contact.className || contact.class_name" label="班级">{{ contact.className || contact.class_name }}</el-descriptions-item>
            <el-descriptions-item label="电话">
              <template v-if="contact.phone">{{ contact.phone }}</template>
              <span v-else class="text-muted">未提供</span>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              <template v-if="contact.email">{{ contact.email }}</template>
              <span v-else class="text-muted">未提供</span>
            </el-descriptions-item>
          </el-descriptions>
        </Card>

        <div class="action-bar">
          <template v-if="contact.friend_status === 'none'">
            <el-button type="primary" @click="handleAddFriend">添加好友</el-button>
          </template>
          <template v-else-if="contact.friend_status === 'pending'">
            <el-tag type="warning" size="large">等待对方同意</el-tag>
          </template>
          <template v-else-if="contact.friend_status === 'pending_receive'">
            <el-button type="primary" @click="handleAddFriend">同意好友申请</el-button>
          </template>
          <template v-else>
            <el-tag type="success" size="large">已是好友</el-tag>
          </template>
          <el-button v-if="contact.phone" @click="callPhone">拨打电话</el-button>
          <el-button v-if="contact.email" @click="sendEmail">发送邮件</el-button>
        </div>
      </template>

      <ErrorState v-else title="用户不存在" @retry="fetchDetail" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { USER_TYPE_LABEL } from '@/utils/constants'
import request from '@/api/request'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

const route = useRoute()
const contact = ref({})
const loading = ref(true)
const avatarError = ref(false)

// 兼容后端 userId（驼峰）与 user_id（下划线）
function getUserId(u) {
  return u?.user_id ?? u?.userId ?? u?.id
}

function getRoleLabel(u) {
  const t = u?.userType ?? u?.user_type
  if (t === 3) return '管理员'
  if (t === 2) return '教师'
  if (t === 1) return '学生'
  if (u?.title) return '教师'
  return ''
}

const contactId = computed(() => getUserId(contact.value))
const avatarInitial = computed(() => (contact.value.nickname || contact.value.realName || contact.value.real_name || contact.value.username || '?').slice(0, 1))
const contactUsername = computed(() => contact.value.username ?? contact.value.studentNo ?? contact.value.student_no ?? '')
const contactRole = computed(() => getRoleLabel(contact.value))

async function fetchDetail() {
  loading.value = true
  try {
    const res = await request.get(`/api/contact/${route.params.id}`)
    contact.value = res.data || {}
  } finally { loading.value = false }
}

async function handleAddFriend() {
  try {
    await request.post(`/api/contact/add/${contactId.value}`)
    ElMessage.success('操作成功')
    fetchDetail()
  } catch { /* handled */ }
}

function callPhone() {
  if (contact.value.phone) window.location.href = `tel:${contact.value.phone}`
}
function sendEmail() {
  if (contact.value.email) window.location.href = `mailto:${contact.value.email}`
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.card-header { display: flex; align-items: center; gap: 16px; }
.card-header h2 { margin: 0; }
.card-header p { margin: 4px 0 0; color: #999; }
.card-avatar {
  width: 64px; height: 64px; border-radius: 18px; flex-shrink: 0;
  background: var(--color-primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; font-weight: 900;
  overflow: hidden;
}
.avatar-img {
  width: 100%; height: 100%; object-fit: cover;
}
.text-muted { color: #999; }
.action-bar { display: flex; gap: 10px; margin-top: 16px; align-items: center; }
</style>
