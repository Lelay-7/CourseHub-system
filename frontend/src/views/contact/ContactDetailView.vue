<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="联系人详情" :show-back="true" />

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="5" />
      </div>

      <template v-else-if="contact.user_id">
        <div class="detail-card card">
          <!-- 头像 + 基本身份 -->
          <div class="contact-header">
            <el-avatar :size="72" :src="contact.avatar_url">
              <el-icon :size="36"><User /></el-icon>
            </el-avatar>
            <div class="contact-identity">
              <h2>{{ contact.real_name }}</h2>
              <div class="identity-tags">
                <el-tag>{{ contact.title || (contact.user_type === 1 ? '学生' : '教师') }}</el-tag>
                <el-tag type="info">{{ contact.department }}</el-tag>
              </div>
            </div>
          </div>

          <!-- 详细信息 -->
          <el-descriptions :column="2" border class="info-table">
            <el-descriptions-item label="姓名">{{ contact.real_name }}</el-descriptions-item>
            <el-descriptions-item label="职称">{{ contact.title || '-' }}</el-descriptions-item>
            <el-descriptions-item label="院系">{{ contact.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="办公地点">{{ contact.office || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">
              <template v-if="contact.phone">
                <span class="phone-masked">{{ maskPhone(contact.phone) }}</span>
                <el-button v-if="canShowPhone" type="primary" link size="small" @click="callPhone">
                  <el-icon><Phone /></el-icon> 拨打
                </el-button>
              </template>
              <span v-else class="text-muted">未提供</span>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              <template v-if="contact.email && contact.email_visible">
                <a :href="'mailto:' + contact.email">{{ contact.email }}</a>
              </template>
              <span v-else class="text-muted">暂未开放</span>
            </el-descriptions-item>
            <el-descriptions-item label="个人简介" :span="2">
              {{ contact.bio || '暂无简介' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 学生隐私提示 -->
          <el-alert
            v-if="isStudent && !contact.is_authorized && contact.user_id !== userStore.userInfo?.user_id"
            title="该同学暂未开放联系方式"
            type="info"
            :closable="false"
            show-icon
            class="privacy-alert"
          />
        </div>

        <!-- 快捷操作 -->
        <div class="action-bar card">
          <el-button :icon="Phone" :disabled="!contact.phone" @click="callPhone">
            拨打电话
          </el-button>
          <el-button
            :icon="Message"
            :disabled="!contact.email || !contact.email_visible"
            @click="sendEmail"
          >
            发送邮件
          </el-button>
          <el-button :icon="CopyDocument" @click="copyWechat">
            复制微信号
          </el-button>
        </div>
      </template>

      <ErrorState v-else title="联系人不存在" @retry="fetchDetail" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Phone, Message, CopyDocument } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useUserStore } from '@/stores/user'
import * as contactApi from '@/api/contact'

const route = useRoute()
const userStore = useUserStore()

const contact = ref({})
const loading = ref(true)

const isStudent = computed(() => contact.value.user_type === 1)
const canShowPhone = computed(() =>
  contact.value.user_type !== 1 || // 教师全员可见
  contact.value.is_authorized ||   // 学生已授权
  contact.value.user_id === userStore.userInfo?.user_id // 本人
)

async function fetchDetail() {
  loading.value = true
  try {
    const res = await contactApi.getContactDetail(route.params.id)
    if (res.code === 200) contact.value = res.data || {}
  } catch { /* handled */ }
  finally { loading.value = false }
}

function maskPhone(phone) {
  if (!phone) return ''
  return phone.replace(/^(\d{3})\d{4}(\d+)$/, '$1****$2')
}

async function callPhone() {
  if (!contact.value.phone) return
  try {
    await ElMessageBox.confirm(`确认拨打 ${maskPhone(contact.value.phone)}？`, '拨号确认', {
      confirmButtonText: '拨打', cancelButtonText: '取消', type: 'info'
    })
    window.location.href = `tel:${contact.value.phone}`
  } catch { /* 用户取消 */ }
}

function sendEmail() {
  if (contact.value.email) {
    window.location.href = `mailto:${contact.value.email}`
  }
}

async function copyWechat() {
  try {
    if (contact.value.wechat_id) {
      await navigator.clipboard.writeText(contact.value.wechat_id)
    } else {
      await navigator.clipboard.writeText(contact.value.real_name || '')
    }
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.info('复制失败，请手动复制')
  }
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.detail-card { margin-bottom: var(--spacing-md); padding: var(--spacing-lg); }

.contact-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}
.contact-identity h2 { margin: 0 0 var(--spacing-sm); }
.identity-tags { display: flex; gap: var(--spacing-xs); }

.info-table { margin-top: var(--spacing-md); }
.phone-masked { letter-spacing: 1px; font-family: monospace; }
.text-muted { color: var(--color-text-placeholder); }
.privacy-alert { margin-top: var(--spacing-md); }

.action-bar {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
}
</style>
