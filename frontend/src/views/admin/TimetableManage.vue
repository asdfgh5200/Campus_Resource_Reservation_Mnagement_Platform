<template>
  <div class="timetable-page">
    <!-- 操作栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="filter.resourceId" placeholder="选择资源" clearable style="width: 200px" @change="loadData">
          <el-option v-for="r in resourceList" :key="r.id" :label="`${r.name}（${r.building}${r.floor}）`" :value="r.id" />
        </el-select>
        <el-select v-model="filter.dayOfWeek" placeholder="星期" clearable style="width: 120px" @change="loadData">
          <el-option v-for="d in weekDays" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
        <el-input v-model="filter.keyword" placeholder="搜索课程/教师" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
        <div class="filter-actions">
          <el-button type="success" @click="showAdd = true">
            <el-icon><Plus /></el-icon> 新增课表
          </el-button>
          <el-button type="warning" @click="showImport = true">
            <el-icon><Upload /></el-icon> 批量导入
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 课表列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="resourceName" label="资源" min-width="140" />
        <el-table-column prop="dayOfWeek" label="星期" width="80">
          <template #default="{ row }">
            <el-tag :type="dayTagType(row.dayOfWeek)" size="small">{{ weekLabel(row.dayOfWeek) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.startTime }} - {{ row.endTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="120" />
        <el-table-column prop="teacher" label="教师" width="100" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="danger" text size="small" @click="removeEntry(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="summary">共 {{ tableData.length }} 条课表记录</div>
    </el-card>

    <!-- 新增课表弹窗 -->
    <el-dialog v-model="showAdd" title="新增课表" width="520px">
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="资源">
          <el-select v-model="addForm.resourceId" placeholder="选择资源" style="width: 100%" filterable>
            <el-option v-for="r in resourceList" :key="r.id" :label="`${r.name}（${r.building}${r.floor}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="星期">
          <el-select v-model="addForm.dayOfWeek" placeholder="选择星期" style="width: 100%">
            <el-option v-for="d in weekDays" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-time-picker v-model="addForm.startTime" format="HH:mm" value-format="HH:mm" placeholder="开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-time-picker v-model="addForm.endTime" format="HH:mm" value-format="HH:mm" placeholder="结束时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="课程名">
          <el-input v-model="addForm.courseName" placeholder="如：高等数学" />
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="addForm.teacher" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="showImport" title="批量导入课表" width="680px">
      <el-alert type="info" :closable="false" class="import-alert">
        <template #title>导入格式</template>
        每行一条记录，字段用 <b>制表符</b> 或 <b>逗号</b> 分隔：
        <code>资源ID, 星期(1-7), 开始时间, 结束时间, 课程名, 教师</code>
      </el-alert>
      <div class="sample-block">
        <div class="sample-title">示例（可直接复制）：</div>
        <pre class="sample-code">1,1,08:00,09:35,高等数学,王教授
1,3,14:00,15:35,大学英语,李教授
2,2,10:00,11:30,数据结构,张教授</pre>
        <el-button text type="primary" @click="importText = sampleText">填充示例</el-button>
      </div>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder="粘贴课表数据，每行一条..."
      />
      <div v-if="importResult.errors.length" class="import-errors">
        <el-alert type="warning" :closable="false" title="部分行导入失败">
          <ul><li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li></ul>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="showImport = false">取消</el-button>
        <el-button type="primary" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { mockApi } from '@/api/mock'

const weekDays = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]
const weekLabel = d => weekDays.find(w => w.value === d)?.label || d
const dayTagType = d => d <= 5 ? '' : 'warning'

const loading = ref(false)
const tableData = ref([])
const resourceList = ref([])

const filter = reactive({ resourceId: '', dayOfWeek: '', keyword: '' })

const showAdd = ref(false)
const addForm = reactive({ resourceId: '', dayOfWeek: '', startTime: '', endTime: '', courseName: '', teacher: '' })

const showImport = ref(false)
const importText = ref('')
const importResult = reactive({ added: 0, errors: [] })

const sampleText = `1,1,08:00,09:35,高等数学,王教授
1,3,14:00,15:35,大学英语,李教授
2,2,10:00,11:30,数据结构,张教授`

async function loadResources() {
  const res = await mockApi.getResourcePage({ current: 1, size: 999 })
  if (res.code === 200) {
    resourceList.value = res.data.list
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await mockApi.getTimetable(filter)
    if (res.code === 200) {
      // 补充资源名
      tableData.value = res.data.map(t => {
        const r = resourceList.value.find(x => x.id === t.resourceId)
        return { ...t, resourceName: r ? r.name : `资源#${t.resourceId}` }
      })
    }
  } finally {
    loading.value = false
  }
}

async function submitAdd() {
  if (!addForm.resourceId) { ElMessage.warning('请选择资源'); return }
  if (!addForm.dayOfWeek) { ElMessage.warning('请选择星期'); return }
  if (!addForm.startTime || !addForm.endTime) { ElMessage.warning('请选择起止时间'); return }
  if (!addForm.courseName) { ElMessage.warning('请填写课程名'); return }
  const res = await mockApi.addTimetableEntry(addForm)
  if (res.code === 200) {
    ElMessage.success('添加成功')
    showAdd.value = false
    Object.assign(addForm, { resourceId: '', dayOfWeek: '', startTime: '', endTime: '', courseName: '', teacher: '' })
    loadData()
  } else {
    ElMessage.error(res.message)
  }
}

async function doImport() {
  if (!importText.value.trim()) { ElMessage.warning('请输入课表数据'); return }
  const res = await mockApi.batchImportTimetable(importText.value)
  if (res.code === 200) {
    importResult.added = res.data.added
    importResult.errors = res.data.errors
    if (res.data.added > 0) ElMessage.success(`成功导入 ${res.data.added} 条`)
    if (res.data.errors.length === 0) showImport.value = false
    loadData()
  }
}

async function removeEntry(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.courseName}」（${weekLabel(row.dayOfWeek)} ${row.startTime}）？`, '提示', { type: 'warning' })
    const res = await mockApi.deleteTimetableEntry(row.id)
    if (res.code === 200) {
      ElMessage.success('已删除')
      loadData()
    }
  } catch {}
}

onMounted(async () => {
  await loadResources()
  loadData()
})
</script>

<style scoped lang="scss">
.timetable-page { max-width: 1200px; margin: 0 auto; }
.filter-card, .table-card { border-radius: 12px; border: none; margin-bottom: 16px; }
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  .filter-actions { margin-left: auto; display: flex; gap: 8px; }
}
.time-text { font-family: 'Consolas', monospace; font-weight: 500; color: #303133; }
.summary { margin-top: 12px; font-size: 13px; color: #909399; text-align: right; }

.import-alert { margin-bottom: 16px; }
.import-alert code {
  background: #f4f4f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.sample-block {
  background: #f9fafc;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sample-title { font-size: 13px; color: #606266; font-weight: 500; }
.sample-code {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #303133;
  font-family: 'Consolas', monospace;
}
.import-errors { margin-top: 12px; }
.import-errors ul { margin: 4px 0 0 16px; font-size: 13px; }
</style>
