<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="个人中心" />

      <!-- 用户信息卡 -->
      <div class="profile-header card">
        <div class="user-basic">
          <el-avatar :size="64" :src="userStore.userInfo?.avatar_url" @click="uploadAvatar">
            <el-icon :size="32"><User /></el-icon>
          </el-avatar>
          <div class="user-text">
            <h2>{{ userStore.userInfo?.nickname || '未设置昵称' }}</h2>
            <span class="user-role">{{ USER_TYPE_LABEL[userStore.userType] || '学生' }}</span>
          </div>
          <el-button :icon="Edit" text @click="showEditDialog = true">编辑资料</el-button>
        </div>

        <!-- 积分展示 -->
        <div class="score-display">
          <div class="score-item" @click="$router.push('/profile/score')">
            <span class="score-value">{{ userStore.scoreBalance }}</span>
            <span class="score-label">可用积分</span>
          </div>
          <el-divider direction="vertical" />
          <div class="score-item">
            <span class="score-value">{{ userStore.userInfo?.total_score || 0 }}</span>
            <span class="score-label">累计积分</span>
          </div>
        </div>
      </div>

      <!-- 功能列表 -->
      <div class="menu-list card">
        <div class="menu-item" @click="$router.push('/profile/orders')">
          <el-icon><Tickets /></el-icon>
          <span>我的订单</span>
          <el-badge v-if="orderCount > 0" :value="orderCount" class="menu-badge" />
          <el-icon class="menu-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="$router.push('/profile/favorites')">
          <el-icon><Star /></el-icon>
          <span>我的收藏</span>
          <el-icon class="menu-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="$router.push('/profile/score')">
          <el-icon><Coin /></el-icon>
          <span>积分明细</span>
          <el-icon class="menu-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="$router.push('/profile/settings')">
          <el-icon><Setting /></el-icon>
          <span>设置</span>
          <el-icon class="menu-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="$router.push('/profile/about')">
          <el-icon><InfoFilled /></el-icon>
          <span>关于我们</span>
          <el-icon class="menu-arrow"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 退出登录 -->
      <el-button type="danger" plain style="width: 100%; margin-top: 16px" @click="handleLogout">
        退出登录
      </el-button>

      <!-- 编辑资料弹窗 -->
      <el-dialog v-model="showEditDialog" title="编辑个人资料" width="480px">
        <el-form :model="editForm" :rules="editRules" label-position="top" ref="editFormRef">
          <el-form-item label="头像">
            <el-upload
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              accept="image/*"
            >
              <el-avatar :size="64" :src="editForm.avatar_url">
                <el-icon :size="32"><User /></el-icon>
              </el-avatar>
            </el-upload>
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="editForm.nickname" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="editForm.real_name" />
          </el-form-item>
          <el-form-item label="院系">
            <el-input v-model="editForm.department" />
          </el-form-item>
          <el-form-item label="班级">
            <el-input v-model="editForm.class_name" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="editForm.phone" placeholder="修改手机号需短信验证" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Edit, Tickets, Star, Coin, Setting, InfoFilled, ArrowRight } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { USER_TYPE_LABEL, AVATAR_MAX_SIZE } from '@/utils/constants'
import { formRules } from '@/utils/validator'
import * as homeApi from '@/api/home'

const router = useRouter()
const userStore = useUserStore()
const showEditDialog = ref(false)
const saving = ref(false)
const orderCount = ref(0)
const editFormRef = ref(null)

const editForm = reactive({
  avatar_url: '',
  nickname: '',
  real_name: '',
  department: '',
  class_name: '',
  phone: ''
})

const editRules = {
  nickname: formRules.nickname
}

function initEditForm() {
  const info = userStore.userInfo || {}
  Object.assign(editForm, {
    avatar_url: info.avatar_url || '',
    nickname: info.nickname || '',
    real_name: info.real_name || '',
    department: info.department || '',
    class_name: info.class_name || '',
    phone: info.phone || ''
  })
}

function beforeAvatarUpload(file) {
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.warning('头像文件不能超过 2MB')
    return false
  }
  // 本地预览（实际项目这里会上传至OSS）
  const reader = new FileReader()
  reader.onload = (e) => {
    editForm.avatar_url = e.target.result
  }
  reader.readAsDataURL(file)
  return false
}

async function saveProfile() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    userStore.updateUserInfo(editForm)
    ElMessage.success('保存成功')
    showEditDialog.value = false
  } catch { /* handled */ }
  finally { saving.value = false }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '操作确认', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
    })
    userStore.logout()
    router.push('/login')
  } catch { /* 用户取消 */ }
}

function uploadAvatar() {
  showEditDialog.value = true
}

onMounted(async () => {
  // 获取订单数
  try {
    const res = await homeApi.getPendingOrderCount()
    if (res.code === 200) orderCount.value = res.data?.count || 0
  } catch { /* ignore */ }
})
</script>

<style scoped>
.profile-header { margin-bottom: var(--spacing-md); }
.user-basic {
  display: flex; align-items: center; gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}
.user-text h2 { margin: 0 0 4px; font-size: var(--font-size-lg); }
.user-role { color: var(--color-text-secondary); font-size: var(--font-size-sm); }

.score-display {
  display: flex; align-items: center; justify-content: center;
  gap: var(--spacing-xl); padding: var(--spacing-md);
  background: var(--color-bg-page); border-radius: var(--radius-sm);
}
.score-item {
  display: flex; flex-direction: column; align-items: center; cursor: pointer;
}
.score-value { font-size: var(--font-size-xxl); font-weight: 700; color: var(--color-warning); }
.score-label { font-size: var(--font-size-xs); color: var(--color-text-secondary); }

.menu-list { }
.menu-item {
  display: flex; align-items: center; gap: var(--spacing-md);
  padding: var(--spacing-md); cursor: pointer;
  border-bottom: 1px solid var(--color-border-light);
  transition: background var(--transition-fast);
}
.menu-item:last-child { border-bottom: none; }
.menu-item:hover { background: var(--color-bg-page); }
.menu-item span { flex: 1; }
.menu-arrow { color: var(--color-text-placeholder); }
</style>
