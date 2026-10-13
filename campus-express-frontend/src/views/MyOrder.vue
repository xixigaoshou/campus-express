<script setup>
/**
 * 个人订单中心页面
 * - el-tabs 分为【我的发布】/【我的接单】
 * - 每个 tab 内通过 radio 分组：全部 / 进行中 / 历史（映射后端 statusList 参数）
 * - 订单卡片按状态动态渲染操作按钮：
 *   发布侧：待接单→取消；配送中→确认收货
 *   接单侧：已接单→更新为取件中、取件中→更新为配送中；已接单/取件中可取消（扣分退回）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { publisherCancelReasons, takerCancelReasons } from '@/utils/cancelReasons'
import { transferReasons, TRANSFER_MIN_CREDIT } from '@/utils/transferReasons'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

// 当前 tab：publish 我的发布 / take 我的接单
// 按角色权限：发布者默认我的发布，接单者默认我的接单
const activeTab = ref(store.canPublish ? 'publish' : 'take')
// 状态分组：'' 全部 / 进行中 / 历史
const activeGroup = ref('')

const orderList = ref([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 状态 → 标签颜色映射 */
const statusTagType = (status) => {
  const map = {
    待接单: 'warning',
    已接单: 'primary',
    取件中: 'info',
    配送中: 'warning',
    待收货: 'success',
    已完成: 'success',
    已取消: 'danger'
  }
  return map[status] || 'info'
}

/** 拉取列表：根据当前 tab 调我的发布/我的接单分页接口 */
const loadOrders = async () => {
  loading.value = true
  try {
    const url = activeTab.value === 'publish' ? '/order/my-publish-page' : '/order/my-take-page'
    const params = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (activeGroup.value) {
      params.statusList = [activeGroup.value] // 后端按"进行中/历史"分组过滤
    }
    const data = await request.get(url, { params })
    orderList.value = data.records
    page.total = data.total
  } finally {
    loading.value = false
  }
}

// tab / 分组切换时回到第一页并刷新
watch([activeTab, activeGroup], () => {
  page.pageNum = 1
  loadOrders()
})

onMounted(loadOrders)

/** 查看详情 */
const goDetail = (orderId) => router.push(`/order/${orderId}`)

/** 一键再次发布：带模板订单 ID 跳转发布页，页面自动预填上次订单信息 */
const rePublish = (order) => {
  router.push({ path: '/publish', query: { templateOrderId: order.orderId } })
}

/** 修改待接单订单：跳转发布页编辑模式，预填订单信息后提交 /order/update-wait */
const editOrder = (order) => {
  router.push({ path: '/publish', query: { editOrderId: order.orderId } })
}

/** 去评价：跳转订单详情页的评价区（仅已完成且本人未评价的订单展示入口） */
const goEvaluate = (order) => {
  router.push({ path: `/order/${order.orderId}`, hash: '#evaluation' })
}

// ---------- 取消订单弹窗：按操作角色选择原因后提交 ----------
const cancelDialogVisible = ref(false)
const cancelOrderTarget = ref(null)
const cancelReasonOptions = ref([])
const cancelReasonValue = ref('')
/** 当前选中的原因对象（用于展示说明） */
const selectedCancelReason = computed(() =>
  cancelReasonOptions.value.find((r) => r.reason === cancelReasonValue.value) || null
)

/** 打开取消订单弹窗：发布者角色加载发布者清单，接单者角色加载接单者清单 */
const openCancelDialog = (order) => {
  cancelOrderTarget.value = order
  cancelReasonValue.value = ''
  cancelReasonOptions.value = store.canPublish ? publisherCancelReasons : takerCancelReasons
  cancelDialogVisible.value = true
}

/** 提交取消：携带所选原因调用接口（原因必选，后端按角色校验合法性） */
const submitCancel = async () => {
  if (!cancelReasonValue.value) {
    ElMessage.warning('请选择取消原因')
    return
  }
  await request.put('/order/cancel', null, {
    params: { orderId: cancelOrderTarget.value.orderId, cancelReason: cancelReasonValue.value }
  })
  ElMessage.success('订单已取消')
  cancelDialogVisible.value = false
  loadOrders()
}

/** 更新订单状态（接单侧操作：取件中 / 配送中） */
const handleStatus = async (order, targetStatus) => {
  await request.put('/order/status', {
    orderId: order.orderId,
    targetStatus,
    changeNote: `接单者更新为${targetStatus}`
  })
  ElMessage.success(`订单已更新为「${targetStatus}」`)
  loadOrders()
}

/** 确认送达（接单侧操作：配送中 → 待收货）
 *  送达照片默认要求上传：已上传直接确认；未上传可进入详情上传，或显式「无法拍照」跳过
 */
const handleConfirmArrival = async (order) => {
  let skipPhoto = false
  if (!order.deliverPhoto) {
    try {
      await ElMessageBox.confirm(
        '该订单尚未上传送达照片，送达凭证用于需求方核对包裹，建议进入订单详情上传。如确实无法拍照，可选择「无法拍照」跳过。',
        '送达照片缺失',
        {
          type: 'warning',
          confirmButtonText: '无法拍照，继续送达',
          cancelButtonText: '去上传凭证',
          distinguishCancelAndClose: true
        }
      )
      skipPhoto = true
    } catch (action) {
      if (action === 'cancel') router.push(`/order/${order.orderId}`)
      return
    }
  } else {
    await ElMessageBox.confirm('确认包裹已送达指定地点吗？确认后等待需求方确认收货（24 小时内未确认将自动确认）。', '确认送达', {
      type: 'info',
      confirmButtonText: '确认送达',
      cancelButtonText: '取消'
    })
  }
  await request.put('/order/confirm-arrival', null, { params: { orderId: order.orderId, skipPhoto } })
  ElMessage.success('已确认送达，等待需求方确认收货')
  loadOrders()
}

/** 确认收货（发布侧操作：待收货 → 已完成，报酬结算给接单者） */
const handleConfirmFinish = async (order) => {
  await ElMessageBox.confirm('确认已收到包裹吗？确认后订单将标记为已完成，报酬结算给接单者。', '确认收货', {
    type: 'info',
    confirmButtonText: '确认收货',
    cancelButtonText: '取消'
  })
  await request.put('/order/confirm-finish', null, { params: { orderId: order.orderId } })
  ElMessage.success('确认收货成功')
  loadOrders()
}

// ---------- 转单接力（接单者取件前发起，进入接力专区等待他人承接） ----------
const transferDialogVisible = ref(false)
const transferOrderTarget = ref(null)
const transferReasonValue = ref('')
const transferSubmitting = ref(false)
const selectedTransferReason = computed(
  () => transferReasons.find((r) => r.reason === transferReasonValue.value) || null
)

/** 订单是否处于接力中 */
const isTransferring = (order) => order.transferStatus === '待接力'

/** 打开转单接力弹窗 */
const openTransferDialog = (order) => {
  // 转单权限前置提示：信誉分门槛（后端为最终校验）
  if (store.creditScore < TRANSFER_MIN_CREDIT) {
    ElMessage.error(`当前信誉分 ${store.creditScore}，低于 ${TRANSFER_MIN_CREDIT} 分暂无转单接力权限`)
    return
  }
  transferOrderTarget.value = order
  transferReasonValue.value = ''
  transferDialogVisible.value = true
}

/** 发起转单接力：订单状态不变，仅打上待接力标记 */
const submitTransfer = async () => {
  if (!transferReasonValue.value) {
    ElMessage.warning('请选择转单原因')
    return
  }
  transferSubmitting.value = true
  try {
    await request.post('/order/transfer', {
      orderId: transferOrderTarget.value.orderId,
      reason: transferReasonValue.value
    })
    ElMessage.success('已发起转单接力，等待其他接单者承接')
    transferDialogVisible.value = false
    loadOrders()
  } finally {
    transferSubmitting.value = false
  }
}

/** 撤回转单接力：清空标记，原接单者继续履约 */
const handleCancelTransfer = async (order) => {
  await ElMessageBox.confirm('撤回后订单将退出接力专区，由您继续履约。确认撤回吗？', '撤回转单接力', {
    type: 'warning',
    confirmButtonText: '确认撤回',
    cancelButtonText: '再想想'
  })
  await request.put('/order/transfer/cancel', null, { params: { orderId: order.orderId } })
  ElMessage.success('已撤回转单接力')
  loadOrders()
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>我的订单</h2>
      <span class="sub">管理发布的代取需求与承接的订单</span>
    </div>

    <el-card shadow="never">
      <!-- 按角色权限条件渲染 Tab：发布者仅【我的发布】；接单者仅【我的接单】 -->
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="store.canPublish" label="我的发布" name="publish" />
        <el-tab-pane v-if="!store.canPublish" label="我的接单" name="take" />
      </el-tabs>

      <!-- 全部 / 进行中 / 历史 分组 -->
      <div class="group-bar">
        <el-radio-group v-model="activeGroup">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="进行中">进行中</el-radio-button>
          <el-radio-button value="历史">历史</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 订单卡片列表 -->
      <div v-loading="loading">
        <EmptyState
          v-if="!loading && orderList.length === 0"
          :description="store.canPublish ? '还没有发布过订单' : '还没有接过订单'"
          :action-text="store.canPublish ? '去发布需求' : '去需求大厅'"
          @action="router.push(store.canPublish ? '/publish' : '/hall')"
        />
        <div v-else class="order-cards">
          <el-card v-for="order in orderList" :key="order.orderId" class="order-card" shadow="hover">
            <div class="card-top">
              <el-tag :type="statusTagType(order.status)" effect="dark">{{ order.status }}</el-tag>
              <span class="order-no">订单 #{{ order.orderId }}</span>
              <el-tag v-if="isTransferring(order)" type="warning" effect="plain" size="small">接力中</el-tag>
              <span class="reward">¥{{ order.rewardAmount }}</span>
            </div>

            <div class="card-body">
              <div class="route">
                <span class="dot pickup"></span>{{ order.pickupLocation }}
                <span class="arrow">→</span>
                <span class="dot deliver"></span>{{ order.deliverLocation }}
              </div>
              <div class="meta">
                <span>快递：{{ order.expressCompany || '未知' }} {{ order.pickupCode || '' }}</span>
                <span>期望取件：{{ order.expectTime }}</span>
                <span v-if="order.takerName">接单者：{{ order.takerName }}</span>
                <span v-else-if="activeTab === 'publish'">接单者：等待接单中</span>
                <!-- 取消原因：已取消终态直接展示；退回待接单的订单标注取消来源 -->
                <span v-if="order.status === '已取消' && order.cancelReason" class="cancel-reason">
                  取消原因：{{ order.cancelReason }}
                </span>
                <span v-else-if="order.status === '待接单' && order.cancelReason" class="cancel-reason warn">
                  已退回待接单（原因：{{ order.cancelReason }}）
                </span>
              </div>
            </div>

            <!-- 操作按钮：按状态动态渲染 -->
            <div class="card-actions">
              <el-button link type="primary" @click="goDetail(order.orderId)">查看详情</el-button>

              <!-- 发布侧：待接单可取消；待收货可确认收货（24h 未确认自动确认） -->
              <template v-if="activeTab === 'publish'">
                <el-button
                  v-if="order.status === '待接单'"
                  link type="primary"
                  @click="editOrder(order)"
                >修改订单</el-button>
                <el-button
                  v-if="order.status === '待接单'"
                  link type="danger"
                  @click="openCancelDialog(order)"
                >取消订单</el-button>
                <el-button
                  v-if="order.status === '待收货'"
                  link type="success"
                  @click="handleConfirmFinish(order)"
                >确认收货</el-button>
                <span v-if="order.status === '待收货'" class="tip-text">24 小时内未确认将自动确认收货</span>
                <!-- 历史订单可一键再次发布（预填上次包裹信息） -->
                <el-button
                  v-if="order.status === '已完成' || order.status === '已取消'"
                  link type="primary"
                  @click="rePublish(order)"
                >再次发布</el-button>
                <!-- 评价闭环：已完成且本人未评价 → 去评价；已评价 → 标注 -->
                <el-button
                  v-if="order.status === '已完成' && !order.myEvaluated"
                  link type="success"
                  @click="goEvaluate(order)"
                >去评价</el-button>
                <span v-else-if="order.status === '已完成' && order.myEvaluated" class="tip-text done">已评价</span>
              </template>

              <!-- 接单侧：已接单/取件中可推进状态或取消；配送中可确认送达 -->
              <template v-else>
                <el-button
                  v-if="order.status === '已接单'"
                  link type="success"
                  @click="handleStatus(order, '取件中')"
                >更新为取件中</el-button>
                <el-button
                  v-if="order.status === '取件中'"
                  link type="success"
                  @click="handleStatus(order, '配送中')"
                >更新为配送中</el-button>
                <el-button
                  v-if="order.status === '配送中'"
                  link type="success"
                  @click="handleConfirmArrival(order)"
                >确认送达</el-button>
                <el-button
                  v-if="order.status === '已接单' || order.status === '取件中'"
                  link type="danger"
                  @click="openCancelDialog(order)"
                >取消订单</el-button>
                <!-- 转单接力：取件前可发起；接力中可撤回 -->
                <el-button
                  v-if="['已接单', '取件中'].includes(order.status) && !isTransferring(order)"
                  link type="warning"
                  @click="openTransferDialog(order)"
                >发起转单接力</el-button>
                <el-button
                  v-if="isTransferring(order)"
                  link type="warning"
                  @click="handleCancelTransfer(order)"
                >撤回转单接力</el-button>
                <span v-if="isTransferring(order)" class="tip-text">接力中（{{ order.transferReason }}），30 分钟无人接力将自动撤回</span>
                <span v-if="order.status === '待接单'" class="tip-text">订单已退回大厅，等待重新接单</span>
                <span v-else-if="order.status === '待收货'" class="tip-text">等待需求方确认收货，24 小时内未确认将自动确认到账</span>
                <!-- 评价闭环：已完成且本人未评价 → 去评价；已评价 → 标注 -->
                <el-button
                  v-if="order.status === '已完成' && !order.myEvaluated"
                  link type="success"
                  @click="goEvaluate(order)"
                >去评价</el-button>
                <span v-else-if="order.status === '已完成' && order.myEvaluated" class="tip-text done">已评价</span>
              </template>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :total="page.total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadOrders"
          @size-change="loadOrders"
        />
      </div>
    </el-card>

    <!-- 取消订单原因选择弹窗：发布者/接单者各自独立的原因清单（编号 + 原因 + 说明） -->
    <el-dialog
      v-model="cancelDialogVisible"
      :title="(store.canPublish ? '发布者' : '接单者') + '取消订单'"
      width="540px"
      append-to-body
    >
      <div class="cancel-reason-head">
        <span>订单 #{{ cancelOrderTarget?.orderId }}（¥{{ cancelOrderTarget?.rewardAmount }}）</span>
        <span class="cancel-reason-sub">{{ store.canPublish ? '取消后订单标记为已取消' : '取消后将扣信誉分5并退回待接单' }}</span>
      </div>
      <el-select v-model="cancelReasonValue" placeholder="请选择取消原因" style="width: 100%">
        <el-option v-for="(r, i) in cancelReasonOptions" :key="i" :label="r.reason" :value="r.reason">
          <div class="cancel-option">
            <span>{{ i + 1 }}. {{ r.reason }}</span>
            <span class="cancel-option-desc">{{ r.desc }}</span>
          </div>
        </el-option>
      </el-select>
      <div v-if="selectedCancelReason" class="cancel-reason-desc">说明：{{ selectedCancelReason.desc }}</div>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">再想想</el-button>
        <el-button type="danger" :disabled="!cancelReasonValue" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 转单接力弹窗：选择转单原因后进入接力专区 -->
    <el-dialog v-model="transferDialogVisible" title="发起转单接力" width="540px" append-to-body>
      <div class="cancel-reason-head">
        <span>订单 #{{ transferOrderTarget?.orderId }}（¥{{ transferOrderTarget?.rewardAmount }}）</span>
        <span class="cancel-reason-sub">转单后由其他接单者承接，您不扣信誉分</span>
      </div>
      <el-select v-model="transferReasonValue" placeholder="请选择转单原因" style="width: 100%">
        <el-option v-for="(r, i) in transferReasons" :key="i" :label="r.reason" :value="r.reason">
          <div class="cancel-option">
            <span>{{ i + 1 }}. {{ r.reason }}</span>
            <span class="cancel-option-desc">{{ r.desc }}</span>
          </div>
        </el-option>
      </el-select>
      <div v-if="selectedTransferReason" class="cancel-reason-desc">说明：{{ selectedTransferReason.desc }}</div>
      <el-alert
        title="接力说明：订单将进入接力专区等待其他接单者承接，期间订单状态不变。30 分钟无人接力将自动撤回，您需继续履约。"
        type="info" :closable="false" show-icon class="transfer-alert"
      />
      <template #footer>
        <el-button @click="transferDialogVisible = false">再想想</el-button>
        <el-button type="warning" :disabled="!transferReasonValue" :loading="transferSubmitting" @click="submitTransfer">确认发起接力</el-button>
      </template>
    </el-dialog>
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

.group-bar {
  margin-bottom: 16px;
}

.order-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 16px;
}

.order-card {
  cursor: pointer;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.order-no {
  color: #909399;
  font-size: 13px;
}

.reward {
  margin-left: auto;
  color: #ff7e3d;
  font-weight: 700;
  font-size: 16px;
}

.card-body {
  padding: 6px 0 10px;
}

.route {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot.pickup {
  background: #f56c6c;
}

.dot.deliver {
  background: #67c23a;
}

.arrow {
  color: #909399;
}

.meta {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.cancel-reason {
  color: #f56c6c;
}

.cancel-reason.warn {
  color: #e6a23c;
}

/* 取消原因选择弹窗 */
.cancel-reason-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}

.cancel-reason-sub {
  font-size: 12px;
  color: #e6a23c;
}

.cancel-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.cancel-option-desc {
  font-size: 12px;
  color: #909399;
}

.cancel-reason-desc {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
}

.transfer-alert {
  margin-top: 12px;
}

.card-actions {
  border-top: 1px solid #f0f2f5;
  padding-top: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.tip-text {
  font-size: 12px;
  color: #e6a23c;
}

.tip-text.done {
  color: #909399;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
