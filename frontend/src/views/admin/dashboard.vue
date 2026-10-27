<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 一键闭馆 / 恢复开放 -->
    <el-card shadow="never" class="campus-bar">
      <div class="cb-inner">
        <div class="cb-left">
          <el-icon :size="18" :color="campusClosed ? '#e05c5c' : '#35b37e'">
            <component :is="campusClosed ? 'WarnTriangleFilled' : 'CircleCheckFilled'" />
          </el-icon>
          <span class="cb-title">校园{{ campusClosed ? '已闭馆' : '开放中' }}</span>
          <span class="cb-hint">{{
            campusClosed
              ? '普通用户暂不可预约，恢复后自动可约'
              : '正常受理预约，可一键闭馆应对突发情况'
          }}</span>
        </div>
        <div class="cb-right">
          <el-button :icon="Files" @click="router.push('/admin/resources')">进入资源管理</el-button>
          <el-button
            v-if="!campusClosed"
            type="danger"
            :loading="switching"
            @click="doCampusToggle(true)"
            >一键闭馆</el-button
          >
          <el-button v-else type="success" :loading="switching" @click="doCampusToggle(false)"
            >恢复开放</el-button
          >
        </div>
      </div>
    </el-card>

    <!-- 关键指标（可点击跳转查看明细） -->
    <div class="stat-cards">
      <div
        v-for="card in statCards"
        :key="card.label"
        class="stat-card"
        :style="{ background: card.bg }"
        @click="go(card)"
      >
        <el-icon class="stat-watermark" :size="66"><component :is="card.icon" /></el-icon>
        <el-icon class="stat-go"><ArrowRight /></el-icon>
        <div class="stat-main">
          <span class="stat-icon">
            <el-icon :size="18"><component :is="card.icon" /></el-icon>
          </span>
          <div class="stat-text">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 数据图表总览 -->
    <div class="chart-grid">
      <el-card class="chart-card span-8" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><TrendCharts /></el-icon> 近 7 天预约趋势
            <span class="title-hint">
              今日 <b>{{ todayCount }}</b> 次 · 昨日 {{ yesterdayCount }} 次
            </span>
          </div>
        </template>
        <div ref="trendRef" class="chart-box tall"></div>
      </el-card>

      <el-card class="chart-card span-4" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><PieChart /></el-icon> 预约状态构成
            <span class="title-hint">共 {{ stats.reservationTotal ?? 0 }} 条</span>
          </div>
        </template>
        <div class="status-wrap">
          <div ref="statusRef" class="status-donut"></div>
          <ul class="status-legend">
            <li v-for="s in statusRows" :key="s.name">
              <i :style="{ background: STATUS_COLORS[s.name] }"></i>
              <span class="legend-name">{{ s.name }}</span>
              <span class="legend-value">{{ s.value }}</span>
              <span class="legend-pct">{{ s.pct }}%</span>
            </li>
          </ul>
        </div>
      </el-card>

      <el-card class="chart-card span-4" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><School /></el-icon> 资源类型分布
            <span class="title-hint">共 {{ stats.resourceTotal ?? 0 }} 个资源</span>
          </div>
        </template>
        <div ref="typeRef" class="chart-box"></div>
      </el-card>

      <el-card class="chart-card span-4" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><Clock /></el-icon> 预约时段热度
            <span class="title-hint">按使用时段统计</span>
          </div>
        </template>
        <div ref="hourRef" class="chart-box"></div>
      </el-card>

      <el-card class="chart-card span-4" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><Histogram /></el-icon> 热门资源 TOP5
            <span class="title-hint">按累计预约次数</span>
          </div>
        </template>
        <div class="chart-wrap">
          <div ref="hotRef" class="chart-box"></div>
          <div v-if="!(stats.hotResources || []).length" class="chart-empty">暂无预约数据</div>
        </div>
      </el-card>

      <el-card class="chart-card span-12" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><User /></el-icon> 近 7 天用户活跃
            <span class="title-hint">
              今日注册 <b>{{ todayRegister }}</b> 人 · 今日下单 {{ todayActive }} 人
            </span>
          </div>
        </template>
        <div ref="userRef" class="chart-box tall"></div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  TrendCharts,
  PieChart,
  Histogram,
  School,
  Clock,
  ArrowRight,
  Files,
  User
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { api } from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const trendRef = ref()
const typeRef = ref()
const hotRef = ref()
const statusRef = ref()
const hourRef = ref()
const userRef = ref()
const stats = reactive({})
const campusClosed = ref(false)
const switching = ref(false)
let charts = []

const statCards = [
  {
    label: '资源总数',
    value: () => stats.resourceTotal,
    icon: 'School',
    bg: 'linear-gradient(135deg, #14407A, #2E76DB)',
    to: '/admin/resources',
    query: { all: 1 }
  },
  {
    label: '当前可预约',
    value: () => stats.resourceActive,
    icon: 'CircleCheck',
    bg: 'linear-gradient(135deg, #1B57A8, #3D8BEE)',
    to: '/admin/resources',
    query: { status: 1 }
  },
  {
    label: '维修中',
    value: () => stats.resourceRepair,
    icon: 'Tools',
    bg: 'linear-gradient(135deg, #1E62D0, #4E9BF0)',
    to: '/admin/resources',
    query: { status: 3 }
  },
  {
    label: '预约总数',
    value: () => stats.reservationTotal,
    icon: 'Calendar',
    bg: 'linear-gradient(135deg, #2A6FD0, #5FA2F3)',
    to: '/admin/reservations'
  },
  {
    label: '用户总数',
    value: () => stats.userTotal,
    icon: 'User',
    bg: 'linear-gradient(135deg, #0E2A50, #1E62D0)',
    to: '/admin/users'
  },
  {
    label: '今日新增注册',
    value: () => stats.userNewToday,
    icon: 'UserFilled',
    bg: 'linear-gradient(135deg, #123A6B, #2E76DB)',
    to: '/admin/users'
  },
  {
    label: '活跃用户',
    value: () => stats.userActive,
    icon: 'CircleCheckFilled',
    bg: 'linear-gradient(135deg, #1B57A8, #4E9BF0)',
    to: '/admin/users'
  },
  {
    label: '违规用户',
    value: () => stats.userViolation,
    icon: 'WarningFilled',
    bg: 'linear-gradient(135deg, #5A2233, #C0554F)',
    to: '/admin/users'
  }
].map((c) => ({
  ...c,
  get value() {
    return c.value()
  }
}))

function go(card) {
  // 资源类指标直接带筛选条件跳到资源管理页，用户类跳到用户管理
  router.push({ path: card.to, query: card.query || {} })
}

// 窗口尺寸变化时同步所有图表
function goChartResize() {
  charts.forEach((c) => c.resize())
}

async function loadData() {
  loading.value = true
  try {
    const [res, cs] = await Promise.all([api.getStats(), api.getCampusStatus()])
    if (res.code === 200) {
      Object.assign(stats, res.data)
      await nextTick()
      renderCharts()
    }
    if (cs.code === 200 || cs.code === 0) campusClosed.value = !!cs.data?.closed
  } finally {
    loading.value = false
  }
}

/** 只刷新闭馆状态：智约机器人确认闭馆/恢复后，顶部卡片要同步 */
async function refreshCampusStatus() {
  try {
    const cs = await api.getCampusStatus()
    if (cs.code === 200 || cs.code === 0) campusClosed.value = !!cs.data?.closed
  } catch (e) {
    /* 忽略，不阻断页面 */
  }
}

async function doCampusToggle(toClose) {
  let reason = ''
  try {
    if (toClose) {
      // 原因会写进发给全体用户的闭馆通知，可留空
      const { value } = await ElMessageBox.prompt(
        '可填写闭馆原因，会写进发给全体用户的闭馆通知（可留空）。',
        '一键闭馆',
        {
          type: 'warning',
          inputPlaceholder: '如：台风停课 / 场馆检修',
          inputValidator: () => true,
          confirmButtonText: '确认闭馆',
          cancelButtonText: '取消'
        }
      )
      reason = (value || '').trim()
    } else {
      await ElMessageBox.confirm('确认恢复开放？恢复后会通知全体用户。', '恢复开放', {
        type: 'warning'
      })
    }
  } catch (e) {
    return
  }
  switching.value = true
  try {
    const res = toClose ? await api.campusClose(reason) : await api.campusOpen()
    if (res.code === 200 || res.code === 0) {
      campusClosed.value = toClose
      ElMessage.success(
        toClose ? '已闭馆，并已向全体用户发送闭馆通知' : '已恢复开放，并已通知全体用户'
      )
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    switching.value = false
  }
}

const TYPE_COLORS = ['#144e9c', '#2e76db', '#4e9bf0', '#8fc2f7']
const STATUS_COLORS = {
  待打卡: '#4e9bf0',
  履约中: '#35b37e',
  已完成: '#5fa2f3',
  已取消: '#d3dbe7',
  爽约: '#e0a044',
  违规: '#e05c5c'
}

const todayCount = computed(() => (stats.trend || []).at(-1)?.value ?? 0)
const yesterdayCount = computed(() => (stats.trend || []).at(-2)?.value ?? 0)
// 用户趋势的最后一项就是今天
const todayRegister = computed(() => (stats.userTrend || []).at(-1)?.register ?? 0)
const todayActive = computed(() => (stats.userTrend || []).at(-1)?.active ?? 0)
// 状态图例自己画，带上占比更直观
const statusRows = computed(() => {
  const rows = stats.statusDistribution || []
  const total = rows.reduce((sum, r) => sum + (r.value || 0), 0)
  return rows.map((r) => ({ ...r, pct: total ? Math.round(((r.value || 0) / total) * 100) : 0 }))
})

function disposeCharts() {
  charts.forEach((c) => c.dispose())
  charts = []
}

/** 环形图公共配置：中心显示汇总数 */
function donutOption(data, total, subtext, colorOf, withLegend = true) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    title: {
      text: String(total ?? 0),
      subtext,
      left: 'center',
      top: withLegend ? '32%' : '41%',
      itemGap: 2,
      textStyle: { fontSize: 20, fontWeight: 700, color: '#1f2d3d' },
      subtextStyle: { fontSize: 11, color: '#9aa3b2' }
    },
    legend: withLegend
      ? {
          bottom: 0,
          left: 'center',
          icon: 'circle',
          itemWidth: 8,
          itemHeight: 8,
          itemGap: 12,
          textStyle: { color: '#6b7688', fontSize: 11 }
        }
      : { show: false },
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', withLegend ? '42%' : '50%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { scale: true, scaleSize: 5, label: { show: false } },
        data: data.map((d, i) => ({
          name: d.name,
          value: d.value,
          itemStyle: { color: colorOf(d, i) }
        }))
      }
    ]
  }
}

function renderCharts() {
  disposeCharts()
  if (!trendRef.value) return

  // 1. 近 7 天预约趋势（面积折线）
  const trend = stats.trend || []
  const trendChart = echarts.init(trendRef.value)
  charts.push(trendChart)
  trendChart.setOption({
    grid: { left: 2, right: 26, top: 24, bottom: 0, containLabel: true },
    tooltip: {
      trigger: 'axis',
      formatter: (p) => `${p[0].axisValue}　新增预约 ${p[0].value} 次`
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((t) => t.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e9eef7' } },
      axisLabel: { color: '#8a94a6', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      // 数据很少时也给到 3 的刻度，避免一条线贴底
      max: (v) => Math.max(3, v.max),
      splitLine: { lineStyle: { color: '#f1f4fa' } },
      axisLabel: { color: '#9aa3b2', fontSize: 11 }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#2e76db' },
        itemStyle: { color: '#2e76db', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(46, 118, 219, 0.26)' },
            { offset: 1, color: 'rgba(46, 118, 219, 0.02)' }
          ])
        },
        data: trend.map((t) => t.value)
      }
    ]
  })
  trendChart.on('click', () => router.push('/admin/reservations'))

  // 2. 预约状态构成（图例在右侧自绘，环比不画图例）
  const statusTotal = (stats.statusDistribution || []).reduce((sum, r) => sum + (r.value || 0), 0)
  const statusChart = echarts.init(statusRef.value)
  charts.push(statusChart)
  statusChart.setOption(
    donutOption(
      statusRows.value,
      statusTotal,
      '预约总数',
      (d) => STATUS_COLORS[d.name] || '#c8d3e3',
      false
    )
  )
  statusChart.on('click', () => router.push('/admin/reservations'))

  // 3. 资源类型分布
  const typeChart = echarts.init(typeRef.value)
  charts.push(typeChart)
  typeChart.setOption(
    donutOption(stats.typeDistribution || [], stats.resourceTotal, '资源总数', (d, i) => {
      const names = ['教室', '实验室', '图书馆', '体育馆']
      const idx = names.indexOf(d.name)
      return TYPE_COLORS[idx >= 0 ? idx : i]
    })
  )
  typeChart.on('click', (p) => {
    const typeOf = { 教室: 1, 实验室: 2, 图书馆: 3, 体育馆: 4 }
    router.push({ path: '/admin/resources', query: { type: typeOf[p.name] } })
  })

  // 4. 预约时段热度（最热时段加深）
  const hours = stats.hourDistribution || []
  const hourMax = Math.max(1, ...hours.map((h) => h.value || 0))
  const hourChart = echarts.init(hourRef.value)
  charts.push(hourChart)
  hourChart.setOption({
    grid: { left: 2, right: 8, top: 16, bottom: 0, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (p) => `${p[0].axisValue} 起　${p[0].value} 次`
    },
    xAxis: {
      type: 'category',
      data: hours.map((h) => h.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e9eef7' } },
      axisLabel: { color: '#8a94a6', fontSize: 10, interval: 1 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      max: (v) => Math.max(3, v.max),
      splitLine: { lineStyle: { color: '#f1f4fa' } },
      axisLabel: { color: '#9aa3b2', fontSize: 10 }
    },
    series: [
      {
        type: 'bar',
        barWidth: '58%',
        data: hours.map((h) => ({
          value: h.value,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(
              0,
              0,
              0,
              1,
              h.value === hourMax && hourMax > 1
                ? [
                    { offset: 0, color: '#2e76db' },
                    { offset: 1, color: '#8fc2f7' }
                  ]
                : [
                    { offset: 0, color: '#a9cdf6' },
                    { offset: 1, color: '#e6eefb' }
                  ]
            )
          }
        }))
      }
    ]
  })

  // 5. 热门资源 TOP5（横向条形）
  const hot = [...(stats.hotResources || [])].reverse()
  const hotMax = Math.max(1, ...hot.map((h) => h.count || 0))
  const hotChart = echarts.init(hotRef.value)
  charts.push(hotChart)
  hotChart.setOption({
    grid: { left: 2, right: 34, top: 6, bottom: 2, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (p) => `${p[0].name}：${p[0].value} 次`
    },
    xAxis: { type: 'value', show: false, max: hotMax * 1.2 },
    yAxis: {
      type: 'category',
      data: hot.map((h) => h.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#5b6779', fontSize: 11, width: 124, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        showBackground: true,
        backgroundStyle: { color: '#f4f7fc', borderRadius: 6 },
        itemStyle: {
          borderRadius: 6,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#2e76db' },
            { offset: 1, color: '#74b9f5' }
          ])
        },
        label: {
          show: true,
          position: 'right',
          color: '#8a94a6',
          fontSize: 11,
          formatter: '{c}'
        },
        data: hot.map((h) => h.count)
      }
    ]
  })
  hotChart.on('click', () => router.push('/admin/reservations'))

  // 6. 近 7 天用户活跃：新增注册与当日下单用户
  const userTrend = stats.userTrend || []
  const userChart = echarts.init(userRef.value)
  charts.push(userChart)
  userChart.setOption({
    grid: { left: 2, right: 20, top: 36, bottom: 0, containLabel: true },
    legend: {
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      itemGap: 16,
      textStyle: { color: '#6b7688', fontSize: 11 }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (ps) =>
        `${ps[0].axisValue}<br/>` +
        ps.map((p) => `${p.marker}${p.seriesName}：${p.value} 人`).join('<br/>')
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: userTrend.map((t) => t.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e9eef7' } },
      axisLabel: { color: '#8a94a6', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      max: (v) => Math.max(3, v.max),
      splitLine: { lineStyle: { color: '#f1f4fa' } },
      axisLabel: { color: '#9aa3b2', fontSize: 11 }
    },
    series: [
      {
        name: '新增注册',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.5, color: '#2e76db' },
        itemStyle: { color: '#2e76db' },
        data: userTrend.map((t) => t.register)
      },
      {
        name: '下单用户',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.5, color: '#35b37e' },
        itemStyle: { color: '#35b37e' },
        data: userTrend.map((t) => t.active)
      }
    ]
  })
  userChart.on('click', () => router.push('/admin/users'))
}

function onResize() {
  goChartResize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', onResize)
  window.addEventListener('campus-status-changed', refreshCampusStatus)
  // 兼容旧链接带筛选参数（/admin/dashboard?status=3）：转交资源管理页
  const q = route.query
  if (q.all || q.status || q.type || q.keyword) {
    router.replace({ path: '/admin/resources', query: q })
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('campus-status-changed', refreshCampusStatus)
  disposeCharts()
})
</script>

<style scoped lang="scss">
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.campus-bar {
  border-radius: 12px;
  border: none;
  :deep(.el-card__body) {
    padding: 12px 18px;
  }
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
    min-width: 0;
  }
  .cb-title {
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;
  }
  .cb-hint {
    font-size: 12px;
    color: #8a94a6;
  }
  .cb-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  padding: 16px 18px;
  border-radius: 14px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #14407a, #2e76db);
  box-shadow: 0 6px 16px rgba(27, 74, 140, 0.18);
  transition:
    transform 0.22s,
    box-shadow 0.22s;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 12px 24px rgba(27, 74, 140, 0.28);
  }

  .stat-main {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .stat-icon {
    flex-shrink: 0;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.18);
  }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    line-height: 1.15;
    font-variant-numeric: tabular-nums;
  }
  .stat-label {
    margin-top: 2px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.85);
    white-space: nowrap;
  }
  .stat-go {
    position: absolute;
    top: 14px;
    right: 12px;
    font-size: 14px;
    opacity: 0;
    transform: translateX(-4px);
    transition:
      opacity 0.22s,
      transform 0.22s;
  }
  &:hover .stat-go {
    opacity: 0.9;
    transform: none;
  }
  .stat-watermark {
    position: absolute;
    right: -12px;
    bottom: -16px;
    opacity: 0.12;
    pointer-events: none;
  }
}

@media (max-width: 1180px) {
  .stat-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.chart-card {
  border-radius: 14px;
  border: none;
  box-shadow: 0 2px 10px rgba(20, 64, 122, 0.05);

  &.span-8 {
    grid-column: span 8;
  }
  &.span-4 {
    grid-column: span 4;
  }
  &.span-12 {
    grid-column: span 12;
  }
  :deep(.el-card__header) {
    padding: 14px 18px;
    border-bottom: 1px solid #f1f4fa;
  }
  :deep(.el-card__body) {
    padding: 10px 16px 12px;
  }
  .card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;

    .title-hint {
      margin-left: auto;
      font-size: 12px;
      font-weight: 400;
      color: #a8abb2;

      b {
        color: #2e76db;
        font-weight: 700;
      }
    }
  }
}

@media (max-width: 1180px) {
  .chart-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .chart-card {
    &.span-8,
    &.span-4,
    &.span-12 {
      grid-column: auto;
    }
  }
}

.chart-wrap {
  position: relative;
}

.chart-box {
  height: 208px;

  &.tall {
    height: 236px;
  }
}

/* 状态构成：左环右图例 */
.status-wrap {
  height: 236px;
  display: flex;
  align-items: center;
  gap: 4px;

  .status-donut {
    flex: 0 0 48%;
    height: 100%;
  }
  .status-legend {
    flex: 1;
    min-width: 0;
    margin: 0;
    padding: 0 0 0 6px;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 10px;

    li {
      display: flex;
      align-items: center;
      gap: 7px;
      font-size: 12px;
      color: #5b6779;
    }
    i {
      flex-shrink: 0;
      width: 8px;
      height: 8px;
      border-radius: 3px;
    }
    .legend-name {
      white-space: nowrap;
    }
    .legend-value {
      margin-left: auto;
      font-weight: 600;
      color: #1f2d3d;
      font-variant-numeric: tabular-nums;
    }
    .legend-pct {
      width: 32px;
      text-align: right;
      color: #a4adbd;
      font-variant-numeric: tabular-nums;
    }
  }
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #a4adbd;
  background: #fff;
}
</style>
