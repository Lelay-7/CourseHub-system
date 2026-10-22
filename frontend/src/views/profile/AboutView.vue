<template>
  <AppLayout>
    <PageContainer class="about-page">
      <PageHeader title="关于选课通" :show-back="true" />
      <section class="about-card card">
        <div class="about-identity"><el-icon :size="42"><School /></el-icon><div><h2>不高山选课通</h2><p>CourseHub · 版本 {{ appVersion }}</p></div></div>
        <p class="about-description">为学生和教师提供课程信息浏览、学习资料共享、同学交流与个人学习记录服务。</p>
        <div class="about-scope"><h3>让课程信息与学习资源更好找</h3><p>浏览课程和评价，整理自己的学习清单，分享资料，与好友交流课程与学习。积分用于资料下载和商城兑换，变动均可查看明细。</p><p>本应用的学习清单不替代学校教务系统。正式选课、退课及学分认定以学校安排为准。</p></div>
        <dl class="project-info"><div><dt>编制单位</dt><dd>成都信息工程大学</dd></div><div><dt>编制人</dt><dd>张义彬</dd></div></dl>
        <div class="about-links"><router-link :to="supportPath">使用帮助 <span aria-hidden="true">→</span></router-link><router-link :to="feedbackPath">反馈与处理记录 <span aria-hidden="true">→</span></router-link><el-button v-if="supportEmail" plain :icon="Message" @click="copyEmail">复制联系邮箱</el-button></div>
        <p class="copyright">© 2026 不高山选课通</p>
      </section>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { School, Message } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import { supportEmail, supportPath, feedbackPath, appVersion } from '@/utils/support'
async function copyEmail() {
  try { await navigator.clipboard.writeText(supportEmail); ElMessage.success('联系邮箱已复制') }
  catch { ElMessage.info(`联系邮箱：${supportEmail}`) }
}
</script>

<style scoped>
.about-page { --content-max-width: 820px; }
.about-card { padding: 32px; border: 1px solid var(--color-border-light); border-radius: 16px; box-shadow: none; }
.about-identity { display: flex; align-items: center; gap: 18px; color: var(--color-primary); }
.about-identity h2 { margin: 0; font-size: 24px; color: var(--color-text-primary); }
.about-identity p { margin: 7px 0 0; font-size: 12px; color: var(--color-text-secondary); }
.about-description { margin: 26px 0; font-size: 15px; line-height: 1.8; color: var(--color-text-regular); }
.about-scope { padding: 20px; border-radius: 10px; background: var(--color-bg-soft); }
.about-scope h3 { margin: 0 0 10px; font-size: 16px; }
.about-scope p { color: var(--color-text-secondary); font-size: 13px; line-height: 1.8; margin: 8px 0 0; }
.project-info { margin: 20px 0; }
.project-info > div { display: flex; justify-content: space-between; gap: 20px; padding: 14px 0; font-size: 13px; border-bottom: 1px solid var(--color-border-light); }
.project-info dt { color: var(--color-text-secondary); }
.project-info dd { margin: 0; text-align: right; }
.about-links { display: flex; align-items: center; flex-wrap: wrap; gap: 16px 28px; }
.about-links a { padding: 10px 0; color: var(--color-primary); font-size: 13px; text-decoration: none; }
.copyright { font-size: 12px; color: var(--color-text-secondary); margin: 26px 0 0; }
@media (max-width: 767px) { .about-card { padding: 20px 16px; } .about-identity h2 { font-size: 21px; } }
</style>
