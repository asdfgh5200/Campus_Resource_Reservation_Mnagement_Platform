<template>
  <div class="closure-manage">
    <!-- 总体一键闭馆（嵌在数据概览的抽屉里时由页面顶部的按钮负责，这里隐藏） -->
    <el-card v-if="showCampus" shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>总体一键闭馆</span>
          <span class="hd-hint"
            >应对节假日/突发情况，闭馆期间普通用户不可新建预约，管理员不受限</span
          >
        </div>
      </template>
      <div class="campus-bar">
        <el-icon :size="20" :color="campusClosed ? '#f56c6c' : '#67c23a'">
          <component :is="campusClosed ? 'WarnTriangleFilled' : 'CircleCheckFilled'" />
        </el-icon>
        <span class="cb-title">校园状态：{{ campusClosed ? '已闭馆' : '开放中' }}</span>
        <el-button
          v-if="!campusClosed"
          type="danger"
          :loading="switching"
          class="cb-btn"
          @click="doCampusToggle(true)"
          >一键闭馆</el-button
        >
        <el-button
          v-else
          type="success"
          :loading="switching"
          class="cb-btn"
          @click="doCampusToggle(false)"
          >恢复开放</el-button
        >
      </div>
    </el-card>

    <!-- 定向闭馆：整类或指定具体资源 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <div class="hd-left">
            <span class="hd-title">定向闭馆</span>
            <span class="hd-hint">可整类闭馆，也可只闭某个资源（如某个球馆 / 某间实验室）</span>
          </div>
          <el-button type="primary" @click="openAdd">新增闭馆</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无生效中的闭馆">
        <el-table-column label="闭馆范围" min-width="210">
          <template #default="{ row }">
            <span v-if="row.resourceId" class="scope-one">
              {{ row.resourceName || '资源 #' + row.resourceId }}
            </span>
            <span v-else class="scope-all">全部{{ typeLabel(row.resourceType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="闭馆日期" width="130">
          <template #default="{ row }">{{ row.closureDate }}</template>
        </el-table-column>
        <el-table-column label="时段" width="150">
          <template #default="{ row }"
            >{{ fmtTime(row.startTime) }} ~ {{ fmtTime(row.endTime) }}</template
          >
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="doRevoke(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增闭馆弹窗 -->
    <el-dialog v-model="addVisible" title="新增闭馆" width="560px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="闭馆范围">
          <el-radio-group v-model="form.scope" @change="onScopeChange">
            <el-radio-button label="type">整类资源</el-radio-button>
            <el-radio-button label="resource">指定资源</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select
            v-model="form.resourceType"
            placeholder="选择类型"
            style="width: 100%"
            @change="onTypeChange"
          >
            <el-option :value="2" label="实验室" />
            <el-option :value="3" label="图书馆" />
            <el-option :value="4" label="体育馆" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'resource'" label="具体资源">
          <el-select
            v-model="form.resourceId"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="searchResources"
            :loading="resourceLoading"
            placeholder="输入资源名称搜索，如 羽毛球"
            style="width: 100%"
          >
            <el-option
              v-for="r in resourceOptions"
              :key="r.id"
              :label="resourceLabel(r)"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="闭馆日期">
          <el-date-picker
            v-model="form.closureDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="开始时刻">
          <el-time-select
            v-model="form.startTime"
            start="06:00"
            step="00:30"
            end="23:30"
            placeholder="开始"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时刻">
          <el-time-select
            v-model="form.endTime"
            :min-time="form.startTime"
            start="06:00"
            step="00:30"
            end="23:30"
            placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="闭馆原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如：设备检修 / 消杀 / 临时活动"
          />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="闭馆会自动取消落在该时段内的未来预约并向预约者发送通知；若范围内此刻有资源正在使用中，则不允许闭馆。"
        />
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">确定闭馆</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarnTriangleFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import api from '@/api'

defineProps({
  // 是否展示顶部「总体一键闭馆」卡片：独立页面/抽屉里均可见，宽度受限时可关闭
  showCampus: { type: Boolean, default: true }
})

const loading = ref(false)
const rows = ref([])
const campusClosed = ref(false)
const switching = ref(false)

const addVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  scope: 'type',
  resourceType: 2,
  resourceId: null,
  closureDate: '',
  startTime: '',
  endTime: '',
  reason: ''
})

// 指定资源时按类型 + 关键词远程搜索可选资源
const resourceOptions = ref([])
const resourceLoading = ref(false)

async function searchResources(keyword) {
  resourceLoading.value = true
  try {
    const res = await api.getResourcePage({
      current: 1,
      size: 50,
      type: form.resourceType,
      keyword: keyword || undefined
    })
    if (res.code === 200) resourceOptions.value = res.data.records || []
  } finally {
    resourceLoading.value = false
  }
}

function resourceLabel(r) {
  const place = [r.campus, r.building].filter(Boolean).join(' · ')
  return place ? `${r.name}（${place}）` : r.name
}

function onScopeChange() {
  if (form.scope === 'resource') {
    form.resourceId = null
    searchResources('')
  }
}

function onTypeChange() {
  form.resourceId = null
  if (form.scope === 'resource') searchResources('')
}

function typeLabel(t) {
  return { 2: '实验室', 3: '图书馆', 4: '体育馆' }[t] || '未知'
}
function fmtTime(t) {
  return t ? String(t).slice(0, 5) : ''
}

async function reload() {
  loading.value = true
  try {
    const [cl, cs] = await Promise.all([api.getClosures(), api.getCampusStatus()])
    if (cl.code === 0 || cl.code === 200) rows.value = cl.data || []
    if (cs.code === 0 || cs.code === 200) campusClosed.value = !!cs.data?.closed
  } finally {
    loading.value = false
  }
}

async function doCampusToggle(toClose) {
  let reason = ''
  try {
    if (toClose) {
      // 闭馆原因会写进给全体用户的闭馆通知，可留空
      const { value } = await ElMessageBox.prompt(
        '可填写闭馆原因，会写进发给全体用户的闭馆通知（可留空）。',
        '一键闭馆',
        {
          type: 'warning',
          inputPlaceholder: '如：台风停课 / 场馆检修',
          inputValidator: () => true,
          confirmButtonText: '确认闭馆',
          cancelButtonText: '取消'
        }
      )
      reason = (value || '').trim()
    } else {
      await ElMessageBox.confirm('确认恢复开放？恢复后会通知全体用户。', '恢复开放', {
        type: 'warning'
      })
    }
  } catch (e) {
    return
  }
  switching.value = true
  try {
    const res = toClose ? await api.campusClose(reason) : await api.campusOpen()
    if (res.code === 0 || res.code === 200) {
      campusClosed.value = toClose
      ElMessage.success(
        toClose ? '已闭馆，并已向全体用户发送闭馆通知' : '已恢复开放，并已通知全体用户'
      )
      // 让布局横幅与其它页面同步状态
      window.dispatchEvent(new Event('campus-status-changed'))
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    switching.value = false
  }
}

function openAdd() {
  form.scope = 'type'
  form.resourceType = 2
  form.resourceId = null
  form.closureDate = ''
  form.startTime = ''
  form.endTime = ''
  form.reason = ''
  resourceOptions.value = []
  addVisible.value = true
}

async function submitAdd() {
  if (!form.closureDate || !form.startTime || !form.endTime) {
    ElMessage.warning('请填写完整的闭馆日期与时段')
    return
  }
  if (form.scope === 'resource' && !form.resourceId) {
    ElMessage.warning('请选择要闭馆的具体资源')
    return
  }
  submitting.value = true
  try {
    const withSec = (t) => (t && t.length === 5 ? `${t}:00` : t)
    const res = await api.addClosure({
      // 指定资源时后端会按资源真实类型落库，这里只传必要字段
      resourceType: form.resourceType,
      resourceId: form.scope === 'resource' ? form.resourceId : null,
      closureDate: form.closureDate,
      startTime: withSec(form.startTime),
      endTime: withSec(form.endTime),
      reason: form.reason
    })
    if (res.code === 0 || res.code === 200) {
      const cancelled = res.data?.cancelled ?? 0
      ElMessage.success(
        cancelled > 0 ? `闭馆成功，已自动取消 ${cancelled} 条预约并通知用户` : '闭馆成功'
      )
      addVisible.value = false
      reload()
    } else {
      ElMessage.error(res.message || '闭馆失败')
    }
  } finally {
    submitting.value = false
  }
}

async function doRevoke(row) {
  const scope = row.resourceId
    ? row.resourceName || `资源 #${row.resourceId}`
    : `全部${typeLabel(row.resourceType)}`
  try {
    await ElMessageBox.confirm(
      `确认撤销「${scope} ${row.closureDate} ${fmtTime(row.startTime)}~${fmtTime(row.endTime)}」的闭馆？`,
      '撤销闭馆',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  const res = await api.deleteClosure(row.id)
  if (res.code === 0 || res.code === 200) {
    ElMessage.success('已撤销')
    reload()
  } else {
    ElMessage.error(res.message || '撤销失败')
  }
}

onMounted(() => {
  reload()
  // 智约机器人确认闭馆/恢复后，本页列表与开关也要刷新
  window.addEventListener('campus-status-changed', reload)
})

onUnmounted(() => {
  window.removeEventListener('campus-status-changed', reload)
})
</script>

<style scoped lang="scss">
.closure-manage {
  max-width: 1400px;
  margin: 0 auto;
}
.block {
  margin-bottom: 16px;
  border-radius: 14px;
}
.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.hd-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.hd-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}
.hd-hint {
  font-size: 12px;
  color: #909399;
}
.scope-one {
  font-weight: 500;
  color: #1f2d3d;
}
.scope-all {
  color: #2e76db;
  font-weight: 500;
}
.campus-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cb-title {
  font-weight: 500;
}
.cb-btn {
  margin-left: auto;
}
</style>
