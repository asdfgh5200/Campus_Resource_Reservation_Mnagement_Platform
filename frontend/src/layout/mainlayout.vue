<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <span class="logo-icon"><el-icon :size="22"><School /></el-icon></span>
        <span v-show="!collapsed" class="logo-text">校园预约</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :router="true"
        background-color="transparent"
        text-color="#bfcbd9"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>资源中心</template>
        </el-menu-item>
        <el-menu-item index="/my-reservations">
          <el-icon><Calendar /></el-icon>
          <template #title>我的预约</template>
        </el-menu-item>
        <el-menu-item index="/my-messages" v-if="!userStore.isAdmin">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>
            <span>消息中心</span>
            <el-badge v-if="myUnread > 0" :value="myUnread" class="menu-badge" />
          </template>
        </el-menu-item>
        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>管理后台</span>
          </template>
          <el-menu-item index="/admin/dashboard">数据概览</el-menu-item>
          <el-menu-item index="/admin/reservations">预约管理</el-menu-item>
          <el-menu-item index="/admin/resources">资源管理</el-menu-item>
          <el-menu-item index="/admin/courses">课表管理</el-menu-item>
          <el-menu-item index="/admin/violations">违规管理</el-menu-item>
          <el-menu-item index="/admin/closures">闭馆管理</el-menu-item>
          <el-menu-item index="/admin/messages">
            <span>留言回复</span>
            <el-badge v-if="adminUnread > 0" :value="adminUnread" class="menu-badge" />
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <span class="clock">
            <el-icon><Clock /></el-icon>
            {{ clockText }}
          </span>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="32" class="avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.userInfo?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>我的
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <el-alert
          v-if="campusClosed"
          class="closed-banner"
          type="error"
          show-icon
          :closable="false"
          title="当前已闭馆，普通用户预约已暂停，等待管理员恢复开放"
        />
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 智约机器人悬浮窗 -->
    <AiChatWidget />
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import api from '@/api'
import { ChatDotRound } from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const campusClosed = ref(false)
const myUnread = ref(0)
const adminUnread = ref(0)
let statusTimer = null

const now = ref(new Date())
let clockTimer = null
const clockText = computed(() => {
  const d = now.value
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})
onMounted(() => { clockTimer = setInterval(() => (now.value = new Date()), 1000); refreshGlobalStatus(); statusTimer = setInterval(refreshGlobalStatus, 30000); window.addEventListener('campus-unread-changed', refreshGlobalStatus) })
onBeforeUnmount(() => { clearInterval(clockTimer); clearInterval(statusTimer); window.removeEventListener('campus-unread-changed', refreshGlobalStatus) })

async function refreshGlobalStatus() {
  try {
    const res = await api.getCampusStatus()
    if (res.code === 0 || res.code === 200) campusClosed.value = !!res.data?.closed
    if (!userStore.isAdmin) {
      const u = await api.getMyUnreadCount()
      if (u.code === 0 || u.code === 200) myUnread.value = u.data?.count || 0
    } else {
      const a = await api.getAdminUnreadCount()
      if (a.code === 0 || a.code === 200) adminUnread.value = a.data?.count || 0
    }
  } catch (e) { /* 忽略，不阻断页面 */ }
}

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '校园资源预约平台')
const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || 'U'
  return name.charAt(0)
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.sidebar {
  background: linear-gradient(180deg, #123A6B 0%, #0A1F3C 100%);
  transition: width 0.3s;
  overflow: hidden;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .logo-icon { font-size: 24px; }
  }

  .sidebar-menu {
    border: none;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      &:hover { background-color: rgba(255, 255, 255, 0.06) !important; }
    }
    :deep(.el-menu-item.is-active) {
      background: linear-gradient(90deg, #2E76DB 0%, #144E9C 100%) !important;
    }
  }
}

.header {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      color: #5a5e66;
      transition: color 0.2s;
      &:hover { color: #409eff; }
    }
    .page-title { font-size: 16px; font-weight: 600; color: #303133; }
  }

  .header-right {
    .clock {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-right: 18px;
      padding: 6px 12px;
      border-radius: 8px;
      background: #f5f7fa;
      border: 1px solid #ebeef5;
      font-size: 13px;
      font-variant-numeric: tabular-nums;
      color: #303133;
    }
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      outline: none;

      .avatar {
        background: linear-gradient(135deg, #2E76DB, #74B9F5);
        color: #fff;
        font-weight: 600;
      }
      .user-name { font-size: 14px; color: #303133; }
    }
  }
}

.main-content {
  padding: 20px;
  overflow-y: auto;
}

.closed-banner {
  margin-bottom: 12px;
}

.menu-badge {
  margin-left: 8px;
  vertical-align: middle;
}
</style>
