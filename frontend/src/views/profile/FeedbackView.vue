<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" :title="userStore.isAdmin ? '用户反馈' : '意见反馈'" :subtitle="userStore.isAdmin ? '查看问题，回复处理结果并保留记录' : '帮我们把选课通做得更好'" />

      <el-radio-group v-if="!userStore.isAdmin" v-model="activeView" style="margin-bottom:20px"><el-radio-button value="feedback">意见反馈</el-radio-button><el-radio-button value="reports">我的举报</el-radio-button></el-radio-group>
      <template v-if="activeView === 'feedback'">
      <ErrorState v-if="myError && !userStore.isAdmin" :title="myError" @retry="fetchMyFeedbacks" />
      <!-- 学生/教师：我的反馈 -->
      <Card v-if="!userStore.isAdmin && myFeedbacks.length > 0" style="padding:20px;margin-bottom:16px">
        <h3 style="margin:0 0 12px">我的反馈</h3>
        <div v-for="fb in myFeedbacks" :key="fb.feedback_id" style="padding:10px 0;border-bottom:1px solid var(--color-border-light)">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span><strong>{{ fb.type }}</strong> · {{ fb.content }}</span>
            <el-tag :type="fb.status === 1 ? 'success' : 'warning'" size="small">{{ fb.status === 1 ? '已回复' : '待处理' }}</el-tag>
          </div>
          <div v-if="fb.status === 1 && fb.reply" style="margin-top:6px;padding:8px 12px;background:var(--color-success-light);border-radius:8px;color:var(--color-text-primary)">
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
        <el-radio-group v-model="filterStatus" @change="resetAndFetch" style="margin-bottom:16px">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button :value="1">已回复</el-radio-button>
        </el-radio-group>

        <ErrorState v-if="error" :title="error" @retry="fetchList" />
        <el-table v-else :data="list" border stripe v-loading="loading">
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
          <el-table-column label="回复" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.reply || '尚未回复' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="create_time" label="提交时间" width="160" />
          <el-table-column label="操作" fixed="right" width="130"><template #default="{ row }"><el-button size="small" @click="openFeedback(row)">{{ row.status === 1 ? '查看详情' : '查看与回复' }}</el-button></template></el-table-column>
        </el-table>
        <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
      </Card>
      </template>
      <MyReportsPanel v-else />
      <el-dialog v-model="replyVisible" title="反馈详情" width="min(600px, 94vw)" :close-on-click-modal="!replying" :show-close="!replying">
        <template v-if="selectedFeedback">
          <p><strong>{{ selectedFeedback.type }}</strong> · {{ selectedFeedback.nickname || '用户 #' + selectedFeedback.user_id }}</p>
          <p class="feedback-content">{{ selectedFeedback.content }}</p>
          <p v-if="selectedFeedback.contact">联系方式：{{ selectedFeedback.contact }}</p>
          <p v-if="selectedFeedback.status === 1" class="feedback-content">回复：{{ selectedFeedback.reply }}</p>
          <el-input v-else v-model="replyTexts[selectedFeedback.feedback_id]" type="textarea" :rows="4" maxlength="1000" show-word-limit :disabled="replying" placeholder="输入回复..." />
          <p v-if="replyError" role="alert">{{ replyError }}</p>
        </template>
        <template #footer><el-button :disabled="replying" @click="replyVisible = false">关闭</el-button><el-button v-if="selectedFeedback?.status === 0" type="primary" :loading="replying" @click="handleReply(selectedFeedback.feedback_id)">回复</el-button></template>
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import MyReportsPanel from '@/components/MyReportsPanel.vue'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
import { usePagedList, useRequestGate } from '@/composables/useListQuery'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'

const userStore = useUserStore()
const myFeedbacks = ref([]), activeView = ref('feedback'), myError = ref('')
const myGate = useRequestGate(() => { myFeedbacks.value = []; activeView.value = 'feedback'; form.content = ''; form.contact = '' })

// 学生/教师
const form = reactive({ type: '功能建议', content: '', contact: '' })
const submitting = ref(false)
async function handleSubmit() {
  if (submitting.value) return
  if (!form.content.trim()) { ElMessage.warning('请填写反馈内容'); return }
  submitting.value = true
  try {
    await request.post('/api/feedback', form)
    ElMessage.success('反馈已提交，感谢你的建议！')
    form.content = ''; form.contact = ''
    fetchMyFeedbacks()
  } catch { /* keep draft for retry */ } finally { submitting.value = false }
}

// 管理员
const filterStatus = ref(undefined)
const replyTexts = reactive({})
const replyVisible = ref(false), selectedFeedback = ref(null), replying = ref(false), replyError = ref('')
const replyGate = useRequestGate(() => { replyVisible.value = false; selectedFeedback.value = null; replying.value = false; Object.keys(replyTexts).forEach(key => delete replyTexts[key]) })
function openFeedback(row) { selectedFeedback.value = row; replyVisible.value = true; replyError.value = '' }

const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(params => request.get('/api/admin/feedback', { params }), () => ({ status: filterStatus.value }))

async function handleReply(feedbackId) {
  if (replying.value) return
  const text = replyTexts[feedbackId]
  if (!text || !text.trim()) { ElMessage.warning('请输入回复内容'); return }
  const ticket = replyGate.begin(); replying.value = true; replyError.value = ''
  try {
    await request.put(`/api/admin/feedback/${feedbackId}/reply`, { reply: text.trim() })
    if (!replyGate.valid(ticket)) return
    ElMessage.success('已回复')
    replyTexts[feedbackId] = ''
    replyVisible.value = false
    fetchList()
  } catch (err) { if (replyGate.valid(ticket) && !err.sessionChanged) replyError.value = err.message || '回复失败，请重试' }
  finally { if (replyGate.valid(ticket)) replying.value = false }
}

async function fetchMyFeedbacks() {
  const ticket = myGate.begin(); myError.value = ''
  try {
    const res = await request.get('/api/feedback/my')
    if (myGate.valid(ticket)) myFeedbacks.value = res.data?.list || res.data || []
  } catch (err) { if (myGate.valid(ticket) && !err.sessionChanged) myError.value = '反馈记录加载失败' }
}

onMounted(() => {
  if (userStore.isAdmin) fetchList()
  else fetchMyFeedbacks()
})
</script>
<style scoped>.feedback-content { white-space: pre-wrap; overflow-wrap: anywhere; } [role=alert] { color: var(--color-danger); }</style>
