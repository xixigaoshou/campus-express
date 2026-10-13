<script setup>
/**
 * 通知中心页面
 * - el-tabs 分为「全部 / 未读」；未读 Tab 通过 isRead=0 参数由后端过滤
 * - 通知卡片：未读高亮 + 红点标签，点击标记已读；关联订单可跳转详情
 * - 支持单条已读 / 全部已读，角标随操作即时刷新
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/time'

const router = useRouter()
const store = useUserStore()

// 当前 tab：all 全部 / unread 未读
const activeTab = ref('all')
const list = ref([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 加载通知列表：未读 Tab 时带 isRead=0 过滤 */
const loadList = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (activeTab.value === 'unread') params.isRead = 0
    const data = await request.get('/notification/page', { params })
    list.value = data.records
    page.total = data.total
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  page.pageNum = 1
  loadList()
})

onMounted(loadList)

/** 点击未读通知：标记为已读并刷新角标 */
const markRead = async (n) => {
  if (n.isRead === 1) return
  await request.put(`/notification/read/${n.notificationId}`)
  n.isRead = 1
  store.fetchNotifyUnread()
}

/** 全部标记已读 */
const markAllRead = async () => {
  await request.put('/notification/read-all')
  ElMessage.success('已全部标记为已读')
  loadList()
  store.fetchNotifyUnread()
}

/** 跳转关联订单详情 */
const goOrder = (orderId) => router.push(`/order/${orderId}`)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>通知中心</h2>
      <span class="sub">订单状态变更与系统提醒一览</span>
      <el-button type="primary" plain size="small" class="read-all-btn" @click="markAllRead">
        全部已读
      </el-button>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="未读" name="unread" />
      </el-tabs>

      <div v-loading="loading" class="notify-body">
        <template v-if="list.length">
          <div
            v-for="n in list"
            :key="n.notificationId"
            class="notify-item"
            :class="{ 'is-unread': n.isRead === 0 }"
            @click="markRead(n)"
          >
            <div class="notify-top">
              <span class="notify-content">{{ n.content }}</span>
              <el-tag v-if="n.isRead === 0" type="danger" size="small">未读</el-tag>
              <el-tag v-else type="info" size="small" effect="plain">已读</el-tag>
            </div>
            <div class="notify-bottom">
              <span class="notify-time">{{ formatDateTime(n.createTime) }}</span>
              <el-link
                v-if="n.orderId"
                type="primary"
                :underline="false"
                class="order-link"
                @click.stop="goOrder(n.orderId)"
              >
                查看订单 #{{ n.orderId }} →
              </el-link>
            </div>
          </div>
        </template>
        <el-empty v-else description="暂无通知" :image-size="60" />
      </div>

      <div class="notify-pager">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :total="page.total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.page-header h2 {
  color: #1e3c72;
}

.sub {
  font-size: 13px;
  color: #909399;
}

.read-all-btn {
  margin-left: auto;
}

.notify-body {
  min-height: 120px;
}

.notify-item {
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.notify-item:hover {
  border-color: #1e3c72;
  background: #f5f7fa;
}

.notify-item.is-unread {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.notify-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notify-content {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.is-unread .notify-content {
  font-weight: 600;
}

.notify-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.notify-time {
  font-size: 12px;
  color: #c0c4cc;
}

.order-link {
  font-size: 13px;
}

.notify-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
