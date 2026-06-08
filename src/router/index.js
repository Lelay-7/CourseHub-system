import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { isAuthenticated } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  { path: '/', redirect: '/home' },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { requiresAuth: true, title: '首页' }
  },
  {
    path: '/course',
    name: 'CourseList',
    component: () => import('@/views/course/CourseListView.vue'),
    meta: { requiresAuth: true, title: '选课中心', keepAlive: true }
  },
  {
    path: '/course/:id',
    name: 'CourseDetail',
    component: () => import('@/views/course/CourseDetailView.vue'),
    meta: { requiresAuth: true, title: '课程详情' }
  },
  {
    path: '/square',
    name: 'Square',
    component: () => import('@/views/square/SquareView.vue'),
    meta: { requiresAuth: true, title: '同学广场', keepAlive: true }
  },
  {
    path: '/material',
    name: 'MaterialList',
    component: () => import('@/views/material/MaterialListView.vue'),
    meta: { requiresAuth: true, title: '学习资料', keepAlive: true }
  },
  {
    path: '/material/:id',
    name: 'MaterialDetail',
    component: () => import('@/views/material/MaterialDetailView.vue'),
    meta: { requiresAuth: true, title: '资料详情' }
  },
  {
    path: '/contact',
    name: 'ContactList',
    component: () => import('@/views/contact/ContactListView.vue'),
    meta: { requiresAuth: true, title: '师生通讯录', keepAlive: true }
  },
  {
    path: '/contact/:id',
    name: 'ContactDetail',
    component: () => import('@/views/contact/ContactDetailView.vue'),
    meta: { requiresAuth: true, title: '联系人详情' }
  },
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
    path: '/profile/feedback',
    name: 'Feedback',
    component: () => import('@/views/profile/FeedbackView.vue'),
    meta: { requiresAuth: true, title: '意见反馈' }
  },
  {
    path: '/profile/support',
    name: 'Support',
    component: () => import('@/views/profile/SupportView.vue'),
    meta: { requiresAuth: true, title: '联系客服' }
  },
  {
    path: '/profile/about',
    name: 'About',
    component: () => import('@/views/profile/AboutView.vue'),
    meta: { requiresAuth: true, title: '关于我们' }
  },
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
    return savedPosition || { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 不高山选课通` : '不高山选课通'

  if (to.meta.requiresAuth === false) {
    if (to.name === 'Login' && isAuthenticated()) {
      next({ path: '/home', replace: true })
      return
    }
    next()
    return
  }

  if (!isAuthenticated()) {
    ElMessage.warning('请先登录')
    next({
      path: '/login',
      query: { redirect: to.fullPath },
      replace: true
    })
    return
  }

  next()
})

export default router
