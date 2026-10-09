/**
 * 不高山选课通 - Web前端
 * 应用入口
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'
import { readTheme, applyTheme } from './utils/theme'

// 全局样式
import './styles/variables.css'
import './styles/common.css'
import './styles/transitions.css'
import './styles/responsive.css'

// Restore appearance before mounting so a saved dark theme doesn't flash white.
applyTheme(readTheme(), { persist: false })
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

// The router restores renewable sessions before showing protected pages.
app.mount('#app')
