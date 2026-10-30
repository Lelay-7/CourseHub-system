<template>
  <AppLayout>
    <PageContainer>
      <PageHeader
        title="同学广场"
        subtitle="分享选课经验、资料线索和避坑提醒。"
      >
        <template #extra>
          <el-button type="primary" :icon="EditPen" @click="showDialog = true">发布动态</el-button>
        </template>
      </PageHeader>

      <Toolbar>
        <el-input
          v-model="keyword"
          placeholder="搜索内容、标签、用户、课程名..."
          :prefix-icon="Search"
          clearable
          @input="onKeywordChange"
          @keyup.enter="resetPosts"
          @clear="resetPosts"
        />
        <el-radio-group v-model="activeTab" size="small" @change="resetPosts">
          <el-radio-button v-for="tab in tabs" :key="tab" :value="tab">{{ tab }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="courseFilter" filterable clearable placeholder="关联课程" @change="resetPosts"><el-option v-for="c in courseOptions" :key="c.course_id" :label="c.course_name" :value="c.course_id" /></el-select>
      </Toolbar>

      <div class="square-entry-grid">
        <Card v-for="entry in entries" :key="entry.title" hover class="square-entry" tag="button" @click="$router.push(entry.path)">
          <span :style="{ background: entry.bg }">
            <el-icon :size="28"><component :is="entry.icon" /></el-icon>
          </span>
          <strong>{{ entry.title }}</strong>
          <small>{{ entry.desc }}</small>
        </Card>
      </div>

      <ErrorState v-if="error" :title="error" @retry="fetchPosts" />
      <div v-else v-loading="loading" class="post-list">
        <article v-for="post in posts" :key="post.id" class="post-card">
          <div class="post-avatar">
            <img
              v-if="post.avatarUrl && !failedAvatars.has(post.id)"
              :src="post.avatarUrl"
              :alt="post.name"
              class="avatar-img"
              @error="failedAvatars.add(post.id)"
            />
            <span v-else>{{ post.name.slice(0, 1) }}</span>
          </div>
          <div class="post-body">
            <div class="post-head">
              <div>
                <strong>{{ post.name }}</strong>
                <span>{{ post.time }}</span>
              </div>
              <div class="post-actions">
                <el-button v-if="post.userId === userStore.userInfo?.user_id" text type="danger" size="small" @click="handleDelete(post.id)">删除</el-button>
              </div>
            </div>
            <p>{{ post.content }}</p>
            <div class="post-relations" v-if="post.course || post.material">
              <button v-if="post.course" type="button" @click="$router.push(post.courseId ? '/course/' + post.courseId : { path: '/course', query: { keyword: post.course } })">课程：{{ post.course }}</button>
              <button v-if="post.material" type="button" @click="$router.push({ path: '/material', query: { keyword: post.material } })">资料：{{ post.material }}</button>
            </div>
            <div class="post-foot">
              <div class="post-tags">
                <StatusTag v-for="tag in post.tags" :key="tag">{{ tag }}</StatusTag>
              </div>
              <div class="post-stats">
                <button type="button" class="like-btn" :class="{ liked: post.liked }" @click="handleLike(post)">
                  {{ post.liked ? '❤️' : '🤍' }} 赞 {{ post.likes }}
                </button>
                <button type="button" class="comment-toggle" @click="toggleComments(post)">💬 评论 {{ post.comments }}</button>
              </div>
            </div>
            <!-- 评论区 -->
            <div v-if="post.showComments" class="comment-section">
              <div v-for="c in post.commentList" :key="c.comment_id" class="comment-item">
                <strong>{{ c.nickname }}</strong>
                <span>{{ c.content }}</span>
                <el-button
                  v-if="c.user_id === userStore.userInfo?.user_id"
                  text
                  type="danger"
                  size="small"
                  @click="handleDeleteComment(post, c.comment_id)"
                >删除</el-button>
              </div>
              <div class="comment-input">
                <el-input v-model="post.commentText" size="small" maxlength="500" show-word-limit placeholder="写下你的评论..." @keyup.enter="submitComment(post)" />
                <el-button size="small" type="primary" @click="submitComment(post)">发送</el-button>
              </div>
            </div>
          </div>
        </article>
      </div>
      <EmptyState v-if="!error && !loading && posts.length === 0" description="还没有帖子，发布第一条动态吧" />
      <el-pagination
        v-if="total > pageSize"
        background
        layout="prev, pager, next"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        style="margin-top: 20px; justify-content: center"
        @current-change="handlePageChange"
      />

      <el-dialog v-model="showDialog" title="发布动态" width="min(620px, 94vw)">
        <el-form label-position="top">
          <el-form-item label="动态内容">
            <el-input v-model="postContent" type="textarea" :rows="5" maxlength="220" show-word-limit placeholder="分享你的选课经验、资料线索或避坑提醒..." />
          </el-form-item>
          <el-form-item label="标签" class="tag-form-item">
            <div class="tag-area">
              <div class="tag-select">
                <el-checkbox-group v-model="selectedTags">
                  <el-checkbox v-for="t in tagOptions" :key="t" :label="t" :value="t" />
                </el-checkbox-group>
              </div>
              <div class="tag-list" v-if="selectedTags.length > 0">
                <el-tag v-for="t in selectedTags" :key="t" closable size="small" @close="selectedTags = selectedTags.filter(v => v !== t)">{{ t }}</el-tag>
              </div>
              <div class="custom-tag">
                <el-input v-model="customTag" size="small" placeholder="自定义标签，回车添加" style="width:180px" @keyup.enter="addCustomTag" />
                <el-button size="small" @click="addCustomTag">添加</el-button>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="关联课程（可选）">
            <el-select v-model="linkedCourse" placeholder="选择课程" clearable filterable style="width:100%">
              <el-option v-for="c in courseOptions" :key="c.course_id || c.courseId" :label="c.course_name || c.courseName" :value="c.course_id || c.courseId" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联资料（可选）">
            <el-select v-model="linkedMaterial" placeholder="选择资料" clearable filterable style="width:100%">
              <el-option v-for="m in materialOptions" :key="m.material_id || m.materialId" :label="m.material_name || m.materialName" :value="m.material_name || m.materialName" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" :loading="posting" @click="submitPost">发布</el-button>
        </template>
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useRequestGate, useListMemory } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Search, Reading, FolderOpened, ChatDotRound, Star } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getSquarePosts, createSquarePost, deleteSquarePost, getComments, addComment, deleteComment, toggleLike } from '@/api/square'
import * as courseApi from '@/api/course'
import * as materialApi from '@/api/material'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import Card from '@/components/Card.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore(), route = useRoute()
const courseFilter = ref(Number(route.query.courseId) || null)
const error = ref('')
const showDialog = ref(false)
const postContent = ref('')
const selectedTags = ref([])
const posting = ref(false)
const keyword = ref('')
const activeTab = ref('全部')
const tabs = ['全部', '选课经验', '资料互助', '问答讨论', '避坑提醒', '期末复习', '项目实战', '考试重点']
const tagOptions = ['选课经验', '资料互助', '问答讨论', '避坑提醒', '期末复习', '项目实战', '考试重点', '复习', '数据库', '算法', '前端', '答辩', '团队项目', '给分高', '作业少', '点名多', '实验多', '课程设计']
const customTag = ref('')
const linkedCourse = ref(Number(route.query.courseId) || null)
const linkedMaterial = ref('')
const courseOptions = ref([])
const materialOptions = ref([])
function addCustomTag() {
  const tag = customTag.value.trim()
  if (tag && !selectedTags.value.includes(tag)) {
    selectedTags.value.push(tag)
  }
  customTag.value = ''
}

const entries = [
  { title: '选课经验', desc: '教师风格、作业强度、考核方式', path: '/course', icon: Reading, bg: 'linear-gradient(135deg, var(--color-primary), var(--color-accent))' },
  { title: '资料互助', desc: '复习资料、实验模板、往年题', path: '/material', icon: FolderOpened, bg: 'linear-gradient(135deg, var(--color-accent), var(--color-primary))' },
  { title: '问答讨论', desc: '课程冲突、学分规划、答疑提醒', path: '/contact', icon: ChatDotRound, bg: 'linear-gradient(135deg, var(--color-primary-hover), var(--color-primary))' }
]

const posts = ref([])
const loading = ref(false)
const failedAvatars = reactive(new Set())
const page = ref(1)
const pageSize = 10
const total = ref(0)
const gate = useRequestGate(() => { posts.value = []; showDialog.value = false; postContent.value = ''; loading.value = false })
const restoreScroll = useListMemory(
  () => ({ keyword: keyword.value, tab: activeTab.value, page: page.value, course: courseFilter.value }),
  state => { keyword.value = state.keyword; activeTab.value = state.tab; page.value = state.page; courseFilter.value = state.course },
  (to, restored) => {
    clearTimeout(searchTimer)
    if (!restored) { keyword.value = ''; activeTab.value = '全部'; page.value = 1; courseFilter.value = Number(to.query.courseId) || null }
    linkedCourse.value = courseFilter.value
    fetchPosts()
  }
)
let searchTimer = null

function formatPost(p) {
  return {
    id: p.post_id,
    userId: p.user_id,
    name: p.nickname || '匿名',
    avatarUrl: p.avatar_url || p.avatarUrl || '',
    time: formatTime(p.create_time),
    content: p.content,
    course: p.course_name || '',
    courseId: p.course_id,
    material: p.material_name || '',
    tags: p.tags ? p.tags.split(',') : [],
    likes: p.likes || 0,
    liked: p.liked || false,
    comments: p.comments || 0,
    showComments: false,
    commentList: [],
    commentText: ''
  }
}

async function fetchPosts() {
  const ticket = gate.begin()
  loading.value = true; error.value = ''
  try {
    const params = { page: page.value, pageSize, courseId: courseFilter.value || undefined }
    const q = keyword.value.trim()
    if (q) params.keyword = q
    if (activeTab.value !== '全部') params.tag = activeTab.value
    const res = await getSquarePosts(params)
    if (!gate.valid(ticket)) return
    const data = res.data || {}
    posts.value = (data.list || []).map(formatPost)
    total.value = data.total || 0
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize))
    if (page.value > lastPage) { page.value = lastPage; await fetchPosts(); return }
    await restoreScroll()
  } catch (err) { if (gate.valid(ticket) && !err.sessionChanged) { error.value = '动态加载失败'; posts.value = [] } }
  finally { if (gate.valid(ticket)) loading.value = false }
}

function resetPosts() { clearTimeout(searchTimer); page.value = 1; fetchPosts() }
function onKeywordChange() {
  clearTimeout(searchTimer)
  gate.invalidate()
  searchTimer = setTimeout(() => {
    page.value = 1
    fetchPosts()
  }, 300)
}

function handlePageChange(p) {
  page.value = p
  fetchPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function submitPost() {
  if (posting.value) return
  if (!postContent.value.trim()) { ElMessage.warning('请先输入动态内容'); return }
  posting.value = true
  try {
    const tags = selectedTags.value.length > 0 ? selectedTags.value.join(',') : ''
    const data = { content: postContent.value.trim(), tags }
    if (linkedCourse.value) { data.courseId = linkedCourse.value; data.courseName = courseOptions.value.find(c => c.course_id === linkedCourse.value)?.course_name || '' }
    if (linkedMaterial.value) data.materialName = linkedMaterial.value
    await createSquarePost(data)
    postContent.value = ''
    selectedTags.value = []
    customTag.value = ''
    linkedCourse.value = courseFilter.value
    linkedMaterial.value = ''
    showDialog.value = false
    ElMessage.success('发布成功')
    fetchPosts()
  } catch { /* retain draft for retry */ } finally { posting.value = false }
}

async function handleLike(post) {
  try {
    const res = await toggleLike(post.id)
    if (res.code === 200) {
      post.liked = res.data?.liked ?? false
      post.likes = res.data?.likes ?? 0
    }
  } catch { /* ignore */ }
}

async function toggleComments(post) {
  post.showComments = !post.showComments
  if (post.showComments) {
    await loadComments(post)
  }
}

async function loadComments(post) {
  try {
    const res = await getComments(post.id)
    const data = res.data?.list || res.data || []
    post.commentList = Array.isArray(data) ? data : []
    post.comments = post.commentList.length
  } catch {
    post.commentList = []
  }
}

async function submitComment(post) {
  const text = post.commentText?.trim()
  if (!text) return
  try {
    await addComment(post.id, { content: text })
    post.commentText = ''
    await loadComments(post)
  } catch { /* ignore */ }
}

async function handleDeleteComment(post, commentId) {
  try {
    await deleteComment(commentId)
    ElMessage.success('评论已删除')
    await loadComments(post)
  } catch { /* handled */ }
}

async function handleDelete(postId) {
  try {
    await ElMessageBox.confirm('确定删除该帖子？', '确认', { type: 'warning' })
    await deleteSquarePost(postId)
    ElMessage.success('已删除')
    fetchPosts()
  } catch { /* cancelled */ }
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString('zh-CN')
}

async function loadAssociationOptions() {
  try {
    const [courseRes, materialRes] = await Promise.all([
      materialApi.getCourseOptions(),
      materialApi.getMaterialList({ page: 1, pageSize: 100 })
    ])
    courseOptions.value = courseRes.data?.list || courseRes.data?.records || []
    materialOptions.value = materialRes.data?.list || materialRes.data?.records || []
  } catch { /* ignore */ }
}

onBeforeUnmount(() => clearTimeout(searchTimer))
onMounted(() => {
  fetchPosts()
  loadAssociationOptions()
})
</script>

<style scoped>
.square-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.square-entry {
  min-height: 64px;
  border: 0;
  padding: 12px 16px;
  text-align: left;
  display: grid; grid-template-columns: 36px 1fr; gap: 4px 12px; align-items: center;
  cursor: pointer;
}

.square-entry span {
  width: 36px;
  height: 36px;
  grid-row: 1 / 3;
  border-radius: 10px;
  color: var(--color-on-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.square-entry strong,
.square-entry small {
  display: block;
}

.square-entry strong {
  margin-top: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.square-entry small {
  margin-top: 0;
  color: var(--color-text-secondary);
}

.post-list {
  display: grid;
  gap: var(--spacing-md);
}

.post-card {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid color-mix(in srgb, var(--color-primary) 8%, transparent);
  box-shadow: var(--shadow-sm);
}

.post-avatar {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  color: var(--color-on-primary);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  overflow: hidden;
}
.post-avatar .avatar-img {
  width: 100%; height: 100%; object-fit: cover;
}

.post-head,
.post-foot,
.post-relations {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.post-head strong,
.post-head span {
  display: block;
}

.post-head span {
  margin-top: 3px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.post-body p {
  white-space: pre-wrap; overflow-wrap: anywhere;
  margin: var(--spacing-sm) 0 var(--spacing-md);
  color: var(--color-text-regular);
  line-height: 1.75;
}

.post-relations {
  justify-content: flex-start;
  margin-bottom: var(--spacing-md);
}

.post-relations button {
  border: 0;
  color: var(--color-primary);
  background: transparent;
  cursor: pointer;
  font-weight: 800;
}

.post-tags,
.post-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.post-stats {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  display: flex;
  gap: 16px;
}
.like-btn, .comment-toggle { cursor: pointer; user-select: none; background: transparent; border: 0; color: inherit; font: inherit; min-height: 36px; }
.like-btn:hover, .comment-toggle:hover { color: var(--color-primary); }
.like-btn.liked { color: var(--color-danger); }
.comment-section { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border-light); }
.comment-item { display: flex; gap: 8px; padding: 6px 0; font-size: 13px; }
.comment-item strong { color: var(--color-text-primary); white-space: nowrap; }
.comment-item span { color: var(--color-text-regular); }
.comment-input { display: flex; gap: 8px; margin-top: 10px; }
.tag-form-item :deep(.el-form-item__content) { display: flex; flex-direction: column; }
.tag-area { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.tag-select :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 4px 16px; }
.custom-tag { display: flex; align-items: center; gap: 8px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; }

@media (max-width: 900px) {
  .square-entry-grid {
    display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px;
  }
  .square-entry { flex: 0 0 auto; display: flex; min-height: 44px; padding: 8px 12px; gap: 8px; }
  .square-entry span { width: 28px; height: 28px; }
  .square-entry small { display: none; }
  .post-card { grid-template-columns: 36px minmax(0, 1fr); gap: 10px; padding: 16px; border-radius: 16px; }
  .post-avatar { width: 36px; height: 36px; border-radius: 12px; }
}
</style>
