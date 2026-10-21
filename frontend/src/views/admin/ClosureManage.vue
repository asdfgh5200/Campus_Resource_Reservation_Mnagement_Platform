<template>
  <div class="closure-manage">
    <!-- 总体一键闭馆 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>总体一键闭馆</span>
          <span class="hd-hint">应对节假日/突发情况，闭馆期间普通用户不可新建预约，管理员不受限</span>
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
        >一键闭馆</el-button>
        <el-button
          v-else
          type="success"
          :loading="switching"
          class="cb-btn"
          @click="doCampusToggle(false)"
        >恢复开放</el-button>
      </div>
    </el-card>

    <!-- 细粒度类型闭馆 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>细粒度闭馆（按资源类型 / 具体日期 / 时段）</span>
          <el-button type="primary" @click="openAdd">新增闭馆</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无生效中的闭馆">
        <el-table-column label="资源类型" width="120">
          <template #default="{ row }">{{ typeLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="闭馆日期" width="140">
          <template #default="{ row }">{{ row.closureDate }}</template>
        </el-table-column>
        <el-table-column label="时段" width="160">
          <template #default="{ row }">{{ fmtTime(row.startTime) }} ~ {{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="doRevoke(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增闭馆弹窗 -->
    <el-dialog v-model="addVisible" title="新增闭馆" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="资源类型">
          <el-select v-model="form.resourceType" placeholder="选择类型" style="width: 100%">
            <el-option :value="2" label="实验室" />
            <el-option :value="3" label="图书馆" />
            <el-option :value="4" label="体育馆" />
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
          <el-time-select v-model="form.startTime" start="06:00" step="00:30" end="23:30" placeholder="开始" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时刻">
          <el-time-select v-model="form.endTime" :min-time="form.startTime" start="06:00" step="00:30" end="23:30" placeholder="结束" style="width: 100%" />
        </el-form-item>
        <el-form-item label="闭馆原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="如：设备检修 / 消杀 / 临时活动" />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="闭馆会自动取消落在该时段内的未来预约并向预约者发送通知；若该类型此刻有资源正在使用中，则不允许闭馆。"
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarnTriangleFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import api from '@/api'

const loading = ref(false)
const rows = ref([])
const campusClosed = ref(false)
const switching = ref(false)

const addVisible = ref(false)
const submitting = ref(false)
const form = reactive({ resourceType: 2, closureDate: '', startTime: '', endTime: '', reason: '' })

function typeLabel(t) { return { 2: '实验室', 3: '图书馆', 4: '体育馆' }[t] || '未知' }
function fmtTime(t) { return t ? String(t).slice(0, 5) : '' }

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
  const tip = toClose ? '确认开启一键闭馆？开启后普通用户将无法新建预约。' : '确认恢复开放？'
  try {
    await ElMessageBox.confirm(tip, '一键闭馆', { type: 'warning' })
  } catch (e) { return }
  switching.value = true
  try {
    const res = toClose ? await api.campusClose() : await api.campusOpen()
    if (res.code === 0 || res.code === 200) {
      campusClosed.value = toClose
      ElMessage.success(toClose ? '已闭馆' : '已恢复开放')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    switching.value = false
  }
}

function openAdd() {
  form.resourceType = 2
  form.closureDate = ''
  form.startTime = ''
  form.endTime = ''
  form.reason = ''
  addVisible.value = true
}

async function submitAdd() {
  if (!form.closureDate || !form.startTime || !form.endTime) {
    ElMessage.warning('请填写完整的闭馆日期与时段')
    return
  }
  submitting.value = true
  try {
    const withSec = (t) => (t && t.length === 5 ? `${t}:00` : t)
    const res = await api.addClosure({
      resourceType: form.resourceType,
      closureDate: form.closureDate,
      startTime: withSec(form.startTime),
      endTime: withSec(form.endTime),
      reason: form.reason
    })
    if (res.code === 0 || res.code === 200) {
      const cancelled = res.data?.cancelled ?? 0
      ElMessage.success(cancelled > 0 ? `闭馆成功，已自动取消 ${cancelled} 条预约并通知用户` : '闭馆成功')
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
  try {
    await ElMessageBox.confirm(`确认撤销「${typeLabel(row.resourceType)} ${row.closureDate} ${fmtTime(row.startTime)}~${fmtTime(row.endTime)}」的闭馆？`, '撤销闭馆', { type: 'warning' })
  } catch (e) { return }
  const res = await api.deleteClosure(row.id)
  if (res.code === 0 || res.code === 200) {
    ElMessage.success('已撤销')
    reload()
  } else {
    ElMessage.error(res.message || '撤销失败')
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.block { margin-bottom: 16px; }
.hd { display: flex; align-items: center; justify-content: space-between; }
.hd-hint { font-size: 12px; color: #909399; }
.campus-bar { display: flex; align-items: center; gap: 10px; }
.cb-title { font-weight: 500; }
.cb-btn { margin-left: auto; }
</style>
