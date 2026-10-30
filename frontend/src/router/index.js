import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserType } from '@/utils/auth'
import { ensureSession } from '@/utils/session'

/**
 * 角色定义
 * 1 = 学生  2 = 教师  3 = 管理员
 */
const STUDENT = 1
const TEACHER = 2
const ADMIN = 3
const ALL = [STUDENT, TEACHER, ADMIN]

const routes = [
  { path: '/messages', name: 'MessageCenter', component: () => import('@/views/chat/MessageCenterView.vue'), meta: { requiresAuth: true, roles: ALL, title: '消息' } },
  { path: '/messages/:id', name: 'PrivateChat', component: () => import('@/views/chat/MessageCenterView.vue'), meta: { requiresAuth: true, roles: ALL, title: '私聊', hideMobileTabbar: true } },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/ForgotPasswordView.vue'),
    meta: { requiresAuth: false, title: '找回密码' }
  },
  {
    path: '/profile/security',
    name: 'AccountSecurity',
    component: () => import('@/views/profile/AccountSecurityView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '账号安全' }
  },
  // ==================== 登录 ====================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },

  // ==================== 首页 ====================
  { path: '/', redirect: '/login' },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '首页' }
  },

  // ==================== 课程 ====================
  {
    path: '/course',
    name: 'CourseList',
    component: () => import('@/views/course/CourseListView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '选课中心', keepAlive: true }
  },
  {
    path: '/course/:id',
    name: 'CourseDetail',
    component: () => import('@/views/course/CourseDetailView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '课程详情' }
  },

  // ==================== 广场 ====================
  {
    path: '/square',
    name: 'Square',
    component: () => import('@/views/square/SquareView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '同学广场', keepAlive: true }
  },

  // ==================== 资料 ====================
  {
    path: '/material',
    name: 'MaterialList',
    component: () => import('@/views/material/MaterialListView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '学习资料', keepAlive: true }
  },
  {
    path: '/material/:id',
    name: 'MaterialDetail',
    component: () => import('@/views/material/MaterialDetailView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '资料详情' }
  },

  // ==================== 通讯录 ====================
  {
    path: '/contact',
    name: 'ContactList',
    component: () => import('@/views/contact/ContactListView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '通讯录', keepAlive: true }
  },
  {
    path: '/contact/:id',
    name: 'ContactDetail',
    component: () => import('@/views/contact/ContactDetailView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '联系人详情' }
  },

  // ==================== 积分商城 ====================
  {
    path: '/mall',
    name: 'MallList',
    component: () => import('@/views/mall/MallListView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '积分商城', keepAlive: true }
  },
  {
    path: '/mall/:id',
    name: 'MallDetail',
    component: () => import('@/views/mall/MallDetailView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '商品详情' }
  },

  // ==================== 个人中心 ====================
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '个人中心' }
  },
  {
    path: '/profile/score',
    name: 'ScoreDetail',
    component: () => import('@/views/profile/ScoreView.vue'),
    meta: { requiresAuth: true, roles: [STUDENT, TEACHER], title: '积分明细' }
  },
  {
    path: '/profile/orders',
    name: 'OrderList',
    component: () => import('@/views/profile/OrderListView.vue'),
    meta: { requiresAuth: true, roles: [STUDENT, TEACHER], title: '我的订单' }
  },
  {
    path: '/profile/orders/:id',
    name: 'OrderDetail',
    component: () => import('@/views/profile/OrderDetailView.vue'),
    meta: { requiresAuth: true, roles: [STUDENT, TEACHER], title: '订单详情' }
  },
  {
    path: '/profile/courses',
    name: 'MyCourses',
    component: () => import('@/views/profile/MyCoursesView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '我的选课' }
  },
  {
    path: '/profile/downloads',
    name: 'Downloads',
    component: () => import('@/views/profile/DownloadView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '我的下载' }
  },
  {
    path: '/profile/favorites',
    name: 'Favorites',
    component: () => import('@/views/profile/FavoriteView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '我的收藏' }
  },
  {
    path: '/profile/edit',
    name: 'EditProfile',
    component: () => import('@/views/profile/EditProfileView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '编辑资料' }
  },
  {
    path: '/profile/settings',
    name: 'Settings',
    component: () => import('@/views/profile/SettingsView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '设置' }
  },
  {
    path: '/profile/feedback',
    name: 'Feedback',
    component: () => import('@/views/profile/FeedbackView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '意见反馈' }
  },
  {
    path: '/profile/support',
    name: 'Support',
    component: () => import('@/views/profile/SupportView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '使用帮助' }
  },
  {
    path: '/profile/about',
    name: 'About',
    component: () => import('@/views/profile/AboutView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '关于选课通' }
  },
  {
    path: '/profile/reviews',
    name: 'MyReviews',
    component: () => import('@/views/profile/MyReviewsView.vue'),
    meta: { requiresAuth: true, roles: ALL, title: '我的评价' }
  },

  // ==================== 管理员后台 ====================
  {
    path: '/admin',
    redirect: '/admin/dashboard'
  },
  {
    path: '/admin/dashboard',
    name: 'AdminDashboard',
    component: () => import('@/views/admin/DashboardView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '管理后台' }
  },
  {
    path: '/admin/courses',
    name: 'AdminCourses',
    component: () => import('@/views/admin/CourseManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '课程管理' }
  },
  {
    path: '/admin/materials',
    name: 'AdminMaterials',
    component: () => import('@/views/admin/MaterialAuditView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '资料审核' }
  },
  {
    path: '/admin/reviews',
    name: 'AdminReviews',
    component: () => import('@/views/admin/ReviewManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '评价管理' }
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/admin/UserManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '用户管理' }
  },
  {
    path: '/admin/banners',
    name: 'AdminBanners',
    component: () => import('@/views/admin/BannerManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: 'Banner管理' }
  },
  {
    path: '/admin/square',
    name: 'AdminSquare',
    component: () => import('@/views/admin/SquareManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '广场管理' }
  },
  {
    path: '/admin/orders',
    name: 'AdminOrders',
    component: () => import('@/views/admin/OrderManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '订单管理' }
  },
  {
    path: '/admin/goods',
    name: 'AdminGoods',
    component: () => import('@/views/admin/GoodsManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '商品管理' }
  },
  {
    path: '/admin/reports',
    name: 'AdminReports',
    component: () => import('@/views/admin/ReportsManageView.vue'),
    meta: { requiresAuth: true, roles: [ADMIN], title: '举报管理' }
  },

  // ==================== 404 ====================
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

async function restoreNavigationSession(next) {
  try { return await ensureSession() }
  catch { ElMessage.error('网络暂不可用，请稍后重试'); next(false); return null }
}

router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 不高山选课通` : '不高山选课通'

  // 登录页：已登录则跳转首页
  if (to.meta.requiresAuth === false) {
    if (to.name === 'Login') {
      const authenticated = await restoreNavigationSession(next)
      if (authenticated === null) return
      if (authenticated) { next({ path: to.query.redirect || '/home', replace: true }); return }
    }
    next()
    return
  }

  // 需要登录
  const authenticated = await restoreNavigationSession(next)
  if (authenticated === null) return
  if (!authenticated) {
    ElMessage.warning('请先登录')
    next({
      path: '/login',
      query: { redirect: to.fullPath },
      replace: true
    })
    return
  }

  // 角色检查
  const allowedRoles = to.meta.roles
  if (allowedRoles && Array.isArray(allowedRoles)) {
    const userType = getUserType()
    if (!allowedRoles.includes(userType)) {
      ElMessage.error('权限不足，无法访问该页面')
      // 管理员重定向到后台，其他人重定向到首页
      if (userType === ADMIN) {
        next({ path: '/admin/dashboard', replace: true })
      } else {
        next({ path: '/home', replace: true })
      }
      return
    }
  }

  next()
})

export default router
