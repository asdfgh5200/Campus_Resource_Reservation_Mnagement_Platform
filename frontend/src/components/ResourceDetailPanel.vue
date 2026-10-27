<template>
  <div class="detail-panel" v-loading="loading">
    <div v-if="resource" class="detail-content">
      <!-- 资源信息卡片 -->
      <el-card class="info-card" shadow="never">
        <div class="info-header" :style="{ backgroundImage: `url(${headerImage})` }">
          <div class="info-overlay"></div>
          <div class="info-text">
            <h1>{{ resource.name }}</h1>
            <el-tag :type="statusTagType(resource.status)" effect="dark">{{
              statusText(resource.status)
            }}</el-tag>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label"
              ><el-icon><OfficeBuilding /></el-icon> 校区楼宇</span
            >
            <span class="value">{{
              [resource.campus, resource.building].filter(Boolean).join(' · ')
            }}</span>
          </div>
          <div class="info-item">
            <span class="label"
              ><el-icon><Position /></el-icon> 位置</span
            >
            <span class="value">{{ resource.floor }} / {{ resource.roomNo }}</span>
          </div>
          <div v-if="resource.roomType" class="info-item">
            <span class="label"
              ><el-icon><Collection /></el-icon> {{ roomTypeLabel }}</span
            >
            <span class="value">{{ resource.roomType }}</span>
          </div>
          <div class="info-item">
            <span class="label"
              ><el-icon><User /></el-icon> 容纳</span
            >
            <span class="value">{{ resource.capacity }} {{ isGym ? gymUnitText : '人' }}</span>
          </div>
          <div class="info-item">
            <span class="label"
              ><el-icon><Clock /></el-icon> 开放</span
            >
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
                  <span><i class="dot booked"></i>上课占用（点击查看该时段课程）</span>
                  <span><i class="dot free"></i>空闲可用</span>
                </div>
                <!-- 课表不常驻展示：点了红色占用段才就地展开该时段的课程明细 -->
                <div v-if="selectedSeg && courseHits.length" class="seg-course-pop">
                  <div class="pop-head">
                    <span>{{ toHM(selectedSeg.start) }} - {{ toHM(selectedSeg.end) }} 上课中</span>
                    <span class="pop-close" @click="selectedSeg = null">收起</span>
                  </div>
                  <div
                    v-for="c in courseHits"
                    :key="c.id"
                    class="course-item"
                    :class="{ now: isCourseNow(c) }"
                  >
                    <span class="ct">{{ shortTime(c.startTime) }} - {{ shortTime(c.endTime) }}</span>
                    <span class="cn">{{ c.courseName }}</span>
                    <span class="cd">{{ c.teacher || '未填教师' }}</span>
                    <el-tag v-if="isCourseNow(c)" type="danger" size="small" effect="light"
                      >进行中</el-tag
                    >
                  </div>
                </div>
              </div>
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="resource.status !== 1" class="reserve-disabled">
          <el-result
            icon="warning"
            title="该资源当前不可预约"
            sub-title="资源处于「维修中」「报修中」或「使用中」状态，请稍后再来"
          >
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
                <div ref="barRef" class="timeline-bar" @click="onBarClick">
                  <div
                    v-for="b in timelineBands"
                    :key="b.start"
                    class="band"
                    :class="b.state"
                    :style="{ flexGrow: b.minutes }"
                    :title="b.tip"
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
                  <span class="bar-hint">点击色带选择结束时间</span>
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
                :min-time="endMinBound"
                :start="openShort"
                step="00:30"
                :end="closeShort"
                placeholder="选择结束时间"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item v-if="isGym" :label="gymCountLabel">
              <el-input-number v-model="form.headcount" :min="1" :max="gymCapacity || 999" />
              <span class="checkin-tip"
                >本场馆容量 {{ gymCapacity }} {{ gymUnitText }}，同一时段可多人同时预约</span
              >
            </el-form-item>
            <el-form-item label="打卡方式">
              <el-tag type="success" effect="plain">定位打卡</el-tag>
              <span class="checkin-tip"
                >请在预约时段内到达「{{
                  resource.building || resource.name
                }}」，授权浏览器定位即可完成打卡</span
              >
            </el-form-item>
            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
                placeholder="选填，填写用途说明"
              />
            </el-form-item>
            <el-form-item>
              <!-- 正在使用本资源（所选时段落在自己的使用中预约内）→ 主操作是提前释放，而不是重复预约 -->
              <template v-if="releaseTarget">
                <div class="primary-action">
                  <el-button
                    type="warning"
                    size="large"
                    class="submit-btn release-btn"
                    :loading="releasing"
                    @click="releaseMyUsing"
                  >
                    提前释放
                  </el-button>
                  <div class="action-hint using">
                    你正在使用本资源（{{ releaseTarget.start }} - {{ releaseTarget.end }}）：提前释放会立即结束预约并让出空间，也可等到期自动结束
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="primary-action">
                  <el-button
                    type="primary"
                    size="large"
                    class="submit-btn"
                    :loading="submitting"
                    :disabled="!!disabledReason"
                    @click="submitReservation"
                  >
                    确认预约
                  </el-button>
                  <div v-if="disabledReason" class="action-hint block">{{ disabledReason }}</div>
                </div>
              </template>
            </el-form-item>
          </el-form>

          <!-- 预约提示 -->
          <div class="reserve-tips">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>预约须知</template>
              <ul class="tips-list">
                <li v-if="resource.resourceType === 2">
                  本实验室可容纳 <b>{{ resource.capacity }} 人</b>，以小组为单位预约，组长一人预约即可（一次预约占一个空间）
                </li>
                <li v-if="resource.resourceType === 4">
                  本场馆容量 <b>{{ gymCapacity }} {{ gymUnitText }}</b>：同一时段可多人同时预约，已约数量加本次数量不得超过容量
                </li>
                <li v-if="resource.resourceType === 2 || resource.resourceType === 4">
                  仅可预约 <b>未来一周内</b> 的时间
                </li>
                <li v-if="resource.resourceType === 3">最多只能预约到 <b>次日</b></li>
                <li>单次预约至少 <b>30 分钟</b>（选「现在」时结束时间会自动跳到满足 30 分钟的最早刻度）</li>
                <li>
                  打卡时间窗：预约开始前 30 分钟 ~ 开始后 5 分钟；需到达资源所在建筑后完成定位打卡，超时未打卡将自动释放并记一次爽约
                </li>
                <li>使用中可提前释放空间，释放后立即结束预约并让出给别人</li>
                <li>
                  同一时段仅可预约 <b>一个资源</b>（与该资源已有预约重叠时也无法提交）；时间不冲突可多次预约
                </li>
                <li>节假日、闭馆时段暂停预约</li>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  OfficeBuilding,
  Position,
  User,
  Clock,
  Timer,
  Document,
  Collection,
  Calendar
} from '@element-plus/icons-vue'
import { api } from '@/api'
import { resourceImage } from '@/utils/resourceImage'
import { useUserStore } from '@/stores/user'

// 详情面板被抽屉复用：资源 id 由外部传入，加载失败时通知外部关闭
const props = defineProps({
  resourceId: { type: [Number, String], required: true }
})
const emit = defineEmits(['close'])
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
// 「提前释放」独立 loading，避免与「确认预约」互相影响
const releasing = ref(false)
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
// 细分类型在各资源类型下的叫法：教室类型 / 实验方向 / 运动类型 / 座位类型
const roomTypeLabel = computed(() => {
  const t = Number(resource.value?.resourceType)
  if (t === 2) return '实验方向'
  if (t === 3) return '座位类型'
  if (t === 4) return '运动类型'
  return '教室类型'
})
const gymCapacity = computed(() => resource.value?.capacity || 0)
const gymUnitText = computed(() => resource.value?.unit || '人')
const gymCountLabel = computed(() => (gymUnitText.value === '人' ? '使用人数' : '使用数量'))

const headerImage = computed(() => resourceImage(resource.value))

function statusText(s) {
  return { 1: '可预约', 2: '使用中', 3: '维修中', 4: '报修中' }[s] || '未知'
}
// 配色约定：可预约/可用=绿，已占用/使用中=红，维修中/报修中=黄
function statusTagType(s) {
  return { 1: 'success', 2: 'danger', 3: 'warning', 4: 'warning' }[s] || 'info'
}

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

// ---------- 时段轴 ----------
const bookedSlots = ref([])
const barRef = ref(null)
// 时间轴刻度：开放 ~ 闭馆均分 4 段
const timeMarks = computed(() => {
  const open = parseHM(openShort.value)
  const close = parseHM(closeShort.value)
  if (close <= open) return []
  const span = close - open
  return [0, 1, 2, 3, 4].map((i) => toHM(open + Math.round((span * i) / 4)))
})

// ---------- 闭馆状态（全局一键闭馆 + 按类型闭馆窗口） ----------
const campusClosed = ref(false)
const closures = ref([])

async function fetchClosureStatus() {
  try {
    const [st, cl] = await Promise.all([api.getCampusStatus(), api.getClosures()])
    campusClosed.value = !!(st.data && st.data.closed)
    closures.value = cl.code === 200 ? cl.data || [] : []
  } catch (e) {
    /* 闭馆状态获取失败不阻断页面 */
  }
}

/** 某时段 [s, e)（分钟）是否与作用于所选日期的闭馆窗口重叠；全局闭馆时视为全天 */
function isClosedSegment(s, e) {
  if (campusClosed.value) return '闭馆中'
  const c = closures.value.find(
    (x) =>
      String(x.resourceType) === String(resource.value?.resourceType) &&
      String(x.closureDate).slice(0, 10) === form.date &&
      parseHM(x.startTime) < e &&
      parseHM(x.endTime) > s
  )
  return c ? (c.reason ? `闭馆：${c.reason}` : '闭馆中') : null
}

/** 所选日期是否存在影响本资源的闭馆（控制图例显隐） */
const hasClosureToday = computed(() => {
  if (!form.date) return false
  if (campusClosed.value) return true
  return closures.value.some(
    (x) =>
      String(x.resourceType) === String(resource.value?.resourceType) &&
      String(x.closureDate).slice(0, 10) === form.date
  )
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

// ---------- 我自己的进行中预约：决定主操作是「确认预约」还是「提前释放」 ----------
const myActiveSlots = computed(() => {
  const uid = userStore.userInfo?.id
  if (!uid) return []
  return bookedSlots.value.filter((b) => b.userId === uid && (b.status === 1 || b.status === 2))
})

// 所选时段的分钟区间（开始选「现在」时取当前时刻）
const selRange = computed(() => {
  if (!form.startTime || !form.endTime) return null
  const s = form.startTime === 'now' ? nowMin.value : parseHM(form.startTime)
  const e = parseHM(form.endTime)
  return e > s ? { s, e } : null
})

/** 所选时段是否与某条预约重叠 */
function overlapsSlot(slot) {
  const r = selRange.value
  return !!r && parseHM(slot.start) < r.e && parseHM(slot.end) > r.s
}

/** 所选时段落在自己的「使用中」预约内 → 主按钮换成提前释放 */
const releaseTarget = computed(
  () => myActiveSlots.value.find((b) => b.status === 2 && overlapsSlot(b)) || null
)

/** 所选时段与自己的待打卡/使用中预约重叠 → 同一时段不能重复预约（单资源单时段只能一个有效预约） */
const blockReason = computed(() => {
  if (releaseTarget.value) return ''
  const hit = myActiveSlots.value.find(overlapsSlot)
  if (!hit) return ''
  return hit.status === 1
    ? `所选时段与你已有的预约（${hit.start} - ${hit.end}）重叠，该预约尚未打卡，请先完成打卡或调整时间`
    : `所选时段与你的使用中预约（${hit.start} - ${hit.end}）重叠`
})

/**
 * 所选时段被他人占用 → 直接置灰，不等提交后被后端拒绝：
 * 非体育馆资源同一时段只能有一个预约；体育馆按已约人数 + 本次人数是否超容量判定。
 */
const occupiedReason = computed(() => {
  const r = selRange.value
  if (!r) return ''
  const hit = bookedSlots.value.filter((b) => parseHM(b.start) < r.e && parseHM(b.end) > r.s)
  if (!hit.length) return ''
  if (isGym.value) {
    const occupied = hit.reduce((sum, b) => sum + (b.headcount || 1), 0)
    const want = form.headcount || 1
    if (occupied + want <= gymCapacity.value) return ''
    return `该时段容量不足：已约 ${occupied} ${gymUnitText.value}，总容量 ${gymCapacity.value} ${gymUnitText.value}`
  }
  // 拖到该时段结束，给出“到几点才能约”的明确提示
  const latest = hit.reduce((m, b) => Math.max(m, parseHM(b.end)), 0)
  return `所选时段已被他人预约（至 ${toHM(latest)}），请改选其他时间或点击绿色色带`
})

/** 所选时段不足 30 分钟（临近闭馆或手动选得太短）→ 同样不能提交 */
const durationReason = computed(() => {
  const r = selRange.value
  if (!r) return ''
  return r.e - r.s < MIN_DURATION
    ? `单次预约至少 ${MIN_DURATION} 分钟，请选择更晚的结束时间`
    : ''
})

/** 主按钮置灰的统一原因（时长不足、自己的预约、他人占用依次判定） */
const disabledReason = computed(
  () => durationReason.value || blockReason.value || occupiedReason.value
)

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
// 当前被点击查看的占用段（null = 未展开课程明细）
const selectedSeg = ref(null)

function shortTime(t) {
  return t ? String(t).slice(0, 5) : ''
}

async function fetchCourses() {
  if (!resource.value) return
  // 换日期后收起旧的课程明细，避免展示錯日期的课
  selectedSeg.value = null
  const res = await api.getAvailableSlots(resource.value.id, courseDate.value || todayStr())
  if (res.code === 200) {
    dayCourses.value = res.data.courses || []
  }
}

/** 被点击的那个占用段内的课程 */
const courseHits = computed(() => {
  const seg = selectedSeg.value
  if (!seg) return []
  return dayCourses.value.filter(
    (c) =>
      parseHM(shortTime(c.startTime)) < seg.end && parseHM(shortTime(c.endTime)) > seg.start
  )
})

/** 按开放时间段切分：红色=上课占用，绿色=空闲 */
const courseSegments = computed(() => {
  if (!resource.value) return []
  const open = parseHM(openShort.value)
  const close = parseHM(closeShort.value)
  if (close <= open) return []
  const points = new Set([open, close])
  dayCourses.value.forEach((c) => {
    const s = parseHM(shortTime(c.startTime))
    const e = parseHM(shortTime(c.endTime))
    if (s > open && s < close) points.add(s)
    if (e > open && e < close) points.add(e)
  })
  const arr = [...points].sort((a, b) => a - b)
  const segs = []
  for (let i = 0; i < arr.length - 1; i++) {
    const s = arr[i],
      e = arr[i + 1]
    if (e <= s) continue
    const busy = dayCourses.value.some((c) => {
      const cs = parseHM(shortTime(c.startTime))
      const ce = parseHM(shortTime(c.endTime))
      return cs < e && ce > s
    })
    segs.push({
      start: s,
      end: e,
      minutes: e - s,
      busy,
      tip: `${toHM(s)}-${toHM(e)} ${busy ? '上课占用' : '空闲可用'}`
    })
  }
  return segs
})

function isCourseNow(c) {
  if (!isToday(courseDate.value)) return false
  const now = nowMin.value
  return now >= parseHM(shortTime(c.startTime)) && now < parseHM(shortTime(c.endTime))
}

/** 点击色带：空闲段提示可直接使用，占用段就地展开该时段的课程（不弹窗、不常驻列表） */
function onCourseSegClick(seg) {
  if (!seg.busy) {
    selectedSeg.value = null
    ElMessage.success(`${toHM(seg.start)} - ${toHM(seg.end)} 空闲，可直接使用这间教室`)
    return
  }
  // 再次点击同一段则收起
  selectedSeg.value = selectedSeg.value && selectedSeg.value.start === seg.start ? null : seg
}

// 当前时间之后的最近一个 30 分钟整点（结束时间默认值）
const nextMarkAfterNow = computed(() => {
  const m = nowMin.value
  return toHM(Math.min((Math.floor(m / 30) + 1) * 30, parseHM(closeShort.value)))
})

// 单次预约最短时长（与后端 ReservationService.MIN_DURATION_MINUTES 保持一致）
const MIN_DURATION = 30

/** 「现在」开始时的最早结束时间：当前 + 30 分钟再向上吸附到 30 分钟刻度 */
const firstEndMarkAfterNow = computed(() => {
  const m = Math.ceil((nowMin.value + MIN_DURATION) / 30) * 30
  return toHM(Math.min(m, parseHM(closeShort.value)))
})

const openShort = computed(() => (resource.value?.openTime || '08:00').slice(0, 5))
const closeShort = computed(() => (resource.value?.closeTime || '22:00').slice(0, 5))
// 结束时间选择器：最小可选 = 开始时间之后至少 30min（开始为"现在"时要满足满 30 分钟）
const endMinTime = computed(() => {
  if (form.startTime === 'now') return firstEndMarkAfterNow.value
  if (form.startTime)
    return toHM(Math.min(parseHM(form.startTime) + MIN_DURATION, parseHM(closeShort.value)))
  return openShort.value
})
// el-time-select 的 min-time 是开区间（等于 min-time 的选项也会被禁用），
// 因此传入「最早可选时间的上一档」，让最早合法刻度本身可选
const endMinBound = computed(() =>
  toHM(Math.max(parseHM(endMinTime.value) - 30, parseHM(openShort.value) - 30))
)
// 开始时间可选的将来 30min 整点（今日从当前之后最近整点起；非今日从开放时间起）
const startFutureMarks = computed(() => {
  const open = parseHM(openShort.value),
    close = parseHM(closeShort.value)
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
    bookedSlots.value = (res.data || []).map((r) => ({
      id: r.id,
      userId: r.userId,
      status: r.status,
      start: String(r.startTime).slice(11, 16),
      end: String(r.endTime).slice(11, 16),
      headcount: r.headcount == null ? 1 : r.headcount
    }))
  }
}

/** 当日作用于本资源的闭馆窗口（用于时间轴切分） */
const closureWindows = computed(() => {
  if (!resource.value || !form.date) return []
  return closures.value.filter(
    (x) =>
      String(x.resourceType) === String(resource.value?.resourceType) &&
      String(x.closureDate).slice(0, 10) === form.date
  )
})

/**
 * 时段轴色带：按真实时刻切分（预约起止、当前时间、闭馆窗口），
 * 不再固定 30 分钟一格，色带宽度与持续时长严格成比例。
 */
const timelineBands = computed(() => {
  if (!resource.value || !form.date) return []
  const open = parseHM(openShort.value)
  const close = parseHM(closeShort.value)
  if (close <= open) return []
  const today = isToday(form.date)
  const now = nowMin.value
  const cap = gymCapacity.value

  const points = new Set([open, close])
  const addPoint = (m) => {
    if (m > open && m < close) points.add(m)
  }
  bookedSlots.value.forEach((b) => {
    addPoint(parseHM(b.start))
    addPoint(parseHM(b.end))
  })
  // 今日把「现在」也作为切分点，已过部分正好停在当前分钟
  if (today) addPoint(now)
  closureWindows.value.forEach((c) => {
    addPoint(parseHM(c.startTime))
    addPoint(parseHM(c.endTime))
  })

  const bounds = [...points].sort((a, b) => a - b)
  const bands = []
  for (let i = 0; i < bounds.length - 1; i++) {
    const s = bounds[i]
    const e = bounds[i + 1]
    if (e <= s) continue
    const hit = bookedSlots.value.filter((b) => parseHM(b.start) < e && parseHM(b.end) > s)
    const closedMsg = isClosedSegment(s, e)
    const occupied = hit.reduce((sum, b) => sum + (b.headcount || 1), 0)
    let state, tip
    if (closedMsg) {
      state = 'closed'
      tip = `${toHM(s)}-${toHM(e)} ${closedMsg}`
    } else if (isGym.value && occupied > 0) {
      const full = occupied >= cap
      state = full ? 'booked' : 'partial'
      tip = `${toHM(s)}-${toHM(e)} 已约 ${occupied}/${cap} ${gymUnitText.value}${full ? '（已满）' : ''}`
    } else if (hit.length > 0) {
      state = 'booked'
      tip = `${toHM(s)}-${toHM(e)} 已约`
    } else if (today && e <= now) {
      state = 'past'
      tip = `${toHM(s)}-${toHM(e)} 已过`
    } else {
      state = 'free'
      tip = `${toHM(s)}-${toHM(e)} 可约`
    }
    bands.push({ start: s, end: e, minutes: e - s, state, tip })
  }
  return bands
})

/** 点击时段轴：按点击位置换算成分钟，吸附到下一个 30 分钟刻度作为结束时间 */
function onBarClick(event) {
  const rect = barRef.value?.getBoundingClientRect()
  if (!rect || !rect.width) return
  const open = parseHM(openShort.value)
  const close = parseHM(closeShort.value)
  if (close <= open) return
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const target = Math.min(close, Math.ceil((open + ratio * (close - open)) / 30) * 30)
  const startMin = form.startTime === 'now' ? nowMin.value : parseHM(form.startTime)
  if (target <= startMin) {
    ElMessage.warning('结束时间需晚于开始时间')
    return
  }
  if (target - startMin < MIN_DURATION) {
    ElMessage.warning(`单次预约至少 ${MIN_DURATION} 分钟，请把结束时间往后选`)
    return
  }
  if (isClosedSegment(startMin, target)) {
    ElMessage.warning('所选时段处于闭馆期间，暂停预约')
    return
  }
  const conflict = bookedSlots.value.filter(
    (b) => parseHM(b.start) < target && parseHM(b.end) > startMin
  )
  // 体育馆：只要容量还够就可以叠加预约；其他资源同一时段只能一个预约
  if (isGym.value) {
    const occupied = conflict.reduce((sum, b) => sum + (b.headcount || 1), 0)
    if (occupied + (form.headcount || 1) > gymCapacity.value) {
      ElMessage.warning(
        `该时段容量不足：已约 ${occupied} ${gymUnitText.value}，总容量 ${gymCapacity.value} ${gymUnitText.value}`
      )
      return
    }
  } else if (conflict.length) {
    const mineHit = conflict.some((b) => b.userId === userStore.userInfo?.id)
    ElMessage.warning(
      mineHit ? '所选时段与你的预约重叠，请调整时间' : '所选时段已被他人预约，请改选其他时间'
    )
    return
  }
  form.endTime = toHM(target)
}

function setDefaults() {
  if (!resource.value) return
  if (isToday(form.date)) {
    // 今日：开始时间默认"现在"，结束时间默认满足 30 分钟的最早刻度
    form.startTime = 'now'
    form.endTime = firstEndMarkAfterNow.value
    if (parseHM(form.endTime) - nowMin.value < MIN_DURATION) {
      ElMessage.warning('今日剩余时间不足 30 分钟，请选择其他日期预约')
    }
  } else {
    form.startTime = openShort.value
    form.endTime = toHM(
      Math.min(parseHM(openShort.value) + MIN_DURATION, parseHM(closeShort.value))
    )
  }
}

watch(
  () => form.date,
  async () => {
    if (form.date) {
      await fetchTimeline()
      setDefaults()
    }
  }
)
// 开始时间变化时，若结束时间不再合法则自动后移
watch(
  () => form.startTime,
  (nv) => {
    if (!nv || !resource.value) return
    const startMin = nv === 'now' ? nowMin.value : parseHM(nv)
    const minEnd =
      nv === 'now'
        ? firstEndMarkAfterNow.value
        : toHM(Math.min(parseHM(nv) + MIN_DURATION, parseHM(closeShort.value)))
    // 结束时间需同时满足：晚于开始时间且时长不少于 30 分钟
    if (!form.endTime || parseHM(form.endTime) - startMin < MIN_DURATION) {
      form.endTime = minEnd
    }
  }
)
// 时间自然流逝使已选结束时间不足 30 分钟时，自动顺延到最近合法刻度
watch(firstEndMarkAfterNow, (nv) => {
  if (form.startTime !== 'now' || !form.endTime) return
  if (parseHM(form.endTime) < parseHM(nv)) form.endTime = nv
})

// 已过/已约 随当前时间自动重算（timelineBands 依赖 nowMin，无需手动刷新）

async function loadData() {
  loading.value = true
  try {
    const res = await api.getResource(props.resourceId)
    if (res.code === 200) {
      resource.value = res.data
    } else {
      ElMessage.error(res.message)
      emit('close')
    }
  } finally {
    loading.value = false
  }
}

async function submitReservation() {
  if (!form.date) {
    ElMessage.warning('请选择预约日期')
    return
  }
  if (!form.endTime) {
    ElMessage.warning('请选择结束时间')
    return
  }

  let startStr, startMinVal
  if (form.startTime === 'now') {
    // 开始时间 = 现在（提交时刻，精确到秒，不取整）
    const d = new Date()
    const p = (n) => String(n).padStart(2, '0')
    startStr = `${form.date} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    startMinVal = d.getHours() * 60 + d.getMinutes()
  } else {
    if (!form.startTime) {
      ElMessage.warning('请选择开始时间')
      return
    }
    startStr = `${form.date} ${form.startTime}:00`
    startMinVal = parseHM(form.startTime)
  }
  const duration = parseHM(form.endTime) - startMinVal
  if (duration <= 0) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  if (duration < MIN_DURATION) {
    ElMessage.warning(`单次预约时长不能少于 ${MIN_DURATION} 分钟`)
    return
  }
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
      const f = (d) =>
        `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      ElMessageBox.confirm(
        `请在 ${f(winFrom)} ~ ${f(winTo)} 之间，到达「${resource.value.name}」附近并在“我的预约”中完成定位打卡；超过该时段未打卡将自动释放空间并记一次爽约。`,
        '预约成功',
        { confirmButtonText: '查看我的预约', cancelButtonText: '继续浏览', type: 'success' }
      )
        .then(() => {
          router.push('/my-reservations')
        })
        .catch(() => {})
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

/** 在当前资源上主动提前释放自己的使用中预约 */
async function releaseMyUsing() {
  const target = releaseTarget.value
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `确定提前释放「${resource.value.name}」吗？释放后本次预约立即结束（状态变为已完成），空间会开放给其他人。`,
      '提前释放',
      { confirmButtonText: '确认释放', cancelButtonText: '继续使用', type: 'warning' }
    )
  } catch {
    return
  }
  releasing.value = true
  try {
    const res = await api.releaseReservation(target.id)
    if (res.code === 200) {
      ElMessage.success('已提前释放，空间已开放给其他人')
      await fetchTimeline()
      setDefaults()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    releasing.value = false
  }
}

onMounted(async () => {
  nowTimer = setInterval(() => {
    liveNow.value = new Date()
  }, 1000)
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
.detail-panel {
  width: 100%;
}

.info-card,
.reserve-card {
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
    background: linear-gradient(180deg, rgba(0, 0, 0, 0.15) 0%, rgba(0, 0, 0, 0.65) 100%);
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
      text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
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
    .value {
      font-size: 15px;
      font-weight: 500;
      color: #303133;
    }
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
  background: linear-gradient(135deg, #144e9c, #2e76db);
  border: none;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  transition:
    transform 0.2s,
    box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(30, 98, 208, 0.4);
  }

  /* 禁用态（时段已被他人占用等）：置灰且不再响应 hover，避免看起来还能点 */
  &.is-disabled,
  &.is-disabled:hover,
  &.is-disabled:focus {
    background: linear-gradient(135deg, #c8ccd4, #dcdfe6);
    color: #f2f3f5;
    transform: none;
    box-shadow: none;
    cursor: not-allowed;
  }
}

/* 主操作按钮与下方的状态说明 */
.primary-action {
  width: 100%;

  .action-hint {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.6;
    color: #909399;

    &.using {
      color: #b88230;
    }
    &.block {
      color: #f56c6c;
    }
  }
}
.release-btn {
  background: linear-gradient(135deg, #b7791f, #e6a23c);

  &:hover {
    box-shadow: 0 6px 20px rgba(230, 162, 60, 0.4);
  }
}

.reserve-tips {
  margin-top: 8px;
}
.tips-list {
  margin: 4px 0 0 16px;
  font-size: 13px;
  line-height: 1.8;
}

/* 时段轴：一条连续色带，宽度按真实时长比例分配，不画任何分割线 */
.timeline-wrap {
  width: 100%;
}
.timeline-bar {
  display: flex;
  width: 100%;
  height: 24px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  cursor: pointer;
}
.band {
  flex-basis: 0;
  flex-shrink: 0;
  min-width: 0;
}
.band.free {
  background: #67c23a;
}
.band.partial {
  background: #e6a23c;
}
.band.booked {
  background: #f56c6c;
}
.band.past {
  background: #dcdfe6;
}
.band.closed {
  background: repeating-linear-gradient(45deg, #bfbfbf, #bfbfbf 4px, #a8a8a8 4px, #a8a8a8 8px);
}
.timeline-marks {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
}
.timeline-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
  /* 点击提示靠右，不与图例挤在一起 */
  .bar-hint {
    margin-left: auto;
    font-size: 11px;
    color: #c0c4cc;
  }
  .dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 3px;
    margin-right: 4px;
    vertical-align: middle;
  }
  .dot.free {
    background: #67c23a;
  }
  .dot.partial {
    background: #e6a23c;
  }
  .dot.booked {
    background: #f56c6c;
  }
  .dot.past {
    background: #dcdfe6;
  }
  .dot.closed {
    background: repeating-linear-gradient(45deg, #bfbfbf, #bfbfbf 2px, #a8a8a8 2px, #a8a8a8 4px);
  }
}
.timeline-empty {
  color: #c0c4cc;
  font-size: 13px;
}
.time-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.opt-now-time {
  float: right;
  color: #409eff;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-family: 'Segoe UI', 'Microsoft YaHei', monospace;
}

/* ===== 教室课表视图 ===== */
.classroom-view {
  .no-reserve-tip {
    margin-bottom: 16px;
  }

  .course-bar-wrap {
    width: 100%;
  }

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
    transition:
      filter 0.15s,
      transform 0.15s;

    &:hover {
      filter: brightness(1.08);
      transform: scaleY(1.12);
    }
  }
  .course-seg.busy {
    background: #f56c6c;
    cursor: pointer;
  }
  .course-seg.free {
    background: #67c23a;
    cursor: pointer;
  }

  .course-bar-marks {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
    font-size: 11px;
    color: #909399;
  }

  /* 点击红色占用段后就地展开的课程明细 */
  .seg-course-pop {
    margin-top: 10px;
    padding: 10px 12px;
    border: 1px solid #fde2e2;
    border-radius: 8px;
    background: #fffafa;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .pop-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 12px;
      font-weight: 600;
      color: #f56c6c;
    }
    .pop-close {
      font-weight: 400;
      color: #909399;
      cursor: pointer;

      &:hover {
        color: #f56c6c;
      }
    }
  }
  .course-item {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 8px 12px;
    border-radius: 8px;
    background: #f7f8fa;
    font-size: 13px;

    &.now {
      background: #fef0f0;
      box-shadow: inset 3px 0 0 #f56c6c;
    }
    .ct {
      color: #909399;
      font-variant-numeric: tabular-nums;
      min-width: 104px;
    }
    .cn {
      color: #303133;
      font-weight: 600;
      flex: 1;
    }
    .cd {
      color: #909399;
    }
  }
}
</style>

<style lang="scss">
/* 抽屉外壳由使用方渲染（会被 teleport 到 body，作用域样式无法命中），样式随面板一起提供 */
.detail-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 18px 24px;
    border-bottom: 1px solid #ebeef5;
  }
  .el-drawer__body {
    padding: 20px 24px 28px;
    overflow-y: auto;
  }
  .drawer-header {
    display: flex;
    align-items: baseline;
    gap: 12px;

    .drawer-title {
      font-size: 17px;
      font-weight: 600;
      color: #303133;
    }
    .drawer-hint {
      font-size: 12px;
      color: #a8abb2;
    }
  }
}
</style>
