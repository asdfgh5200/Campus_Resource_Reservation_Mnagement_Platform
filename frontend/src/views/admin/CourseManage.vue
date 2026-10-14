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

      <!-- 概览 -->
      <div class="sum-row">
        <div class="sum"><b>{{ courses.length }}</b><span>课表条数</span></div>
        <div class="sum"><b>{{ roomCount }}</b><span>涉及教室</span></div>
        <div class="sum"><b>{{ courseCount }}</b><span>课程门数</span></div>
      </div>

      <!-- 文档式入口：不再把上千条记录直接铺在页面上 -->
      <div class="doc-entry">
        <div class="doc-icon"><el-icon :size="26"><Document /></el-icon></div>
        <div class="doc-text">
          <h3>课表文档</h3>
          <p>
            全部课表已按「教室 → 星期」整理成一份文档，共 {{ courses.length }} 条记录。
            点击右侧按钮查阅，避免整页堆砌表格。
          </p>
        </div>
        <el-button type="primary" round size="large" @click="openDoc">查看课表文档</el-button>
      </div>
    </el-card>

    <!-- 课表文档 -->
    <el-drawer v-model="docVisible" title="武汉理工大学课表" size="72%" :destroy-on-close="true">
      <template #header>
        <div class="doc-header">
          <span class="doc-title">武汉理工大学课表</span>
          <span class="doc-sub">
            {{ filterBuilding || '全部楼栋' }} · {{ filterDay ? dayText(filterDay) : '周一~周日' }} ·
            共 {{ courses.length }} 条
          </span>
        </div>
      </template>

      <div class="doc-body">
        <el-empty v-if="docGroups.length === 0" description="暂无课表记录，可先「批量导入」" />
        <section v-for="g in docGroups" :key="g.roomName" class="doc-room">
          <h4 class="room-title">
            <span class="room-name">{{ g.roomName }}</span>
            <span class="room-meta">{{ g.building }} {{ g.floor }}</span>
          </h4>
          <div v-for="d in g.dayList" :key="d.day" class="doc-day">
            <span class="day-label">{{ dayText(d.day) }}</span>
            <div class="day-items">
              <div v-for="c in d.items" :key="c.id" class="doc-item">
                <span class="it-time">{{ short(c.startTime) }} - {{ short(c.endTime) }}</span>
                <span class="it-course">{{ c.courseName }}</span>
                <span class="it-teacher">{{ c.teacher || '—' }}</span>
                <el-button text type="danger" size="small" :icon="Delete" @click="doDelete(c)" />
              </div>
            </div>
          </div>
        </section>
      </div>
    </el-drawer>

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
          示例：A101,高等数学,1,08:00,09:35,张老师<br />
          星期可写 1~7 或 周一~周日；也可先选择 CSV/TXT 文件自动填入下方文本框。
        </template>
      </el-alert>
      <div class="import-actions">
        <input ref="fileInput" type="file" accept=".csv,.txt" style="display: none" @change="onFilePicked" />
        <el-button :icon="Upload" @click="fileInput.click()">选择文件</el-button>
      </div>
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="A101,高等数学,1,08:00,09:35,张老师" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">解析并导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Delete, Upload, Refresh, Document } from '@element-plus/icons-vue'
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
const docVisible = ref(false)

const form = reactive({ roomId: null, dayOfWeek: null, startTime: '', endTime: '', courseName: '', teacher: '', term: '' })

const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
function dayText(d) { return DAY_NAMES[d - 1] || d }
function short(t) { return t ? String(t).slice(0, 5) : '' }

// 概览统计
const roomCount = computed(() => new Set(courses.value.map(c => c.roomName)).size)
const courseCount = computed(() => new Set(courses.value.map(c => c.courseName)).size)

// 文档结构：教室 → 星期 → 课程条目
const docGroups = computed(() => {
  const map = new Map()
  for (const c of courses.value) {
    const key = c.roomName || `#${c.roomId}`
    if (!map.has(key)) {
      map.set(key, { roomName: key, building: c.building, floor: c.floor, days: new Map() })
    }
    const g = map.get(key)
    if (!g.days.has(c.dayOfWeek)) g.days.set(c.dayOfWeek, [])
    g.days.get(c.dayOfWeek).push(c)
  }
  return Array.from(map.values()).map(g => ({
    roomName: g.roomName,
    building: g.building,
    floor: g.floor,
    dayList: Array.from(g.days.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([day, items]) => ({
        day,
        items: items.slice().sort((x, y) => String(x.startTime).localeCompare(String(y.startTime)))
      }))
  })).sort((a, b) => String(a.roomName).localeCompare(String(b.roomName), 'zh'))
})

function openDoc() { docVisible.value = true }

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

/* 概览 */
.sum-row {
  display: flex; gap: 12px; margin-bottom: 16px;
  .sum {
    flex: 1; text-align: center; padding: 14px 0; border-radius: 12px;
    background: #f7f9fc; border: 1px solid #eef2f7;
    b { display: block; font-size: 24px; font-weight: 800; color: #303133; }
    span { font-size: 12px; color: #909399; }
  }
}

/* 文档入口 */
.doc-entry {
  display: flex; align-items: center; gap: 16px;
  padding: 20px 22px; border-radius: 14px;
  background: linear-gradient(135deg, #eef5ff 0%, #f3fbf7 100%);
  border: 1px solid #e3ecf9;
  .doc-icon {
    width: 52px; height: 52px; border-radius: 14px; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    color: #fff; background: linear-gradient(135deg, #2E76DB, #74B9F5);
  }
  .doc-text {
    flex: 1;
    h3 { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 4px; }
    p { font-size: 13px; color: #808894; line-height: 1.6; }
  }
}

/* 文档正文 */
.doc-header { display: flex; flex-direction: column;
  .doc-title { font-size: 17px; font-weight: 600; color: #303133; }
  .doc-sub { margin-top: 4px; font-size: 12px; color: #909399; }
}

.doc-body { padding: 4px 6px 24px; }

.doc-room {
  margin-bottom: 22px;
  break-inside: avoid;

  .room-title {
    display: flex; align-items: baseline; gap: 10px;
    padding-bottom: 6px; margin-bottom: 8px;
    border-bottom: 2px solid #eef2f7;
    .room-name { font-size: 15px; font-weight: 700; color: #303133; }
    .room-meta { font-size: 12px; color: #909399; }
  }

  .doc-day {
    display: flex; gap: 12px; padding: 3px 0;
    .day-label {
      flex: 0 0 44px; font-size: 13px; font-weight: 600; color: #409eff;
      padding-top: 3px;
    }
    .day-items { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .doc-item {
      display: flex; align-items: center; gap: 12px;
      padding: 4px 8px; border-radius: 8px; font-size: 13px;
      &:hover { background: #f7f9fc; }
      .it-time { flex: 0 0 108px; color: #909399; font-variant-numeric: tabular-nums; }
      .it-course { flex: 1; color: #303133; }
      .it-teacher { flex: 0 0 80px; color: #a8abb2; }
    }
  }
}
</style>
