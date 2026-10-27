<template>
  <div class="resource-manage">
    <!-- 资源档案主页：报修与维护作为子功能收进抽屉 -->
    <el-card shadow="never" class="top-bar">
      <div class="tb-inner">
        <div class="tb-left">
          <el-icon :size="18" color="#2e76db"><Files /></el-icon>
          <span class="tb-title">资源档案</span>
          <span class="tb-hint"
            >按校区 / 楼栋 / 类型逐级筛选，点任意一行查看全部信息并直接修改</span
          >
        </div>
        <div class="tb-right">
          <el-button :icon="Tools" @click="repairVisible = true">报修与维护</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="archive-card" shadow="never">
      <ResourceArchive ref="archiveRef" />
    </el-card>

    <!-- 报修与维护：资源档案的子功能 -->
    <el-drawer v-model="repairVisible" title="报修与维护" size="min(1200px, 94vw)" destroy-on-close>
      <RepairManage />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { Files, Tools } from '@element-plus/icons-vue'
import ResourceArchive from './ResourceArchive.vue'
import RepairManage from './RepairManage.vue'

const route = useRoute()
const archiveRef = ref()
const repairVisible = ref(false)

onMounted(async () => {
  const q = route.query
  // 资源档案默认空态引导；仅当从数据概览带筛选条件跳转时才主动查询
  if (!q.all && !q.status && !q.type && !q.keyword) return
  await nextTick()
  // 「资源总数」卡片：直接看全部资源
  if (q.all) {
    archiveRef.value?.loadAll()
    return
  }
  archiveRef.value?.applyFilters({
    status: q.status ? Number(q.status) : undefined,
    type: q.type ? Number(q.type) : undefined,
    keyword: q.keyword ? String(q.keyword) : undefined
  })
})
</script>

<style scoped lang="scss">
.resource-manage {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.top-bar {
  border-radius: 12px;
  border: none;

  :deep(.el-card__body) {
    padding: 12px 18px;
  }

  .tb-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .tb-left {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }
  .tb-title {
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;
  }
  .tb-hint {
    font-size: 12px;
    color: #8a94a6;
  }
  .tb-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }
}

.archive-card {
  border-radius: 14px;
  border: none;
  box-shadow: 0 2px 10px rgba(20, 64, 122, 0.05);

  :deep(.el-card__body) {
    padding: 16px 18px 18px;
  }
  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>
