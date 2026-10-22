export const THEME_STORAGE_KEY = 'coursehub-theme'

export const THEMES = [
  { id: 'blue', name: '瓷白墨蓝', description: '清爽的白色与沉静的墨蓝', scheme: 'light', colors: ['#fafbfc', '#eef2f5', '#183244'] },
  { id: 'wine', name: '暖白酒红', description: '温润的暖白与克制的酒红', scheme: 'light', colors: ['#f8f6f2', '#eeece8', '#6a2635'] },
  { id: 'black', name: '曜石黑', description: '深黑底色与清晰的银白层次', scheme: 'dark', colors: ['#101216', '#1b1f25', '#e3e7eb'] },
  { id: 'sage', name: '雾青竹影', description: '柔和的米白与安静的青绿', scheme: 'light', colors: ['#f4f7f2', '#e7eee7', '#2f6357'] },
  { id: 'lavender', name: '暮山浅紫', description: '轻盈的雾白与温柔的灰紫', scheme: 'light', colors: ['#f7f5fa', '#ede8f2', '#64517e'] },
  { id: 'navy', name: '深海夜航', description: '深蓝夜色与明净的冰蓝', scheme: 'dark', colors: ['#101923', '#223240', '#94c8df'] }
]

export function isTheme(value) {
  return THEMES.some(theme => theme.id === value)
}

export function readTheme() {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    if (isTheme(saved)) return saved
  } catch { /* Storage can be unavailable in private browsing. */ }
  return 'blue'
}

export function applyTheme(value, { persist = true } = {}) {
  const theme = THEMES.find(item => item.id === value) || THEMES[0]
  document.documentElement.dataset.theme = theme.id
  document.documentElement.classList.toggle('dark', theme.scheme === 'dark')
  document.documentElement.style.colorScheme = theme.scheme
  document.documentElement.style.backgroundColor = theme.colors[0]
  if (persist) {
    try { localStorage.setItem(THEME_STORAGE_KEY, theme.id) } catch { /* Keep switching usable without storage. */ }
  }
  return theme.id
}
