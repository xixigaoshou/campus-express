<template>
  <div class="message-center">
    <el-card class="mc-card" shadow="never">
      <div class="mc-layout">
        <!-- 左侧：订单会话列表 -->
        <div class="mc-list">
          <div class="mc-list-head">消息会话</div>
          <div v-loading="convLoading" class="mc-list-body">
            <template v-if="conversations.length">
              <div
                v-for="c in conversations"
                :key="c.orderId"
                class="mc-item"
                :class="{ active: activeConv && activeConv.orderId === c.orderId }"
                @click="selectConversation(c)"
              >
                <div class="mc-item-top">
                  <span class="mc-item-name">{{ c.otherName }}</span>
                  <span class="mc-item-time">{{ c.lastTime }}</span>
                </div>
                <div class="mc-item-preview">
                  <span class="mc-item-sender">{{ c.lastSenderName }}：</span>{{ c.lastContent }}
                </div>
                <el-badge
                  v-if="c.unreadCount > 0"
                  :value="c.unreadCount"
                  :max="99"
                  class="mc-item-badge"
                />
              </div>
            </template>
            <el-empty v-else description="暂无消息会话，接单或下单后可留言沟通" :image-size="80" />
          </div>
        </div>

        <!-- 右侧：聊天窗口 -->
        <div class="mc-chat">
          <template v-if="activeConv">
            <div class="mc-chat-head">
              <span>与「{{ activeConv.otherName }}」的对话（订单 #{{ activeConv.orderId }}）</span>
              <el-button link type="primary" @click="goOrder(activeConv.orderId)">查看订单</el-button>
            </div>
            <div ref="msgBodyRef" v-loading="msgLoading" class="mc-msg-body">
              <template v-if="messages.length">
                <div
                  v-for="m in messages"
                  :key="m.messageId"
                  class="mc-msg"
                  :class="m.senderId === store.userId ? 'mine' : 'other'"
                >
                  <div class="mc-bubble">{{ m.content }}</div>
                  <div class="mc-msg-meta">{{ m.senderName }} · {{ formatTime(m.createTime) }}</div>
                </div>
              </template>
              <el-empty v-else description="暂无留言，发送第一条消息开始沟通" :image-size="60" />
            </div>
            <div class="mc-input">
              <el-input
                v-model="inputContent"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="输入留言…"
                @keydown.enter.exact.prevent="sendMessage"
              />
              <el-button
                type="primary"
                :disabled="!inputContent.trim() || sending"
                @click="sendMessage"
              >发送</el-button>
            </div>
          </template>
          <div v-else class="mc-empty">从左侧选择会话开始聊天</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const conversations = ref([])
const convLoading = ref(false)
const activeConv = ref(null)
const messages = ref([])
const msgLoading = ref(false)
const inputContent = ref('')
const sending = ref(false)
const msgBodyRef = ref(null)

/** 加载订单会话列表（按订单聚合，含对方昵称/未读数/最新消息） */
const loadConversations = async () => {
  convLoading.value = true
  try {
    conversations.value = await request.get('/message/conversations')
  } finally {
    convLoading.value = false
  }
}

/** 选中会话：先标记该订单已读（角标即时消失），再拉取消息时间线 */
const selectConversation = async (c) => {
  activeConv.value = c
  await request.put(`/message/mark-read/${c.orderId}`)
  store.fetchUnreadCount()
  await loadMessages(c.orderId)
}

/** 拉取订单消息时间线（升序，最多 100 条） */
const loadMessages = async (orderId) => {
  msgLoading.value = true
  try {
    const page = await request.get(`/message/order-page/${orderId}`, {
      params: { pageNum: 1, pageSize: 100 }
    })
    messages.value = page.records || []
    scrollToBottom()
  } finally {
    msgLoading.value = false
  }
}

/** 发送留言：成功后清空输入并刷新时间线与会话列表 */
const sendMessage = async () => {
  const content = inputContent.value.trim()
  if (!content || !activeConv.value) return
  sending.value = true
  try {
    await request.post('/message/send', { orderId: activeConv.value.orderId, content })
    inputContent.value = ''
    await loadMessages(activeConv.value.orderId)
    loadConversations()
  } finally {
    sending.value = false
  }
}

/** 跳转订单详情（留言区会自动标记已读） */
const goOrder = (orderId) => {
  router.push(`/order/${orderId}`)
}

/** 新消息到达（Home.vue WebSocket 转发）：刷新会话列表；属于当前会话则同步时间线 */
const onMessageArrive = () => {
  loadConversations()
  if (activeConv.value) loadMessages(activeConv.value.orderId)
}

/** 滚动到聊天区底部（新消息/切换会话时） */
const scrollToBottom = async () => {
  await nextTick()
  if (msgBodyRef.value) msgBodyRef.value.scrollTop = msgBodyRef.value.scrollHeight
}

/** 时间格式化：MM-dd HH:mm */
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

onMounted(() => {
  loadConversations()
  window.addEventListener('message-arrive', onMessageArrive)
})

onUnmounted(() => {
  window.removeEventListener('message-arrive', onMessageArrive)
})
</script>

<style scoped>
.message-center {
  max-width: 1100px;
  margin: 0 auto;
}

.mc-card :deep(.el-card__body) {
  padding: 0;
}

.mc-layout {
  display: flex;
  height: calc(100vh - 190px);
  min-height: 460px;
}

/* 左侧会话列表 */
.mc-list {
  width: 320px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}

.mc-list-head {
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  color: #1e3c72;
  border-bottom: 1px solid #ebeef5;
}

.mc-list-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.mc-item {
  position: relative;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}

.mc-item:hover {
  background: #f5f7fa;
}

.mc-item.active {
  background: #ecf5ff;
}

.mc-item-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 4px;
}

.mc-item-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.mc-item-time {
  font-size: 12px;
  color: #909399;
}

.mc-item-preview {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mc-item-sender {
  color: #909399;
}

.mc-item-badge {
  position: absolute;
  top: 10px;
  right: 12px;
}

/* 右侧聊天窗 */
.mc-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.mc-chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
}

.mc-msg-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}

.mc-msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.mc-msg.mine {
  align-items: flex-end;
}

.mc-msg.other {
  align-items: flex-start;
}

.mc-bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.mc-msg.mine .mc-bubble {
  background: #1e3c72;
  color: #fff;
  border-top-right-radius: 2px;
}

.mc-msg.other .mc-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 2px;
}

.mc-msg-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.mc-input {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  align-items: flex-end;
}

.mc-input .el-input {
  flex: 1;
}

.mc-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
}
</style>
