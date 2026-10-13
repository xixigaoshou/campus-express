import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 二次封装
 * - baseURL 固定 /api（由 Vite 代理转发到后端 8080）
 * - 请求拦截器：自动从 localStorage 取出 token 放入 Authorization 请求头
 * - 响应拦截器：统一解包后端 Result 结构（code/message/data）
 *   · code=200 → 直接返回业务数据 data
 *   · code=401 → 清除本地登录态并跳转登录页
 *   · 其他 code → 弹出错误提示并 reject
 *   · 请求配置传 silent: true 时跳过全局错误弹窗（供轮询/预检类请求静默失败）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  // 数组参数序列化为重复 key（statusList=进行中），
  // 而非 axios 1.x 默认的 statusList[]=进行中 —— 后者后端 @RequestParam List<String> 取不到值
  paramsSerializer: { indexes: null }
})

// 请求拦截器：自动携带 JWT 令牌（Authorization: Bearer <token>）
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理结果与错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    // 401：token 失效 / 未登录，清除本地状态并跳转登录页（始终提示，不受 silent 影响）
    if (res.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(res.message || '登录已过期，请重新登录')
      setTimeout(() => {
        window.location.href = '/login'
      }, 800)
      return Promise.reject(new Error(res.message))
    }
    // 其他业务错误：统一弹出后端提示（抢单失败、无权限、重复评价等）
    // silent 请求由调用方自行处理，不弹全局提示
    if (!response.config?.silent) {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    // HTTP 401：拦截器返回的未登录响应，清除本地登录态并跳转登录页
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(error.response?.data?.message || '登录已过期，请重新登录')
      setTimeout(() => {
        window.location.href = '/login'
      }, 800)
      return Promise.reject(error)
    }
    if (!error.config?.silent) {
      ElMessage.error(error.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
