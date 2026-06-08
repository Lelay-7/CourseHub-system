/**
 * localStorage 封装
 * 统一处理 JSON 序列化/反序列化 + 异常容错
 */

/**
 * 读取存储值
 * @param {string} key
 * @param {*} defaultValue - 不存在时的默认值
 */
export function getItem(key, defaultValue = null) {
  try {
    const raw = localStorage.getItem(key)
    if (raw === null) return defaultValue
    return JSON.parse(raw)
  } catch {
    return defaultValue
  }
}

/**
 * 写入存储值（自动 JSON 序列化）
 * @param {string} key
 * @param {*} value
 */
export function setItem(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.warn('[Storage] setItem failed', key, e)
  }
}

/**
 * 删除指定 key
 */
export function removeItem(key) {
  try {
    localStorage.removeItem(key)
  } catch (e) {
    console.warn('[Storage] removeItem failed', key, e)
  }
}

/**
 * 获取缓存大小（估算，单位：字节）
 */
export function getCacheSize() {
  let total = 0
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    const value = localStorage.getItem(key)
    total += (key?.length || 0) + (value?.length || 0)
  }
  return total * 2 // UTF-16 编码，每个字符2字节
}

/**
 * 格式化缓存大小为可读字符串
 */
export function getCacheSizeFormatted() {
  const bytes = getCacheSize()
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/**
 * 清除所有本地缓存
 */
export function clearAll() {
  try {
    localStorage.clear()
  } catch (e) {
    console.warn('[Storage] clearAll failed', e)
  }
}
