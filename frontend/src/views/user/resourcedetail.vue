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
              />
            </el-form-item>
            <el-form-item label="开始时间">
              <el-time-select
                v-model="form.startTime"
                :max-time="form.endTime"
                placeholder="选择开始时间"
                start="08:00"
                step="00:30"
                end="22:00"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-time-select
                v-model="form.endTime"
                :min-time="form.startTime"
                placeholder="选择结束时间"
                start="08:30"
                step="00:30"
                end="22:00"
                style="width: 100%"
              />
            </el-form-item>
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
                <li>请在预约开始后 15 分钟内完成打卡，否则将记录违规</li>
                <li>预约时间冲突时系统会自动拒绝</li>
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
  ArrowLeft, OfficeBuilding, Position, User, Clock, Timer, Document, Calendar
} from '@element-plus/icons-vue'
import { mockApi } from '@/api/mock'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const resource = ref(null)

const form = reactive({
  date: '',
  startTime: '',
  endTime: '',
  checkinType: 1,
  remark: ''
})

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

async function submitReservation() {
  if (!form.date) { ElMessage.warning('请选择预约日期'); return }
  if (!form.startTime) { ElMessage.warning('请选择开始时间'); return }
  if (!form.endTime) { ElMessage.warning('请选择结束时间'); return }

  // 计算时长
  const [sh, sm] = form.startTime.split(':').map(Number)
  const [eh, em] = form.endTime.split(':').map(Number)
  const duration = (eh * 60 + em) - (sh * 60 + sm)
  if (duration <= 0) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  if (duration > resource.value.maxDuration) {
    ElMessage.warning(`单次预约时长不能超过 ${resource.value.maxDuration} 分钟`)
    return
  }

  submitting.value = true
  try {
    const res = await mockApi.createReservation({
      resourceId: resource.value.id,
      startTime: `${form.date} ${form.startTime}:00`,
      endTime: `${form.date} ${form.endTime}:00`,
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
      // 重置表单
      form.date = ''
      form.startTime = ''
      form.endTime = ''
      form.remark = ''
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
</style>
