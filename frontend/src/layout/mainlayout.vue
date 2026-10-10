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
        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>管理后台</span>
          </template>
          <el-menu-item index="/admin/dashboard">数据概览</el-menu-item>
          <el-menu-item index="/admin/resources">资源管理</el-menu-item>
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
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="32" class="avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.userInfo?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

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
  }
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.sidebar {
  background: linear-gradient(180deg, #1a2b4a 0%, #0d1b2e 100%);
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
      background: linear-gradient(90deg, #409eff 0%, #337ecc 100%) !important;
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
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      outline: none;

      .avatar {
        background: linear-gradient(135deg, #409eff, #67c23a);
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
</style>
