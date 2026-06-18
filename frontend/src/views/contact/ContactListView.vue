<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="师生通讯录" subtitle="搜索添加好友，查看联系人名片" />

      <el-tabs v-model="tab" @tab-change="handleTabChange">
        <el-tab-pane label="搜索用户" name="search" />
        <el-tab-pane label="我的好友" name="friends" />
        <el-tab-pane label="好友申请" name="pending">
          <template #label>
            好友申请
            <el-badge v-if="pendingCount > 0" :value="pendingCount" style="margin-left:4px" />
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 搜索 -->
      <div v-if="tab === 'search'" v-loading="loading">
        <el-input v-model="keyword" placeholder="输入昵称、姓名、院系自动搜索..." :prefix-icon="Search" clearable size="large" @input="onSearchInput" @clear="searchResults=[];searched=false" style="margin-bottom:16px" />
        <div v-if="searchResults.length > 0" class="contact-grid">
          <article v-for="u in searchResults" :key="getUserId(u)" class="contact-card">
            <span class="card-avatar">
              <img
                v-if="u.avatarUrl && !failedAvatars.has(getUserId(u))"
                :src="u.avatarUrl"
                :alt="u.nickname"
                class="avatar-img"
                @error="onAvatarError(u)"
              />
              <span v-else>{{ getInitial(u) }}</span>
            </span>
            <div class="card-info">
              <strong>{{ u.nickname || u.real_name || u.username }}</strong>
              <span>{{ getIdentity(u) || getRoleLabel(u) || '用户' }}</span>
              <span class="card-department">{{ u.department || u.title || '' }}</span>
            </div>
            <div class="card-action">
              <el-button v-if="u.friend_status === 'none'" type="primary" size="small" @click="handleAddFriend(getUserId(u))">添加好友</el-button>
              <el-tag v-else-if="u.friend_status === 'pending'" type="warning">等待同意</el-tag>
              <el-tag v-else-if="u.friend_status === 'pending_receive'" type="info">待你同意</el-tag>
              <el-button v-else type="success" size="small" plain @click="$router.push(`/contact/${getUserId(u)}`)">查看名片</el-button>
            </div>
          </article>
        </div>
        <EmptyState v-else-if="searched" description="未找到匹配用户" />
      </div>

      <!-- 我的好友 -->
      <div v-if="tab === 'friends'" v-loading="loading">
        <div v-if="friends.length > 0" class="contact-grid">
          <article v-for="f in friends" :key="getUserId(f)" class="contact-card" @click="$router.push(`/contact/${getUserId(f)}`)">
            <span class="card-avatar">
              <img
                v-if="f.avatarUrl && !failedAvatars.has(getUserId(f))"
                :src="f.avatarUrl"
                :alt="f.nickname"
                class="avatar-img"
                @error="onAvatarError(f)"
              />
              <span v-else>{{ getInitial(f) }}</span>
            </span>
            <div class="card-info">
              <strong>{{ f.nickname || f.real_name }}</strong>
              <span>{{ getIdentity(f) || getRoleLabel(f) || '用户' }}</span>
              <span class="card-department">{{ f.department || f.title || '' }}</span>
            </div>
          </article>
        </div>
        <EmptyState v-else description="还没有好友，去搜索添加吧" />
      </div>

      <!-- 好友申请 -->
      <div v-if="tab === 'pending'" v-loading="loading">
        <div v-if="pendingList.length > 0" class="contact-grid">
          <article v-for="p in pendingList" :key="p.friend_id + p.direction" class="contact-card">
            <span class="card-avatar">
              <img
                v-if="p.avatarUrl && !failedAvatars.has(getUserId(p))"
                :src="p.avatarUrl"
                :alt="p.nickname"
                class="avatar-img"
                @error="onAvatarError(p)"
              />
              <span v-else>{{ getInitial(p) }}</span>
            </span>
            <div class="card-info">
              <strong>{{ p.nickname || p.realName || p.real_name }}</strong>
              <span>{{ getIdentity(p) || getRoleLabel(p) || '用户' }}</span>
              <span class="card-department">{{ p.department || '' }}</span>
              <el-tag size="small" :type="p.direction === 'sent' ? 'warning' : 'info'" style="margin-top:4px">
                {{ p.direction === 'sent' ? '等待对方同意' : '待你处理' }}
              </el-tag>
            </div>
            <div class="card-action" v-if="p.direction === 'received'">
              <el-button type="primary" size="small" @click="handleAccept(p.friend_id)">同意</el-button>
              <el-button size="small" @click="handleRemove(p.friend_id)">拒绝</el-button>
            </div>
            <div class="card-action" v-else>
              <el-button size="small" type="danger" @click="handleRemove(p.friend_id)">撤回</el-button>
            </div>
          </article>
        </div>
        <EmptyState v-else description="暂无好友申请" />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { USER_TYPE_LABEL } from '@/utils/constants'
import request from '@/api/request'

const route = useRoute()
const tab = ref(route.query.tab || 'search')
const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const searchResults = ref([])
const friends = ref([])
const pendingList = ref([])
const pendingCount = ref(0)

let searchTimer = null

// 兼容后端 userId（驼峰）与 mock/user_id（下划线）两种字段名
function getUserId(u) {
  return u?.user_id ?? u?.userId ?? u?.id
}

// 跟踪加载失败的头像，回退到文字首字母
const failedAvatars = reactive(new Set())

function getInitial(u) {
  return (u.nickname || u.realName || u.real_name || u.username || '?').slice(0, 1)
}

function getRoleLabel(u) {
  const t = u.userType ?? u.user_type
  if (t === 3) return '管理员'
  if (t === 2) return '教师'
  if (t === 1) return '学生'
  // 从 title 字段推断教师身份
  if (u.title) return '教师'
  return ''
}

function getIdentity(u) {
  const parts = []
  const name = u.realName ?? u.real_name
  const uname = u.username ?? u.userName
  const sno = u.studentNo ?? u.student_no
  const cls = u.className ?? u.class_name
  // 账号 / 学号
  if (uname) parts.push(uname)
  else if (sno) parts.push(sno)
  // 角色
  const role = getRoleLabel(u)
  if (role) parts.push(role)
  // 班级（仅学生）
  if (role === '学生' && cls) parts.push(cls)
  return parts.join(' · ')
}

function onAvatarError(user) {
  failedAvatars.add(getUserId(user))
}
function onSearchInput() {
  clearTimeout(searchTimer)
  const q = keyword.value.trim()
  if (!q) { searchResults.value = []; searched.value = false; return }
  searchTimer = setTimeout(() => doSearch(q), 300)
}

async function doSearch(q) {
  loading.value = true
  searched.value = true
  try {
    const res = await request.get('/api/contact/search', { params: { keyword: q } })
    searchResults.value = res.data?.list || res.data || []
  } catch { searchResults.value = [] }
  finally { loading.value = false }
}

async function loadFriends() {
  loading.value = true
  try {
    const res = await request.get('/api/contact/my-friends')
    friends.value = res.data?.list || res.data || []
  } catch { friends.value = [] }
  finally { loading.value = false }
}

async function loadPending() {
  loading.value = true
  try {
    const res = await request.get('/api/contact/pending')
    pendingList.value = res.data?.list || res.data || []
    pendingCount.value = pendingList.value.length
  } catch { pendingList.value = [] }
  finally { loading.value = false }
}

async function handleAddFriend(targetId) {
  try {
    await request.post(`/api/contact/add/${targetId}`)
    ElMessage.success('好友申请已发送')
    if (keyword.value.trim()) doSearch(keyword.value.trim())
  } catch { /* handled */ }
}

async function handleAccept(friendId) {
  await request.post(`/api/contact/accept/${friendId}`)
  ElMessage.success('已添加为好友')
  loadFriends()
  loadPending()
}

async function handleRemove(friendId) {
  await request.delete(`/api/contact/remove/${friendId}`)
  ElMessage.success('已处理')
  loadFriends()
  loadPending()
}

function handleTabChange(val) {
  if (val === 'friends') loadFriends()
  if (val === 'pending') loadPending()
}

onMounted(() => {
  if (route.query.keyword) {
    tab.value = 'search'
    keyword.value = route.query.keyword
    doSearch(keyword.value)
  }
  if (tab.value === 'friends') loadFriends()
  if (tab.value === 'pending') loadPending()
})
</script>

<style scoped>
.contact-grid { display: grid; gap: 12px; }
.contact-card {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 18px; border-radius: 16px;
  background: var(--color-bg-card); border: 1px solid rgba(154,66,45,.08);
  cursor: pointer; transition: box-shadow .15s;
}
.contact-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.06); }
.card-avatar {
  width: 44px; height: 44px; border-radius: 14px; flex-shrink: 0;
  background: var(--color-primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 900;
  overflow: hidden;
}
.avatar-img {
  width: 100%; height: 100%; object-fit: cover;
}
.card-info { flex: 1; min-width: 0; }
.card-info strong { display: block; color: #333; }
.card-info span { display: block; margin-top: 2px; font-size: 12px; color: #999; }
.card-department { color: #bbb !important; font-size: 11px !important; }
.card-action { flex-shrink: 0; }
</style>
