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
              <!-- 分类实景图：独占一列，不与统计数字重叠 -->
              <div class="rc-head-art"></div>
            </header>

            <!-- 统计整行均分，数字与标签同行 -->
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

            <!-- 该类资源的真实使用规则，代替原来意义不大的随机速览 -->
            <div class="rc-meta-row">
              <span class="rc-meta">
                <el-icon><Clock /></el-icon>开放 {{ cur.open }}
              </span>
              <span class="rc-meta">
                <el-icon><Calendar /></el-icon>{{ cur.advance }}
              </span>
              <span class="rc-meta">
                <el-icon><InfoFilled /></el-icon>{{ cur.rule }}
              </span>
            </div>

            <div class="rc-actions">
              <span class="rc-actions-tip">{{ cur.tip }}</span>
              <el-button type="primary" round @click="goCategory(cur.type)">
                查看全部 {{ cur.label }}
                <el-icon style="margin-left: 4px"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </Transition>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Sort, Clock, Calendar, InfoFilled } from '@element-plus/icons-vue'
import { api } from '@/api'
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
    desc: '覆盖 4 个校区·44 栋教学楼，共 1016 间真实教室，课表按教学周联动。',
    features: ['课表联动', '无需预约', '空闲可直接使用'],
    open: '08:00 - 22:00',
    advance: '无需预约，课程空档可直接使用',
    rule: '排课时段自动占用，不参与预约',
    tip: '想知道哪间教室哪节课空着？进去按日期和时段查'
  },
  {
    type: 2,
    label: '实验室',
    emoji: '🧪',
    color: '#2E76DB',
    bg: '/assets/type-lab.png',
    pageBg: '/assets/pagebg-2.jpg',
    desc: '3 个校区·41 栋实验楼，共 394 间实验室，按真实排课展示占用。',
    features: ['按容量预约', '按时段独占', '定位打卡'],
    open: '08:00 - 22:00',
    advance: '可预约未来 7 天内的时段',
    rule: '按时段独占，到点需定位打卡',
    tip: '先选时段看哪些实验室空着，再按容量预约'
  },
  {
    type: 3,
    label: '图书馆',
    emoji: '📚',
    color: '#4E9BF0',
    bg: '/assets/type-library.png',
    pageBg: '/assets/pagebg-3.jpg',
    desc: '6F / 7F 多媒体自习室，每层 60 个座位，可缩放的座位图按位预约。',
    features: ['6F / 7F 座位图', '单人单座', '隔板独立工位'],
    open: '08:00 - 22:00',
    advance: '仅可预约次日座位',
    rule: '一人一座，到点需定位打卡',
    tip: '在座位图上点选座位，预约后记得按时打卡'
  },
  {
    type: 4,
    label: '体育馆',
    emoji: '🏐',
    color: '#74B9F5',
    bg: '/assets/gym-basketball.png',
    pageBg: '/assets/pagebg-4.jpg',
    desc: '4 个校区·17 处场馆，共 368 个场地，容量与剩余实时可见。',
    features: ['多人同时预约', '容量实时校验', '剩余数量可见'],
    open: '09:00 - 21:00',
    advance: '可预约未来 7 天内的时段',
    rule: '多人共享同一时段，按剩余容量叠加',
    tip: '先选时段看剩余多少场地，再决定约几块'
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
const animating = ref(false)

// 当前分类的资源统计（总数 / 可约 / 占用 / 维修）
const stats = reactive({ total: 0, available: 0, busy: 0, repair: 0 })
// 按分类缓存统计结果，切换分类不重复拉取
const statsCache = reactive({})

const rootEl = ref(null)
// 光点坐标用响应式：首帧就落在上次的分类上，不会先闪一下教室
const travelerPos = reactive({ x: nodes[initialIndex].x, y: nodes[initialIndex].y })
let raf = null

async function loadStats(type) {
  if (statsCache[type]) return
  // 一次拉全量（教室 1016 间），统计数字才与「资源总数」对得上
  const res = await api.getResourcePage({ current: 1, size: 2000, type })
  if (res.code !== 200) return
  const recs = res.data.records || []
  statsCache[type] = {
    total: res.data.total || recs.length,
    available: recs.filter((r) => r.status === 1 && !r.reserved && !r.courseBusy).length,
    busy: recs.filter(
      (r) => r.status !== 3 && r.status !== 4 && (r.reserved || r.courseBusy || r.status === 2)
    ).length,
    repair: recs.filter((r) => r.status === 3 || r.status === 4).length
  }
}

async function applyStats(i) {
  await loadStats(cats[i].type)
  Object.assign(stats, statsCache[cats[i].type] || { total: 0, available: 0, busy: 0, repair: 0 })
}

function switchTo(i) {
  if (i === active.value || i < 0 || i >= cats.length || animating.value) return
  active.value = i
  saveLastCategory(cats[i].type)
  animating.value = true
  animateTraveler(nodes[i].x, nodes[i].y)
  applyStats(i)
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
onMounted(async () => {
  rootEl.value?.focus()
  // 非被动监听，确保 preventDefault 生效
  rootEl.value?.addEventListener('wheel', onWheel, { passive: false })
  await Promise.all(cats.map((c) => loadStats(c.type)))
  await applyStats(active.value)
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
  display: flex;
  width: 100%;
  min-height: 470px;
  background: #fff;
  border: 1px solid #dbe6f5;
  border-radius: 20px;
  padding: 28px 32px 24px;
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
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
}

.rc-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(30, 98, 208, 0.08);
}

/* 分类实景图：独占一列，不再压住统计数字 */
.rc-head-art {
  flex: 0 0 300px;
  height: 176px;
  border-radius: 16px;
  background-image: var(--bg);
  background-size: cover;
  background-position: center;
  box-shadow: 0 10px 24px rgba(30, 98, 208, 0.16);
}

.rc-title-box {
  min-width: 0;
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
/* 统计整行均分，数字与标签同行 */
.rc-stat-box {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.rc-stat-item {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  padding: 14px 8px;
  border-radius: 14px;
  background: #f6f9fd;
  border: 1px solid #e8eff9;
  .num {
    font-size: 26px;
    font-weight: 800;
    line-height: 1;
    color: #303133;
  }
  .lbl {
    font-size: 12px;
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

.rc-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  .rc-meta {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12.5px;
    color: #606266;
    .el-icon {
      color: var(--accent);
    }
  }
}

.rc-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: auto;
  .rc-actions-tip {
    font-size: 12px;
    color: #a8abb2;
  }
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
  .rc-panel {
    min-height: 0;
  }
  .rc-panel-head {
    flex-direction: column;
    gap: 16px;
  }
  .rc-head-art {
    flex: 0 0 auto;
    width: 100%;
    height: 140px;
  }
  .rc-stat-box {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .rc-actions {
    flex-wrap: wrap;
  }
}
</style>
