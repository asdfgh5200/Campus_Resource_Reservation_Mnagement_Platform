<template>
  <div class="my-reservations-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="0" />
      <el-tab-pane label="待打卡" name="1" />
      <el-tab-pane label="使用中" name="2" />
      <el-tab-pane label="已完成" name="3" />
      <el-tab-pane label="已取消" name="4" />
    </el-tabs>

    <div v-loading="loading" class="reservation-list">
      <div v-for="item in reservations" :key="item.id" class="reservation-card">
        <div class="card-left" :style="{ backgroundImage: `url(${getTypeImage(item.resource?.resourceType)})` }">
          <div class="card-left-overlay"></div>
        </div>
        <div class="card-content">
          <div class="card-top">
            <h3 class="res-name">{{ item.resource?.name || '未知资源' }}</h3>
            <el-tag :type="statusTagType(item.status)" effect="light" size="small">{{ statusText(item.status) }}</el-tag>
          </div>
          <div class="card-info">
            <span class="info-line"><el-icon><Clock /></el-icon> {{ item.startTime }} → {{ item.endTime }}</span>
            <span class="info-line" v-if="item.checkinFlag"><el-icon><CircleCheck /></el-icon> 已打卡 {{ item.checkinTime }}</span>
          </div>
          <div class="card-footer">
            <span class="remark" v-if="item.remark">备注：{{ item.remark }}</span>
            <div class="actions">
              <el-button v-if="item.status === 1" type="success" size="small" :icon="Location" @click="doCheckin(item)">打卡</el-button>
              <el-button v-if="item.status === 1" type="danger" size="small" plain :icon="Close" @click="doCancel(item)">取消</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && reservations.length === 0" description="暂无预约记录" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Clock, CircleCheck, Location, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mockApi } from '@/api/mock'

const loading = ref(false)
const activeTab = ref('0')
const reservations = ref([])
const page = reactive({ current: 1, size: 20 })

const typeImages = {
  1: '/assets/type-classroom.png',
  2: '/assets/type-lab.png',
  3: '/assets/type-library.png',
  4: '/assets/type-gym.png'
}

function getTypeImage(t) { return typeImages[t] || typeImages[1] }
function statusText(s) { return { 1: '待打卡', 2: '使用中', 3: '已完成', 4: '已取消' }[s] || '未知' }
function statusTagType(s) { return { 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }[s] || 'info' }

function onTabChange() {
  page.current = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await mockApi.getMyReservations({
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

async function doCheckin(item) {
  try {
    await ElMessageBox.confirm('确认到场打卡？', '提示', { type: 'info' })
    const res = await mockApi.checkin(item.id)
    if (res.code === 200) {
      ElMessage.success('打卡成功！')
      loadData()
    } else {
      ElMessage.error(res.message)
    }
  } catch { /* cancelled */ }
}

async function doCancel(item) {
  try {
    await ElMessageBox.confirm('确定取消此预约？频繁取消可能影响信用分', '警告', { type: 'warning' })
    const res = await mockApi.cancelReservation(item.id)
    if (res.code === 200) {
      ElMessage.success('已取消')
      loadData()
    }
  } catch { /* cancelled */ }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.my-reservations-page { max-width: 900px; margin: 0 auto; }

.reservation-list { display: flex; flex-direction: column; gap: 12px; }

.reservation-card {
  display: flex;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover { transform: translateX(4px); box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1); }

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
      }
    }

    .card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .remark { font-size: 13px; color: #a8abb2; }
    }
  }
}
</style>
