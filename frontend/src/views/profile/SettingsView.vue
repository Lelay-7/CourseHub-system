<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="设置" :show-back="true" />

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
        <div class="setting-item flex-between">
          <div>
            <span>手机号可见范围</span>
            <p class="setting-desc">控制其他用户是否可以看到你的手机号</p>
          </div>
          <el-select v-model="settings.phoneVisibility" style="width: 140px">
            <el-option label="仅自己可见" :value="0" />
            <el-option label="授权可见" :value="1" />
            <el-option label="所有人可见" :value="2" />
          </el-select>
        </div>
        <div class="setting-item flex-between">
          <div>
            <span>邮箱可见范围</span>
            <p class="setting-desc">控制其他用户是否可以看到你的邮箱</p>
          </div>
          <el-select v-model="settings.emailVisibility" style="width: 140px">
            <el-option label="仅自己可见" :value="0" />
            <el-option label="授权可见" :value="1" />
            <el-option label="所有人可见" :value="2" />
          </el-select>
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
      <el-button type="primary" style="width: 100%; margin-top: 16px" @click="saveSettings">
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
import { getCacheSizeFormatted, clearAll } from '@/utils/storage'
import { getMyAuth, updateMyAuth } from '@/api/contact'

const settings = reactive({
  courseNotify: true,
  activityNotify: true,
  systemNotify: true,
  phoneVisibility: 1,
  emailVisibility: 0
})

const cacheSize = ref('0 B')

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
  localStorage.setItem('user_settings', JSON.stringify(settings))
  try {
    await updateMyAuth({
      phoneVisibility: settings.phoneVisibility,
      emailVisibility: settings.emailVisibility
    })
    ElMessage.success('设置已保存')
  } catch {
    ElMessage.success('部分设置已保存（同步到服务器失败）')
  }
}

onMounted(async () => {
  refreshCacheSize()
  // 从localStorage恢复通知设置
  const saved = localStorage.getItem('user_settings')
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      settings.courseNotify = parsed.courseNotify ?? true
      settings.activityNotify = parsed.activityNotify ?? true
      settings.systemNotify = parsed.systemNotify ?? true
    } catch {}
  }
  // 从后端加载隐私设置
  try {
    const res = await getMyAuth()
    if (res.code === 200 && res.data) {
      settings.phoneVisibility = res.data.phoneVisibility ?? settings.phoneVisibility
      settings.emailVisibility = res.data.emailVisibility ?? settings.emailVisibility
    }
  } catch { /* 使用localStorage中的值 */ }
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
