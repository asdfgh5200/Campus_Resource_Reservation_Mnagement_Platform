<template>
  <div class="category-detail">
    <!-- 返回 + 标题 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" text @click="$router.push('/home')">返回首页</el-button>
      <h2 class="page-title">
        <el-icon :size="22"><component :is="catInfo.icon" /></el-icon>
        {{ catInfo.label }}
      </h2>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-bar" shadow="never">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="搜索房间号或名称" :prefix-icon="Search" clearable class="search-input" @keyup.enter="onSearch" @clear="onSearch" />
        <el-select v-model="selBuilding" placeholder="选择楼栋" clearable @change="onBuildingChange" class="building-select">
          <el-option v-for="b in buildingOptions" :key="b" :label="b" :value="b" />
        </el-select>
        <el-select v-model="selFloor" placeholder="选择楼层" clearable @change="onFloorChange" class="floor-select">
          <el-option v-for="f in floorOptions" :key="f" :label="f" :value="f" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>
      <div class="info-tags" v-if="catInfo.subItems.length">
        <el-tag v-for="sub in catInfo.subItems" :key="sub" type="info" effect="plain" size="large" class="sub-tag">{{ sub }}</el-tag>
      </div>
    </el-card>

    <!-- 按钮模式：教室 / 实验室 -->
    <div v-if="isButtonMode" v-loading="loading" class="button-view">
      <div v-for="group in pagedGroups" :key="group.key" class="floor-section">
        <div class="floor-header">
          <span class="floor-title">{{ group.label }}</span>
          <span class="floor-count">{{ group.items.length }} 间</span>
        </div>
        <div class="room-grid">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="room-btn"
            :class="{ disabled: item.status === 3 }"
            @click="goDetail(item.id)"
          >
            <span class="room-no">{{ item.roomNo }}</span>
            <span class="room-cap">{{ item.capacity }}人</span>
            <span v-if="item.status !== 1" class="room-status" :class="statusClass(item.status)">
              {{ item.status === 3 ? '维修' : '占用' }}
            </span>
            <span v-else class="room-dot on"></span>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && groupedResources.length === 0" description="暂无符合条件的资源" />
    </div>

    <!-- 卡片模式：图书馆 / 体育馆 -->
    <div v-else v-loading="loading" class="resource-grid">
      <div
        v-for="item in resources"
        :key="item.id"
        class="resource-card"
        @click="goDetail(item.id)"
      >
        <div class="card-banner" :style="{ backgroundImage: `url(${getVenueImage(item)})` }">
          <div class="banner-overlay"></div>
          <span class="banner-room">{{ item.roomNo }}</span>
          <el-tag :type="statusTagType(item.status)" size="small" effect="dark" class="status-tag">
            {{ statusText(item.status) }}
          </el-tag>
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ item.name }}</h3>
          <div class="card-info">
            <span class="info-item"><el-icon><Location /></el-icon> {{ item.building }} {{ item.floor }}</span>
            <span class="info-item"><el-icon><User /></el-icon> {{ item.capacity }} 人</span>
          </div>
          <div class="card-time"><el-icon><Clock /></el-icon> {{ item.openTime }} - {{ item.closeTime }}</div>
        </div>
      </div>
    </div>

    <el-empty v-if="!isButtonMode && !loading && resources.length === 0" description="暂无符合条件的资源" />

    <!-- 分页（按钮模式按楼层分页） -->
    <div class="pagination" v-if="isButtonMode && totalGroups > groupsPerPage">
      <el-pagination
        v-model:current-page="groupPage"
        :page-size="groupsPerPage"
        :total="totalGroups"
        layout="prev, pager, next"
      />
    </div>

    <!-- 分页（卡片模式） -->
    <div class="pagination" v-if="!isButtonMode && total > 0">
      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Search, Location, User, Clock, School, Tools, Reading, Trophy } from '@element-plus/icons-vue'
import { mockApi } from '@/api/mock'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const selBuilding = ref('')
const selFloor = ref('')
const loading = ref(false)
const resources = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 12 })
const groupPage = ref(1)
const groupsPerPage = 5

const catConfig = {
  1: {
    label: '教室', icon: 'School', image: '/assets/type-classroom.png',
    subItems: [],
    buildings: ['教学楼A栋', '教学楼B栋'],
    floors: ['1F', '2F', '3F', '4F', '5F']
  },
  2: {
    label: '实验室', icon: 'Tools', image: '/assets/type-lab.png',
    subItems: ['物理实验室', '化学实验室', '生物实验室', '材料实验室', '计算机实验室', '机电实验室'],
    buildings: ['实验楼'],
    floors: ['1F', '2F', '3F', '4F', '5F', '6F']
  },
  3: {
    label: '图书馆', icon: 'Reading', image: '/assets/type-library.png',
    subItems: ['自习区', '研讨室', '阅览区'],
    buildings: ['图书馆'],
    floors: ['2F', '3F', '4F']
  },
  4: {
    label: '体育馆', icon: 'Trophy', image: '/assets/type-gym.png',
    subItems: ['游泳馆', '乒乓球馆', '篮球场', '羽毛球场', '网球场', '排球场'],
    buildings: ['体育馆A栋', '体育馆B栋'],
    floors: ['1F', '2F']
  }
}

const catInfo = computed(() => catConfig[Number(route.params.type)] || catConfig[1])
const isButtonMode = computed(() => [1, 2].includes(Number(route.params.type)))
const buildingOptions = computed(() => catInfo.value.buildings)
const floorOptions = computed(() => {
  if (!selBuilding.value) return catInfo.value.floors
  if (selBuilding.value === '实验楼') return ['1F', '2F', '3F', '4F', '5F', '6F']
  if (selBuilding.value.includes('教学')) return ['1F', '2F', '3F', '4F', '5F']
  if (selBuilding.value.includes('图书馆')) return ['2F', '3F', '4F']
  if (selBuilding.value.includes('体育馆')) return ['1F', '2F']
  return catInfo.value.floors
})

// 楼层对应的学科（仅实验室）
const floorToSubject = {
  '1F': '物理实验室', '2F': '化学实验室', '3F': '生物实验室',
  '4F': '材料实验室', '5F': '计算机实验室', '6F': '机电实验室'
}

// 按楼栋+楼层分组（扁平）
const groupedResources = computed(() => {
  const groups = []
  const map = new Map()
  for (const r of resources.value) {
    const key = `${r.building}|${r.floor}`
    if (!map.has(key)) {
      const subject = Number(route.params.type) === 2 ? floorToSubject[r.floor] : null
      const label = subject ? `${r.building} · ${r.floor}（${subject}）` : `${r.building} · ${r.floor}`
      map.set(key, { key, label, items: [] })
      groups.push(map.get(key))
    }
    map.get(key).items.push(r)
  }
  return groups
})

const totalGroups = computed(() => groupedResources.value.length)
const pagedGroups = computed(() => {
  const start = (groupPage.value - 1) * groupsPerPage
  return groupedResources.value.slice(start, start + groupsPerPage)
})

// 体育馆各场馆对应图片
const gymImageMap = {
  '游泳馆': '/assets/gym-swimming.png',
  '乒乓球馆': '/assets/gym-tabletennis.png',
  '篮球场': '/assets/gym-basketball.png',
  '羽毛球场': '/assets/gym-badminton.png',
  '网球场': '/assets/gym-tennis.png',
  '排球场': '/assets/gym-volleyball.png'
}

function getVenueImage(item) {
  if (Number(route.params.type) === 4) {
    return gymImageMap[item.name] || catInfo.value.image
  }
  return catInfo.value.image
}

function statusText(s) { return { 1: '可预约', 2: '使用中', 3: '维修中' }[s] || '未知' }
function statusTagType(s) { return { 1: 'success', 2: 'warning', 3: 'danger' }[s] || 'info' }
function statusClass(s) { return { 1: 'on', 2: 'busy', 3: 'off' }[s] || 'on' }

function onSearch() {
  page.current = 1
  groupPage.value = 1
  loadData()
}

function onBuildingChange() {
  selFloor.value = ''
  page.current = 1
  groupPage.value = 1
  loadData()
}

function onFloorChange() {
  page.current = 1
  groupPage.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    // 按钮模式一次全加载
    const size = isButtonMode.value ? 500 : page.size
    const res = await mockApi.getResourcePage({
      current: page.current,
      size,
      type: Number(route.params.type),
      keyword: keyword.value || undefined,
      building: selBuilding.value || undefined,
      floor: selFloor.value || undefined
    })
    if (res.code === 200) {
      resources.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/resource/${id}`)
}

watch(() => route.params.type, () => {
  keyword.value = ''
  selBuilding.value = ''
  selFloor.value = ''
  page.current = 1
  groupPage.value = 1
  loadData()
})

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.category-detail { max-width: 1200px; margin: 0 auto; }

.page-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
  .page-title { display: flex; align-items: center; gap: 6px; font-size: 20px; font-weight: 600; color: #303133; }
}

.filter-bar {
  margin-bottom: 20px; border-radius: 12px; border: none;
  .filter-row { display: flex; gap: 12px; margin-bottom: 12px;
    .search-input { flex: 1; }
    .building-select { width: 180px; }
    .floor-select { width: 140px; }
  }
  .info-tags { display: flex; flex-wrap: wrap; gap: 8px;
    .sub-tag { border-radius: 8px; }
  }
}

/* ===== 按钮模式 ===== */
.button-view {
  display: flex; flex-direction: column; gap: 18px;
}

.floor-section {
  .floor-header {
    display: flex; align-items: center; gap: 8px;
    margin-bottom: 10px;
    padding-bottom: 6px;
    border-bottom: 1px solid #ebeef5;
    .floor-title { font-size: 14px; font-weight: 600; color: #303133; }
    .floor-count { font-size: 12px; color: #c0c4cc; margin-left: auto; }
  }
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 8px;
}

.room-btn {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  padding: 10px 6px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;

  &:hover {
    border-color: #409eff;
    background: #ecf5ff;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  }
  &.disabled { opacity: 0.45; cursor: not-allowed; }

  .room-no { font-size: 14px; font-weight: 600; color: #303133; }
  .room-cap { font-size: 11px; color: #909399; }
  .room-dot {
    position: absolute; top: 7px; right: 7px;
    width: 8px; height: 8px; border-radius: 50%;
    &.on { background: #67c23a; }
  }
  .room-status {
    position: absolute; top: 5px; right: 5px;
    font-size: 10px; padding: 1px 5px; border-radius: 8px;
    color: #fff; font-weight: 500;
    &.busy { background: #e6a23c; }
    &.off { background: #f56c6c; }
  }
}

/* ===== 卡片模式 ===== */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px; min-height: 200px;
}

.resource-card {
  background: #fff; border-radius: 14px; overflow: hidden;
  cursor: pointer; transition: transform 0.3s, box-shadow 0.3s;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  &:hover { transform: translateY(-6px); box-shadow: 0 12px 32px rgba(0,0,0,0.12); }

  .card-banner { height: 120px; background-size: cover; background-position: center; position: relative;
    .banner-overlay { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0) 30%, rgba(0,0,0,0.5) 100%); }
    .banner-room { position: absolute; bottom: 10px; left: 16px; color: #fff; font-size: 22px; font-weight: 700; text-shadow: 0 2px 8px rgba(0,0,0,0.5); }
    .status-tag { position: absolute; top: 10px; right: 10px; }
  }
  .card-body { padding: 14px 18px 18px;
    .card-title { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 8px; }
    .card-info { display: flex; gap: 16px; margin-bottom: 6px;
      .info-item { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #909399; }
    }
    .card-time { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #909399; }
  }
}

.pagination { display: flex; justify-content: center; margin-top: 24px; }
</style>
