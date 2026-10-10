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

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="test-accounts">
        <p class="test-title">测试账号（点击快速填充）</p>
        <div class="test-buttons">
          <span class="test-tag" @click="fillAccount('admin', 'admin')">管理员 admin/admin</span>
          <span class="test-tag" @click="fillAccount('user', 'user')">学生 user/user</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fillAccount(u, p) {
  form.username = u
  form.password = p
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await userStore.login(form.username, form.password)
      if (res.code === 200) {
        ElMessage.success('登录成功')
        const target = userStore.isAdmin ? '/admin/dashboard' : '/home'
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
  margin-bottom: 32px;

  .logo-icon { font-size: 48px; }
  .title { font-size: 24px; font-weight: 700; color: #fff; margin: 12px 0 6px; text-shadow: 0 2px 10px rgba(0,0,0,0.3); }
  .subtitle { font-size: 13px; color: rgba(255, 255, 255, 0.85); }
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

.test-accounts {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);

  .test-title { font-size: 12px; color: rgba(255, 255, 255, 0.8); margin-bottom: 10px; }
  .test-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
  .test-tag {
    font-size: 12px;
    color: #fff;
    background: rgba(255, 255, 255, 0.15);
    padding: 4px 10px;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover { background: rgba(255, 255, 255, 0.3); }
  }
}
</style>
