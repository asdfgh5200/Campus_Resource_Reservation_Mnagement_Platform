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

    <!-- 筛选栏（实验室/体育馆等网格视图用；图书馆用座位图、教室用查询表单，均无需此栏） -->
    <el-card v-if="!isLibraryType && !isClassroomType" class="filter-bar" shadow="never">
      <!-- 关键词搜索单独成行，置于所有筛选条件之上 -->
      <div class="filter-row search-row">
        <el-input
          v-model="keyword"
          placeholder="搜索房间号或名称"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      </div>
      <div class="filter-row">
        <el-select
          v-model="selCampus"
          placeholder="选择校区"
          clearable
          @change="onCampusChange"
          class="campus-select"
        >
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select
          v-model="selBuilding"
          :placeholder="isGymType ? '选择运动场' : '选择楼栋'"
          clearable
          @change="onBuildingChange"
          class="building-select"
        >
          <el-option
            v-for="b in buildingOptions"
            :key="b"
            :label="isGymType ? venueLabelOf(b) : b"
            :value="b"
          />
        </el-select>
        <el-select
          v-if="filterRoomTypes.length"
          v-model="selRoomType"
          :placeholder="`选择${roomTypeLabel}`"
          clearable
          @change="onRoomTypeChange"
          class="roomtype-select"
        >
          <el-option v-for="t in filterRoomTypes" :key="t" :label="t" :value="t" />
        </el-select>
        <!-- 体育馆按「校区 → 运动场 → 运动类型」检索，楼层无区分度 -->
        <el-select
          v-if="!isGymType"
          v-model="selFloor"
          placeholder="选择楼层"
          clearable
          @change="onFloorChange"
          class="floor-select"
        >
          <el-option v-for="f in floorOptions" :key="f" :label="f" :value="f" />
        </el-select>
      </div>
    </el-card>

    <!-- 教室：校区 → 教学楼 → 教室类型 → 日期 → 时段，只列出该时段真正可选的教室 -->
    <template v-if="isClassroomType">
      <el-card class="room-query" shadow="never">
        <!-- 关键词搜索单独成行，置于地点/时间条件之上 -->
        <div class="query-row search-row">
          <el-input
            v-model="roomKeyword"
            placeholder="搜索教室号或名称"
            :prefix-icon="Search"
            clearable
            class="keyword-input"
            @keyup.enter="searchFreeRooms"
            @clear="searchFreeRooms"
          />
          <el-button type="primary" :icon="Search" :loading="querying" @click="searchFreeRooms">
            查询空闲教室
          </el-button>
        </div>
        <div class="query-row">
          <span class="query-label">地点</span>
          <el-select
            v-model="roomQuery.campus"
            clearable
            placeholder="校区"
            class="campus-select"
            @change="onRoomCampusChange"
          >
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select
            v-model="roomQuery.buildings"
            multiple
            filterable
            clearable
            placeholder="教学楼（可多选、可搜索，默认全部）"
            class="building-multi"
          >
            <el-option v-for="b in classroomBuildings" :key="b" :label="b" :value="b" />
          </el-select>
          <!-- 教务系统未给出教室类型时不展示该维度 -->
          <el-select
            v-if="classroomRoomTypes.length"
            v-model="roomQuery.roomTypes"
            multiple
            filterable
            clearable
            :placeholder="`${roomTypeLabel}（默认全部）`"
            class="type-multi"
          >
            <el-option v-for="t in classroomRoomTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div class="query-row">
          <span class="query-label">时间</span>
          <el-date-picker
            v-model="roomQuery.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :clearable="false"
            :disabled-date="disabledQueryDate"
            class="date-picker"
          />
          <el-time-select
            v-model="roomQuery.start"
            start="08:00"
            step="00:30"
            end="21:30"
            placeholder="开始"
            class="time-select"
          />
          <span class="range-dash">至</span>
          <el-time-select
            v-model="roomQuery.end"
            :start="roomQuery.start || '08:30'"
            step="00:30"
            end="22:00"
            placeholder="结束"
            class="time-select"
          />
        </div>
        <div class="quick-row">
          <span class="quick-label">快捷时段</span>
          <button
            v-for="q in quickSpans"
            :key="q.label"
            class="quick-chip"
            :class="{ active: roomQuery.start === q.start && roomQuery.end === q.end }"
            @click="applyQuickSpan(q)"
          >
            {{ q.label }}
          </button>
        </div>
      </el-card>

      <div v-loading="querying" class="free-result">
        <template v-if="freeSearched">
          <div class="result-head">
            <span class="result-date">{{ roomQuery.date }}</span>
            {{ roomQuery.start }}–{{ roomQuery.end }} <b>{{ freeRooms.length }}</b> 间教室可选
          </div>
          <div v-if="freeRooms.length" class="free-grid">
            <div v-for="r in freeRooms" :key="r.id" class="free-card" @click="goDetail(r.id)">
              <div class="free-room-no">{{ r.roomNo }}</div>
              <div class="free-where">
                {{ [r.campus, r.building, r.floor].filter(Boolean).join(' · ') }}
              </div>
              <div v-if="r.roomType" class="free-type">{{ r.roomType }}</div>
              <div class="free-cap">容纳 {{ r.capacity }} 人</div>
              <div class="free-until">可坐到 {{ r.freeUntil }}</div>
            </div>
          </div>
          <el-empty
            v-else
            description="这个时段没有可选教室，换个时间或楼栋试试"
            :image-size="96"
          />
        </template>
        <el-empty
          v-else-if="!querying"
          description="选好地点与时段，点「查询空闲教室」"
          :image-size="96"
        />
      </div>
    </template>

    <!-- 图书馆：SVG 座位图 -->
    <div v-else-if="isLibraryType" class="library-view">
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
      <LibrarySeatMap
        v-loading="loading"
        :floor="libFloor"
        :resources="librarySeats"
        @select="onSeatSelect"
      />
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
            :class="{ disabled: isUnavailable(item.status) }"
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
          <el-tag
            :type="
              Number(route.params.type) === 4 ? venueTagType(item) : statusTagType(item.status)
            "
            size="small"
            effect="dark"
            class="status-tag"
          >
            {{ venueLabel(item) }}
          </el-tag>
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ item.name }}</h3>
          <div v-if="item.roomType" class="card-type">{{ item.roomType }}</div>
          <div class="card-info">
            <span class="info-item"
              ><el-icon><Location /></el-icon>
              {{ [item.campus, item.building, item.floor].filter(Boolean).join(' ') }}</span
            >
            <span class="info-item"
              ><el-icon><User /></el-icon> {{ capText(item) }}</span
            >
          </div>
          <div class="card-time">
            <el-icon><Clock /></el-icon> {{ item.openTime }} - {{ item.closeTime }}
          </div>
        </div>
      </div>
    </div>

    <el-empty
      v-if="!isButtonMode && !isLibraryType && !loading && resources.length === 0"
      description="暂无符合条件的资源"
    />

    <!-- 分页（按钮模式按楼层分页；教室页只有空闲查询结果，不参与分页） -->
    <div class="pagination" v-if="isButtonMode && !isClassroomType && totalGroups > groupsPerPage">
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

    <!-- 不可进入提示：他人预约 / 维修中、报修中 -->
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
        <p v-else class="block-tip">该空间正在维修或报修中，暂不可查看与预约。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="blockDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 资源详情抽屉：点击卡片/座位不跳页，筛选条件保持原样 -->
    <el-drawer
      v-model="detailVisible"
      :size="detailDrawerSize"
      :destroy-on-close="true"
      class="detail-drawer"
      @closed="detailId = null"
    >
      <template #header>
        <div class="drawer-header">
          <span class="drawer-title">资源详情</span>
          <span class="drawer-hint">关闭后筛选条件保持不变</span>
        </div>
      </template>
      <ResourceDetailPanel v-if="detailId" :resource-id="detailId" @close="detailVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowLeft,
  Search,
  Location,
  User,
  Clock,
  School,
  Tools,
  Reading,
  Trophy
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useUserStore } from '@/stores/user'
import LibrarySeatMap from '@/components/LibrarySeatMap.vue'
import ResourceDetailPanel from '@/components/ResourceDetailPanel.vue'
import { saveLastCategory } from '@/utils/lastCategory'
import { resourceImage } from '@/utils/resourceImage'

const route = useRoute()
const userStore = useUserStore()

const keyword = ref('')
// 教室空闲查询的关键词（教室号或名称）
const roomKeyword = ref('')
const selCampus = ref('')
const selBuilding = ref('')
const selFloor = ref('')
// 筛选栏的教室类型（空表示不限）
const selRoomType = ref('')
const loading = ref(false)
const resources = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 12 })
const groupPage = ref(1)
const groupsPerPage = 5

const catConfig = {
  1: {
    label: '教室',
    icon: 'School',
    image: '/assets/type-classroom.png',
    subItems: [],
    // 楼栋与楼层由 /resources/buildings 按校区动态下发，此处仅作接口返回前的兜底
    buildings: [],
    floors: []
  },
  2: {
    label: '实验室',
    icon: 'Tools',
    image: '/assets/type-lab.png',
    subItems: [
      '物理实验室',
      '化学实验室',
      '生物实验室',
      '材料实验室',
      '计算机实验室',
      '机电实验室'
    ],
    buildings: [],
    floors: []
  },
  3: {
    label: '图书馆',
    icon: 'Reading',
    image: '/assets/type-library.png',
    subItems: ['6F 多媒体自习室', '7F 多媒体自习室'],
    buildings: ['图书馆'],
    floors: ['6F', '7F']
  },
  4: {
    label: '体育馆',
    icon: 'Trophy',
    image: '/assets/type-gym.png',
    subItems: ['游泳馆', '乒乓球馆', '篮球场', '羽毛球场', '网球场', '排球场'],
    buildings: [],
    floors: []
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

// ---------- 校区 / 楼栋 / 楼层：由后端聚合真实资源后下发，避免前端写死 ----------
const CAMPUS_ORDER = ['南湖校区', '马房山校区', '余家头校区', '军山校区']
// [{ campus, building, total, floors: '1F,2F' }]
const buildingTree = ref([])
let buildingTreeType = null

const campusOptions = computed(() => {
  const list = [...new Set(buildingTree.value.map((x) => x.campus).filter(Boolean))]
  return [
    ...CAMPUS_ORDER.filter((c) => list.includes(c)),
    ...list.filter((c) => !CAMPUS_ORDER.includes(c))
  ]
})

// 按校区过滤楼栋；未选校区则返回全部，并按「校区顺序 + 楼栋中文拼音」排序
function buildingsOf(campus) {
  return buildingTree.value
    .filter((x) => !campus || x.campus === campus)
    .slice()
    .sort((a, b) => {
      const ia = CAMPUS_ORDER.indexOf(a.campus)
      const ib = CAMPUS_ORDER.indexOf(b.campus)
      const ca = ia < 0 ? CAMPUS_ORDER.length : ia
      const cb = ib < 0 ? CAMPUS_ORDER.length : ib
      if (ca !== cb) return ca - cb
      return String(a.building || '').localeCompare(String(b.building || ''), 'zh')
    })
    .map((x) => x.building)
}

// 楼层排序：数字楼层先后，非数字（如「—」）置后
function floorSortKey(f) {
  const n = parseInt(f, 10)
  return Number.isNaN(n) ? 999 : n
}

// 细分类型展示顺序：教室按教务系统常见度、实验室按学科方向、体育馆按场地数量排序
const CLASSROOM_TYPE_ORDER = [
  '多媒体教室',
  '普通教室',
  '学院专用',
  '机房',
  '制图教室',
  '自习教室',
  '其他场地'
]
const LAB_TYPE_ORDER = ['化学', '化学生物', '工程实训', '交通运输', '管理', '通用实验']
const SPORT_TYPE_ORDER = [
  '篮球',
  '田径',
  '网球',
  '排球',
  '乒乓球',
  '羽毛球',
  '游泳',
  '足球',
  '体能训练',
  '舞蹈',
  '瑜伽',
  '器材',
  '跆拳道',
  '柔力球',
  '健美操',
  '体质测试',
  '气排球',
  '散打',
  '综合场地'
]

// 体育馆：教务系统里部分场馆只到区域级（南湖/北院/余区…），展示时补「运动区」后缀以区别于校区
const GYM_AREA_ALIAS = ['南湖', '北院', '余区', '西院', '东院', '军山']
function venueLabelOf(b) {
  return GYM_AREA_ALIAS.includes(b) ? `${b}运动区` : b
}

// 当前页面的细分类型名称：教室类型 / 实验方向 / 运动类型
const roomTypeLabel = computed(() => {
  const t = Number(route.params.type)
  if (t === 2) return '实验方向'
  if (t === 4) return '运动类型'
  return '教室类型'
})
const roomTypeOrder = computed(() => {
  const t = Number(route.params.type)
  if (t === 2) return LAB_TYPE_ORDER
  if (t === 4) return SPORT_TYPE_ORDER
  return CLASSROOM_TYPE_ORDER
})

/** 汇总选定校区/楼栋下真实存在的细分类型（后端 GROUP_CONCAT 以逗号分隔） */
function roomTypesOf(campus, buildings) {
  const set = new Set()
  for (const b of buildingTree.value) {
    if (campus && b.campus !== campus) continue
    if (buildings && buildings.length && !buildings.includes(b.building)) continue
    // 兼容 selectMaps 返回的 key 大小写差异
    const raw = b.roomTypes || b.roomtypes || b.room_types || b.ROOMTYPES
    String(raw || '')
      .split(',')
      .filter(Boolean)
      .forEach((t) => set.add(t))
  }
  const order = roomTypeOrder.value
  return [...set].sort((a, b) => {
    const ia = order.indexOf(a)
    const ib = order.indexOf(b)
    return (ia < 0 ? order.length : ia) - (ib < 0 ? order.length : ib)
  })
}

// 同一类型下只拉一次楼栋聚合数据
async function ensureBuildingTree() {
  const type = Number(route.params.type)
  if (buildingTreeType === type) return
  const res = await api.getBuildings(type)
  buildingTree.value = res.code === 200 ? res.data || [] : []
  buildingTreeType = type
}

// ---------- 教室：按 校区 → 教学楼 → 教室类型 → 日期 → 时段 查询空闲教室 ----------
const roomQuery = reactive({
  date: todayStr(),
  start: '',
  end: '',
  campus: '',
  buildings: [],
  roomTypes: []
})
const quickSpans = [
  { label: '上午 08:00–12:00', start: '08:00', end: '12:00' },
  { label: '下午 14:00–18:00', start: '14:00', end: '18:00' },
  { label: '晚上 18:00–22:00', start: '18:00', end: '22:00' }
]
const freeRooms = ref([])
const freeSearched = ref(false)
const querying = ref(false)
const classroomBuildings = computed(() => buildingsOf(roomQuery.campus))
const classroomRoomTypes = computed(() => roomTypesOf(roomQuery.campus, roomQuery.buildings))

// 校区变化：楼栋与类型选项都需重置，避免选到不属于该校区的组合
function onRoomCampusChange() {
  roomQuery.buildings = []
  roomQuery.roomTypes = []
}

function todayStr() {
  const t = new Date()
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
}

// 仅允许查询今天及以后（历史日期无查询意义）
function disabledQueryDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function applyQuickSpan(q) {
  roomQuery.start = q.start
  roomQuery.end = q.end
}

async function searchFreeRooms() {
  if (!roomQuery.date) {
    ElMessage.warning('请选择查询日期')
    return
  }
  // 只搜教室号/名称时未选时段，默认按上午时段查，避免多一步必填
  if (!roomQuery.start || !roomQuery.end) {
    roomQuery.start = quickSpans[0].start
    roomQuery.end = quickSpans[0].end
  }
  if (roomQuery.end <= roomQuery.start) {
    ElMessage.warning('结束时间需晚于开始时间')
    return
  }
  querying.value = true
  try {
    const res = await api.getFreeRooms({
      date: roomQuery.date,
      start: roomQuery.start,
      end: roomQuery.end,
      campus: roomQuery.campus || undefined,
      buildings: roomQuery.buildings,
      roomTypes: roomQuery.roomTypes,
      keyword: roomKeyword.value.trim() || undefined
    })
    if (res.code === 200) {
      freeRooms.value = res.data || []
      freeSearched.value = true
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    querying.value = false
  }
}

// 图书馆楼层与当前楼层座位数据
const libFloors = ['6F', '7F']
const libFloor = ref('6F')
const librarySeats = computed(() => resources.value.filter((r) => r.floor === libFloor.value))
const buildingOptions = computed(() => buildingsOf(selCampus.value))
const floorOptions = computed(() => {
  if (isLibraryType.value) return libFloors
  const set = new Set()
  for (const b of buildingTree.value) {
    if (selCampus.value && b.campus !== selCampus.value) continue
    if (selBuilding.value && b.building !== selBuilding.value) continue
    String(b.floors || '')
      .split(',')
      .filter(Boolean)
      .forEach((f) => set.add(f))
  }
  return [...set].sort((a, b) => floorSortKey(a) - floorSortKey(b))
})

// 筛选栏的类型可选值：随校区/楼栋收窄
const filterRoomTypes = computed(() =>
  roomTypesOf(selCampus.value, selBuilding.value ? [selBuilding.value] : [])
)

// 按楼栋+楼层分组（扁平）
const groupedResources = computed(() => {
  const groups = []
  const map = new Map()
  for (const r of resources.value) {
    const key = `${r.campus}|${r.building}|${r.floor}`
    if (!map.has(key)) {
      const label = [r.campus, r.building, r.floor].filter(Boolean).join(' · ')
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

// 卡片配图：与详情页共用同一套规则（体育馆按场馆名取实拍图）
function getVenueImage(item) {
  return resourceImage(item)
}

// 维修中(3) / 报修中(4)：用户端一律视为不可用
function isUnavailable(s) {
  return s === 3 || s === 4
}
function unavailableText(s) {
  return s === 4 ? '报修中' : '维修中'
}

function statusText(s) {
  return { 1: '可预约', 2: '使用中', 3: '维修中', 4: '报修中' }[s] || '未知'
}
// 配色约定：可预约/可用=绿，已约/使用中=红，维修中/报修中=黄
function statusTagType(s) {
  return { 1: 'success', 2: 'danger', 3: 'warning', 4: 'warning' }[s] || 'info'
}

// 房间角标：维修/报修 → 「维修」「报修」；使用中 → 「占用」；其余以小圆点表示
function roomStatusText(item) {
  if (isUnavailable(item.status)) return item.status === 4 ? '报修' : '维修'
  if (isClassroomType.value) return ''
  if (item.status === 2) return '占用'
  return ''
}
function roomStatusClass(item) {
  return isUnavailable(item.status) ? 'repair' : 'occupied'
}

// 小圆点是否标红：教室看"是否正在上课"，其他资源看"是否已被预约"
function isDotRed(item) {
  if (isClassroomType.value) return !!item.courseBusy
  return !!item.reserved
}

function roomTip(item) {
  if (isUnavailable(item.status)) return unavailableText(item.status)
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
  if (isUnavailable(item.status)) return unavailableText(item.status)
  const unit = item.unit || '人'
  const cap = item.capacity || 0
  const remain = Math.max(0, cap - (item.occupiedNow || 0))
  return remain > 0 ? `剩余 ${remain} ${unit}` : '已满'
}
function venueTagType(item) {
  if (isUnavailable(item.status)) return 'warning'
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

function onCampusChange() {
  selBuilding.value = ''
  selFloor.value = ''
  selRoomType.value = ''
  page.current = 1
  groupPage.value = 1
  loadData()
}

function onRoomTypeChange() {
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
  await ensureBuildingTree()
  // 教室页只展示空闲查询结果，不加载全量教室列表
  if (isClassroomType.value) return
  loading.value = true
  try {
    // 按钮模式 / 图书馆座位图：一次全加载，避免分页切断校区楼栋分组
    const size = isButtonMode.value || isLibraryType.value ? 1000 : page.size
    const res = await api.getResourcePage({
      current: page.current,
      size,
      type: Number(route.params.type),
      keyword: keyword.value || undefined,
      campus: selCampus.value || undefined,
      building: selBuilding.value || undefined,
      roomType: selRoomType.value || undefined,
      floor: isLibraryType.value ? libFloor.value : selFloor.value || undefined
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

// 点击座位：维修中仅提示；被他人占用时不可进入（仅显示对方时段与备注）；其余进入预约详情
function onSeatSelect(item) {
  if (isUnavailable(item.status)) {
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
  if (!userStore.isAdmin && item.reserved && !item.reservedByMe) {
    showOccupied(item, '该座位已被他人预约')
    return
  }
  goDetail(item.id)
}

function goDetail(id) {
  // 详情用侧边抽屉展示，不离开当前页，筛选条件不会丢失
  detailId.value = id
  detailVisible.value = true
}

// 详情抽屉：四类资源共用一个面板组件（教室课表 / 实验室与体育馆预约 / 图书馆座位）
const detailVisible = ref(false)
const detailId = ref(null)
// 宽屏固定 760px，窄屏自适应留边
const detailDrawerSize = 'min(760px, 94vw)'

// 非本人预约的资源/维修中的资源：普通用户不可进入，仅提示占用信息（管理员不受限）
const blockDialogVisible = ref(false)
const blockInfo = reactive({ type: '', title: '', resourceName: '', timeRange: '', remark: '' })

function fmt(dt) {
  return dt ? String(dt).replace('T', ' ').slice(0, 16) : ''
}

// 被他人占用：普通用户不可进入，仅提示对方的预约时段与备注（管理员不受限）
function showOccupied(item, title = '该资源已被他人占用') {
  Object.assign(blockInfo, {
    type: 'occupied',
    title,
    resourceName: item.name,
    timeRange: `${fmt(item.reservedStart)} ~ ${fmt(item.reservedEnd)}`,
    remark: item.reservedRemark || ''
  })
  blockDialogVisible.value = true
}

function onRoomClick(item) {
  // 教室不参与预约，任何用户都可查看课表
  if (userStore.isAdmin || isClassroomType.value) {
    goDetail(item.id)
    return
  }
  if (isUnavailable(item.status)) {
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
  // 体育馆可多人同时预约，任何用户都可进入
  if (isGymType.value) {
    goDetail(item.id)
    return
  }
  // 实验室 / 图书馆座位：被他人占用时不可进入，仅提示备注信息
  if (item.reserved && !item.reservedByMe) {
    showOccupied(item, '该资源已被他人预约')
    return
  }
  goDetail(item.id)
}

watch(
  () => route.params.type,
  () => {
    keyword.value = ''
    roomKeyword.value = ''
    selCampus.value = ''
    selBuilding.value = ''
    selFloor.value = ''
    selRoomType.value = ''
    libFloor.value = '6F'
    roomQuery.campus = ''
    roomQuery.buildings = []
    roomQuery.roomTypes = []
    buildingTreeType = null
    page.current = 1
    groupPage.value = 1
    saveLastCategory(route.params.type)
    loadData()
  }
)

onMounted(() => {
  // 记录当前分类，返回首页时弧线导航直接停在对应节点上
  saveLastCategory(route.params.type)
  loadData()
})
</script>

<style scoped lang="scss">
.category-detail {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  .page-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }
}

.filter-bar {
  margin-bottom: 20px;
  border-radius: 12px;
  border: none;
  .filter-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
    &:last-child {
      margin-bottom: 0;
    }
    // 关键词搜索独占一行，宽度与筛选栏区分开
    &.search-row .search-input {
      flex: 1;
      max-width: 420px;
    }
    .campus-select {
      width: 150px;
    }
    .building-select {
      width: 180px;
    }
    .roomtype-select {
      width: 170px;
    }
    .floor-select {
      width: 140px;
    }
  }
  .info-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    .sub-tag {
      border-radius: 8px;
    }
  }
}

/* ===== 教室：空闲教室查询 ===== */
.room-query {
  margin-bottom: 20px;
  border-radius: 12px;
  border: none;
}
.query-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  & + .query-row {
    margin-top: 10px;
  }
  .query-label {
    width: 32px;
    font-size: 13px;
    color: #909399;
  }
  // 关键词搜索独占一行
  .keyword-input {
    flex: 1;
    max-width: 420px;
  }
  .date-picker {
    width: 160px;
  }
  .time-select {
    width: 120px;
  }
  .range-dash {
    font-size: 13px;
    color: #909399;
  }
  .building-multi {
    flex: 1;
    min-width: 220px;
  }
  .type-multi {
    width: 200px;
  }
  .campus-select {
    width: 150px;
  }
}
.quick-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  .quick-label {
    font-size: 13px;
    color: #909399;
  }
}
.quick-chip {
  padding: 4px 12px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    color: #409eff;
    border-color: #b3d8ff;
  }
  &.active {
    color: #fff;
    background: linear-gradient(135deg, #144e9c, #2e76db);
    border-color: transparent;
  }
}
.free-result {
  min-height: 160px;
}
.result-head {
  margin-bottom: 14px;
  font-size: 14px;
  color: #606266;
  .result-date {
    margin-right: 6px;
    font-weight: 600;
    color: #303133;
  }
  b {
    margin: 0 2px;
    font-size: 16px;
    color: #144e9c;
  }
}
.free-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.free-card {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    border-color: #409eff;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(64, 158, 255, 0.16);
  }
  .free-room-no {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
  .free-where {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
  .free-type {
    display: inline-block;
    margin-top: 6px;
    padding: 1px 8px;
    font-size: 12px;
    color: #144e9c;
    background: #eef4fc;
    border-radius: 999px;
  }
  .free-cap {
    margin-top: 6px;
    font-size: 12px;
    color: #606266;
  }
  .free-until {
    margin-top: 2px;
    font-size: 12px;
    color: #67c23a;
  }
}

/* ===== 图书馆座位图 ===== */
.library-view {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.floor-tabs {
  display: flex;
  gap: 12px;
  .floor-tab {
    padding: 9px 22px;
    font-size: 14px;
    font-weight: 500;
    color: #606266;
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.2s;
    &:hover {
      border-color: #409eff;
      color: #409eff;
    }
    &.active {
      color: #fff;
      border-color: transparent;
      background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
    }
  }
}

/* ===== 按钮模式 ===== */
.button-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.floor-section {
  .floor-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    padding-bottom: 6px;
    border-bottom: 1px solid #ebeef5;
    .floor-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
    .floor-count {
      font-size: 12px;
      color: #c0c4cc;
      margin-left: auto;
    }
  }
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 8px;
}

.room-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
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
  &.disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  .room-no {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
  .room-cap {
    font-size: 11px;
    color: #909399;
  }
  .room-dot {
    position: absolute;
    top: 7px;
    right: 7px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    &.on {
      background: #67c23a;
    }
    &.off {
      background: #f56c6c;
    }
  }
  .room-status {
    position: absolute;
    top: 5px;
    right: 5px;
    font-size: 10px;
    padding: 1px 5px;
    border-radius: 8px;
    color: #fff;
    font-weight: 500;
    &.occupied {
      background: #f56c6c;
    }
    &.repair {
      background: #e6a23c;
    }
  }
}

/* ===== 卡片模式 ===== */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.resource-card {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.3s,
    box-shadow 0.3s;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  &:hover {
    transform: translateY(-6px);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
  }

  .card-banner {
    height: 120px;
    background-size: cover;
    background-position: center;
    position: relative;
    .banner-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, rgba(0, 0, 0, 0) 30%, rgba(0, 0, 0, 0.5) 100%);
    }
    .banner-room {
      position: absolute;
      bottom: 10px;
      left: 16px;
      color: #fff;
      font-size: 22px;
      font-weight: 700;
      text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
    }
    .status-tag {
      position: absolute;
      top: 10px;
      right: 10px;
    }
  }
  .card-body {
    padding: 14px 18px 18px;
    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
    }
    // 运动类型/实验方向等细分类型标签
    .card-type {
      display: inline-block;
      margin: -2px 0 8px;
      font-size: 12px;
      color: #144e9c;
    }
    .card-info {
      display: flex;
      gap: 16px;
      margin-bottom: 6px;
      .info-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: #909399;
      }
    }
    .card-time {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      color: #909399;
    }
  }
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.block-body {
  .block-res {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 14px;
  }
  .block-row {
    display: flex;
    gap: 10px;
    margin-bottom: 10px;
    font-size: 13px;
    line-height: 1.7;

    .block-label {
      flex: 0 0 64px;
      color: #909399;
    }
    .block-value {
      flex: 1;
      color: #303133;
      word-break: break-all;
    }
  }
  .block-tip {
    font-size: 13px;
    color: #909399;
  }
}
</style>
