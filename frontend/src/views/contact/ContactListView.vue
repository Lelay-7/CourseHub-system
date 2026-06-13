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
          <article v-for="u in searchResults" :key="u.user_id" class="contact-card">
            <span class="card-avatar">{{ (u.nickname || '?').slice(0, 1) }}</span>
            <div class="card-info">
              <strong>{{ u.nickname || u.real_name || u.username }}</strong>
              <span>{{ u.title || USER_TYPE_LABEL[u.user_type] || '未知' }} · {{ u.department }}</span>
            </div>
            <div class="card-action">
              <el-button v-if="u.friend_status === 'none'" type="primary" size="small" @click="handleAddFriend(u.user_id)">添加好友</el-button>
              <el-tag v-else-if="u.friend_status === 'pending'" type="warning">等待同意</el-tag>
              <el-tag v-else-if="u.friend_status === 'pending_receive'" type="info">待你同意</el-tag>
              <el-button v-else type="success" size="small" plain @click="$router.push(`/contact/${u.user_id}`)">查看名片</el-button>
            </div>
          </article>
        </div>
        <EmptyState v-else-if="searched" description="未找到匹配用户" />
      </div>

      <!-- 我的好友 -->
      <div v-if="tab === 'friends'" v-loading="loading">
        <div v-if="friends.length > 0" class="contact-grid">
          <article v-for="f in friends" :key="f.user_id" class="contact-card" @click="$router.push(`/contact/${f.user_id}`)">
            <span class="card-avatar">{{ (f.nickname || '?').slice(0, 1) }}</span>
            <div class="card-info">
              <strong>{{ f.nickname || f.real_name }}</strong>
              <span>{{ f.user_type === 2 ? (f.title || '教师') : '学生' }} · {{ f.department }}</span>
            </div>
          </article>
        </div>
        <EmptyState v-else description="还没有好友，去搜索添加吧" />
      </div>

      <!-- 好友申请 -->
      <div v-if="tab === 'pending'" v-loading="loading">
        <div v-if="pendingList.length > 0" class="contact-grid">
          <article v-for="p in pendingList" :key="p.friend_id + p.direction" class="contact-card">
            <span class="card-avatar">{{ (p.nickname || '?').slice(0, 1) }}</span>
            <div class="card-info">
              <strong>{{ p.nickname || p.real_name }}</strong>
              <span>{{ p.department || '' }}</span>
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
import { ref, onMounted } from 'vue'
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
}
.card-info { flex: 1; min-width: 0; }
.card-info strong { display: block; color: #333; }
.card-info span { display: block; margin-top: 2px; font-size: 12px; color: #999; }
.card-action { flex-shrink: 0; }
</style>
