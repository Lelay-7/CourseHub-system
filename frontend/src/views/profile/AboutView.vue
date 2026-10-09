<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="关于我们" :show-back="true" />

      <div class="about-card card">
        <div class="about-header">
          <el-icon :size="64" color="var(--color-primary)"><School /></el-icon>
          <h2>不高山选课通</h2>
          <el-tag>V{{ appVersion }}</el-tag>
        </div>

        <el-descriptions :column="1" border class="about-info">
          <el-descriptions-item label="项目名称">不高山选课通</el-descriptions-item>
          <el-descriptions-item label="简介">
            一款基于Web技术的高校学习服务应用，旨在为学生和教师提供便捷的课程信息浏览、学习资料共享、通讯录查询和积分激励机制。
          </el-descriptions-item>
          <el-descriptions-item label="技术栈">
            Vue 3 + Element Plus + Pinia + Vue Router + Axios + Vite
          </el-descriptions-item>
          <el-descriptions-item label="版本号">{{ appVersion }}</el-descriptions-item>
          <el-descriptions-item label="编制单位">成都信息工程大学</el-descriptions-item>
          <el-descriptions-item label="编制人">张义彬</el-descriptions-item>
        </el-descriptions>

        <div class="contact-support">
          <h4>联系客服</h4>
          <el-button :icon="Message" @click="copyEmail">
            复制客服邮箱
          </el-button>
        </div>

        <div class="copyright">
          <p>© 2026 不高山选课通. All rights reserved.</p>
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { School, Message } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'

const appVersion = import.meta.env.VITE_APP_VERSION || '3.0.0'

async function copyEmail() {
  try {
    await navigator.clipboard.writeText('support@bugaoshan.com')
    ElMessage.success('客服邮箱已复制到剪贴板')
  } catch {
    ElMessage.info('客服邮箱：support@bugaoshan.com')
  }
}
</script>

<style scoped>
.about-card {
  text-align: center;
}

.about-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
}
.about-header h2 { margin: 0; }

.about-info {
  margin-bottom: var(--spacing-lg);
  text-align: left;
}

.contact-support {
  margin-bottom: var(--spacing-lg);
}
.contact-support h4 { margin: 0 0 var(--spacing-sm); }

.copyright {
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-light);
}
.copyright p {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}
</style>
