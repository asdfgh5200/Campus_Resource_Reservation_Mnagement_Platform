<template>
  <div class="repair-manage">
    <el-card class="main-card" shadow="never">
      <template #header>
        <div class="page-head">
          <p class="page-tip">
            用户提交报修后先进入「待审核」，地点仍可正常预约；确认维修 →
            资源变为「维修中」并暂停预约， 完成维修 →
            恢复「可预约」。每次处理都会自动给报修人发一条站内通知，驳回需填写原因。
          </p>
          <el-select
            v-model="query.status"
            class="status-filter"
            clearable
            placeholder="全部工单"
            @change="search"
          >
            <el-option label="待审核" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无维修工单">
        <el-table-column label="资源" min-width="150">
          <template #default="{ row }">{{
            row.resource?.name || `资源 #${row.resourceId}`
          }}</template>
        </el-table-column>
        <el-table-column label="位置" min-width="150">
          <template #default="{ row }">{{ locationText(row.resource) }}</template>
        </el-table-column>
        <el-table-column label="报修人" min-width="130">
          <template #default="{ row }">
            <div class="reporter">
              <span class="rep-name">{{ row.reporterName || `用户 #${row.userId}` }}</span>
              <span class="rep-acc">{{ row.reporterAccount || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="报修说明" min-width="215">
          <template #default="{ row }">
            <div class="problem">{{ row.problem }}</div>
            <el-image
              v-if="row.imageUrl"
              class="problem-img"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              fit="cover"
              preview-teleported
            />
          </template>
        </el-table-column>
        <el-table-column label="提交 / 处理" min-width="165">
          <template #default="{ row }">
            <div class="times">
              <span>提交 {{ formatDate(row.createdAt) }}</span>
              <span v-if="row.handleTime" class="muted">处理 {{ formatDate(row.handleTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 3 && row.rejectReason"
              :content="`驳回原因：${row.rejectReason}`"
              placement="top"
            >
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理" width="190" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="handle(row, 1)">开始维修</el-button>
              <el-button link type="danger" @click="handle(row, 3)">驳回</el-button>
            </template>
            <el-button v-else-if="row.status === 1" link type="success" @click="handle(row, 2)"
              >完成维修</el-button
            >
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > query.size"
        v-model:current-page="query.current"
        class="pager"
        background
        layout="total, prev, pager, next"
        :page-size="query.size"
        :total="total"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: undefined })

const statusMap = {
  0: ['待审核', 'warning'],
  1: ['处理中', 'primary'],
  2: ['已完成', 'success'],
  3: ['已驳回', 'info']
}

function statusText(status) {
  return statusMap[status]?.[0] || '未知'
}
function statusType(status) {
  return statusMap[status]?.[1] || 'info'
}
function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}
function locationText(resource) {
  if (!resource) return '—'
  return [resource.building, resource.floor, resource.roomNo].filter(Boolean).join(' ')
}

async function loadData() {
  loading.value = true
  try {
    const response = await api.getRepairs(query)
    if (response.code === 200) {
      rows.value = response.data.records || []
      total.value = response.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function search() {
  query.current = 1
  loadData()
}

const handlePrompts = {
  1: '确认开始维修？地点将标记为「维修中」并暂停预约，系统会自动通知报修人。',
  2: '确认维修已完成？地点将恢复为「可预约」，系统会自动通知报修人。'
}

async function handle(row, status) {
  if (status === 3) {
    await reject(row)
    return
  }
  try {
    await ElMessageBox.confirm(handlePrompts[status], '处理维修工单', {
      type: 'warning',
      confirmButtonText: '确认处理'
    })
  } catch {
    return
  }
  await submitHandle(row, { status })
}

// 驳回必须写原因：会随通知发给报修人，用户端工单卡片也会展示
async function reject(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写驳回原因，会随通知发送给报修人，便于对方了解处理结果。',
      '驳回维修工单',
      {
        inputType: 'textarea',
        inputPlaceholder: '例如：经核实为使用不当，设备本身正常',
        inputValidator: (v) => (v && v.trim() ? true : '请填写驳回原因'),
        confirmButtonText: '确认驳回'
      }
    )
    reason = value.trim()
  } catch {
    return
  }
  await submitHandle(row, { status: 3, reason })
}

async function submitHandle(row, payload) {
  const response = await api.handleRepair(row.id, payload)
  if (response.code === 200) {
    ElMessage.success('处理成功，已通知报修人')
    loadData()
  } else {
    ElMessage.error(response.message || '处理失败')
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.main-card {
  border: none;
  border-radius: 14px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-head h2 {
  margin: 0;
  color: #24344d;
  font-size: 18px;
}
.page-tip {
  margin: 0;
  color: #7a8799;
  font-size: 13px;
}
.status-filter {
  width: 140px;
}
.reporter {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rep-name {
  color: #24344d;
  font-weight: 600;
}
.rep-acc {
  color: #909399;
  font-size: 12px;
}
.problem {
  color: #43536b;
  line-height: 1.55;
  word-break: break-word;
}
.problem-img {
  width: 56px;
  height: 56px;
  margin-top: 6px;
  border-radius: 8px;
  cursor: zoom-in;
}
.times {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #43536b;
  font-size: 13px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
.muted {
  color: #909399;
  font-size: 13px;
}

@media (max-width: 768px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .status-filter {
    width: 100%;
  }
}
</style>
