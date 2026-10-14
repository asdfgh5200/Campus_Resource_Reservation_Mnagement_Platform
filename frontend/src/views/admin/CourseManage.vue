<template>
  <div class="course-manage">
    <el-card shadow="never" class="main-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="filterBuilding" placeholder="楼栋" clearable style="width: 150px" @change="loadData">
            <el-option label="教学楼A栋" value="教学楼A栋" />
            <el-option label="教学楼B栋" value="教学楼B栋" />
          </el-select>
          <el-select v-model="filterDay" placeholder="星期" clearable style="width: 120px" @change="loadData">
            <el-option v-for="d in 7" :key="d" :label="dayText(d)" :value="d" />
          </el-select>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Upload" @click="openImport">批量导入</el-button>
          <el-button type="primary" :icon="Plus" @click="openForm()">手动填写</el-button>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon class="tip">
        <template #title>教室不参与预约，仅用于展示课表占用。用户端会在对应时段把教室标红，点击可查看课程安排。</template>
      </el-alert>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="courses" stripe style="width: 100%">
        <el-table-column label="教室" min-width="150">
          <template #default="{ row }">{{ row.roomName }}</template>
        </el-table-column>
        <el-table-column label="楼栋/楼层" width="150">
          <template #default="{ row }">{{ row.building }} {{ row.floor }}</template>
        </el-table-column>
        <el-table-column label="星期" width="90">
          <template #default="{ row }">{{ dayText(row.dayOfWeek) }}</template>
        </el-table-column>
        <el-table-column label="上课时间" width="180">
          <template #default="{ row }">{{ short(row.startTime) }} - {{ short(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="课程名称" min-width="160" prop="courseName" />
        <el-table-column label="任课教师" width="120">
          <template #default="{ row }">{{ row.teacher || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="danger" :icon="Delete" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && courses.length === 0" description="暂无课表记录，可点击「批量导入」或「手动填写」" />
    </el-card>

    <!-- 手动填写 -->
    <el-dialog v-model="formVisible" title="填写课表" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="教室" required>
          <el-select v-model="form.roomId" filterable placeholder="选择教室" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="`${r.roomNo} ${r.building}${r.floor}`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="星期" required>
          <el-select v-model="form.dayOfWeek" placeholder="选择星期" style="width: 100%">
            <el-option v-for="d in 7" :key="d" :label="dayText(d)" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="上课时间" required>
          <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm:ss" placeholder="开始" />
          <span style="margin: 0 8px">-</span>
          <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm:ss" placeholder="结束" />
        </el-form-item>
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" placeholder="如 高等数学" />
        </el-form-item>
        <el-form-item label="任课教师">
          <el-input v-model="form.teacher" placeholder="选填" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="form.term" placeholder="选填，如 2026春" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入课表" width="640px">
      <el-alert type="info" :closable="false" show-icon class="tip">
        <template #title>
          每行一条，格式：<b>教室,课程名称,星期,开始时间,结束时间,教师</b>（教师可省略）<br />
          示例：A101,高等数学,1,08:00,09:40,张老师<br />
          星期可写 1~7 或 周一~周日；也可先选择 CSV/TXT 文件自动填入下方文本框。
        </template>
      </el-alert>
      <div class="import-actions">
        <input ref="fileInput" type="file" accept=".csv,.txt" style="display: none" @change="onFilePicked" />
        <el-button :icon="Upload" @click="fileInput.click()">选择文件</el-button>
      </div>
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="A101,高等数学,1,08:00,09:40,张老师" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">解析并导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Delete, Upload, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const importing = ref(false)
const courses = ref([])
const rooms = ref([])
const filterBuilding = ref('')
const filterDay = ref(null)

const formVisible = ref(false)
const importVisible = ref(false)
const importText = ref('')
const fileInput = ref(null)

const form = reactive({ roomId: null, dayOfWeek: null, startTime: '', endTime: '', courseName: '', teacher: '', term: '' })

const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
function dayText(d) { return DAY_NAMES[d - 1] || d }
function short(t) { return t ? String(t).slice(0, 5) : '' }

async function loadRooms() {
  const res = await api.getResourcePage({ current: 1, size: 500, type: 1 })
  if (res.code === 200) rooms.value = res.data.records
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.getCourses({
      building: filterBuilding.value || undefined,
      dayOfWeek: filterDay.value ?? undefined
    })
    if (res.code === 200) courses.value = res.data
  } finally {
    loading.value = false
  }
}

function openForm() {
  Object.assign(form, { roomId: null, dayOfWeek: null, startTime: '', endTime: '', courseName: '', teacher: '', term: '' })
  formVisible.value = true
}

async function submitForm() {
  if (!form.roomId) { ElMessage.warning('请选择教室'); return }
  if (!form.dayOfWeek) { ElMessage.warning('请选择星期'); return }
  if (!form.startTime || !form.endTime) { ElMessage.warning('请选择上课时间'); return }
  if (!form.courseName) { ElMessage.warning('请填写课程名称'); return }
  submitting.value = true
  try {
    const res = await api.importCourses([{
      roomId: form.roomId,
      courseName: form.courseName,
      dayOfWeek: form.dayOfWeek,
      startTime: form.startTime,
      endTime: form.endTime,
      teacher: form.teacher,
      term: form.term
    }])
    if (res.code === 200) {
      ElMessage.success('已保存')
      formVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

function openImport() {
  importText.value = ''
  importVisible.value = true
}

function onFilePicked(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => { importText.value = String(reader.result || '') }
  reader.readAsText(file, 'UTF-8')
  e.target.value = ''
}

const WEEK_ALIAS = { 周一: 1, 周二: 2, 周三: 3, 周四: 4, 周五: 5, 周六: 6, 周日: 7, 周天: 7, 星期一: 1, 星期二: 2, 星期三: 3, 星期四: 4, 星期五: 5, 星期六: 6, 星期日: 7, 星期天: 7 }

function parseDay(s) {
  const v = String(s || '').trim()
  if (/^[1-7]$/.test(v)) return Number(v)
  return WEEK_ALIAS[v] || null
}

function parseHM(s) {
  const m = String(s || '').trim().match(/^(\d{1,2}):(\d{2})/)
  if (!m) return null
  return `${String(m[1]).padStart(2, '0')}:${m[2]}:00`
}

async function doImport() {
  const lines = importText.value.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
  if (lines.length === 0) { ElMessage.warning('请先粘贴或选择课表内容'); return }
  const roomMap = new Map()
  rooms.value.forEach(r => {
    roomMap.set(String(r.roomNo).trim().toUpperCase(), r.id)
    roomMap.set(String(r.name).trim().toUpperCase(), r.id)
  })
  const list = []
  const errors = []
  lines.forEach((line, i) => {
    if (i === 0 && /教室|room/i.test(line) && /课程|course/i.test(line)) return // 跳过表头
    const cols = line.split(/[,，\t]/).map(c => c.trim())
    if (cols.length < 5) { errors.push(`第 ${i + 1} 行列数不足`); return }
    const [room, courseName, day, start, end, teacher] = cols
    const roomId = roomMap.get(String(room).toUpperCase())
    if (!roomId) { errors.push(`第 ${i + 1} 行教室「${room}」不存在`); return }
    const dayOfWeek = parseDay(day)
    if (!dayOfWeek) { errors.push(`第 ${i + 1} 行星期「${day}」无法识别`); return }
    const st = parseHM(start)
    const et = parseHM(end)
    if (!st || !et) { errors.push(`第 ${i + 1} 行时间格式应为 HH:mm`); return }
    list.push({ roomId, courseName, dayOfWeek, startTime: st, endTime: et, teacher: teacher || null })
  })
  if (errors.length) {
    ElMessage.error(`解析失败：${errors.slice(0, 3).join('；')}${errors.length > 3 ? ` 等 ${errors.length} 处` : ''}`)
    return
  }
  importing.value = true
  try {
    const res = await api.importCourses(list)
    if (res.code === 200) {
      ElMessage.success(`成功导入 ${res.data} 条课表`)
      importVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    importing.value = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.roomName} ${dayText(row.dayOfWeek)} ${short(row.startTime)} ${row.courseName}」？`, '警告', { type: 'warning' })
  } catch { return }
  const res = await api.deleteCourse(row.id)
  if (res.code === 200) {
    ElMessage.success('已删除')
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

onMounted(async () => {
  await loadRooms()
  await loadData()
})
</script>

<style scoped lang="scss">
.course-manage { max-width: 1200px; margin: 0 auto; }

.main-card { border-radius: 14px; border: none; }

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;

  .toolbar-left, .toolbar-right { display: flex; gap: 10px; align-items: center; }
}

.tip { margin-bottom: 16px; }

.import-actions { margin: 12px 0 8px; }
</style>
