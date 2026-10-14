<template>
  <div class="resource-manage-page">
    <el-card shadow="never" class="main-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" placeholder="搜索资源名称" :prefix-icon="Search" clearable class="search-input" @clear="loadData" @keyup.enter="loadData" />
          <el-select v-model="filterType" placeholder="资源类型" clearable @change="loadData" class="type-select">
            <el-option label="教室" :value="1" />
            <el-option label="实验室" :value="2" />
            <el-option label="图书馆" :value="3" />
            <el-option label="体育馆" :value="4" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="状态" clearable @change="loadData" class="type-select">
            <el-option label="可预约" :value="1" />
            <el-option label="使用中" :value="2" />
            <el-option label="维修中" :value="3" />
          </el-select>
        </div>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增资源</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="resources" v-loading="loading" style="width: 100%" row-key="id">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.resourceType)" effect="light">{{ typeText(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="building" label="楼宇" width="120" />
        <el-table-column prop="roomNo" label="房间号" width="100" />
        <el-table-column prop="capacity" label="容量" width="80" />
        <el-table-column label="开放时间" width="160">
          <template #default="{ row }">
            {{ row.openTime?.slice(0,5) }} - {{ row.closeTime?.slice(0,5) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text type="success" :icon="Key" @click="showCheckinCode(row)">打卡码</el-button>
            <el-button text type="primary" :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="page.current"
          :page-size="page.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑资源' : '新增资源'" width="560px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" placeholder="如 A101" />
        </el-form-item>
        <el-form-item label="资源类型" required>
          <el-radio-group v-model="editForm.resourceType">
            <el-radio-button :value="1">教室</el-radio-button>
            <el-radio-button :value="2">实验室</el-radio-button>
            <el-radio-button :value="3">图书馆</el-radio-button>
            <el-radio-button :value="4">体育馆</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="楼宇">
          <el-input v-model="editForm.building" placeholder="如 第一教学楼" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-input v-model="editForm.floor" placeholder="如 1F" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="editForm.roomNo" placeholder="如 A101" />
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number v-model="editForm.capacity" :min="0" :max="500" />
        </el-form-item>
        <el-form-item label="计量单位">
          <el-input v-model="editForm.unit" placeholder="人 / 台 / 片" style="width: 160px" />
          <span style="margin-left: 8px; font-size: 12px; color: #909399">体育馆各场馆不同（乒乓球馆=台、球场=片）</span>
        </el-form-item>
        <el-form-item label="开放时间">
          <el-time-picker v-model="editForm.openTimeObj" format="HH:mm" value-format="HH:mm:00" placeholder="开始" />
          <span style="margin: 0 8px">-</span>
          <el-time-picker v-model="editForm.closeTimeObj" format="HH:mm" value-format="HH:mm:00" placeholder="结束" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 当日验证码弹窗 -->
    <el-dialog v-model="codeDialogVisible" :title="`当日验证码 — ${codeData.resourceName}`" width="360px" align-center>
      <div class="code-box">
        <p class="code-hint">将下方验证码告知到场用户，用于验证码打卡</p>
        <div class="code-value">{{ codeData.code }}</div>
        <p class="code-date">有效日期：{{ codeData.validDate }}</p>
        <el-button type="primary" :icon="DocumentCopy" @click="copyCode">复制验证码</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Plus, Edit, Delete, Key, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { copyText as copyToClipboard } from '@/utils/clipboard'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterType = ref(undefined)
const filterStatus = ref(undefined)
const resources = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)

const editForm = reactive({
  id: null, name: '', resourceType: 1, building: '', floor: '',
  roomNo: '', capacity: 0, unit: '人', openTimeObj: '08:00:00', closeTimeObj: '22:00:00',
  maxDuration: 120, description: ''
})

// 当日验证码
const codeDialogVisible = ref(false)
const codeData = reactive({ resourceId: null, resourceName: '', code: '', validDate: '' })

async function showCheckinCode(row) {
  const res = await api.getCheckinCode(row.id)
  if (res.code !== 200) { ElMessage.error(res.message); return }
  codeData.resourceId = row.id
  codeData.resourceName = row.name
  codeData.code = res.data.code
  codeData.validDate = res.data.validDate
  codeDialogVisible.value = true
}

async function copyCode() {
  const ok = await copyToClipboard(codeData.code)
  if (ok) ElMessage.success('验证码已复制')
  else ElMessage.warning('复制失败，请手动选中复制')
}

function typeText(t) { return { 1: '教室', 2: '实验室', 3: '图书馆', 4: '体育馆' }[t] || '未知' }
function typeTagType(t) { return { 1: '', 2: 'success', 3: 'warning', 4: 'danger' }[t] || 'info' }
function statusText(s) { return { 1: '可预约', 2: '使用中', 3: '维修中' }[s] || '未知' }
// 配色约定：可预约/可用=绿，已占用/使用中=红，维修中=黄
function statusTagType(s) { return { 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info' }

async function loadData() {
  loading.value = true
  try {
    const res = await api.getResourcePage({
      current: page.current,
      size: page.size,
      type: filterType.value || undefined,
      status: filterStatus.value || undefined,
      keyword: keyword.value || undefined
    })
    if (res.code === 200) {
      resources.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(editForm, {
      id: row.id, name: row.name, resourceType: row.resourceType,
      building: row.building, floor: row.floor, roomNo: row.roomNo,
      capacity: row.capacity, unit: row.unit || '人',
      openTimeObj: row.openTime, closeTimeObj: row.closeTime,
      maxDuration: row.maxDuration, description: row.description
    })
  } else {
    Object.assign(editForm, {
      id: null, name: '', resourceType: 1, building: '', floor: '',
      roomNo: '', capacity: 0, unit: '人', openTimeObj: '08:00:00', closeTimeObj: '22:00:00',
      maxDuration: 120, description: ''
    })
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!editForm.name) { ElMessage.warning('请填写资源名称'); return }
  submitting.value = true
  try {
    const payload = {
      id: editForm.id,
      name: editForm.name,
      resourceType: editForm.resourceType,
      building: editForm.building,
      floor: editForm.floor,
      roomNo: editForm.roomNo,
      capacity: editForm.capacity,
      unit: editForm.unit || '人',
      openTime: editForm.openTimeObj,
      closeTime: editForm.closeTimeObj,
      maxDuration: editForm.maxDuration,
      description: editForm.description
    }
    const res = editForm.id
      ? await api.updateResource(payload)
      : await api.addResource(payload)
    if (res.code === 200) {
      ElMessage.success(editForm.id ? '更新成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '警告', { type: 'warning' })
    const res = await api.deleteResource(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    }
  } catch { /* cancelled */ }
}

// 支持从「数据概览」点击卡片带筛选跳转：/admin/resources?status=1&type=2
onMounted(() => {
  if (route.query.type) filterType.value = Number(route.query.type)
  if (route.query.status) filterStatus.value = Number(route.query.status)
  loadData()
})
</script>

<style scoped lang="scss">
.resource-manage-page { max-width: 1200px; margin: 0 auto; }

.main-card {
  border-radius: 14px;
  border: none;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    gap: 12px;

    .search-input { width: 240px; }
    .type-select { width: 140px; }
  }
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.code-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 4px 0;

  .code-hint { font-size: 12px; color: #909399; }
  .code-value {
    font-size: 34px;
    font-weight: 700;
    letter-spacing: 6px;
    color: #303133;
    font-family: 'Consolas', 'Courier New', monospace;
    user-select: all;
  }
  .code-date { font-size: 13px; color: #909399; }
}
</style>
