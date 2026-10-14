<template>
  <div class="violation-manage">
    <el-card shadow="never" class="main-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名 / 姓名"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
        <div class="toolbar-right">
          <span class="rule-tip">规则：一周内违规满 {{ limit }} 次，本周内无法预约</span>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon class="tip">
        <template #title>按<b>用户</b>统计违规情况（每人一行，不展开违规记录明细）；点击行可查看该用户的违规明细。</template>
      </el-alert>

      <!-- 概览 -->
      <div class="sum-row">
        <div class="sum"><b>{{ total }}</b><span>有违规的用户</span></div>
        <div class="sum"><b>{{ restrictedCount }}</b><span>当前被限制预约</span></div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe style="width: 100%" @row-click="openDetail">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="avatar">{{ (row.nickname || row.username || 'U').charAt(0) }}</span>
              <div class="user-text">
                <div class="uname">{{ row.nickname || row.username }}</div>
                <div class="uid">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="信用分" width="100" align="center">
          <template #default="{ row }">
            <span class="credit" :class="creditClass(row.creditScore)">{{ row.creditScore ?? 100 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="近 7 天违规" width="120" align="center">
          <template #default="{ row }">
            <span class="week-count" :class="{ danger: row.weekCount > 0 }">{{ row.weekCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计违规" width="110" align="center">
          <template #default="{ row }">{{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column label="预约权限" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.restricted ? 'danger' : 'success'" size="small" effect="light">
              {{ row.restricted ? '受限' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近违规时间" width="170">
          <template #default="{ row }">{{ fmt(row.lastViolationAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="openDetail(row)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="暂无违规用户" />

      <div class="pagination" v-if="total > page.size">
        <el-pagination
          v-model:current-page="page.current"
          :page-size="page.size"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 某用户的违规明细 -->
    <el-dialog v-model="detailVisible" :title="`${detailUser.nickname || detailUser.username || ''} 的违规明细`" width="720px">
      <el-table :data="detailRows" v-loading="detailLoading" stripe style="width: 100%" empty-text="暂无违规记录">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag type="danger" size="small" effect="light">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="260">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="扣分" width="80" align="center">
          <template #default="{ row }"><span class="deduct">-{{ row.deductScore || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '有效' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { api } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const limit = ref(3)
const keyword = ref('')
const page = reactive({ current: 1, size: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailUser = ref({})
const detailRows = ref([])

const restrictedCount = computed(() => rows.value.filter(r => r.restricted).length)

const TYPE_TEXT = { 1: '爽约', 2: '超时使用', 3: '恶意取消', 4: '其他' }
function typeText(t) { return TYPE_TEXT[t] || '其他' }
function fmt(dt) { return dt ? String(dt).replace('T', ' ').slice(0, 16) : '-' }
function creditClass(s) {
  const v = s ?? 100
  return v < 60 ? 'bad' : v < 90 ? 'warn' : 'good'
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.getAdminViolationUsers({
      keyword: keyword.value || undefined,
      current: page.current,
      size: page.size
    })
    if (res.code === 200) {
      rows.value = res.data?.records || []
      total.value = res.data?.total || 0
      if (res.data?.limit) limit.value = res.data.limit
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.current = 1
  loadData()
}

async function openDetail(row) {
  detailUser.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await api.getViolations({ userId: row.userId, current: 1, size: 100 })
    detailRows.value = res.code === 200 ? (res.data?.records || []) : []
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.violation-manage { max-width: 1200px; margin: 0 auto; }
.main-card { border-radius: 14px; border: none; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 12px; margin-bottom: 16px;
  .toolbar-left { display: flex; gap: 10px; align-items: center; }
  .rule-tip { font-size: 12px; color: #909399; }
}

.tip { margin-bottom: 16px; }

.sum-row {
  display: flex; gap: 12px; margin-bottom: 16px;
  .sum {
    flex: 1; text-align: center; padding: 14px 0; border-radius: 12px;
    background: #f7f9fc; border: 1px solid #eef2f7;
    b { display: block; font-size: 24px; font-weight: 800; color: #303133; }
    span { font-size: 12px; color: #909399; }
  }
}

.user-cell {
  display: flex; align-items: center; gap: 10px;
  .avatar {
    width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    font-size: 13px; font-weight: 700; color: #fff;
    background: linear-gradient(135deg, #2E76DB, #74B9F5);
  }
  .user-text { line-height: 1.3;
    .uname { font-size: 13px; font-weight: 600; color: #303133; }
    .uid { font-size: 12px; color: #909399; }
  }
}

.credit {
  font-weight: 700;
  &.good { color: #67c23a; }
  &.warn { color: #e6a23c; }
  &.bad { color: #f56c6c; }
}

.week-count { font-weight: 700; color: #606266;
  &.danger { color: #f56c6c; }
}

.deduct { color: #f56c6c; font-weight: 600; }

.pagination { display: flex; justify-content: center; margin-top: 18px; }
</style>
