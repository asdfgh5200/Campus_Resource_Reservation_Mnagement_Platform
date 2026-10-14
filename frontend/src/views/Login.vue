<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-card">
      <div class="login-header">
        <div class="logo-icon"><el-icon :size="32"><School /></el-icon></div>
        <h1 class="title">校园资源预约平台</h1>
        <p class="subtitle">统一管理教室 · 实验室 · 图书馆 · 体育馆</p>
      </div>

      <el-tabs v-model="activeTab" class="auth-tabs" stretch @tab-change="onTabChange">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <!-- 登录 -->
      <el-form
        v-if="activeTab === 'login'"
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>

      <!-- 注册 -->
      <el-form
        v-else
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        size="large"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input v-model="registerForm.username" placeholder="用户名（3-20 位字母/数字/下划线）" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="registerForm.nickname" placeholder="姓名 / 昵称（选填）" :prefix-icon="Postcard" />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="registerForm.phone" placeholder="手机号（选填）" :prefix-icon="Iphone" maxlength="11" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="密码（6-32 位）" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleRegister">注 册</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Postcard, Iphone } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { api } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref()
const registerFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', nickname: '', phone: '', password: '', confirmPassword: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  phone: [{
    validator: (rule, value, callback) => {
      if (!value || /^1[3-9]\d{9}$/.test(value)) callback()
      else callback(new Error('手机号格式不正确'))
    },
    trigger: 'blur'
  }]
}

function onTabChange() {
  loginFormRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await userStore.login(loginForm.username, loginForm.password)
      if (res.code === 200) {
        ElMessage.success('登录成功')
        const target = route.query.redirect || (userStore.isAdmin ? '/admin/dashboard' : '/home')
        router.replace(target)
      } else {
        ElMessage.error(res.message)
      }
    } catch (e) {
      ElMessage.error('登录失败，请重试')
    } finally {
      loading.value = false
    }
  })
}

async function handleRegister() {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await api.register({
        username: registerForm.username,
        password: registerForm.password,
        nickname: registerForm.nickname,
        phone: registerForm.phone
      })
      if (res.code === 200) {
        ElMessage.success('注册成功，请使用新账号登录')
        loginForm.username = registerForm.username
        loginForm.password = ''
        activeTab.value = 'login'
        registerFormRef.value.resetFields()
      } else {
        ElMessage.error(res.message)
      }
    } catch (e) {
      ElMessage.error('注册失败，请重试')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: url('/assets/bg-login.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(20, 40, 80, 0.3) 0%, rgba(50, 30, 80, 0.3) 100%);
    z-index: 0;
  }
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;

  .bg-circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
    animation: float 8s ease-in-out infinite;

    &-1 { width: 400px; height: 400px; top: -100px; left: -100px; animation-delay: 0s; }
    &-2 { width: 300px; height: 300px; bottom: -80px; right: -60px; animation-delay: 2s; }
    &-3 { width: 200px; height: 200px; top: 40%; left: 60%; animation-delay: 4s; }
  }
}

@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-30px) scale(1.05); }
}

.login-card {
  width: 420px;
  max-width: 90vw;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  padding: 40px 36px;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.2);
  z-index: 1;
  animation: cardIn 0.6s ease;
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-header {
  text-align: center;
  margin-bottom: 20px;

  .logo-icon { font-size: 48px; }
  .title { font-size: 24px; font-weight: 700; color: #fff; margin: 12px 0 6px; text-shadow: 0 2px 10px rgba(0,0,0,0.3); }
  .subtitle { font-size: 13px; color: rgba(255, 255, 255, 0.85); }
}

.auth-tabs {
  :deep(.el-tabs__header) { margin-bottom: 20px; }
  :deep(.el-tabs__nav-wrap::after) { background-color: rgba(255, 255, 255, 0.2); }
  :deep(.el-tabs__item) { color: rgba(255, 255, 255, 0.7); font-size: 15px; }
  :deep(.el-tabs__item.is-active) { color: #fff; font-weight: 600; }
  :deep(.el-tabs__active-bar) { background-color: #fff; }
}

// 半透明输入框
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.18) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  box-shadow: none !important;

  &:hover { background: rgba(255, 255, 255, 0.25) !important; }
  &.is-focus { background: rgba(255, 255, 255, 0.3) !important; }
}
:deep(.el-input__inner) {
  color: #fff !important;
  &::placeholder { color: rgba(255, 255, 255, 0.7) !important; }
}
:deep(.el-input__prefix-inner), :deep(.el-input__suffix-inner) { color: rgba(255, 255, 255, 0.85) !important; }

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5); }
}
</style>
