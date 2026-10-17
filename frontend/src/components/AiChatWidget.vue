<template>
  <div class="ai-chat-root">
    <!-- 悬浮球：可拖拽，位置持久化 -->
    <div
      v-show="!open"
      ref="fabRef"
      class="ai-fab"
      :class="{ dragging: fabDrag.active }"
      :style="fabStyle"
      title="按住拖动 · 单击打开智约机器人"
      @pointerdown="onFabDown"
    >
      <el-icon :size="22"><MagicStick /></el-icon>
    </div>

    <!-- 聊天窗：整体可拖，位置自动贴合视口，不会溢出屏幕 -->
    <el-card
      v-if="open"
      ref="panelRef"
      class="ai-panel"
      shadow="always"
      :style="panelStyle"
    >
      <template #header>
        <div class="hd" @pointerdown="onPanelHeaderDown">
          <span class="ttl"><el-icon><MagicStick /></el-icon> 智约机器人 <small class="drag-tip">（按住标题栏拖动）</small></span>
          <div class="hd-btns" @pointerdown.stop>
            <el-button link size="small" @click="clearChat">清空</el-button>
            <el-button link size="small" @click="closePanel">收起</el-button>
          </div>
        </div>
      </template>

      <div ref="scrollRef" class="body">
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="bubble" v-if="m.content">{{ m.content }}</div>
          <div v-if="m.actions && m.actions.length" class="actions">
            <div v-for="(a, ai) in m.actions" :key="ai" class="act-card">
              <div class="act-title">
                {{ a.kind === 'create_reservation' ? '📝 预约草案' : '🚫 取消草案' }}
              </div>
              <div class="act-body" v-if="a.kind === 'create_reservation'">
                <div>资源：{{ a.payload.resourceName || a.payload.resourceId }}</div>
                <div>时段：{{ shortTime(a.payload.startTime) }} — {{ shortTime(a.payload.endTime) }}</div>
                <div v-if="a.payload.headcount && a.payload.headcount > 1">人数：{{ a.payload.headcount }}</div>
              </div>
              <div class="act-body" v-else>
                <div>预约 ID：{{ a.payload.reservationId }}</div>
              </div>
              <div class="act-btns">
                <el-button size="small" type="primary" :loading="a.busy" @click="confirmAction(a, m)">确认</el-button>
                <el-button size="small" @click="dismissAction(a)">取消</el-button>
              </div>
              <div v-if="a.done" class="act-done">✓ {{ a.doneText || '已完成' }}</div>
            </div>
          </div>
        </div>
        <div v-if="sending" class="msg assistant"><div class="bubble typing">正在思考…</div></div>
      </div>

      <div class="input-row">
        <el-input
          v-model="draft"
          placeholder="例如：明天下午有空位图书馆吗？帮我预约两小时"
          :disabled="sending"
          @keyup.enter.exact="send"
          type="textarea"
          :rows="2"
          resize="none"
        />
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import api from '@/api'

const FAB_SIZE = 52
const PANEL_W = 400
const PANEL_H = 560
const EDGE = 12          // 距离视口边缘的安全留白
const LS_FAB = 'ai-fab-pos'
const LS_PANEL = 'ai-panel-pos'

const open = ref(false)
const sending = ref(false)
const draft = ref('')
const messages = ref([])
const scrollRef = ref()
const fabRef = ref()
const panelRef = ref()

const viewport = reactive({ w: window.innerWidth, h: window.innerHeight })
// 位置以左上角 (x, y) 表示
const fabPos = reactive({ x: 0, y: 0 })
const panelPos = reactive({ x: 0, y: 0 })

const fabDrag = reactive({ active: false, moved: false, dx: 0, dy: 0, pointerId: -1 })
const panelDrag = reactive({ active: false, dx: 0, dy: 0, pointerId: -1 })

const fabStyle = computed(() => ({
  left: fabPos.x + 'px',
  top: fabPos.y + 'px'
}))

const panelStyle = computed(() => ({
  left: panelPos.x + 'px',
  top: panelPos.y + 'px',
  width: PANEL_W + 'px',
  height: PANEL_H + 'px'
}))

// ---------------- 位置初始化与钳制 ----------------

function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val))
}

function clampFab() {
  fabPos.x = clamp(fabPos.x, EDGE, Math.max(EDGE, viewport.w - FAB_SIZE - EDGE))
  fabPos.y = clamp(fabPos.y, EDGE, Math.max(EDGE, viewport.h - FAB_SIZE - EDGE))
}

function clampPanel() {
  panelPos.x = clamp(panelPos.x, EDGE, Math.max(EDGE, viewport.w - PANEL_W - EDGE))
  panelPos.y = clamp(panelPos.y, EDGE, Math.max(EDGE, viewport.h - PANEL_H - EDGE))
}

function defaultFabPos() {
  return { x: viewport.w - FAB_SIZE - 24, y: viewport.h - FAB_SIZE - 24 }
}

/** 面板锚点：优先在悬浮球左上方展开；若空间不足则自动翻边 */
function defaultPanelPos() {
  const rightOfFab = fabPos.x + FAB_SIZE + 16
  const leftOfFab = fabPos.x - PANEL_W - 16
  const aboveFab = fabPos.y - PANEL_H - 16
  const belowFab = fabPos.y + FAB_SIZE + 16

  let x = rightOfFab + PANEL_W + EDGE < viewport.w ? rightOfFab : leftOfFab
  if (x < EDGE) x = Math.max(EDGE, viewport.w - PANEL_W - EDGE)
  if (x + PANEL_W + EDGE > viewport.w) x = viewport.w - PANEL_W - EDGE

  let y = aboveFab >= EDGE ? aboveFab : belowFab
  if (y + PANEL_H + EDGE > viewport.h) y = viewport.h - PANEL_H - EDGE
  if (y < EDGE) y = EDGE

  return { x, y }
}

function loadPositions() {
  try {
    const f = JSON.parse(localStorage.getItem(LS_FAB) || 'null')
    if (f && typeof f.x === 'number' && typeof f.y === 'number') {
      fabPos.x = f.x; fabPos.y = f.y
    } else {
      const d = defaultFabPos(); fabPos.x = d.x; fabPos.y = d.y
    }
    const p = JSON.parse(localStorage.getItem(LS_PANEL) || 'null')
    if (p && typeof p.x === 'number' && typeof p.y === 'number') {
      panelPos.x = p.x; panelPos.y = p.y
    } else {
      const d = defaultPanelPos(); panelPos.x = d.x; panelPos.y = d.y
    }
    clampFab(); clampPanel()
  } catch (e) {
    const d = defaultFabPos(); fabPos.x = d.x; fabPos.y = d.y
    const dp = defaultPanelPos(); panelPos.x = dp.x; panelPos.y = dp.y
  }
}

function saveFab() {
  try { localStorage.setItem(LS_FAB, JSON.stringify({ x: fabPos.x, y: fabPos.y })) } catch (e) {}
}
function savePanel() {
  try { localStorage.setItem(LS_PANEL, JSON.stringify({ x: panelPos.x, y: panelPos.y })) } catch (e) {}
}

// ---------------- 悬浮球拖拽 ----------------

function onFabDown(e) {
  if (e.button !== undefined && e.button !== 0) return
  fabDrag.active = true
  fabDrag.moved = false
  fabDrag.pointerId = e.pointerId
  fabDrag.dx = e.clientX - fabPos.x
  fabDrag.dy = e.clientY - fabPos.y
  window.addEventListener('pointermove', onFabMove)
  window.addEventListener('pointerup', onFabUp, { once: true })
  e.preventDefault()
}

function onFabMove(e) {
  if (!fabDrag.active) return
  const nx = e.clientX - fabDrag.dx
  const ny = e.clientY - fabDrag.dy
  if (Math.abs(nx - fabPos.x) > 3 || Math.abs(ny - fabPos.y) > 3) fabDrag.moved = true
  fabPos.x = nx; fabPos.y = ny
  clampFab()
}

function onFabUp() {
  window.removeEventListener('pointermove', onFabMove)
  fabDrag.active = false
  saveFab()
  if (!fabDrag.moved) {
    // 视为单击 → 打开面板，并把面板重新贴到悬浮球附近
    openPanel()
  }
}

// ---------------- 面板拖拽（按住标题栏） ----------------

function openPanel() {
  const d = defaultPanelPos()
  panelPos.x = d.x; panelPos.y = d.y
  clampPanel()
  open.value = true
  savePanel()
  scrollToBottom()
}

function closePanel() {
  open.value = false
}

function onPanelHeaderDown(e) {
  // 忽略标题栏内的按钮区域
  if (e.target.closest('.hd-btns')) return
  panelDrag.active = true
  panelDrag.pointerId = e.pointerId
  panelDrag.dx = e.clientX - panelPos.x
  panelDrag.dy = e.clientY - panelPos.y
  window.addEventListener('pointermove', onPanelMove)
  window.addEventListener('pointerup', onPanelUp, { once: true })
  e.preventDefault()
}

function onPanelMove(e) {
  if (!panelDrag.active) return
  panelPos.x = e.clientX - panelDrag.dx
  panelPos.y = e.clientY - panelDrag.dy
  clampPanel()
}

function onPanelUp() {
  window.removeEventListener('pointermove', onPanelMove)
  panelDrag.active = false
  savePanel()
}

// ---------------- 视口变化时重新钳制 ----------------

function onResize() {
  viewport.w = window.innerWidth
  viewport.h = window.innerHeight
  clampFab()
  clampPanel()
  saveFab(); savePanel()
}

// ---------------- 会话逻辑（保持不变） ----------------

function shortTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

function clearChat() {
  messages.value = []
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', content: text })
  draft.value = ''
  sending.value = true
  scrollToBottom()
  try {
    const history = messages.value
      .filter(m => m.content)
      .map(m => ({ role: m.role, content: m.content }))
    const res = await api.aiChat(history)
    if (res.code === 200 || res.code === 0) {
      const reply = res.data || {}
      messages.value.push({
        role: 'assistant',
        content: reply.content || '',
        actions: (reply.pendingActions || []).map(a => ({ ...a, busy: false, done: false }))
      })
    } else {
      messages.value.push({ role: 'assistant', content: '⚠ ' + (res.message || 'AI 服务异常') })
    }
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '⚠ 网络异常：' + (e?.message || e) })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function confirmAction(a) {
  a.busy = true
  try {
    if (a.kind === 'create_reservation') {
      const payload = {
        resourceId: a.payload.resourceId,
        startTime: normalizeDt(a.payload.startTime),
        endTime: normalizeDt(a.payload.endTime),
        headcount: a.payload.headcount || 1,
        remark: a.payload.remark || '由智约机器人创建'
      }
      const res = await api.createReservation(payload)
      if (res.code === 200 || res.code === 0) {
        a.done = true
        a.doneText = '预约成功，等待验证码打卡'
      } else {
        ElMessage.error(res.message || '下单失败')
      }
    } else if (a.kind === 'cancel_reservation') {
      const res = await api.cancelReservation(a.payload.reservationId)
      if (res.code === 200 || res.code === 0) {
        a.done = true
        a.doneText = '已取消'
      } else {
        ElMessage.error(res.message || '取消失败')
      }
    }
  } finally {
    a.busy = false
    scrollToBottom()
  }
}

function dismissAction(a) {
  a.done = true
  a.doneText = '已忽略'
}

function normalizeDt(s) {
  if (!s) return s
  let t = String(s).trim().replace('T', ' ')
  const dot = t.indexOf('.')
  if (dot > 0) t = t.slice(0, dot)
  const plus = t.indexOf('+')
  if (plus > 0) t = t.slice(0, plus)
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(t)) return t + ':00'
  return t
}

onMounted(() => {
  loadPositions()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('pointermove', onFabMove)
  window.removeEventListener('pointermove', onPanelMove)
})
</script>

<style scoped lang="scss">
.ai-chat-root { /* 承载 fixed 子元素的空容器 */ }

.ai-fab {
  position: fixed;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2E76DB, #74B9F5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
  touch-action: none;      /* 关闭浏览器默认手势，避免拖拽时误触发滚动 */
  box-shadow: 0 6px 20px rgba(46, 118, 219, 0.45);
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 2000;
  &:hover { transform: scale(1.08); }
  &.dragging {
    cursor: grabbing;
    transform: scale(1.12);
    box-shadow: 0 10px 28px rgba(46, 118, 219, 0.6);
    transition: none;
  }
}

.ai-panel {
  position: fixed;
  z-index: 2001;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  :deep(.el-card__header) { padding: 0; cursor: grab; user-select: none; }
  :deep(.el-card__body) { padding: 0; display: flex; flex-direction: column; height: calc(100% - 55px); }
}

.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(90deg, #123A6B, #2E76DB);
  color: #fff;
}
.ttl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  .drag-tip { font-size: 11px; opacity: 0.6; font-weight: normal; margin-left: 4px; }
}
.hd-btns {
  display: inline-flex;
  gap: 4px;
  cursor: default;
  :deep(.el-button) { color: #fff; }
  :deep(.el-button:hover) { color: #d9ecff; }
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f8fafc;
}
.msg { margin-bottom: 12px; display: flex; flex-direction: column; }
.msg.user { align-items: flex-end; }
.msg.assistant { align-items: flex-start; }
.bubble {
  max-width: 88%;
  padding: 8px 12px;
  border-radius: 10px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  border: 1px solid #ebeef5;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #2E76DB, #4E9BF0);
  color: #fff;
  border: none;
}
.typing { color: #909399; font-style: italic; }
.actions { margin-top: 6px; width: 100%; }
.act-card {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 6px;
}
.act-title { font-weight: 600; color: #303133; margin-bottom: 6px; font-size: 13px; }
.act-body { color: #606266; font-size: 13px; line-height: 1.6; }
.act-btns { margin-top: 8px; display: flex; gap: 8px; }
.act-done { margin-top: 6px; font-size: 12px; color: #67c23a; }
.input-row {
  display: flex;
  gap: 8px;
  padding: 10px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  align-items: flex-end;
}
</style>
