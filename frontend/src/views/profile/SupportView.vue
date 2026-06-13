<template>
  <AppLayout>
    <div class="page-content support-page">
      <PageHeader title="联系客服" :show-back="true" />

      <div class="support-hero">
        <div>
          <span>服务支持</span>
          <h2>遇到选课、资料或积分问题，可以从这里开始处理</h2>
        </div>
        <el-button type="primary" @click="$router.push('/profile/feedback')">提交反馈</el-button>
      </div>

      <div class="support-grid">
        <div class="support-card card">
          <h3>在线客服</h3>
          <p>工作日 9:00-18:00，优先处理课程信息错误、资料下载异常和积分兑换问题。</p>
          <strong>support@bugaoshan.edu</strong>
        </div>
        <div class="support-card card">
          <h3>常见问题</h3>
          <ul>
            <li>课程评价提交后可获得积分奖励。</li>
            <li>资料下载会按资料标价扣除积分。</li>
            <li>商城兑换记录可在个人中心查看。</li>
          </ul>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
</script>

<style scoped>
.support-page {
  --content-max-width: 980px;
}

.support-hero {
  min-height: 190px;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: 28px;
  color: #fffaf3;
  background: linear-gradient(135deg, #f07462, #f8b25f);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  box-shadow: var(--shadow-md);
}

.support-hero span {
  font-weight: 900;
}

.support-hero h2 {
  max-width: 600px;
  margin: 8px 0 0;
  line-height: 1.35;
}

.support-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-lg);
}

.support-card h3 {
  margin: 0 0 10px;
}

.support-card p,
.support-card li {
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.support-card strong {
  color: var(--color-primary-dark);
}

@media (max-width: 767px) {
  .support-hero,
  .support-grid {
    grid-template-columns: 1fr;
  }
  .support-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--spacing-md);
  }
}
</style>
