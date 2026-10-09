export const THEME_STORAGE_KEY = 'coursehub-theme'

export const THEMES = [
  { id: 'blue', name: '瓷白墨蓝', description: '清爽的白色与沉静的墨蓝', colors: ['#fafbfc', '#eef2f5', '#183244'] },
  { id: 'wine', name: '暖白酒红', description: '温润的暖白与克制的酒红', colors: ['#f8f6f2', '#eeece8', '#6a2635'] },
  { id: 'black', name: '曜石黑', description: '深黑底色与清晰的银白层次', colors: ['#101216', '#1b1f25', '#e3e7eb'] }
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
  const theme = isTheme(value) ? value : 'blue'
  document.documentElement.dataset.theme = theme
  document.documentElement.classList.toggle('dark', theme === 'black')
  document.documentElement.style.colorScheme = theme === 'black' ? 'dark' : 'light'
  if (persist) {
    try { localStorage.setItem(THEME_STORAGE_KEY, theme) } catch { /* Keep switching usable without storage. */ }
  }
  return theme
}
