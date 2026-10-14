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

    <!-- 筛选栏（图书馆使用座位图自带的楼层切换，无需筛选） -->
    <el-card v-if="!isLibraryType" class="filter-bar" shadow="never">
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
    </el-card>

    <!-- 图书馆：SVG 座位图 -->
    <div v-if="isLibraryType" class="library-view">
      <div class="floor-tabs">
        <button
          v-for="f in libFloors"
          :key="f"
          class="floor-tab"
          :class="{ active: libFloor === f }"
          @click="switchLibFloor(f)"
        >
          {{ f }} 多媒体自习室
        </button>
      </div>
      <LibrarySeatMap v-loading="loading" :floor="libFloor" :resources="librarySeats" @select="onSeatSelect" />
    </div>

    <!-- 按钮模式：教室 / 实验室 -->
    <div v-else-if="isButtonMode" v-loading="loading" class="button-view">
      <div v-for="group in pagedGroups" :key="group.key" class="floor-section">
        <div class="floor-header">
          <span class="floor-title">{{ group.label }}</span>
        </div>
        <div class="room-grid">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="room-btn"
            :class="{ disabled: item.status === 3 }"
            :title="roomTip(item)"
            @click="onRoomClick(item)"
          >
            <span class="room-no">{{ item.roomNo }}</span>
            <span v-if="isLabType" class="room-cap">限{{ item.capacity }}人</span>
            <span v-if="roomStatusText(item)" class="room-status" :class="roomStatusClass(item)">
              {{ roomStatusText(item) }}
            </span>
            <span v-else class="room-dot" :class="isDotRed(item) ? 'off' : 'on'"></span>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && groupedResources.length === 0" description="暂无符合条件的资源" />
    </div>

    <!-- 卡片模式：体育馆 -->
    <div v-else v-loading="loading" class="resource-grid">
      <div
        v-for="item in resources"
        :key="item.id"
        class="resource-card"
        @click="onRoomClick(item)"
      >
        <div class="card-banner" :style="{ backgroundImage: `url(${getVenueImage(item)})` }">
          <div class="banner-overlay"></div>
          <span class="banner-room">{{ item.roomNo }}</span>
          <el-tag :type="Number(route.params.type) === 4 ? venueTagType(item) : statusTagType(item.status)" size="small" effect="dark" class="status-tag">
            {{ venueLabel(item) }}
          </el-tag>
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ item.name }}</h3>
          <div class="card-info">
            <span class="info-item"><el-icon><Location /></el-icon> {{ item.building }} {{ item.floor }}</span>
            <span class="info-item"><el-icon><User /></el-icon> {{ capText(item) }}</span>
          </div>
          <div class="card-time"><el-icon><Clock /></el-icon> {{ item.openTime }} - {{ item.closeTime }}</div>
        </div>
      </div>
    </div>

    <el-empty v-if="!isButtonMode && !isLibraryType && !loading && resources.length === 0" description="暂无符合条件的资源" />

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
    <div class="pagination" v-if="!isButtonMode && !isLibraryType && total > 0">
      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <!-- 不可进入提示：他人预约 / 维修中 -->
    <el-dialog v-model="blockDialogVisible" :title="blockInfo.title" width="420px" align-center>
      <div class="block-body">
        <p class="block-res">{{ blockInfo.resourceName }}</p>
        <template v-if="blockInfo.type === 'occupied'">
          <div class="block-row">
            <span class="block-label">预约时段</span>
            <span class="block-value">{{ blockInfo.timeRange }}</span>
          </div>
          <div class="block-row">
            <span class="block-label">备注信息</span>
            <span class="block-value">{{ blockInfo.remark || '（未填写）' }}</span>
          </div>
        </template>
        <p v-else class="block-tip">该空间正在维修中，暂不可查看与预约。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="blockDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Search, Location, User, Clock, School, Tools, Reading, Trophy } from '@element-plus/icons-vue'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'
import LibrarySeatMap from '@/components/LibrarySeatMap.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

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
    buildings: ['学海楼'],
    floors: ['1F', '2F', '3F', '4F', '5F', '6F']
  },
  3: {
    label: '图书馆', icon: 'Reading', image: '/assets/type-library.png',
    subItems: ['6F 多媒体自习室', '7F 多媒体自习室'],
    buildings: ['图书馆'],
    floors: ['6F', '7F']
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
// 教室（type=1）不参与预约，红点表示"正在上课"
const isClassroomType = computed(() => Number(route.params.type) === 1)
// 实验室（type=2）限 10 人，小组式预约
const isLabType = computed(() => Number(route.params.type) === 2)
// 体育馆（type=4）可多人同时预约，不因他人已约而禁止进入
const isGymType = computed(() => Number(route.params.type) === 4)
// 图书馆（type=3）座位级单人预约，使用 SVG 座位图
const isLibraryType = computed(() => Number(route.params.type) === 3)
// 图书馆楼层与当前楼层座位数据
const libFloors = ['6F', '7F']
const libFloor = ref('6F')
const librarySeats = computed(() => resources.value.filter(r => r.floor === libFloor.value))
const buildingOptions = computed(() => catInfo.value.buildings)
const floorOptions = computed(() => {
  if (!selBuilding.value) return catInfo.value.floors
  if (selBuilding.value === '学海楼') return ['1F', '2F', '3F', '4F', '5F', '6F']
  if (selBuilding.value.includes('教学')) return ['1F', '2F', '3F', '4F', '5F']
  if (selBuilding.value.includes('图书馆')) return ['6F', '7F']
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
      const label = subject ? `${r.floor}（${subject}）` : `${r.floor}`
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
// 配色约定：可预约/可用=绿，已约/使用中=红，维修中=黄
function statusTagType(s) { return { 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info' }

// 房间角标：维修 → 「维修」；使用中 → 「占用」；其余以小圆点表示（可预约绿、已被占用红）
function roomStatusText(item) {
  if (item.status === 3) return '维修'
  if (isClassroomType.value) return ''
  if (item.status === 2) return '占用'
  return ''
}
function roomStatusClass(item) {
  return item.status === 3 ? 'repair' : 'occupied'
}

// 小圆点是否标红：教室看"是否正在上课"，其他资源看"是否已被预约"
function isDotRed(item) {
  if (isClassroomType.value) return !!item.courseBusy
  return !!item.reserved
}

function roomTip(item) {
  if (item.status === 3) return '维修中'
  if (isClassroomType.value) {
    return item.courseBusy ? `正在上课：${item.courseNowName || ''}` : '空闲，可直接使用'
  }
  // 红点语义：当前时刻是否有人正在使用
  return item.reserved ? '当前正被占用' : '当前空闲，可预约'
}

// 体育馆：数量按 容量 - 当前时段已约数量 实时计算，单位取自资源自身（人/台/片）
// 卡片模式状态标签：体育馆显示实时"剩余"信息，图书馆沿用原状态文案
function venueLabel(item) {
  if (Number(route.params.type) !== 4) return statusText(item.status)
  if (item.status === 3) return '维修中'
  const unit = item.unit || '人'
  const cap = item.capacity || 0
  const remain = Math.max(0, cap - (item.occupiedNow || 0))
  return remain > 0 ? `剩余 ${remain} ${unit}` : '已满'
}
function venueTagType(item) {
  if (item.status === 3) return 'warning'
  const cap = item.capacity || 0
  const used = item.occupiedNow || 0
  if (used >= cap) return 'danger'
  return used > 0 ? 'warning' : 'success'
}

// 容量文案：体育馆带自己的计量单位，其余为"人"
function capText(item) {
  if (Number(route.params.type) === 4) return `${item.capacity} ${item.unit || '人'}`
  return `${item.capacity} 人`
}

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
    // 按钮模式 / 图书馆座位图：一次全加载
    const size = (isButtonMode.value || isLibraryType.value) ? 500 : page.size
    const res = await api.getResourcePage({
      current: page.current,
      size,
      type: Number(route.params.type),
      keyword: keyword.value || undefined,
      building: selBuilding.value || undefined,
      floor: isLibraryType.value ? libFloor.value : (selFloor.value || undefined)
    })
    if (res.code === 200) {
      resources.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

// 图书馆楼层切换
function switchLibFloor(f) {
  if (libFloor.value === f) return
  libFloor.value = f
  loadData()
}

// 点击座位：维修中仅提示，其余进入预约详情（与实验室等按时段预约一致）
function onSeatSelect(item) {
  if (item.status === 3) {
    Object.assign(blockInfo, {
      type: 'repair',
      title: '暂不可预约',
      resourceName: item.name,
      timeRange: '',
      remark: ''
    })
    blockDialogVisible.value = true
    return
  }
  goDetail(item.id)
}

function goDetail(id) {
  router.push(`/resource/${id}`)
}

// 非本人预约的资源/维修中的资源：普通用户不可进入，仅提示占用信息（管理员不受限）
const blockDialogVisible = ref(false)
const blockInfo = reactive({ type: '', title: '', resourceName: '', timeRange: '', remark: '' })

function fmt(dt) {
  return dt ? String(dt).replace('T', ' ').slice(0, 16) : ''
}

function onRoomClick(item) {
  // 教室不参与预约，任何用户都可查看课表
  if (userStore.isAdmin || isClassroomType.value) {
    goDetail(item.id)
    return
  }
  if (item.status === 3) {
    Object.assign(blockInfo, {
      type: 'repair',
      title: '暂不可查看',
      resourceName: item.name,
      timeRange: '',
      remark: ''
    })
    blockDialogVisible.value = true
    return
  }
  // 体育馆可多人同时预约；实验室按时段独占（只锁已约时段）
  // 两者都允许进入详情页查看时段条并预约其他时段
  if (isGymType.value || isLabType.value) {
    goDetail(item.id)
    return
  }
  if (item.reserved && !item.reservedByMe) {
    Object.assign(blockInfo, {
      type: 'occupied',
      title: '该空间已被他人预约',
      resourceName: item.name,
      timeRange: `${fmt(item.reservedStart)} ~ ${fmt(item.reservedEnd)}`,
      remark: item.reservedRemark || ''
    })
    blockDialogVisible.value = true
    return
  }
  goDetail(item.id)
}

watch(() => route.params.type, () => {
  keyword.value = ''
  selBuilding.value = ''
  selFloor.value = ''
  libFloor.value = '6F'
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

/* ===== 图书馆座位图 ===== */
.library-view {
  display: flex; flex-direction: column; gap: 14px;
}
.floor-tabs {
  display: flex; gap: 12px;
  .floor-tab {
    padding: 9px 22px;
    font-size: 14px; font-weight: 500;
    color: #606266; background: #fff;
    border: 1px solid #e4e7ed; border-radius: 10px;
    cursor: pointer; transition: all 0.2s;
    &:hover { border-color: #409eff; color: #409eff; }
    &.active {
      color: #fff; border-color: transparent;
      background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
    }
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
    &.off { background: #f56c6c; }
  }
  .room-status {
    position: absolute; top: 5px; right: 5px;
    font-size: 10px; padding: 1px 5px; border-radius: 8px;
    color: #fff; font-weight: 500;
    &.occupied { background: #f56c6c; }
    &.repair { background: #e6a23c; }
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

.block-body {
  .block-res { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 14px; }
  .block-row {
    display: flex;
    gap: 10px;
    margin-bottom: 10px;
    font-size: 13px;
    line-height: 1.7;

    .block-label { flex: 0 0 64px; color: #909399; }
    .block-value { flex: 1; color: #303133; word-break: break-all; }
  }
  .block-tip { font-size: 13px; color: #909399; }
}
</style>
