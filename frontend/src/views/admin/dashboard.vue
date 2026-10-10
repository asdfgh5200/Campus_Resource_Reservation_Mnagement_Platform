<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div v-for="card in statCards" :key="card.label" class="stat-card" :style="{ background: card.bg }">
        <div class="stat-icon"><el-icon :size="28"><component :is="card.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 图表 + 列表 -->
    <div class="dashboard-main">
      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="card-title"><el-icon><PieChart /></el-icon> 资源类型分布</div>
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </el-card>

      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="card-title"><el-icon><List /></el-icon> 最近预约</div>
        </template>
        <div class="recent-list">
          <div v-for="item in stats.recentReservations || []" :key="item.id" class="recent-item">
            <div class="recent-info">
              <div class="recent-name">{{ item.resourceName }}</div>
              <div class="recent-time">{{ item.startTime }}</div>
            </div>
            <el-tag :type="statusTagType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { PieChart, List } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { mockApi } from '@/api/mock'

const loading = ref(false)
const chartRef = ref()
const stats = reactive({})
let chartInstance = null

const statCards = [
  { label: '资源总数', value: () => stats.resourceTotal, icon: 'School', bg: 'linear-gradient(135deg, #667eea, #764ba2)' },
  { label: '可预约', value: () => stats.resourceActive, icon: 'CircleCheck', bg: 'linear-gradient(135deg, #43e97b, #38f9d7)' },
  { label: '维修中', value: () => stats.resourceRepair, icon: 'Tools', bg: 'linear-gradient(135deg, #f093fb, #f5576c)' },
  { label: '预约总数', value: () => stats.reservationTotal, icon: 'Calendar', bg: 'linear-gradient(135deg, #4facfe, #00f2fe)' }
].map(c => ({ ...c, get value() { return c.value() } }))

function statusText(s) { return { 1: '待打卡', 2: '使用中', 3: '已完成', 4: '已取消' }[s] || '未知' }
function statusTagType(s) { return { 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }[s] || 'info' }

async function loadData() {
  loading.value = true
  try {
    const res = await mockApi.getStats()
    if (res.code === 200) {
      Object.assign(stats, res.data)
      await nextTick()
      renderChart()
    }
  } finally {
    loading.value = false
  }
}

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
          color: { '教室': '#667eea', '实验室': '#f5576c', '图书馆': '#4facfe', '体育馆': '#43e97b' }[d.name] || '#999'
        }
      }))
    }]
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 14px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.25s;

  &:hover { transform: translateY(-4px); }
  .stat-icon { font-size: 36px; }
  .stat-value { font-size: 32px; font-weight: 700; }
  .stat-label { font-size: 14px; opacity: 0.9; }
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
  transition: background 0.2s;

  &:hover { background: #ecf5ff; }
  .recent-name { font-size: 14px; font-weight: 500; color: #303133; }
  .recent-time { font-size: 12px; color: #909399; margin-top: 2px; }
}
</style>
