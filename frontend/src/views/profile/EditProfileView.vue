<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" title="编辑资料" subtitle="修改你的个人信息" />

      <Card>
        <h3 style="margin-bottom:16px;padding-left:12px">个人信息设置</h3>
        <el-form :model="form" :disabled="saving" label-width="100px" label-position="right" style="max-width:500px">
          <el-form-item label="头像" class="avatar-item">
            <div class="avatar-wrap">
              <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarChange" />
              <button type="button" class="avatar-button" :disabled="avatarSaving || saving" aria-label="更换头像" @click="$refs.avatarInput.click()">
                <el-avatar :size="72" :src="userStore.userInfo?.avatar_url" class="avatar-pic">
                  <el-icon :size="32"><User /></el-icon>
                </el-avatar>
              </button>
              <p class="avatar-hint">{{ avatarSaving ? '正在上传…' : '点击头像更换' }}</p>
            </div>
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" maxlength="20" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="选填" />
          </el-form-item>
          <el-form-item label="联系邮箱">
            <el-input v-model="form.email" placeholder="选填" />
          </el-form-item>
          <el-form-item v-if="!userStore.isAdmin" label="院系">
            <el-input v-model="form.department" placeholder="如：计算机学院" />
          </el-form-item>
          <el-form-item v-if="userStore.isStudent" label="班级">
            <el-input v-model="form.className" placeholder="如：计科246" />
          </el-form-item>
          <el-form-item v-if="userStore.isStudent" label="学号">
            <el-input v-model="form.studentNo" placeholder="学号" />
          </el-form-item>
          <el-form-item v-if="userStore.isTeacher" label="职称">
            <el-input v-model="form.title" placeholder="如：教授" />
          </el-form-item>
          <el-form-item v-if="userStore.isTeacher" label="办公室">
            <el-input v-model="form.officeLocation" placeholder="如：信达楼B507" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
          </el-form-item>
        </el-form>

        <el-divider />
        <div class="privacy-entry">
          <div><h3>谁可以查看我的资料</h3><p class="privacy-hint">在设置中统一管理详细资料、手机号和联系邮箱的可见范围。找回邮箱请前往账号安全设置。</p></div>
          <router-link to="/profile/settings#privacy">管理通讯录隐私</router-link>
        </div>
      </Card>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import request from '@/api/request'
import * as authApi from '@/api/auth'
import { getAuthSessionId } from '@/utils/auth'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'

const userStore = useUserStore()
const saving = ref(false)
const avatarSaving = ref(false)
const owner = getAuthSessionId()
let alive = true
const ownsSession = () => alive && owner === getAuthSessionId()
onBeforeUnmount(() => { alive = false })

const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  department: '',
  className: '',
  studentNo: '',
  title: '',
  officeLocation: ''
})

onMounted(() => {
  const u = userStore.userInfo || {}
  form.nickname = u.nickname || ''
  form.phone = u.phone || ''
  form.email = u.email || ''
  form.department = u.department || ''
  form.className = u.className || u.class_name || ''
  form.studentNo = u.studentNo || u.student_no || ''
  form.title = u.title || ''
  form.officeLocation = u.officeLocation || u.office_location || ''
})

async function handleSave() {
  if (saving.value || !ownsSession()) return
  const snapshot = { ...form }
  saving.value = true
  try {
    await request.put('/api/user/profile', snapshot)
    if (!ownsSession()) return
    userStore.updateUserInfo(snapshot)
    ElMessage.success('资料已更新')
  } catch { /* The shared request handler reports the failure; retain the form for retry. */ }
  finally { if (ownsSession()) saving.value = false }
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || avatarSaving.value || !ownsSession()) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  avatarSaving.value = true
  try {
    const res = await authApi.uploadAvatar(file)
    if (!ownsSession()) return
    userStore.updateUserInfo({ avatar_url: `${res.data?.avatar_url}?v=${Date.now()}` })
    ElMessage.success('头像已更新')
  } catch { /* The shared request handler reports upload failures. */ }
  finally { if (ownsSession()) avatarSaving.value = false }
}
</script>

<style scoped>
.privacy-hint { color: var(--color-text-secondary); line-height: 1.6; font-size: 12px; }
.privacy-entry { display: flex; align-items: center; gap: 20px; justify-content: space-between; }
.privacy-entry h3 { font-size: 16px; margin: 0; }
.privacy-entry a { flex-shrink: 0; color: var(--color-primary); font-size: 14px; padding: 10px 0; }
@media (max-width: 767px) { .privacy-entry { align-items: flex-start; flex-direction: column; gap: 4px; } }
.avatar-item :deep(.el-form-item__content) {
  align-items: center;
}
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 10px;
}
.avatar-pic {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.avatar-button { border: 0; border-radius: 50%; background: transparent; padding: 0; display: flex; cursor: pointer; }
.avatar-button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 4px; }
.avatar-button:disabled { cursor: wait; opacity: .6; }
.avatar-pic:hover {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 30%, transparent);
}
.avatar-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
