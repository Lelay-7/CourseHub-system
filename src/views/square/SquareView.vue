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
          placeholder="搜索课程、老师、资料或关键词"
          :prefix-icon="Search"
          clearable
        />
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button v-for="tab in tabs" :key="tab" :value="tab">{{ tab }}</el-radio-button>
        </el-radio-group>
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

      <div class="post-list">
        <article v-for="post in visiblePosts" :key="post.id" class="post-card">
          <div class="post-avatar">{{ post.name.slice(0, 1) }}</div>
          <div class="post-body">
            <div class="post-head">
              <div>
                <strong>{{ post.name }}</strong>
                <span>{{ post.time }}</span>
              </div>
              <el-button text :icon="Star" @click="post.favorited = !post.favorited">
                {{ post.favorited ? '已收藏' : '收藏' }}
              </el-button>
            </div>
            <p>{{ post.content }}</p>
            <div class="post-relations">
              <button type="button" @click="$router.push(post.coursePath)">课程：{{ post.course }}</button>
              <button v-if="post.material" type="button" @click="$router.push('/material')">资料：{{ post.material }}</button>
            </div>
            <div class="post-foot">
              <div class="post-tags">
                <StatusTag v-for="tag in post.tags" :key="tag">{{ tag }}</StatusTag>
              </div>
              <div class="post-stats">
                <span>赞 {{ post.likes }}</span>
                <span>评论 {{ post.comments }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <el-dialog v-model="showDialog" title="发布动态" width="560px">
        <el-form label-position="top">
          <el-form-item label="动态内容">
            <el-input v-model="postContent" type="textarea" :rows="5" maxlength="220" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" @click="submitPost">发布</el-button>
        </template>
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Search, Reading, FolderOpened, ChatDotRound, Star } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import Card from '@/components/Card.vue'
import StatusTag from '@/components/StatusTag.vue'

const showDialog = ref(false)
const postContent = ref('')
const keyword = ref('')
const activeTab = ref('全部')
const tabs = ['全部', '选课经验', '资料互助', '问答讨论', '避坑提醒']

const entries = [
  { title: '选课经验', desc: '教师风格、作业强度、考核方式', path: '/course', icon: Reading, bg: 'linear-gradient(135deg, #f07462, #f8b25f)' },
  { title: '资料互助', desc: '复习资料、实验模板、往年题', path: '/material', icon: FolderOpened, bg: 'linear-gradient(135deg, #f4b24f, #e87452)' },
  { title: '问答讨论', desc: '课程冲突、学分规划、答疑提醒', path: '/contact', icon: ChatDotRound, bg: 'linear-gradient(135deg, #c98272, #e87452)' }
]

const posts = ref([
  { id: 1, type: '选课经验', name: '张义彬', time: '10 分钟前', course: '数据库系统原理', coursePath: '/course/3', material: 'SQL 练习题集', tags: ['数据库', '复习'], likes: 38, comments: 12, favorited: false, content: '数据库系统原理建议先看往年 SQL 题和规范化例题，王雪老师会强调索引设计和事务隔离。' },
  { id: 2, type: '资料互助', name: '刘青', time: '1 小时前', course: 'Web 前端工程实践', coursePath: '/course/2', material: 'Vue3 组件设计实验指导书', tags: ['前端', '实验模板'], likes: 56, comments: 18, favorited: true, content: '前端工程实践适合做完整展示项目，组件拆分和交互说明要写清楚，资料区那份实验指导很有用。' },
  { id: 3, type: '避坑提醒', name: '陈同学', time: '今天', course: '算法与数据结构', coursePath: '/course/1', material: '期末复习提纲', tags: ['算法', '避坑'], likes: 74, comments: 21, favorited: false, content: '算法课不要只背题，复杂度分析和边界条件经常会出小题，建议配合知识图谱复习。' },
  { id: 4, type: '问答讨论', name: '李欣', time: '昨天', course: '软件工程与项目管理', coursePath: '/course/5', material: '', tags: ['答辩', '团队项目'], likes: 24, comments: 9, favorited: false, content: '软件工程答辩老师会追问需求变更和风险管理，最好在项目文档里提前写出迭代记录。' }
])

const visiblePosts = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return posts.value.filter(post => {
    const tabOk = activeTab.value === '全部' || post.type === activeTab.value
    const text = `${post.content} ${post.course} ${post.material} ${post.tags.join(' ')}`.toLowerCase()
    return tabOk && (!q || text.includes(q))
  })
})

function submitPost() {
  if (!postContent.value.trim()) {
    ElMessage.warning('请先输入动态内容')
    return
  }
  posts.value.unshift({
    id: Date.now(),
    type: '问答讨论',
    name: '张义彬',
    time: '刚刚',
    course: 'Web 前端工程实践',
    coursePath: '/course/2',
    material: '',
    tags: ['讨论'],
    likes: 0,
    comments: 0,
    favorited: false,
    content: postContent.value.trim()
  })
  postContent.value = ''
  showDialog.value = false
  ElMessage.success('发布成功，参与问答互动可获得积分')
}
</script>

<style scoped>
.square-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.square-entry {
  min-height: 142px;
  border: 0;
  padding: var(--spacing-lg);
  text-align: left;
  cursor: pointer;
}

.square-entry span {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.square-entry strong,
.square-entry small {
  display: block;
}

.square-entry strong {
  margin-top: 12px;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.square-entry small {
  margin-top: 5px;
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
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
}

.post-avatar {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  color: #fff;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
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
  color: #4c91d8;
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
}

@media (max-width: 900px) {
  .square-entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
