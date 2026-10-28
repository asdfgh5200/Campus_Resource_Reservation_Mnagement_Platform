<template>
  <div class="user-manage">
    <el-card shadow="never" class="main-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名 / 姓名"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
        <div class="toolbar-right">
          <el-popover placement="bottom-end" :width="340" trigger="hover">
            <template #reference>
              <el-button class="rule-btn" :icon="InfoFilled" circle size="small" />
            </template>
            <div class="rule-pop">
              <p>一天内违规满 {{ limit }} 次，当日禁止预约，次日自动恢复。</p>
              <p>累计取消满 {{ cancelLimit }} 次，记恶意取消，该用户之后不可再自行取消预约。</p>
            </div>
          </el-popover>
          <el-button type="warning" :icon="Bell" @click="broadcastVisible = true"
            >发送通知</el-button
          >
        </div>
      </div>

      <!-- 概览 -->
      <div class="sum-row">
        <div class="sum">
          <b>{{ total }}</b
          ><span>用户总数</span>
        </div>
        <div class="sum">
          <b>{{ activeUserCount }}</b
          ><span>本页有进行中预约</span>
        </div>
        <div class="sum">
          <b>{{ restrictedCount }}</b
          ><span>本页今日受限</span>
        </div>
        <div class="sum">
          <b>{{ violationUserCount }}</b
          ><span>本页有违规记录</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe style="width: 100%" @row-click="openDetail">
        <el-table-column label="用户" min-width="190">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="avatar">{{ (row.nickname || row.username || 'U').charAt(0) }}</span>
              <div class="user-text">
                <div class="uname">{{ row.nickname || row.username }}</div>
                <div class="uid">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="账号 / 权限" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="permOf(row).type" size="small" effect="light">{{
              permOf(row).text
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预约状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="stateOf(row).type" size="small" effect="light">{{
              stateOf(row).text
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="取消次数" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ danger: row.cancelCount >= cancelLimit }"
              >{{ row.cancelCount }} / {{ cancelLimit }}</span
            >
          </template>
        </el-table-column>
        <el-table-column label="预约数" width="90" align="center">
          <template #default="{ row }">{{ row.reservationCount }}</template>
        </el-table-column>
        <el-table-column label="今日违规" width="100" align="center">
          <template #default="{ row }">
            <span class="today-count" :class="{ danger: row.todayCount > 0 }">{{
              row.todayCount
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计违规" width="100" align="center">
          <template #default="{ row }">{{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <!-- 用户发来留言但还没回复：「详细信息」上挂一个红色数字角标 -->
            <el-badge :value="row.pendingAsks || 0" :hidden="!row.pendingAsks" class="ask-badge">
              <el-button text type="primary" size="small" @click.stop="openDetail(row)"
                >详细信息</el-button
              >
            </el-badge>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="暂无用户" />

      <div class="pagination" v-if="total > page.size">
        <el-pagination
          v-model:current-page="page.current"
          :page-size="page.size"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 用户详情抽屉：用户端能看到的内容都在这里，且可直接修改 -->
    <el-drawer
      v-model="detailVisible"
      :with-header="false"
      size="920px"
      class="user-drawer"
      @closed="onDetailClosed"
    >
      <div v-loading="detailLoading" class="drawer-shell">
        <header class="drawer-head">
          <span class="avatar">{{ (detail.user?.nickname || 'U').charAt(0) }}</span>
          <div class="head-text">
            <div class="head-name">
              <span class="name">{{ detail.user?.nickname || detail.user?.username }}</span>
              <el-tag :type="detailPerm.type" size="small" effect="light" round>
                {{ detailPerm.text }}
              </el-tag>
              <el-tag :type="detailState.type" size="small" effect="light" round>
                {{ detailState.text }}
              </el-tag>
            </div>
            <div class="head-sub">
              @{{ detail.user?.username }} · 注册于 {{ fmt(detail.user?.createdAt) }}
            </div>
          </div>
          <div class="head-actions">
            <el-button :icon="Bell" round @click="openUserNotify">发送通知</el-button>
            <el-button class="close-btn" :icon="Close" circle @click="detailVisible = false" />
          </div>
        </header>

        <div class="drawer-body">
          <template v-if="detail.user">
            <!-- 资料与数值：与用户端「我的」一致，管理端可直接改 -->
            <section class="panel">
              <div class="panel-hd">
                <span class="panel-title">资料与数值</span>
                <div class="panel-actions">
                  <el-button size="small" @click="resetForm">重置</el-button>
                  <el-button size="small" type="primary" :loading="saving" @click="saveUser"
                    >保存修改</el-button
                  >
                </div>
              </div>

              <el-form :model="form" label-position="top" class="profile-form">
                <el-row :gutter="18">
                  <el-col :span="6">
                    <el-form-item label="学号 / 登录账号">
                      <el-input v-model="form.username" maxlength="20" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item label="姓名 / 昵称">
                      <el-input v-model="form.nickname" maxlength="20" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item label="手机号">
                      <el-input v-model="form.phone" maxlength="20" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item label="邮箱">
                      <el-input v-model="form.email" maxlength="60" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="18">
                  <el-col :span="6">
                    <el-form-item label="账号权限">
                      <el-select v-model="permValue" style="width: 100%">
                        <el-option
                          v-for="o in PERM_OPTIONS"
                          :key="o.value"
                          :value="o.value"
                          :label="o.label"
                        />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item label="重置密码">
                      <el-input
                        v-model="form.password"
                        type="password"
                        show-password
                        maxlength="32"
                        placeholder="留空表示不修改"
                      />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>

              <!-- 数值：与用户端同一套统计，改完随「保存修改」提交 -->
              <div class="stat-row">
                <div v-for="t in statTiles" :key="t.key" class="stat">
                  <el-input-number
                    v-model="form[t.key]"
                    :min="t.min"
                    :max="t.max"
                    :controls="false"
                    size="small"
                    class="stat-input"
                  />
                  <span class="stat-label">{{ t.label }}</span>
                </div>
              </div>
            </section>

            <!-- 记录：可按单日筛选 -->
            <section class="panel records">
              <div class="records-hd">
                <span class="panel-title">用户记录</span>
                <el-date-picker
                  v-model="recordDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="筛选日期"
                  clearable
                  size="small"
                  style="width: 210px"
                  @change="onDateChange"
                />
              </div>

              <el-tabs v-model="detailTab" class="detail-tabs" @tab-change="onTabChange">
                <el-tab-pane label="预约记录" name="reservations">
                  <el-table
                    :data="detail.reservations"
                    size="small"
                    stripe
                    empty-text="暂无预约记录"
                  >
                    <el-table-column label="资源" min-width="140">
                      <template #default="{ row }">{{
                        row.resource?.name || '资源 #' + row.resourceId
                      }}</template>
                    </el-table-column>
                    <el-table-column label="时段" min-width="200">
                      <template #default="{ row }"
                        >{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template
                      >
                    </el-table-column>
                    <el-table-column label="人数" width="70" align="center">
                      <template #default="{ row }">{{ row.headcount ?? 1 }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="150" align="center">
                      <template #default="{ row }">
                        <div class="res-status">
                          <el-select
                            :model-value="row.status"
                            size="small"
                            style="width: 118px"
                            @change="(v) => changeResStatus(row, v)"
                          >
                            <el-option
                              v-for="s in statusOptionsOf(row)"
                              :key="s.value"
                              :value="s.value"
                              :label="s.label"
                            />
                          </el-select>
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-tab-pane>

                <el-tab-pane
                  :label="`违规记录（${detail.stats.totalViolations ?? 0}）`"
                  name="violations"
                >
                  <el-table :data="detail.violations" size="small" stripe empty-text="暂无违规记录">
                    <el-table-column label="类型" width="100">
                      <template #default="{ row }">
                        <el-tag type="danger" size="small" effect="light">{{
                          vioTypeText(row.type)
                        }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="描述" min-width="220">
                      <template #default="{ row }">{{ row.description || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="90" align="center">
                      <template #default="{ row }">
                        <el-tag
                          :type="row.status === 1 ? 'danger' : 'info'"
                          size="small"
                          effect="plain"
                        >
                          {{ row.status === 1 ? '有效' : '已撤销' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="时间" width="150">
                      <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="90" align="center">
                      <template #default="{ row }">
                        <el-button text type="primary" size="small" @click="toggleViolation(row)">
                          {{ row.status === 1 ? '撤销' : '恢复' }}
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-tab-pane>

                <!-- 留言：只有用户与管理员之间的往来，按对话展示 -->
                <el-tab-pane name="messages">
                  <template #label>
                    <span class="tab-with-badge">
                      {{ askTabLabel }}
                      <em v-if="pendingAskCount" class="pending-num">{{ pendingAskCount }}</em>
                    </span>
                  </template>
                  <div class="chat">
                    <div v-if="askMessages.length" ref="chatListRef" class="chat-list">
                      <div
                        v-for="m in askMessages"
                        :key="m.id"
                        class="chat-row"
                        :class="m.direction === 1 ? 'from-user' : 'from-admin'"
                      >
                        <span class="chat-avatar">{{
                          m.direction === 1 ? userInitial : '管'
                        }}</span>
                        <div class="chat-bubble">
                          <div class="chat-text">{{ m.content }}</div>
                          <div class="chat-meta">
                            <span class="who">{{ msgTagText(m) }}</span>
                            <span class="flag" :class="msgState(m).cls">{{
                              msgState(m).text
                            }}</span>
                            <span class="time">{{ fmt(m.createdAt) }}</span>
                            <el-button
                              v-if="m.direction === 1"
                              class="reply-btn"
                              link
                              type="primary"
                              @click="openReply(m)"
                              >回复</el-button
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                    <div v-else class="chat-empty">该用户还没有留言</div>
                  </div>
                </el-tab-pane>

                <!-- 通知：只列出该用户收到的站内通知与阅读状态，不做对话 -->
                <el-tab-pane :label="noticesLabel" name="notices">
                  <div class="notice-pane">
                    <div class="notice-send">
                      <el-input
                        v-model="userNotifyDraft"
                        size="small"
                        maxlength="1000"
                        placeholder="给该用户发送一条站内通知，用户端「消息中心」可见"
                        @keyup.enter="sendUserNotify"
                      />
                      <el-button
                        type="primary"
                        size="small"
                        :loading="userNotifying"
                        @click="sendUserNotify"
                        >发送通知</el-button
                      >
                    </div>
                    <el-table
                      :data="noticeRows"
                      size="small"
                      stripe
                      max-height="300"
                      empty-text="该用户暂未收到通知"
                    >
                      <el-table-column label="通知内容" min-width="300">
                        <template #default="{ row }">
                          <span class="notice-text">{{ row.content }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="状态" width="90" align="center">
                        <template #default="{ row }">
                          <el-tag
                            :type="row.isRead === 1 ? 'info' : 'danger'"
                            size="small"
                            effect="light"
                            >{{ row.isRead === 1 ? '已读' : '未读' }}</el-tag
                          >
                        </template>
                      </el-table-column>
                      <el-table-column label="发送时间" width="150">
                        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                </el-tab-pane>
              </el-tabs>
            </section>
          </template>
        </div>
      </div>
    </el-drawer>

    <!-- 回复留言 -->
    <el-dialog v-model="replyVisible" title="回复该用户的留言" width="520px">
      <div class="origin">{{ replyTarget?.content }}</div>
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="输入回复内容，用户端「消息中心」可见"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>

    <!-- 广播通知：原「留言管理」页的发送通知 -->
    <el-dialog v-model="broadcastVisible" title="发送通知" width="540px">
      <el-radio-group v-model="broadcastScope" class="scope-group">
        <el-radio-button value="all">全体用户</el-radio-button>
        <el-radio-button value="active">正在使用中的用户</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="broadcastDraft"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="输入通知内容，将以内站消息形式推送给选中范围用户"
      />
      <template #footer>
        <el-button @click="broadcastVisible = false">取消</el-button>
        <el-button type="primary" :loading="broadcasting" @click="submitBroadcast"
          >发送通知</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Bell, Close, InfoFilled } from '@element-plus/icons-vue'
import { api } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const limit = ref(3)
const cancelLimit = ref(5)
const keyword = ref('')
const page = reactive({ current: 1, size: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('reservations')
// 记录时间：单日筛选（留空看全部）
const recordDate = ref('')
const detail = ref({ user: null, stats: {}, reservations: [], violations: [], messages: [] })

// 资料 + 统计数值 + 预约权限，统一由「保存修改」提交
const form = reactive({
  username: '',
  nickname: '',
  phone: '',
  email: '',
  password: '',
  status: 1,
  cancelCount: 0,
  reservationTotal: 0,
  todayViolations: 0,
  totalViolations: 0,
  restrictOverride: 0
})
const saving = ref(false)

const replyVisible = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const replying = ref(false)

const broadcastVisible = ref(false)
const broadcastScope = ref('all')
const broadcastDraft = ref('')
const broadcasting = ref(false)

const userNotifyDraft = ref('')
const userNotifying = ref(false)
const chatListRef = ref(null)

/** 用户当前预约状态（与用户端「我的预约」同一套语义） */
const RES_STATE = {
  none: { text: '未预约', type: 'info' },
  pending: { text: '预约中', type: 'warning' },
  in_use: { text: '履约中', type: 'success' }
}
const RES_STATUS_OPTIONS = [
  { value: 1, label: '预约中' },
  { value: 2, label: '履约中' },
  { value: 3, label: '已完成' },
  { value: 4, label: '已取消' },
  { value: 5, label: '爽约' },
  { value: 6, label: '违规' }
]
// 打卡时间窗（与后端 ReservationService 常量一致）：开始前 30 分钟 ~ 开始后 5 分钟
const CHECKIN_ADVANCE_MINUTES = 30
const CHECKIN_LATE_MINUTES = 5

/** 解析后端时间字符串为时间戳 */
function parseTime(v) {
  if (!v) return null
  const t = new Date(String(v).replace(' ', 'T')).getTime()
  return Number.isFinite(t) ? t : null
}

/**
 * 下拉可选状态：与后端 adminUpdateStatus 的时间规定保持一致。
 * - 时段已结束：只能「已完成」/「违规」互切；
 * - 「履约中」=代为打卡，需在打卡时间窗内（或已打卡）；
 * - 「预约中」不可在已打卡或超过打卡时限后回退。
 * 当前状态始终保留，避免下拉显示空白。
 */
function statusOptionsOf(row) {
  const now = Date.now()
  const start = parseTime(row.startTime)
  const end = parseTime(row.endTime)
  const checkedIn = row.checkinFlag === 1
  let allowed
  if (end != null && end < now) {
    allowed = [3, 6]
  } else {
    // 打卡窗口：[start-30min, start+5min]
    const windowOpen =
      start != null &&
      now >= start - CHECKIN_ADVANCE_MINUTES * 60000 &&
      now <= start + CHECKIN_LATE_MINUTES * 60000
    const latePassed = start != null && now > start + CHECKIN_LATE_MINUTES * 60000
    allowed = RES_STATUS_OPTIONS.map((s) => s.value).filter((v) => {
      // 「履约中」(2)：未打卡且不在窗口内不可选；「预约中」(1)：已打卡或超时限不可选
      if (v === 2 && !checkedIn && !windowOpen) return false
      if (v === 1 && (checkedIn || latePassed)) return false
      return true
    })
  }
  const opts = RES_STATUS_OPTIONS.filter((s) => allowed.includes(s.value))
  const cur = RES_STATUS_OPTIONS.find((s) => s.value === row.status)
  if (cur && !allowed.includes(cur.value)) return [cur, ...opts]
  return opts
}
const VIO_TYPE = { 1: '爽约', 2: '超时使用', 3: '恶意取消', 4: '其他' }
// 账号权限（单选，四个状态）：
// 0 正常=可预约可取消；1 违规=禁止新建预约；2 禁止取消=可预约但不能取消；3 禁用账号=停用登录
const PERM_OPTIONS = [
  { value: 0, label: '正常' },
  { value: 1, label: '违规' },
  { value: 2, label: '禁止取消' },
  { value: 3, label: '禁用账号' }
]

const restrictedCount = computed(() => rows.value.filter((r) => r.restricted).length)
const violationUserCount = computed(() => rows.value.filter((r) => r.totalCount > 0).length)
const activeUserCount = computed(
  () => rows.value.filter((r) => r.reservationState !== 'none').length
)
// 四个可编辑数值：今日违规不得超出累计值，反之亦然
const statTiles = computed(() => [
  { key: 'reservationTotal', label: '累计预约', min: 0, max: 9999 },
  { key: 'todayViolations', label: '今日违规', min: 0, max: form.totalViolations },
  { key: 'totalViolations', label: '累计违规', min: form.todayViolations, max: 999 },
  {
    key: 'cancelCount',
    label: '取消次数',
    min: 0,
    max: cancelLimit.value
  }
])
const detailState = computed(
  () => RES_STATE[detail.value.stats?.reservationState] || RES_STATE.none
)
const detailPerm = computed(() =>
  permOf({
    status: detail.value.user?.status,
    restricted: detail.value.stats?.restricted,
    cancelCount: detail.value.stats?.cancelCount ?? detail.value.user?.cancelCount
  })
)

// 表单里的账号权限（单选）：由 status / restrictOverride / cancelCount 三个字段推导，优先级 禁用 > 违规 > 禁止取消 > 正常
const permValue = computed({
  get: () => {
    if (form.status !== 1) return 3
    if (form.restrictOverride === 2) return 1
    if ((form.cancelCount ?? 0) >= cancelLimit.value) return 2
    return 0
  },
  set: (v) => {
    if (v === 3) {
      // 禁用账号：停用登录
      form.status = 0
      form.restrictOverride = 0
      return
    }
    form.status = 1
    if (v === 1) {
      // 违规：禁止新建预约，仍可取消
      form.restrictOverride = 2
      if ((form.cancelCount ?? 0) >= cancelLimit.value) form.cancelCount = 0
    } else if (v === 2) {
      // 禁止取消：可预约，取消次数置上限（后端据此拦截取消）
      form.restrictOverride = 1
      form.cancelCount = cancelLimit.value
    } else {
      // 正常：可预约可取消
      form.restrictOverride = 1
      if ((form.cancelCount ?? 0) >= cancelLimit.value) form.cancelCount = 0
    }
  }
})

/** 列表与详情的权限标签：与下拉四态同一套词汇（禁用 > 违规 > 禁止取消 > 正常） */
function permOf(row) {
  if (row?.status !== 1) return { text: '已禁用', type: 'info' }
  if (row.restricted) return { text: '违规', type: 'danger' }
  if ((row.cancelCount ?? 0) >= cancelLimit.value) return { text: '禁止取消', type: 'warning' }
  return { text: '正常', type: 'success' }
}

function stateOf(row) {
  return RES_STATE[row.reservationState] || RES_STATE.none
}
function vioTypeText(t) {
  return VIO_TYPE[t] || '其他'
}
function msgTagText(row) {
  if (row.direction === 1) return '用户留言'
  return row.refId ? '管理员回复' : '系统通知'
}
// 用户留言看“是否已回复”，管理员侧消息看“是否已读”
function msgState(row) {
  const done = row.direction === 1 ? !!row.replied : row.isRead === 1
  let text = row.isRead === 1 ? '已读' : '未读'
  if (row.direction === 1) text = row.replied ? '已回复' : '待回复'
  return { text, cls: done ? 'ok' : 'wait' }
}
const userInitial = computed(() =>
  (detail.value.user?.nickname || detail.value.user?.username || 'U').charAt(0)
)
// 留言按时间正序，读起来像一段对话
const chatMessages = computed(() =>
  [...(detail.value.messages || [])].sort(
    (a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0)
  )
)
// 留言 = 用户留言 + 管理员回复（带 refId），只有往来对话
const askMessages = computed(() => chatMessages.value.filter((m) => m.direction === 1 || m.refId))
// 通知 = 管理员/系统单向推送（无 refId），按时间倒序只展示不对话
const noticeRows = computed(() =>
  chatMessages.value.filter((m) => m.direction === 2 && !m.refId).reverse()
)
const askTabLabel = computed(() =>
  askMessages.value.length ? `留言（${askMessages.value.length}）` : '留言'
)
// 「留言」页签上的红色角标：用户发来但还没回复的条数
const pendingAskCount = computed(
  () => askMessages.value.filter((m) => m.direction === 1 && !m.replied).length
)
const noticesLabel = computed(() => {
  const unread = noticeRows.value.filter((m) => m.isRead !== 1).length
  if (unread) return `通知（${unread} 未读）`
  return noticeRows.value.length ? `通知（${noticeRows.value.length}）` : '通知'
})
function fmt(dt) {
  return dt ? String(dt).replace('T', ' ').slice(0, 16) : '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.getAdminUsers({
      keyword: keyword.value || undefined,
      current: page.current,
      size: page.size
    })
    if (res.code === 200 || res.code === 0) {
      rows.value = res.data?.records || []
      total.value = res.data?.total || 0
      if (res.data?.limit) limit.value = res.data.limit
      if (res.data?.cancelLimit) cancelLimit.value = res.data.cancelLimit
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.current = 1
  loadData()
}

function resetForm() {
  const u = detail.value.user || {}
  const s = detail.value.stats || {}
  form.username = u.username || ''
  form.nickname = u.nickname || ''
  form.phone = u.phone || ''
  form.email = u.email || ''
  form.password = ''
  form.status = u.status ?? 1
  form.cancelCount = s.cancelCount ?? u.cancelCount ?? 0
  form.reservationTotal = s.reservationCount ?? u.reservationTotal ?? 0
  form.todayViolations = s.todayViolations ?? 0
  form.totalViolations = s.totalViolations ?? 0
  form.restrictOverride = u.restrictOverride ?? s.restrictOverride ?? 0
}

async function openDetail(row) {
  detailVisible.value = true
  detailTab.value = 'reservations'
  recordDate.value = ''
  detail.value = { user: row, stats: {}, reservations: [], violations: [], messages: [] }
  resetForm()
  await fetchDetail(row.id)
}

/** 拉取详情：选中某天时按该日筛选三类记录 */
async function fetchDetail(id) {
  const userId = id || detail.value.user?.id
  if (!userId) return
  const params = {}
  if (recordDate.value) {
    params.startDate = recordDate.value
    params.endDate = recordDate.value
  }
  detailLoading.value = true
  try {
    const res = await api.getAdminUserDetail(userId, params)
    if ((res.code === 200 || res.code === 0) && res.data) {
      detail.value = {
        user: res.data.user,
        stats: res.data.stats || {},
        reservations: res.data.reservations || [],
        violations: res.data.violations || [],
        messages: res.data.messages || []
      }
      resetForm()
      if (detailTab.value === 'messages') scrollChatToBottom()
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } finally {
    detailLoading.value = false
  }
}

function onDateChange() {
  fetchDetail()
}

/** 留言多了默认停在最新一条 */
function scrollChatToBottom() {
  nextTick(() => {
    const el = chatListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onTabChange(name) {
  if (name === 'messages') scrollChatToBottom()
}

function onDetailClosed() {
  detail.value = { user: null, stats: {}, reservations: [], violations: [], messages: [] }
  recordDate.value = ''
}

async function saveUser() {
  if (!/^[0-9A-Za-z]{4,20}$/.test((form.username || '').trim())) {
    ElMessage.warning('学号需为 4-20 位数字或字母')
    return
  }
  if (!form.nickname.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  if (form.password && (form.password.length < 6 || form.password.length > 32)) {
    ElMessage.warning('密码长度需为 6-32 位')
    return
  }
  saving.value = true
  try {
    const payload = {
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      phone: form.phone,
      email: form.email,
      status: form.status,
      cancelCount: form.cancelCount,
      reservationTotal: form.reservationTotal,
      todayViolations: form.todayViolations,
      totalViolations: form.totalViolations,
      restrictOverride: form.restrictOverride
    }
    // 仅在填写了新密码时才提交，避免把空串当成重置指令
    if (form.password) payload.password = form.password
    const res = await api.updateAdminUser(detail.value.user.id, payload)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('已保存')
      await fetchDetail()
      loadData()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

async function changeResStatus(row, status) {
  if (status === row.status) return
  const res = await api.updateAdminReservationStatus(row.id, status)
  if (res.code === 200 || res.code === 0) {
    ElMessage.success('预约状态已更新')
    await fetchDetail()
    loadData()
  } else {
    ElMessage.error(res.message || '修改失败')
  }
}

async function toggleViolation(row) {
  const next = row.status === 1 ? 0 : 1
  const res = await api.updateAdminViolationStatus(row.id, next)
  if (res.code === 200 || res.code === 0) {
    ElMessage.success(next === 0 ? '已撤销该违规' : '已恢复该违规')
    await fetchDetail()
    loadData()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

function openReply(row) {
  replyTarget.value = row
  replyContent.value = ''
  replyVisible.value = true
}

async function submitReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replying.value = true
  try {
    const res = await api.replyMessage(replyTarget.value.id, replyContent.value.trim())
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('已回复')
      replyVisible.value = false
      await fetchDetail()
      // 列表里该用户的红色角标同步消失
      loadData()
    } else {
      ElMessage.error(res.message || '回复失败')
    }
  } finally {
    replying.value = false
  }
}

function openUserNotify() {
  detailTab.value = 'notices'
  userNotifyDraft.value = ''
}

async function sendUserNotify() {
  const content = userNotifyDraft.value.trim()
  if (!content) {
    ElMessage.warning('通知内容不能为空')
    return
  }
  userNotifying.value = true
  try {
    const res = await api.notifyAdminUser(detail.value.user.id, content)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('通知已发送')
      userNotifyDraft.value = ''
      await fetchDetail()
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally {
    userNotifying.value = false
  }
}

async function submitBroadcast() {
  const content = broadcastDraft.value.trim()
  if (!content) {
    ElMessage.warning('通知内容不能为空')
    return
  }
  broadcasting.value = true
  try {
    const res = await api.broadcastMessage(content, broadcastScope.value)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success(`已推送给 ${res.data?.sent ?? 0} 位用户`)
      broadcastDraft.value = ''
      broadcastVisible.value = false
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally {
    broadcasting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.user-manage {
  max-width: 1200px;
  margin: 0 auto;
}
.main-card {
  border-radius: 14px;
  border: none;
}

/* 「详细信息」右上角的待回复留言角标 */
.ask-badge {
  :deep(.el-badge__content) {
    top: 4px;
    right: 12px;
    height: 16px;
    padding: 0 5px;
    line-height: 16px;
    font-size: 11px;
    border: none;
  }
}
/* 详情抽屉「留言」页签上的红色数字 */
.tab-with-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  .pending-num {
    min-width: 16px;
    height: 16px;
    padding: 0 5px;
    border-radius: 999px;
    background: #f56c6c;
    color: #fff;
    font-size: 11px;
    font-style: normal;
    line-height: 16px;
    text-align: center;
    font-variant-numeric: tabular-nums;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  .toolbar-left {
    display: flex;
    gap: 10px;
    align-items: center;
  }
  .toolbar-right {
    display: flex;
    gap: 12px;
    align-items: center;
  }
  .rule-btn {
    color: #909399;
    border-color: #dcdfe6;
  }
}

.sum-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  .sum {
    flex: 1;
    text-align: center;
    padding: 14px 0;
    border-radius: 12px;
    background: #f7f9fc;
    border: 1px solid #eef2f7;
    b {
      display: block;
      font-size: 24px;
      font-weight: 800;
      color: #303133;
      &.danger {
        color: #f56c6c;
      }
      &.ok {
        color: #67c23a;
      }
    }
    span {
      font-size: 12px;
      color: #909399;
    }
  }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  .avatar {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    background: linear-gradient(135deg, #2e76db, #74b9f5);
    &.big {
      width: 52px;
      height: 52px;
      font-size: 20px;
    }
  }
  .user-text {
    .uname {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .uid {
      font-size: 12px;
      color: #909399;
    }
  }
}

.today-count.danger,
.danger {
  color: #f56c6c;
  font-weight: 700;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 用户详情抽屉 */
.drawer-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.drawer-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  background: linear-gradient(120deg, #ffffff 0%, #f2f7ff 100%);
  border-bottom: 1px solid #e9eff8;
  .avatar {
    flex-shrink: 0;
    width: 52px;
    height: 52px;
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    font-weight: 700;
    color: #fff;
    background: linear-gradient(135deg, #2e76db, #74b9f5);
    box-shadow: 0 8px 18px rgba(46, 118, 219, 0.25);
  }
  .head-text {
    flex: 1;
    min-width: 0;
  }
  .head-name {
    display: flex;
    align-items: center;
    gap: 8px;
    .name {
      font-size: 19px;
      font-weight: 700;
      color: #1f2d3d;
    }
  }
  .head-sub {
    margin-top: 5px;
    font-size: 12px;
    color: #8a94a6;
  }
  .head-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .close-btn {
    border: none;
    color: #8a94a6;
  }
}
.drawer-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 22px 26px;
}
.panel {
  padding: 16px 20px 18px;
  border-radius: 14px;
  border: 1px solid #eef2f8;
  background: #fff;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.04);
}
.panel-hd,
.records-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #1f2d3d;
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    width: 3px;
    height: 14px;
    margin-top: -7px;
    border-radius: 2px;
    background: linear-gradient(180deg, #2e76db, #74b9f5);
  }
}
.panel-actions {
  display: flex;
  gap: 8px;
}
.profile-form {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
  :deep(.el-form-item__label) {
    padding-bottom: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: #8a94a6;
  }
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e9eff8;
  .stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 10px 12px 12px;
    border-radius: 12px;
    border: 1px solid transparent;
    background: #f7f9fd;
    cursor: text;
    transition:
      background 0.2s,
      border-color 0.2s,
      box-shadow 0.2s;
    &:hover {
      background: #f0f5fd;
    }
    &:focus-within {
      background: #fff;
      border-color: #b9cff0;
      box-shadow: 0 2px 10px rgba(30, 98, 208, 0.1);
    }
  }
  .stat-label {
    font-size: 12px;
    color: #8a94a6;
    white-space: nowrap;
  }
  .stat-input {
    width: 100%;
    :deep(.el-input__wrapper) {
      height: 30px;
      padding: 0;
      background: transparent;
      box-shadow: none;
    }
    :deep(.el-input__inner) {
      font-size: 18px;
      font-weight: 700;
      color: #1f2d3d;
      text-align: center;
      font-variant-numeric: tabular-nums;
    }
  }
}
.records-hd {
  margin-bottom: 4px;
}
.detail-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 8px;
  }
  :deep(.el-tabs__item) {
    font-size: 13px;
    color: #6b7688;
    &.is-active {
      font-weight: 600;
    }
  }
  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background: #eef2f8;
  }
  :deep(.el-table) {
    --el-table-border-color: #eef2f8;
  }
  :deep(.el-table th.el-table__cell) {
    background: #f7f9fd;
    color: #6b7688;
    font-weight: 600;
  }
}
.res-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.chat {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.chat-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 340px;
  overflow-y: auto;
  padding: 2px 2px 2px 0;
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dbe3ef;
    border-radius: 3px;
  }
}
.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  &.from-admin {
    flex-direction: row-reverse;
  }
}
.chat-avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #2e76db, #74b9f5);
}
.from-user .chat-avatar {
  color: #5b6b85;
  background: #e8effa;
}
.chat-bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 14px;
  border-top-left-radius: 4px;
  background: #fff;
  border: 1px solid #e9eef7;
  box-shadow: 0 1px 3px rgba(31, 45, 61, 0.04);
}
.from-admin .chat-bubble {
  border-radius: 14px;
  border-top-right-radius: 4px;
  border-color: transparent;
  background: linear-gradient(135deg, #2e76db, #4e9bf0);
  box-shadow: 0 4px 12px rgba(46, 118, 219, 0.22);
}
.chat-text {
  font-size: 13px;
  line-height: 1.7;
  color: #303a4c;
  white-space: pre-wrap;
  word-break: break-word;
}
.from-admin .chat-text {
  color: #fff;
}
.chat-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
  color: #9aa3b2;
  .who {
    font-weight: 600;
    color: #6b7688;
  }
  .flag {
    padding: 0 5px;
    border-radius: 4px;
    line-height: 16px;
    &.ok {
      color: #52a95c;
      background: #eef8ef;
    }
    &.wait {
      color: #d08a1e;
      background: #fdf4e4;
    }
  }
  .time {
    font-variant-numeric: tabular-nums;
  }
  .reply-btn {
    height: auto;
    padding: 0;
    font-size: 11px;
  }
}
.from-admin .chat-meta {
  color: rgba(255, 255, 255, 0.75);
  .who {
    color: #fff;
  }
  .flag {
    color: #fff;
    background: rgba(255, 255, 255, 0.2);
  }
}
.chat-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 13px;
  color: #a4adbd;
}
.notice-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notice-send {
  display: flex;
  align-items: center;
  gap: 10px;
  .el-button {
    flex-shrink: 0;
  }
}
.notice-text {
  font-size: 13px;
  line-height: 1.6;
  color: #303a4c;
  white-space: pre-wrap;
  word-break: break-word;
}
.origin {
  position: relative;
  padding: 10px 14px 10px 16px;
  margin-bottom: 14px;
  border-radius: 10px;
  background: #f5f8fd;
  color: #59647a;
  font-size: 13px;
  line-height: 1.7;
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 10px;
    bottom: 10px;
    width: 3px;
    border-radius: 2px;
    background: #c3d6f2;
  }
}
.scope-group {
  margin-bottom: 12px;
}
</style>

<style lang="scss">
/* 抽屉 teleport 到 body，头部需要通栅，内边距只能用非 scoped 覆盖 */
.user-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
  background: #f5f7fb;
}
</style>
