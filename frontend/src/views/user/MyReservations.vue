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
      <!-- 统一删除入口：不再逐条放「删除」按钮 -->
      <div v-if="reservations.length" class="list-toolbar">
        <span class="toolbar-hint">共 {{ reservations.length }} 条记录，其中 {{ deletableList.length }} 条可删除</span>
        <el-button :icon="Delete" size="small" :disabled="deletableList.length === 0" @click="openCleanup">批量删除记录</el-button>
      </div>

      <div
        v-for="item in reservations"
        :key="item.id"
        class="reservation-card"
        :class="{ clickable: item.status === 2 }"
        @click="onCardClick(item)"
      >
        <div class="card-left" :style="{ backgroundImage: `url(${getTypeImage(item.resource)})` }">
          <div class="card-left-overlay"></div>
        </div>
        <div class="card-content">
          <div class="card-top">
            <div class="name-wrap">
              <h3 class="res-name">{{ item.resource?.name || '未知资源' }}</h3>
              <span v-if="item.status === 2" class="detail-hint">
                <el-icon><View /></el-icon> 点击卡片查看详情
              </span>
            </div>
            <el-tag :type="statusTagType(item.status)" effect="light" size="small">{{ statusText(item.status) }}</el-tag>
          </div>
          <div class="card-info">
            <span class="info-line"><el-icon><Clock /></el-icon> {{ item.startTime }} → {{ item.endTime }}</span>
            <span class="info-line" v-if="item.checkinFlag"><el-icon><CircleCheck /></el-icon> 已打卡 {{ item.checkinTime }}</span>
            <span class="info-line away" v-if="isAway(item)"><el-icon><Timer /></el-icon> 暂离中，须于 {{ fmtTime(item.awayUntil) }} 前返回（超时将自动释放并记违规）</span>
          </div>
          <div class="card-footer">
            <span class="remark" v-if="item.remark">备注：{{ item.remark }}</span>
            <div class="actions" @click.stop>
              <el-button v-if="item.status === 1" type="success" size="small" :icon="EditPen" @click="doCodeCheckin(item)">验证码打卡</el-button>
              <el-button v-if="item.status === 1" type="danger" size="small" plain :icon="Close" @click="doCancel(item)">取消</el-button>
              <template v-if="item.status === 2">
                <el-button v-if="isAway(item)" type="primary" size="small" @click="doCancelAway(item)">结束暂离</el-button>
                <template v-else>
                  <el-button type="warning" size="small" plain @click="doAway(item, 30)">暂离30分钟</el-button>
                  <el-button type="warning" size="small" plain :disabled="!mealTime" @click="doAway(item, 60)">
                    暂离1小时{{ mealTime ? '' : '（仅饭点）' }}
                  </el-button>
                </template>
                <el-button type="success" size="small" @click="doRelease(item)">提前释放</el-button>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && reservations.length === 0" description="暂无预约记录" />

    <!-- 批量删除记录 -->
    <el-dialog v-model="cleanupVisible" title="批量删除预约记录" width="560px">
      <el-alert type="info" :closable="false" show-icon class="cleanup-tip">
        <template #title>仅"已结束"的记录可删除（已结束 / 已取消 / 爽约 / 违规）；删除后不再显示，管理端仍保留操作日志。</template>
      </el-alert>
      <div class="cleanup-bar">
        <el-checkbox v-model="cleanupAll" :indeterminate="cleanupIndeterminate" @change="onCleanupAllChange">全选</el-checkbox>
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
        <el-button type="danger" :loading="cleanupLoading" :disabled="selectedIds.length === 0" @click="doCleanup">
          删除选中（{{ selectedIds.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, CircleCheck, EditPen, Close, Timer, Delete, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { resourceImage } from '@/utils/resourceImage'

const router = useRouter()
const loading = ref(false)
const activeTab = ref('0')
const reservations = ref([])
const page = reactive({ current: 1, size: 20 })

// 每分钟刷新一次，用于饭点判断与暂离倒计时
const now = ref(new Date())
let timer = null

// 饭点暂离时段：午餐 11:00-12:30、晚餐 17:30-19:00
const mealTime = computed(() => {
  const m = now.value.getHours() * 60 + now.value.getMinutes()
  return (m >= 11 * 60 && m <= 12 * 60 + 30) || (m >= 17 * 60 + 30 && m <= 19 * 60)
})

function isAway(item) {
  return item.status === 2 && item.awayUntil && new Date(String(item.awayUntil).replace(' ', 'T')) > now.value
}

function fmtTime(dt) {
  return dt ? String(dt).replace('T', ' ').slice(11, 16) : ''
}

function getTypeImage(res) { return resourceImage(res) }
function statusText(s) { return { 1: '待打卡', 2: '使用中', 3: '已结束', 4: '已取消', 5: '爽约', 6: '违规' }[s] || '未知' }
// 配色：待打卡=黄、使用中=蓝、已结束/已取消=灰、爽约/违规=红
function statusTagType(s) { return { 1: 'warning', 2: 'primary', 3: 'info', 4: 'info', 5: 'danger', 6: 'danger' }[s] || 'info' }

// 可删除：仅限已结束的预约（已结束 / 已取消 / 爽约 / 违规）
function canDelete(item) { return [3, 4, 5, 6].includes(item.status) }

// 使用中的预约：点击卡片跳转到该资源详情
function onCardClick(item) {
  if (item.status !== 2) return
  const rid = item.resource?.id ?? item.resourceId
  if (rid) router.push(`/resource/${rid}`)
}

function onTabChange() {
  page.current = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.getMyReservations({
      current: page.current,
      size: page.size,
      status: activeTab.value !== '0' ? activeTab.value : undefined
    })
    if (res.code === 200) {
      reservations.value = res.data.records
    }
  } finally {
    loading.value = false
  }
}

async function doCodeCheckin(item) {
  try {
    const { value } = await ElMessageBox.prompt('请输入资源当日验证码', '验证码打卡', {
      inputPattern: /\d{6}/, inputErrorMessage: '请输入6位数字验证码', confirmButtonText: '验证'
    })
    const res = await api.checkinByCode({ reservationId: item.id, code: value })
    if (res.code === 200) { ElMessage.success('验证码打卡成功'); loadData() } else { ElMessage.error(res.message) }
  } catch { /* cancelled */ }
}

async function doCancel(item) {
  try {
    await ElMessageBox.confirm('确定取消此预约？频繁取消可能影响信用分', '警告', { type: 'warning' })
    const res = await api.cancelReservation(item.id)
    if (res.code === 200) {
      ElMessage.success('已取消')
      loadData()
    }
  } catch { /* cancelled */ }
}

// ---- 批量删除（统一入口）----
const cleanupVisible = ref(false)
const cleanupLoading = ref(false)
const selectedIds = ref([])
const cleanupAll = ref(false)
const cleanupIndeterminate = computed(() =>
  selectedIds.value.length > 0 && selectedIds.value.length < deletableList.value.length)

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
      `确定删除选中的 ${selectedIds.value.length} 条预约记录？删除后不再显示，管理端仍保留操作日志。`,
      '批量删除', { type: 'warning', confirmButtonText: '确定删除' }
    )
  } catch { return }
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
    await ElMessageBox.confirm('提前释放后将立即结束本次预约并释放空间，确定继续？', '提前释放空间', { type: 'warning', confirmButtonText: '确定释放' })
  } catch { return }
  const res = await api.releaseReservation(item.id)
  if (res.code === 200) {
    ElMessage.success('已提前释放空间')
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

async function doAway(item, minutes) {
  const label = minutes === 60 ? '1 小时' : '30 分钟'
  try {
    await ElMessageBox.confirm(
      `暂离 ${label} 内空间将为你保留，超时未返回将自动释放并记一次违规。确定暂离？`,
      '暂离', { type: 'info', confirmButtonText: '确定暂离' }
    )
  } catch { return }
  const res = await api.awayReservation(item.id, minutes)
  if (res.code === 200) {
    ElMessage.success(`已暂离 ${label}`)
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

async function doCancelAway(item) {
  const res = await api.cancelAway(item.id)
  if (res.code === 200) {
    ElMessage.success('已结束暂离')
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

onMounted(() => {
  loadData()
  timer = setInterval(() => { now.value = new Date() }, 60000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
.my-reservations-page { max-width: 900px; margin: 0 auto; }

.reservation-list { display: flex; flex-direction: column; gap: 12px; }

.list-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 4px;
  .toolbar-hint { font-size: 13px; color: #909399; }
}

.cleanup-tip { margin-bottom: 10px; }
.cleanup-bar { padding: 0 2px 6px; }
.cleanup-list {
  display: flex; flex-direction: column; gap: 2px;
  max-height: 320px; overflow-y: auto;
  .cleanup-item {
    display: flex; align-items: center; gap: 10px;
    padding: 6px 10px; border-radius: 8px;
    &:hover { background: #f5f7fa; }
    .ci-name { flex: 1; font-size: 13px; color: #303133; }
    .ci-status { font-size: 12px; color: #909399; }
    .ci-time { font-size: 12px; color: #a8abb2; }
  }
}

.reservation-card {
  display: flex;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover { transform: translateX(4px); box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1); }

  /* 使用中的预约：整张卡片可点击，跳转到资源详情 */
  &.clickable {
    cursor: pointer;
    &:hover { box-shadow: 0 6px 20px rgba(64, 158, 255, 0.28); }
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
      background: linear-gradient(90deg, rgba(0,0,0,0.25) 0%, rgba(0,0,0,0.1) 100%);
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

      .res-name { font-size: 16px; font-weight: 600; color: #303133; }
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

        &.away { color: #e6a23c; }
      }
    }

    .card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .remark { font-size: 13px; color: #a8abb2; }
      .actions { display: flex; flex-wrap: wrap; gap: 6px; justify-content: flex-end; }
    }
  }
}
</style>
