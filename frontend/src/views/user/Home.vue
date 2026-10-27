<template>
  <div
    class="rc-root"
    ref="rootEl"
    tabindex="0"
    :style="{ '--pagebg': `url(${cur.pageBg})` }"
    @keydown="onKey"
  >
    <!-- 页面级氛围背景图：随分类切换，极淡纹理 -->
    <div class="rc-pagebg"></div>
    <!-- 左侧弧线导航 -->
    <aside class="rc-rail">
      <div class="rc-rail-inner">
        <svg class="rc-svg" viewBox="0 0 380 640" preserveAspectRatio="xMidYMid meet">
          <defs>
            <linearGradient id="curveGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#144E9C" />
              <stop offset="33%" stop-color="#2E76DB" />
              <stop offset="66%" stop-color="#4E9BF0" />
              <stop offset="100%" stop-color="#74B9F5" />
            </linearGradient>
            <filter id="glow" x="-80%" y="-80%" width="260%" height="260%">
              <feGaussianBlur stdDeviation="6" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          <!-- 底层淡色弧线 -->
          <path
            class="rc-curve-bg"
            d="M 120 110 C 200 175 215 195 270 255 C 320 315 195 355 130 400 C 70 445 205 495 280 545"
          />
          <!-- 渐变高亮弧线（随进度描边） -->
          <path
            class="rc-curve-fg"
            d="M 120 110 C 200 175 215 195 270 255 C 320 315 195 355 130 400 C 70 445 205 495 280 545"
            ref="curvePath"
          />

          <!-- 游走光点 -->
          <circle
            class="rc-traveler"
            :fill="cur.color"
            :cx="travelerPos.x"
            :cy="travelerPos.y"
            r="7"
            stroke="#fff"
            stroke-width="2"
            filter="url(#glow)"
          />

          <!-- 节点 -->
          <g
            v-for="(n, i) in nodes"
            :key="n.type"
            class="rc-node-g"
            :class="{ active: i === active }"
            @click="switchTo(i)"
          >
            <circle class="rc-node-ring" :cx="n.x" :cy="n.y" r="30" :style="{ '--c': n.color }" />
            <circle class="rc-node-core" :cx="n.x" :cy="n.y" r="22" :style="{ fill: n.color }" />
            <text class="rc-node-emoji" :x="n.x" :y="n.y + 7" text-anchor="middle">
              {{ n.emoji }}
            </text>
            <text
              class="rc-node-label"
              :x="n.x + (i % 2 ? 44 : -44)"
              :y="n.y + 5"
              :text-anchor="i % 2 ? 'start' : 'end'"
            >
              {{ n.label }}
            </text>
          </g>
        </svg>

        <div class="rc-hint">
          <el-icon><Sort /> </el-icon>
          <span>滚轮 / 点击节点切换</span>
        </div>
      </div>
    </aside>

    <!-- 右侧内容面板 -->
    <main class="rc-content">
      <Transition name="swap" mode="out-in">
        <div
          class="rc-panel"
          :key="active"
          :style="{ '--accent': cur.color, '--bg': `url(${cur.bg})` }"
        >
          <!-- 分类背景图（四个分类各不相同） -->
          <div class="rc-panel-bg"></div>
          <div class="rc-panel-inner">
            <header class="rc-panel-head">
              <div class="rc-title-box">
                <span class="rc-badge">{{ active + 1 }} / 4</span>
                <h2 class="rc-title">
                  <span class="rc-title-emoji">{{ cur.emoji }}</span>
                  {{ cur.label }}
                </h2>
                <p class="rc-desc">{{ cur.desc }}</p>
                <div class="rc-feats">
                  <span v-for="f in cur.features" :key="f" class="rc-feat">{{ f }}</span>
                </div>
              </div>
              <div class="rc-stat-box">
                <div class="rc-stat-item">
                  <span class="num">{{ stats.total }}</span>
                  <span class="lbl">资源总数</span>
                </div>
                <div class="rc-stat-item ok">
                  <span class="num">{{ stats.available }}</span>
                  <span class="lbl">当前可约</span>
                </div>
                <div class="rc-stat-item danger">
                  <span class="num">{{ stats.busy }}</span>
                  <span class="lbl">占用中</span>
                </div>
                <div class="rc-stat-item warn">
                  <span class="num">{{ stats.repair }}</span>
                  <span class="lbl">维修中</span>
                </div>
              </div>
            </header>

            <!-- 我的预约概览 -->
            <div class="rc-mine">
              <span class="mine-title">我的预约</span>
              <span class="mine-item"><i class="d warn"></i>待打卡 {{ mine.pending }}</span>
              <span class="mine-item"><i class="d danger"></i>使用中 {{ mine.inUse }}</span>
              <span class="mine-item"><i class="d gray"></i>已结束 {{ mine.done }}</span>
              <el-button link type="primary" class="mine-more" @click="goMine">
                查看全部 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>

            <!-- 资源预览：圆点为"当前是否被占用" -->
            <div class="rc-preview-head">
              <span class="ph-title">资源速览</span>
              <span class="ph-legend">
                <i class="d ok"></i>空闲 <i class="d danger"></i>占用
                <i class="d warn"></i>维修/报修
              </span>
            </div>
            <div class="rc-preview" v-loading="loading">
              <div
                v-for="r in preview?.records || []"
                :key="r.id"
                class="rc-chip"
                @click="goDetail(r.id)"
              >
                <span class="dot" :style="{ background: chipColor(r) }"></span>
                <span class="name">{{ r.name }}</span>
                <span class="meta">{{ r.building }} {{ r.floor }}</span>
              </div>
              <div v-if="!loading && !preview?.records?.length" class="rc-empty">暂无资源</div>
            </div>

            <div class="rc-actions">
              <el-button type="primary" round @click="goCategory(cur.type)">
                查看全部 {{ cur.label }} <el-icon style="margin-left: 4px"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </Transition>
    </main>

    <!-- 资源速览：点开详情用抽屉，不离开首页也不打乱当前分类 -->
    <el-drawer
      v-model="detailVisible"
      :size="detailDrawerSize"
      :destroy-on-close="true"
      class="detail-drawer"
      @closed="detailId = null"
    >
      <template #header>
        <div class="drawer-header">
          <span class="drawer-title">资源详情</span>
          <span class="drawer-hint">关闭后回到当前分类</span>
        </div>
      </template>
      <ResourceDetailPanel v-if="detailId" :resource-id="detailId" @close="detailVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Sort } from '@element-plus/icons-vue'
import { api } from '@/api'
import ResourceDetailPanel from '@/components/ResourceDetailPanel.vue'
import { readLastCategory, saveLastCategory } from '@/utils/lastCategory'

const router = useRouter()

const cats = [
  {
    type: 1,
    label: '教室',
    emoji: '🏫',
    color: '#144E9C',
    bg: '/assets/type-classroom.png',
    pageBg: '/assets/pagebg-1.jpg',
    desc: '覆盖 4 个校区·27 栋教学楼，共 820 间真实教室，课表实时联动。',
    features: ['课表联动', '无需预约', '空闲可直接使用']
  },
  {
    type: 2,
    label: '实验室',
    emoji: '🧪',
    color: '#2E76DB',
    bg: '/assets/type-lab.png',
    pageBg: '/assets/pagebg-2.jpg',
    desc: '3 个校区·14 栋实验楼，共 155 间实验室，按真实排课展示占用。',
    features: ['限 10 人小组', '按时段独占', '定位打卡']
  },
  {
    type: 3,
    label: '图书馆',
    emoji: '📚',
    color: '#4E9BF0',
    bg: '/assets/type-library.png',
    pageBg: '/assets/pagebg-3.jpg',
    desc: '6F / 7F 多媒体自习室，每层 60 个座位，可缩放的座位图按位预约。',
    features: ['6F / 7F 座位图', '单人单座', '隔板独立工位']
  },
  {
    type: 4,
    label: '体育馆',
    emoji: '🏐',
    color: '#74B9F5',
    bg: '/assets/gym-basketball.png',
    pageBg: '/assets/pagebg-4.jpg',
    desc: '4 个校区·17 处场馆，共 368 个场地，容量与剩余实时可见。',
    features: ['多人同时预约', '容量实时校验', '剩余数量可见']
  }
]

// 弧线上 4 个节点的坐标（与 SVG path 对应）
const nodes = [
  { type: 1, x: 120, y: 110, label: '教室', emoji: '🏫', color: '#144E9C' },
  { type: 2, x: 270, y: 255, label: '实验室', emoji: '🧪', color: '#2E76DB' },
  { type: 3, x: 130, y: 400, label: '图书馆', emoji: '📚', color: '#4E9BF0' },
  { type: 4, x: 280, y: 545, label: '体育馆', emoji: '🏐', color: '#74B9F5' }
]

// 首屏直接显示上次浏览的分类（从资源列表返回时不会又跳回教室）
const initialIndex = Math.max(
  0,
  cats.findIndex((c) => c.type === readLastCategory())
)
const active = ref(initialIndex)
const cur = computed(() => cats[active.value])
const preview = ref(null)
const loading = ref(false)
const animating = ref(false)

// 当前分类的资源统计（总数 / 可约 / 占用 / 维修）
const stats = reactive({ total: 0, available: 0, busy: 0, repair: 0 })
// 我的预约概览
const mine = reactive({ pending: 0, inUse: 0, done: 0 })

const rootEl = ref(null)
// 光点坐标用响应式：首帧就落在上次的分类上，不会先闪一下教室
const travelerPos = reactive({ x: nodes[initialIndex].x, y: nodes[initialIndex].y })
let raf = null

const previews = reactive({})

// 资源圆点颜色：维修/报修=黄、占用=红、空闲=绿（教室看"是否正在上课"）
function chipColor(r) {
  if (r.status === 3 || r.status === 4) return '#e6a23c'
  if (r.resourceType === 1) return r.courseBusy ? '#f56c6c' : '#67c23a'
  return r.reserved ? '#f56c6c' : '#67c23a'
}

async function loadPreview(type) {
  if (previews[type]) return
  const res = await api.getResourcePage({ current: 1, size: 1000, type })
  if (res.code === 200) {
    const recs = res.data.records || []
    previews[type] = {
      total: res.data.total || recs.length,
      records: recs.slice(0, 8),
      available: recs.filter((r) => r.status === 1 && !r.reserved && !r.courseBusy).length,
      busy: recs.filter(
        (r) => r.status !== 3 && r.status !== 4 && (r.reserved || r.courseBusy || r.status === 2)
      ).length,
      repair: recs.filter((r) => r.status === 3 || r.status === 4).length
    }
  }
}

async function loadMine() {
  const res = await api.getMyReservations({ current: 1, size: 100 })
  if (res.code === 200) {
    const recs = res.data?.records || []
    mine.pending = recs.filter((r) => r.status === 1).length
    mine.inUse = recs.filter((r) => r.status === 2).length
    mine.done = recs.filter((r) => r.status === 3).length
  }
}

async function showPreview(i) {
  loading.value = true
  preview.value = previews[cats[i].type] || null
  await loadPreview(cats[i].type)
  preview.value = previews[cats[i].type]
  Object.assign(stats, {
    total: preview.value?.total || 0,
    available: preview.value?.available || 0,
    busy: preview.value?.busy || 0,
    repair: preview.value?.repair || 0
  })
  loading.value = false
}

function switchTo(i) {
  if (i === active.value || i < 0 || i >= cats.length || animating.value) return
  active.value = i
  saveLastCategory(cats[i].type)
  animating.value = true
  animateTraveler(nodes[i].x, nodes[i].y)
  showPreview(i)
  setTimeout(() => {
    animating.value = false
  }, 650)
}

function animateTraveler(tx, ty) {
  cancelAnimationFrame(raf)
  const step = () => {
    travelerPos.x += (tx - travelerPos.x) * 0.16
    travelerPos.y += (ty - travelerPos.y) * 0.16
    if (Math.abs(tx - travelerPos.x) > 0.4 || Math.abs(ty - travelerPos.y) > 0.4) {
      raf = requestAnimationFrame(step)
    } else {
      travelerPos.x = tx
      travelerPos.y = ty
    }
  }
  step()
}

function onWheel(e) {
  if (animating.value) {
    e.preventDefault()
    return
  }
  if (Math.abs(e.deltaY) < 6) return
  e.preventDefault()
  switchTo(active.value + (e.deltaY > 0 ? 1 : -1))
}
function onKey(e) {
  if (e.key === 'ArrowDown' || e.key === 'ArrowRight') switchTo(active.value + 1)
  else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') switchTo(active.value - 1)
}

function goCategory(type) {
  router.push(`/category/${type}`)
}
function goDetail(id) {
  // 详情用侧边抽屉展示，留在首页
  detailId.value = id
  detailVisible.value = true
}

const detailVisible = ref(false)
const detailId = ref(null)
// 宽屏固定 760px，窄屏自适应留边
const detailDrawerSize = 'min(760px, 94vw)'
function goMine() {
  router.push('/my-reservations')
}

onMounted(async () => {
  rootEl.value?.focus()
  // 非被动监听，确保 preventDefault 生效
  rootEl.value?.addEventListener('wheel', onWheel, { passive: false })
  loadMine()
  await Promise.all(cats.map((c) => loadPreview(c.type)))
  await showPreview(active.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  rootEl.value?.removeEventListener('wheel', onWheel)
})
</script>

<style scoped lang="scss">
.rc-root {
  position: relative;
  display: flex;
  gap: 8px;
  height: calc(100vh - 70px);
  min-height: 560px;
  padding: 18px 6px 18px 0;
  box-sizing: border-box;
  overflow: hidden;
  border-radius: 22px;
  background:
    radial-gradient(1100px 560px at 80% 15%, rgba(46, 118, 219, 0.05), transparent 60%),
    radial-gradient(900px 560px at 8% 88%, rgba(116, 185, 245, 0.06), transparent 60%),
    linear-gradient(135deg, #fbfcfe 0%, #f5f8fd 55%, #eef4fd 100%);
  outline: none;
  color: #303133;
}

/* 页面级氛围背景图：左侧导航区较明显，向右淡出（蓝色底已调淡，避免盖住照片） */
.rc-pagebg {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image: var(--pagebg);
  background-size: cover;
  background-position: center;
  opacity: 0.22;
  pointer-events: none;
  mask-image: linear-gradient(90deg, #000 0%, #000 34%, rgba(0, 0, 0, 0.45) 58%, transparent 82%);
  -webkit-mask-image: linear-gradient(
    90deg,
    #000 0%,
    #000 34%,
    rgba(0, 0, 0, 0.45) 58%,
    transparent 82%
  );
}

/* ---------- 左侧弧线导航 ---------- */
.rc-rail {
  flex: 0 0 40%;
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rc-rail-inner {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rc-svg {
  width: 100%;
  height: 100%;
  max-height: 640px;
  overflow: visible;
}
.rc-curve-bg {
  fill: none;
  stroke: rgba(48, 49, 51, 0.08);
  stroke-width: 6;
  stroke-linecap: round;
}
.rc-curve-fg {
  fill: none;
  stroke: url(#curveGrad);
  stroke-width: 6;
  stroke-linecap: round;
  stroke-dasharray: 1400;
  stroke-dashoffset: 0;
  transition: stroke-dashoffset 0.65s cubic-bezier(0.22, 1, 0.36, 1);
  filter: drop-shadow(0 2px 6px rgba(46, 118, 219, 0.28));
}
.rc-traveler {
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.18));
}

.rc-node-g {
  cursor: pointer;
}
.rc-node-ring {
  fill: #fff;
  stroke: #e4e7ed;
  stroke-width: 2;
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.rc-node-core {
  transition:
    r 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.5s,
    opacity 0.5s;
  transform-origin: center;
  opacity: 0.35;
  filter: saturate(0.5);
}
.rc-node-emoji {
  font-size: 22px;
  pointer-events: none;
  opacity: 0.55;
  transition:
    opacity 0.5s,
    font-size 0.5s;
}
.rc-node-label {
  fill: #909399;
  font-size: 16px;
  font-weight: 600;
  pointer-events: none;
  transition:
    fill 0.5s,
    font-size 0.5s;
}

.rc-node-g.active .rc-node-ring {
  r: 33;
  stroke: var(--c);
  stroke-width: 3;
  fill: #fff;
  filter: drop-shadow(0 4px 14px rgba(0, 0, 0, 0.12));
}
.rc-node-g.active .rc-node-core {
  opacity: 1;
  filter: saturate(1.05) drop-shadow(0 2px 8px var(--c));
}
.rc-node-g.active .rc-node-emoji {
  opacity: 1;
  font-size: 24px;
}
.rc-node-g.active .rc-node-label {
  fill: #303133;
  font-size: 18px;
}

.rc-hint {
  position: absolute;
  bottom: 18px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #c0c4cc;
  white-space: nowrap;
}

/* ---------- 右侧内容 ---------- */
.rc-content {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  padding: 18px 28px 18px 0;
}
.rc-panel {
  position: relative;
  width: 100%;
  background: #fff;
  border: 1px solid #dbe6f5;
  border-radius: 20px;
  padding: 30px 32px;
  overflow: hidden;
  box-shadow: 0 8px 30px rgba(30, 98, 208, 0.1);
}

/* 右侧大面积淡背景图（四个分类各不相同） */
.rc-panel-bg {
  position: absolute;
  inset: 0;
  background-image: var(--bg);
  background-size: cover;
  background-position: center;
  opacity: 0.1;
  pointer-events: none;
}
.rc-panel-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.55) 0%,
    rgba(255, 255, 255, 0.92) 55%,
    #fff 100%
  );
}
.rc-panel-inner {
  position: relative;
  z-index: 1;
}

.rc-panel-head {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(30, 98, 208, 0.1);
}

/* 标题区右侧露出分类实景图 */
.rc-panel-head::after {
  content: '';
  position: absolute;
  right: -34px;
  top: -30px;
  width: 220px;
  height: 118px;
  background-image: var(--bg);
  background-size: cover;
  background-position: center;
  border-radius: 0 20px 0 90px;
  opacity: 0.5;
  mask-image: linear-gradient(90deg, transparent 0%, #000 60%);
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, #000 60%);
  pointer-events: none;
}
.rc-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: #f5f7fa;
  border: 1px solid var(--accent);
  margin-bottom: 10px;
}
.rc-title {
  font-size: 30px;
  font-weight: 800;
  margin: 0 0 8px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 10px;
}
.rc-title-emoji {
  font-size: 30px;
}
.rc-desc {
  font-size: 14px;
  line-height: 1.7;
  color: #909399;
  margin: 0;
  max-width: 520px;
}
.rc-stat-box {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 10px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.rc-stat-item {
  min-width: 74px;
  text-align: center;
  padding: 8px 12px;
  border-radius: 12px;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  .num {
    display: block;
    font-size: 22px;
    font-weight: 800;
    color: #303133;
  }
  .lbl {
    font-size: 11px;
    color: #909399;
  }
  &.ok .num {
    color: #67c23a;
  }
  &.danger .num {
    color: #f56c6c;
  }
  &.warn .num {
    color: #e6a23c;
  }
}

/* 分类特色标签 */
.rc-feats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  .rc-feat {
    font-size: 12px;
    color: var(--el-color-primary);
    background: rgba(30, 98, 208, 0.1);
    padding: 3px 10px;
    border-radius: 20px;
  }
}

/* 我的预约概览 */
.rc-mine {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 16px;
  margin-bottom: 14px;
  border-radius: 12px;
  background: #f7f9fc;
  border: 1px solid #eef2f7;
  .mine-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }
  .mine-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #606266;
  }
  .mine-more {
    margin-left: auto;
  }
  .d {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    display: inline-block;
    &.ok {
      background: #67c23a;
    }
    &.danger {
      background: #f56c6c;
    }
    &.warn {
      background: #e6a23c;
    }
    &.gray {
      background: #c0c4cc;
    }
  }
}

/* 资源速览标题与图例 */
.rc-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 2px 8px;
  .ph-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }
  .ph-legend {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 12px;
    color: #909399;
    .d {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      display: inline-block;
      margin-right: 4px;
      vertical-align: middle;
      &.ok {
        background: #67c23a;
      }
      &.danger {
        background: #f56c6c;
      }
      &.warn {
        background: #e6a23c;
      }
    }
  }
}

.rc-preview {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  min-height: 168px;
  margin-bottom: 22px;
}
.rc-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.25s;
  &:hover {
    background: #f5f7fa;
    border-color: var(--accent);
    transform: translateX(4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }
  .name {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .meta {
    font-size: 11px;
    color: #c0c4cc;
  }
}
.rc-empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
}

.rc-actions {
  display: flex;
  justify-content: flex-end;
}

/* ---------- 切换过渡 ---------- */
.swap-enter-active,
.swap-leave-active {
  transition:
    opacity 0.45s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.swap-enter-from {
  opacity: 0;
  transform: translateX(48px) scale(0.985);
}
.swap-leave-to {
  opacity: 0;
  transform: translateX(-48px) scale(0.985);
}

/* 响应式 */
@media (max-width: 900px) {
  .rc-root {
    flex-direction: column;
    height: auto;
    min-height: 0;
  }
  .rc-rail {
    flex: 0 0 auto;
    height: 320px;
    width: 100%;
  }
  .rc-content {
    padding: 8px 14px 18px;
  }
  .rc-panel {
    padding: 22px 18px;
  }
  .rc-panel-head {
    flex-direction: column;
  }
  .rc-preview {
    grid-template-columns: 1fr;
  }
}
</style>
