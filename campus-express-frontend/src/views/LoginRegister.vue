<script setup>
/**
 * 登录注册页
 * el-tabs 切换「登录 / 注册」两个表单，均带 Element Plus 表单校验
 * - 登录成功：保存 token 与用户信息到 Pinia + localStorage，跳转需求大厅
 * - 注册成功：提示后自动切换至登录 tab
 */
import { reactive, ref, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import loginBg from '@/assets/login-bg.jpg'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 背景图加载失败时置位：隐藏图片，回退到 CSS 渐变底色，避免出现加载失败提示文字
const bgFailed = ref(false)

// 当前激活的 tab：login / register
const activeTab = ref('login')

/** 登录/注册提交中（按钮 loading，防重复点击） */
const submitting = ref(false)

// ---------- 登录表单 ----------
const loginFormRef = ref()
const loginForm = reactive({ loginName: '', password: '' })

const loginRules = {
  loginName: [{ required: true, message: '请输入学号或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 登录提交：校验通过后调用 /user/login */
const handleLogin = async () => {
  if (submitting.value) return
  await loginFormRef.value.validate()
  submitting.value = true
  try {
    const data = await request.post('/user/login', loginForm)
    store.setLogin(data)
    ElMessage.success('登录成功，欢迎回来')
    // 被路由守卫拦截时会带上 redirect，登录后回跳原页面；否则按角色进入默认首页
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      router.push(redirect)
    } else {
      router.push('/hall')
    }
  } finally {
    submitting.value = false
  }
}

// ---------- 注册表单 ----------
const registerFormRef = ref()
const registerForm = reactive({
  studentNo: '',
  username: '',
  phone: '',
  smsCode: '',
  password: '',
  role: 1, // 默认需求方；同账号可参与两侧业务
  dormArea: ''
})

const registerRules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{4,20}$/, message: '学号为 4-20 位字母数字', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  smsCode: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{6,20}$/, message: '密码需 6-20 位，且同时包含字母和数字', trigger: 'blur' }
  ]
}

// ---------- 注册短信验证码（校验手机号真实性） ----------
const sending = ref(false)
const countdown = ref(0) // 重发倒计时（秒）
const demoCode = ref('') // 演示环境回传的验证码，便于本地测试
let countdownTimer = null

/** 获取验证码：先校验手机号格式，成功后进入 60 秒重发倒计时 */
const handleSendCode = async () => {
  if (sending.value || countdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(registerForm.phone || '')) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const code = await request.post('/user/register/code', { phone: registerForm.phone })
    demoCode.value = code || ''
    if (code) {
      ElMessage.success(`验证码已发送（演示环境验证码：${code}）`)
    } else {
      ElMessage.success('验证码已发送，请查收短信')
    }
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } finally {
    sending.value = false
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (resetTimer) clearInterval(resetTimer)
})

/** 注册提交：成功后切换到登录 tab */
const handleRegister = async () => {
  if (submitting.value) return
  await registerFormRef.value.validate()
  submitting.value = true
  try {
    await request.post('/user/register', registerForm)
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.loginName = registerForm.studentNo
  } finally {
    submitting.value = false
  }
}

// ---------- 找回密码（两步验证：校验学号+手机号发码 → 校验验证码重置） ----------
const resetVisible = ref(false)
const resetStep = ref(1) // 1=校验学号+手机号并发送验证码 2=输入验证码并重置密码

const verifyFormRef = ref()
const verifyForm = reactive({ studentNo: '', phone: '' })

const resetFormRef = ref()
const resetForm = reactive({ smsCode: '', newPassword: '', confirmPassword: '' })

const verifyRules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{4,20}$/, message: '学号为 4-20 位字母数字', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入注册手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

/** 确认新密码二次校验：与新密码一致 */
const validateResetConfirm = (rule, value, callback) => {
  if (!value) callback(new Error('请再次输入新密码'))
  else if (value !== resetForm.newPassword) callback(new Error('两次输入的新密码不一致'))
  else callback()
}

const resetRules = {
  smsCode: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{6,20}$/, message: '新密码需 6-20 位，且同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateResetConfirm, trigger: 'blur' }]
}

// 找回密码短信验证码：发送状态、重发倒计时、演示环境验证码
const resetSending = ref(false)
const resetCountdown = ref(0)
const resetDemoCode = ref('')
let resetTimer = null

/** 启动 60 秒重发倒计时 */
const startResetCountdown = () => {
  if (resetTimer) clearInterval(resetTimer)
  resetCountdown.value = 60
  resetTimer = setInterval(() => {
    resetCountdown.value -= 1
    if (resetCountdown.value <= 0) {
      clearInterval(resetTimer)
      resetTimer = null
    }
  }, 1000)
}

/** 打开找回密码弹窗：回到第一步并清空表单与倒计时 */
const openReset = () => {
  resetStep.value = 1
  resetDemoCode.value = ''
  resetCountdown.value = 0
  if (resetTimer) {
    clearInterval(resetTimer)
    resetTimer = null
  }
  Object.assign(verifyForm, { studentNo: '', phone: '' })
  Object.assign(resetForm, { smsCode: '', newPassword: '', confirmPassword: '' })
  resetVisible.value = true
}

/** 发送找回密码验证码：校验学号 + 注册手机号匹配后向该手机号下发验证码 */
const sendResetCode = async () => {
  const code = await request.post('/user/password/reset/code', {
    studentNo: verifyForm.studentNo,
    phone: verifyForm.phone
  })
  resetDemoCode.value = code || ''
  if (code) {
    ElMessage.success(`验证码已发送（演示环境验证码：${code}）`)
  } else {
    ElMessage.success('验证码已发送，请查收短信')
  }
  startResetCountdown()
}

/** 第一步：校验学号 + 注册手机号并发送短信验证码，成功后进入第二步 */
const handleSendResetCode = async () => {
  if (resetSending.value) return
  await verifyFormRef.value.validate()
  resetSending.value = true
  try {
    await sendResetCode()
    resetStep.value = 2
  } finally {
    resetSending.value = false
  }
}

/** 第二步：在弹窗内重新获取验证码（受 60 秒倒计时与后端发送间隔限制） */
const handleResendResetCode = async () => {
  if (resetSending.value || resetCountdown.value > 0) return
  resetSending.value = true
  try {
    await sendResetCode()
  } finally {
    resetSending.value = false
  }
}

/** 第二步：校验短信验证码后重置密码（验证码一次性、带时效） */
const handleReset = async () => {
  await resetFormRef.value.validate()
  await request.post('/user/password/reset', {
    studentNo: verifyForm.studentNo,
    phone: verifyForm.phone,
    smsCode: resetForm.smsCode,
    newPassword: resetForm.newPassword
  })
  ElMessage.success('密码重置成功，请使用新密码登录')
  resetVisible.value = false
  loginForm.loginName = verifyForm.studentNo
}
</script>

<template>
  <div class="login-page">
    <!-- 动态背景层：校园快递主题图 + 渐变遮罩 + 流动光晕 + 漂浮包裹 -->
    <div class="bg-layer" :class="{ 'bg-fallback': bgFailed }">
      <img
        v-show="!bgFailed"
        class="bg-img"
        :src="loginBg"
        alt="校园快递"
        @error="bgFailed = true"
      />
      <div class="bg-mask"></div>
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>

      <!-- 漂浮装饰：快递箱与纸飞机 -->
      <svg class="float float-1" viewBox="0 0 48 48">
        <rect x="9" y="15" width="30" height="24" rx="2" fill="#ffb25e" />
        <path d="M9 21h30" stroke="#e68a2e" stroke-width="2" />
        <rect x="20" y="9" width="8" height="12" rx="1" fill="#ffd9a0" />
      </svg>
      <svg class="float float-2" viewBox="0 0 48 48">
        <rect x="9" y="15" width="30" height="24" rx="2" fill="#8fb8ff" />
        <path d="M9 21h30" stroke="#5b8de8" stroke-width="2" />
        <rect x="20" y="9" width="8" height="12" rx="1" fill="#c4d8ff" />
      </svg>
      <svg class="float float-3" viewBox="0 0 48 48">
        <path d="M6 36 L42 12 L28 40 L24 28 Z" fill="#ffffff" opacity="0.9" />
        <path d="M24 28 L42 12 L28 40" fill="#ffe3c2" />
      </svg>
    </div>

    <!-- 内容区：左侧品牌信息 + 右侧表单卡片 -->
    <div class="login-wrap">
      <div class="brand-panel">
        <div class="brand-logo">
          <svg viewBox="0 0 48 48">
            <rect x="9" y="15" width="30" height="24" rx="3" fill="#ffb25e" />
            <path d="M9 22h30" stroke="#e68a2e" stroke-width="2.5" />
            <rect x="20" y="8" width="8" height="13" rx="1.5" fill="#ffd9a0" />
            <circle cx="24" cy="30" r="3" fill="#fff" opacity="0.85" />
          </svg>
        </div>
        <h1>校园快递代取</h1>
        <p class="slogan">让每一份包裹，都有同学帮你送达</p>
        <ul class="features">
          <li>
            <span class="dot"></span>
            发布需求 · 顺路取件，轻松省时
          </li>
          <li>
            <span class="dot"></span>
            实时抢单 · 接单同学即刻响应
          </li>
          <li>
            <span class="dot"></span>
            信誉保障 · 分分有据，互帮互助
          </li>
        </ul>
      </div>

      <div class="login-card">
        <el-tabs v-model="activeTab" stretch>
          <!-- 登录 Tab -->
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              size="large"
            >
              <el-form-item label="学号 / 手机号" prop="loginName">
                <el-input v-model="loginForm.loginName" placeholder="请输入学号或手机号" clearable />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                  @keyup.enter="handleLogin"
                />
                <div class="form-tip">
                  <el-link type="primary" :underline="false" @click="openReset">忘记密码？</el-link>
                </div>
              </el-form-item>
              <el-button type="primary" size="large" :loading="submitting" class="submit-btn" @click="handleLogin">
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <!-- 注册 Tab -->
          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              size="large"
            >
              <el-form-item label="学号" prop="studentNo">
                <el-input v-model="registerForm.studentNo" placeholder="请输入学号（唯一）" clearable />
              </el-form-item>
              <el-form-item label="姓名" prop="username">
                <el-input v-model="registerForm.username" placeholder="请输入姓名" clearable />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="registerForm.phone" placeholder="请输入手机号" clearable />
              </el-form-item>
              <el-form-item label="短信验证码" prop="smsCode">
                <div class="sms-row">
                  <el-input
                    v-model="registerForm.smsCode"
                    placeholder="请输入 6 位验证码"
                    maxlength="6"
                    clearable
                  />
                  <el-button
                    :disabled="countdown > 0"
                    :loading="sending"
                    class="sms-btn"
                    @click="handleSendCode"
                  >
                    {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
                  </el-button>
                </div>
                <div v-if="demoCode" class="sms-demo-tip">
                  演示环境验证码：<b>{{ demoCode }}</b>（未接入真实短信通道）
                </div>
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="6-20 位，含字母和数字"
                  show-password
                />
              </el-form-item>
              <el-form-item label="我的身份">
                <el-radio-group v-model="registerForm.role">
                  <el-radio :value="1">需求方（发布代取）</el-radio>
                  <el-radio :value="2">接单方（代取人员）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="宿舍区域">
                <el-input v-model="registerForm.dormArea" placeholder="如：一区 / 二区（选填）" clearable />
              </el-form-item>
              <el-button type="primary" size="large" :loading="submitting" class="submit-btn" @click="handleRegister">
                注册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <!-- 找回密码弹窗（免登录，两步验证：短信验证码校验手机号归属） -->
        <el-dialog v-model="resetVisible" title="找回密码" width="440px" append-to-body>
          <el-steps :active="resetStep" simple style="margin-bottom: 18px">
            <el-step title="验证身份" />
            <el-step title="重置密码" />
          </el-steps>

          <!-- 第一步：学号 + 手机号校验后发送短信验证码 -->
          <el-form
            v-if="resetStep === 1"
            ref="verifyFormRef"
            :model="verifyForm"
            :rules="verifyRules"
            label-width="90px"
          >
            <p class="reset-hint">请输入学号与注册手机号，验证码将发送至注册手机号，连续输错将临时限制尝试</p>
            <el-form-item label="学号" prop="studentNo">
              <el-input v-model="verifyForm.studentNo" placeholder="请输入学号" clearable />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="verifyForm.phone" placeholder="请输入注册手机号" clearable />
            </el-form-item>
          </el-form>

          <!-- 第二步：输入短信验证码并设置新密码 -->
          <el-form
            v-else
            ref="resetFormRef"
            :model="resetForm"
            :rules="resetRules"
            label-width="90px"
          >
            <p class="reset-hint">验证码已发送至 {{ verifyForm.phone }}，10 分钟内有效，请输入验证码并设置新密码</p>
            <el-form-item label="验证码" prop="smsCode">
              <div class="sms-row">
                <el-input
                  v-model="resetForm.smsCode"
                  placeholder="请输入 6 位验证码"
                  maxlength="6"
                  clearable
                />
                <el-button
                  :disabled="resetCountdown > 0"
                  :loading="resetSending"
                  class="sms-btn"
                  @click="handleResendResetCode"
                >
                  {{ resetCountdown > 0 ? resetCountdown + 's 后重发' : '重新获取' }}
                </el-button>
              </div>
              <div v-if="resetDemoCode" class="sms-demo-tip">
                演示环境验证码：<b>{{ resetDemoCode }}</b>（未接入真实短信通道）
              </div>
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="6-20 位，含字母和数字" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
          </el-form>

          <template #footer>
            <el-button @click="resetVisible = false">取消</el-button>
            <el-button v-if="resetStep === 2" @click="resetStep = 1">上一步</el-button>
            <el-button
              v-if="resetStep === 1"
              type="primary"
              :loading="resetSending"
              @click="handleSendResetCode"
            >
              获取验证码
            </el-button>
            <el-button v-else type="primary" @click="handleReset">重置密码</el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #16294d;
}

/* ---------- 动态背景层 ---------- */
.bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.bg-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 图片加载失败兜底：隐藏失败图后，底色为蓝橙渐变，界面依然完整可读 */
.bg-fallback {
  background: linear-gradient(115deg, #16294d 0%, #2a5298 50%, #c25e2e 100%);
}

/* 深蓝→橙的渐变遮罩，保证前景可读 */
.bg-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(115deg, rgba(14, 36, 78, 0.94) 0%, rgba(30, 60, 114, 0.82) 45%, rgba(255, 126, 61, 0.45) 100%);
}

/* 流动光晕 */
.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.55;
  animation: orb-drift 12s ease-in-out infinite alternate;
}

.orb-1 {
  width: 420px;
  height: 420px;
  left: -80px;
  top: -60px;
  background: radial-gradient(circle, #ffb25e, transparent 70%);
}

.orb-2 {
  width: 380px;
  height: 380px;
  right: -60px;
  bottom: -80px;
  background: radial-gradient(circle, #5b8de8, transparent 70%);
  animation-delay: -6s;
}

@keyframes orb-drift {
  0% {
    transform: translate(0, 0) scale(1);
  }
  100% {
    transform: translate(40px, 30px) scale(1.15);
  }
}

/* 漂浮包裹/纸飞机 */
.float {
  position: absolute;
  z-index: 1;
  width: 56px;
  height: 56px;
  opacity: 0.9;
  filter: drop-shadow(0 6px 14px rgba(0, 0, 0, 0.25));
  animation: float-drift 7s ease-in-out infinite;
}

.float-1 {
  left: 12%;
  top: 22%;
  animation-delay: 0s;
}

.float-2 {
  right: 14%;
  top: 30%;
  width: 44px;
  height: 44px;
  animation-delay: -2.4s;
}

.float-3 {
  left: 18%;
  bottom: 16%;
  width: 40px;
  height: 40px;
  animation-delay: -4.2s;
}

@keyframes float-drift {
  0%,
  100% {
    transform: translateY(0) rotate(-6deg);
  }
  50% {
    transform: translateY(-22px) rotate(8deg);
  }
}

/* ---------- 内容区 ---------- */
.login-wrap {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 72px;
  max-width: 1080px;
  width: 100%;
  padding: 24px;
}

/* 左侧品牌区 */
.brand-panel {
  flex: 1;
  color: #fff;
  min-width: 0;
}

.brand-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
  filter: drop-shadow(0 8px 18px rgba(0, 0, 0, 0.3));
}

.brand-logo svg {
  width: 100%;
  height: 100%;
}

.brand-panel h1 {
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 2px;
  text-shadow: 0 4px 18px rgba(0, 0, 0, 0.3);
}

.slogan {
  margin-top: 14px;
  font-size: 17px;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: 1px;
}

.features {
  margin-top: 34px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.features li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.9);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ffb25e;
  box-shadow: 0 0 0 4px rgba(255, 178, 94, 0.25);
  flex-shrink: 0;
}

/* 右侧表单卡片：毛玻璃 + 阴影 */
.login-card {
  width: 420px;
  padding: 30px 34px 26px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 50px rgba(8, 20, 45, 0.35);
  flex-shrink: 0;
}

/* Tab 主题色：活力橙 */
.login-card :deep(.el-tabs__item) {
  font-size: 15px;
  color: #6b7280;
}

.login-card :deep(.el-tabs__item.is-active) {
  color: #ff7e3d;
  font-weight: 600;
}

.login-card :deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #ff9f45, #ff7e3d);
  height: 3px;
  border-radius: 2px;
}

/* 输入框圆角 */
.login-card :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-size: 16px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #ff9f45, #ff7e3d);
  box-shadow: 0 8px 20px rgba(255, 126, 61, 0.35);
  transition: transform 0.2s, box-shadow 0.2s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(255, 126, 61, 0.45);
}

.form-tip {
  width: 100%;
  text-align: right;
  margin-top: 2px;
}

.reset-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #909399;
}

/* 短信验证码输入行：输入框 + 获取按钮并排 */
.sms-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.sms-row .el-input {
  flex: 1;
}

.sms-btn {
  flex-shrink: 0;
  min-width: 112px;
}

.sms-demo-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #e68a2e;
}

.sms-demo-tip b {
  letter-spacing: 1px;
}

/* 小屏适配：隐藏品牌区，卡片居中 */
@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }

  .login-wrap {
    justify-content: center;
  }
}
</style>
