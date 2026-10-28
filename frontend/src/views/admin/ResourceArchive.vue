<template>
  <div class="resource-archive">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-search">
        <el-input
          v-model="keyword"
          placeholder="搜索资源名称 / 楼栋 / 房间号"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @clear="searchNow"
          @keyup.enter="searchNow"
        />
        <el-button type="primary" :icon="Search" @click="searchNow">搜索</el-button>
      </div>
      <div class="toolbar-body">
        <div class="toolbar-left">
          <el-select
            v-model="filterType"
            placeholder="资源类型"
            clearable
            class="type-select"
            @change="onTypeChange"
          >
            <el-option label="教室" :value="1" />
            <el-option label="实验室" :value="2" />
            <el-option label="图书馆" :value="3" />
            <el-option label="体育馆" :value="4" />
          </el-select>
          <el-select
            v-model="filterCampus"
            placeholder="校区"
            clearable
            class="type-select"
            @change="onCampusChange"
          >
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select
            v-model="filterBuilding"
            placeholder="楼栋"
            clearable
            filterable
            class="type-select"
            @change="onBuildingChange"
          >
            <el-option v-for="b in buildingOptions" :key="b" :label="b" :value="b" />
          </el-select>
          <el-select
            v-model="filterRoomType"
            placeholder="细分类型"
            clearable
            class="type-select"
            @change="query"
          >
            <el-option v-for="t in roomTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select
            v-model="filterStatus"
            placeholder="状态"
            clearable
            class="type-select"
            @change="query"
          >
            <el-option label="可预约" :value="1" />
            <el-option label="使用中" :value="2" />
            <el-option label="维修中" :value="3" />
            <el-option label="报修中" :value="4" />
          </el-select>
        </div>
        <el-button type="primary" :icon="Plus" @click="openDrawer()">新增资源</el-button>
      </div>
    </div>

    <!-- 表格：整行可点，点开就是这一行的全部信息；未主动查询前不拉全量 -->
    <el-table
      v-if="loaded"
      :data="resources"
      v-loading="loading"
      style="width: 100%"
      row-key="id"
      empty-text="没有符合条件的资源，换个条件试试"
      row-class-name="row-clickable"
      max-height="560"
      @row-click="openDrawer"
    >
      <el-table-column prop="name" label="名称" min-width="170" />
      <el-table-column label="类型" width="96">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.resourceType)" effect="light">{{
            typeText(row.resourceType)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="campus" label="校区" width="110" />
      <el-table-column prop="building" label="楼宇" width="120" />
      <el-table-column prop="floor" label="楼层" width="72" />
      <el-table-column prop="roomType" label="细分类型" width="108" />
      <el-table-column prop="roomNo" label="房间号" width="96" />
      <el-table-column label="容量" width="80">
        <template #default="{ row }">{{ row.capacity }} {{ row.unit || '人' }}</template>
      </el-table-column>
      <el-table-column label="开放时间" width="140">
        <template #default="{ row }">
          {{ short(row.openTime) }} - {{ short(row.closeTime) }}
        </template>
      </el-table-column>
      <el-table-column label="打卡点" width="150">
        <template #default="{ row }">
          <a
            v-if="row.latitude != null && row.longitude != null"
            class="coord-link"
            :href="mapLink(row.longitude, row.latitude, row.name)"
            target="_blank"
            rel="noopener"
            @click.stop
          >
            {{ Number(row.latitude).toFixed(5) }}, {{ Number(row.longitude).toFixed(5) }}
          </a>
          <el-tag v-else type="warning" size="small">未配置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前占用" width="112">
        <template #default="{ row }">
          <el-tag v-if="row.courseBusy" type="warning" effect="light">上课中</el-tag>
          <el-tag v-else-if="row.reserved" type="danger" effect="light">已预约</el-tag>
          <el-tag v-else-if="row.resourceType === 1" type="info" effect="plain">空闲</el-tag>
          <el-tag v-else type="success" effect="plain">空闲</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="dark">{{
            statusText(row.status)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="132" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" :icon="Edit" @click.stop="openDrawer(row)">详情</el-button>
          <el-button text type="danger" :icon="Delete" @click.stop="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else description="尚未加载资源：选择筛选条件、输入关键词搜索，或直接查看全部资源">
      <el-button type="primary" :icon="Search" @click="loadAll">查看全部资源</el-button>
    </el-empty>

    <div class="pagination" v-if="loaded">
      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <!-- 右侧抽屉：看全部信息 + 直接改 + 看这个资源被谁预约了 -->
    <el-drawer
      v-model="drawerVisible"
      :size="760"
      :with-header="false"
      destroy-on-close
      class="res-drawer"
    >
      <div v-loading="detailLoading" class="dw">
        <header class="dw-head">
          <div class="dw-title">
            <span class="dw-name">{{
              editForm.id ? editForm.name || '未命名资源' : '新增资源'
            }}</span>
            <el-tag :type="typeTagType(editForm.resourceType)" effect="light">{{
              typeText(editForm.resourceType)
            }}</el-tag>
            <el-tag v-if="editForm.id" :type="statusTagType(editForm.status)" effect="dark">{{
              statusText(editForm.status)
            }}</el-tag>
            <el-tag v-if="editForm.id" :type="occupancy.type" effect="plain">{{
              occupancy.text
            }}</el-tag>
          </div>
          <el-button text circle :icon="Close" @click="drawerVisible = false" />
        </header>

        <div class="dw-body">
          <section class="dw-sec">
            <div class="sec-hd">
              <span>资源信息（全部可改）</span>
              <span v-if="editForm.id" class="sec-hint">
                编号 #{{ editForm.id }} · 创建于 {{ fmtTime(editForm.createdAt) }}
              </span>
            </div>
            <el-form :model="editForm" label-width="92px" class="dw-form">
              <el-form-item label="名称" required>
                <el-input v-model="editForm.name" placeholder="如 南院-博学主楼-101" />
              </el-form-item>
              <el-form-item label="资源类型" required>
                <el-radio-group v-model="editForm.resourceType" @change="onFormTypeChange">
                  <el-radio-button :value="1">教室</el-radio-button>
                  <el-radio-button :value="2">实验室</el-radio-button>
                  <el-radio-button :value="3">图书馆</el-radio-button>
                  <el-radio-button :value="4">体育馆</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <div class="form-grid">
                <el-form-item label="校区">
                  <el-select
                    v-model="editForm.campus"
                    placeholder="选择或输入校区"
                    filterable
                    allow-create
                  >
                    <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
                  </el-select>
                </el-form-item>
                <el-form-item label="楼宇">
                  <el-input v-model="editForm.building" placeholder="如 博学主楼" />
                </el-form-item>
                <el-form-item label="楼层">
                  <el-input v-model="editForm.floor" placeholder="如 1F" />
                </el-form-item>
                <el-form-item label="细分类型">
                  <el-select
                    v-model="editForm.roomType"
                    placeholder="如 多媒体教室"
                    filterable
                    allow-create
                  >
                    <el-option v-for="t in roomTypeOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
                <el-form-item label="房间号">
                  <el-input v-model="editForm.roomNo" placeholder="如 101" />
                </el-form-item>
                <el-form-item label="容纳人数">
                  <el-input-number v-model="editForm.capacity" :min="0" :max="5000" />
                </el-form-item>
                <el-form-item label="计量单位">
                  <el-select
                    v-model="editForm.unit"
                    filterable
                    allow-create
                    placeholder="人 / 台 / 片"
                  >
                    <el-option label="人" value="人" />
                    <el-option label="台" value="台" />
                    <el-option label="片" value="片" />
                    <el-option label="座" value="座" />
                  </el-select>
                </el-form-item>
                <el-form-item label="单次上限">
                  <el-input-number
                    v-model="editForm.maxDuration"
                    :min="15"
                    :max="480"
                    :step="15"
                    class="duration-input"
                  />
                  <span class="unit-hint">分钟</span>
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="editForm.status">
                    <el-option label="可预约" :value="1" />
                    <el-option label="使用中" :value="2" />
                    <el-option label="维修中" :value="3" />
                    <el-option label="报修中" :value="4" />
                  </el-select>
                </el-form-item>
              </div>
              <el-form-item label="开放时间">
                <el-time-picker
                  v-model="editForm.openTimeObj"
                  format="HH:mm"
                  value-format="HH:mm:00"
                  placeholder="开始"
                />
                <span class="dash">-</span>
                <el-time-picker
                  v-model="editForm.closeTimeObj"
                  format="HH:mm"
                  value-format="HH:mm:00"
                  placeholder="结束"
                />
              </el-form-item>
              <el-form-item label="打卡基准点">
                <div class="coord-box">
                  <div class="coord-row">
                    <el-input-number
                      v-model="editForm.latitude"
                      :precision="6"
                      :step="0.0001"
                      controls-position="right"
                      class="coord-input"
                    />
                    <span class="dash">,</span>
                    <el-input-number
                      v-model="editForm.longitude"
                      :precision="6"
                      :step="0.0001"
                      controls-position="right"
                      class="coord-input"
                    />
                    <span class="unit-hint">纬度 , 经度</span>
                    <el-button
                      size="small"
                      :icon="Aim"
                      :loading="locating"
                      class="locate-btn"
                      @click="locateMe"
                      >取当前位置</el-button
                    >
                  </div>
                  <div class="coord-tip">
                    坐标按「校区 + 楼栋」共享：保存后该楼栋全部资源一并更新（WGS-84）。
                    建议管理员站在楼栋前点一次「取当前位置」完成校准。
                  </div>
                </div>
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="editForm.description" type="textarea" :rows="2" />
              </el-form-item>
            </el-form>
          </section>

          <section v-if="editForm.id" class="dw-sec">
            <div class="sec-hd">
              <span>{{ editForm.resourceType === 1 ? '课表与占用' : '预约与占用' }}</span>
              <el-date-picker
                v-model="bookingDate"
                type="date"
                value-format="YYYY-MM-DD"
                :clearable="false"
                size="small"
                style="width: 132px"
                @change="reloadOccupancy"
              />
            </div>

            <p v-if="editForm.resourceType === 1" class="sec-tip">
              教室按课表上课、不参与预约，这里展示该教室当天的课程安排；课程只在标注的周次上课，
              其他周次的同一时段视为空闲。
            </p>
            <p v-else class="sec-tip">
              当天共 {{ bookings.length }} 条预约，其中进行中
              {{ activeCount }} 条；红点表示当前时刻正被占用。
            </p>

            <p v-if="editForm.resourceType === 1 || roomCourses.length" class="list-hd">
              课程占用（该时段不可预约）
            </p>
            <div
              v-if="editForm.resourceType === 1 || roomCourses.length"
              v-loading="courseLoading"
              class="booking-box"
            >
              <p v-if="!roomCourses.length" class="booking-empty">这一天没有排课</p>
              <ul v-else class="booking-list">
                <li v-for="c in roomCourses" :key="c.id">
                  <span class="bk-time">{{ short(c.startTime) }} - {{ short(c.endTime) }}</span>
                  <span class="bk-user">{{ c.courseName }}</span>
                  <span class="bk-meta">{{ c.teacher || '—' }}</span>
                  <span v-if="c.weeksLabel && c.weeksLabel !== '每周'" class="bk-meta"
                    >仅 {{ c.weeksLabel }}</span
                  >
                </li>
              </ul>
            </div>

            <template v-if="editForm.resourceType !== 1">
              <p class="list-hd">当日预约</p>
              <div v-loading="bookingLoading" class="booking-box">
                <p v-if="!bookings.length" class="booking-empty">这一天还没有人预约</p>
                <ul v-else class="booking-list">
                  <li v-for="b in bookings" :key="b.id">
                    <span class="bk-time">{{ clock(b.startTime) }} - {{ clock(b.endTime) }}</span>
                    <span class="bk-user">{{ userName(b) }}</span>
                    <span class="bk-meta">{{ b.remark || '—' }}</span>
                    <el-tag size="small" :type="resStatusType(b.status)">{{
                      resStatusText(b.status)
                    }}</el-tag>
                  </li>
                </ul>
              </div>
            </template>
          </section>
        </div>

        <footer class="dw-foot">
          <el-button
            v-if="editForm.id"
            type="danger"
            plain
            :icon="Delete"
            @click="doDelete(editForm)"
            >删除资源</el-button
          >
          <div class="foot-right">
            <el-button @click="drawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">
              {{ editForm.id ? '保存修改' : '创建资源' }}
            </el-button>
          </div>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Plus, Edit, Delete, Close, Aim } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const emit = defineEmits(['changed'])

const loading = ref(false)
// 是否已经发起过查询：false 时显示空态引导，不拉全量数据
const loaded = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterType = ref(undefined)
const filterStatus = ref(undefined)
const filterCampus = ref('')
const filterBuilding = ref('')
const filterRoomType = ref('')
const resources = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })

// 校区与楼栋来自真实资源聚合，楼栋按当前类型与校区联动过滤
const buildingTree = ref([])
const campusOptions = computed(() => [
  ...new Set(buildingTree.value.map((x) => x.campus).filter(Boolean))
])
const buildingOptions = computed(() =>
  buildingTree.value
    .filter((x) => !filterCampus.value || x.campus === filterCampus.value)
    .filter(
      (x) =>
        !filterType.value ||
        String(x.types || '')
          .split(',')
          .includes(String(filterType.value))
    )
    .map((x) => x.building)
)

// 细分类型展示顺序：教室 → 实验室 → 体育馆 → 图书馆，未知类型补在末尾
const ROOM_TYPE_ORDER = [
  '多媒体教室',
  '普通教室',
  '学院专用',
  '机房',
  '制图教室',
  '自习教室',
  '其他场地',
  '化学',
  '化学生物',
  '工程实训',
  '交通运输',
  '管理',
  '通用实验',
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
  '综合场地',
  '自习座位'
]
const roomTypeOptions = computed(() => {
  const set = new Set()
  for (const x of buildingTree.value) {
    if (filterCampus.value && x.campus !== filterCampus.value) continue
    if (filterBuilding.value && x.building !== filterBuilding.value) continue
    if (
      filterType.value &&
      !String(x.types || '')
        .split(',')
        .includes(String(filterType.value))
    )
      continue
    const raw = x.roomTypes || x.roomtypes || x.room_types || x.ROOMTYPES
    String(raw || '')
      .split(',')
      .filter(Boolean)
      .forEach((t) => set.add(t))
  }
  return [...set].sort((a, b) => {
    const ia = ROOM_TYPE_ORDER.indexOf(a)
    const ib = ROOM_TYPE_ORDER.indexOf(b)
    return (ia < 0 ? ROOM_TYPE_ORDER.length : ia) - (ib < 0 ? ROOM_TYPE_ORDER.length : ib)
  })
})

async function loadBuildingTree() {
  const res = await api.getBuildings()
  buildingTree.value = res.code === 200 ? res.data || [] : []
}

function typeText(t) {
  return { 1: '教室', 2: '实验室', 3: '图书馆', 4: '体育馆' }[t] || '未知'
}
function typeTagType(t) {
  return { 1: '', 2: 'success', 3: 'warning', 4: 'danger' }[t] || 'info'
}
function statusText(s) {
  return { 1: '可预约', 2: '使用中', 3: '维修中', 4: '报修中' }[s] || '未知'
}
// 配色约定：可预约/空闲=绿，使用中/已预约=红，维修中/报修中/上课=黄
function statusTagType(s) {
  return { 1: 'success', 2: 'danger', 3: 'warning', 4: 'warning' }[s] || 'info'
}
const RES_STATUS_TEXT = {
  1: '待打卡',
  2: '使用中',
  3: '已结束',
  4: '已取消',
  5: '爽约',
  6: '违规'
}
const RES_STATUS_TYPE = {
  1: 'warning',
  2: 'danger',
  3: 'info',
  4: 'info',
  5: 'warning',
  6: 'danger'
}
function resStatusText(s) {
  return RES_STATUS_TEXT[s] || '未知'
}
function resStatusType(s) {
  return RES_STATUS_TYPE[s] || 'info'
}

const short = (t) => (t ? String(t).slice(0, 5) : '—')
const clock = (t) => (t ? String(t).slice(11, 16) : '—')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')
const pad = (n) => String(n).padStart(2, '0')
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function userName(b) {
  return b.user?.nickname || b.user?.username || `用户#${b.userId}`
}

// ---------------- 打卡基准点：按楼栋共享 + 现场校准 ----------------
const locating = ref(false)

/** 高德地图点位链接：坐标必须按「经度,纬度」顺序传 */
function mapLink(lng, lat, name) {
  if (lng == null || lat == null) return '#'
  return `https://uri.amap.com/marker?position=${lng},${lat}&name=${encodeURIComponent(name || '打卡点')}`
}

/** 用浏览器定位校准楼栋打卡点：管理员站在楼前点一次即可 */
function locateMe() {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      editForm.latitude = Number(pos.coords.latitude.toFixed(6))
      editForm.longitude = Number(pos.coords.longitude.toFixed(6))
      locating.value = false
      ElMessage.success(
        `已填入当前位置（精度约 ${Math.round(pos.coords.accuracy)} 米），保存后同楼栋资源共享该坐标`
      )
    },
    (err) => {
      locating.value = false
      ElMessage.error(err.code === 1 ? '定位权限被拒，请允许后重试' : '定位失败，请稍后重试')
    },
    { enableHighAccuracy: true, timeout: 10000 }
  )
}

async function loadData() {
  loaded.value = true
  loading.value = true
  try {
    const res = await api.getResourcePage({
      current: page.current,
      size: page.size,
      type: filterType.value || undefined,
      status: filterStatus.value || undefined,
      campus: filterCampus.value || undefined,
      building: filterBuilding.value || undefined,
      roomType: filterRoomType.value || undefined,
      keyword: keyword.value || undefined
    })
    if (res.code === 200) {
      resources.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function query() {
  page.current = 1
  loadData()
}

/** 空态引导里的「查看全部资源」：不带条件拉第一页 */
function loadAll() {
  filterStatus.value = undefined
  filterType.value = undefined
  keyword.value = ''
  filterBuilding.value = ''
  filterRoomType.value = ''
  query()
}

function searchNow() {
  query()
}

function onTypeChange() {
  filterBuilding.value = ''
  filterRoomType.value = ''
  query()
}

function onCampusChange() {
  filterBuilding.value = ''
  filterRoomType.value = ''
  query()
}

function onBuildingChange() {
  filterRoomType.value = ''
  query()
}

/** 概览页的指标卡 / 饼图点击后，按条件筛出对应资源（没传的条件一律清空） */
function applyFilters({ status, type, keyword: kw } = {}) {
  filterStatus.value = status || undefined
  filterType.value = type || undefined
  keyword.value = kw || ''
  filterBuilding.value = ''
  filterRoomType.value = ''
  query()
}

// ---------------- 抽屉：查看 / 编辑 / 占用 ----------------
const drawerVisible = ref(false)
const detailLoading = ref(false)
const bookingLoading = ref(false)
const courseLoading = ref(false)
const bookings = ref([])
const roomCourses = ref([])
const bookingDate = ref(todayStr())
const flags = reactive({ reserved: false, courseBusy: false })

const emptyForm = () => ({
  id: null,
  name: '',
  resourceType: 1,
  campus: filterCampus.value || '',
  building: '',
  floor: '',
  roomType: '',
  roomNo: '',
  capacity: 0,
  unit: '人',
  status: 1,
  openTimeObj: '08:00:00',
  closeTimeObj: '22:00:00',
  maxDuration: 120,
  latitude: null,
  longitude: null,
  description: '',
  createdAt: null
})
const editForm = reactive(emptyForm())

const occupancy = computed(() => {
  if (!editForm.id) return { text: '', type: 'info' }
  // 课程占用优先：实验室/体育馆被排课时同样整段不可预约
  if (flags.courseBusy) return { text: '正在上课', type: 'warning' }
  if (editForm.resourceType === 1) return { text: '课表空闲', type: 'success' }
  return flags.reserved
    ? { text: '当前有预约', type: 'danger' }
    : { text: '当前空闲', type: 'success' }
})
const activeCount = computed(
  () => bookings.value.filter((b) => b.status === 1 || b.status === 2).length
)

function fillForm(row) {
  Object.assign(editForm, emptyForm())
  if (!row) return
  Object.assign(editForm, {
    id: row.id,
    name: row.name || '',
    resourceType: row.resourceType || 1,
    campus: row.campus || '',
    building: row.building || '',
    floor: row.floor || '',
    roomType: row.roomType || '',
    roomNo: row.roomNo || '',
    capacity: row.capacity ?? 0,
    unit: row.unit || '人',
    status: row.status ?? 1,
    openTimeObj: row.openTime || '08:00:00',
    closeTimeObj: row.closeTime || '22:00:00',
    maxDuration: row.maxDuration ?? 120,
    latitude: row.latitude == null ? null : Number(row.latitude),
    longitude: row.longitude == null ? null : Number(row.longitude),
    description: row.description || '',
    createdAt: row.createdAt || null
  })
  flags.reserved = !!row.reserved
  flags.courseBusy = !!row.courseBusy
}

async function openDrawer(row) {
  fillForm(row)
  drawerVisible.value = true
  bookingDate.value = todayStr()
  bookings.value = []
  roomCourses.value = []
  if (!row) return
  await Promise.all([loadDetail(), reloadOccupancy()])
}

/** 抽屉里重新拉一次详情，保证看到的是库里最新值 */
async function loadDetail() {
  if (!editForm.id) return
  detailLoading.value = true
  try {
    const res = await api.getResource(editForm.id)
    if (res.code === 200) fillForm(res.data)
  } finally {
    detailLoading.value = false
  }
}

function weekdayOf(dateStr) {
  const d = new Date(`${dateStr}T00:00:00`)
  return d.getDay() || 7
}

async function reloadOccupancy() {
  if (!editForm.id) return
  if (editForm.resourceType === 1) {
    await loadRoomCourses()
    return
  }
  // 非教室也要看课表：排课覆盖的时段整段不可预约，管理员得知道用户为什么约不进来
  await Promise.all([loadRoomCourses(), loadBookings()])
}

async function loadBookings() {
  bookingLoading.value = true
  try {
    const res = await api.getAdminReservations({
      current: 1,
      size: 100,
      resourceId: editForm.id,
      date: bookingDate.value
    })
    bookings.value = res.code === 200 ? res.data.records || [] : []
  } finally {
    bookingLoading.value = false
  }
}

async function loadRoomCourses() {
  courseLoading.value = true
  try {
    const res = await api.getCourses({
      roomId: editForm.id,
      dayOfWeek: weekdayOf(bookingDate.value)
    })
    roomCourses.value = res.code === 200 ? res.data || [] : []
  } finally {
    courseLoading.value = false
  }
}

function onFormTypeChange() {
  bookings.value = []
  roomCourses.value = []
  reloadOccupancy()
}

async function submitForm() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写资源名称')
    return
  }
  if (!editForm.openTimeObj || !editForm.closeTimeObj) {
    ElMessage.warning('请填写开放时间')
    return
  }
  if (editForm.openTimeObj >= editForm.closeTimeObj) {
    ElMessage.warning('开放结束时间要晚于开始时间')
    return
  }
  submitting.value = true
  try {
    const payload = {
      id: editForm.id,
      name: editForm.name.trim(),
      resourceType: editForm.resourceType,
      campus: editForm.campus,
      building: editForm.building,
      floor: editForm.floor,
      roomType: editForm.roomType,
      roomNo: editForm.roomNo,
      capacity: editForm.capacity,
      unit: editForm.unit || '人',
      status: editForm.status,
      openTime: editForm.openTimeObj,
      closeTime: editForm.closeTimeObj,
      maxDuration: editForm.maxDuration,
      latitude: editForm.latitude,
      longitude: editForm.longitude,
      description: editForm.description
    }
    const res = editForm.id ? await api.updateResource(payload) : await api.addResource(payload)
    if (res.code === 200) {
      ElMessage.success(editForm.id ? '已保存修改' : '已新增资源')
      drawerVisible.value = false
      await Promise.all([loadData(), loadBuildingTree()])
      emit('changed')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    submitting.value = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」？该资源的历史预约记录会保留。`,
      '删除资源',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const res = await api.deleteResource(row.id)
  if (res.code === 200) {
    ElMessage.success('已删除')
    drawerVisible.value = false
    await Promise.all([loadData(), loadBuildingTree()])
    emit('changed')
  } else {
    ElMessage.error(res.message || '删除失败')
  }
}

onMounted(() => {
  // 默认不拉全量：只加载校区/楼栋选项，列表等用户筛选或点「查看全部资源」
  loadBuildingTree()
})

defineExpose({ applyFilters, reload: loadData, loadAll })
</script>

<style scoped lang="scss">
.resource-archive {
  width: 100%;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 14px;

  .toolbar-search {
    display: flex;
    align-items: center;
    gap: 12px;

    .search-input {
      flex: 1;
      max-width: 420px;
    }
  }

  .toolbar-body {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .toolbar-left {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;

    .type-select {
      width: 132px;
    }
  }
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

:deep(.row-clickable) {
  cursor: pointer;
}

/* ---------------- 抽屉 ---------------- */
.dw {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 40px);
}

.dw-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;

  .dw-title {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .dw-name {
    font-size: 17px;
    font-weight: 700;
    color: #1f2d3d;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dw-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 2px 8px;
}

.dw-sec {
  & + .dw-sec {
    margin-top: 18px;
    padding-top: 16px;
    border-top: 1px dashed #e6ecf5;
  }
}

.sec-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;

  .sec-hint {
    font-size: 12px;
    font-weight: 400;
    color: #9aa3b2;
  }
}

.sec-tip {
  margin: -4px 0 10px;
  font-size: 12px;
  color: #8a94a6;
}

/* 课表与预约两张列表同时出现时，靠小标题区分 */
.list-hd {
  margin: 6px 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: #5b6779;
}

.dw-form {
  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 14px;
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  :deep(.el-select) {
    width: 100%;
  }
}

.dash {
  margin: 0 6px;
  color: #b6c2d4;
}

.unit-hint {
  margin-left: 6px;
  font-size: 12px;
  color: #9aa3b2;
}

.coord-input {
  width: 148px !important;
}

.coord-box {
  width: 100%;
}

.coord-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.locate-btn {
  margin-left: 8px;
}

.coord-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa3b2;
}

.coord-link {
  color: #2e76db;
  text-decoration: none;
  font-variant-numeric: tabular-nums;

  &:hover {
    text-decoration: underline;
  }
}

.duration-input {
  width: 132px;
}

.booking-box {
  min-height: 60px;
}

.booking-empty {
  margin: 0;
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: #a4adbd;
}

.booking-list {
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 7px 10px;
    border-radius: 8px;
    font-size: 13px;

    &:nth-child(odd) {
      background: #f8fafd;
    }
  }

  .bk-time {
    flex: 0 0 116px;
    color: #5b6779;
    font-variant-numeric: tabular-nums;
  }
  .bk-user {
    flex: 1;
    min-width: 0;
    color: #1f2d3d;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .bk-meta {
    flex: 0 0 120px;
    color: #9aa3b2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dw-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;

  .foot-right {
    margin-left: auto;
    display: flex;
    gap: 10px;
  }
}
</style>
