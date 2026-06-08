<template>
  <AppLayout>
    <div class="page-content feedback-page">
      <PageHeader title="意见反馈" />

      <div class="feedback-card card">
        <h2>帮我们把选课通做得更好</h2>
        <p>反馈页面当前为前端模拟提交，后续可接入反馈工单接口。</p>

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
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="6"
              maxlength="300"
              show-word-limit
              placeholder="请描述你遇到的问题或建议"
            />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" placeholder="可填写手机号、QQ 或邮箱，选填" />
          </el-form-item>
          <el-button type="primary" size="large" @click="submit">提交反馈</el-button>
        </el-form>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'

const form = reactive({
  type: '功能建议',
  content: '',
  contact: ''
})

function submit() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写反馈内容')
    return
  }
  ElMessage.success('反馈已提交，当前为前端模拟')
  form.content = ''
  form.contact = ''
}
</script>

<style scoped>
.feedback-page {
  --content-max-width: 860px;
}

.feedback-card {
  padding: var(--spacing-xl);
}

.feedback-card h2 {
  margin: 0 0 8px;
  color: var(--color-text-primary);
}

.feedback-card p {
  margin: 0 0 var(--spacing-xl);
  color: var(--color-text-secondary);
}
</style>
