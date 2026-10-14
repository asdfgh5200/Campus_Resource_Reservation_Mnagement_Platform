<template>
  <div class="reservation-manage">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 全部预约 -->
      <el-tab-pane label="预约记录" name="reservations">
        <div class="filters">
          <el-input v-model="resQuery.username" placeholder="按用户名搜索" clearable style="width: 180px" @keyup.enter="searchReservations" />
          <el-select v-model="resQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="Number(k)" />
          </el-select>
          <el-select v-model="resQuery.resourceType" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="教室" :value="1" />
            <el-option label="实验室" :value="2" />
            <el-option label="图书馆" :value="3" />
            <el-option label="体育馆" :value="4" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchReservations">查询</el-button>
          <el-button :icon="Refresh" @click="resetReservations">重置</el-button>
        </div>

        <el-table v-loading="resLoading" :data="reservations" stripe style="width: 100%">
          <el-table-column label="用户" min-width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="username">{{ row.user?.username || '（已注销用户）' }}</span>
                <span class="nickname">{{ row.user?.nickname }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="资源" min-width="180">
            <template #default="{ row }">{{ row.resource?.name || '未知资源' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ typeText[row.resource?.resourceType] || '-' }}</template>
          </el-table-column>
          <el-table-column label="预约时段" min-width="290">
            <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
          </el-table-column>
          <el-table-column label="验证码" width="140">
            <template #default="{ row }">
              <div v-if="row.checkinCode" class="code-cell">
                <span class="code-num">{{ row.checkinCode }}</span>
                <el-button text type="primary" :icon="DocumentCopy" @click="copyText(row.checkinCode)" />
              </div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType[row.status] || 'info'" size="small">{{ statusText[row.status] || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170" prop="createdAt" />
        </el-table>

        <el-pagination
          class="pager"
          background
          layout="total, prev, pager, next"
          :total="resTotal"
          :current-page="resQuery.current"
          :page-size="resQuery.size"
          @current-change="onResPage"
        />
      </el-tab-pane>

      <!-- 操作日志 -->
      <el-tab-pane label="操作日志" name="logs">
        <div class="filters">
          <el-input v-model="logQuery.username" placeholder="按用户名搜索" clearable style="width: 180px" @keyup.enter="searchLogs" />
          <el-select v-model="logQuery.action" placeholder="全部操作" clearable style="width: 150px">
            <el-option v-for="(t, k) in actionText" :key="k" :label="t" :value="Number(k)" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
          <el-button :icon="Refresh" @click="resetLogs">重置</el-button>
        </div>

        <el-table v-loading="logLoading" :data="logs" stripe style="width: 100%">
          <el-table-column label="操作时间" min-width="170" prop="createdAt" />
          <el-table-column label="用户" min-width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="username">{{ row.username || '（已注销用户）' }}</span>
                <span class="nickname">{{ row.nickname }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-tag :type="actionTagType[row.action] || 'info'" size="small">{{ actionText[row.action] || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="资源" min-width="180" prop="resourceName" />
          <el-table-column label="详情" min-width="280" prop="detail" show-overflow-tooltip />
        </el-table>

        <el-pagination
          class="pager"
          background
          layout="total, prev, pager, next"
          :total="logTotal"
          :current-page="logQuery.current"
          :page-size="logQuery.size"
          @current-change="onLogPage"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { copyText as copyToClipboard } from '@/utils/clipboard'

const route = useRoute()
const activeTab = ref('reservations')

// 状态 3 在用户端已更名为「已结束」，此处保持一致
const statusText = { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }
const statusTagType = { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }
const typeText = { 1: '教室', 2: '实验室', 3: '图书馆', 4: '体育馆' }
const actionText = { 1: '创建预约', 2: '取消预约', 3: '打卡', 4: '爽约', 5: '完成', 6: '提前释放', 7: '暂离', 8: '暂离超时', 9: '删除记录' }
const actionTagType = { 1: 'success', 2: 'info', 3: 'primary', 4: 'danger', 5: 'warning', 6: 'warning', 7: 'primary', 8: 'danger', 9: 'danger' }

const reservations = ref([])
const resLoading = ref(false)
const resTotal = ref(0)
const resQuery = reactive({ current: 1, size: 10, username: '', status: null, resourceType: null })

const logs = ref([])
const logLoading = ref(false)
const logTotal = ref(0)
const logQuery = reactive({ current: 1, size: 10, username: '', action: null })

async function loadReservations() {
  resLoading.value = true
  try {
    const res = await api.getAdminReservations({
      current: resQuery.current,
      size: resQuery.size,
      username: resQuery.username || undefined,
      status: resQuery.status ?? undefined,
      resourceType: resQuery.resourceType ?? undefined
    })
    if (res.code === 200) {
      reservations.value = res.data.records
      resTotal.value = res.data.total
    }
  } finally {
    resLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await api.getAdminReservationLogs({
      current: logQuery.current,
      size: logQuery.size,
      username: logQuery.username || undefined,
      action: logQuery.action ?? undefined
    })
    if (res.code === 200) {
      logs.value = res.data.records
      logTotal.value = res.data.total
    }
  } finally {
    logLoading.value = false
  }
}

function searchReservations() { resQuery.current = 1; loadReservations() }
function resetReservations() {
  resQuery.username = ''
  resQuery.status = null
  resQuery.resourceType = null
  resQuery.current = 1
  loadReservations()
}
function onResPage(p) { resQuery.current = p; loadReservations() }

function searchLogs() { logQuery.current = 1; loadLogs() }
function resetLogs() {
  logQuery.username = ''
  logQuery.action = null
  logQuery.current = 1
  loadLogs()
}
function onLogPage(p) { logQuery.current = p; loadLogs() }

function onTabChange() {
  if (activeTab.value === 'logs' && !logs.value.length) loadLogs()
}

async function copyText(text) {
  const ok = await copyToClipboard(text)
  if (ok) ElMessage.success('验证码已复制')
  else ElMessage.warning('复制失败，请手动选中复制')
}

onMounted(() => {
  // 支持从「数据概览」点击卡片带筛选跳转：/admin/reservations?status=1&username=xxx
  if (route.query.status) resQuery.status = Number(route.query.status)
  if (route.query.username) resQuery.username = String(route.query.username)
  if (route.query.tab) activeTab.value = String(route.query.tab)
  loadReservations()
})
</script>

<style scoped lang="scss">
.reservation-manage { max-width: 1200px; margin: 0 auto; }

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.user-cell {
  display: flex;
  flex-direction: column;

  .username { font-weight: 600; color: #303133; }
  .nickname { font-size: 12px; color: #909399; }
}

.code-cell {
  display: flex;
  align-items: center;
  gap: 4px;

  .code-num {
    font-family: 'Consolas', 'Courier New', monospace;
    font-weight: 600;
    letter-spacing: 1px;
    color: #303133;
  }
}

.muted { color: #c0c4cc; }

.pager { margin-top: 16px; justify-content: flex-end; }
</style>
