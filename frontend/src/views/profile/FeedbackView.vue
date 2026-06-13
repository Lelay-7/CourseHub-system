<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" :title="userStore.isAdmin ? '接受反馈' : '意见反馈'" :subtitle="userStore.isAdmin ? '查看并回复用户提交的反馈' : '帮我们把选课通做得更好'" />

      <!-- 学生/教师：我的反馈 -->
      <Card v-if="!userStore.isAdmin && myFeedbacks.length > 0" style="padding:20px;margin-bottom:16px">
        <h3 style="margin:0 0 12px">我的反馈</h3>
        <div v-for="fb in myFeedbacks" :key="fb.feedback_id" style="padding:10px 0;border-bottom:1px solid #f0e4da">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span><strong>{{ fb.type }}</strong> · {{ fb.content }}</span>
            <el-tag :type="fb.status === 1 ? 'success' : 'warning'" size="small">{{ fb.status === 1 ? '已回复' : '待处理' }}</el-tag>
          </div>
          <div v-if="fb.status === 1 && fb.reply" style="margin-top:6px;padding:8px 12px;background:#f0faf5;border-radius:8px;color:#333">
            💬 管理员回复：{{ fb.reply }}
          </div>
        </div>
      </Card>

      <!-- 学生/教师：提交反馈表单 -->
      <Card v-if="!userStore.isAdmin" style="padding:20px">
        <el-form label-position="top">
          <el-form-item label="反馈类型">
            <el-radio-group v-model="form.type">
              <el-radio-button label="功能建议" />
              <el-radio-button label="资料问题" />
              <el-radio-button label="课程信息错误" />
              <el-radio-button label="其他" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="反馈内容">
            <el-input v-model="form.content" type="textarea" :rows="6" maxlength="1000" show-word-limit placeholder="请描述你遇到的问题或建议" />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" placeholder="可填写手机号、QQ 或邮箱，选填" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">提交反馈</el-button>
        </el-form>
      </Card>

      <!-- 管理员：反馈列表 + 回复 -->
      <Card v-else style="padding:20px">
        <el-radio-group v-model="filterStatus" @change="fetchList" style="margin-bottom:16px">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button :value="1">已回复</el-radio-button>
        </el-radio-group>

        <el-table :data="list" border stripe v-loading="loading">
          <el-table-column prop="feedback_id" label="ID" width="60" />
          <el-table-column prop="nickname" label="用户" width="100" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <el-table-column prop="contact" label="联系方式" width="140" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '已回复' : '待处理' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回复" min-width="180">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <span style="color:#666">{{ row.reply }}</span>
              </template>
              <template v-else>
                <el-input v-model="replyTexts[row.feedback_id]" placeholder="输入回复..." size="small" style="width:160px" />
                <el-button size="small" type="primary" style="margin-left:8px" @click="handleReply(row.feedback_id)">回复</el-button>
              </template>
            </template>
          </el-table-column>
          <el-table-column prop="create_time" label="提交时间" width="160" />
        </el-table>
      </Card>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'

const userStore = useUserStore()
const myFeedbacks = ref([])

// 学生/教师
const form = reactive({ type: '功能建议', content: '', contact: '' })
const submitting = ref(false)
async function handleSubmit() {
  if (!form.content.trim()) { ElMessage.warning('请填写反馈内容'); return }
  submitting.value = true
  try {
    await request.post('/api/feedback', form)
    ElMessage.success('反馈已提交，感谢你的建议！')
    form.content = ''; form.contact = ''
    fetchMyFeedbacks()
  } finally { submitting.value = false }
}

// 管理员
const list = ref([])
const loading = ref(false)
const filterStatus = ref(undefined)
const replyTexts = reactive({})

async function fetchList() {
  loading.value = true
  try {
    const params = filterStatus.value !== undefined ? { status: filterStatus.value } : {}
    const res = await request.get('/api/admin/feedback', { params })
    list.value = res.data?.list || res.data || []
  } finally { loading.value = false }
}

async function handleReply(feedbackId) {
  const text = replyTexts[feedbackId]
  if (!text || !text.trim()) { ElMessage.warning('请输入回复内容'); return }
  await request.put(`/api/admin/feedback/${feedbackId}/reply`, { reply: text })
  ElMessage.success('已回复')
  replyTexts[feedbackId] = ''
  fetchList()
}

async function fetchMyFeedbacks() {
  try {
    const res = await request.get('/api/feedback/my')
    myFeedbacks.value = res.data?.list || res.data || []
  } catch { myFeedbacks.value = [] }
}

onMounted(() => {
  if (userStore.isAdmin) fetchList()
  else fetchMyFeedbacks()
})
</script>
