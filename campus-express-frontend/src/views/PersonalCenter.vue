<script setup>
/**
 * 个人中心页面
 * - 个人信息表单回显：学号/姓名只读，宿舍区、手机号可修改（学号不可改）
 * - 统计卡片：发布订单总数、取消订单数、接单完成数、累计收入、累计支出、信誉分等级
 *   （统计基于我的发布/我的接单接口聚合；等级按 R-24 区间本地映射）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

// ---------- 个人信息表单 ----------
const formRef = ref()
const form = reactive({
  studentNo: '',
  username: '',
  phone: '',
  dormArea: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

/** 回显个人信息（来自 /user/info，已由 store 缓存，无需重复请求） */
const loadInfo = async () => {
  await store.fetchInfo()
  form.studentNo = store.studentNo || ''
}

// ---------- 统计 ----------
const stats = reactive({ publishTotal: 0, cancelTotal: 0, takeCompleted: 0, income: 0, expense: 0, pendingEvaluation: 0 })

/** 加载统计：后端全量聚合（COUNT / SUM），不受分页条数上限影响，口径准确 */
const loadStats = async () => {
  const data = await request.get('/user/stats')
  stats.publishTotal = data.publishTotal || 0
  stats.takeCompleted = data.takeCompleted || 0
  // 发布者看发布侧取消数，接单者看接单后取消数（按信誉分流水统计）
  stats.cancelTotal = store.canPublish ? data.publishCancelled || 0 : data.takeCancelled || 0
  stats.income = Number(data.income || 0)
  stats.expense = Number(data.expense || 0)
  stats.pendingEvaluation = data.pendingEvaluation || 0
}

/** 信誉等级（规则 R-24：80-99 普通 / 100-119 良好 / 120+ 优秀） */
const creditLevel = computed(() => {
  const s = store.creditScore
  if (s >= 120) return { name: '优秀', type: 'success' }
  if (s >= 100) return { name: '良好', type: 'primary' }
  return { name: '普通', type: 'info' }
})

/** 保存资料：调用 /user/info（PUT） */
const handleSave = async () => {
  await formRef.value.validate()
  await request.put('/user/info', { phone: form.phone, dormArea: form.dormArea })
  ElMessage.success('资料更新成功')
  store.fetchInfo()
  loadInfo()
}

/** 头像上传（el-upload 自定义请求：走 axios 自动携带 JWT token） */
const handleAvatarUpload = async (options) => {
  const fd = new FormData()
  fd.append('file', options.file)
  try {
    const url = await request.post('/user/avatar', fd)
    store.setAvatar(url)
    ElMessage.success('头像上传成功')
  } catch (e) {
    // 格式/大小校验失败信息由拦截器统一弹出
  }
}

// ---------- 修改密码 ----------
const changePwdVisible = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 确认新密码二次校验：与新密码一致 */
const validateConfirm = (rule, value, callback) => {
  if (!value) callback(new Error('请再次输入新密码'))
  else if (value !== pwdForm.newPassword) callback(new Error('两次输入的新密码不一致'))
  else callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{6,20}$/, message: '新密码需 6-20 位，且同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

/** 打开修改密码弹窗并重置表单 */
const openChangePwd = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  changePwdVisible.value = true
}

/** 提交修改密码：调用 /user/password（PUT） */
const handleChangePwd = async () => {
  await pwdFormRef.value.validate()
  await request.put('/user/password', {
    oldPassword: pwdForm.oldPassword,
    newPassword: pwdForm.newPassword
  })
  ElMessage.success('密码修改成功，下次登录请使用新密码')
  changePwdVisible.value = false
}

// ---------- 设置 / 修改支付密码 ----------
const payPwdVisible = ref(false)
const payPwdFormRef = ref()
const payPwdForm = reactive({ oldPayPassword: '', newPayPassword: '', confirmPayPassword: '' })

/** 确认支付密码二次校验：与新支付密码一致 */
const validateConfirmPay = (rule, value, callback) => {
  if (!value) callback(new Error('请再次输入新支付密码'))
  else if (value !== payPwdForm.newPayPassword) callback(new Error('两次输入的支付密码不一致'))
  else callback()
}

const payPwdRules = {
  oldPayPassword: [
    {
      validator: (rule, value, callback) => {
        if (store.payPasswordSet && !value) callback(new Error('请输入原支付密码'))
        else if (value && !/^\d{6}$/.test(value)) callback(new Error('支付密码为 6 位数字'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  newPayPassword: [
    { required: true, message: '请输入新支付密码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '支付密码为 6 位数字', trigger: 'blur' }
  ],
  confirmPayPassword: [{ required: true, validator: validateConfirmPay, trigger: 'blur' }]
}

/** 打开设置支付密码弹窗并重置表单 */
const openPayPwd = () => {
  payPwdForm.oldPayPassword = ''
  payPwdForm.newPayPassword = ''
  payPwdForm.confirmPayPassword = ''
  payPwdVisible.value = true
}

/** 提交设置支付密码：调用 /user/pay-password（PUT），首次设置无需原密码 */
const handleSetPayPwd = async () => {
  await payPwdFormRef.value.validate()
  await request.put('/user/pay-password', {
    oldPayPassword: payPwdForm.oldPayPassword,
    newPayPassword: payPwdForm.newPayPassword
  })
  ElMessage.success('支付密码设置成功')
  payPwdVisible.value = false
  store.fetchInfo()
}

// ---------- 信誉分明细 ----------
const creditLogVisible = ref(false)
const creditLogs = ref([])
const creditPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const creditLoading = ref(false)

/** 变化类型 → 标签颜色映射 */
const creditTypeTag = (type) => {
  const map = {
    评价好评: 'success',
    评价差评: 'danger',
    接单者取消: 'warning',
    接单超时: 'info'
  }
  return map[type] || 'info'
}

/** 打开明细弹窗并加载第一页 */
const openCreditLogs = () => {
  creditPage.pageNum = 1
  creditLogVisible.value = true
  loadCreditLogs()
}

/** 加载信誉分明细：调用 /user/credit-logs（分页） */
const loadCreditLogs = async () => {
  creditLoading.value = true
  try {
    const data = await request.get('/user/credit-logs', {
      params: { pageNum: creditPage.pageNum, pageSize: creditPage.pageSize }
    })
    creditLogs.value = data.records
    creditPage.total = data.total
  } finally {
    creditLoading.value = false
  }
}

// ---------- 我的评价（我收到的评价记录列表）----------
const evaluationVisible = ref(false)
const evaluations = ref([])
const evalPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const evalLoading = ref(false)

/** 打开评价记录弹窗并加载第一页 */
const openEvaluations = () => {
  evalPage.pageNum = 1
  evaluationVisible.value = true
  loadEvaluations()
}

/** 加载我收到的评价：调用 /evaluation/page（分页，默认查当前登录用户） */
const loadEvaluations = async () => {
  evalLoading.value = true
  try {
    const data = await request.get('/evaluation/page', {
      params: { pageNum: evalPage.pageNum, pageSize: evalPage.pageSize }
    })
    evaluations.value = data.records
    evalPage.total = data.total
  } finally {
    evalLoading.value = false
  }
}

// ---------- 虚拟钱包（R-25 ~ R-28）----------
const wallet = reactive({ balance: 0, frozenBalance: 0 })

/** 加载钱包信息：调用 /wallet/info（可用余额 / 冻结余额） */
const loadWallet = async () => {
  try {
    const data = await request.get('/wallet/info')
    wallet.balance = Number(data.balance || 0)
    wallet.frozenBalance = Number(data.frozenBalance || 0)
  } catch (e) {
    // 钱包接口异常不阻塞个人中心其它功能，金额按 0 展示
  }
}

// ---------- 模拟充值 ----------
const rechargeVisible = ref(false)
const rechargeFormRef = ref()
const rechargeForm = reactive({ rechargeAmount: null, payPassword: '' })

/** 充值金额校验：0.5 ~ 100 元（与后端 DTO 一致）；支付密码 6 位数字 */
const rechargeRules = {
  rechargeAmount: [
    { required: true, message: '请输入充值金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value == null || value === '') {
          callback()
        } else if (value < 0.5 || value > 100) {
          callback(new Error('充值金额需在 0.5 ~ 100 元之间'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  payPassword: [
    { required: true, message: '请输入支付密码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '支付密码为 6 位数字', trigger: 'blur' }
  ]
}

/** 打开充值弹窗并重置表单 */
const openRecharge = () => {
  rechargeForm.rechargeAmount = null
  rechargeForm.payPassword = ''
  rechargeVisible.value = true
}

/** 提交模拟充值：调用 /wallet/recharge（仅模拟，无真实支付），成功后刷新钱包 */
const handleRecharge = async () => {
  await rechargeFormRef.value.validate()
  await request.post('/wallet/recharge', {
    rechargeAmount: rechargeForm.rechargeAmount,
    payPassword: rechargeForm.payPassword
  })
  ElMessage.success('充值成功（模拟充值，无真实支付）')
  rechargeVisible.value = false
  loadWallet()
}

// ---------- 提现（模拟，无真实支付通道）----------
const withdrawVisible = ref(false)
const withdrawFormRef = ref()
const withdrawForm = reactive({ withdrawAmount: null, payPassword: '' })

/** 提现金额校验：0.5 ~ 100 元，且不超过当前可用余额；支付密码 6 位数字 */
const withdrawRules = {
  withdrawAmount: [
    { required: true, message: '请输入提现金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value == null || value === '') {
          callback()
        } else if (value < 0.5 || value > 100) {
          callback(new Error('提现金额需在 0.5 ~ 100 元之间'))
        } else if (value > wallet.balance) {
          callback(new Error(`提现金额不能超过可用余额 ¥${wallet.balance.toFixed(2)}`))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  payPassword: [
    { required: true, message: '请输入支付密码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '支付密码为 6 位数字', trigger: 'blur' }
  ]
}

/** 打开提现弹窗并重置表单 */
const openWithdraw = () => {
  withdrawForm.withdrawAmount = null
  withdrawForm.payPassword = ''
  withdrawVisible.value = true
}

/** 提交提现：调用 /wallet/withdraw（仅模拟，无真实支付），成功后刷新钱包 */
const handleWithdraw = async () => {
  await withdrawFormRef.value.validate()
  await request.post('/wallet/withdraw', {
    withdrawAmount: withdrawForm.withdrawAmount,
    payPassword: withdrawForm.payPassword
  })
  ElMessage.success('提现成功（模拟提现，无真实支付）')
  withdrawVisible.value = false
  loadWallet()
}

// ---------- 资金流水分页 ----------
const walletLogVisible = ref(false)
const walletLogs = ref([])
const walletPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const walletLoading = ref(false)
/** 当前流水筛选类型（WalletLogType code：null=全部 / 4=结算收入 / 5=支出扣减） */
const walletLogFilter = ref(null)

/** 流水类型 → 标签颜色映射 */
const walletLogTypeTag = (type) => {
  const map = {
    充值: 'success',
    订单结算收入: 'success',
    订单解冻退回: 'info',
    订单报酬冻结: 'warning',
    订单支出扣减: 'danger',
    提现: 'danger'
  }
  return map[type] || 'info'
}

/** 打开资金流水弹窗并加载第一页（logType 为空=全部，4=收入明细，5=支出明细）
 * 注意：模板中必须显式传参调用（如 openWalletLogs()），否则 Vue 会把 click 事件对象当作 logType 传入 */
const openWalletLogs = (logType = null) => {
  // 防御：事件对象等非数字参数一律按"全部"处理
  if (typeof logType !== 'number' || !Number.isInteger(logType)) {
    logType = null
  }
  walletLogFilter.value = logType
  walletPage.pageNum = 1
  walletLogVisible.value = true
  loadWalletLogs()
}

/** 加载资金流水：调用 /wallet/log/page（分页，仅本人，可按类型过滤） */
const loadWalletLogs = async () => {
  walletLoading.value = true
  try {
    const data = await request.get('/wallet/log/page', {
      params: {
        pageNum: walletPage.pageNum,
        pageSize: walletPage.pageSize,
        logType: walletLogFilter.value ?? undefined
      }
    })
    walletLogs.value = data.records
    walletPage.total = data.total
  } finally {
    walletLoading.value = false
  }
}

onMounted(async () => {
  // 拉取用户信息与统计
  const user = await request.get('/user/info')
  Object.assign(form, { studentNo: user.studentNo, username: user.username, phone: user.phone, dormArea: user.dormArea })
  loadStats()
  loadWallet()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>个人中心</h2>
      <span class="sub">管理个人资料，查看订单与接单统计</span>
    </div>

    <el-row :gutter="16">
      <!-- 左侧：个人信息表单 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>个人信息</template>

          <!-- 头像上传：点击区域选择图片，走 axios 携带 token 上传 -->
          <div class="avatar-zone">
            <el-upload :show-file-list="false" accept="image/*" :http-request="handleAvatarUpload">
              <div class="avatar-box">
                <img v-if="store.avatar" :src="store.avatar" class="avatar-img" alt="头像" />
                <div v-else class="avatar-placeholder">{{ store.username.charAt(0) || '友' }}</div>
                <div class="avatar-edit-tip">点击更换头像</div>
              </div>
            </el-upload>
            <div class="avatar-hint">支持 jpg / png / gif / webp，不超过 5MB</div>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="学号" prop="studentNo">
              <el-input v-model="form.studentNo" disabled placeholder="学号不可修改" />
            </el-form-item>
            <el-form-item label="姓名" prop="username">
              <el-input v-model="form.username" disabled />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" clearable />
            </el-form-item>
            <el-form-item label="宿舍区域" prop="dormArea">
              <el-input v-model="form.dormArea" placeholder="如：一区 / 二区" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSave">保存修改</el-button>
              <el-button type="warning" plain @click="openChangePwd">修改密码</el-button>
              <el-button type="success" plain @click="openPayPwd">
                {{ store.payPasswordSet ? '修改支付密码' : '设置支付密码' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 设置 / 修改支付密码弹窗 -->
      <el-dialog v-model="payPwdVisible" :title="store.payPasswordSet ? '修改支付密码' : '设置支付密码'" width="440px" append-to-body>
        <el-form ref="payPwdFormRef" :model="payPwdForm" :rules="payPwdRules" label-width="100px">
          <el-form-item v-if="store.payPasswordSet" label="原支付密码" prop="oldPayPassword">
            <el-input v-model="payPwdForm.oldPayPassword" type="password" show-password maxlength="6" placeholder="请输入原支付密码" />
          </el-form-item>
          <el-form-item label="新支付密码" prop="newPayPassword">
            <el-input v-model="payPwdForm.newPayPassword" type="password" show-password maxlength="6" placeholder="6 位数字" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPayPassword">
            <el-input v-model="payPwdForm.confirmPayPassword" type="password" show-password maxlength="6" placeholder="再次输入新支付密码" />
          </el-form-item>
        </el-form>
        <div class="wallet-recharge-note">
          支付密码用于充值、提现、发布订单付钱等资金操作；为 6 位数字，与登录密码不同，BCrypt 加盐存储
        </div>
        <template #footer>
          <el-button @click="payPwdVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSetPayPwd">确认</el-button>
        </template>
      </el-dialog>

      <!-- 修改密码弹窗 -->
      <el-dialog v-model="changePwdVisible" title="修改密码" width="420px" append-to-body>
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位，含字母和数字" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="changePwdVisible = false">取消</el-button>
          <el-button type="primary" @click="handleChangePwd">确认修改</el-button>
        </template>
      </el-dialog>

      <!-- 右侧：统计卡片 -->
      <el-col :span="14">
        <!-- 虚拟钱包卡片（R-25 ~ R-28）：可用余额 / 冻结余额 / 模拟充值 / 资金流水 -->
        <el-card shadow="never" class="wallet-card">
          <template #header>
            <div class="wallet-header">
              <span>虚拟钱包</span>
              <el-link type="primary" :underline="false" @click="openWalletLogs()">资金流水</el-link>
            </div>
          </template>
          <div class="wallet-body">
            <div class="wallet-item">
              <div class="wallet-num">{{ wallet.balance.toFixed(2) }}</div>
              <div class="wallet-label">可用余额（¥）</div>
            </div>
            <div class="wallet-item">
              <div class="wallet-num frozen">{{ wallet.frozenBalance.toFixed(2) }}</div>
              <div class="wallet-label">
                冻结余额（¥）
                <el-tooltip content="发布订单时报酬会转入冻结余额（R-26），订单完成结算给接单者，取消则解冻退回（R-27/R-28）" placement="top">
                  <el-icon class="wallet-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
            </div>
            <div class="wallet-item wallet-action">
              <el-button type="primary" @click="openRecharge">模拟充值</el-button>
              <el-button type="warning" plain @click="openWithdraw">提现</el-button>
            </div>
          </div>
        </el-card>

        <el-row :gutter="16">
          <!-- 发布者专属：发布订单总数、累计支出 -->
          <el-col v-if="store.canPublish" :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num">{{ stats.publishTotal }}</div>
              <div class="stat-label">发布订单总数</div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num">{{ stats.cancelTotal }}</div>
              <div class="stat-label">取消订单数</div>
            </el-card>
          </el-col>
          <!-- 接单者专属：接单完成数、累计收入 -->
          <el-col v-if="!store.canPublish" :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num">{{ stats.takeCompleted }}</div>
              <div class="stat-label">接单完成数</div>
            </el-card>
          </el-col>
          <el-col v-if="store.canPublish" :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num income">¥{{ stats.expense.toFixed(1) }}</div>
              <div class="stat-label">
                累计支出（发布已完单报酬）
                <el-link type="primary" :underline="false" class="credit-log-link" @click="openWalletLogs(5)">查看明细</el-link>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num">
                {{ stats.pendingEvaluation }}
                <el-tag v-if="stats.pendingEvaluation > 0" type="danger" size="small" class="level-tag">待处理</el-tag>
              </div>
              <div class="stat-label">
                待评价订单
                <el-link v-if="stats.pendingEvaluation > 0" type="primary" :underline="false" class="credit-log-link" @click="router.push('/my-order')">去评价</el-link>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num">
                {{ store.creditScore }}
                <el-tag :type="creditLevel.type" size="small" class="level-tag">{{ creditLevel.name }}</el-tag>
              </div>
              <div class="stat-label">
                信誉分（等级）
                <el-link type="primary" :underline="false" class="credit-log-link" @click="openCreditLogs">查看明细</el-link>
                <el-link type="primary" :underline="false" class="credit-log-link" @click="openEvaluations()">我的评价</el-link>
              </div>
            </el-card>
          </el-col>
          <el-col v-if="!store.canPublish" :span="12">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-num income">¥{{ stats.income.toFixed(1) }}</div>
              <div class="stat-label">
                累计收入（已完单报酬）
                <el-link type="primary" :underline="false" class="credit-log-link" @click="openWalletLogs(4)">查看明细</el-link>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="rule-card">
          <template #header>信誉规则提示（R-21 ~ R-24）</template>
          <ul class="rule-list">
            <li>好评（4-5 星）：被评价方信誉分 +1；差评（1-3 星）：-2</li>
            <li>违约取消（接单后取消 / 超时未取件）：扣信誉分 5 分</li>
            <li>信誉分低于 80：禁止接单；同时进行中订单数受等级上限约束</li>
            <li>等级区间：普通 80-99 / 良好 100-119 / 优秀 120+</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <!-- 信誉分明细弹窗 -->
    <el-dialog v-model="creditLogVisible" title="信誉分明细" width="720px" append-to-body>
      <el-table v-loading="creditLoading" :data="creditLogs" stripe>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="creditTypeTag(row.changeType)" size="small">{{ row.changeType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变化" width="90">
          <template #default="{ row }">
            <span :class="row.changeAmount > 0 ? 'credit-plus' : 'credit-minus'">
              {{ row.changeAmount > 0 ? '+' : '' }}{{ row.changeAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变化后" width="90">
          <template #default="{ row }">{{ row.afterScore }} 分</template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">{{ row.remark }}</template>
        </el-table-column>
      </el-table>
      <div class="credit-pager">
        <el-pagination
          v-model:current-page="creditPage.pageNum"
          v-model:page-size="creditPage.pageSize"
          :total="creditPage.total"
          layout="total, prev, pager, next"
          @current-change="loadCreditLogs"
        />
      </div>
    </el-dialog>

    <!-- 模拟充值弹窗（仅模拟，无真实支付） -->
    <el-dialog v-model="rechargeVisible" title="模拟充值" width="420px" append-to-body>
      <el-form ref="rechargeFormRef" :model="rechargeForm" :rules="rechargeRules" label-width="90px">
        <el-form-item label="充值金额" prop="rechargeAmount">
          <el-input-number v-model="rechargeForm.rechargeAmount" :min="0.5" :max="100" :precision="2" :step="10" />
          <span class="wallet-tip">元（0.5 ~ 100 元）</span>
        </el-form-item>
        <el-form-item label="支付密码" prop="payPassword">
          <el-input v-model="rechargeForm.payPassword" type="password" show-password maxlength="6" placeholder="请输入 6 位支付密码" />
        </el-form-item>
        <div class="wallet-recharge-note">仅模拟充值，不接入真实支付；充值成功后资金实时到账并写入流水</div>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 提现弹窗（模拟，无真实支付通道；补齐资金闭环，接单者报酬可取出） -->
    <el-dialog v-model="withdrawVisible" title="提现" width="420px" append-to-body>
      <el-form ref="withdrawFormRef" :model="withdrawForm" :rules="withdrawRules" label-width="90px">
        <el-form-item label="提现金额" prop="withdrawAmount">
          <el-input-number v-model="withdrawForm.withdrawAmount" :min="0.5" :max="Math.min(100, wallet.balance)" :precision="2" :step="10" />
          <span class="wallet-tip">元（0.5 ~ 100 元，且不超过可用余额）</span>
        </el-form-item>
        <el-form-item label="支付密码" prop="payPassword">
          <el-input v-model="withdrawForm.payPassword" type="password" show-password maxlength="6" placeholder="请输入 6 位支付密码" />
        </el-form-item>
        <div class="wallet-recharge-note">当前可用余额 ¥{{ wallet.balance.toFixed(2) }}；仅模拟提现，不接入真实支付通道</div>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="primary" @click="handleWithdraw">确认提现</el-button>
      </template>
    </el-dialog>

    <!-- 资金流水分页弹窗（R-25 ~ R-28，复用信誉分明细布局；可按收入/支出类型过滤）
         注意：模板中 ref 自动解包，不可写 .value（否则初始 null 时渲染抛错） -->
    <el-dialog
      v-model="walletLogVisible"
      :title="walletLogFilter === 4 ? '收入明细（订单结算收入）' : walletLogFilter === 5 ? '支出明细（订单支出扣减）' : '资金流水'"
      width="820px"
      append-to-body
    >
      <el-table v-loading="walletLoading" :data="walletLogs" stripe>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="walletLogTypeTag(row.logTypeText)" size="small">{{ row.logTypeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <span :class="row.amount > 0 ? 'credit-plus' : 'credit-minus'">
              {{ row.amount > 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="余额变动" min-width="180">
          <template #default="{ row }">
            可用 {{ row.beforeBalance.toFixed(2) }} → {{ row.afterBalance.toFixed(2) }}
            <span v-if="row.beforeFrozen !== row.afterFrozen" class="wallet-frozen-change">
              ；冻结 {{ row.beforeFrozen.toFixed(2) }} → {{ row.afterFrozen.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="关联订单" width="90">
          <template #default="{ row }">{{ row.relatedOrderId ? '#' + row.relatedOrderId : '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">{{ row.remark }}</template>
        </el-table-column>
      </el-table>
      <div class="credit-pager">
        <el-pagination
          v-model:current-page="walletPage.pageNum"
          v-model:page-size="walletPage.pageSize"
          :total="walletPage.total"
          layout="total, prev, pager, next"
          @current-change="loadWalletLogs"
        />
      </div>
    </el-dialog>

    <!-- 我的评价弹窗：我收到的评价记录（星级 + 评价人 + 关联订单 + 内容，分页） -->
    <el-dialog v-model="evaluationVisible" title="我的评价（收到的评价记录）" width="820px" append-to-body>
      <el-table v-loading="evalLoading" :data="evaluations" stripe>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="星级" width="150">
          <template #default="{ row }">
            <el-rate :model-value="row.starLevel" disabled />
          </template>
        </el-table-column>
        <el-table-column label="评价人" width="120">
          <template #default="{ row }">{{ row.evaluatorName }}</template>
        </el-table-column>
        <el-table-column label="关联订单" width="170">
          <template #default="{ row }">
            <span>#{{ row.orderId }}</span>
            <span v-if="row.expressCompany" class="eval-order-exp">{{ row.expressCompany }} {{ row.pickupCode || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="180">
          <template #default="{ row }">{{ row.content || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="credit-pager">
        <el-pagination
          v-model:current-page="evalPage.pageNum"
          v-model:page-size="evalPage.pageSize"
          :total="evalPage.total"
          layout="total, prev, pager, next"
          @current-change="loadEvaluations"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 头像上传区 */
.avatar-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}

.avatar-box {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid #e4e7ed;
  transition: border-color 0.2s;
}

.avatar-box:hover {
  border-color: #1e3c72;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72, #2a5298);
  color: #fff;
  font-size: 34px;
  font-weight: 700;
}

.avatar-edit-tip {
  position: absolute;
  inset: auto 0 0 0;
  padding: 3px 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  text-align: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-box:hover .avatar-edit-tip {
  opacity: 1;
}

.avatar-hint {
  font-size: 12px;
  color: #c0c4cc;
}

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

.stat-card {
  text-align: center;
  margin-bottom: 16px;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #1e3c72;
}

.stat-num.income {
  color: #ff7e3d;
}

.level-tag {
  margin-left: 6px;
  vertical-align: middle;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.rule-card {
  font-size: 13px;
}

.rule-list {
  padding-left: 18px;
  line-height: 2;
  color: #606266;
}

.credit-log-link {
  margin-left: 8px;
  font-size: 12px;
}

.credit-plus {
  color: #67c23a;
  font-weight: 700;
}

.credit-minus {
  color: #f56c6c;
  font-weight: 700;
}

.credit-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 虚拟钱包卡片（R-25 ~ R-28） */
.wallet-card {
  margin-bottom: 16px;
}

.wallet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wallet-body {
  display: flex;
  align-items: center;
  gap: 32px;
}

.wallet-item {
  text-align: center;
}

.wallet-action {
  margin-left: auto;
}

.wallet-num {
  font-size: 26px;
  font-weight: 700;
  color: #1e3c72;
}

.wallet-num.frozen {
  color: #ff7e3d;
}

.wallet-label {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.wallet-help {
  vertical-align: -2px;
  margin-left: 2px;
  color: #c0c4cc;
}

.wallet-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.wallet-recharge-note {
  padding: 8px 12px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

.wallet-frozen-change {
  color: #909399;
}

.eval-order-exp {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
