<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" title="编辑资料" subtitle="修改你的个人信息" />

      <Card>
        <h3 style="margin-bottom:16px;padding-left:12px">个人信息设置</h3>
        <el-form :model="form" label-width="100px" label-position="right" style="max-width:500px">
          <el-form-item label="头像" class="avatar-item">
            <div class="avatar-wrap">
              <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarChange" />
                <el-avatar :size="72" :src="userStore.userInfo?.avatar_url" class="avatar-pic" style="cursor:pointer" @click="$refs.avatarInput.click()">
                  <el-icon :size="32"><User /></el-icon>
                </el-avatar>
              <p class="avatar-hint">点击头像更换</p>
            </div>
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" maxlength="20" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱">
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
        <h3 style="margin-bottom:16px;padding-left:12px">通讯录隐私设置</h3>
        <ErrorState v-if="privacyError" title="隐私设置加载失败" @retry="loadPrivacy" />
        <el-form :model="privacyForm" :disabled="privacyLoading || privacySaving || privacyError" label-width="140px" label-position="right" style="max-width:500px">
          <el-form-item label="手机号可见">
            <el-switch v-model="privacyForm.phone_visible" :active-value="1" :inactive-value="2" active-text="展示" inactive-text="隐藏" @change="savePrivacy" />
          </el-form-item>
          <el-form-item label="邮箱可见">
            <el-switch v-model="privacyForm.email_visible" :active-value="1" :inactive-value="2" active-text="展示" inactive-text="隐藏" @change="savePrivacy" />
          </el-form-item>
          <el-form-item label="联系方式可见范围">
            <el-radio-group v-model="privacyForm.contact_visible" @change="savePrivacy">
              <el-radio :value="1">所有人可见</el-radio>
              <el-radio :value="2">仅好友可见</el-radio>
              <el-radio :value="3">仅自己可见</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <p class="privacy-hint">姓名、学号和联系方式按上述范围展示；关闭的手机号和邮箱仅自己可见。修改立即保存。</p>
      </Card>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import request from '@/api/request'
import * as authApi from '@/api/auth'
import * as contactApi from '@/api/contact'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import ErrorState from '@/components/ErrorState.vue'

const userStore = useUserStore()
const saving = ref(false)
const privacySaving = ref(false)
const privacyLoading = ref(true)
const privacyError = ref(false)

const privacyForm = reactive({
  phone_visible: 2,
  email_visible: 2,
  contact_visible: 2
})

async function loadPrivacy() {
  privacyLoading.value = true
  privacyError.value = false
  try {
    const res = await contactApi.getMyAuth()
    if (res.code === 200 && res.data) {
      const d = res.data
      privacyForm.phone_visible = d.phone_visible ?? d.phoneVisible ?? 2
      privacyForm.email_visible = d.email_visible ?? d.emailVisible ?? 2
      privacyForm.contact_visible = d.contact_visible ?? d.contactVisible ?? 2
    }
  } catch { privacyError.value = true }
  finally { privacyLoading.value = false }
}

async function savePrivacy() {
  if (privacySaving.value || privacyLoading.value || privacyError.value) return
  privacySaving.value = true
  try {
    await contactApi.updateMyAuth(privacyForm)
  } catch { await loadPrivacy() }
  finally { privacySaving.value = false }
}

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
  loadPrivacy()
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
  saving.value = true
  try {
    await request.put('/api/user/profile', form)
    userStore.updateUserInfo(form)
    ElMessage.success('资料已更新')
  } finally { saving.value = false }
}

function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  authApi.uploadAvatar(file).then(res => {
    userStore.updateUserInfo({ avatar_url: `${res.data?.avatar_url}?v=${Date.now()}` })
    ElMessage.success('头像已更新')
  }).catch(() => { ElMessage.warning('上传失败') })
}
</script>

<style scoped>
.privacy-hint { color: var(--color-text-secondary); line-height: 1.6; font-size: 12px; }
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
.avatar-pic:hover {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 30%, transparent);
}
.avatar-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
