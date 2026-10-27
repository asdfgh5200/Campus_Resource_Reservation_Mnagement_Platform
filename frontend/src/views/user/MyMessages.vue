<template>
  <div class="my-messages">
    <!-- 顶部概览 -->
    <div class="msg-hero">
      <div class="hero-main">
        <span class="hero-icon"
          ><el-icon><ChatDotRound /></el-icon
        ></span>
        <div class="hero-text">
          <div class="hero-title">消息中心</div>
          <div class="hero-sub">
            与工作人员的沟通在「沟通」里，平台推送在「通知」里，报修进度在「维修工单」里，点开即视为已读
          </div>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat" :class="{ alert: chatUnread > 0 }">
          <b>{{ chatUnread }}</b
          ><span>沟通未读</span>
        </div>
        <i class="stat-divider"></i>
        <div class="stat" :class="{ alert: noticeUnread > 0 }">
          <b>{{ noticeUnread }}</b
          ><span>通知未读</span>
        </div>
        <i class="stat-divider"></i>
        <div class="stat">
          <b>{{ openRepairCount }}</b
          ><span>进行中工单</span>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-hd">
          <div class="seg-switch">
            <button class="seg-item" :class="{ active: tab === 'chat' }" @click="tab = 'chat'">
              沟通
              <em v-if="chatUnread">{{ chatUnread }}</em>
            </button>
            <button class="seg-item" :class="{ active: tab === 'notice' }" @click="tab = 'notice'">
              通知
              <em v-if="noticeUnread">{{ noticeUnread }}</em>
            </button>
            <button class="seg-item" :class="{ active: tab === 'repair' }" @click="tab = 'repair'">
              维修工单
              <em v-if="openRepairCount">{{ openRepairCount }}</em>
            </button>
          </div>
          <div class="panel-actions">
            <el-date-picker
              v-model="range"
              class="date-filter"
              type="daterange"
              size="small"
              unlink-panels
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :shortcuts="rangeShortcuts"
            />
            <el-button
              v-if="tab !== 'repair'"
              link
              type="primary"
              size="small"
              :disabled="!unreadTotal"
              @click="markAllRead"
              >全部标为已读</el-button
            >
            <el-button link size="small" :icon="Refresh" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 1. 和管理员沟通：一段连续的对话流，输入框贴在底部 -->
      <div v-show="tab === 'chat'" class="chat-pane">
        <div ref="chatBox" class="chat-list">
          <el-empty v-if="!chatRows.length" :image-size="86" :description="emptyChatText" />
          <template v-else>
            <div v-for="row in chatRows" :key="row.m.id">
              <div v-if="row.showDay" class="day-line">
                <span>{{ dayText(row.m.createdAt) }}</span>
              </div>
              <div
                class="chat-row"
                :class="row.m.direction === 1 ? 'right' : 'left'"
                @click="readOne(row.m)"
              >
                <span v-if="row.m.direction !== 1" class="avatar adm">管</span>
                <div class="bubble-wrap">
                  <div class="bubble" :class="row.m.direction === 1 ? 'mine' : 'adm'">
                    {{ row.m.content }}
                  </div>
                  <div class="bubble-meta" :class="{ right: row.m.direction === 1 }">
                    <span class="tm">{{ clockText(row.m.createdAt) }}</span>
                    <span v-if="row.m.direction === 2 && row.m.isRead === 0" class="unread-tag"
                      >未读</span
                    >
                  </div>
                </div>
                <span v-if="row.m.direction === 1" class="avatar">我</span>
              </div>
            </div>
          </template>
        </div>

        <div class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            resize="none"
            maxlength="1000"
            show-word-limit
            placeholder="描述你遇到的问题或想提的建议，例如：3F-01 自习位的插座损坏、希望延长开放时间……"
            @keydown.ctrl.enter="sendAsk"
          />
          <div class="composer-row">
            <span class="composer-hint">提交后在这里继续查看回复，Ctrl + Enter 快捷发送</span>
            <el-button type="primary" :loading="sending" @click="sendAsk">发送</el-button>
          </div>
        </div>
      </div>

      <!-- 2. 接收管理员的通知：单向推送，只展示阅读状态 -->
      <div v-show="tab === 'notice'" class="notice-list">
        <el-empty v-if="!noticeRows.length" :image-size="86" :description="emptyNoticeText" />
        <template v-else>
          <div
            v-for="n in noticeRows"
            :key="n.id"
            class="notice"
            :class="{ unread: n.isRead === 0 }"
            @click="readOne(n)"
          >
            <span class="notice-icon" :class="{ hot: n.isRead === 0 }">
              <el-icon><Bell /></el-icon>
            </span>
            <div class="notice-main">
              <div class="notice-text">{{ n.content }}</div>
              <div class="notice-meta">
                <span class="time">{{ formatTime(n.createdAt) }}</span>
                <span class="state" :class="{ hot: n.isRead === 0 }">{{
                  n.isRead === 0 ? '未读' : '已读'
                }}</span>
                <el-button
                  v-if="n.resourceId"
                  class="notice-link"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openNoticeResource(n)"
                  >查看地点</el-button
                >
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 3. 维修工单：提交申请 + 我的工单进度（可跳转到地点核对状态） -->
      <div v-show="tab === 'repair'" class="repair-pane">
        <div class="repair-form">
          <div class="form-title">
            <el-icon><Tools /></el-icon>
            <span>提交维修申请</span>
          </div>
          <el-form :model="repairForm" label-width="86px" size="default">
            <el-form-item label="维修地点">
              <div class="loc-filter-row">
                <el-select
                  v-model="campusFilter"
                  class="lc-campus"
                  clearable
                  placeholder="校区"
                  @change="onCampusChange"
                >
                  <el-option
                    v-for="c in campusOptions"
                    :key="c.name"
                    :label="`${c.name}（${c.total}）`"
                    :value="c.name"
                  />
                </el-select>
                <el-select
                  v-model="buildingFilter"
                  class="lc-building"
                  clearable
                  filterable
                  placeholder="楼栋"
                  @change="onBuildingChange"
                >
                  <el-option v-for="b in buildingOptions" :key="b" :label="b" :value="b" />
                </el-select>
                <el-select
                  v-model="repairForm.resourceId"
                  class="lc-resource"
                  filterable
                  remote
                  clearable
                  :remote-method="searchResources"
                  :loading="resourceLoading"
                  placeholder="搜索资源名称或房间号，也可直接选"
                >
                  <el-option
                    v-for="r in resourceOptions"
                    :key="r.id"
                    :label="resourceLabel(r)"
                    :value="r.id"
                  >
                    <span class="opt-name">{{ r.name }}</span>
                    <span class="opt-loc">{{ locationText(r) }}</span>
                    <span class="opt-status">{{ statusText(r.status) }}</span>
                  </el-option>
                </el-select>
              </div>
            </el-form-item>
            <el-form-item label="问题描述">
              <el-input
                v-model="repairForm.problem"
                type="textarea"
                :rows="3"
                resize="none"
                maxlength="300"
                show-word-limit
                placeholder="例如：3F-01 自习位的插座没电、投影仪无法开机、空调漏水……"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submittingRepair" @click="submitRepair"
                >提交工单</el-button
              >
              <span class="form-tip"
                >提交后进入待审核；审核、维修、完成每个环节都会在「通知」里提醒你</span
              >
            </el-form-item>
          </el-form>
        </div>

        <div class="repair-list">
          <el-empty v-if="!repairRows.length" :image-size="86" :description="emptyRepairText" />
          <template v-else>
            <div v-for="o in repairRows" :key="o.id" class="repair-card">
              <div class="rc-head">
                <span class="rc-name">{{ o.resource?.name || `资源 #${o.resourceId}` }}</span>
                <el-tag :type="orderTagType(o.status)" size="small" effect="light">{{
                  orderStatusText(o.status)
                }}</el-tag>
                <span class="rc-time">{{ formatTime(o.createdAt) }}</span>
              </div>
              <div class="rc-loc">
                <el-icon><Position /></el-icon>
                <span>{{ locationText(o.resource) }}</span>
              </div>
              <div class="rc-problem">{{ o.problem }}</div>
              <div class="rc-steps">
                <span class="rstep" :class="stepState(o.status, 0)">已提交</span>
                <i class="rline" :class="stepState(o.status, 1)"></i>
                <span class="rstep" :class="stepState(o.status, 1)">审核</span>
                <i class="rline" :class="stepState(o.status, 2)"></i>
                <span class="rstep" :class="stepState(o.status, 2)">维修完成</span>
              </div>
              <div class="rc-hint" :class="{ rejected: o.status === 3 }">
                <el-icon><InfoFilled /></el-icon>
                <span>{{ orderHint(o) }}</span>
              </div>
              <div class="rc-foot">
                <span class="rc-state"
                  >地点状态
                  <em :class="'s' + (o.resource?.status || 0)">{{
                    statusText(o.resource?.status)
                  }}</em></span
                >
                <span v-if="o.handleTime" class="rc-time"
                  >处理于 {{ formatTime(o.handleTime) }}</span
                >
                <el-button
                  v-if="o.resource"
                  link
                  type="primary"
                  :icon="Position"
                  @click="gotoResource(o)"
                  >查看地点</el-button
                >
              </div>
            </div>
          </template>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Bell, ChatDotRound, Tools, Position, InfoFilled } from '@element-plus/icons-vue'
import api from '@/api'

const router = useRouter()

// 维修地点下拉单次最多取多少条（配合校区/楼栋筛选与关键词搜索）
const RESOURCE_OPTION_LIMIT = 50

const tab = ref('chat')
const draft = ref('')
const sending = ref(false)
const range = ref([])
const chatBox = ref(null)

// 我的消息 = 我发出的留言（direction=1）+ 收到的回复/通知（direction=2，refId 区分回复与通知）
const messages = ref([])
// 我提交的维修工单
const repairs = ref([])
// 维修申请的地点下拉数据
const resourceOptions = ref([])
const resourceLoading = ref(false)
const submittingRepair = ref(false)
const repairForm = ref({ resourceId: undefined, problem: '' })
// 维修地点的校区 / 楼栋筛选（楼栋随校区级联）
const buildings = ref([])
const campusFilter = ref('')
const buildingFilter = ref('')

const rangeShortcuts = [
  {
    text: '今天',
    value: () => [new Date(), new Date()]
  },
  {
    text: '近 7 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 6)
      return [start, end]
    }
  },
  {
    text: '近 30 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 29)
      return [start, end]
    }
  }
]

/* ---------------- 日期筛选：三个页签共用 ---------------- */

function dateOf(t) {
  return t ? String(t).replace('T', ' ').slice(0, 10) : ''
}

function inRange(t) {
  if (!range.value || range.value.length !== 2) return true
  const d = dateOf(t)
  return !!d && d >= range.value[0] && d <= range.value[1]
}

/* ---------------- 沟通（与管理员对话） ---------------- */

const chatMessages = computed(() =>
  messages.value.filter((m) => m.direction === 1 || (m.direction === 2 && m.refId))
)
const chatUnread = computed(
  () => chatMessages.value.filter((m) => m.direction === 2 && m.isRead === 0).length
)

// 平铺成一条时间正序的对话，跨天时插一条日期分隔
const chatRows = computed(() => {
  const list = chatMessages.value
    .filter((m) => inRange(m.createdAt))
    .slice()
    .sort((a, b) => timeValue(a.createdAt) - timeValue(b.createdAt))
  let prevDay = ''
  return list.map((m) => {
    const day = dateOf(m.createdAt)
    const row = { m, showDay: day !== prevDay }
    prevDay = day
    return row
  })
})

const emptyChatText = computed(() =>
  chatMessages.value.length
    ? '该时间范围内没有沟通记录'
    : '还没有沟通记录，在下方输入框留言即可联系工作人员'
)

/* ---------------- 通知 ---------------- */

const notices = computed(() => messages.value.filter((m) => m.direction === 2 && !m.refId))
const noticeUnread = computed(() => notices.value.filter((m) => m.isRead === 0).length)
const noticeRows = computed(() => notices.value.filter((n) => inRange(n.createdAt)))
const emptyNoticeText = computed(() =>
  notices.value.length ? '该时间范围内没有通知' : '还没有收到通知，有新的平台消息会出现在这里'
)

const unreadTotal = computed(() => chatUnread.value + noticeUnread.value)

/* ---------------- 维修工单 ---------------- */

const openRepairCount = computed(
  () => repairs.value.filter((o) => o.status === 0 || o.status === 1).length
)
const repairRows = computed(() => repairs.value.filter((o) => inRange(o.createdAt)))
const emptyRepairText = computed(() =>
  repairs.value.length ? '该时间范围内没有维修工单' : '还没有提交过维修申请，可在上方填写并提交'
)

// 校区选项：按楼栋清单聚合，括号里带上该校区可选地点数
const campusOptions = computed(() => {
  const map = new Map()
  buildings.value.forEach((b) => {
    if (!b.campus) return
    map.set(b.campus, (map.get(b.campus) || 0) + Number(b.total || 0))
  })
  return [...map.entries()].map(([name, total]) => ({ name, total }))
})
// 楼栋选项：选定校区后只留该校区楼栋；未选校区时去重展示全部楼栋
const buildingOptions = computed(() => {
  const names = buildings.value
    .filter((b) => b.building && (!campusFilter.value || b.campus === campusFilter.value))
    .map((b) => b.building)
  return [...new Set(names)]
})

/* ---------------- 工具方法 ---------------- */

function timeValue(t) {
  return t ? new Date(String(t).replace(' ', 'T')).getTime() : 0
}
function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}
function clockText(t) {
  return t ? String(t).replace('T', ' ').slice(11, 16) : ''
}
function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function dayText(t) {
  const d = dateOf(t)
  return d === todayStr() ? `今天 · ${d}` : d
}
function locationText(resource) {
  if (!resource) return '地点信息已失效'
  const parts = [resource.campus, resource.building, resource.floor, resource.roomNo].filter(
    Boolean
  )
  return parts.length ? parts.join(' · ') : '地点待补充'
}
function resourceLabel(r) {
  return [r.name, locationText(r)].filter(Boolean).join('｜')
}
function statusText(status) {
  return { 1: '可预约', 2: '使用中', 3: '维修中', 4: '报修中' }[status] || '未知'
}
const orderStatusMap = {
  0: ['待审核', 'warning'],
  1: ['维修中', 'primary'],
  2: ['已完成', 'success'],
  3: ['已驳回', 'info']
}
function orderStatusText(status) {
  return orderStatusMap[status]?.[0] || '未知'
}
function orderTagType(status) {
  return orderStatusMap[status]?.[1] || 'info'
}

// 工单进度节点：0 已提交、1 审核、2 维修完成（驳回时停在审核节点上）
function stepState(status, index) {
  if (status === 3) {
    if (index === 0) return 'done'
    return index === 1 ? 'rejected' : ''
  }
  if (status === 0) return index === 0 ? 'done' : index === 1 ? 'active' : ''
  if (status === 1) return index <= 1 ? 'done' : 'active'
  return 'done'
}

function orderHint(order) {
  if (order.status === 0) {
    return '等待工作人员审核；审核通过后地点会标为「维修中」并暂停预约，结果会出现在「通知」里'
  }
  if (order.status === 1) {
    return '工作人员已确认维修，地点正在维护并暂停预约，修好后会再通知你'
  }
  if (order.status === 2) {
    return '维修已完成，地点已恢复「可预约」'
  }
  const reason = order.rejectReason ? `：${order.rejectReason}` : ''
  return `工单已驳回${reason}`
}
function isOk(res) {
  return res && (res.code === 0 || res.code === 200)
}

/* ---------------- 数据加载 ---------------- */

async function loadInbox() {
  const res = await api.getMyMessages()
  if (isOk(res)) {
    messages.value = [...(res.data || [])].sort(
      (a, b) => timeValue(b.createdAt) - timeValue(a.createdAt)
    )
  }
  window.dispatchEvent(new Event('campus-unread-changed'))
}

async function loadRepairs() {
  const res = await api.getMyRepairs()
  if (isOk(res)) repairs.value = res.data || []
}

async function loadResources(keyword) {
  resourceLoading.value = true
  try {
    const res = await api.getResourcePage({
      current: 1,
      size: RESOURCE_OPTION_LIMIT,
      keyword,
      campus: campusFilter.value || undefined,
      building: buildingFilter.value || undefined
    })
    if (isOk(res)) resourceOptions.value = res.data?.records || []
  } finally {
    resourceLoading.value = false
  }
}

function searchResources(keyword) {
  loadResources(keyword)
}

// 校区/楼栋变化后旧选项已不在范围内，清空已选地点再重新拉取
function onCampusChange() {
  buildingFilter.value = ''
  repairForm.value.resourceId = undefined
  loadResources()
}

function onBuildingChange() {
  repairForm.value.resourceId = undefined
  loadResources()
}

async function loadBuildings() {
  const res = await api.getBuildings()
  if (isOk(res)) buildings.value = res.data || []
}

async function reload() {
  await Promise.all([loadInbox(), loadRepairs()])
  if (tab.value === 'chat') scrollChatToEnd()
}

function scrollChatToEnd() {
  nextTick(() => {
    const el = chatBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(tab, (val) => {
  if (val === 'chat') scrollChatToEnd()
})

/* ---------------- 交互 ---------------- */

async function sendAsk() {
  if (!draft.value.trim()) {
    ElMessage.warning('留言内容不能为空')
    return
  }
  sending.value = true
  try {
    const res = await api.askAdmin(draft.value.trim())
    if (isOk(res)) {
      ElMessage.success('留言已提交')
      draft.value = ''
      await loadInbox()
      scrollChatToEnd()
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally {
    sending.value = false
  }
}

async function readOne(m) {
  if (m.direction !== 2 || m.isRead === 1) return
  const res = await api.markMessageRead(m.id)
  if (isOk(res)) {
    const origin = messages.value.find((x) => x.id === m.id)
    if (origin) origin.isRead = 1
    m.isRead = 1
    // 导航栏「消息中心」徽标同步未读数
    window.dispatchEvent(new Event('campus-unread-changed'))
  }
}

// 批量已读：逐条调用单条接口，数量级很小（最多几十条）
async function markAllRead() {
  const list = messages.value.filter((m) => m.direction === 2 && m.isRead === 0)
  if (!list.length) return
  for (const m of list) {
    const res = await api.markMessageRead(m.id)
    if (isOk(res)) m.isRead = 1
  }
  window.dispatchEvent(new Event('campus-unread-changed'))
  ElMessage.success('已全部标为已读')
}

async function submitRepair() {
  const { resourceId, problem } = repairForm.value
  if (!resourceId) {
    ElMessage.warning('请先选择需要维修的地点')
    return
  }
  if (!problem.trim()) {
    ElMessage.warning('请填写问题描述')
    return
  }
  submittingRepair.value = true
  try {
    const res = await api.createRepair({ resourceId, problem: problem.trim() })
    if (isOk(res)) {
      ElMessage.success('维修申请已提交，处理结果会通过「通知」告知')
      repairForm.value = { resourceId: undefined, problem: '' }
      // 提交会同时回一条「已受理」通知，这里一并刷新
      await Promise.all([loadInbox(), loadRepairs()])
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } finally {
    submittingRepair.value = false
  }
}

function gotoResource(order) {
  if (!order.resource?.id) return
  router.push(`/resource/${order.resource.id}`)
}

// 通知里带上关联资源时可直接跳转，顺便标为已读
async function openNoticeResource(notice) {
  await readOne(notice)
  if (notice.resourceId) router.push(`/resource/${notice.resourceId}`)
}

/* 页签停留在通知/工单时每 30 秒静默拉一次：管理员处理完不用手动刷新 */
let inboxTimer = null
function refreshVisibleTab() {
  if (document.visibilityState !== 'visible' || tab.value === 'chat') return
  Promise.all([loadInbox(), loadRepairs()])
}

onMounted(async () => {
  await Promise.all([loadBuildings(), loadResources()])
  await reload()
  inboxTimer = setInterval(refreshVisibleTab, 30000)
})

onBeforeUnmount(() => {
  if (inboxTimer) clearInterval(inboxTimer)
})
</script>

<style scoped lang="scss">
.my-messages {
  max-width: 1080px;
  margin: 0 auto;
}

/* 顶部概览：与下方卡片同一种白底卡片，避免一进页就是一块蓝渐变横幅 */
.msg-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px 24px;
  padding: 18px 22px;
  margin-bottom: 16px;
  border-radius: 14px;
  border: 1px solid #eef2f7;
  background: #fff;
  box-shadow: 0 2px 10px rgba(31, 79, 150, 0.04);

  .hero-main {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .hero-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    font-size: 19px;
    color: #fff;
    background: linear-gradient(135deg, #144e9c, #2e76db);
    box-shadow: 0 4px 12px rgba(30, 98, 208, 0.22);
  }
  .hero-title {
    font-size: 18px;
    font-weight: 700;
    color: #1f2d3d;
    letter-spacing: 0.3px;
  }
  .hero-sub {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
  .hero-stats {
    display: flex;
    align-items: center;
    .stat-divider {
      width: 1px;
      height: 24px;
      margin: 0 14px;
      background: #eef2f7;
    }
    .stat {
      display: flex;
      align-items: baseline;
      gap: 6px;
      b {
        font-size: 20px;
        font-weight: 800;
        line-height: 1;
        color: #1f4f96;
        font-variant-numeric: tabular-nums;
      }
      span {
        font-size: 12px;
        color: #909399;
      }
      &.alert b {
        color: #f56c6c;
      }
    }
  }
}

.panel {
  border: 1px solid #eef2f7;
  border-radius: 14px;
  :deep(.el-card__header) {
    padding: 12px 18px;
    border-bottom: 1px solid #f0f3f8;
  }
  :deep(.el-card__body) {
    padding: 16px 18px;
  }
}
.panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  .date-filter {
    width: 240px;
    margin-right: 4px;
  }
}

/* 沟通 / 通知 / 维修工单 分段切换 */
.seg-switch {
  display: inline-flex;
  padding: 3px;
  border-radius: 999px;
  border: 1px solid #e8eef7;
  background: #f2f5fa;

  .seg-item {
    display: inline-flex;
    align-items: center;
    padding: 5px 16px;
    border: none;
    border-radius: 999px;
    background: transparent;
    font-size: 13px;
    color: #606266;
    cursor: pointer;
    transition:
      background 0.2s,
      color 0.2s,
      box-shadow 0.2s;

    em {
      margin-left: 5px;
      padding: 0 6px;
      border-radius: 999px;
      background: #f56c6c;
      color: #fff;
      font-size: 11px;
      font-style: normal;
      font-variant-numeric: tabular-nums;
    }
    &.active {
      background: #fff;
      color: #144e9c;
      font-weight: 600;
      box-shadow: 0 2px 8px rgba(30, 98, 208, 0.14);
    }
  }
}

/* ---------------- 沟通 ---------------- */
.chat-pane {
  display: flex;
  flex-direction: column;
}
.chat-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 220px;
  max-height: 460px;
  overflow-y: auto;
  padding: 4px 2px 10px;
}
.day-line {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 2px 0;
  span {
    padding: 2px 10px;
    border-radius: 999px;
    background: #f4f6fa;
    font-size: 11px;
    color: #a8b3c4;
    font-variant-numeric: tabular-nums;
  }
}
.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;

  &.right {
    justify-content: flex-end;
  }

  .avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    font-size: 12px;
    font-weight: 700;
    color: #fff;
    background: linear-gradient(135deg, #7fb2f0, #a9cdf7);

    &.adm {
      background: linear-gradient(135deg, #144e9c, #2e76db);
    }
  }
  .bubble-wrap {
    display: flex;
    flex-direction: column;
    max-width: 72%;
    min-width: 0;
  }
  .bubble {
    padding: 9px 13px;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
    box-shadow: 0 1px 2px rgba(31, 45, 61, 0.04);

    &.mine {
      border-radius: 12px 4px 12px 12px;
      background: #e8f1ff;
      color: #1f4f96;
    }
    &.adm {
      border-radius: 4px 12px 12px 12px;
      background: #f4f6fa;
      color: #303133;
    }
  }
  .bubble-meta {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 4px;
    font-size: 11px;
    color: #a8b3c4;

    &.right {
      justify-content: flex-end;
    }
    .unread-tag {
      padding: 0 6px;
      border-radius: 999px;
      background: #fdecec;
      color: #f56c6c;
    }
  }
}

.composer {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid #f0f3f8;

  .composer-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 10px;
  }
  .composer-hint {
    font-size: 12px;
    line-height: 1.5;
    color: #a8b3c4;
  }
  :deep(.el-button) {
    min-width: 96px;
    height: 34px;
    border-radius: 9px;
    font-weight: 600;
  }
}

/* ---------------- 通知 ---------------- */
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 600px;
  overflow-y: auto;
  padding-right: 2px;
}
.notice {
  display: flex;
  gap: 12px;
  padding: 13px 15px;
  border: 1px solid #eff2f7;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;

  &:hover {
    border-color: #d8e6fb;
    box-shadow: 0 8px 20px rgba(30, 98, 208, 0.08);
    transform: translateY(-1px);
  }
  &.unread {
    background: #f7fbff;
    border-color: #dbe8fb;
    box-shadow: inset 3px 0 0 #2e76db;

    .notice-text {
      color: #303133;
      font-weight: 500;
    }
  }

  .notice-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 16px;
    background: #f2f5fa;
    color: #9aa7bb;

    &.hot {
      background: linear-gradient(135deg, #eaf2ff, #dbe9ff);
      color: #2e76db;
    }
  }
  .notice-main {
    min-width: 0;
  }
  .notice-text {
    font-size: 13px;
    line-height: 1.6;
    color: #606266;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .notice-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 6px;
    font-size: 11px;
    color: #a8b3c4;

    .notice-link {
      height: auto;
      padding: 0;
      font-size: 11px;
    }

    .state {
      display: inline-flex;
      align-items: center;
      gap: 4px;

      &.hot {
        color: #f56c6c;

        &::before {
          content: '';
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: #f56c6c;
        }
      }
    }
  }
}

/* ---------------- 维修工单 ---------------- */
.repair-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.repair-form {
  padding: 14px 16px 4px;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  background: #fafcff;

  /* 维修地点：校区 + 楼栋 + 具体地点（搜索框）一行排开 */
  .loc-filter-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;

    .lc-campus {
      width: 168px;
      flex-shrink: 0;
    }
    .lc-building {
      width: 150px;
      flex-shrink: 0;
    }
    .lc-resource {
      flex: 1;
      min-width: 180px;
    }
  }

  .form-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 12px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
  .form-tip {
    margin-left: 12px;
    font-size: 12px;
    line-height: 1.5;
    color: #a8b3c4;
  }
}
.opt-name {
  margin-right: 8px;
  color: #303133;
}
.opt-loc {
  margin-right: 8px;
  font-size: 12px;
  color: #a8b3c4;
}
.opt-status {
  font-size: 12px;
  color: #1f4f96;
}

.repair-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.repair-card {
  padding: 13px 16px 14px;
  border: 1px solid #eff2f7;
  border-radius: 14px;
  background: #fff;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;

  &:hover {
    border-color: #d8e6fb;
    box-shadow: 0 8px 20px rgba(30, 98, 208, 0.08);
  }

  .rc-head {
    display: flex;
    align-items: center;
    gap: 8px;

    .rc-name {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
    .rc-time {
      margin-left: auto;
      font-size: 11px;
      color: #a8b3c4;
      font-variant-numeric: tabular-nums;
    }
  }
  .rc-loc {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: 6px;
    font-size: 12px;
    color: #7a8799;
  }
  .rc-problem {
    margin-top: 8px;
    padding: 9px 12px;
    border-radius: 10px;
    background: #f7f9fc;
    font-size: 13px;
    line-height: 1.6;
    color: #606266;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .rc-steps {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 10px;
    font-size: 12px;
    color: #b6bfcc;

    .rstep.done {
      color: #3aa675;
      font-weight: 600;
    }
    .rstep.active {
      color: #1e62d0;
      font-weight: 600;
    }
    .rstep.rejected {
      color: #d48806;
      font-weight: 600;
    }
    .rline {
      flex: 0 0 22px;
      height: 2px;
      border-radius: 2px;
      background: #eef1f6;

      &.done {
        background: #b7ddc9;
      }
      &.active {
        background: #c8dcfb;
      }
      &.rejected {
        background: #f2d9b3;
      }
    }
  }
  .rc-hint {
    display: flex;
    align-items: flex-start;
    gap: 5px;
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.6;
    color: #7a8799;

    &.rejected {
      color: #b88230;
    }
    .el-icon {
      flex-shrink: 0;
      margin-top: 2px;
    }
  }
  .rc-foot {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 10px;
    font-size: 12px;
    color: #a8b3c4;
    .rc-state {
      em {
        margin-left: 4px;
        font-style: normal;
        font-weight: 600;

        &.s1 {
          color: #3aa675;
        }
        &.s2 {
          color: #e6a23c;
        }
        &.s3,
        &.s4 {
          color: #f56c6c;
        }
      }
    }
    .el-button {
      margin-left: auto;
    }
  }
}

@media (max-width: 768px) {
  .panel-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
    .date-filter {
      width: 100%;
      margin-right: 0;
    }
  }
  .chat-list {
    min-height: 200px;
    max-height: 380px;
  }
  .composer .composer-row {
    flex-direction: column;
    align-items: stretch;
  }
  .repair-form .form-tip {
    display: block;
    margin: 6px 0 0;
  }
  .repair-form .loc-filter-row {
    flex-direction: column;
    align-items: stretch;
    .lc-campus,
    .lc-building,
    .lc-resource {
      width: 100%;
    }
  }
  .repair-card .rc-foot {
    flex-wrap: wrap;
    .el-button {
      margin-left: 0;
    }
  }
}
</style>
