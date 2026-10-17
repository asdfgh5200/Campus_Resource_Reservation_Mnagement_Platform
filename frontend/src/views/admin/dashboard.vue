<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 一键闭馆 / 恢复开放 -->
    <el-card shadow="never" class="campus-bar">
      <div class="cb-inner">
        <div class="cb-left">
          <el-icon :size="20" :color="campusClosed ? '#f56c6c' : '#67c23a'">
            <component :is="campusClosed ? 'WarnTriangleFilled' : 'CircleCheckFilled'" />
          </el-icon>
          <span class="cb-title">校园状态：{{ campusClosed ? '已闭馆（普通用户不可预约）' : '开放中' }}</span>
          <span class="cb-hint">为应对节假日/突发情况，管理员可一键闭馆，恢复后普通用户自动可约</span>
        </div>
        <el-button
          v-if="!campusClosed"
          type="danger"
          :loading="switching"
          @click="doCampusToggle(true)"
        >一键闭馆</el-button>
        <el-button
          v-else
          type="success"
          :loading="switching"
          @click="doCampusToggle(false)"
        >恢复开放</el-button>
      </div>
    </el-card>

    <!-- 统计卡片（可点击跳转查看明细） -->
    <div class="stat-cards">
      <div
        v-for="card in statCards"
        :key="card.label"
        class="stat-card"
        :style="{ background: card.bg }"
        @click="go(card)"
      >
        <div class="stat-icon"><el-icon :size="28"><component :is="card.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
        <div class="stat-go"><el-icon><ArrowRight /></el-icon></div>
      </div>
    </div>

    <!-- 图表 + 列表 -->
    <div class="dashboard-main">
      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><PieChart /></el-icon> 资源类型分布
            <span class="title-hint">点击扇区查看该类资源</span>
          </div>
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </el-card>

      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><List /></el-icon> 最近预约
            <span class="title-hint">点击查看全部</span>
          </div>
        </template>
        <div class="recent-list">
          <div
            v-for="item in stats.recentReservations || []"
            :key="item.id"
            class="recent-item"
            @click="goRecent(item)"
          >
            <div class="recent-info">
              <div class="recent-name">{{ item.resourceName }}</div>
              <div class="recent-time">{{ item.username || '—' }} · {{ item.startTime }}</div>
            </div>
            <el-tag :type="statusTagType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
          </div>
          <el-empty v-if="!(stats.recentReservations || []).length" description="暂无预约" :image-size="70" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { PieChart, List, ArrowRight, WarnTriangleFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { api } from '@/api'

const router = useRouter()
const loading = ref(false)
const chartRef = ref()
const stats = reactive({})
const violationUsers = ref(0)
const campusClosed = ref(false)
const switching = ref(false)
let chartInstance = null

const statCards = [
  { label: '资源总数', value: () => stats.resourceTotal, icon: 'School', bg: 'linear-gradient(135deg, #123A6B, #2E76DB)', to: '/admin/resources' },
  { label: '当前可预约', value: () => stats.resourceActive, icon: 'CircleCheck', bg: 'linear-gradient(135deg, #144E9C, #3D8BEE)', to: '/admin/resources', query: { status: 1 } },
  { label: '维修中', value: () => stats.resourceRepair, icon: 'Tools', bg: 'linear-gradient(135deg, #1E62D0, #4E9BF0)', to: '/admin/resources', query: { status: 3 } },
  { label: '预约总数', value: () => stats.reservationTotal, icon: 'Calendar', bg: 'linear-gradient(135deg, #2E76DB, #74B9F5)', to: '/admin/reservations' },
  { label: '待打卡预约', value: () => stats.reservationPending, icon: 'AlarmClock', bg: 'linear-gradient(135deg, #4E9BF0, #8FCBFB)', to: '/admin/reservations', query: { status: 1 } },
  { label: '违规用户', value: () => violationUsers.value, icon: 'WarningFilled', bg: 'linear-gradient(135deg, #0A1F3C, #1E62D0)', to: '/admin/violations' }
].map(c => ({ ...c, get value() { return c.value() } }))

function statusText(s) { return { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }[s] || '未知' }
function statusTagType(s) { return { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }[s] || 'info' }

function go(card) {
  router.push({ path: card.to, query: card.query || {} })
}
function goRecent(item) {
  router.push({ path: '/admin/reservations', query: item.username ? { username: item.username } : {} })
}

async function loadData() {
  loading.value = true
  try {
    const [res, vio, cs] = await Promise.all([
      api.getStats(),
      api.getAdminViolationUsers({ current: 1, size: 1 }),
      api.getCampusStatus()
    ])
    if (res.code === 200) {
      Object.assign(stats, res.data)
      await nextTick()
      renderChart()
    }
    if (vio.code === 200) violationUsers.value = vio.data?.total || 0
    if (cs.code === 200 || cs.code === 0) campusClosed.value = !!cs.data?.closed
  } finally {
    loading.value = false
  }
}

async function doCampusToggle(toClose) {
  const tip = toClose
    ? '确认开启一键闭馆？开启后普通用户将无法新建预约。'
    : '确认恢复开放？开放后普通用户可重新预约。'
  try {
    await ElMessageBox.confirm(tip, '一键闭馆', { type: 'warning' })
  } catch (e) { return }
  switching.value = true
  try {
    const res = toClose ? await api.campusClose() : await api.campusOpen()
    if (res.code === 200 || res.code === 0) {
      campusClosed.value = toClose
      ElMessage.success(toClose ? '已闭馆' : '已恢复开放')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    switching.value = false
  }
}

const TYPE_BY_NAME = { '教室': 1, '实验室': 2, '图书馆': 3, '体育馆': 4 }

function renderChart() {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 10, icon: 'circle' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: (stats.typeDistribution || []).map(d => ({
        name: d.name,
        value: d.value,
        itemStyle: {
          color: { '教室': '#144E9C', '实验室': '#2E76DB', '图书馆': '#4E9BF0', '体育馆': '#74B9F5' }[d.name] || '#999'
        }
      }))
    }]
  })
  chartInstance.off('click')
  chartInstance.on('click', (p) => {
    const type = TYPE_BY_NAME[p.name]
    if (type) router.push({ path: '/admin/resources', query: { type } })
  })
}

function onResize() { chartInstance?.resize() }

onMounted(() => {
  loadData()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chartInstance?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard-page { max-width: 1200px; margin: 0 auto; }

.campus-bar {
  margin-bottom: 16px;
  border-radius: 12px;
  .cb-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .cb-left {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .cb-title { font-weight: 600; color: #303133; }
  .cb-hint { font-size: 12px; color: #909399; }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  border-radius: 14px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.25s, box-shadow 0.25s;

  &:hover { transform: translateY(-4px); box-shadow: 0 10px 24px rgba(0, 0, 0, 0.16); }
  .stat-icon { font-size: 36px; }
  .stat-value { font-size: 32px; font-weight: 700; }
  .stat-label { font-size: 14px; opacity: 0.9; }
  .stat-go {
    position: absolute; right: 14px; bottom: 12px;
    opacity: 0.55; font-size: 16px;
    transition: opacity 0.25s, transform 0.25s;
  }
  &:hover .stat-go { opacity: 1; transform: translateX(3px); }
}

.dashboard-main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-card, .recent-card {
  border-radius: 14px;
  border: none;

  .card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;

    .title-hint { margin-left: auto; font-size: 12px; font-weight: 400; color: #a8abb2; }
  }
}

.chart-container { height: 300px; }

.recent-list { display: flex; flex-direction: column; gap: 12px; }

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f9fafc;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover { background: #ecf5ff; }
  .recent-name { font-size: 14px; font-weight: 500; color: #303133; }
  .recent-time { font-size: 12px; color: #909399; margin-top: 2px; }
}
</style>
