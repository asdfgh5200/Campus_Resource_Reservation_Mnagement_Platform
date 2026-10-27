<template>
  <el-container class="layout">
    <!-- 侧边栏：桌面端固定，移动端由菜单按钮展开 -->
    <el-aside :width="sidebarWidth" class="sidebar" :class="{ 'is-mobile-open': mobileMenuOpen }">
      <div class="logo">
        <span class="logo-icon"
          ><el-icon :size="22"><School /></el-icon
        ></span>
        <span v-show="!collapsed" class="logo-text">校园预约</span>
      </div>
      <el-menu
        ref="menuRef"
        :default-active="activeMenu"
        :collapse="collapsed"
        :router="true"
        background-color="transparent"
        text-color="#bfcbd9"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <el-menu-item index="/home" v-if="!userStore.isAdmin">
          <el-icon><HomeFilled /></el-icon>
          <template #title>资源中心</template>
        </el-menu-item>
        <el-menu-item index="/my-reservations" v-if="!userStore.isAdmin">
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
        <!-- 管理端：数据概览 / 资源管理（二级：资源档案、预约管理、闭馆管理）/ 用户管理 -->
        <el-menu-item v-if="userStore.isAdmin" index="/admin/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>数据概览</template>
        </el-menu-item>
        <el-sub-menu v-if="userStore.isAdmin" index="resource-group" popper-class="resource-popper">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>资源管理</span>
          </template>
          <el-menu-item index="/admin/resources">资源档案</el-menu-item>
          <el-menu-item index="/admin/reservations">预约管理</el-menu-item>
          <el-menu-item index="/admin/closures">闭馆管理</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/admin/users" v-if="userStore.isAdmin">
          <el-icon><User /></el-icon>
          <template #title>
            <span>用户管理</span>
            <el-badge v-if="adminUnread > 0" :value="adminUnread" class="menu-badge" />
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleMenu">
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
          title="校园当前处于闭馆状态，暂停开放预约，恢复时间以平台通知为准"
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
  <div v-if="mobileMenuOpen" class="mobile-mask" @click="mobileMenuOpen = false"></div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import api from '@/api'
import { ChatDotRound } from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const mobileMenuOpen = ref(false)
const menuRef = ref(null)

// 管理端：数据概览 + 资源管理（三个子页面）+ 用户管理
const isMobile = ref(window.innerWidth <= 768)
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
onMounted(() => {
  clockTimer = setInterval(() => (now.value = new Date()), 1000)
  refreshGlobalStatus()
  statusTimer = setInterval(refreshGlobalStatus, 30000)
  window.addEventListener('campus-unread-changed', refreshGlobalStatus)
  // 闭馆/恢复开放在智约机器人卡片或后台页面生效后，横幅与未读数需要立即跟上
  window.addEventListener('campus-status-changed', refreshGlobalStatus)
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  clearInterval(clockTimer)
  clearInterval(statusTimer)
  window.removeEventListener('campus-unread-changed', refreshGlobalStatus)
  window.removeEventListener('campus-status-changed', refreshGlobalStatus)
  window.removeEventListener('resize', syncViewport)
})

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
  } catch (e) {
    /* 忽略，不阻断页面 */
  }
}

const activeMenu = computed(() => route.path)
const sidebarWidth = computed(() => {
  if (isMobile.value) return mobileMenuOpen.value ? '220px' : '0px'
  return collapsed.value ? '64px' : '220px'
})
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

function toggleMenu() {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value
    return
  }
  collapsed.value = !collapsed.value
}

function syncViewport() {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) mobileMenuOpen.value = false
}

watch(
  () => route.path,
  () => {
    mobileMenuOpen.value = false
  }
)
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.sidebar {
  background: linear-gradient(180deg, #123a6b 0%, #0a1f3c 100%);
  transition: width 0.3s;
  overflow: hidden;
  z-index: 30;

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

    .logo-icon {
      font-size: 24px;
    }
  }

  .sidebar-menu {
    border: none;

    /* 一级项与一级分组标题统一字号，二级项同样取这个值，保证整栏字号一致 */
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      font-size: 14px;

      &:hover {
        background-color: rgba(255, 255, 255, 0.06) !important;
      }
    }
    :deep(.el-menu-item.is-active) {
      background: linear-gradient(90deg, #2e76db 0%, #144e9c 100%) !important;
    }

    /* 二级菜单：背景压暗 + 文字右缩进 + 圆点引导，和一级项区分层级（字号与一级保持一致） */
    :deep(.el-sub-menu .el-menu) {
      background: rgba(0, 0, 0, 0.18) !important;
      padding: 2px 0;
    }
    :deep(.el-sub-menu .el-menu-item) {
      padding-left: 56px !important;
      height: 40px;
      line-height: 40px;
      font-size: 14px;
      font-weight: 400;
      position: relative;

      &::before {
        content: '';
        position: absolute;
        left: 36px;
        top: 50%;
        width: 5px;
        height: 5px;
        margin-top: -2.5px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.32);
        transition: background 0.2s;
      }
      &:hover::before {
        background: rgba(255, 255, 255, 0.7);
      }
      &.is-active {
        box-shadow: inset 3px 0 0 #7cc0ff;

        &::before {
          background: #fff;
        }
      }
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
      &:hover {
        color: #409eff;
      }
    }
    .page-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
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
        background: linear-gradient(135deg, #2e76db, #74b9f5);
        color: #fff;
        font-weight: 600;
      }
      .user-name {
        font-size: 14px;
        color: #303133;
      }
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

.mobile-mask {
  display: none;
}

@media (max-width: 768px) {
  .layout {
    position: relative;
  }

  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    transition: width 0.25s ease;
    box-shadow: 8px 0 24px rgba(10, 31, 60, 0.2);
  }

  .sidebar:not(.is-mobile-open) {
    box-shadow: none;
  }
  .main-content {
    padding: 12px;
  }
  .header {
    padding: 0 12px;
  }
  .header-right .clock,
  .header-right .user-name {
    display: none;
  }
  .mobile-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(10, 31, 60, 0.35);
  }
}
</style>

<style lang="scss">
/* 侧栏折叠时「资源管理」二级菜单的弹出层配色 */
.resource-popper {
  .el-menu {
    background: #0f2a4d;
    padding: 4px 0;

    .el-menu-item {
      height: 40px;
      line-height: 40px;
      font-size: 14px;
      font-weight: 400;
      color: #bfcbd9;

      &:hover {
        background: rgba(255, 255, 255, 0.08) !important;
      }
      &.is-active {
        background: linear-gradient(90deg, #2e76db 0%, #144e9c 100%) !important;
        color: #fff;
      }
    }
  }
}
</style>
