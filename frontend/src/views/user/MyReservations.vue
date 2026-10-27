<template>
  <div class="my-reservations-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="0" />
      <el-tab-pane label="待打卡" name="1" />
      <el-tab-pane label="使用中" name="2" />
      <el-tab-pane label="已结束" name="3" />
      <el-tab-pane label="已取消" name="4" />
    </el-tabs>

    <div v-loading="loading" class="reservation-list">
      <!-- 日期筛选 + 统一删除入口：筛选常驻，避免筛出 0 条后没地方取消筛选 -->
      <div class="list-toolbar">
        <div class="toolbar-left">
          <el-date-picker
            v-model="queryDate"
            class="date-filter"
            type="date"
            size="small"
            placeholder="按预约日期筛选"
            value-format="YYYY-MM-DD"
            clearable
            @change="onDateChange"
          />
          <span class="toolbar-hint"
            >共 {{ reservations.length }} 条记录，其中 {{ deletableList.length }} 条可删除</span
          >
          <!-- 取消次数达上限：悬停查看完整说明，不再占用页面顶部 -->
          <el-tooltip v-if="!cancelState.canCancel" placement="top">
            <template #content>
              您的预约取消次数已达上限（{{ cancelState.count }} /
              {{
                cancelState.limit
              }}），暂不可继续取消预约。如需帮助，请到「消息中心」发消息说明情况。
            </template>
            <span class="cancel-limit-chip">
              <el-icon><WarningFilled /></el-icon>
              取消次数已达上限 {{ cancelState.count }}/{{ cancelState.limit }}
            </span>
          </el-tooltip>
        </div>
        <el-button
          :icon="Delete"
          size="small"
          :disabled="deletableList.length === 0"
          @click="openCleanup"
          >批量删除记录</el-button
        >
      </div>

      <div
        v-for="item in reservations"
        :key="item.id"
        class="reservation-card clickable"
        @click="onCardClick(item)"
      >
        <div class="card-left" :style="{ backgroundImage: `url(${getTypeImage(item.resource)})` }">
          <div class="card-left-overlay"></div>
        </div>
        <div class="card-content">
          <div class="card-top">
            <div class="name-wrap">
              <h3 class="res-name">{{ item.resource?.name || '未知资源' }}</h3>
              <span class="detail-hint">
                <el-icon><View /></el-icon> 点击查看当时情况
              </span>
            </div>
            <el-tag :type="statusTagType(item.status)" effect="light" size="small">{{
              statusText(item.status)
            }}</el-tag>
          </div>
          <div class="card-info">
            <span class="info-line"
              ><el-icon><Clock /></el-icon> {{ item.startTime }} → {{ item.endTime }}</span
            >
            <span class="info-line" v-if="item.checkinFlag"
              ><el-icon><CircleCheck /></el-icon> 已打卡（{{
                checkinTypeLabel(item.checkinType)
              }}）{{ item.checkinTime }}</span
            >
            <span class="info-line" v-if="item.status === 2 && item.endTime"
              ><el-icon><Timer /></el-icon> 距预约结束
              <b class="cd">{{ countdownText(parseDt(item.endTime)) }}</b></span
            >
            <span class="info-line" v-if="item.status === 1"
              ><el-icon><AlarmClock /></el-icon> 距打卡截止
              <b
                class="cd"
                :class="{
                  urgent:
                    cdSeconds(checkinDeadline(item)) !== null &&
                    cdSeconds(checkinDeadline(item)) < 300
                }"
                >{{ countdownText(checkinDeadline(item)) }}</b
              ></span
            >
          </div>
          <div class="card-footer">
            <span class="remark" v-if="item.remark">备注：{{ item.remark }}</span>
            <div class="actions" @click.stop>
              <el-button
                v-if="item.status === 1"
                type="success"
                size="small"
                :icon="Location"
                :loading="locatingId === item.id"
                :disabled="locatingId !== null && locatingId !== item.id"
                @click="doLocationCheckin(item)"
                >定位打卡</el-button
              >
              <el-tooltip
                v-if="item.status === 1"
                :disabled="cancelState.canCancel"
                content="取消次数已达上限，暂不可取消"
                placement="top"
              >
                <span class="cancel-slot">
                  <el-button
                    type="danger"
                    size="small"
                    plain
                    :icon="Close"
                    :disabled="!cancelState.canCancel"
                    @click="doCancel(item)"
                    >取消</el-button
                  >
                </span>
              </el-tooltip>
              <el-button
                v-if="item.status === 2"
                type="success"
                size="small"
                @click="doRelease(item)"
                >提前释放</el-button
              >
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && reservations.length === 0" :description="emptyText" />

    <!-- 批量删除记录 -->
    <el-dialog v-model="cleanupVisible" title="批量删除预约记录" width="560px">
      <el-alert type="info" :closable="false" show-icon class="cleanup-tip">
        <template #title
          >仅"已结束"的记录可删除（已结束 / 已取消 / 爽约 /
          违规）；删除后不再显示，平台仍保留相关操作记录。</template
        >
      </el-alert>
      <div class="cleanup-bar">
        <el-checkbox
          v-model="cleanupAll"
          :indeterminate="cleanupIndeterminate"
          @change="onCleanupAllChange"
          >全选</el-checkbox
        >
      </div>
      <el-checkbox-group v-model="selectedIds" class="cleanup-list">
        <div v-for="item in deletableList" :key="item.id" class="cleanup-item">
          <el-checkbox :value="item.id" />
          <span class="ci-name">{{ item.resource?.name || '未知资源' }}</span>
          <span class="ci-status">{{ statusText(item.status) }}</span>
          <span class="ci-time">{{ item.startTime }} → {{ item.endTime }}</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="cleanupVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="cleanupLoading"
          :disabled="selectedIds.length === 0"
          @click="doCleanup"
        >
          删除选中（{{ selectedIds.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 预约详情：整张卡片可点，侧拉框还原当时的情况（打卡、违规、操作时间线） -->
    <ReservationTracePanel
      ref="tracePanelRef"
      v-model="traceVisible"
      :reservation-id="traceId"
      @action="onTraceAction"
      @open-resource="openResourceDetail"
    />

    <!-- 资源详情：点开卡片用抽屉展示，不跳页、不打乱当前列表 -->
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
          <span class="drawer-hint">关闭后回到我的预约</span>
        </div>
      </template>
      <ResourceDetailPanel v-if="detailId" :resource-id="detailId" @close="detailVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  Clock,
  CircleCheck,
  Close,
  Timer,
  Delete,
  View,
  AlarmClock,
  Location,
  WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import ResourceDetailPanel from '@/components/ResourceDetailPanel.vue'
import ReservationTracePanel from '@/components/ReservationTracePanel.vue'
import { resourceImage } from '@/utils/resourceImage'

const loading = ref(false)
const activeTab = ref('0')
const reservations = ref([])
const page = reactive({ current: 1, size: 20 })
// 日期筛选：精确到单个自然日（按预约开始时间所属日期）
const queryDate = ref('')
// 正在定位打卡的预约 ID（同时间只允许一次定位请求）
const locatingId = ref(null)
// 取消次数 / 上限：达到上限后不能再自行取消（后端同样拦截），这里提前置灰入口
const cancelState = reactive({ count: 0, limit: 5, canCancel: true })

// 每秒刷新一次：打卡截止 / 预约结束的实时倒计时
const now = ref(new Date())
let timer = null
let lastAutoReload = 0

// 与后端 ReservationService.CHECKIN_ADVANCE_MINUTES 保持一致：可提前 30 分钟打卡
const CHECKIN_ADVANCE_MINUTES = 30
// 与后端 ReservationService.CHECKIN_LATE_MINUTES 保持一致：开始后 5 分钟内未打卡自动爽约
const CHECKIN_LATE_MINUTES = 5

// 打卡方式：2 为早期验证码打卡，现仅用于兼容历史数据
function checkinTypeLabel(t) {
  return { 1: '定位打卡', 2: '验证码打卡', 3: '工作人员补签' }[t] || '已打卡'
}

function parseDt(dt) {
  return dt ? new Date(String(dt).replace(' ', 'T')) : null
}

/** 待打卡记录的截止时刻 = 预约开始 + 打卡宽限 */
function checkinDeadline(item) {
  const s = parseDt(item.startTime)
  return s ? new Date(s.getTime() + CHECKIN_LATE_MINUTES * 60000) : null
}

function cdSeconds(target) {
  if (!target) return null
  return Math.max(0, Math.floor((target.getTime() - now.value.getTime()) / 1000))
}

function countdownText(target) {
  const sec = cdSeconds(target)
  if (sec === null) return '--:--'
  const p = (n) => String(n).padStart(2, '0')
  if (sec >= 3600)
    return `${Math.floor(sec / 3600)}:${p(Math.floor((sec % 3600) / 60))}:${p(sec % 60)}`
  return `${p(Math.floor(sec / 60))}:${p(sec % 60)}`
}

function tick() {
  now.value = new Date()
  // 已到截止/超时点但状态还没流转 → 重新拉列表（后端懒惰结算会自动爽约/释放/完成），10s 防抖
  const hasExpired = reservations.value.some((it) => {
    if (it.status === 1) {
      const d = checkinDeadline(it)
      return d && d <= now.value
    }
    if (it.status === 2 && it.endTime) {
      const e = parseDt(it.endTime)
      return e && e <= now.value
    }
    return false
  })
  if (hasExpired && Date.now() - lastAutoReload > 10000) {
    lastAutoReload = Date.now()
    loadData()
  }
}

function getTypeImage(res) {
  return resourceImage(res)
}
function statusText(s) {
  return { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }[s] || '未知'
}
// 配色：待打卡=黄、使用中=蓝、已结束/已取消=灰、爽约/违规=红
function statusTagType(s) {
  return { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }[s] || 'info'
}

// 可删除：仅限已结束的预约（已结束 / 已取消 / 爽约 / 违规）
function canDelete(item) {
  return [3, 4, 5, 6].includes(item.status)
}

// 资源详情抽屉：点开卡片在本页展开，不跳页
const detailVisible = ref(false)
const detailId = ref(null)
// 与首页 / 分类页共用同一抽屉宽度
const detailDrawerSize = 'min(760px, 94vw)'

// 预约详情（侧拉框）：还原“当时的情况”——打卡、违规、时间线，并可直接完成操作
const traceVisible = ref(false)
const traceId = ref(null)
const tracePanelRef = ref(null)

function onCardClick(item) {
  if (!item?.id) return
  traceId.value = item.id
  traceVisible.value = true
}

// 抽屉里点「查看资源详情」：先关详情再开资源抽屉，避免两层抽屉叠着
function openResourceDetail(resourceId) {
  if (!resourceId) return
  traceVisible.value = false
  detailId.value = resourceId
  detailVisible.value = true
}

// 抽屉底部操作直接复用列表里的同一套逻辑，处理完刷新抽屉与列表
async function onTraceAction({ type, reservation }) {
  if (type === 'checkin') await doLocationCheckin(reservation)
  else if (type === 'cancel') await doCancel(reservation)
  else if (type === 'release') await doRelease(reservation)
  else if (type === 'delete') return doDeleteOne(reservation)
  // 打卡/取消/释放后刷新抽屉，让用户当场看到状态与时间线变化
  if (traceVisible.value) tracePanelRef.value?.reload()
}

async function doDeleteOne(item) {
  try {
    await ElMessageBox.confirm(
      '确定删除这条预约记录？删除后不再显示在「我的预约」中，平台仍保留操作留痕。',
      '删除记录',
      { type: 'warning', confirmButtonText: '确定删除' }
    )
  } catch {
    return
  }
  const res = await api.deleteReservation(item.id)
  if (res.code === 200) {
    ElMessage.success('已删除该记录')
    traceVisible.value = false
    loadData()
  } else {
    ElMessage.error(res.message || '删除失败')
  }
}

function onTabChange() {
  page.current = 1
  loadData()
}

function onDateChange() {
  page.current = 1
  loadData()
}

const emptyText = computed(() =>
  queryDate.value || activeTab.value !== '0' ? '该条件下暂无预约记录' : '暂无预约记录'
)

async function loadData() {
  loading.value = true
  try {
    const res = await api.getMyReservations({
      current: page.current,
      size: page.size,
      status: activeTab.value !== '0' ? activeTab.value : undefined,
      date: queryDate.value || undefined
    })
    if (res.code === 200) {
      reservations.value = res.data.records
    }
  } finally {
    loading.value = false
  }
}

async function loadCancelState() {
  const res = await api.getMyViolationSummary()
  if (res.code === 200) {
    cancelState.count = res.data?.cancelCount ?? 0
    cancelState.limit = res.data?.cancelLimit ?? 5
    cancelState.canCancel = res.data?.canCancel !== false
  }
}

function getBrowserLocation() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 12000,
      maximumAge: 0
    })
  })
}

// 与后端一致：预约开始前 30 分钟 ~ 开始后 5 分钟可打卡
function canCheckinNow(item) {
  const start = parseDt(item.startTime)
  if (!start) return true
  const from = new Date(start.getTime() - CHECKIN_ADVANCE_MINUTES * 60000)
  const to = new Date(start.getTime() + CHECKIN_LATE_MINUTES * 60000)
  return now.value >= from && now.value <= to
}

async function doLocationCheckin(item) {
  if (locatingId.value !== null) return
  if (!canCheckinNow(item)) {
    ElMessage.warning(
      `未到打卡时间：请在预约开始前 ${CHECKIN_ADVANCE_MINUTES} 分钟 ~ 开始后 ${CHECKIN_LATE_MINUTES} 分钟内打卡`
    )
    return
  }
  locatingId.value = item.id
  try {
    const position = await getBrowserLocation()
    const { latitude, longitude, accuracy } = position.coords
    const res = await api.checkinByLocation({
      reservationId: item.id,
      latitude,
      longitude,
      accuracy
    })
    if (res.code === 200) {
      ElMessage.success('定位打卡成功')
      loadData()
    } else {
      ElMessage.error(res.message)
    }
  } catch (error) {
    const message =
      {
        1: '定位权限被拒绝，请在浏览器设置中允许定位后重试',
        2: '暂时无法获取位置，请确认设备定位服务和网络正常',
        3: '获取定位超时，请在信号更好的位置重试'
      }[error.code] ||
      error.message ||
      '定位打卡失败'
    ElMessage.error(message)
  } finally {
    locatingId.value = null
  }
}

async function doCancel(item) {
  if (!cancelState.canCancel) {
    ElMessage.warning('预约取消次数已达上限，暂不可继续取消')
    return
  }
  const next = cancelState.count + 1
  try {
    await ElMessageBox.confirm(
      `确定取消此预约？这将记为第 ${next} 次取消，累计达 ${cancelState.limit} 次后，账号将无法再自行取消预约。`,
      '取消预约',
      { type: 'warning', confirmButtonText: '确定取消' }
    )
    const res = await api.cancelReservation(item.id)
    if (res.code === 200) {
      ElMessage.success('已取消')
      loadData()
      loadCancelState()
    } else {
      ElMessage.error(res.message || '取消失败')
      loadCancelState()
    }
  } catch {
    /* cancelled */
  }
}

// ---- 批量删除（统一入口）----
const cleanupVisible = ref(false)
const cleanupLoading = ref(false)
const selectedIds = ref([])
const cleanupAll = ref(false)
const cleanupIndeterminate = computed(
  () => selectedIds.value.length > 0 && selectedIds.value.length < deletableList.value.length
)

const deletableList = computed(() => reservations.value.filter(canDelete))

function openCleanup() {
  selectedIds.value = []
  cleanupAll.value = false
  cleanupVisible.value = true
}
function onCleanupAllChange(val) {
  selectedIds.value = val ? deletableList.value.map((r) => r.id) : []
}
async function doCleanup() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 条预约记录？删除后不再显示，平台仍保留相关操作记录。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '确定删除' }
    )
  } catch {
    return
  }
  cleanupLoading.value = true
  try {
    let ok = 0
    const fails = []
    for (const id of selectedIds.value) {
      const res = await api.deleteReservation(id)
      if (res.code === 200) ok++
      else fails.push(res.message)
    }
    if (fails.length) {
      ElMessage.warning(`成功删除 ${ok} 条，${fails.length} 条失败：${fails[0]}`)
    } else {
      ElMessage.success(`已删除 ${ok} 条记录`)
    }
    cleanupVisible.value = false
    loadData()
  } finally {
    cleanupLoading.value = false
  }
}

async function doRelease(item) {
  try {
    await ElMessageBox.confirm(
      '提前释放后将立即结束本次预约并释放空间，确定继续？',
      '提前释放空间',
      { type: 'warning', confirmButtonText: '确定释放' }
    )
  } catch {
    return
  }
  const res = await api.releaseReservation(item.id)
  if (res.code === 200) {
    ElMessage.success('已提前释放空间')
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

onMounted(() => {
  loadData()
  loadCancelState()
  timer = setInterval(tick, 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.my-reservations-page {
  max-width: 900px;
  margin: 0 auto;
}

.cancel-slot {
  display: inline-flex;
}

.reservation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .toolbar-hint {
    font-size: 13px;
    color: #909399;
  }
  .date-filter {
    width: 152px;
  }
  /* 取消次数达上限：悬停看详情的小标签 */
  .cancel-limit-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 12px;
    line-height: 18px;
    color: #f56c6c;
    background: #fef0f0;
    cursor: help;
  }
}

.cleanup-tip {
  margin-bottom: 10px;
}
.cleanup-bar {
  padding: 0 2px 6px;
}
.cleanup-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 320px;
  overflow-y: auto;
  .cleanup-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 10px;
    border-radius: 8px;
    &:hover {
      background: #f5f7fa;
    }
    .ci-name {
      flex: 1;
      font-size: 13px;
      color: #303133;
    }
    .ci-status {
      font-size: 12px;
      color: #909399;
    }
    .ci-time {
      font-size: 12px;
      color: #a8abb2;
    }
  }
}

.reservation-card {
  display: flex;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition:
    transform 0.2s,
    box-shadow 0.2s;

  &:hover {
    transform: translateX(4px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }

  /* 整张卡片可点击：侧拉框里查看这次预约当时的情况 */
  &.clickable {
    cursor: pointer;
    &:hover {
      box-shadow: 0 6px 20px rgba(64, 158, 255, 0.28);
    }
  }

  .card-left {
    width: 80px;
    background-size: cover;
    background-position: center;
    flex-shrink: 0;
    position: relative;

    .card-left-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(90deg, rgba(0, 0, 0, 0.25) 0%, rgba(0, 0, 0, 0.1) 100%);
    }
  }

  .card-content {
    flex: 1;
    padding: 16px 20px;

    .card-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .name-wrap {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;

        .detail-hint {
          display: inline-flex;
          align-items: center;
          gap: 3px;
          font-size: 12px;
          color: #409eff;
        }
      }

      .res-name {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }
    }

    .card-info {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 8px;

      .info-line {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: #909399;

        .cd {
          font-weight: 600;
          color: #409eff;
          font-variant-numeric: tabular-nums;
        }
        .cd.urgent {
          color: #f56c6c;
        }
      }
    }

    .card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .remark {
        font-size: 13px;
        color: #a8abb2;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        justify-content: flex-end;
      }
    }
  }
}
</style>
