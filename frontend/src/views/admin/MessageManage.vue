<template>
  <div class="msg-manage">
    <!-- 广播通知 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>发送通知</span>
          <span class="hd-hint">应对紧急情况，向用户群发站内通知</span>
        </div>
      </template>
      <el-radio-group v-model="broadcastScope" class="scope-group">
        <el-radio-button value="all">全体用户</el-radio-button>
        <el-radio-button value="active">正在使用中的用户</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="broadcastDraft"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        class="broadcast-input"
        placeholder="输入通知内容，将以内站消息形式推送给选中范围用户"
      />
      <div class="send-row">
        <el-button type="primary" :loading="broadcasting" @click="submitBroadcast">发送通知</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>用户留言</span>
          <div>
            <el-switch
              v-model="unreadOnly"
              inline-prompt
              active-text="未处理"
              inactive-text="全部"
              @change="reload"
            />
            <el-button type="primary" link @click="reload">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无留言">
        <el-table-column prop="username" label="用户" width="140" />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.replied" type="success" size="small">已回复</el-tag>
            <el-tag v-else-if="row.isRead === 0" type="danger" size="small">未处理</el-tag>
            <el-tag v-else type="warning" size="small">待回复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="最近回复" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reply || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openReply(row)">回复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page.current"
        :page-size="page.size"
        @current-change="onPage"
      />
    </el-card>

    <el-dialog v-model="replyVisible" :title="`回复 @${replyTarget?.username || ''}`" width="520px">
      <div class="origin">{{ replyTarget?.content }}</div>
      <el-input v-model="replyContent" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="输入回复内容" />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const rows = ref([])
const total = ref(0)
const page = ref({ current: 1, size: 10 })
const loading = ref(false)
const unreadOnly = ref(false)

const replyVisible = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const replying = ref(false)

// 广播通知
const broadcastScope = ref('all')
const broadcastDraft = ref('')
const broadcasting = ref(false)

async function submitBroadcast() {
  if (!broadcastDraft.value.trim()) {
    ElMessage.warning('通知内容不能为空')
    return
  }
  broadcasting.value = true
  try {
    const res = await api.broadcastMessage(broadcastDraft.value.trim(), broadcastScope.value)
    if (res.code === 0 || res.code === 200) {
      ElMessage.success(`已推送给 ${res.data?.sent ?? 0} 位用户`)
      broadcastDraft.value = ''
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally {
    broadcasting.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function reload() {
  loading.value = true
  try {
    const res = await api.getAdminMessages({
      current: page.value.current,
      size: page.value.size,
      unreadOnly: unreadOnly.value || undefined
    })
    if (res.code === 0 || res.code === 200) {
      rows.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value.current = p
  reload()
}

function openReply(row) {
  replyTarget.value = row
  replyContent.value = ''
  replyVisible.value = true
}

async function submitReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replying.value = true
  try {
    const res = await api.replyMessage(replyTarget.value.id, replyContent.value.trim())
    if (res.code === 0 || res.code === 200) {
      ElMessage.success('已回复')
      replyVisible.value = false
      reload()
    } else {
      ElMessage.error(res.message || '回复失败')
    }
  } finally {
    replying.value = false
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.block { margin-bottom: 16px; }
.hd { display: flex; align-items: center; justify-content: space-between; }
.hd-hint { font-size: 12px; color: #909399; }
.scope-group { margin-bottom: 12px; }
.broadcast-input { margin-bottom: 12px; }
.send-row { text-align: right; }
.pager { margin-top: 12px; justify-content: flex-end; }
.origin {
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  color: #606266;
  line-height: 1.6;
}
</style>
