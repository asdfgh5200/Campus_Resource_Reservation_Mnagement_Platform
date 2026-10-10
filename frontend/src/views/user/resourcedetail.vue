<template>
  <div class="detail-page" v-loading="loading">
    <div class="back-btn">
      <el-button :icon="ArrowLeft" text @click="$router.back()">返回资源列表</el-button>
    </div>

    <div v-if="resource" class="detail-content">
      <!-- 资源信息卡片 -->
      <el-card class="info-card" shadow="never">
        <div class="info-header" :style="{ backgroundImage: `url(${headerImage})` }">
          <div class="info-overlay"></div>
          <div class="info-text">
            <h1>{{ resource.name }}</h1>
            <el-tag :type="statusTagType(resource.status)" effect="dark">{{ statusText(resource.status) }}</el-tag>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label"><el-icon><OfficeBuilding /></el-icon> 楼宇</span>
            <span class="value">{{ resource.building }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><Position /></el-icon> 位置</span>
            <span class="value">{{ resource.floor }} / {{ resource.roomNo }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><User /></el-icon> 容纳</span>
            <span class="value">{{ resource.capacity }} 人</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><Clock /></el-icon> 开放</span>
            <span class="value">{{ resource.openTime }} - {{ resource.closeTime }}</span>
          </div>
          <div class="info-item">
            <span class="label"><el-icon><Timer /></el-icon> 时长上限</span>
            <span class="value">{{ resource.maxDuration }} 分钟</span>
          </div>
        </div>
        <div class="info-desc">
          <el-icon><Document /></el-icon>
          <span>{{ resource.description }}</span>
        </div>
      </el-card>

      <!-- 预约操作区 -->
      <el-card class="reserve-card" shadow="never">
        <template #header>
          <div class="card-header-title">
            <el-icon><Calendar /></el-icon>
            <span>预约时段</span>
          </div>
        </template>

        <div v-if="resource.status !== 1" class="reserve-disabled">
          <el-result icon="warning" title="该资源当前不可预约" sub-title="资源处于「维修中」或「使用中」状态，请稍后再来">
          </el-result>
        </div>

        <div v-else class="reserve-form">
          <el-form :model="form" label-width="100px" size="default">
            <el-form-item label="预约日期">
              <el-date-picker
                v-model="form.date"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                style="width: 100%"
                @change="loadAvailability"
              />
            </el-form-item>
          </el-form>

          <!-- 时段网格 -->
          <div v-if="form.date" class="slot-section">
            <div class="slot-legend">
              <span class="legend-item"><i class="dot available"></i> 可预约</span>
              <span class="legend-item"><i class="dot occupied"></i> 已占用</span>
              <span class="legend-item"><i class="dot selected"></i> 已选择</span>
            </div>

            <div v-if="loadingSlots" class="slot-loading">
              <el-icon class="is-loading"><Loading /></el-icon> 加载时段中...
            </div>

            <div v-else-if="slots.length" class="slot-grid">
              <div
                v-for="slot in slots"
                :key="slot.time"
                class="slot-cell"
                :class="{
                  'is-available': slot.available && !isSlotSelected(slot),
                  'is-occupied': !slot.available,
                  'is-selected': isSlotSelected(slot)
                }"
                @click="onSlotClick(slot)"
              >
                <div class="slot-time">{{ slot.time }}</div>
                <div v-if="!slot.available" class="slot-reason" :title="slot.reason">{{ slot.reason }}</div>
                <div v-else-if="isSlotSelected(slot)" class="slot-check">
                  <el-icon><Check /></el-icon>
                </div>
              </div>
            </div>

            <div v-else class="slot-empty">该日无可预约时段</div>

            <!-- 已选时段信息 -->
            <div v-if="selectedRange.start && selectedRange.end" class="selected-info">
              <el-alert type="success" :closable="false" show-icon>
                <template #title>
                  已选时段：{{ selectedRange.start.time }} - {{ addMinutes(selectedRange.end.time, 30) }}
                  （{{ selectedDuration }} 分钟）
                </template>
              </el-alert>
            </div>
          </div>

          <el-form :model="form" label-width="100px" size="default" style="margin-top: 16px;">
            <el-form-item label="打卡方式">
              <el-radio-group v-model="form.checkinType">
                <el-radio :value="1">定位打卡</el-radio>
                <el-radio :value="2">验证码打卡</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，填写用途说明" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="submitReservation">
                确认预约
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 预约提示 -->
          <div class="reserve-tips">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>预约须知</template>
              <ul class="tips-list">
                <li>单次预约时长上限 {{ resource.maxDuration }} 分钟</li>
                <li>时段按 30 分钟粒度切分，点击起止时段即可选择连续区间</li>
                <li>灰色时段为全校课表占用或已被他人预约，不可选择</li>
                <li>请在预约开始后 15 分钟内完成打卡，否则将记录违规</li>
              </ul>
            </el-alert>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, OfficeBuilding, Position, User, Clock, Timer, Document, Calendar, Loading, Check
} from '@element-plus/icons-vue'
import { mockApi } from '@/api/mock'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const resource = ref(null)

const form = reactive({
  date: '',
  checkinType: 1,
  remark: ''
})

// 时段网格相关
const slots = ref([])
const loadingSlots = ref(false)
const selectedRange = reactive({ start: null, end: null })

const typeImages = {
  1: '/assets/type-classroom.png',
  2: '/assets/type-lab.png',
  3: '/assets/type-library.png',
  4: '/assets/type-gym.png'
}

const headerImage = computed(() => typeImages[resource.value?.resourceType] || typeImages[1])

function statusText(s) { return { 1: '可预约', 2: '使用中', 3: '维修中' }[s] || '未知' }
function statusTagType(s) { return { 1: 'success', 2: 'warning', 3: 'danger' }[s] || 'info' }

function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

async function loadData() {
  loading.value = true
  try {
    const res = await mockApi.getResource(route.params.id)
    if (res.code === 200) {
      resource.value = res.data
    } else {
      ElMessage.error(res.message)
      router.back()
    }
  } finally {
    loading.value = false
  }
}

// 加载某日可用时段
async function loadAvailability() {
  if (!form.date || !resource.value) return
  selectedRange.start = null
  selectedRange.end = null
  loadingSlots.value = true
  try {
    const res = await mockApi.getAvailability(resource.value.id, form.date)
    if (res.code === 200) {
      slots.value = res.data.slots
    }
  } finally {
    loadingSlots.value = false
  }
}

// 已选时长
const selectedDuration = computed(() => {
  if (!selectedRange.start || !selectedRange.end) return 0
  return selectedRange.end.endMin - selectedRange.start.startMin
})

// 判断某时段是否在已选区间内
function isSlotSelected(slot) {
  if (!selectedRange.start || !selectedRange.end) return false
  return slot.startMin >= selectedRange.start.startMin && slot.endMin <= selectedRange.end.endMin
}

// 点击时段：选起点或终点
function onSlotClick(slot) {
  if (!slot.available) return
  if (!selectedRange.start) {
    selectedRange.start = slot
    selectedRange.end = slot
    return
  }
  // 第二次点击：若点击已选起点则取消
  if (selectedRange.start && selectedRange.start.time === slot.time) {
    selectedRange.start = null
    selectedRange.end = null
    return
  }
  // 确保区间内全是可用时段
  const lo = slot.startMin < selectedRange.start.startMin ? slot : selectedRange.start
  const hi = slot.startMin < selectedRange.start.startMin ? selectedRange.start : slot
  const between = slots.value.filter(s => s.startMin >= lo.startMin && s.endMin <= hi.endMin)
  const allAvail = between.every(s => s.available)
  if (!allAvail) {
    ElMessage.warning('所选区间包含已占用时段，请重新选择')
    selectedRange.start = slot
    selectedRange.end = slot
    return
  }
  selectedRange.start = lo
  selectedRange.end = hi

  // 校验时长上限
  const dur = selectedRange.end.endMin - selectedRange.start.startMin
  if (dur > resource.value.maxDuration) {
    ElMessage.warning(`单次预约时长不能超过 ${resource.value.maxDuration} 分钟，已自动截断`)
    // 截断：保留起点，向后取 maxDuration 对应的可用片数
    const maxSlots = Math.floor(resource.value.maxDuration / 30)
    const availFromStart = slots.value.filter(s => s.startMin >= selectedRange.start.startMin && s.available)
    const valid = availFromStart.slice(0, maxSlots)
    selectedRange.end = valid[valid.length - 1]
  }
}

// 时间字符串加分钟
function addMinutes(timeStr, mins) {
  const [h, m] = timeStr.split(':').map(Number)
  const total = h * 60 + m + mins
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

async function submitReservation() {
  if (!form.date) { ElMessage.warning('请选择预约日期'); return }
  if (!selectedRange.start || !selectedRange.end) {
    ElMessage.warning('请在时段网格中选择预约时段')
    return
  }
  const dur = selectedRange.end.endMin - selectedRange.start.startMin
  if (dur <= 0) { ElMessage.warning('请选择有效的预约时段'); return }
  if (dur > resource.value.maxDuration) {
    ElMessage.warning(`单次预约时长不能超过 ${resource.value.maxDuration} 分钟`)
    return
  }

  const startTime = `${form.date} ${selectedRange.start.time}:00`
  const endTime = `${form.date} ${addMinutes(selectedRange.end.time, 30)}:00`

  submitting.value = true
  try {
    const res = await mockApi.createReservation({
      resourceId: resource.value.id,
      startTime,
      endTime,
      checkinType: form.checkinType,
      remark: form.remark
    })
    if (res.code === 200) {
      ElMessageBox.confirm('预约成功！是否查看我的预约？', '提示', {
        confirmButtonText: '查看预约',
        cancelButtonText: '继续浏览',
        type: 'success'
      }).then(() => {
        router.push('/my-reservations')
      }).catch(() => {})
      // 重置
      form.date = ''
      form.remark = ''
      slots.value = []
      selectedRange.start = null
      selectedRange.end = null
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.detail-page { max-width: 900px; margin: 0 auto; }

.back-btn { margin-bottom: 12px; }

.info-card, .reserve-card {
  border-radius: 14px;
  border: none;
  margin-bottom: 20px;
}

.info-header {
  height: 180px;
  border-radius: 12px;
  background-size: cover;
  background-position: center;
  position: relative;
  display: flex;
  align-items: flex-end;
  margin-bottom: 20px;

  .info-overlay {
    position: absolute;
    inset: 0;
    border-radius: 12px;
    background: linear-gradient(180deg, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.65) 100%);
  }
  .info-text {
    position: relative;
    z-index: 1;
    padding: 24px 28px;
    width: 100%;

    h1 {
      color: #fff;
      font-size: 26px;
      margin-bottom: 10px;
      text-shadow: 0 2px 8px rgba(0,0,0,0.5);
    }
  }
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 20px;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px 16px;
    background: #f9fafc;
    border-radius: 8px;

    .label {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #909399;
    }
    .value { font-size: 15px; font-weight: 500; color: #303133; }
  }
}

.info-desc {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #f4f4f5;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
}

.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4); }
}

.reserve-tips { margin-top: 8px; }
.tips-list { margin: 4px 0 0 16px; font-size: 13px; line-height: 1.8; }

/* 时段网格 */
.slot-section { margin-top: 4px; }

.slot-legend {
  display: flex;
  gap: 20px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #606266;
}
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-item .dot {
  width: 12px; height: 12px; border-radius: 3px; display: inline-block;
}
.legend-item .dot.available { background: #67c23a; }
.legend-item .dot.occupied { background: #c0c4cc; }
.legend-item .dot.selected { background: #667eea; }

.slot-loading { padding: 30px; text-align: center; color: #909399; }
.slot-empty { padding: 30px; text-align: center; color: #c0c4cc; font-size: 14px; }

.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 10px;
}

.slot-cell {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 10px 6px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
  min-height: 52px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.slot-cell .slot-time {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.slot-cell .slot-reason {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.slot-cell .slot-check { color: #fff; font-size: 18px; }

.slot-cell.is-available {
  background: #f0f9eb;
  border-color: #e1f3d8;
}
.slot-cell.is-available:hover {
  border-color: #67c23a;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.25);
}

.slot-cell.is-occupied {
  background: #f4f4f5;
  border-color: #e9e9eb;
  cursor: not-allowed;
}
.slot-cell.is-occupied .slot-time { color: #909399; }

.slot-cell.is-selected {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-color: #667eea;
}
.slot-cell.is-selected .slot-time { color: #fff; }
.slot-cell.is-selected:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }

.selected-info { margin-top: 14px; }
</style>
