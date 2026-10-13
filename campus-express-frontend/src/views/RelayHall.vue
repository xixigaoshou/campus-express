<script setup>
/**
 * 接力专区页面
 * - 分页展示「转单接力中」的订单（/order/relay-page）
 * - 这些订单已由原接单者发起转单、尚未被他人接力，订单状态仍为已接单/取件中
 * - 每条订单展示：原接单者、转单原因、取件/送达地点、报酬、期望取件时间
 * - 「接力接单」按钮调 /order/relay-take（乐观锁），接力成功后接单者换为自己、订单继续履约
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { formatDateTimeShort, relativeTime } from '@/utils/time'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

// 筛选条件：关键词（快递公司/取件地点/送达地点/备注）、报酬范围
// 说明：取件码是取件凭据，接力成功前不展示，也不支持按取件码检索
const filters = reactive({ keyword: '', minReward: null, maxReward: null })

const orderList = ref([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 正在接力的订单 ID（按钮 loading，防重复点击） */
const takingId = ref(null)

/** 期望取件时间是否临近（2 小时内，橙色警示） */
const expectSoon = (expectTime) => {
  if (!expectTime) return false
  return new Date(expectTime).getTime() - Date.now() < 2 * 60 * 60 * 1000
}

/** 是否为本人发起的转单（本人不可接力自己的单） */
const isOwnTransfer = (order) => order.takerId === store.userId

/** 拉取接力专区分页数据 */
const loadOrders = async () => {
  loading.value = true
  try {
    const data = await request.get('/order/relay-page', {
      params: {
        pageNum: page.pageNum,
        pageSize: page.pageSize,
        keyword: filters.keyword.trim() || undefined,
        minReward: filters.minReward ?? undefined,
        maxReward: filters.maxReward ?? undefined
      }
    })
    orderList.value = data.records
    page.total = data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.pageNum = 1
  loadOrders()
}

const handleReset = () => {
  filters.keyword = ''
  filters.minReward = null
  filters.maxReward = null
  page.pageNum = 1
  loadOrders()
}

/** 接力列表实时刷新：接收 Home.vue 转发的 WebSocket 广播事件 */
const onRelayRefresh = () => {
  loadOrders()
}

onMounted(() => {
  loadOrders()
  window.addEventListener('relay-refresh', onRelayRefresh)
})

onUnmounted(() => {
  window.removeEventListener('relay-refresh', onRelayRefresh)
})

const handlePageChange = (p) => {
  page.pageNum = p
  loadOrders()
}

const goDetail = (orderId) => {
  router.push(`/order/${orderId}`)
}

/**
 * 接力接单：乐观锁并发控制，前置信誉分本地校验
 * 并发冲突（已被他人接力）由后端返回业务提示
 */
const relayTake = async (order) => {
  if (!store.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (store.creditScore < 80) {
    ElMessage.error(`当前信誉分 ${store.creditScore}，低于 80 分暂不可接单`)
    return
  }
  await ElMessageBox.confirm(
    `确认接力订单 #${order.orderId} 吗？接力后订单由您继续履约，原接单者不再承担。`,
    '接力接单',
    { type: 'info', confirmButtonText: '确认接力', cancelButtonText: '再看看' }
  )
  try {
    takingId.value = order.orderId
    await request.put('/order/relay-take', null, { params: { orderId: order.orderId } })
    ElMessage.success('接力成功，去订单中心查看吧')
    loadOrders()
    store.fetchUnreadCount()
  } catch (e) {
    // 接力失败提示已由拦截器统一弹出
  } finally {
    takingId.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>接力专区</h2>
      <span class="sub">其他接单者因故无法取件而转出的订单，接力承接即可继续赚取报酬</span>
    </div>

    <el-card shadow="never">
      <!-- 筛选栏：关键词 + 报酬范围 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索快递公司 / 取件地点 / 送达地点 / 备注"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="filter-reward">
          <span class="reward-label">报酬范围</span>
          <el-input-number v-model="filters.minReward" :min="0" :max="50" :precision="2" :step="1" controls-position="right" placeholder="最低" class="reward-input" />
          <span class="reward-sep">-</span>
          <el-input-number v-model="filters.maxReward" :min="0" :max="50" :precision="2" :step="1" controls-position="right" placeholder="最高" class="reward-input" />
        </div>

        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="orderList" stripe>
        <el-table-column prop="expressCompany" label="快递公司" width="100">
          <template #default="{ row }">{{ row.expressCompany || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="pickupLocation" label="取件地点" min-width="130" show-overflow-tooltip />
        <el-table-column prop="deliverLocation" label="送达地点" min-width="130" show-overflow-tooltip />
        <el-table-column label="期望取件时间" width="150">
          <template #default="{ row }">
            <span :class="{ 'expect-soon': expectSoon(row.expectTime) }">
              {{ formatDateTimeShort(row.expectTime) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="转单原因" min-width="160">
          <template #default="{ row }">
            <el-tag type="warning" effect="plain" size="small">{{ row.transferReason || '未填写' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原接单者" width="110">
          <template #default="{ row }">
            {{ row.takerName || '-' }}
            <el-tag v-if="isOwnTransfer(row)" type="info" size="small" effect="plain" class="own-tag">我发起的</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="转单时间" width="110">
          <template #default="{ row }">{{ relativeTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="报酬" width="90">
          <template #default="{ row }"><span class="reward">¥{{ row.rewardAmount }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.orderId)">详情</el-button>
            <el-button
              link type="success"
              :loading="takingId === row.orderId"
              :disabled="isOwnTransfer(row)"
              @click="relayTake(row)"
            >接力接单</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无接力订单，稍后再来看看吧" action-text="刷新" @action="loadOrders" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :total="page.total"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handlePageChange(page.pageNum)"
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-keyword {
  width: 320px;
}

.filter-reward {
  display: flex;
  align-items: center;
  gap: 6px;
}

.reward-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.reward-input {
  width: 110px;
}

.reward-sep {
  color: #c0c4cc;
}

.reward {
  color: #ff7e3d;
  font-weight: 700;
}

.expect-soon {
  color: #e6a23c;
  font-weight: 600;
}

.own-tag {
  margin-left: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>