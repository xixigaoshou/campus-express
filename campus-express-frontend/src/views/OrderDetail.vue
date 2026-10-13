<script setup>
/**
 * 订单详情页
 * - 完整订单信息（el-descriptions）+ 状态变更时间线（el-timeline）
 * - 内嵌留言会话组件：3 秒定时轮询拉取最新消息，模拟即时聊天
 *   发送成功清空输入框；进入页面自动标记消息已读
 * - 订单已完成时展示评价表单（1-5 星 + 文字），并展示已有双向评价
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { publisherCancelReasons, takerCancelReasons } from '@/utils/cancelReasons'
import { transferReasons, TRANSFER_MIN_CREDIT } from '@/utils/transferReasons'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const orderId = route.params.orderId

// ---------- 订单详情 ----------
const detail = ref(null)
const loading = ref(false)

/** 状态 → 标签颜色 */
const statusTagType = (status) => {
  const map = { 待接单: 'warning', 已接单: 'primary', 取件中: 'info', 配送中: 'warning', 待收货: 'success', 已完成: 'success', 已取消: 'danger' }
  return map[status] || 'info'
}

/** 信誉等级（规则 R-24：80-99 普通 / 100-119 良好 / 120+ 优秀），需求详情页展示接单者等级 */
const creditLevelOf = (score) => {
  if (score == null) return null
  if (score >= 120) return { name: '优秀', type: 'success' }
  if (score >= 100) return { name: '良好', type: 'primary' }
  return { name: '普通', type: 'info' }
}

const loadDetail = async () => {
  loading.value = true
  try {
    detail.value = await request.get(`/order/${orderId}`)
  } finally {
    loading.value = false
  }
}

// ---------- 状态操作（按身份 + 状态渲染，与订单中心逻辑一致） ----------
const isPublisher = computed(() => detail.value?.order?.publisherId === store.userId)
const isTaker = computed(
  () => detail.value?.order?.takerId != null && detail.value.order.takerId === store.userId
)

/** 是否为订单参与者（发布者或接单者）：非参与者不加载留言，避免无权限请求与错误提示 */
const isParticipant = computed(() => isPublisher.value || isTaker.value)

/** 当前订单是否有可执行的操作 */
const actionVisible = computed(() => {
  const s = detail.value?.order?.status
  // 发布者：待接单可取消；待收货可确认收货；已接单/取件中/配送中可提醒接单者（催单）
  if (isPublisher.value) {
    return s === '待接单' || s === '待收货'
      || s === '已接单' || s === '取件中' || s === '配送中'
  }
  if (isTaker.value) return s === '已接单' || s === '取件中' || s === '配送中'
  return false
})

// ---------- 取消订单原因选择（按身份加载对应原因清单） ----------
const cancelDialogVisible = ref(false)
const cancelReasonOptions = ref([])
const cancelReasonValue = ref('')
/** 当前选中的原因对象（用于展示说明） */
const selectedCancelReason = computed(() =>
  cancelReasonOptions.value.find((r) => r.reason === cancelReasonValue.value) || null
)

/** 打开取消原因选择弹窗：发布者取消加载发布者清单，接单者取消加载接单者清单 */
const openCancelDialog = () => {
  cancelReasonValue.value = ''
  cancelReasonOptions.value = isPublisher.value ? publisherCancelReasons : takerCancelReasons
  cancelDialogVisible.value = true
}

/** 发布者提醒接单者（催单）：后端校验履约中状态 + 10 分钟防刷 */
const handleUrge = async () => {
  try {
    await request.post(`/order/${orderId}/urge`)
    ElMessage.success('已提醒接单者，请耐心等待')
  } catch (e) {
    // 状态/防刷提示已由拦截器统一弹出
  }
}

// ---------- 转单接力（接单者取件前发起，其他接单者可在接力专区承接） ----------
const transferDialogVisible = ref(false)
const transferReasonValue = ref('')
const transferSubmitting = ref(false)
const selectedTransferReason = computed(
  () => transferReasons.find((r) => r.reason === transferReasonValue.value) || null
)

/** 是否处于接力中（订单标记为待接力） */
const isTransferring = computed(() => detail.value?.order?.transferStatus === '待接力')

/** 打开转单接力弹窗（原因必选，与后端 TransferReason 清单一致） */
const openTransferDialog = () => {
  // 转单权限前置提示：信誉分门槛（后端为最终校验）
  if (store.creditScore < TRANSFER_MIN_CREDIT) {
    ElMessage.error(`当前信誉分 ${store.creditScore}，低于 ${TRANSFER_MIN_CREDIT} 分暂无转单接力权限`)
    return
  }
  transferReasonValue.value = ''
  transferDialogVisible.value = true
}

/** 发起转单接力：订单状态不变，仅打上待接力标记并进入接力专区 */
const submitTransfer = async () => {
  if (!transferReasonValue.value) {
    ElMessage.warning('请选择转单原因')
    return
  }
  transferSubmitting.value = true
  try {
    await request.post('/order/transfer', { orderId: Number(orderId), reason: transferReasonValue.value })
    ElMessage.success('已发起转单接力，等待其他接单者承接')
    transferDialogVisible.value = false
    loadDetail()
  } finally {
    transferSubmitting.value = false
  }
}

/** 撤回转单接力：清空待接力标记，原接单者继续履约 */
const handleCancelTransfer = async () => {
  await ElMessageBox.confirm('撤回后订单将退出接力专区，由您继续履约。确认撤回吗？', '撤回转单接力', {
    type: 'warning',
    confirmButtonText: '确认撤回',
    cancelButtonText: '再想想'
  })
  await request.put('/order/transfer/cancel', null, { params: { orderId } })
  ElMessage.success('已撤回转单接力')
  loadDetail()
}

/** 提交取消：携带所选原因调用接口（原因必选，后端按角色校验合法性） */
const submitCancel = async () => {
  if (!cancelReasonValue.value) {
    ElMessage.warning('请选择取消原因')
    return
  }
  await request.put('/order/cancel', null, {
    params: { orderId, cancelReason: cancelReasonValue.value }
  })
  ElMessage.success('订单已取消')
  cancelDialogVisible.value = false
  loadDetail()
}

/** 接单侧推进状态（已接单 → 取件中 → 配送中） */
const handleStatus = async (targetStatus) => {
  await request.put('/order/status', {
    orderId: Number(orderId),
    targetStatus,
    changeNote: `接单者更新为${targetStatus}`
  })
  ElMessage.success(`订单已更新为「${targetStatus}」`)
  loadDetail()
}

/** 接单者确认送达（配送中 → 待收货，等待需求方确认收货）
 *  送达照片默认要求上传：已上传直接确认；未上传须显式选择「无法拍照」跳过凭证
 */
const handleConfirmArrival = async () => {
  const hasDeliverPhoto = !!detail.value?.order?.deliverPhoto
  let skipPhoto = false
  if (!hasDeliverPhoto) {
    try {
      await ElMessageBox.confirm(
        '您尚未上传送达照片。送达凭证用于需求方核对包裹，建议先上传；如确实无法拍照，可选择「无法拍照」跳过凭证直接确认送达。',
        '送达照片缺失',
        {
          type: 'warning',
          confirmButtonText: '无法拍照，继续送达',
          cancelButtonText: '先去上传',
          distinguishCancelAndClose: true
        }
      )
      skipPhoto = true
    } catch (action) {
      if (action === 'cancel') ElMessage.info('请先上传送达照片后再确认送达')
      return
    }
  } else {
    await ElMessageBox.confirm('确认包裹已送达指定地点吗？确认后等待需求方确认收货（24 小时内未确认将自动确认收货）。', '确认送达', {
      type: 'info',
      confirmButtonText: '确认送达',
      cancelButtonText: '取消'
    })
  }
  await request.put('/order/confirm-arrival', null, { params: { orderId, skipPhoto } })
  ElMessage.success('已确认送达，等待需求方确认收货')
  loadDetail()
}

// ---------- 订单照片凭证（取件选填 / 送达默认要求） ----------
const uploadingType = ref('')

/** 上传订单照片凭证：type=pickup（取件，选填）/ deliver（送达，默认要求），成功后刷新详情回显 */
const handlePhotoUpload = async (options, type) => {
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('type', type)
  uploadingType.value = type
  try {
    await request.post(`/order/${orderId}/photo`, fd)
    ElMessage.success(type === 'pickup' ? '取件照片上传成功' : '送达照片上传成功')
    await loadDetail()
  } catch (e) {
    // 格式/大小/状态校验失败提示由拦截器统一弹出
  } finally {
    uploadingType.value = ''
  }
}

/** 发布者确认收货（待收货 → 已完成，报酬结算给接单者，规则 R-3） */
const handleConfirmFinish = async () => {
  await ElMessageBox.confirm('确认已收到包裹吗？确认后订单将标记为已完成，报酬结算给接单者。', '确认收货', {
    type: 'info',
    confirmButtonText: '确认收货',
    cancelButtonText: '取消'
  })
  await request.put('/order/confirm-finish', null, { params: { orderId } })
  ElMessage.success('确认收货成功')
  loadDetail()
}

// ---------- 留言会话（3 秒轮询） ----------
const messages = ref([])
const messageLoading = ref(false)
const inputContent = ref('')
let msgTimer = null

/** 拉取会话消息（一次拉最近 100 条，轮询模拟即时聊天）
 *  非参与者（未接单/非发布者）无权查看留言，直接跳过请求，避免触发 403 提示
 */
const loadMessages = async () => {
  if (!isParticipant.value) {
    messages.value = []
    return
  }
  try {
    const data = await request.get(`/message/order-page/${orderId}`, {
      params: { pageNum: 1, pageSize: 100 },
      silent: true
    })
    messages.value = data.records
  } catch (e) {
    // 无权限 / 网络异常时静默，轮询不打断页面
  }
}

/** 标记该订单消息全部已读 */
const markRead = async () => {
  if (!isParticipant.value) return
  try {
    await request.put(`/message/mark-read/${orderId}`, null, { silent: true })
    store.fetchUnreadCount()
  } catch (e) {
    // 忽略
  }
}

/** 发送留言：成功后清空输入框并刷新消息 */
const sendMessage = async () => {
  const content = inputContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入留言内容')
    return
  }
  await request.post('/message/send', { orderId: Number(orderId), content })
  inputContent.value = ''
  loadMessages()
  markRead()
}

// 判断消息是否为自己发送（气泡左右区分）
const isMine = (msg) => msg.senderId === store.userId

// ---------- 评价 ----------
const evaluationForm = reactive({ starLevel: 0, content: '' })
const evaluations = ref([])
const myEvaluation = computed(() =>
  evaluations.value.find((e) => e.evaluatorId === store.userId)
)

/** 拉取订单双向评价
 *  非参与者无权查看（后端返回 403），直接跳过请求，避免触发越权提示
 */
const loadEvaluations = async () => {
  if (!isParticipant.value) {
    evaluations.value = []
    return
  }
  try {
    evaluations.value = await request.get(`/evaluation/list/${orderId}`, { silent: true })
  } catch (e) {
    // 无权限 / 网络异常时静默，不打断页面
  }
}

/** 提交评价：仅已完成订单可评（后端 R-21 校验） */
const submitEvaluation = async () => {
  if (!evaluationForm.starLevel) {
    ElMessage.warning('请选择星级')
    return
  }
  await request.post('/evaluation/create', {
    orderId: Number(orderId),
    starLevel: evaluationForm.starLevel,
    content: evaluationForm.content
  })
  ElMessage.success('评价成功，已联动信誉分')
  evaluationForm.starLevel = 0
  evaluationForm.content = ''
  loadEvaluations()
}

// 是否已完成（展示评价区）
const isCompleted = computed(() => detail.value?.order?.status === '已完成')

onMounted(async () => {
  await loadDetail()
  markRead()
  loadMessages()
  loadEvaluations()
  // 3 秒轮询最新留言，模拟即时聊天
  msgTimer = setInterval(() => {
    loadMessages()
    markRead()
  }, 3000)
})

onUnmounted(() => {
  clearInterval(msgTimer)
})
</script>

<template>
  <div class="page" v-loading="loading">
    <template v-if="detail">
      <div class="page-header">
        <el-button link class="back-btn" @click="router.back()">← 返回</el-button>
        <h2>订单详情 #{{ detail.order.orderId }}</h2>
        <el-tag :type="statusTagType(detail.order.status)" effect="dark">
          {{ detail.order.status }}
        </el-tag>
        <el-tag v-if="isTransferring" type="warning" effect="plain">接力中 · 等待承接</el-tag>
      </div>

      <!-- 状态操作区：按身份与状态渲染可执行操作 -->
      <el-card v-if="actionVisible" shadow="never" class="block action-card">
        <template #header>订单操作</template>
        <div class="action-bar">
          <template v-if="isPublisher">
            <el-button v-if="detail.order.status === '待接单'" type="danger" plain @click="openCancelDialog">取消订单</el-button>
            <el-button v-if="detail.order.status === '待收货'" type="success" @click="handleConfirmFinish">确认收货</el-button>
            <el-button
              v-if="['已接单', '取件中', '配送中'].includes(detail.order.status)"
              type="warning" plain
              @click="handleUrge"
            >提醒接单者</el-button>
            <span v-if="detail.order.status === '待收货'" class="action-tip">24 小时内未确认将自动确认收货</span>
          </template>
          <template v-else-if="isTaker">
            <el-button v-if="detail.order.status === '已接单'" type="success" @click="handleStatus('取件中')">更新为取件中</el-button>
            <el-button v-if="detail.order.status === '取件中'" type="success" @click="handleStatus('配送中')">更新为配送中</el-button>

            <!-- 取件照片：已接单/取件中可上传（选填凭证） -->
            <el-upload
              v-if="['已接单', '取件中'].includes(detail.order.status)"
              :show-file-list="false"
              accept="image/*"
              :http-request="(o) => handlePhotoUpload(o, 'pickup')"
            >
              <el-button type="primary" plain :loading="uploadingType === 'pickup'">
                {{ detail.order.pickupPhoto ? '重新上传取件照片' : '上传取件照片（选填）' }}
              </el-button>
            </el-upload>

            <!-- 送达照片：配送中可上传（默认要求，无法拍照可跳过） -->
            <el-upload
              v-if="detail.order.status === '配送中'"
              :show-file-list="false"
              accept="image/*"
              :http-request="(o) => handlePhotoUpload(o, 'deliver')"
            >
              <el-button type="primary" plain :loading="uploadingType === 'deliver'">
                {{ detail.order.deliverPhoto ? '重新上传送达照片' : '上传送达照片（应上传）' }}
              </el-button>
            </el-upload>

            <el-button v-if="detail.order.status === '配送中'" type="success" @click="handleConfirmArrival">确认送达</el-button>
            <el-button v-if="detail.order.status === '已接单' || detail.order.status === '取件中'" type="danger" plain @click="openCancelDialog">取消订单</el-button>

            <!-- 转单接力：取件前（已接单/取件中）可发起；接力中可撤回 -->
            <el-button
              v-if="['已接单', '取件中'].includes(detail.order.status) && !isTransferring"
              type="warning" plain
              @click="openTransferDialog"
            >发起转单接力</el-button>
            <el-button
              v-if="isTransferring"
              type="warning"
              @click="handleCancelTransfer"
            >撤回转单接力</el-button>
            <span v-if="isTransferring" class="action-tip">已进入接力专区，等待其他接单者承接（30 分钟无人接力将自动撤回）</span>
          </template>
        </div>
      </el-card>

      <el-row :gutter="16">
        <!-- 左侧：订单信息 + 状态时间线 -->
        <el-col :span="14">
          <el-card shadow="never" class="block">
            <template #header>订单信息</template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="快递公司">{{ detail.order.expressCompany || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="取件码">{{ detail.order.pickupCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="取件地点">{{ detail.order.pickupLocation }}</el-descriptions-item>
              <el-descriptions-item label="送达地点">{{ detail.order.deliverLocation }}</el-descriptions-item>
              <el-descriptions-item label="期望取件时间">{{ detail.order.expectTime }}</el-descriptions-item>
              <el-descriptions-item label="报酬金额">
                <span class="reward">¥{{ detail.order.rewardAmount }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="发布者">{{ detail.order.publisherName }}</el-descriptions-item>
              <el-descriptions-item label="接单者">
                <template v-if="detail.order.takerName">
                  {{ detail.order.takerName }}
                  <el-tag v-if="creditLevelOf(detail.order.takerCreditScore)" :type="creditLevelOf(detail.order.takerCreditScore).type" size="small" class="credit-tag">
                    信誉 {{ detail.order.takerCreditScore }} · {{ creditLevelOf(detail.order.takerCreditScore).name }}
                  </el-tag>
                </template>
                <span v-else>待接单</span>
              </el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ detail.order.remark || '无' }}</el-descriptions-item>
              <el-descriptions-item v-if="isTransferring" label="转单原因" :span="2">
                <el-tag type="warning" effect="plain">{{ detail.order.transferReason || '未填写' }}</el-tag>
                <span class="transfer-hint">原接单者已发起接力，其他接单者可在接力专区承接</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card v-if="detail.order.pickupPhoto || detail.order.deliverPhoto || isTaker" shadow="never" class="block">
            <template #header>包裹凭证</template>
            <div class="photo-row">
              <div class="photo-item">
                <div class="photo-label">取件照片<span class="photo-opt">（选填）</span></div>
                <el-image
                  v-if="detail.order.pickupPhoto"
                  :src="detail.order.pickupPhoto"
                  :preview-src-list="[detail.order.pickupPhoto]"
                  fit="cover"
                  class="photo-thumb"
                />
                <div v-else class="photo-empty">未上传</div>
              </div>
              <div class="photo-item">
                <div class="photo-label">送达照片<span class="photo-req">（应上传，无法拍照可跳过）</span></div>
                <el-image
                  v-if="detail.order.deliverPhoto"
                  :src="detail.order.deliverPhoto"
                  :preview-src-list="[detail.order.deliverPhoto]"
                  fit="cover"
                  class="photo-thumb"
                />
                <div v-else class="photo-empty">未上传</div>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="block">
            <template #header>状态变更轨迹</template>
            <el-timeline>
              <el-timeline-item
                v-for="h in detail.statusHistory"
                :key="h.historyId"
                :timestamp="h.changeTime"
                placement="top"
                :type="h.toStatus === '已完成' || h.toStatus === '已取消' ? 'success' : 'primary'"
              >
                <div class="history-item">
                  <b>{{ h.fromStatus ? h.fromStatus + ' → ' : '' }}{{ h.toStatus }}</b>
                  <span class="history-note">{{ h.changeNote }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>

        <!-- 右侧：留言会话 -->
        <el-col :span="10">
          <el-card shadow="never" class="block chat-card">
            <template #header>{{ isParticipant ? '订单留言（3 秒自动刷新）' : '订单留言' }}</template>
            <div class="chat-body" ref="chatBody">
              <!-- 非参与者（未接单）：留言仅对订单双方可见，接单后方可查看 -->
              <el-empty
                v-if="!isParticipant"
                description="接单后即可查看并参与订单留言"
                :image-size="60"
              />
              <template v-else>
                <el-empty v-if="messages.length === 0" description="暂无留言，发起第一条吧" :image-size="60" />
                <div
                  v-for="msg in messages"
                  :key="msg.messageId"
                  class="msg-row"
                  :class="isMine(msg) ? 'mine' : 'other'"
                >
                  <div class="msg-bubble">
                    <div class="msg-name">{{ msg.senderName }}</div>
                    <div class="msg-content">{{ msg.content }}</div>
                    <div class="msg-time">{{ msg.createTime }}</div>
                  </div>
                </div>
              </template>
            </div>
            <div class="chat-input" v-if="isParticipant">
              <el-input
                v-model="inputContent"
                type="textarea"
                :rows="2"
                placeholder="输入留言内容（敏感词自动过滤）"
                @keyup.ctrl.enter="sendMessage"
              />
              <el-button type="primary" class="send-btn" @click="sendMessage">发送</el-button>
            </div>
          </el-card>

          <!-- 报酬结算说明（R-28：订单完成后冻结报酬自动结算至接单者钱包） -->
          <el-alert
            v-if="isCompleted"
            title="报酬结算说明：订单完成后，发布者发布时冻结的报酬将自动结算至接单者钱包余额，可在个人中心查看收支流水。"
            type="info"
            :closable="false"
            show-icon
            class="settle-alert"
          />

          <!-- 评价区：订单已完成时展示（id 供「去评价」锚点定位） -->
          <el-card v-if="isCompleted" id="evaluation" shadow="never" class="block">
            <template #header>订单评价</template>
            <template v-if="myEvaluation">
              <el-alert title="您已评价过该订单" type="info" :closable="false" show-icon />
            </template>
            <template v-else>
              <div class="rate-row">
                <span>满意度：</span>
                <el-rate v-model="evaluationForm.starLevel" />
              </div>
              <el-input
                v-model="evaluationForm.content"
                type="textarea"
                :rows="2"
                placeholder="写下评价内容（选填）"
                class="rate-input"
              />
              <el-button type="primary" class="rate-btn" @click="submitEvaluation">提交评价</el-button>
            </template>

            <el-divider v-if="evaluations.length" />
            <div v-for="e in evaluations" :key="e.evaluationId" class="eval-item">
              <el-rate :model-value="e.starLevel" disabled size="small" />
              <span class="eval-name">{{ e.evaluatorName }} → {{ e.evaluateeName }}</span>
              <div class="eval-content">{{ e.content || '（无文字评价）' }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 取消订单原因选择弹窗：发布者/接单者各自独立的原因清单（编号 + 原因 + 说明） -->
    <el-dialog
      v-model="cancelDialogVisible"
      :title="(isPublisher ? '发布者' : '接单者') + '取消订单'"
      width="540px"
      append-to-body
    >
      <div class="cancel-reason-head">
        <span>订单 #{{ detail?.order?.orderId }}（¥{{ detail?.order?.rewardAmount }}）</span>
        <span class="cancel-reason-sub">{{ isPublisher ? '取消后订单标记为已取消' : '取消后将扣信誉分5并退回待接单' }}</span>
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

    <!-- 转单接力弹窗：选择转单原因后进入接力专区（订单状态不变，30 分钟无人接力自动撤回） -->
    <el-dialog v-model="transferDialogVisible" title="发起转单接力" width="540px" append-to-body>
      <div class="cancel-reason-head">
        <span>订单 #{{ detail?.order?.orderId }}（¥{{ detail?.order?.rewardAmount }}）</span>
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
        title="接力说明：订单将进入接力专区等待其他接单者承接，期间订单状态保持「已接单/取件中」不变。30 分钟无人接力将自动撤回，您需继续履约。"
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
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.back-btn {
  font-size: 14px;
  color: #909399;
}

.page-header h2 {
  color: #1e3c72;
}

.action-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.action-tip {
  font-size: 12px;
  color: #e6a23c;
}

.block {
  margin-bottom: 16px;
}

.reward {
  color: #ff7e3d;
  font-weight: 700;
}

.credit-tag {
  margin-left: 6px;
}

/* 包裹凭证卡片 */
.photo-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.photo-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.photo-label {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}

.photo-opt {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.photo-req {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 400;
}

.photo-thumb {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.photo-empty {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #c0c4cc;
}

.settle-alert {
  margin-bottom: 16px;
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-note {
  font-size: 12px;
  color: #909399;
}

/* 留言会话样式 */
.chat-card .el-card__body {
  display: flex;
  flex-direction: column;
}

.chat-body {
  height: 380px;
  overflow-y: auto;
  padding: 4px 2px;
  margin-bottom: 10px;
}

.msg-row {
  display: flex;
  margin-bottom: 10px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.msg-row.other {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 75%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.mine .msg-bubble {
  background: #1e3c72;
  color: #fff;
  border-top-right-radius: 2px;
}

.other .msg-bubble {
  background: #f0f2f5;
  color: #303133;
  border-top-left-radius: 2px;
}

.msg-name {
  font-size: 11px;
  opacity: 0.75;
  margin-bottom: 2px;
}

.msg-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 4px;
}

.chat-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.send-btn {
  height: 62px;
}

/* 评价区 */
.rate-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.rate-input {
  margin-bottom: 10px;
}

.rate-btn {
  width: 100%;
}

.eval-item {
  padding: 6px 0;
  border-bottom: 1px dashed #f0f2f5;
}

.eval-name {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.eval-content {
  margin-top: 4px;
  font-size: 13px;
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

.transfer-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.transfer-alert {
  margin-top: 12px;
}
</style>
