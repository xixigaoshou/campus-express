import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表
 * - /login 登录注册页（public 标记，免鉴权）
 * - / 主布局（Home），子路由承载各业务页面
 * - 兜底重定向到需求大厅
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginRegister.vue'),
    meta: { public: true, title: '登录注册' }
  },
  {
    path: '/',
    component: () => import('@/views/Home.vue'),
    redirect: '/hall',
    children: [
      {
        path: 'hall',
        name: 'DemandHall',
        component: () => import('@/views/DemandHall.vue'),
        meta: { title: '需求大厅' }
      },
      {
        path: 'publish',
        name: 'PublishOrder',
        component: () => import('@/views/PublishOrder.vue'),
        meta: { title: '发布需求' }
      },
      {
        path: 'relay',
        name: 'RelayHall',
        component: () => import('@/views/RelayHall.vue'),
        meta: { title: '接力专区' }
      },
      {
        path: 'my-order',
        name: 'MyOrder',
        component: () => import('@/views/MyOrder.vue'),
        meta: { title: '我的订单' }
      },
      {
        path: 'order/:orderId',
        name: 'OrderDetail',
        component: () => import('@/views/OrderDetail.vue'),
        meta: { title: '订单详情' }
      },
      {
        path: 'personal',
        name: 'PersonalCenter',
        component: () => import('@/views/PersonalCenter.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'message',
        name: 'MessageCenter',
        component: () => import('@/views/MessageCenter.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'notification',
        name: 'NotificationCenter',
        component: () => import('@/views/NotificationCenter.vue'),
        meta: { title: '通知中心' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/hall' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置路由守卫
 * - 每次导航先恢复本地存储的登录态（刷新页面后 token 不丢失）
 * - 未登录访问业务页面 → 重定向 /login
 * - 已登录访问 /login → 按角色跳转对应首页
 * - 角色权限拦截：发布者不可进需求大厅；接单者不可进发布需求（防止直接输入 URL 越权）
 */
router.beforeEach((to) => {
  const store = useUserStore()
  store.restore()
  if (to.meta.public) {
    return store.token ? { path: store.canPublish ? '/publish' : '/hall' } : true
  }
  if (!store.token) {
    // 记录原目标地址，登录成功后回跳（如从订单详情链接被拦截到登录页）
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 发布者（canPublish=true）手动输入 /hall：拦截跳转我的订单
  if (to.path === '/hall' && store.canPublish) {
    return { path: '/my-order' }
  }
  // 接单者（canPublish=false）手动输入 /publish：拦截跳转我的订单
  if (to.path === '/publish' && !store.canPublish) {
    return { path: '/my-order' }
  }
  // 发布者（canPublish=true）手动输入 /relay：接力专区仅接单者可见，拦截跳转我的订单
  if (to.path === '/relay' && store.canPublish) {
    return { path: '/my-order' }
  }
  return true
})

// 导航后更新浏览器标签页标题
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 校园快递代取平台` : '校园快递代取平台'
})

export default router
