<template>
  <div class="detail-page" v-loading="loading">
    <div class="back-btn">
      <el-button :icon="ArrowLeft" text @click="$router.back()">返回资源列表</el-button>
    </div>

    <div v-if="resource" class="detail-content">
      <!-- 资源信息卡片 -->
      <el-card class="info-card" shadow="never">
        <div class="info-header" :style="{ backgroundImage: `url(${headerImage})` }">
          <div class="info-overlay"></div>
          <div class="info-text">
            <h1>{{ resource.name }}</h1>
            <el-tag :type="statusTagType(resource.status)" effect="dark">{{ statusText(resource.status) }}</el-tag>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label"><el-icon><OfficeBuilding /></el-icon> 楼宇</span>
            <span class="value">{{ resource.building }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><Position /></el-icon> 位置</span>
            <span class="value">{{ resource.floor }} / {{ resource.roomNo }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><User /></el-icon> 容纳</span>
            <span class="value">{{ resource.capacity }} {{ isGym ? gymUnitText : '人' }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><Clock /></el-icon> 开放</span>
            <span class="value">{{ resource.openTime }} - {{ resource.closeTime }}</span>
          </div>
        </div>
        <div class="info-desc">
          <el-icon><Document /></el-icon>
          <span>{{ resource.description }}</span>
        </div>
      </el-card>

      <!-- 预约操作区 / 教室课表 -->
      <el-card class="reserve-card" shadow="never">
        <template #header>
          <div class="card-header-title">
            <el-icon><Calendar /></el-icon>
            <span>{{ isClassroom ? '课表安排' : '预约时段' }}</span>
          </div>
        </template>

        <!-- 教室：仅展示课表占用，不提供预约 -->
        <div v-if="isClassroom" class="classroom-view">
          <el-alert type="success" :closable="false" show-icon class="no-reserve-tip">
            <template #title>教室无需预约，未被课程占用时可直接使用。下方为课程占用情况。</template>
          </el-alert>
          <el-form label-width="100px" size="default">
            <el-form-item label="查看日期">
              <el-date-picker
                v-model="courseDate"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                style="width: 100%"
                @change="fetchCourses"
              />
            </el-form-item>
            <el-form-item label="占用概览">
              <div class="course-bar-wrap">
                <div class="course-bar">
                  <div
                    v-for="(seg, i) in courseSegments"
                    :key="i"
                    class="course-seg"
                    :class="seg.busy ? 'busy' : 'free'"
                    :style="{ flexGrow: seg.minutes }"
                    :title="seg.tip"
                    @click="onCourseSegClick(seg)"
                  ></div>
                </div>
                <div class="course-bar-marks">
                  <span>{{ openShort }}</span>
                  <span>{{ closeShort }}</span>
                </div>
                <div class="timeline-legend">
                  <span><i class="dot booked"></i>上课占用（点击看课程）</span>
                  <span><i class="dot free"></i>空闲可用</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="当日课程">
              <div v-if="dayCourses.length" class="course-list">
                <div
                  v-for="c in dayCourses"
                  :key="c.id"
                  class="course-item"
                  :class="{ now: isCourseNow(c) }"
                >
                  <span class="ct">{{ shortTime(c.startTime) }} - {{ shortTime(c.endTime) }}</span>
                  <span class="cn">{{ c.courseName }}</span>
                  <span class="cd">{{ c.teacher || '—' }}</span>
                  <el-tag v-if="isCourseNow(c)" type="danger" size="small" effect="light">进行中</el-tag>
                </div>
              </div>
              <el-empty v-else description="该教室当日无课程安排" :image-size="70" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="resource.status !== 1" class="reserve-disabled">
          <el-result icon="warning" title="该资源当前不可预约" sub-title="资源处于「维修中」或「使用中」状态，请稍后再来">
          </el-result>
        </div>

        <div v-else class="reserve-form">
          <el-form :model="form" label-width="100px" size="default">
            <el-form-item label="预约日期">
              <el-date-picker
                v-model="form.date"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="时段预览">
              <div v-if="form.date && resource" class="timeline-wrap">
                <div class="timeline-bar">
                  <div
                    v-for="seg in timeSegments"
                    :key="seg.start"
                    class="seg"
                    :class="{ booked: !seg.closed && seg.booked, partial: !seg.closed && seg.partial, past: seg.past, closed: seg.closed, free: !seg.closed && !seg.booked && !seg.partial && !seg.past }"
                    :title="seg.tip"
                    @click="onSegClick(seg)"
                  ></div>
                </div>
                <div class="timeline-marks">
                  <span v-for="m in timeMarks" :key="m" class="mark">{{ m }}</span>
                </div>
                <div class="timeline-legend">
                  <span><i class="dot free"></i>{{ isGym ? '有空位' : '可预约' }}</span>
                  <span v-if="isGym"><i class="dot partial"></i>部分占用</span>
                  <span><i class="dot booked"></i>{{ isGym ? '已满' : '已约' }}</span>
                  <span><i class="dot past"></i>已过</span>
                  <span v-if="hasClosureToday"><i class="dot closed"></i>闭馆</span>
                </div>
              </div>
              <span v-else class="timeline-empty">请先选择预约日期</span>
            </el-form-item>
            <el-form-item label="开始时间">
              <el-select v-model="form.startTime" placeholder="选择开始时间" style="width: 100%">
                <el-option v-if="isToday(form.date)" label="现在" value="now">
                  <span>现在</span>
                  <span class="opt-now-time">{{ liveNowStr }}</span>
                </el-option>
                <el-option v-for="t in startFutureMarks" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="结束时间">
              <el-time-select
                v-model="form.endTime"
                :min-time="endMinTime"
                :start="openShort"
                step="00:30"
                :end="closeShort"
                placeholder="选择结束时间"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item v-if="isGym" :label="gymCountLabel">
              <el-input-number v-model="form.headcount" :min="1" :max="gymCapacity || 999" />
              <span class="checkin-tip">本场馆容量 {{ gymCapacity }} {{ gymUnitText }}，同一时段可多人同时预约</span>
            </el-form-item>
            <el-form-item label="打卡方式">
              <el-tag type="success" effect="plain">验证码打卡</el-tag>
              <span class="checkin-tip">预约成功后请向管理员索取资源当日验证码完成打卡</span>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，填写用途说明" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="submitReservation">
                确认预约
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 预约提示 -->
          <div class="reserve-tips">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>预约须知</template>
              <ul class="tips-list">
                <li v-if="resource.resourceType === 2">本实验室限 <b>10 人</b>，以小组为单位预约，组长一人预约即可</li>
                <li v-if="resource.resourceType === 2 || resource.resourceType === 4">实验室、体育馆仅可预约 <b>未来一周内</b> 的时间</li>
                <li v-if="resource.resourceType === 3">图书馆最多只能预约到 <b>次日</b></li>
                <li>打卡时间窗：预约开始前 30 分钟 ~ 开始后 5 分钟，超时未打卡将自动释放空间并记一次爽约</li>
                <li>使用中可提前释放空间，或暂离（30 分钟；饭点 11:00-12:30 / 17:30-19:00 可暂离 1 小时）</li>
                <li>同一时间段内只能预约 <b>一个资源</b>；时间不冲突可多次预约（冲突时提交会被拒绝）</li>
                <li>预约时间冲突时系统会自动拒绝</li>
              </ul>
            </el-alert>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, OfficeBuilding, Position, User, Clock, Timer, Document, Calendar
} from '@element-plus/icons-vue'
import { api } from '@/api'
import { resourceImage } from '@/utils/resourceImage'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const resource = ref(null)

const form = reactive({
  date: '',
  startTime: '',
  endTime: '',
  headcount: 1,
  remark: ''
})

// 体育馆：支持多人预约，按时段人数之和校验容量（单位取自资源自身）
const isGym = computed(() => resource.value?.resourceType === 4)
const gymCapacity = computed(() => resource.value?.capacity || 0)
const gymUnitText = computed(() => resource.value?.unit || '人')
const gymCountLabel = computed(() => (gymUnitText.value === '人' ? '使用人数' : '使用数量'))

const headerImage = computed(() => resourceImage(resource.value))

function statusText(s) { return { 1: '可预约', 2: '使用中', 3: '维修中' }[s] || '未知' }
// 配色约定：可预约/可用=绿，已占用/使用中=红，维修中=黄
function statusTagType(s) { return { 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info' }

function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (date.getTime() < today.getTime()) return true
  // 按资源类型限制最晚可预约日期：图书馆(3)最多到次日；实验室(2)/体育馆(4)未来一周内
  const type = resource.value?.resourceType
  const max = new Date(today)
  if (type === 3) {
    max.setDate(max.getDate() + 1)
  } else if (type === 2 || type === 4) {
    max.setDate(max.getDate() + 7)
  } else {
    return false
  }
  max.setHours(23, 59, 59, 999)
  return date.getTime() > max.getTime()
}

// ---------- 时段条 ----------
const bookedSlots = ref([])
const timeSegments = ref([])
const timeMarks = ref([])

// ---------- 闭馆状态（全局一键闭馆 + 按类型闭馆窗口） ----------
const campusClosed = ref(false)
const closures = ref([])

async function fetchClosureStatus() {
  try {
    const [st, cl] = await Promise.all([api.getCampusStatus(), api.getClosures()])
    campusClosed.value = !!(st.data && st.data.closed)
    closures.value = cl.code === 200 ? (cl.data || []) : []
  } catch (e) { /* 闭馆状态获取失败不阻断页面 */ }
}

/** 某时段 [s, e)（分钟）是否与作用于所选日期的闭馆窗口重叠；全局闭馆时视为全天 */
function isClosedSegment(s, e) {
  if (campusClosed.value) return '系统闭馆中'
  const c = closures.value.find(x =>
    String(x.resourceType) === String(resource.value?.resourceType) &&
    String(x.closureDate).slice(0, 10) === form.date &&
    parseHM(x.startTime) < e && parseHM(x.endTime) > s)
  return c ? (c.reason ? `闭馆：${c.reason}` : '闭馆中') : null
}

/** 所选日期是否存在影响本资源的闭馆（控制图例显隐） */
const hasClosureToday = computed(() => {
  if (!form.date) return false
  if (campusClosed.value) return true
  return closures.value.some(x =>
    String(x.resourceType) === String(resource.value?.resourceType) &&
    String(x.closureDate).slice(0, 10) === form.date)
})

// 实时当前时间（每秒更新）
const liveNow = ref(new Date())
let nowTimer = null
const liveNowStr = computed(() => {
  const d = liveNow.value
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})
const nowMin = computed(() => liveNow.value.getHours() * 60 + liveNow.value.getMinutes())

function parseHM(hm) {
  if (!hm) return 0
  const [h, m] = String(hm).split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}
function toHM(min) {
  const h = String(Math.floor(min / 60) % 24).padStart(2, '0')
  const m = String(min % 60).padStart(2, '0')
  return `${h}:${m}`
}
function todayStr() {
  const t = new Date()
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
}
function isToday(dateStr) {
  return dateStr === todayStr()
}

// ---------- 教室课表（教室不参与预约，仅展示课程占用） ----------
const isClassroom = computed(() => resource.value?.resourceType === 1)
const courseDate = ref('')
const dayCourses = ref([])

function shortTime(t) { return t ? String(t).slice(0, 5) : '' }

async function fetchCourses() {
  if (!resource.value) return
  const res = await api.getAvailableSlots(resource.value.id, courseDate.value || todayStr())
  if (res.code === 200) {
    dayCourses.value = res.data.courses || []
  }
}

/** 按开放时间段切分：红色=上课占用，绿色=空闲 */
const courseSegments = computed(() => {
  if (!resource.value) return []
  const open = parseHM(openShort.value)
  const close = parseHM(closeShort.value)
  if (close <= open) return []
  const points = new Set([open, close])
  dayCourses.value.forEach(c => {
    const s = parseHM(shortTime(c.startTime))
    const e = parseHM(shortTime(c.endTime))
    if (s > open && s < close) points.add(s)
    if (e > open && e < close) points.add(e)
  })
  const arr = [...points].sort((a, b) => a - b)
  const segs = []
  for (let i = 0; i < arr.length - 1; i++) {
    const s = arr[i], e = arr[i + 1]
    if (e <= s) continue
    const busy = dayCourses.value.some(c => {
      const cs = parseHM(shortTime(c.startTime))
      const ce = parseHM(shortTime(c.endTime))
      return cs < e && ce > s
    })
    segs.push({ start: s, end: e, minutes: e - s, busy, tip: `${toHM(s)}-${toHM(e)} ${busy ? '上课占用' : '空闲可用'}` })
  }
  return segs
})

function isCourseNow(c) {
  if (!isToday(courseDate.value)) return false
  const now = nowMin.value
  return now >= parseHM(shortTime(c.startTime)) && now < parseHM(shortTime(c.endTime))
}

/** 点击占用段 → 展示该时段正在上的课程与日程 */
function onCourseSegClick(seg) {
  if (!seg.busy) {
    ElMessage.success(`${toHM(seg.start)} - ${toHM(seg.end)} 空闲，可直接使用这间教室`)
    return
  }
  const hit = dayCourses.value.filter(c => parseHM(shortTime(c.startTime)) < seg.end && parseHM(shortTime(c.endTime)) > seg.start)
  const lines = hit.map(c => `${shortTime(c.startTime)} - ${shortTime(c.endTime)}　${c.courseName}（${c.teacher || '未填教师'}）`)
  ElMessageBox.alert(
    `${resource.value.name} 在该时段有课：\n\n${lines.join('\n')}\n\n课程期间请勿占用教室。`,
    '课程安排',
    { confirmButtonText: '知道了', type: 'warning', customStyle: { whiteSpace: 'pre-line' } }
  )
}

// 当前时间之后的最近一个 30 分钟整点（结束时间默认值）
const nextMarkAfterNow = computed(() => {
  const m = nowMin.value
  return toHM(Math.min((Math.floor(m / 30) + 1) * 30, parseHM(closeShort.value)))
})

const openShort = computed(() => (resource.value?.openTime || '08:00').slice(0, 5))
const closeShort = computed(() => (resource.value?.closeTime || '22:00').slice(0, 5))
// 结束时间选择器：最小可选 = 开始时间之后 30min（开始为"现在"时取当前之后最近整点）
const endMinTime = computed(() => {
  if (form.startTime === 'now') return nextMarkAfterNow.value
  if (form.startTime) return toHM(Math.min(parseHM(form.startTime) + 30, parseHM(closeShort.value)))
  return openShort.value
})
// 开始时间可选的将来 30min 整点（今日从当前之后最近整点起；非今日从开放时间起）
const startFutureMarks = computed(() => {
  const open = parseHM(openShort.value), close = parseHM(closeShort.value)
  let from = open
  if (isToday(form.date)) from = Math.min((Math.floor(nowMin.value / 30) + 1) * 30, close)
  const arr = []
  for (let m = from; m < close; m += 30) arr.push(toHM(m))
  return arr
})

async function fetchTimeline() {
  if (!form.date || !resource.value) return
  const res = await api.getReservationsByResource(resource.value.id, form.date)
  if (res.code === 200) {
    bookedSlots.value = (res.data || []).map(r => ({
      start: String(r.startTime).slice(11, 16),
      end: String(r.endTime).slice(11, 16),
      headcount: r.headcount == null ? 1 : r.headcount
    }))
  }
  computeSegments()
}

function computeSegments() {
  if (!resource.value) return
  const open = parseHM(openShort.value), close = parseHM(closeShort.value)
  const today = isToday(form.date)
  const nm = nowMin.value
  const cap = gymCapacity.value
  const segs = []
  for (let m = open; m < close; m += 30) {
    const hit = bookedSlots.value.filter(b => parseHM(b.start) < m + 30 && parseHM(b.end) > m)
    let booked, partial, tip
    if (isGym.value) {
      // 体育馆：累计该时间段已约人数
      const occupied = hit.reduce((s, b) => s + (b.headcount || 1), 0)
      booked = occupied >= cap
      partial = occupied > 0 && occupied < cap
      tip = `${toHM(m)}-${toHM(m + 30)} 已约 ${occupied}/${cap} ${gymUnitText.value}${booked ? '（已满）' : ''}`
    } else {
      booked = hit.length > 0
      partial = false
      tip = `${toHM(m)}-${toHM(m + 30)} ${booked ? '已约' : '可约'}`
    }
    // 今日：当前时间之前的所有 30min 时段不可选
    const past = today && m < nm
    // 闭馆时段：置灰不可预约（管理员不受限，提交时由后端把关）
    const closedMsg = past ? null : isClosedSegment(m, m + 30)
    if (closedMsg) tip = `${toHM(m)}-${toHM(m + 30)} ${closedMsg}`
    segs.push({ start: toHM(m), end: toHM(m + 30), booked, partial, past, closed: !!closedMsg, tip })
  }
  timeSegments.value = segs
  const span = close - open
  const marks = []
  for (let i = 0; i <= 4; i++) marks.push(toHM(open + Math.round((span * i) / 4)))
  timeMarks.value = marks
}

function onSegClick(seg) {
  if (seg.closed) { ElMessage.warning('该时段已闭馆，暂停预约'); return }
  if (seg.booked || seg.past) return
  form.endTime = seg.end
}

function setDefaults() {
  if (!resource.value) return
  if (isToday(form.date)) {
    // 今日：开始时间默认"现在"，结束时间默认当前之后最近 30min 整点
    form.startTime = 'now'
    form.endTime = nextMarkAfterNow.value
    if (parseHM(form.endTime) >= parseHM(closeShort.value)) {
      ElMessage.warning('今日已无可预约时段')
    }
  } else {
    form.startTime = openShort.value
    form.endTime = toHM(Math.min(parseHM(openShort.value) + 30, parseHM(closeShort.value)))
  }
}

watch(() => form.date, async () => {
  if (form.date) {
    await fetchTimeline()
    setDefaults()
  }
})
// 开始时间变化时，若结束时间不再合法则自动后移
watch(() => form.startTime, (nv) => {
  if (!nv || !resource.value) return
  const startMin = nv === 'now' ? nowMin.value : parseHM(nv)
  const minEnd = nv === 'now'
    ? nextMarkAfterNow.value
    : toHM(Math.min(parseHM(nv) + 30, parseHM(closeShort.value)))
  if (!form.endTime || parseHM(form.endTime) <= startMin) {
    form.endTime = minEnd
  }
})
// 实时刷新时段条（仅今日，past 随当前时间变化）
watch(liveNow, () => {
  if (form.date && isToday(form.date) && resource.value) computeSegments()
})

async function loadData() {
  loading.value = true
  try {
    const res = await api.getResource(route.params.id)
    if (res.code === 200) {
      resource.value = res.data
    } else {
      ElMessage.error(res.message)
      router.back()
    }
  } finally {
    loading.value = false
  }
}

async function submitReservation() {
  if (!form.date) { ElMessage.warning('请选择预约日期'); return }
  if (!form.endTime) { ElMessage.warning('请选择结束时间'); return }

  let startStr, startMinVal
  if (form.startTime === 'now') {
    // 开始时间 = 现在（提交时刻，精确到秒，不取整）
    const d = new Date()
    const p = (n) => String(n).padStart(2, '0')
    startStr = `${form.date} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    startMinVal = d.getHours() * 60 + d.getMinutes()
  } else {
    if (!form.startTime) { ElMessage.warning('请选择开始时间'); return }
    startStr = `${form.date} ${form.startTime}:00`
    startMinVal = parseHM(form.startTime)
  }
  const duration = parseHM(form.endTime) - startMinVal
  if (duration <= 0) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  // 前端预校验闭馆时段（后端同样会拦截）
  if (isClosedSegment(startMinVal, parseHM(form.endTime))) {
    ElMessage.warning('所选时段处于闭馆期间，暂停预约')
    return
  }

  submitting.value = true
  try {
    const res = await api.createReservation({
      resourceId: resource.value.id,
      startTime: startStr,
      endTime: `${form.date} ${form.endTime}:00`,
      headcount: isGym.value ? form.headcount : 1,
      remark: form.remark
    })
    if (res.code === 200) {
      // 打卡时间窗：预约开始前 30 分钟 ~ 开始后 5 分钟
      const startAt = new Date(startStr.replace(' ', 'T'))
      const winFrom = new Date(startAt.getTime() - 30 * 60 * 1000)
      const winTo = new Date(startAt.getTime() + 5 * 60 * 1000)
      const f = (d) => `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      ElMessageBox.confirm(
        `请在 ${f(winFrom)} ~ ${f(winTo)} 之间，向管理员索取「${resource.value.name}」的当日验证码完成打卡；超过该时段未打卡将自动释放空间并记一次爽约。`,
        '预约成功',
        { confirmButtonText: '查看我的预约', cancelButtonText: '继续浏览', type: 'success' }
      ).then(() => {
        router.push('/my-reservations')
      }).catch(() => {})
      // 重置表单（保留日期，刷新时段条以显示新预约）
      form.endTime = ''
      form.headcount = 1
      form.remark = ''
      await fetchTimeline()
      setDefaults()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  nowTimer = setInterval(() => { liveNow.value = new Date() }, 1000)
  await loadData()
  await fetchClosureStatus()
  form.date = todayStr()
  if (isClassroom.value) {
    courseDate.value = todayStr()
    await fetchCourses()
  }
})
onBeforeUnmount(() => clearInterval(nowTimer))
</script>

<style scoped lang="scss">
.detail-page { max-width: 900px; margin: 0 auto; }

.back-btn { margin-bottom: 12px; }

.info-card, .reserve-card {
  border-radius: 14px;
  border: none;
  margin-bottom: 20px;
}

.info-header {
  height: 180px;
  border-radius: 12px;
  background-size: cover;
  background-position: center;
  position: relative;
  display: flex;
  align-items: flex-end;
  margin-bottom: 20px;

  .info-overlay {
    position: absolute;
    inset: 0;
    border-radius: 12px;
    background: linear-gradient(180deg, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.65) 100%);
  }
  .info-text {
    position: relative;
    z-index: 1;
    padding: 24px 28px;
    width: 100%;

    h1 {
      color: #fff;
      font-size: 26px;
      margin-bottom: 10px;
      text-shadow: 0 2px 8px rgba(0,0,0,0.5);
    }
  }
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 20px;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px 16px;
    background: #f9fafc;
    border-radius: 8px;

    .label {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #909399;
    }
    .value { font-size: 15px; font-weight: 500; color: #303133; }
  }
}

.info-desc {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #f4f4f5;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
}

.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.checkin-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #144E9C, #2E76DB);
  border: none;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(30, 98, 208, 0.4); }
}

.reserve-tips { margin-top: 8px; }
.tips-list { margin: 4px 0 0 16px; font-size: 13px; line-height: 1.8; }

/* 时段条 */
.timeline-wrap { width: 100%; }
.timeline-bar {
  display: flex;
  width: 100%;
  height: 24px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}
.seg {
  flex: 1;
  cursor: pointer;
  border-right: 1px solid rgba(255, 255, 255, 0.65);
  transition: filter 0.2s, transform 0.15s;
  &:last-child { border-right: none; }
  &:hover { filter: brightness(1.1); transform: scaleY(1.12); }
}
.seg.free { background: #67c23a; }
.seg.partial { background: #e6a23c; }
.seg.booked { background: #f56c6c; cursor: not-allowed; }
.seg.past { background: #dcdfe6; cursor: not-allowed; }
.seg.closed { background: #bfbfbf; cursor: not-allowed; }
.timeline-marks {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
}
.timeline-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
  .dot {
    display: inline-block;
    width: 10px; height: 10px;
    border-radius: 3px;
    margin-right: 4px;
    vertical-align: middle;
  }
  .dot.free { background: #67c23a; }
  .dot.partial { background: #e6a23c; }
  .dot.booked { background: #f56c6c; }
  .dot.past { background: #dcdfe6; }
  .dot.closed { background: repeating-linear-gradient(45deg, #bfbfbf, #bfbfbf 2px, #a8a8a8 2px, #a8a8a8 4px); }
}
.timeline-empty { color: #c0c4cc; font-size: 13px; }
.time-row { display: flex; gap: 8px; width: 100%; }
.opt-now-time {
  float: right;
  color: #409eff;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-family: 'Segoe UI', 'Microsoft YaHei', monospace;
}

/* ===== 教室课表视图 ===== */
.classroom-view {
  .no-reserve-tip { margin-bottom: 16px; }

  .course-bar-wrap { width: 100%; }

  .course-bar {
    display: flex;
    width: 100%;
    height: 26px;
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid #ebeef5;
  }
  .course-seg {
    min-width: 2px;
    transition: filter 0.15s, transform 0.15s;

    &:hover { filter: brightness(1.08); transform: scaleY(1.12); }
  }
  .course-seg.busy { background: #f56c6c; cursor: pointer; }
  .course-seg.free { background: #67c23a; cursor: pointer; }

  .course-bar-marks {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
    font-size: 11px;
    color: #909399;
  }

  .course-list {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .course-item {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 8px 12px;
    border-radius: 8px;
    background: #f7f8fa;
    font-size: 13px;

    &.now { background: #fef0f0; box-shadow: inset 3px 0 0 #f56c6c; }
    .ct { color: #909399; font-variant-numeric: tabular-nums; min-width: 104px; }
    .cn { color: #303133; font-weight: 600; flex: 1; }
    .cd { color: #909399; }
  }
}
</style>
