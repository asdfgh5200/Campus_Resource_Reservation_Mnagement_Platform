<template>
  <div class="my-messages">
    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>联系管理员</span>
          <el-tag type="info" size="small">{{ unreadCount }} 条未读</el-tag>
        </div>
      </template>

      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="写下你的问题或建议，管理员会尽快回复"
      />
      <div class="send-row">
        <el-button type="primary" :loading="sending" @click="sendAsk">发送留言</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="hd">
          <span>收到的回复与通知</span>
          <el-button link type="primary" @click="loadInbox">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!inbox.length" description="暂无消息" />
      <ul v-else class="inbox">
        <li
          v-for="m in inbox"
          :key="m.id"
          :class="{ unread: m.isRead === 0 }"
          @click="markRead(m)"
        >
          <div class="meta">
            <el-tag v-if="m.refId" size="small" type="success">回复</el-tag>
            <el-tag v-else size="small" type="warning">通知</el-tag>
            <span class="time">{{ formatTime(m.createdAt) }}</span>
            <el-badge v-if="m.isRead === 0" is-dot class="dot" />
          </div>
          <div class="content">{{ m.content }}</div>
        </li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const draft = ref('')
const sending = ref(false)
const inbox = ref([])
const unreadCount = ref(0)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadInbox() {
  const [mineRes, unreadRes] = await Promise.all([api.getMyMessages(), api.getMyUnreadCount()])
  if (mineRes.code === 0 || mineRes.code === 200) inbox.value = mineRes.data || []
  if (unreadRes.code === 0 || unreadRes.code === 200) unreadCount.value = unreadRes.data?.count || 0
}

async function sendAsk() {
  if (!draft.value.trim()) {
    ElMessage.warning('留言内容不能为空')
    return
  }
  sending.value = true
  try {
    const res = await api.askAdmin(draft.value.trim())
    if (res.code === 0 || res.code === 200) {
      ElMessage.success('留言已发送，管理员回复会显示在下方面板')
      draft.value = ''
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally {
    sending.value = false
  }
}

async function markRead(m) {
  if (m.isRead === 1) return
  const res = await api.markMessageRead(m.id)
  if (res.code === 0 || res.code === 200) {
    m.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    // 通知左侧「消息中心」徽标同步未读数
    window.dispatchEvent(new Event('campus-unread-changed'))
  }
}

onMounted(loadInbox)
</script>

<style scoped lang="scss">
.my-messages { max-width: 900px; margin: 0 auto; }
.block { margin-bottom: 16px; }
.hd { display: flex; align-items: center; justify-content: space-between; }
.send-row { margin-top: 12px; text-align: right; }
.inbox { list-style: none; padding: 0; margin: 0; }
.inbox li {
  padding: 12px;
  border-bottom: 1px dashed #ebeef5;
  cursor: pointer;
  transition: background 0.2s;
}
.inbox li:hover { background: #f5f7fa; }
.inbox li.unread .content { color: #303133; font-weight: 500; }
.meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #909399; }
.time { font-variant-numeric: tabular-nums; }
.content { margin-top: 6px; color: #606266; line-height: 1.6; }
</style>
