/**
 * Vue Router 配置
 * - 路由表（懒加载所有页面）
 * - beforeEach 导航守卫（登录态校验 + 权限控制）
 */
import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import { ElMessage } from 'element-plus'

/**
 * 路由配置
 * meta.requiresAuth: true 表示需要登录才能访问
 * meta.allowedRoles: 允许访问的用户角色数组（1-学生 2-教师 3-管理员）
 */
const routes = [
  // ============ 登录（无需认证） ============
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },

  // ============ 首页 ============
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { requiresAuth: true, title: '首页' }
  },

  // ============ 课程模块 ============
  {
    path: '/course',
    name: 'CourseList',
    component: () => import('@/views/course/CourseListView.vue'),
    meta: { requiresAuth: true, title: '课程', keepAlive: true }
  },
  {
    path: '/course/:id',
    name: 'CourseDetail',
    component: () => import('@/views/course/CourseDetailView.vue'),
    meta: { requiresAuth: true, title: '课程详情' }
  },

  // ============ 资料模块 ============
  {
    path: '/material',
    name: 'MaterialList',
    component: () => import('@/views/material/MaterialListView.vue'),
    meta: { requiresAuth: true, title: '资料', keepAlive: true }
  },
  {
    path: '/material/:id',
    name: 'MaterialDetail',
    component: () => import('@/views/material/MaterialDetailView.vue'),
    meta: { requiresAuth: true, title: '资料详情' }
  },

  // ============ 通讯录模块 ============
  {
    path: '/contact',
    name: 'ContactList',
    component: () => import('@/views/contact/ContactListView.vue'),
    meta: { requiresAuth: true, title: '通讯录', keepAlive: true }
  },
  {
    path: '/contact/:id',
    name: 'ContactDetail',
    component: () => import('@/views/contact/ContactDetailView.vue'),
    meta: { requiresAuth: true, title: '联系人详情' }
  },

  // ============ 积分商城模块 ============
  {
    path: '/mall',
    name: 'MallList',
    component: () => import('@/views/mall/MallListView.vue'),
    meta: { requiresAuth: true, title: '积分商城', keepAlive: true }
  },
  {
    path: '/mall/:id',
    name: 'MallDetail',
    component: () => import('@/views/mall/MallDetailView.vue'),
    meta: { requiresAuth: true, title: '商品详情' }
  },

  // ============ 个人中心模块 ============
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/profile/score',
    name: 'ScoreDetail',
    component: () => import('@/views/profile/ScoreView.vue'),
    meta: { requiresAuth: true, title: '积分明细' }
  },
  {
    path: '/profile/orders',
    name: 'OrderList',
    component: () => import('@/views/profile/OrderListView.vue'),
    meta: { requiresAuth: true, title: '我的订单' }
  },
  {
    path: '/profile/orders/:id',
    name: 'OrderDetail',
    component: () => import('@/views/profile/OrderDetailView.vue'),
    meta: { requiresAuth: true, title: '订单详情' }
  },
  {
    path: '/profile/favorites',
    name: 'Favorites',
    component: () => import('@/views/profile/FavoriteView.vue'),
    meta: { requiresAuth: true, title: '我的收藏' }
  },
  {
    path: '/profile/settings',
    name: 'Settings',
    component: () => import('@/views/profile/SettingsView.vue'),
    meta: { requiresAuth: true, title: '设置' }
  },
  {
    path: '/profile/about',
    name: 'About',
    component: () => import('@/views/profile/AboutView.vue'),
    meta: { requiresAuth: true, title: '关于我们' }
  },

  // ============ 404 ============
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  }
})

// ======================
// 全局前置守卫：登录态校验
// ======================
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title
    ? `${to.meta.title} - 不高山选课通`
    : '不高山选课通'

  // 不需要认证的页面直接放行
  if (to.meta.requiresAuth === false) {
    // 已登录用户访问登录页 → 重定向到首页
    if (to.name === 'Login' && isAuthenticated()) {
      next({ path: '/home', replace: true })
      return
    }
    next()
    return
  }

  // 需要认证但未登录 → 跳转登录页（附带重定向）
  if (!isAuthenticated()) {
    ElMessage.warning('请先登录')
    next({
      path: '/login',
      query: { redirect: to.fullPath },
      replace: true
    })
    return
  }

  // 已登录 → 放行
  next()
})

export default router
