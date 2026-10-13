<script setup>
/**
 * 需求大厅页面
 * - 分页展示全部「待接单」订单（/order/hall-page）
 * - 列表展示：快递公司、取件地点、送达地点、报酬、期望时间、发布者
 * - 每条订单可查看详情；「接单」按钮做前置校验后调抢单接口（乐观锁）
 */
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { formatDateTimeShort, relativeTime } from '@/utils/time'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

// 筛选条件：关键词（快递公司/取件地点/送达地点/备注）、报酬范围
// 说明：取件码是取件凭据，仅在接单后由订单详情可见，大厅不展示、也不支持按取件码检索
const filters = reactive({ keyword: '', minReward: null, maxReward: null })

/** 订单列表与分页 */
const orderList = ref([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 正在接单的订单 ID（按钮 loading，防重复点击） */
const takingId = ref(null)

/** 是否新发布（30 分钟内，展示 NEW 标签） */
const isNew = (createTime) => {
  if (!createTime) return false
  return Date.now() - new Date(createTime).getTime() < 30 * 60 * 1000
}

/** 期望取件时间是否临近（2 小时内，橙色警示"将到点"） */
const expectSoon = (expectTime) => {
  if (!expectTime) return false
  return new Date(expectTime).getTime() - Date.now() < 2 * 60 * 60 * 1000
}

/** 拉取需求大厅分页数据（携带筛选条件） */
const loadOrders = async () => {
  loading.value = true
  try {
    const data = await request.get('/order/hall-page', {
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

/** 查询：重置到第一页再刷新 */
const handleSearch = () => {
  page.pageNum = 1
  loadOrders()
}

/** 重置筛选并刷新 */
const handleReset = () => {
  filters.keyword = ''
  filters.minReward = null
  filters.maxReward = null
  page.pageNum = 1
  loadOrders()
}

/** 新订单实时刷新：接收 Home.vue 转发的 WebSocket 广播事件（保留当前筛选与页码） */
const onHallNewOrder = () => {
  loadOrders()
}

// 页面挂载后立即加载订单列表，并监听新订单推送事件
onMounted(() => {
  loadOrders()
  window.addEventListener('hall-new-order', onHallNewOrder)
})

onUnmounted(() => {
  window.removeEventListener('hall-new-order', onHallNewOrder)
})

/** 分页切换 */
const handlePageChange = (p) => {
  page.pageNum = p
  loadOrders()
}

/** 查看订单详情 */
const goDetail = (orderId) => {
  router.push(`/order/${orderId}`)
}

/**
 * 抢单（乐观锁并发控制）
 * 前置校验：未登录（本地兜底）、信誉分 < 80 直接本地提示
 * 并发冲突（被别人抢走）由后端返回业务提示
 */
const takeOrder = async (order) => {
  if (!store.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  // 本地前置校验：信誉分不足 80 禁止接单（R-23）
  if (store.creditScore < 80) {
    ElMessage.error(`当前信誉分 ${store.creditScore}，低于 80 分暂不可接单`)
    return
  }
  try {
    takingId.value = order.orderId
    await request.put('/order/take-order', null, { params: { orderId: order.orderId } })
    ElMessage.success('抢单成功，去订单中心查看吧')
    loadOrders()
    store.fetchUnreadCount()
  } catch (e) {
    // 抢单失败提示已由拦截器统一弹出
  } finally {
    takingId.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>需求大厅</h2>
      <span class="sub">实时更新的待接单代取需求，点击接单即可赚取报酬</span>
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
          <template #default="{ row }">
            {{ row.expressCompany || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="pickupLocation" label="取件地点" min-width="140" show-overflow-tooltip />
        <el-table-column prop="deliverLocation" label="送达地点" min-width="140" show-overflow-tooltip />
        <el-table-column label="期望取件时间" width="150">
          <template #default="{ row }">
            <span :class="{ 'expect-soon': expectSoon(row.expectTime) }">
              {{ formatDateTimeShort(row.expectTime) }}
            </span>
            <el-tag
              v-if="expectSoon(row.expectTime)"
              type="warning"
              size="small"
              effect="plain"
              class="soon-tag"
            >将到点</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="120">
          <template #default="{ row }">
            <div class="pub-cell">
              <span>{{ relativeTime(row.createTime) }}</span>
              <el-tag v-if="isNew(row.createTime)" type="danger" size="small" effect="dark" class="new-tag">NEW</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="报酬" width="90">
          <template #default="{ row }">
            <span class="reward">¥{{ row.rewardAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布者" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.orderId)">详情</el-button>
            <el-button link type="success" :loading="takingId === row.orderId" @click="takeOrder(row)">接单</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无待接单需求，稍后再来看看吧" action-text="刷新" @action="loadOrders" />
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

/* 筛选栏 */
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

/* 期望取件时间临近警示 */
.expect-soon {
  color: #e6a23c;
  font-weight: 600;
}

.soon-tag {
  margin-left: 4px;
}

/* 发布时间相对时间 + NEW 标签 */
.pub-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
}

.new-tag {
  flex-shrink: 0;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
