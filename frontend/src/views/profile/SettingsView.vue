<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="设置" :show-back="true" />

      <div class="settings-section card">
        <h3>界面配色</h3>
        <ThemeOptions />
        <p class="setting-desc">切换立即生效，配色会保存在当前浏览器中。</p>
      </div>

      <!-- 消息通知 -->
      <div class="settings-section card">
        <h3>消息通知</h3>
        <div class="setting-item flex-between">
          <span>课程通知</span>
          <el-switch v-model="settings.courseNotify" />
        </div>
        <div class="setting-item flex-between">
          <span>活动提醒</span>
          <el-switch v-model="settings.activityNotify" />
        </div>
        <div class="setting-item flex-between">
          <span>系统消息</span>
          <el-switch v-model="settings.systemNotify" />
        </div>
      </div>

      <!-- 隐私设置 -->
      <div class="settings-section card">
        <h3>隐私设置</h3>
        <ErrorState v-if="privacyError" title="隐私设置加载失败" @retry="loadPrivacy" />
        <div class="setting-item flex-between">
          <div>
            <span>联系方式可见范围</span>
            <p class="setting-desc">姓名、学号和联系方式按此范围展示。</p>
          </div>
          <el-select v-model="settings.contact_visible" style="width: 140px">
            <el-option label="所有人可见" :value="1" />
            <el-option label="仅好友可见" :value="2" />
            <el-option label="仅自己可见" :value="3" />
          </el-select>
        </div>
        <div class="setting-item flex-between">
          <div>
            <span>展示手机号</span>
            <p class="setting-desc">在上述范围内展示手机号，关闭后仅自己可见。</p>
          </div>
          <el-switch v-model="settings.phone_visible" :active-value="1" :inactive-value="2" />
        </div>
        <div class="setting-item flex-between">
          <div>
            <span>展示邮箱</span>
            <p class="setting-desc">在上述范围内展示邮箱，关闭后仅自己可见。</p>
          </div>
          <el-switch v-model="settings.email_visible" :active-value="1" :inactive-value="2" />
        </div>
      </div>

      <!-- 缓存 -->
      <div class="settings-section card">
        <div class="setting-item flex-between">
          <div>
            <span>清除缓存</span>
            <p class="setting-desc">当前缓存大小：{{ cacheSize }}</p>
          </div>
          <el-button type="danger" plain size="small" @click="handleClearCache">
            清除
          </el-button>
        </div>
      </div>

      <!-- 保存按钮 -->
      <el-button type="primary" :loading="saving" :disabled="privacyLoading || privacyError" style="width: 100%; margin-top: 16px" @click="saveSettings">
        保存设置
      </el-button>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ThemeOptions from '@/components/ThemeOptions.vue'
import ErrorState from '@/components/ErrorState.vue'
import { getCacheSizeFormatted, clearAll } from '@/utils/storage'
import { getMyAuth, updateMyAuth } from '@/api/contact'

const settings = reactive({
  courseNotify: true,
  activityNotify: true,
  systemNotify: true,
  phone_visible: 2,
  email_visible: 2,
  contact_visible: 2
})

const cacheSize = ref('0 B')
const saving = ref(false)
const privacyLoading = ref(true)
const privacyError = ref(false)

function refreshCacheSize() {
  cacheSize.value = getCacheSizeFormatted()
}

async function handleClearCache() {
  try {
    await ElMessageBox.confirm('确认清除所有缓存数据吗？清除后需要重新加载', '操作确认', {
      confirmButtonText: '确认清除', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  clearAll()
  refreshCacheSize()
  ElMessage.success('缓存已清除')
  setTimeout(() => window.location.reload(), 1000)
}

async function saveSettings() {
  if (saving.value || privacyLoading.value || privacyError.value) return
  saving.value = true
  try {
    await updateMyAuth({
      phone_visible: settings.phone_visible,
      email_visible: settings.email_visible,
      contact_visible: settings.contact_visible
    })
    localStorage.setItem('user_settings', JSON.stringify(settings))
    ElMessage.success('设置已保存')
  } catch { /* The request layer reports server failures. */ }
  finally { saving.value = false }
}

async function loadPrivacy() {
  privacyLoading.value = true
  privacyError.value = false
  try {
    const res = await getMyAuth()
    if (res.code === 200 && res.data) {
      settings.phone_visible = res.data.phone_visible ?? 2
      settings.email_visible = res.data.email_visible ?? 2
      settings.contact_visible = res.data.contact_visible ?? 2
    }
  } catch { privacyError.value = true }
  finally { privacyLoading.value = false }
}

onMounted(async () => {
  refreshCacheSize()
  const saved = localStorage.getItem('user_settings')
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      settings.courseNotify = parsed.courseNotify ?? true
      settings.activityNotify = parsed.activityNotify ?? true
      settings.systemNotify = parsed.systemNotify ?? true
    } catch {}
  }
  await loadPrivacy()
})
</script>

<style scoped>
.settings-section {
  margin-bottom: var(--spacing-md);
}
.settings-section h3 {
  margin: 0 0 var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border-light);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.setting-item {
  padding: var(--spacing-sm) 0;
  gap: 16px;
}
.setting-item + .setting-item {
  border-top: 1px solid var(--color-border-light);
}
.setting-desc {
  margin: 4px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}
</style>
