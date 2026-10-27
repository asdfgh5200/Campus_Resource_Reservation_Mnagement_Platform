<template>
  <el-drawer
    v-model="visible"
    :size="drawerSize"
    :destroy-on-close="true"
    class="trace-drawer"
    @closed="onClosed"
  >
    <template #header>
      <div class="trace-head">
        <span class="trace-title">{{ title }}</span>
        <span class="trace-sub">记录当时发生了什么，可对照时间线核对</span>
      </div>
    </template>

    <div v-loading="loading" class="trace-body">
      <!-- 违规记录详情：从「违规记录」表点进来时先说明这条违规本身 -->
      <el-card v-if="violation" shadow="never" class="vio-card">
        <div class="vio-top">
          <el-tag type="danger" effect="light">{{ violationTypeText(violation.type) }}</el-tag>
          <el-tag :type="violation.status === 1 ? 'danger' : 'info'" effect="plain" size="small">
            {{ violation.status === 1 ? '有效' : '已撤销' }}
          </el-tag>
          <span class="vio-time">{{ fmt(violation.createdAt) }}</span>
        </div>
        <p class="vio-desc">{{ violation.description || '平台未填写具体说明' }}</p>
      </el-card>

      <template v-if="reservation">
        <!-- 资源与状态 -->
        <div class="res-head">
          <div class="res-thumb" :style="{ backgroundImage: `url(${image})` }"></div>
          <div class="res-main">
            <div class="res-name">{{ reservation.resource?.name || '未知资源' }}</div>
            <div class="res-loc">
              {{ locationText
              }}<span v-if="reservation.resource?.roomNo">
                · {{ reservation.resource.roomNo }}</span
              >
            </div>
            <div class="res-meta">
              <span>{{ typeText(reservation.resource?.resourceType) }}</span>
              <span v-if="reservation.resource?.openTime">
                开放 {{ hm(reservation.resource.openTime) }} -
                {{ hm(reservation.resource.closeTime) }}
              </span>
              <span>预约号 #{{ reservation.id }}</span>
            </div>
          </div>
          <el-tag :type="statusTagType(reservation.status)" effect="dark">
            {{ statusText(reservation.status) }}
          </el-tag>
        </div>

        <!-- 一句话还原当时情况 -->
        <el-alert
          :type="hint.type"
          :closable="false"
          show-icon
          class="situation"
          :title="hint.text"
        />

        <div class="sec-title">预约信息</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="预约时段" :span="2">
            {{ fmt(reservation.startTime) }} → {{ hm(reservation.endTime) }}
            <span class="muted">（{{ durationText }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="使用人数">
            {{ reservation.headcount || 1 }} 人
          </el-descriptions-item>
          <el-descriptions-item label="预约时间">{{
            fmt(reservation.createdAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ reservation.remark || '无' }}
          </el-descriptions-item>
          <el-descriptions-item v-for="node in keyNodes" :key="node.label" :label="node.label">
            {{ node.value }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">打卡情况</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="打卡状态">
            <el-tag
              :type="reservation.checkinFlag === 1 ? 'success' : 'info'"
              size="small"
              effect="plain"
            >
              {{ reservation.checkinFlag === 1 ? '已打卡' : '未打卡' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="打卡方式">
            {{ reservation.checkinFlag === 1 ? checkinTypeText(reservation.checkinType) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="打卡时间">
            {{ reservation.checkinTime ? fmt(reservation.checkinTime) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="相对预约开始">
            {{ checkinOffsetText }}
          </el-descriptions-item>
          <el-descriptions-item label="距打卡点">
            {{ reservation.checkinDistance == null ? '—' : reservation.checkinDistance + ' 米' }}
          </el-descriptions-item>
          <el-descriptions-item label="定位精度">
            {{ reservation.checkinAccuracy == null ? '—' : reservation.checkinAccuracy + ' 米' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">
          违规记录
          <span v-if="violations.length" class="sec-extra">{{ violations.length }} 条</span>
        </div>
        <div v-if="!violations.length" class="empty-line">本次预约没有产生违规记录</div>
        <div v-else class="vio-list">
          <div v-for="item in violations" :key="item.id" class="vio-row">
            <el-tag type="danger" size="small" effect="light">{{
              violationTypeText(item.type)
            }}</el-tag>
            <span class="vio-row-desc">{{ item.description || '—' }}</span>
            <el-tag :type="item.status === 1 ? 'danger' : 'info'" size="small" effect="plain">
              {{ item.status === 1 ? '有效' : '已撤销' }}
            </el-tag>
            <span class="vio-row-time">{{ fmt(item.createdAt) }}</span>
          </div>
        </div>

        <div class="sec-title">
          操作时间线
          <span class="sec-extra">{{ logs.length }} 条留痕</span>
        </div>
        <el-empty v-if="!logs.length" description="暂无操作记录" :image-size="56" />
        <el-timeline v-else class="trace-line">
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :timestamp="fmt(log.createdAt)"
            :color="actionColor[log.action] || '#909399'"
            placement="top"
          >
            <div class="tl-head">
              <el-tag :type="actionTagType[log.action] || 'info'" size="small" effect="light">
                {{ actionText[log.action] || '操作' }}
              </el-tag>
              <span class="tl-operator">{{ operatorText(log) }}</span>
            </div>
            <div class="tl-detail">{{ log.detail || '—' }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="trace-actions">
          <el-button
            v-if="reservation.resource"
            @click="emit('open-resource', reservation.resourceId)"
          >
            查看资源详情
          </el-button>
          <template v-if="showActions">
            <el-button
              v-if="reservation.status === 1"
              type="success"
              :loading="actionLoading === 'checkin'"
              @click="submit('checkin')"
              >定位打卡</el-button
            >
            <el-button
              v-if="reservation.status === 1"
              type="danger"
              plain
              :loading="actionLoading === 'cancel'"
              @click="submit('cancel')"
              >取消预约</el-button
            >
            <el-button
              v-if="reservation.status === 2"
              type="success"
              :loading="actionLoading === 'release'"
              @click="submit('release')"
              >提前释放</el-button
            >
            <el-button
              v-if="canDelete"
              type="danger"
              plain
              :loading="actionLoading === 'delete'"
              @click="submit('delete')"
              >删除这条记录</el-button
            >
          </template>
        </div>
      </template>

      <!-- 违规记录没有关联预约（管理员手动登记），只展示违规本身 -->
      <el-empty
        v-else-if="!loading && violation && !violation.reservationId"
        description="这条违规由管理员手动登记，未关联具体预约记录"
        :image-size="70"
      />
      <el-empty v-else-if="!loading" description="未找到该预约记录" :image-size="70" />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { resourceImage } from '@/utils/resourceImage'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reservationId: { type: [Number, String], default: null },
  violation: { type: Object, default: null },
  title: { type: String, default: '预约详情' },
  showActions: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'action', 'open-resource'])

const drawerSize = 'min(720px, 94vw)'
const loading = ref(false)
const actionLoading = ref('')
const trace = ref(null)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
const reservation = computed(() => trace.value?.reservation || null)
const logs = computed(() => trace.value?.logs || [])
const violations = computed(() => trace.value?.violations || [])
const image = computed(() => resourceImage(reservation.value?.resource))
const canDelete = computed(() => [3, 4, 5, 6].includes(reservation.value?.status))

// 与后端一致：开始前 30 分钟 ~ 开始后 5 分钟内可打卡
const CHECKIN_ADVANCE_MINUTES = 30
const CHECKIN_LATE_MINUTES = 5

const STATUS_TEXT = { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }
const STATUS_TAG = { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }
const TYPE_TEXT = { 1: '教室', 2: '实验室', 3: '座位', 4: '体育场馆' }
const CHECKIN_TYPE_TEXT = { 1: '定位打卡', 2: '验证码打卡', 3: '工作人员补签' }
const VIOLATION_TYPE_TEXT = { 1: '爽约', 2: '超时使用', 3: '取消超限', 4: '其他' }

const actionText = {
  1: '创建预约',
  2: '取消预约',
  3: '完成打卡',
  4: '自动爽约',
  5: '结束使用',
  6: '提前释放',
  7: '暂离',
  8: '暂离超时',
  9: '删除记录',
  10: '管理员调整'
}
const actionTagType = {
  1: 'primary',
  2: 'warning',
  3: 'success',
  4: 'danger',
  5: 'info',
  6: 'success',
  7: 'info',
  8: 'warning',
  9: 'info',
  10: 'warning'
}
const actionColor = {
  1: '#409eff',
  2: '#e6a23c',
  3: '#67c23a',
  4: '#f56c6c',
  5: '#909399',
  6: '#67c23a',
  7: '#909399',
  8: '#e6a23c',
  9: '#909399',
  10: '#e6a23c'
}

function statusText(s) {
  return STATUS_TEXT[s] || '未知'
}
function statusTagType(s) {
  return STATUS_TAG[s] || 'info'
}
function typeText(t) {
  return TYPE_TEXT[t] || '其他'
}
function checkinTypeText(t) {
  return CHECKIN_TYPE_TEXT[t] || '已打卡'
}
function violationTypeText(t) {
  return VIOLATION_TYPE_TEXT[t] || '其他'
}
// 操作人展示：系统自动 / 管理员这类角色名与姓名字段重复时只显示一次，避免「系统自动 · 系统自动」
function operatorText(log) {
  const role = log.operatorRole === 1 ? '管理员' : log.operatorRole === 2 ? '系统自动' : '本人'
  const name = log.operatorName && log.operatorName !== role ? log.operatorName : ''
  return name ? `${name} · ${role}` : role
}

function parseDt(dt) {
  return dt ? new Date(String(dt).replace(' ', 'T')) : null
}
function fmt(dt) {
  return dt ? String(dt).replace('T', ' ').slice(0, 16) : '—'
}
// 同时兼容 "HH:mm:ss"（资源开放时间）与 "yyyy-MM-dd HH:mm:ss"（预约时间）
function hm(value) {
  if (!value) return '—'
  const s = String(value).replace('T', ' ')
  return s.includes(' ') ? s.slice(11, 16) : s.slice(0, 5)
}
const locationText = computed(() => {
  const r = reservation.value?.resource
  if (!r) return '位置未知'
  return [r.campus, r.building, r.floor].filter(Boolean).join(' · ') || '位置未知'
})
const durationText = computed(() => {
  const s = parseDt(reservation.value?.startTime)
  const e = parseDt(reservation.value?.endTime)
  if (!s || !e) return '时长未知'
  const minutes = Math.round((e - s) / 60000)
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m ? `${h} 小时 ${m} 分钟` : `${h} 小时`
})

/** 打卡时间相对预约开始：提前几分钟 / 迟到几分钟；管理员补签不受时间窗限制 */
const checkinOffsetText = computed(() => {
  const r = reservation.value
  if (!r || r.checkinFlag !== 1) return '—'
  if (r.checkinType === 3) return '管理员补签（不受打卡时间窗限制）'
  const start = parseDt(r.startTime)
  const checkin = parseDt(r.checkinTime)
  if (!start || !checkin) return '—'
  const diff = Math.round((checkin - start) / 60000)
  if (diff === 0) return '准点打卡'
  return diff < 0 ? `提前 ${-diff} 分钟` : `迟 ${diff} 分钟`
})

/** 关键节点：取消 / 完成 / 爽约 / 管理员调整，直接从时间线里取，保证与留痕一致 */
const keyNodes = computed(() => {
  const find = (action) => logs.value.filter((l) => l.action === action).pop()
  const nodes = []
  const cancelLog = find(2)
  const releaseLog = find(6)
  const completeLog = find(5)
  const noshowLog = find(4)
  const adminLog = find(10)
  if (cancelLog) nodes.push({ label: '取消时间', value: fmt(cancelLog.createdAt) })
  if (releaseLog) nodes.push({ label: '提前释放', value: fmt(releaseLog.createdAt) })
  else if (completeLog) nodes.push({ label: '结束时间', value: fmt(completeLog.createdAt) })
  if (noshowLog) nodes.push({ label: '爽约判定', value: fmt(noshowLog.createdAt) })
  if (adminLog) nodes.push({ label: '管理员调整', value: fmt(adminLog.createdAt) })
  return nodes
})

/** 一句话说明"当时发生了什么" */
const hint = computed(() => {
  const r = reservation.value
  if (!r) return { type: 'info', text: '' }
  const start = parseDt(r.startTime)
  const checkin = parseDt(r.checkinTime)
  const lateMinutes = start && checkin ? Math.max(0, Math.round((checkin - start) / 60000)) : null
  switch (r.status) {
    case 1:
      return {
        type: 'warning',
        text: `预约尚待打卡：可在开始前 ${CHECKIN_ADVANCE_MINUTES} 分钟至开始后 ${CHECKIN_LATE_MINUTES} 分钟内完成定位打卡，超时未打卡会被自动记为爽约。`
      }
    case 2:
      return {
        type: 'info',
        text: checkin
          ? `已于 ${fmt(r.checkinTime)} 完成${checkinTypeText(r.checkinType)}${lateMinutes ? `（开始后 ${lateMinutes} 分钟）` : '（准点）'}，当前正在使用中。`
          : '本次预约处于使用中状态，由管理员代为打卡。'
      }
    case 3:
      return {
        type: 'success',
        text:
          r.checkinFlag === 1
            ? `本次预约已正常使用并结束，打卡时间为 ${fmt(r.checkinTime)}。`
            : '预约时段已结束，当时未产生打卡记录。'
      }
    case 4:
      return {
        type: 'info',
        text: '该预约已取消，取消次数会累计到账号的取消次数上限里。'
      }
    case 5:
      return {
        type: 'danger',
        text: `开始后 ${CHECKIN_LATE_MINUTES} 分钟内未完成打卡，系统已自动记为爽约并释放了资源。`
      }
    case 6:
      return {
        type: 'danger',
        text: violations.value.some((v) => v.status === 1)
          ? `该记录被标记为违规：${violations.value.find((v) => v.status === 1)?.description || '具体说明见下方违规记录'}`
          : '该记录曾被标记为违规，相关违规记录已撤销。'
      }
    default:
      return { type: 'info', text: '暂无状态说明' }
  }
})

async function load() {
  const id = props.reservationId ?? props.violation?.reservationId
  if (!id) {
    trace.value = null
    return
  }
  loading.value = true
  try {
    const res = await api.getReservationTrace(id)
    trace.value = res.code === 200 ? res.data : null
    if (res.code !== 200) ElMessage.error(res.message || '加载预约详情失败')
  } finally {
    loading.value = false
  }
}

function submit(type) {
  if (!reservation.value) return
  emit('action', { type, reservation: reservation.value })
}

function onClosed() {
  trace.value = null
  actionLoading.value = ''
}

watch(
  () => [props.modelValue, props.reservationId, props.violation],
  ([open]) => {
    if (open) load()
  },
  { immediate: true }
)

defineExpose({ reload: load })
</script>

<style scoped lang="scss">
.trace-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  .trace-title {
    font-size: 16px;
    font-weight: 600;
    color: #24344d;
  }
  .trace-sub {
    font-size: 12px;
    color: #8a94a6;
  }
}

.trace-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.vio-card {
  border: none;
  border-radius: 12px;
  background: #fff6f6;
  .vio-top {
    display: flex;
    align-items: center;
    gap: 8px;
    .vio-time {
      margin-left: auto;
      font-size: 12px;
      color: #8a94a6;
    }
  }
  .vio-desc {
    margin: 8px 0 0;
    font-size: 13px;
    color: #5b6572;
    line-height: 1.6;
  }
}

.res-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f7f9fc;
  .res-thumb {
    width: 64px;
    height: 64px;
    border-radius: 10px;
    background-size: cover;
    background-position: center;
    flex-shrink: 0;
  }
  .res-main {
    min-width: 0;
    flex: 1;
    .res-name {
      font-size: 15px;
      font-weight: 600;
      color: #24344d;
    }
    .res-loc {
      margin-top: 4px;
      font-size: 12px;
      color: #6b7787;
    }
    .res-meta {
      margin-top: 6px;
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      font-size: 12px;
      color: #98a1ae;
    }
  }
}

.situation {
  border-radius: 10px;
}

.sec-title {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 600;
  color: #24344d;
  display: flex;
  align-items: center;
  gap: 8px;
  .sec-extra {
    font-size: 12px;
    font-weight: 400;
    color: #98a1ae;
  }
}

.empty-line {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7f9fc;
  font-size: 13px;
  color: #8a94a6;
}

.vio-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  .vio-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 10px;
    background: #f7f9fc;
    .vio-row-desc {
      flex: 1;
      font-size: 13px;
      color: #5b6572;
    }
    .vio-row-time {
      font-size: 12px;
      color: #98a1ae;
    }
  }
}

.trace-line {
  padding-left: 2px;
  .tl-head {
    display: flex;
    align-items: center;
    gap: 8px;
    .tl-operator {
      font-size: 12px;
      color: #8a94a6;
    }
  }
  .tl-detail {
    margin-top: 4px;
    font-size: 13px;
    color: #5b6572;
    line-height: 1.6;
  }
}

.trace-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.muted {
  color: #98a1ae;
  font-size: 12px;
}
</style>
