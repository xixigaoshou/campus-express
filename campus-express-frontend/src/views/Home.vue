<script setup>
/**
 * 主布局页面（登录后所有业务页面的外层框架）
 * - 顶部导航栏：Logo + 水平菜单（需求大厅/发布需求/我的订单/个人中心）
 * - 右侧用户区：未读消息/通知角标（轮询 + WebSocket 实时刷新）、用户名、信誉分、退出登录
 * - 内容区：嵌套子路由（router-view）
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 导航菜单（按角色权限条件渲染，index 为路由路径配合 el-menu router 模式）
// - 发布者（canPublish=true）：显示【发布需求】，隐藏【需求大厅】
// - 接单者（canPublish=false）：显示【需求大厅】，隐藏【发布需求】
const menus = computed(() => {
  const home = store.canPublish
    ? { path: '/publish', label: '发布需求' }
    : { path: '/hall', label: '需求大厅' }
  const list = [home]
  // 接力专区仅接单者可见（发布者不承接订单）
  if (!store.canPublish) list.push({ path: '/relay', label: '接力专区' })
  list.push({ path: '/my-order', label: '我的订单' }, { path: '/personal', label: '个人中心' })
  return list
})

// Logo 点击跳转到角色对应首页（发布者→发布需求，接单者→需求大厅）
const goHome = () => {
  router.push(store.canPublish ? '/publish' : '/hall')
}

// 未读消息角标轮询定时器
let unreadTimer = null

// ---------- WebSocket 实时推送（留言/通知到达时立即刷新角标，轮询保留作兜底） ----------
let ws = null
let wsReconnectTimer = null
let wsRetryDelay = 2000
// 组件卸载标记：置 true 后 onclose 不再重连，避免销毁后残留的 close 回调重新建立连接造成泄漏
let wsDisposed = false

/** 建立 WebSocket 连接：带 JWT token 握手，断线后按递增延迟自动重连 */
const connectWebSocket = () => {
  if (wsDisposed || !store.token) return
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${protocol}://${location.host}/ws?token=${store.token}`)
  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'new_message') {
        store.fetchUnreadCount()
        // 转发事件：消息中心页面监听后实时刷新会话列表与当前聊天窗
        window.dispatchEvent(new CustomEvent('message-arrive'))
      }
      else if (data.type === 'new_notification') store.fetchNotifyUnread()
      else if (data.type === 'new_order' && route.path === '/hall') {
        // 新订单发布：若当前停留在需求大厅则派发事件触发列表实时刷新
        window.dispatchEvent(new CustomEvent('hall-new-order'))
      }
      else if (data.type === 'new_relay') {
        // 转单接力状态变化（发起/接力/撤回/超时）：派发事件触接力专区与订单列表刷新
        window.dispatchEvent(new CustomEvent('relay-refresh'))
      }
    } catch (err) {
      // 忽略无法解析的推送
    }
  }
  ws.onclose = () => {
    // 组件已卸载则不再重连，防止销毁后重新握手造成连接泄漏
    if (wsDisposed) return
    // 连接意外断开后重连；退避上限 30s，避免短时间频繁握手
    wsReconnectTimer = setTimeout(connectWebSocket, wsRetryDelay)
    wsRetryDelay = Math.min(wsRetryDelay * 2, 30000)
  }
  ws.onopen = () => {
    wsRetryDelay = 2000
  }
}

onMounted(() => {
  // 进入布局先刷新用户信息与未读数，再每 5 秒轮询一次角标
  store.fetchInfo()
  store.fetchUnreadCount()
  store.fetchNotifyUnread()
  unreadTimer = setInterval(() => {
    store.fetchUnreadCount()
    store.fetchNotifyUnread()
  }, 5000)
  // 建立 WebSocket 实时推送连接
  connectWebSocket()
})

onUnmounted(() => {
  clearInterval(unreadTimer)
  clearTimeout(wsReconnectTimer)
  // 先置卸载标记并摘除回调，再关闭连接：close() 异步触发 onclose 时不会再安排重连
  wsDisposed = true
  if (ws) {
    ws.onclose = null
    ws.onmessage = null
    ws.onopen = null
    ws.close()
    ws = null
  }
})

/** 退出登录：确认后清空本地登录态并跳转登录页 */
const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  })
  store.logout()
  router.push('/login')
}

/** 按时间段生成问候语（欢迎横幅） */
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 6 ? '凌晨好' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
})

/** 按角色生成欢迎横幅副文案 */
const welcomeText = computed(() =>
  store.canPublish ? '发布一份新需求，让同学帮你把快递带回宿舍' : '去需求大厅逛逛，看看有没有合适的任务可以接单')
</script>

<template>
  <el-container class="layout">
    <!-- 顶部导航栏：校园蓝渐变主题 -->
    <el-header class="header">
      <div class="logo" @click="goHome">
        <svg class="logo-icon" viewBox="0 0 48 48">
          <rect x="9" y="15" width="30" height="24" rx="3" fill="#ffb25e" />
          <path d="M9 22h30" stroke="#e68a2e" stroke-width="2.5" />
          <rect x="20" y="8" width="8" height="13" rx="1.5" fill="#ffd9a0" />
          <circle cx="24" cy="30" r="3" fill="#fff" opacity="0.85" />
        </svg>
        <span>校园快递代取平台</span>
      </div>

      <!-- 水平导航菜单：router 模式，index 即路由路径 -->
      <el-menu
        class="nav-menu"
        mode="horizontal"
        :default-active="route.path"
        router
        :ellipsis="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.88)"
        active-text-color="#ffd9a0"
        hover-background-color="rgba(255,255,255,0.12)"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>

      <!-- 右侧用户区 -->
      <div class="user-area">
        <!-- 头像：点击进入个人中心 -->
        <div class="header-avatar" title="个人中心" @click="router.push('/personal')">
          <img v-if="store.avatar" :src="store.avatar" alt="头像" />
          <span v-else>{{ store.username.charAt(0) || '友' }}</span>
        </div>

        <el-badge :value="store.unreadMsgCount" :max="99" :hidden="store.unreadMsgCount === 0">
          <el-button text class="area-btn" @click="router.push('/message')">消息</el-button>
        </el-badge>

        <el-badge :value="store.unreadNotifyCount" :max="99" :hidden="store.unreadNotifyCount === 0">
          <el-button text class="area-btn" @click="router.push('/notification')">通知</el-button>
        </el-badge>

        <span class="credit-tag">信誉分 {{ store.creditScore }}</span>

        <span class="username">{{ store.username }}</span>

        <el-button class="logout-btn" size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <!-- 内容区：欢迎横幅 + 嵌套子路由页面 -->
    <el-main class="main">
      <div class="welcome-banner">
        <svg class="welcome-icon" viewBox="0 0 48 48">
          <rect x="9" y="15" width="30" height="24" rx="3" fill="#ffffff" opacity="0.92" />
          <path d="M9 22h30" stroke="#ffb25e" stroke-width="2.5" />
          <rect x="20" y="8" width="8" height="13" rx="1.5" fill="#ffe3c2" />
        </svg>
        <div class="welcome-text">
          <div class="welcome-title">{{ greeting }}，{{ store.username }}</div>
          <div class="welcome-sub">{{ welcomeText }}</div>
        </div>
      </div>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

/* 顶部导航：校园蓝渐变主题 */
.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 70%, #3a6bd6 100%);
  padding: 0 24px;
  color: #fff;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  white-space: nowrap;
}

.logo-icon {
  width: 30px;
  height: 30px;
  filter: drop-shadow(0 3px 8px rgba(0, 0, 0, 0.3));
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

/* 水平菜单在深色导航上的细节微调（选中项底部橙条） */
.nav-menu :deep(.el-menu-item) {
  border-bottom: 2px solid transparent;
}

.nav-menu :deep(.el-menu-item.is-active) {
  border-bottom-color: #ffb25e;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 14px;
  white-space: nowrap;
}

/* 用户区按钮（消息/通知）：白字 */
.area-btn {
  color: rgba(255, 255, 255, 0.92);
}

.area-btn:hover {
  color: #ffd9a0;
}

.header-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  border: 2px solid #ffb25e;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.header-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.header-avatar span {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff9f45, #ff7e3d);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

/* 信誉分胶囊：橙渐变底 */
.credit-tag {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #ff9f45, #ff7e3d);
  box-shadow: 0 2px 8px rgba(255, 126, 61, 0.35);
}

.username {
  font-size: 14px;
  color: #fff;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-btn {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.55);
  background: transparent;
}

.logout-btn:hover {
  color: #ffd9a0;
  border-color: #ffb25e;
  background: rgba(255, 255, 255, 0.1);
}

/* 内容区：浅色渐变氛围背景 */
.main {
  padding: 20px 24px;
  overflow-y: auto;
  background: linear-gradient(180deg, #f0f4fa 0%, #f5f7fa 30%, #f7f8fa 100%);
}

/* 欢迎横幅 */
.welcome-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 18px 24px;
  border-radius: 14px;
  background: linear-gradient(120deg, #1e3c72, #3a6bd6 60%, #ff9f45 130%);
  color: #fff;
  box-shadow: 0 8px 24px rgba(30, 60, 114, 0.22);
}

.welcome-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  filter: drop-shadow(0 3px 8px rgba(0, 0, 0, 0.25));
}

.welcome-title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.welcome-sub {
  margin-top: 4px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}
</style>
