<template>
  <div class="reservation-manage">
    <el-card shadow="never" class="main-card">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="resQuery.username"
            placeholder="按用户名搜索"
            clearable
            class="f-user"
            @keyup.enter="searchReservations"
          />
          <el-select v-model="resQuery.status" placeholder="全部状态" clearable class="f-sm">
            <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="Number(k)" />
          </el-select>
          <el-select v-model="resQuery.campus" placeholder="全部校区" clearable class="f-sm">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="resQuery.resourceType" placeholder="全部类型" clearable class="f-xs">
            <el-option label="教室" :value="1" />
            <el-option label="实验室" :value="2" />
            <el-option label="图书馆" :value="3" />
            <el-option label="体育馆" :value="4" />
          </el-select>
          <el-date-picker
            v-model="resQuery.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="预约日期"
            clearable
            class="f-date"
          />
          <el-button type="primary" :icon="Search" @click="searchReservations">查询</el-button>
          <el-button :icon="Refresh" @click="resetReservations">重置</el-button>
        </div>
        <div class="toolbar-right">
          <el-popover placement="bottom-end" :width="360" trigger="hover">
            <template #reference>
              <el-button class="rule-btn" :icon="InfoFilled" circle size="small" />
            </template>
            <div class="rule-pop">
              <p>这里只管理普通用户的预约。</p>
              <p>点击「状态」下拉可直接调整，改为「使用中」即代为打卡（需在打卡时间窗内）。</p>
              <p>时段已结束的记录只能在「已结束」与「违规」之间调整。</p>
              <p>点击任意一行查看完整记录（含打卡坐标与操作时间线）。</p>
            </div>
          </el-popover>
          <el-button class="log-btn" :icon="Tickets" @click="openLogs">操作日志</el-button>
        </div>
      </div>

      <el-table
        v-loading="resLoading"
        :data="reservations"
        stripe
        style="width: 100%"
        @row-click="openDetail"
      >
        <el-table-column label="用户" min-width="170">
          <template #default="{ row }">
            <div class="cell-user">
              <span class="cell-avatar">{{ avatarOf(row.user) }}</span>
              <div class="cell-main">
                <span class="cell-title">{{ row.user?.nickname || '（已注销用户）' }}</span>
                <span class="cell-sub">{{ row.user?.username || '-' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="资源" min-width="190">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-title">{{ row.resource?.name || '未知资源' }}</span>
              <span class="cell-sub">
                {{
                  [row.resource?.campus, row.resource?.building].filter(Boolean).join(' · ') || '-'
                }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="88">
          <template #default="{ row }">{{ typeText[row.resource?.resourceType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="预约时段" min-width="185">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-title">{{ dateOf(row.startTime) }}</span>
              <span class="cell-sub">{{ timeOf(row.startTime) }} - {{ timeOf(row.endTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="126">
          <template #default="{ row }">
            <el-select
              :model-value="row.status"
              class="status-select"
              :class="'st-' + row.status"
              size="small"
              :disabled="savingId === row.id"
              @click.stop
              @change="(v) => changeStatus(row, v)"
            >
              <el-option
                v-for="code in statusOptionsOf(row)"
                :key="code"
                :label="statusText[code]"
                :value="code"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="打卡" width="140">
          <template #default="{ row }">
            <template v-if="row.checkinFlag === 1">
              <el-tag
                size="small"
                effect="plain"
                :type="row.checkinType === 3 ? 'warning' : 'success'"
              >
                {{ checkinTypeText[row.checkinType] || '已打卡' }}
              </el-tag>
              <div class="cell-sub time-sub">{{ timeOf(row.checkinTime) }}</div>
            </template>
            <span v-else class="muted">未打卡</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click.stop="openDetail(row)"
              >详情</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="resTotal"
        :current-page="resQuery.current"
        :page-size="resQuery.size"
        @current-change="onResPage"
      />
    </el-card>

    <!-- 预约详情：一行记录的全部信息 + 该预约的操作时间线 -->
    <el-drawer v-model="detailVisible" title="预约详情" size="720px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-head">
            <div>
              <div class="dt-name">{{ detail.resource?.name || '未知资源' }}</div>
              <div class="dt-sub">
                预约用户：{{ detail.user?.nickname || '-' }}（{{ detail.user?.username || '-' }}）
              </div>
            </div>
            <el-tag :type="statusTagType[detail.status] || 'info'" effect="dark">
              {{ statusText[detail.status] || '未知' }}
            </el-tag>
          </div>

          <el-descriptions :column="2" border size="small" class="dt-desc">
            <el-descriptions-item label="预约时段">
              {{ detail.startTime }} ~ {{ detail.endTime }}
            </el-descriptions-item>
            <el-descriptions-item label="使用人数">
              {{ detail.headcount || 1 }}
            </el-descriptions-item>
            <el-descriptions-item label="资源类型">
              {{ typeText[detail.resource?.resourceType] || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="位置">
              {{
                [detail.resource?.campus, detail.resource?.building, detail.resource?.floor]
                  .filter(Boolean)
                  .join(' · ') || '-'
              }}
            </el-descriptions-item>
            <el-descriptions-item label="房间号">
              {{ detail.resource?.roomNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="开放时间">
              {{ fmtTime(detail.resource?.openTime) }} - {{ fmtTime(detail.resource?.closeTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="预约备注" :span="2">
              {{ detail.remark || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{
              detail.createdAt || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{
              detail.updatedAt || '-'
            }}</el-descriptions-item>
          </el-descriptions>

          <!-- 打卡留痕：方式、时间、坐标、距离与精度 -->
          <div class="dt-sec">打卡与定位</div>
          <el-descriptions :column="2" border size="small" class="dt-desc">
            <el-descriptions-item label="打卡方式">
              {{
                checkinTypeText[detail.checkinType] ||
                (detail.checkinFlag === 1 ? '已打卡' : '未打卡')
              }}
            </el-descriptions-item>
            <el-descriptions-item label="打卡时间">
              {{ detail.checkinTime || '未打卡' }}
            </el-descriptions-item>
            <el-descriptions-item label="资源打卡点">
              <a
                v-if="detail.resource?.latitude != null"
                class="coord-link"
                :href="
                  mapLink(
                    detail.resource?.longitude,
                    detail.resource?.latitude,
                    detail.resource?.name
                  )
                "
                target="_blank"
                rel="noopener"
              >
                {{ fmtCoord(detail.resource?.latitude, detail.resource?.longitude) }}
              </a>
              <span v-else class="muted">未配置坐标</span>
            </el-descriptions-item>
            <el-descriptions-item label="打卡距离">
              {{ detail.checkinDistance == null ? '-' : detail.checkinDistance + ' 米' }}
            </el-descriptions-item>
            <el-descriptions-item label="用户打卡坐标">
              <a
                v-if="detail.checkinLatitude != null"
                class="coord-link"
                :href="mapLink(detail.checkinLongitude, detail.checkinLatitude, '打卡位置')"
                target="_blank"
                rel="noopener"
              >
                {{ fmtCoord(detail.checkinLatitude, detail.checkinLongitude) }}
              </a>
              <span v-else class="muted">无（未定位打卡）</span>
            </el-descriptions-item>
            <el-descriptions-item label="定位精度">
              {{ detail.checkinAccuracy == null ? '-' : detail.checkinAccuracy + ' 米' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 操作时间线：谁在什么时候动了这条预约 -->
          <div class="dt-sec">操作时间线</div>
          <el-empty v-if="!detailLogs.length" description="暂无操作记录" :image-size="60" />
          <el-timeline v-else class="dt-timeline">
            <el-timeline-item
              v-for="log in detailLogs"
              :key="log.id"
              :timestamp="log.createdAt"
              :color="actionColor[log.action] || '#909399'"
            >
              <div class="tl-title">
                <el-tag :type="actionTagType[log.action] || 'info'" size="small">
                  {{ actionText[log.action] || '操作' }}
                </el-tag>
                <span class="tl-role" :class="'role-' + (log.operatorRole ?? 0)">
                  操作人：{{ log.operatorName || '未知' }} ·
                  {{ operatorRoleText[log.operatorRole ?? 0] }}
                </span>
              </div>
              <div class="tl-detail">{{ log.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-drawer>

    <!-- 操作日志：翻查历史动作（用户、管理员、系统都会留痕） -->
    <el-drawer v-model="logsVisible" title="预约操作日志" size="70%" destroy-on-close>
      <div class="drawer-body">
        <div class="filters">
          <el-input
            v-model="logQuery.username"
            placeholder="按预约用户名搜索"
            clearable
            style="width: 180px"
            @keyup.enter="searchLogs"
          />
          <el-select
            v-model="logQuery.operatorRole"
            placeholder="全部操作人"
            clearable
            style="width: 150px"
          >
            <el-option label="管理员" :value="1" />
            <el-option label="用户本人" :value="0" />
            <el-option label="系统自动" :value="2" />
          </el-select>
          <el-select
            v-model="logQuery.action"
            placeholder="全部操作"
            clearable
            style="width: 140px"
          >
            <el-option v-for="(t, k) in actionText" :key="k" :label="t" :value="Number(k)" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
          <el-button :icon="Refresh" @click="resetLogs">重置</el-button>
        </div>

        <el-table v-loading="logLoading" :data="logs" stripe style="width: 100%">
          <el-table-column label="操作时间" min-width="165" prop="createdAt" />
          <el-table-column label="操作人" min-width="150">
            <template #default="{ row }">
              <div class="cell-main">
                <span class="cell-title">{{ row.operatorName || '未知' }}</span>
                <span class="cell-sub">
                  {{ operatorRoleText[row.operatorRole ?? 0] }}
                  <template v-if="row.operatorRole === 1"> · 代为操作</template>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="预约用户" min-width="140">
            <template #default="{ row }">
              <div class="cell-main">
                <span class="cell-title">{{ row.username || '（已注销用户）' }}</span>
                <span class="cell-sub">{{ row.nickname }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-tag :type="actionTagType[row.action] || 'info'" size="small">
                {{ actionText[row.action] || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="资源" min-width="160" prop="resourceName" />
          <el-table-column label="详情" min-width="280" prop="detail" show-overflow-tooltip />
        </el-table>

        <el-pagination
          class="pager"
          background
          layout="total, prev, pager, next"
          :total="logTotal"
          :current-page="logQuery.current"
          :page-size="logQuery.size"
          @current-change="onLogPage"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, Tickets, View, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const logsVisible = ref(false)

// 状态 3 在用户端已更名为「已结束」，此处保持一致
const statusText = { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }
const STATUS_CODES = [1, 2, 3, 4, 5, 6]
// 时段已结束的记录：只能在「已结束」与「违规」之间切换
const ENDED_STATUS_CODES = [3, 6]
// 打卡时间窗（与后端 ReservationService 常量一致）：开始前 30 分钟 ~ 开始后 5 分钟
const CHECKIN_ADVANCE_MINUTES = 30
const CHECKIN_LATE_MINUTES = 5

/** 解析后端时间字符串（yyyy-MM-dd HH:mm:ss）为时间戳 */
function parseTime(v) {
  if (!v) return null
  const t = new Date(String(v).replace(' ', 'T')).getTime()
  return Number.isFinite(t) ? t : null
}

/**
 * 下拉可选状态：与后端 adminUpdateStatus 的时间规定保持一致。
 * - 时段已结束：只能「已结束」/「违规」互切；
 * - 「使用中」=代为打卡，需在打卡时间窗内（或已打卡）；
 * - 「待打卡」不可在已打卡或超过打卡时限后回退。
 * 当前状态始终保留，避免下拉显示空白。
 */
function statusOptionsOf(row) {
  const now = Date.now()
  const start = parseTime(row.startTime)
  const end = parseTime(row.endTime)
  const checkedIn = row.checkinFlag === 1
  let opts
  if (end != null && end < now) {
    opts = [...ENDED_STATUS_CODES]
  } else {
    opts = [...STATUS_CODES]
    // 打卡窗口：[start-30min, start+5min]
    const windowOpen =
      start != null &&
      now >= start - CHECKIN_ADVANCE_MINUTES * 60000 &&
      now <= start + CHECKIN_LATE_MINUTES * 60000
    // 未到窗口或已超过时限且未打卡：不能代为打卡（去掉「使用中」）
    if (!checkedIn && !windowOpen) opts = opts.filter((c) => c !== 2)
    // 已打卡或已超过打卡时限：不能退回「待打卡」
    const latePassed = start != null && now > start + CHECKIN_LATE_MINUTES * 60000
    if (checkedIn || latePassed) opts = opts.filter((c) => c !== 1)
  }
  if (!opts.includes(row.status)) opts = [row.status, ...opts]
  return opts
}
const statusTagType = { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }
const typeText = { 1: '教室', 2: '实验室', 3: '图书馆', 4: '体育馆' }
// 2 为早期验证码打卡，现仅用于兼容历史数据
const checkinTypeText = { 1: '定位打卡', 2: '验证码打卡（历史）', 3: '管理员补签' }
const operatorRoleText = { 0: '用户本人', 1: '管理员', 2: '系统自动' }
const actionText = {
  1: '创建预约',
  2: '取消预约',
  3: '打卡',
  4: '爽约',
  5: '完成',
  6: '提前释放',
  // 7、8 为已下线的暂离功能，仅用于兼容历史日志
  7: '暂离（已下线）',
  8: '暂离超时（已下线）',
  9: '删除记录',
  10: '管理员调整'
}
const actionTagType = {
  1: 'success',
  2: 'info',
  3: 'primary',
  4: 'danger',
  5: 'warning',
  6: 'warning',
  7: 'primary',
  8: 'danger',
  9: 'danger',
  10: 'warning'
}
const actionColor = {
  1: '#67c23a',
  2: '#909399',
  3: '#2e76db',
  4: '#f56c6c',
  5: '#e6a23c',
  6: '#e6a23c',
  7: '#2e76db',
  8: '#f56c6c',
  9: '#909399',
  10: '#e6a23c'
}

const reservations = ref([])
const resLoading = ref(false)
const resTotal = ref(0)
// 正在保存状态的行 id，避免同一行重复提交
const savingId = ref(null)
const resQuery = reactive({
  current: 1,
  size: 10,
  username: '',
  status: null,
  campus: '',
  resourceType: null,
  date: ''
})
// 校区下拉选项来自真实资源聚合（与资源档案保持同一套校区名）
const campusOptions = ref([])

async function loadCampusOptions() {
  const res = await api.getBuildings(1)
  if (res.code === 200) {
    campusOptions.value = [...new Set((res.data || []).map((x) => x.campus).filter(Boolean))]
  }
}

const logs = ref([])
const logLoading = ref(false)
const logTotal = ref(0)
const logQuery = reactive({ current: 1, size: 10, username: '', action: null, operatorRole: null })

// 详情
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailLogs = ref([])

async function loadReservations() {
  resLoading.value = true
  try {
    const res = await api.getAdminReservations({
      current: resQuery.current,
      size: resQuery.size,
      username: resQuery.username || undefined,
      status: resQuery.status ?? undefined,
      campus: resQuery.campus || undefined,
      resourceType: resQuery.resourceType ?? undefined,
      date: resQuery.date || undefined
    })
    if (res.code === 200) {
      reservations.value = res.data.records
      resTotal.value = res.data.total
    }
  } finally {
    resLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await api.getAdminReservationLogs({
      current: logQuery.current,
      size: logQuery.size,
      username: logQuery.username || undefined,
      action: logQuery.action ?? undefined,
      operatorRole: logQuery.operatorRole ?? undefined
    })
    if (res.code === 200) {
      logs.value = res.data.records
      logTotal.value = res.data.total
    }
  } finally {
    logLoading.value = false
  }
}

function searchReservations() {
  resQuery.current = 1
  loadReservations()
}
function resetReservations() {
  resQuery.username = ''
  resQuery.status = null
  resQuery.campus = ''
  resQuery.resourceType = null
  resQuery.date = ''
  resQuery.current = 1
  loadReservations()
}
function onResPage(p) {
  resQuery.current = p
  loadReservations()
}

function searchLogs() {
  logQuery.current = 1
  loadLogs()
}
function resetLogs() {
  logQuery.username = ''
  logQuery.action = null
  logQuery.operatorRole = null
  logQuery.current = 1
  loadLogs()
}
function onLogPage(p) {
  logQuery.current = p
  loadLogs()
}

function openLogs() {
  logsVisible.value = true
  if (!logs.value.length) loadLogs()
}

/**
 * 直接在下拉里改状态：切到「使用中」= 管理员代为打卡（后端记为管理员补签），
 * 其余状态照常流转，每次调整都会写入操作日志并记在当前管理员名下。
 */
async function changeStatus(row, value) {
  const prev = row.status
  if (value === prev) return
  row.status = value
  savingId.value = row.id
  try {
    const res = await api.updateAdminReservationStatus(row.id, value)
    if (res.code === 200) {
      ElMessage.success(
        value === 2 ? '已代为打卡，状态改为使用中' : `状态已改为「${statusText[value]}」`
      )
      await loadReservations()
      if (detailVisible.value && detail.value?.id === row.id) openDetail(row)
    } else {
      row.status = prev
      ElMessage.error(res.message || '修改失败')
    }
  } catch (e) {
    row.status = prev
    ElMessage.error('修改失败，请稍后重试')
  } finally {
    savingId.value = null
  }
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailLogs.value = []
  try {
    const [d, l] = await Promise.all([
      api.getAdminReservationDetail(row.id),
      api.getAdminReservationLogs({ current: 1, size: 50, reservationId: row.id })
    ])
    if (d.code === 200) detail.value = d.data
    if (l.code === 200) {
      // 时间线按时间正序，方便顺着看流程
      detailLogs.value = (l.data.records || []).slice().reverse()
    }
  } finally {
    detailLoading.value = false
  }
}

function avatarOf(user) {
  const name = user?.nickname || user?.username || '?'
  return name.charAt(0).toUpperCase()
}
function dateOf(t) {
  return t ? String(t).replace('T', ' ').slice(0, 10) : '-'
}
function timeOf(t) {
  return t ? String(t).replace('T', ' ').slice(11, 16) : '-'
}
function fmtTime(t) {
  return t ? String(t).slice(0, 5) : '-'
}
function fmtCoord(lat, lng) {
  if (lat == null || lng == null) return ''
  return `${Number(lat).toFixed(6)}, ${Number(lng).toFixed(6)}`
}
/** 高德地图点位链接（WGS-84 坐标，浏览器直接打开可看位置） */
function mapLink(lng, lat, name) {
  if (lng == null || lat == null) return '#'
  return `https://uri.amap.com/marker?position=${lng},${lat}&name=${encodeURIComponent(name || '打卡点')}`
}

onMounted(() => {
  // 支持从「数据概览」点击卡片带筛选跳转：/admin/reservations?status=1&username=xxx
  if (route.query.status) resQuery.status = Number(route.query.status)
  if (route.query.username) resQuery.username = String(route.query.username)
  if (route.query.campus) resQuery.campus = String(route.query.campus)
  loadCampusOptions()
  loadReservations()
})
</script>

<style scoped lang="scss">
.reservation-manage {
  max-width: 1360px;
  margin: 0 auto;
}

.main-card {
  border-radius: 14px;
  border: none;
  box-shadow: 0 2px 14px rgba(17, 40, 74, 0.06);

  :deep(.el-card__body) {
    padding: 18px 20px 14px;
  }
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;

  .log-btn {
    flex-shrink: 0;
  }
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;

  .f-user {
    width: 170px;
  }
  .f-sm {
    width: 132px;
  }
  .f-xs {
    width: 118px;
  }
  .f-date {
    width: 148px;
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;

  .log-btn {
    flex-shrink: 0;
  }
}

.rule-btn {
  color: #909399;
  border-color: #dcdfe6;
}

/* 表格：表头轻量、行高舒展，避免信息挤在一起 */
:deep(.el-table) {
  --el-table-header-bg-color: #f7f9fc;
  --el-table-row-hover-bg-color: #f3f8ff;

  th.el-table__cell {
    font-size: 13px;
    font-weight: 600;
    color: #5c6b81;
  }
  td.el-table__cell {
    padding: 9px 0;
  }
  .cell {
    line-height: 1.5;
  }
}

/* 用户列：首字母头像 + 两行信息 */
.cell-user {
  display: flex;
  align-items: center;
  gap: 10px;

  .cell-avatar {
    width: 30px;
    height: 30px;
    flex-shrink: 0;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    color: #2e76db;
    background: #e8f1fd;
  }
}

.cell-main {
  display: flex;
  flex-direction: column;

  .cell-title {
    font-size: 13px;
    font-weight: 600;
    color: #2b3648;
  }
  .cell-sub {
    font-size: 12px;
    color: #8d99ab;
  }
}

.time-sub {
  margin-top: 3px;
}

.muted {
  color: #a8abb2;
  font-size: 12px;
}

/* 状态下拉：无边框形态，选中值按状态着色，点开即可切换 */
.status-select {
  width: 112px;

  :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 0 8px;
    background: transparent;
    box-shadow: none;
    border: 1px solid transparent;
    transition:
      background 0.2s,
      border-color 0.2s;

    &:hover {
      background: #fff;
      border-color: #d9e3f2;
    }
  }
  :deep(.el-select__selected-item) {
    font-size: 13px;
    font-weight: 600;
  }

  &.st-1 :deep(.el-select__selected-item) {
    color: #e6a23c;
  }
  &.st-2 :deep(.el-select__selected-item) {
    color: #2e76db;
  }
  &.st-3 :deep(.el-select__selected-item) {
    color: #7c8798;
  }
  &.st-4 :deep(.el-select__selected-item) {
    color: #a0a8b6;
  }
  &.st-5 :deep(.el-select__selected-item) {
    color: #f56c6c;
  }
  &.st-6 :deep(.el-select__selected-item) {
    color: #c45656;
  }
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.drawer-body {
  padding-bottom: 12px;
}

/* 详情抽屉 */
.detail-body {
  min-height: 200px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid #ebeef5;

  .dt-name {
    font-size: 17px;
    font-weight: 700;
    color: #1f2d3d;
  }
  .dt-sub {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}

.dt-desc {
  margin-bottom: 6px;
}

.dt-sec {
  margin: 18px 0 10px;
  padding-left: 9px;
  border-left: 3px solid #2e76db;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.coord-link {
  color: #2e76db;
  text-decoration: none;
  font-variant-numeric: tabular-nums;

  &:hover {
    text-decoration: underline;
  }
}

.dt-timeline {
  padding-left: 4px;
  max-height: 320px;
  overflow-y: auto;

  .tl-title {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .tl-role {
    font-size: 12px;
    color: #909399;
  }
  .tl-detail {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: #5a6b85;
  }
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
