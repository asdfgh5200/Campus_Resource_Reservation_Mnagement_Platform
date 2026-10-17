<template>
  <div class="profile-page" v-loading="loading">
    <!-- 基本信息 -->
    <el-card shadow="never" class="info-card">
      <div class="profile-head">
        <div class="avatar">{{ (user.nickname || user.username || 'U').charAt(0) }}</div>
        <div class="head-main">
          <div class="name-row">
            <h2 class="nick">{{ user.nickname || user.username }}</h2>
            <el-tag :type="user.role === 1 ? 'danger' : 'primary'" size="small" effect="light">
              {{ user.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
            <el-tag v-if="summary.restricted" type="danger" size="small" effect="dark">今日禁止预约</el-tag>
          </div>
          <div class="sub-row">
            <span class="account">@{{ user.username }}</span>
          </div>
        </div>
      </div>

      <el-descriptions :column="2" border class="desc">
        <el-descriptions-item label="用户名">{{ user.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名 / 昵称">{{ user.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ user.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ user.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="user.status === 1 ? 'success' : 'info'" size="small">
            {{ user.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ fmt(user.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 违规概览 -->
    <el-card shadow="never" class="violation-card">
      <template #header>
        <div class="card-header">
          <span class="title">违规情况</span>
          <span class="rule">规则：一天内违规满 {{ summary.limit }} 次，当日禁止预约，次日自动恢复</span>
        </div>
      </template>

      <el-alert v-if="summary.restricted" type="error" :closable="false" show-icon class="restrict-tip">
        <template #title>
          你今日已有 {{ summary.todayCount }} 次违规记录，今日禁止预约，次日自动恢复。
        </template>
      </el-alert>

      <div class="stat-row">
        <div class="stat">
          <div class="stat-num" :class="{ danger: summary.todayCount > 0 }">{{ summary.todayCount }}</div>
          <div class="stat-label">今日违规</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ summary.totalCount }}</div>
          <div class="stat-label">累计违规</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ summary.limit }}</div>
          <div class="stat-label">每日上限</div>
        </div>
        <div class="stat">
          <div class="stat-num" :class="summary.restricted ? 'danger' : 'ok'">
            {{ summary.restricted ? '受限' : '正常' }}
          </div>
          <div class="stat-label">今日预约权限</div>
        </div>
      </div>

      <el-table :data="violations" stripe style="width: 100%" empty-text="暂无违规记录">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag type="danger" size="small" effect="light">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="260">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '有效' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const user = ref({})
const violations = ref([])
const summary = reactive({ todayCount: 0, totalCount: 0, limit: 3, restricted: false })

const TYPE_TEXT = { 1: '爽约', 2: '超时使用', 3: '恶意取消', 4: '其他' }
function typeText(t) { return TYPE_TEXT[t] || '其他' }

function fmt(dt) { return dt ? String(dt).replace('T', ' ').slice(0, 16) : '-' }

async function loadData() {
  loading.value = true
  try {
    const [meRes, sumRes] = await Promise.all([
      api.getUserInfo(),
      api.getMyViolationSummary()
    ])
    if (meRes.code === 200) {
      user.value = meRes.data || {}
      userStore.userInfo = user.value
      localStorage.setItem('userInfo', JSON.stringify(user.value))
    }
    if (sumRes.code === 200 && sumRes.data) Object.assign(summary, sumRes.data)
    // 显式带上本人 userId：管理员调用该接口默认返回全部用户，这里只看自己的
    const vioRes = await api.getViolations({ userId: user.value.id, current: 1, size: 50 })
    if (vioRes.code === 200) violations.value = vioRes.data?.records || []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.profile-page { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }

.info-card { border-radius: 14px; border: none;
  .profile-head { display: flex; align-items: center; gap: 18px; margin-bottom: 18px;
    .avatar {
      width: 64px; height: 64px; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      font-size: 26px; font-weight: 700; color: #fff;
      background: linear-gradient(135deg, #2E76DB, #74B9F5);
    }
    .head-main {
      .name-row { display: flex; align-items: center; gap: 10px;
        .nick { font-size: 20px; font-weight: 600; color: #303133; }
      }
      .sub-row { display: flex; align-items: center; gap: 16px; margin-top: 6px;
        .account { font-size: 13px; color: #909399; }
      }
    }
  }
  .desc { margin-top: 4px; }
}

.violation-card { border-radius: 14px; border: none;
  .card-header { display: flex; align-items: center; justify-content: space-between;
    .title { font-size: 15px; font-weight: 600; color: #303133; }
    .rule { font-size: 12px; color: #909399; }
  }
  .restrict-tip { margin-bottom: 14px; }
  .stat-row { display: flex; gap: 12px; margin-bottom: 16px;
    .stat { flex: 1; text-align: center; padding: 14px 0; border-radius: 10px; background: #f7f9fc;
      .stat-num { font-size: 22px; font-weight: 700; color: #303133;
        &.danger { color: #f56c6c; }
        &.ok { color: #67c23a; }
      }
      .stat-label { margin-top: 4px; font-size: 12px; color: #909399; }
    }
  }
}
</style>
