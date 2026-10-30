<template>
  <AppLayout>
    <PageContainer class="settings-page">
      <PageHeader title="设置" subtitle="管理账号、隐私和你的浏览体验" :show-back="true" />
      <div class="settings-layout">
        <nav class="settings-nav" aria-label="设置分区">
          <a v-for="section in sections" :key="section.id" :href="`#${section.id}`" @click.prevent="goSection(section.id)">{{ section.label }}<span aria-hidden="true">↗</span></a>
        </nav>
        <div class="settings-content">
          <section id="account" class="settings-section card" aria-labelledby="account-title">
            <h2 id="account-title">账号与安全</h2>
            <div class="setting-row"><div><h3>登录账号</h3><p>{{ userStore.userInfo?.username || '—' }}</p></div><span class="setting-label">{{ roleLabel }}</span></div>
            <div class="setting-row"><div><h3>个人资料</h3><p>昵称、头像与用于联系的个人信息。</p></div><router-link to="/profile/edit" class="setting-link">编辑个人资料 <span aria-hidden="true">→</span></router-link></div>
            <div class="setting-row">
              <div><h3>密码与找回邮箱</h3>
                <p v-if="security.loading">正在读取账号安全状态…</p>
                <p v-else-if="security.error">暂时无法读取绑定状态。<button type="button" class="inline-retry" @click="loadSecurity">重试</button></p>
                <p v-else-if="security.verified">找回邮箱已验证：{{ security.masked }}</p>
                <p v-else>尚未绑定找回邮箱。</p>
                <p>找回邮箱与个人资料中的联系邮箱独立。</p>
              </div>
              <router-link to="/profile/security" class="setting-link">账号安全 <span aria-hidden="true">→</span></router-link>
            </div>
          </section>

          <section id="privacy" class="settings-section card" aria-labelledby="privacy-title">
            <div class="section-heading"><h2 id="privacy-title">隐私与可见性</h2><span v-if="dirty" class="unsaved-note" role="status">有未保存的修改</span></div>
            <p class="section-description">昵称、头像、角色和院系等基本名片仍可被查看。以下设置控制姓名、学号、班级、职称、办公室等详细资料，以及联系方式。</p>
            <ErrorState v-if="state.loadError" title="隐私设置加载失败" @retry="load" />
            <p v-else-if="state.loading" class="setting-status" role="status">正在读取隐私设置…</p>
            <fieldset class="privacy-fields" :disabled="!editable" :aria-busy="state.loading || state.saving">
              <legend class="sr-only">通讯录详细资料可见范围</legend>
              <div class="setting-row setting-row--stacked"><div><h3>谁可以查看详细资料</h3><p>手机号、办公电话和邮箱还取决于下方的单独选择。</p></div>
                <el-radio-group v-model="draft.contact_visible" aria-label="详细资料可见范围" :disabled="!editable">
                  <el-radio-button :value="1">所有人</el-radio-button><el-radio-button :value="2">仅好友</el-radio-button><el-radio-button :value="3">仅自己</el-radio-button>
                </el-radio-group>
              </div>
              <div class="setting-row"><div><h3>展示手机号</h3><p>在选定范围内展示手机号和办公电话。</p></div><el-switch v-model="draft.phone_visible" aria-label="展示手机号" :active-value="1" :inactive-value="2" :disabled="!editable || draft.contact_visible === 3" /></div>
              <div class="setting-row"><div><h3>展示邮箱</h3><p>在选定范围内展示个人资料中的联系邮箱。</p></div><el-switch v-model="draft.email_visible" aria-label="展示邮箱" :active-value="1" :inactive-value="2" :disabled="!editable || draft.contact_visible === 3" /></div>
            </fieldset>
            <p v-if="state.loaded" class="privacy-summary">{{ privacySummary }}</p>
            <p v-if="state.saveError" class="save-error" role="alert">保存失败，请重试；你的修改已保留。</p>
            <div class="privacy-actions"><el-button type="primary" data-testid="privacy-save" :loading="state.saving" :disabled="!canSave" @click="savePrivacy">保存隐私设置</el-button><el-button :disabled="!dirty || state.saving" @click="discard">放弃修改</el-button></div>
          </section>

          <section id="appearance" class="settings-section card" aria-labelledby="appearance-title">
            <h2 id="appearance-title">界面与显示</h2>
            <p class="section-description">配色切换立即生效，只保存在当前浏览器，不会提交上方的隐私修改。</p>
            <ThemeOptions />
          </section>

          <section id="help" class="settings-section card" aria-labelledby="help-title">
            <h2 id="help-title">帮助与页面维护</h2>
            <div class="setting-row"><div><h3>{{ userStore.isAdmin ? '反馈管理' : '反馈与举报' }}</h3><p>{{ userStore.isAdmin ? '查看用户反馈并回复处理结果。' : '提交问题，回看反馈回复和举报处理结果。' }}</p></div><router-link to="/profile/feedback" class="setting-link">查看 <span aria-hidden="true">→</span></router-link></div>
            <div class="setting-row"><div><h3>使用帮助</h3><p>了解课程、资料和积分的使用方式。</p></div><router-link :to="supportPath" class="setting-link">打开帮助 <span aria-hidden="true">→</span></router-link></div>
            <div class="setting-row"><div><h3>关于选课通</h3><p>版本 {{ appVersion }}</p></div><router-link to="/profile/about" class="setting-link">查看介绍 <span aria-hidden="true">→</span></router-link></div>
            <div class="setting-row"><div><h3>重新加载页面</h3><p>遇到显示异常时可以重试。保留登录和主题；尚未提交的页面内容会丢失。</p></div><el-button plain @click="reloadPage">重新加载页面</el-button></div>
          </section>
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, reactive, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ThemeOptions from '@/components/ThemeOptions.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useUserStore } from '@/stores/user'
import { getAuthSessionId, subscribeAuthSessionChange } from '@/utils/auth'
import { getMyAuth, updateMyAuth } from '@/api/contact'
import { getSecurity } from '@/api/security'
import { usePrivacySettings } from '@/composables/usePrivacySettings'
import { supportPath, appVersion } from '@/utils/support'

const userStore = useUserStore(), route = useRoute(), router = useRouter()
const sections = [{ id: 'account', label: '账号与安全' }, { id: 'privacy', label: '隐私与可见性' }, { id: 'appearance', label: '界面与显示' }, { id: 'help', label: '帮助与页面维护' }]
const roleLabel = computed(() => userStore.isAdmin ? '管理员' : userStore.isTeacher ? '教师' : '学生')
const privacy = usePrivacySettings({ getIdentity: getAuthSessionId, read: getMyAuth, update: updateMyAuth })
const { draft, state, dirty, editable, canSave, load, discard } = privacy
const security = reactive({ loading: true, error: false, verified: false, masked: '' })
let alive = true, securityRequest = 0, ignoreUnload = false
const privacySummary = computed(() => {
  if (draft.contact_visible === 3) return `当前${dirty.value ? '草稿' : '设置'}：详细资料和联系方式仅自己可见。手机号和邮箱的原选择已保留，切回其他范围后生效。`
  const audience = draft.contact_visible === 1 ? '所有登录用户' : '你的好友'
  const visible = [draft.phone_visible === 1 && '手机号及办公电话', draft.email_visible === 1 && '联系邮箱'].filter(Boolean)
  return `当前${dirty.value ? '草稿' : '设置'}：${audience}可以查看详细资料${visible.length ? `和${visible.join('、')}` : '；手机号及办公电话、联系邮箱仅自己可见'}。`
})
async function savePrivacy() { if (await privacy.save()) ElMessage.success('隐私设置已保存') }
async function loadSecurity() {
  const request = ++securityRequest, owner = getAuthSessionId()
  Object.assign(security, { loading: true, error: false, verified: false, masked: '' })
  if (!owner) { security.loading = false; return }
  try {
    const res = await getSecurity()
    if (!alive || owner !== getAuthSessionId() || request !== securityRequest) return
    security.verified = Boolean(res.data?.recovery_email_verified)
    security.masked = res.data?.recovery_email_masked || ''
  } catch { if (alive && owner === getAuthSessionId() && request === securityRequest) security.error = true }
  finally { if (alive && owner === getAuthSessionId() && request === securityRequest) security.loading = false }
}
async function confirmDiscard() {
  if (!privacy.needsLeaveConfirmation()) return true
  try {
    await ElMessageBox.confirm('隐私设置尚未保存，离开后这些修改会丢失。', '离开设置', { confirmButtonText: '放弃并离开', cancelButtonText: '留在此页', type: 'warning' })
    return true
  } catch { return !privacy.needsLeaveConfirmation() }
}
onBeforeRouteLeave(confirmDiscard)
function beforeUnload(event) { if (!ignoreUnload && privacy.needsLeaveConfirmation()) { event.preventDefault(); event.returnValue = '' } }
async function reloadPage() {
  try {
    await ElMessageBox.confirm(dirty.value ? '隐私修改尚未保存，重新加载会放弃这些修改。登录和主题会保留。' : '将重新加载当前页面。登录和主题会保留，尚未提交的页面内容会丢失。', '重新加载页面', { confirmButtonText: '重新加载', cancelButtonText: '取消', type: 'warning' })
    ignoreUnload = true
    window.location.reload()
  } catch { /* Cancel keeps the draft. */ }
}
function goSection(id) { router.replace({ hash: `#${id}`, query: route.query }); scrollToSection(`#${id}`) }
async function scrollToSection(hash) {
  await nextTick()
  if (alive && sections.some(section => `#${section.id}` === hash)) document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'start' })
}
watch(() => route.hash, scrollToSection)
const unsubscribe = subscribeAuthSessionChange(() => { privacy.resetForSession(); loadSecurity(); if (getAuthSessionId()) load() })
onMounted(() => { load(); loadSecurity(); scrollToSection(route.hash); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { alive = false; securityRequest++; unsubscribe(); privacy.dispose(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<style scoped>
.settings-page { --content-max-width: 1120px; }
.settings-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 32px; align-items: start; }
.settings-nav { display: grid; position: sticky; top: 104px; gap: 6px; }
.settings-nav a { display: flex; justify-content: space-between; padding: 12px 14px; border-radius: 10px; text-decoration: none; color: var(--color-text-secondary); font-size: 14px; }
.settings-nav a:hover, .settings-nav a:focus-visible { color: var(--color-primary); background: var(--color-bg-soft); }
.settings-nav a span { color: var(--color-text-placeholder); }
.settings-content { min-width: 0; display: grid; gap: 22px; }
.settings-section { padding: 24px; scroll-margin-top: 104px; border: 1px solid var(--color-border-light); border-radius: 16px; box-shadow: none; }
.settings-section h2 { margin: 0 0 10px; font-size: 19px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.section-description { margin: 0 0 16px; line-height: 1.7; color: var(--color-text-secondary); font-size: 13px; }
.setting-row { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 18px 0; }
.setting-row + .setting-row { border-top: 1px solid var(--color-border-light); }
.setting-row > div { min-width: 0; }
.setting-row h3 { font-size: 14px; font-weight: 650; margin: 0; }
.setting-row p { font-size: 13px; line-height: 1.6; color: var(--color-text-secondary); margin: 5px 0 0; overflow-wrap: anywhere; }
.setting-row--stacked { align-items: flex-start; flex-direction: column; gap: 12px; }
.setting-link { color: var(--color-primary); text-decoration: none; font-size: 13px; flex-shrink: 0; padding: 10px 0; }
.setting-link span { margin-left: 6px; }
.setting-label, .unsaved-note { font-size: 12px; color: var(--color-text-secondary); }
.setting-label { background: var(--color-bg-soft); padding: 5px 10px; border-radius: 6px; }
.privacy-fields { min-width: 0; border: 0; padding: 0; margin: 0; }
.privacy-summary { background: var(--color-bg-soft); border-radius: 8px; padding: 12px 14px; font-size: 13px; line-height: 1.7; color: var(--color-text-regular); }
.privacy-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.privacy-actions :deep(.el-button + .el-button) { margin-left: 0; }
.setting-status { color: var(--color-text-secondary); font-size: 13px; }
.save-error { font-size: 13px; color: var(--color-danger); }
.inline-retry { border: 0; padding: 4px 8px; color: var(--color-primary); background: transparent; cursor: pointer; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.settings-section :deep(.error-state) { min-height: 140px; padding: 0; }
@media (max-width: 900px) { .settings-layout { grid-template-columns: 1fr; gap: 14px; } .settings-nav { position: static; display: flex; flex-wrap: wrap; gap: 4px; } .settings-nav a { padding: 10px; font-size: 12px; } .settings-nav a span { display: none; } }
@media (max-width: 767px) { .settings-section { padding: 18px 14px; scroll-margin-top: 80px; } .settings-content { gap: 16px; } .setting-row { gap: 14px; align-items: flex-start; } .setting-row > .el-button { font-size: 12px; padding: 9px 12px; flex-shrink: 0; } .privacy-fields :deep(.el-radio-button__inner) { padding: 10px 14px; } .setting-row .el-switch { flex-shrink: 0; } }
</style>
