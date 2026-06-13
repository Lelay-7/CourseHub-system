/**
 * 用户状态 Store
 * 管理：用户信息、Token、积分余额、登录/登出
 */
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import {
  getAccessToken, getRefreshToken, saveTokens, clearTokens,
  isAuthenticated
} from '@/utils/auth'
import { getItem, setItem, removeItem } from '@/utils/storage'
import * as authApi from '@/api/auth'
import { useSelectionStore } from '@/stores/selection'

export const useUserStore = defineStore('user', () => {
  const selectionStore = useSelectionStore()
  // ========== State ==========
  const userInfo = ref(getItem('user_info', null))
  const accessToken = ref(getAccessToken() || '')
  const refreshToken = ref(getRefreshToken() || '')
  const scoreBalance = ref(0)

  // Watch userInfo to keep selectionStore in sync after rehydration
  watch(userInfo, (info) => {
    selectionStore.setCurrentUser(info?.user_id ?? info?.userId ?? null)
  }, { immediate: true })

  // ========== Getters ==========
  const isLoggedIn = computed(() => !!accessToken.value && isAuthenticated())
  const userType = computed(() => userInfo.value?.user_type ?? userInfo.value?.userType)
  const isStudent = computed(() => userType.value === 1)
  const isTeacher = computed(() => userType.value === 2)
  const isAdmin = computed(() => userType.value === 3)

  // ========== Actions ==========

  /** 账号密码登录 */
  async function loginByPassword({ username, password }) {
    const res = await authApi.loginByPassword({ username, password })
    if (res.code === 200) {
      const { accessToken: at, refreshToken: rt } = res.data
      saveTokens(at, rt)
      accessToken.value = at
      refreshToken.value = rt
      await fetchUserInfo()
    }
  }

  /** 手机验证码登录 */
  async function loginBySms({ phone, code }) {
    const res = await authApi.loginBySms({ phone, code })
    if (res.code === 200) {
      const { accessToken: at, refreshToken: rt } = res.data
      saveTokens(at, rt)
      accessToken.value = at
      refreshToken.value = rt
      await fetchUserInfo()
    }
  }

  /** 注册并登录 */
  async function register(data) {
    const res = await authApi.register(data)
    if (res.code === 200) {
      const { accessToken: at, refreshToken: rt } = res.data
      saveTokens(at, rt)
      accessToken.value = at
      refreshToken.value = rt
      await fetchUserInfo()
    }
  }

  /** 获取用户信息 */
  async function fetchUserInfo() {
    try {
      const res = await authApi.getUserInfo()
      if (res.code === 200) {
        userInfo.value = res.data
        scoreBalance.value = res.data?.balance || res.data?.score_balance || 0
        setItem('user_info', res.data)
        selectionStore.setCurrentUser(res.data?.user_id ?? res.data?.userId)
      }
    } catch (err) {
      console.error('[UserStore] fetchUserInfo error', err)
    }
  }

  /** 更新积分余额（本地乐观更新 + 服务端同步） */
  async function updateScoreBalance(newBalance) {
    scoreBalance.value = newBalance
  }

  /** 退出登录 */
  function logout() {
    userInfo.value = null
    accessToken.value = ''
    refreshToken.value = ''
    scoreBalance.value = 0
    clearTokens()
    removeItem('user_info')
    removeItem('user-store')
    selectionStore.setCurrentUser(null)
  }

  /** 更新用户信息 */
  function updateUserInfo(info) {
    userInfo.value = { ...userInfo.value, ...info }
    setItem('user_info', userInfo.value)
  }

  return {
    // state
    userInfo,
    accessToken,
    refreshToken,
    scoreBalance,
    // getters
    isLoggedIn,
    userType,
    isStudent,
    isTeacher,
    isAdmin,
    // actions
    loginByPassword,
    loginBySms,
    register,
    fetchUserInfo,
    updateScoreBalance,
    logout,
    updateUserInfo
  }
}, {
  persist: {
    key: 'user-store',
    storage: localStorage,
    paths: ['userInfo', 'accessToken', 'refreshToken', 'scoreBalance']
  }
})
