<template>
  <div class="rc-root" ref="rootEl" tabindex="0" @keydown="onKey">
    <!-- 左侧弧线导航 -->
    <aside class="rc-rail">
      <div class="rc-rail-inner">
        <svg class="rc-svg" viewBox="0 0 380 640" preserveAspectRatio="xMidYMid meet">
          <defs>
            <linearGradient id="curveGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#5B9DFF" />
              <stop offset="33%" stop-color="#22D3A4" />
              <stop offset="66%" stop-color="#F5B547" />
              <stop offset="100%" stop-color="#FF6B6B" />
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
          <path class="rc-curve-bg" d="M 120 110 C 200 175 215 195 270 255 C 320 315 195 355 130 400 C 70 445 205 495 280 545" />
          <!-- 渐变高亮弧线（随进度描边） -->
          <path
            class="rc-curve-fg"
            d="M 120 110 C 200 175 215 195 270 255 C 320 315 195 355 130 400 C 70 445 205 495 280 545"
            ref="curvePath"
          />

          <!-- 游走光点 -->
        <circle ref="traveler" class="rc-traveler" :fill="cur.color" cx="120" cy="110" r="7" stroke="#fff" stroke-width="2" filter="url(#glow)" />

          <!-- 节点 -->
          <g
            v-for="(n, i) in nodes"
            :key="n.type"
            class="rc-node-g"
            :class="{ active: i === active }"
            @click="switchTo(i)"
          >
            <circle
              class="rc-node-ring"
              :cx="n.x" :cy="n.y" r="30"
              :style="{ '--c': n.color }"
            />
            <circle
              class="rc-node-core"
              :cx="n.x" :cy="n.y" r="22"
              :style="{ fill: n.color }"
            />
            <text class="rc-node-emoji" :x="n.x" :y="n.y + 7" text-anchor="middle">{{ n.emoji }}</text>
            <text class="rc-node-label" :x="n.x + (i % 2 ? 44 : -44)" :y="n.y + 5" :text-anchor="i % 2 ? 'start' : 'end'">{{ n.label }}</text>
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
        <div class="rc-panel" :key="active" :style="{ '--accent': cur.color }">
          <header class="rc-panel-head">
            <div class="rc-title-box">
              <span class="rc-badge">{{ active + 1 }} / 4</span>
              <h2 class="rc-title">
                <span class="rc-title-emoji">{{ cur.emoji }}</span>
                {{ cur.label }}
              </h2>
              <p class="rc-desc">{{ cur.desc }}</p>
            </div>
            <div class="rc-stat-box">
              <div class="rc-stat-item">
                <span class="num">{{ preview?.total ?? 0 }}</span>
                <span class="lbl">资源总数</span>
              </div>
              <div class="rc-stat-item alt">
                <span class="num">{{ cur.tag }}</span>
                <span class="lbl">{{ cur.tagUnit }}</span>
              </div>
            </div>
          </header>

          <div class="rc-preview" v-loading="loading">
            <div
              v-for="r in (preview?.records || [])"
              :key="r.id"
              class="rc-chip"
              @click="goDetail(r.id)"
            >
              <span class="dot" :style="{ background: cur.color }"></span>
              <span class="name">{{ r.name }}</span>
              <span class="meta">{{ r.building }} {{ r.floor }}</span>
            </div>
            <div v-if="!loading && !preview?.records?.length" class="rc-empty">暂无资源</div>
          </div>

          <div class="rc-actions">
            <el-button type="primary" round @click="goCategory(cur.type)">
              查看全部 {{ cur.label }} <el-icon style="margin-left:4px"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </Transition>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Sort } from '@element-plus/icons-vue'
import { api } from '@/api'

const router = useRouter()

const cats = [
  {
    type: 1, label: '教室', emoji: '🏫', color: '#5B9DFF',
    desc: '教学楼 A/B 栋智慧教室，配备多媒体投影，适合课程教学与小组讨论。',
    tag: '120', tagUnit: '分钟/次'
  },
  {
    type: 2, label: '实验室', emoji: '🧪', color: '#22D3A4',
    desc: '实验楼 6 层，物理 / 化学 / 生物 / 材料 / 计算机 / 机电六大实验室。',
    tag: '180', tagUnit: '分钟/次'
  },
  {
    type: 3, label: '图书馆', emoji: '📚', color: '#F5B547',
    desc: '三楼自习区、四楼研讨室、二楼阅览区，安静宽敞的学习空间。',
    tag: '240', tagUnit: '分钟/次'
  },
  {
    type: 4, label: '体育馆', emoji: '🏐', color: '#FF6B6B',
    desc: 'A 栋游泳馆 / 乒乓球馆，B 栋篮球 / 羽毛球 / 网球 / 排球场地。',
    tag: '90', tagUnit: '分钟/次'
  }
]

// 弧线上 4 个节点的坐标（与 SVG path 对应）
const nodes = [
  { type: 1, x: 120, y: 110, label: '教室', emoji: '🏫', color: '#5B9DFF' },
  { type: 2, x: 270, y: 255, label: '实验室', emoji: '🧪', color: '#22D3A4' },
  { type: 3, x: 130, y: 400, label: '图书馆', emoji: '📚', color: '#F5B547' },
  { type: 4, x: 280, y: 545, label: '体育馆', emoji: '🏐', color: '#FF6B6B' }
]

const active = ref(0)
const cur = computed(() => cats[active.value])
const preview = ref(null)
const loading = ref(false)
const animating = ref(false)

const rootEl = ref(null)
const traveler = ref(null)
let raf = null

const previews = reactive({})

async function loadPreview(type) {
  if (previews[type]) return
  const res = await api.getResourcePage({ current: 1, size: 8, type })
  if (res.code === 200) previews[type] = res.data
}

async function showPreview(i) {
  loading.value = true
  preview.value = previews[cats[i].type] || null
  await loadPreview(cats[i].type)
  preview.value = previews[cats[i].type]
  loading.value = false
}

function switchTo(i) {
  if (i === active.value || i < 0 || i >= cats.length || animating.value) return
  active.value = i
  animating.value = true
  animateTraveler(nodes[i].x, nodes[i].y)
  showPreview(i)
  setTimeout(() => { animating.value = false }, 650)
}

function animateTraveler(tx, ty) {
  cancelAnimationFrame(raf)
  const dot = traveler.value
  if (!dot) return
  let x = parseFloat(dot.getAttribute('cx'))
  let y = parseFloat(dot.getAttribute('cy'))
  const step = () => {
    x += (tx - x) * 0.16
    y += (ty - y) * 0.16
    dot.setAttribute('cx', x.toFixed(2))
    dot.setAttribute('cy', y.toFixed(2))
    if (Math.abs(tx - x) > 0.4 || Math.abs(ty - y) > 0.4) {
      raf = requestAnimationFrame(step)
    } else {
      dot.setAttribute('cx', tx)
      dot.setAttribute('cy', ty)
    }
  }
  step()
}

function onWheel(e) {
  if (animating.value) { e.preventDefault(); return }
  if (Math.abs(e.deltaY) < 6) return
  e.preventDefault()
  switchTo(active.value + (e.deltaY > 0 ? 1 : -1))
}
function onKey(e) {
  if (e.key === 'ArrowDown' || e.key === 'ArrowRight') switchTo(active.value + 1)
  else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') switchTo(active.value - 1)
}

function goCategory(type) { router.push(`/category/${type}`) }
function goDetail(id) { router.push(`/resource/${id}`) }

onMounted(async () => {
  rootEl.value?.focus()
  // 非被动监听，确保 preventDefault 生效
  rootEl.value?.addEventListener('wheel', onWheel, { passive: false })
  await Promise.all(cats.map(c => loadPreview(c.type)))
  await showPreview(0)
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
    radial-gradient(1100px 560px at 80% 15%, rgba(91, 157, 255, 0.08), transparent 60%),
    radial-gradient(900px 560px at 8% 88%, rgba(255, 107, 107, 0.07), transparent 60%),
    linear-gradient(135deg, #f7f9fc 0%, #eef2f8 55%, #f3f0fa 100%);
  outline: none;
  color: #303133;
}

/* ---------- 左侧弧线导航 ---------- */
.rc-rail {
  flex: 0 0 40%;
  position: relative;
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
  filter: drop-shadow(0 2px 6px rgba(91, 157, 255, 0.25));
}
.rc-traveler {
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.18));
}

.rc-node-g { cursor: pointer; }
.rc-node-ring {
  fill: #fff;
  stroke: #e4e7ed;
  stroke-width: 2;
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.rc-node-core {
  transition: r 0.5s cubic-bezier(0.22, 1, 0.36, 1), filter 0.5s, opacity 0.5s;
  transform-origin: center;
  opacity: 0.35;
  filter: saturate(0.5);
}
.rc-node-emoji {
  font-size: 22px;
  pointer-events: none;
  opacity: 0.55;
  transition: opacity 0.5s, font-size 0.5s;
}
.rc-node-label {
  fill: #909399;
  font-size: 16px;
  font-weight: 600;
  pointer-events: none;
  transition: fill 0.5s, font-size 0.5s;
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
.rc-node-g.active .rc-node-emoji { opacity: 1; font-size: 24px; }
.rc-node-g.active .rc-node-label { fill: #303133; font-size: 18px; }

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
  display: flex;
  align-items: center;
  padding: 18px 28px 18px 0;
}
.rc-panel {
  width: 100%;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 20px;
  padding: 30px 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

.rc-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 22px;
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
.rc-title-emoji { font-size: 30px; }
.rc-desc {
  font-size: 14px;
  line-height: 1.7;
  color: #909399;
  margin: 0;
  max-width: 520px;
}
.rc-stat-box { display: flex; gap: 14px; flex-shrink: 0; }
.rc-stat-item {
  text-align: center;
  padding: 8px 16px;
  border-radius: 12px;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  .num { display: block; font-size: 22px; font-weight: 800; color: #303133; }
  .lbl { font-size: 11px; color: #909399; }
}
.rc-stat-item.alt .num { color: var(--accent); }

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
  .dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
  .name { font-size: 13px; font-weight: 600; color: #303133; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .meta { font-size: 11px; color: #c0c4cc; }
}
.rc-empty { grid-column: 1 / -1; text-align: center; color: #c0c4cc; padding: 40px 0; }

.rc-actions { display: flex; justify-content: flex-end; }

/* ---------- 切换过渡 ---------- */
.swap-enter-active, .swap-leave-active {
  transition: opacity 0.45s cubic-bezier(0.22, 1, 0.36, 1), transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.swap-enter-from { opacity: 0; transform: translateX(48px) scale(0.985); }
.swap-leave-to { opacity: 0; transform: translateX(-48px) scale(0.985); }

/* 响应式 */
@media (max-width: 900px) {
  .rc-root { flex-direction: column; height: auto; min-height: 0; }
  .rc-rail { flex: 0 0 auto; height: 320px; width: 100%; }
  .rc-content { padding: 8px 14px 18px; }
  .rc-panel { padding: 22px 18px; }
  .rc-panel-head { flex-direction: column; }
  .rc-preview { grid-template-columns: 1fr; }
}
</style>
