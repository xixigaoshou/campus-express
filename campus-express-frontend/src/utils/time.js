/**
 * 时间展示工具
 * - formatDateTime：ISO 字符串 / Date 转为 yyyy-MM-dd HH:mm
 * - formatDateTimeShort：转为 MM-dd HH:mm（紧凑，适合列表）
 * - relativeTime：转为相对时间（刚刚 / x 分钟前 / x 小时前 / x 天前）
 */

/** 转为 yyyy-MM-dd HH:mm */
export const formatDateTime = (t) => {
  if (!t) return ''
  const d = typeof t === 'string' ? new Date(t) : t
  if (Number.isNaN(d.getTime())) return String(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 转为 MM-dd HH:mm（紧凑，适合列表行内展示） */
export const formatDateTimeShort = (t) => {
  if (!t) return ''
  const full = formatDateTime(t)
  return full ? full.slice(5) : ''
}

/** 转为相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 */
export const relativeTime = (t) => {
  if (!t) return ''
  const d = typeof t === 'string' ? new Date(t) : t
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60 * 1000) return '刚刚'
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatDateTimeShort(t)
}
