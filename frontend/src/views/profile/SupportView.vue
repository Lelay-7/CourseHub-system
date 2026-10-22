<template>
  <AppLayout>
    <PageContainer class="support-page">
      <PageHeader title="使用帮助" subtitle="找到操作入口，查看问题处理结果" :show-back="true" />
      <section class="support-intro card"><div><h2>遇到问题，从反馈开始</h2><p>请附上相关课程或资料名称、操作步骤和实际表现。回复会保存在反馈记录中，便于继续跟进。</p><p v-if="supportEmail">也可以通过邮箱联系：<a :href="`mailto:${supportEmail}`">{{ supportEmail }}</a></p></div><el-button type="primary" @click="$router.push(feedbackPath)">{{ userStore.isAdmin ? '管理反馈' : '提交与查看反馈' }}</el-button></section>
      <div class="support-grid">
        <section v-for="section in helpSections" :key="section.title" class="support-card card"><h2>{{ section.title }}</h2><p>{{ section.description }}</p><ul><li v-for="item in section.items" :key="item.label"><router-link :to="item.path">{{ item.label }} <span aria-hidden="true">→</span></router-link></li></ul></section>
      </div>
      <p class="scope-note">选课通用于课程信息浏览和个人学习清单管理。学校正式选课、退课和学分认定请以教务系统及学校通知为准。</p>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { supportEmail, feedbackPath } from '@/utils/support'
const userStore = useUserStore()
const helpSections = computed(() => [
  { title: '课程与学习', description: '查看课程信息、记录学习选择，并分享你的课程体验。', items: [{ label: '浏览课程', path: '/course' }, { label: '我的选课清单', path: '/profile/courses' }, { label: '我的课程评价', path: '/profile/reviews' }] },
  { title: '学习资料', description: '下载前查看资料说明与积分价格；上传后可查看审核结果，按驳回原因修改并重新提交。', items: [{ label: '查找学习资料', path: '/material' }, { label: '查看上传与审核进度', path: '/material?tab=my' }, { label: '查看下载记录', path: '/profile/downloads' }] },
  userStore.isAdmin
    ? { title: '商城与订单管理', description: '维护商品价格与库存，处理兑换订单，并填写用户可以回看的处理说明。', items: [{ label: '管理兑换商品', path: '/admin/goods' }, { label: '处理兑换订单', path: '/admin/orders' }] }
    : { title: '积分与兑换', description: '课程评价和资料审核通过可获得相应积分。下载与兑换按页面价格结算，退款会记录在积分明细中。', items: [{ label: '查看积分明细', path: '/profile/score' }, { label: '浏览积分商城', path: '/mall' }, { label: '查看兑换订单及说明', path: '/profile/orders' }] },
  { title: '账号与隐私', description: '找回邮箱需要单独验证。个人资料中的联系邮箱以及手机号，可在设置中选择展示范围。', items: [{ label: '管理密码与找回邮箱', path: '/profile/security' }, { label: '管理通讯录隐私', path: '/profile/settings#privacy' }, { label: '查看好友和申请', path: '/contact?tab=friends' }] }
])
</script>

<style scoped>
.support-page { --content-max-width: 1040px; }
.support-intro { display: flex; align-items: center; justify-content: space-between; gap: 28px; margin-bottom: 24px; padding: 24px; border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-bg-soft); box-shadow: none; }
.support-intro h2, .support-card h2 { font-size: 18px; margin: 0 0 10px; }
.support-intro p, .support-card p { color: var(--color-text-secondary); font-size: 13px; line-height: 1.8; margin: 0; }
.support-intro .el-button { flex-shrink: 0; }
.support-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.support-card { padding: 22px; border: 1px solid var(--color-border-light); border-radius: 14px; box-shadow: none; }
.support-card ul { margin: 12px 0 0; padding: 0; list-style: none; }
.support-card a { display: flex; justify-content: space-between; align-items: center; min-height: 44px; color: var(--color-primary); font-size: 13px; text-decoration: none; border-bottom: 1px solid var(--color-border-light); }
.support-intro a { color: var(--color-primary); }
.scope-note { color: var(--color-text-secondary); font-size: 12px; line-height: 1.8; margin-top: 24px; }
@media (max-width: 767px) { .support-intro { align-items: flex-start; flex-direction: column; gap: 16px; padding: 18px; } .support-grid { grid-template-columns: 1fr; gap: 16px; } .support-card { padding: 18px; } }
</style>
