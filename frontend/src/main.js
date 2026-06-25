/**
 * 不高山选课通 - Web前端
 * 应用入口
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'

// 全局样式
import './styles/variables.css'
import './styles/common.css'
import './styles/transitions.css'
import './styles/responsive.css'

const app = createApp(App)

// Pinia 状态管理（带持久化）
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)

// Vue Router
app.use(router)

// Element Plus（中文语言包）
app.use(ElementPlus, { locale: zhCn })

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 权限指令 v-permission
app.directive('permission', permissionDirective)

// ---- 启动时清理过期 / 坏的登录态，避免进入"首页 401 → 踢回登录 → 守卫又放过"的循环 ----
try {
  const token = localStorage.getItem('access_token')
  if (token) {
    // 简单解析 JWT exp
    const parts = token.split('.')
    if (parts.length === 3) {
      const payload = JSON.parse(atob(parts[1]))
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        // Token 已过期，清理整个登录态
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        localStorage.removeItem('user_info')
        localStorage.removeItem('user-store')
      }
    }
  }
} catch { /* ignore parse errors */ }

app.mount('#app')
