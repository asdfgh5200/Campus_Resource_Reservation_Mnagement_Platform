<template>
  <div class="seat-map-wrap">
    <!-- 工具栏：图例 + 缩放 -->
    <div class="seat-map-toolbar">
      <div class="legend">
        <span class="lg"><i class="dot free"></i>空闲可约</span>
        <span class="lg"><i class="dot mine"></i>我的预约</span>
        <span class="lg"><i class="dot taken"></i>他人占用</span>
        <span class="lg"><i class="dot repair"></i>维修中</span>
      </div>
      <div class="zoom-ctrl">
        <el-button size="small" :icon="ZoomOut" circle @click="zoomBy(1 / 1.25)" />
        <span class="zoom-val">{{ Math.round(scale * 100) }}%</span>
        <el-button size="small" :icon="ZoomIn" circle @click="zoomBy(1.25)" />
        <el-button size="small" @click="resetView">重置</el-button>
      </div>
    </div>

    <!-- 座位图 -->
    <div class="seat-map-canvas">
      <svg
        ref="svgRef"
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
        preserveAspectRatio="xMidYMid meet"
        @wheel.prevent="onWheel"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointerleave="onPointerUp"
      >
        <g :transform="`translate(${tx} ${ty}) scale(${scale})`">
          <!-- 房间底图 -->
          <rect class="floor-bg" x="6" y="6" :width="layout.width - 12" :height="layout.height - 12" rx="20" />

          <!-- 分区框与分区标题 -->
          <g v-for="(b, bi) in layout.blocks" :key="'b' + bi">
            <rect class="block-bg" :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="14" />
            <text class="block-title" :x="b.x + 14" :y="b.y + 23">{{ b.label }}</text>
          </g>

          <!-- 隔板 -->
          <rect
            v-for="(d, di) in layout.dividers"
            :key="'d' + di"
            class="divider"
            :x="d.x"
            :y="d.y"
            :width="d.w"
            :height="d.h"
            rx="1.5"
          />

          <!-- 座位（可旋转：研讨卡座上下两排面对面） -->
          <g
            v-for="s in seatList"
            :key="s.index"
            class="seat"
            :class="s.cls"
            :transform="`translate(${s.x} ${s.y}) rotate(${s.rot} ${s.size / 2} ${s.size / 2})`"
            @click="onSeatClick(s)"
          >
            <title>{{ s.tip }}</title>
            <!-- 桌面 -->
            <rect class="desk" x="3" y="4" :width="s.size - 6" height="20" rx="3" />
            <!-- 显示器 -->
            <rect class="monitor" :x="s.size / 2 - 9" y="7" width="18" height="13" rx="2" />
            <rect class="screen" :x="s.size / 2 - 7" y="9" width="14" height="9" rx="1" />
            <rect class="stand" :x="s.size / 2 - 3" y="19" width="6" height="4" rx="1" />
            <!-- 座椅 -->
            <rect class="chair" :x="s.size / 2 - 11" :y="s.size - 26" width="22" height="22" rx="7" />
            <!-- 占用小人 -->
            <g v-if="s.occupied" class="person">
              <circle :cx="s.size / 2" :cy="s.size - 20" r="4.4" />
              <path :d="`M ${s.size / 2 - 8.5} ${s.size - 4} a 8.5 9.5 0 0 1 17 0 z`" />
            </g>
          </g>

          <!-- 座位号（始终正向显示） -->
          <text
            v-for="s in seatList"
            :key="'n' + s.index"
            class="seat-no"
            :x="s.x + s.size / 2"
            :y="s.y + s.size / 2 + 3"
            text-anchor="middle"
          >{{ s.label }}</text>
        </g>
      </svg>
    </div>
    <p class="seat-hint">滚轮缩放，按住拖动可平移；点击座位进入预约详情</p>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { buildSeatLayout, seatRoomNo } from '@/utils/librarySeats'

const props = defineProps({
  floor: { type: String, required: true },
  resources: { type: Array, default: () => [] }
})
const emit = defineEmits(['select'])

const layout = computed(() => buildSeatLayout(props.floor))
const svgRef = ref(null)
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)

const resourceMap = computed(() => {
  const m = {}
  for (const r of props.resources) m[r.roomNo] = r
  return m
})

const seatList = computed(() => layout.value.seats.map((s) => {
  const roomNo = seatRoomNo(props.floor, s.index)
  const res = resourceMap.value[roomNo]
  const occupied = !!(res && res.reserved)
  const mine = !!(res && res.reserved && res.reservedByMe)
  const repair = !!(res && res.status === 3)
  let cls = 'free'
  let tip = `座位 ${roomNo}：当前空闲，可预约`
  if (repair) {
    cls = 'repair'
    tip = `座位 ${roomNo}：维修中`
  } else if (mine) {
    cls = 'mine'
    tip = `座位 ${roomNo}：我的预约`
  } else if (occupied) {
    cls = 'taken'
    tip = `座位 ${roomNo}：他人占用中`
  }
  return {
    ...s,
    label: String(s.index).padStart(2, '0'),
    resource: res,
    occupied,
    cls,
    tip
  }
}))

// ---- 缩放 ----
function zoomBy(factor) {
  const nk = clampScale(scale.value * factor)
  const f = nk / scale.value
  const cx = layout.value.width / 2
  const cy = layout.value.height / 2
  tx.value = cx - (cx - tx.value) * f
  ty.value = cy - (cy - ty.value) * f
  scale.value = nk
}
function clampScale(k) { return Math.min(3, Math.max(0.6, k)) }

function onWheel(e) {
  const rect = svgRef.value.getBoundingClientRect()
  const px = ((e.clientX - rect.left) / rect.width) * layout.value.width
  const py = ((e.clientY - rect.top) / rect.height) * layout.value.height
  const nk = clampScale(scale.value * (e.deltaY < 0 ? 1.12 : 1 / 1.12))
  const f = nk / scale.value
  tx.value = px - (px - tx.value) * f
  ty.value = py - (py - ty.value) * f
  scale.value = nk
}

function resetView() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

// ---- 拖动平移 ----
const drag = { active: false, x: 0, y: 0, tx: 0, ty: 0, moved: 0 }
let lastClickGuard = 0

function onPointerDown(e) {
  drag.active = true
  drag.x = e.clientX
  drag.y = e.clientY
  drag.tx = tx.value
  drag.ty = ty.value
  drag.moved = 0
}
function onPointerMove(e) {
  if (!drag.active) return
  const rect = svgRef.value.getBoundingClientRect()
  const dxPx = e.clientX - drag.x
  const dyPx = e.clientY - drag.y
  drag.moved = Math.max(drag.moved, Math.abs(dxPx) + Math.abs(dyPx))
  tx.value = drag.tx + (dxPx / rect.width) * layout.value.width
  ty.value = drag.ty + (dyPx / rect.height) * layout.value.height
}
function onPointerUp() {
  if (drag.moved > 4) lastClickGuard = Date.now()
  drag.active = false
}

function onSeatClick(s) {
  // 拖动结束后的误触忽略
  if (Date.now() - lastClickGuard < 120) return
  if (!s.resource) return
  emit('select', s.resource)
}
</script>

<style scoped lang="scss">
.seat-map-wrap { background: #fff; border-radius: 14px; padding: 14px 16px 10px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }

.seat-map-toolbar {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
  margin-bottom: 10px;
  .legend { display: flex; gap: 16px; flex-wrap: wrap;
    .lg { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #606266;
      .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block;
        &.free { background: #67c23a; }
        &.mine { background: #409eff; }
        &.taken { background: #f56c6c; }
        &.repair { background: #c0c4cc; }
      }
    }
  }
  .zoom-ctrl { display: flex; align-items: center; gap: 8px;
    .zoom-val { font-size: 12px; color: #909399; min-width: 40px; text-align: center; }
  }
}

.seat-map-canvas {
  width: 100%; height: 600px;
  background: linear-gradient(180deg, #f7f9fc 0%, #eef2f8 100%);
  border-radius: 12px;
  overflow: hidden;
  svg { width: 100%; height: 100%; cursor: grab; user-select: none;
    &:active { cursor: grabbing; }
  }
}

.floor-bg { fill: #ffffff; stroke: #e6ebf3; stroke-width: 2; }

.block-bg { fill: #f7f9fd; stroke: #e2e9f4; stroke-width: 1.5; stroke-dasharray: 7 5; }
.block-title { font-size: 13px; font-weight: 600; fill: #7b8aa0; letter-spacing: 0.5px; }

.divider { fill: #aab9cd; }

.seat {
  cursor: pointer;

  .desk { fill: #dbe4f0; stroke: #c3cfe0; stroke-width: 1; }
  .monitor { fill: #2c3e50; }
  .screen { fill: #6fa8dc; }
  .stand { fill: #9aa7b6; }
  .chair { fill: #cbd6e4; stroke: #b4c1d2; stroke-width: 1; transition: fill 0.2s; }

  &.free {
    .desk { fill: #e3f4e5; stroke: #b7e0bd; }
    .screen { fill: #7fc98a; }
    .chair { fill: #d4efd8; stroke: #a9dbb1; }
    &:hover .chair { fill: #b6e6bd; }
  }
  &.mine {
    .desk { fill: #e2eefc; stroke: #b3d1f5; }
    .screen { fill: #5b9bf3; }
    .chair { fill: #cfe3fb; stroke: #9dc2f2; }
    .person circle, .person path { fill: #409eff; }
  }
  &.taken {
    .desk { fill: #fde5e5; stroke: #f5b9b9; }
    .screen { fill: #e98080; }
    .chair { fill: #fbdada; stroke: #f0b4b4; }
    .person circle, .person path { fill: #f56c6c; }
  }
  &.repair {
    cursor: not-allowed;
    .desk { fill: #f0f2f5; stroke: #dcdfe6; }
    .screen { fill: #c8ccd4; }
    .chair { fill: #e9ebef; stroke: #dcdfe6; }
  }

  &:hover { filter: drop-shadow(0 2px 6px rgba(64,158,255,0.35)); }
}

.seat-no { font-size: 9.5px; fill: #93a1b3; pointer-events: none; }

.seat-hint { margin: 8px 2px 0; font-size: 12px; color: #a8abb2; text-align: center; }
</style>
