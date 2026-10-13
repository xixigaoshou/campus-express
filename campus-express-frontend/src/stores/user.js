import { defineStore } from 'pinia'
import request from '@/utils/request'

/**
 * 用户状态仓库（Pinia）
 * 保存登录令牌与用户信息：userId / username / phone / dormArea / creditScore / unreadMsgCount
 * token 持久化到 localStorage，刷新页面后由 restore() 恢复
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    userId: null,
    username: '',
    studentNo: '',
    phone: '',
    dormArea: '',
    avatar: '', // 头像相对路径 /uploads/avatar/xxx，空则显示姓名占位
    creditScore: 100,
    canPublish: false, // true=发布者（可发布需求） false=接单者（可抢单接单）
    payPasswordSet: false, // 是否已设置支付密码（充值/提现/发布订单付钱前需验证）
    unreadMsgCount: 0, // 未读消息数，导航栏角标展示
    unreadNotifyCount: 0, // 未读通知数，导航栏角标展示
    token: ''
  }),

  actions: {
    /**
     * 登录成功后保存令牌与用户信息（同步写入 localStorage）
     * @param data 后端 /user/login 返回的 { token, user }
     */
    setLogin(data) {
      this.token = data.token
      this.userId = data.user.userId
      this.username = data.user.username
      this.studentNo = data.user.studentNo || ''
      this.phone = data.user.phone
      this.dormArea = data.user.dormArea
      this.avatar = data.user.avatar || ''
      this.creditScore = data.user.creditScore
      this.canPublish = !!data.user.canPublish
      this.payPasswordSet = !!data.user.payPasswordSet
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },

    /** 更新头像：同步内存与 localStorage
     *  路由守卫每次跳转都会 restore() 用 localStorage 覆盖内存状态，
     *  若只改内存不落盘，头像会在跳转后被旧值覆盖而回退
     */
    setAvatar(url) {
      this.avatar = url || ''
      const raw = localStorage.getItem('user')
      if (!raw) return
      try {
        const user = JSON.parse(raw)
        user.avatar = this.avatar
        localStorage.setItem('user', JSON.stringify(user))
      } catch (e) {
        // 本地存储内容损坏时忽略，下次 fetchInfo 会重建
      }
    },

    /** 从本地存储恢复登录态（路由守卫 / 页面刷新时调用） */
    restore() {
      const token = localStorage.getItem('token')
      const user = localStorage.getItem('user')
      if (!token || !user) return
      try {
        this.token = token
        Object.assign(this, JSON.parse(user))
      } catch (e) {
        // localStorage 内容损坏（非合法 JSON）：清空登录态并回退到未登录，避免解析异常导致页面白屏
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        this.$reset()
      }
    },

    /** 拉取最新用户信息（信誉分等实时刷新） */
    async fetchInfo() {
      const user = await request.get('/user/info')
      this.userId = user.userId
      this.username = user.username
      this.studentNo = user.studentNo || ''
      this.phone = user.phone
      this.dormArea = user.dormArea
      this.avatar = user.avatar || ''
      this.creditScore = user.creditScore
      this.canPublish = !!user.canPublish
      this.payPasswordSet = !!user.payPasswordSet
      localStorage.setItem('user', JSON.stringify(user))
    },

    /** 刷新未读消息数（导航栏角标） */
    async fetchUnreadCount() {
      try {
        this.unreadMsgCount = await request.get('/message/unread-count')
      } catch (e) {
        // 轮询失败静默处理，避免弹错打断操作
      }
    },

    /** 刷新未读通知数（导航栏角标） */
    async fetchNotifyUnread() {
      try {
        this.unreadNotifyCount = await request.get('/notification/unread-count')
      } catch (e) {
        // 轮询失败静默处理，避免弹错打断操作
      }
    },

    /** 退出登录：清空内存状态与本地存储 */
    logout() {
      this.$reset()
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
