<template>
  <div class="detail-page">
    <div class="back-btn">
      <el-button :icon="ArrowLeft" text @click="$router.back()">返回资源列表</el-button>
    </div>
    <ResourceDetailPanel :resource-id="route.params.id" />
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import ResourceDetailPanel from '@/components/ResourceDetailPanel.vue'

const route = useRoute()
</script>

<style scoped lang="scss">
/* 直接访问 /resource/:id 时保留独立页布局；列表页改用抽屉复用同一个面板组件 */
.detail-page {
  max-width: 900px;
  margin: 0 auto;
}

.back-btn {
  margin-bottom: 12px;
}
</style>
