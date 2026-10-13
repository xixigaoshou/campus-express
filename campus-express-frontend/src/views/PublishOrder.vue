<script setup>
/**
 * 发布代取需求页面
 * - el-form 完整表单校验（必填 + 报酬范围）
 * - 支持一键填入个人资料：宿舍区域 → 送达地点，手机号 → 备注联系方式
 * - 提交 /order/publish，成功后跳转「我的订单」
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const formRef = ref()

/** 编辑模式：从「我的订单」待接单卡片进入，预填订单信息后提交 /order/update-wait */
const editOrderId = ref(route.query.editOrderId ? Number(route.query.editOrderId) : null)
const isEdit = computed(() => editOrderId.value != null)
/** 编辑前的原报酬，用于判断是否因提高报酬而需要支付密码 */
const originalReward = ref(0)
/** 支付密码是否必填：发布必填；编辑时仅提高报酬（追加冻结）才必填 */
const payPwdRequired = computed(() => !isEdit.value || Number(form.rewardAmount || 0) > originalReward.value)

const form = reactive({
  pickupCode: '',
  expressCompany: '',
  pickupLocation: '',
  deliverLocation: '',
  expectTime: null,
  remark: '',
  rewardAmount: null,
  payPassword: ''
})

/** 禁用昨天及更早的日期，防止将取件时间选到过去（今天已过时段由表单校验兜底） */
const disabledDate = (time) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return time.getTime() < today.getTime()
}

// ---------- 虚拟钱包余额（R-25：余额不足禁止发布）----------
const walletBalance = ref(0)
const walletLoading = ref(true)
/** 发布提交中（按钮 loading，防重复提交） */
const publishing = ref(false)

/** 加载钱包可用余额：调用 /wallet/info，供发布前余额校验 */
const loadWallet = async () => {
  try {
    const data = await request.get('/wallet/info')
    walletBalance.value = Number(data.balance || 0)
  } catch (e) {
    walletBalance.value = 0
  } finally {
    walletLoading.value = false
  }
}

/** 余额是否足以支付报酬：发布需覆盖全额；编辑仅需覆盖「提高的差额」 */
const balanceEnough = computed(() => {
  const reward = Number(form.rewardAmount || 0)
  if (reward <= 0) return false
  if (isEdit.value) {
    const delta = reward - originalReward.value
    return delta <= 0 || delta <= walletBalance.value
  }
  return reward <= walletBalance.value
})

/** 一键再次发布：从历史订单模板拉取包裹信息预填表单（期望取件时间不预填，需重新选择） */
const loadTemplate = async (orderId) => {
  try {
    const t = await request.get(`/order/${orderId}/reuse-template`)
    form.pickupCode = t.pickupCode || ''
    form.expressCompany = t.expressCompany || ''
    form.pickupLocation = t.pickupLocation || ''
    form.deliverLocation = t.deliverLocation || ''
    form.remark = t.remark || ''
    form.rewardAmount = t.rewardAmount ?? null
    ElMessage.success('已填入上次订单信息，请确认后发布')
  } catch (e) {
    // 模板加载失败不阻塞发布页，用户可手动填写
  }
}

/** 编辑模式：拉取待接单订单详情并预填表单（仅待接单可改） */
const loadOrderForEdit = async (orderId) => {
  try {
    const detail = await request.get(`/order/${orderId}`)
    const o = detail.order || {}
    if (o.status !== '待接单') {
      ElMessage.warning('该订单已被接单，进入业务流转后不可修改')
      router.replace('/my-order')
      return
    }
    form.pickupCode = o.pickupCode || ''
    form.expressCompany = o.expressCompany || ''
    form.pickupLocation = o.pickupLocation || ''
    form.deliverLocation = o.deliverLocation || ''
    form.expectTime = o.expectTime || null
    form.remark = o.remark || ''
    form.rewardAmount = o.rewardAmount ?? null
    originalReward.value = Number(o.rewardAmount ?? 0)
  } catch (e) {
    router.replace('/my-order')
  }
}

/** 页面加载时同步最新个人资料（宿舍区域 / 手机号）与钱包余额；编辑/模板模式则预填表单 */
onMounted(() => {
  store.fetchInfo()
  loadWallet()
  if (editOrderId.value) {
    loadOrderForEdit(editOrderId.value)
    return
  }
  const templateOrderId = route.query.templateOrderId
  if (templateOrderId) {
    loadTemplate(templateOrderId)
  }
})

/** 一键填入个人资料：宿舍区域 → 送达地点；手机号 → 备注联系电话 */
const fillProfile = () => {
  if (!store.dormArea && !store.phone) {
    ElMessage.warning('个人中心尚未保存宿舍区域和手机号，请先到个人中心完善资料')
    return
  }
  if (store.dormArea) {
    form.deliverLocation = store.dormArea
  }
  if (store.phone) {
    const phoneText = `联系电话：${store.phone}`
    form.remark = form.remark.includes(phoneText) ? form.remark : form.remark ? `${form.remark}；${phoneText}` : phoneText
  }
  ElMessage.success('已填入个人资料，可继续修改')
}

const rules = {
  pickupCode: [
    { required: true, message: '请输入取件码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback()
        } else if (!/^[0-9-]{4,15}$/.test(value)) {
          callback(new Error('取件码为 4-15 位数字或横线'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  pickupLocation: [{ required: true, message: '请输入取件地点', trigger: 'blur' }],
  deliverLocation: [{ required: true, message: '请输入送达地点', trigger: 'blur' }],
  expectTime: [
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback(new Error('请选择期望取件时间'))
        } else if (new Date(value).getTime() <= Date.now()) {
          callback(new Error('期望取件时间必须晚于当前时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  rewardAmount: [
    { required: true, message: '请输入报酬金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value == null || value === '') {
          callback()
        } else if (value < 2 || value > 50) {
          callback(new Error('报酬金额需在 2 ~ 50 元之间（R-17）'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  payPassword: [
    {
      validator: (rule, value, callback) => {
        if (!payPwdRequired.value) {
          callback()
        } else if (!value) {
          callback(new Error('请输入支付密码'))
        } else if (!/^\d{6}$/.test(value)) {
          callback(new Error('支付密码为 6 位数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 提交：发布调 /order/publish；编辑调 /order/update-wait（R-25：前端余额预校验 + 后端冻结二次校验） */
const handleSubmit = async () => {
  if (publishing.value) return
  await formRef.value.validate()
  if (!balanceEnough.value) {
    ElMessage.warning(
      isEdit.value
        ? '钱包可用余额不足以补足提高的报酬差额，请先到个人中心充值'
        : '钱包可用余额不足，无法支付订单报酬，请先到个人中心充值'
    )
    return
  }
  publishing.value = true
  try {
    if (isEdit.value) {
      await request.put('/order/update-wait', { orderId: editOrderId.value, ...form })
      ElMessage.success('订单修改成功')
    } else {
      await request.post('/order/publish', form)
      ElMessage.success('发布成功，订单已进入需求大厅')
    }
    router.push('/my-order')
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>{{ isEdit ? '修改代取需求' : '发布代取需求' }}</h2>
      <span class="sub">{{ isEdit ? '仅待接单订单可修改，修改后重新进入需求大厅' : '填写快递信息与报酬，订单将进入需求大厅等待接单' }}</span>
    </div>

    <el-card shadow="never" class="publish-card">
      <!-- 一键填入个人资料入口：复用个人中心保存的宿舍区域与手机号 -->
      <el-alert type="info" :closable="false" show-icon class="profile-tip">
        <template #title>
          想直接用个人资料？点击
          <el-button link type="primary" @click="fillProfile">填入我的个人资料</el-button>
          —— 宿舍区域自动填入送达地点，手机号自动写入备注（便于接单者联系）
        </template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="取件码" prop="pickupCode">
          <el-input v-model="form.pickupCode" placeholder="必填，如 8-2-3021" clearable />
          <span class="tip">注：取件码是取件凭据，为 4-15 位数字或横线；同一取件地点下，相同取件码不可同时存在多个进行中订单；已完成的订单取件码可再次使用</span>
        </el-form-item>

        <el-form-item label="快递公司" prop="expressCompany">
          <el-select v-model="form.expressCompany" placeholder="选择快递公司" clearable>
            <el-option v-for="c in ['顺丰', '中通', '圆通', '申通', '韵达', '京东', '邮政', '极兔']"
                       :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <el-form-item label="取件地点" prop="pickupLocation">
          <el-input v-model="form.pickupLocation" placeholder="如：菜鸟驿站 / 校门口快递点" clearable />
        </el-form-item>

        <el-form-item label="送达地点" prop="deliverLocation">
          <el-input v-model="form.deliverLocation" placeholder="如：学生宿舍3栋 / 教学楼" clearable />
        </el-form-item>

        <el-form-item label="期望取件时间" prop="expectTime">
          <el-date-picker
            v-model="form.expectTime"
            type="datetime"
            placeholder="选择期望取件时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="报酬金额" prop="rewardAmount">
          <el-input-number v-model="form.rewardAmount" :min="2" :max="50" :precision="1" :step="0.5" />
          <span class="tip">元（2 ~ 50 元）</span>
        </el-form-item>

        <!-- 钱包余额提示（R-25）：余额不足时警告并禁用提交 -->
        <el-form-item label="钱包余额">
          <div class="wallet-balance">
            <span>当前可用余额：<b class="wallet-balance-num">¥{{ walletBalance.toFixed(2) }}</b></span>
            <el-alert v-if="form.rewardAmount != null && !balanceEnough" type="warning" :closable="false" show-icon class="wallet-alert"
                      title="钱包可用余额不足，无法支付订单报酬，请先到个人中心充值" />
            <span v-else class="wallet-hint">发布成功后报酬将转入冻结余额（R-26），订单完成结算给接单者</span>
          </div>
        </el-form-item>

        <el-form-item v-if="payPwdRequired" label="支付密码" prop="payPassword">
          <el-input v-model="form.payPassword" type="password" show-password maxlength="6" placeholder="请输入 6 位支付密码" style="width: 240px" />
          <span class="tip">{{ isEdit ? '提高报酬将追加冻结，需验证支付密码' : '发布将冻结报酬，需验证支付密码' }}</span>
        </el-form-item>
        <el-alert
          v-if="payPwdRequired && !store.payPasswordSet"
          type="warning"
          :closable="false"
          show-icon
          class="pay-pwd-alert"
          title="尚未设置支付密码，请先到「个人中心 → 设置支付密码」完成设置后再提交"
        />

        <el-form-item label="包裹备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，如：请轻拿轻放" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="publishing" :disabled="!balanceEnough || walletLoading || (payPwdRequired && !store.payPasswordSet)" @click="handleSubmit">{{ isEdit ? '保存修改' : '发布需求' }}</el-button>
          <el-button size="large" @click="router.push('/my-order')">取消</el-button>
          <span v-if="!balanceEnough" class="submit-hint">{{ isEdit ? '余额不足以补足报酬差额' : '余额不足，暂不可发布' }}</span>
        </el-form-item>
      </el-form>
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

.publish-card {
  max-width: 720px;
}

.profile-tip {
  margin-bottom: 18px;
}

.tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

/* 钱包余额提示（R-25） */
.wallet-balance {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.wallet-balance-num {
  color: #1e3c72;
}

.wallet-alert {
  padding: 6px 12px;
}

.wallet-hint {
  font-size: 12px;
  color: #909399;
}

.submit-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #f56c6c;
}

.pay-pwd-alert {
  margin: 0 0 18px 110px;
}
</style>
