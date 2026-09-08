<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <h1 class="logo">🤖 AI Agent</h1>
        <button class="btn-new" @click="handleNewChat">+ 新对话</button>
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="selectConversation(conv.id)"
        >
          <span class="conv-title">{{ conv.title }}</span>
          <button class="btn-delete" @click.stop="handleDelete(conv.id)" title="删除">×</button>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="toggle-group">
          <label class="toggle">
            <input type="checkbox" v-model="enableRag" />
            <span>RAG 知识库</span>
          </label>
          <label class="toggle">
            <input type="checkbox" v-model="enableTools" />
            <span>工具调用</span>
          </label>
        </div>
        <button class="btn-upload" @click="showKnowledge = !showKnowledge">
          📚 知识库管理
        </button>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="main-area">
      <ChatWindow
        :messages="messages"
        :loading="loading"
        :enable-rag="enableRag"
        :enable-tools="enableTools"
        @send="handleSend"
      />
    </main>

    <!-- 知识库面板 -->
    <div v-if="showKnowledge" class="knowledge-panel">
      <div class="panel-header">
        <h3>知识库管理</h3>
        <button @click="showKnowledge = false">×</button>
      </div>
      <div class="panel-body">
        <div class="upload-section">
          <input
            ref="fileInputRef"
            type="file"
            accept=".txt,.md,.csv"
            @change="onFileSelected"
            hidden
          />
          <div class="upload-area" @click="triggerFileSelect">
            <span v-if="!selectedFile">📄 点击选择文档 (.txt / .md / .csv)</span>
            <span v-else class="selected-file">📎 {{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})</span>
          </div>
          <div class="upload-actions">
            <button class="btn-select" @click="triggerFileSelect">选择文件</button>
            <button
              class="btn-confirm-upload"
              @click="handleUpload"
              :disabled="!selectedFile || uploading"
            >
              {{ uploading ? '上传中...' : '确认上传' }}
            </button>
          </div>
          <p v-if="uploadMessage" class="upload-message" :class="uploadStatus">{{ uploadMessage }}</p>
        </div>
        <div class="doc-list">
          <div v-for="doc in knowledgeDocs" :key="doc.id" class="doc-item">
            <div class="doc-info">
              <span>{{ doc.filename }}</span>
              <small>{{ doc.chunkCount }} 个分块 · {{ formatDate(doc.createdAt) }}</small>
            </div>
            <button class="btn-doc-delete" @click="handleDeleteDoc(doc.id)" title="删除">×</button>
          </div>
          <p v-if="knowledgeDocs.length === 0" class="empty-hint">暂无文档，请选择文件后点击「确认上传」</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ChatWindow from './components/ChatWindow.vue'
import {
  fetchConversations, createConversation, fetchMessages,
  deleteConversation, chatStream, uploadKnowledge, fetchKnowledgeDocs, deleteKnowledgeDoc
} from './api/chat.js'

const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const loading = ref(false)
const enableRag = ref(true)
const enableTools = ref(true)
const showKnowledge = ref(false)
const knowledgeDocs = ref([])
const selectedFile = ref(null)
const fileInputRef = ref(null)
const uploading = ref(false)
const uploadMessage = ref('')
const uploadStatus = ref('')
let streamController = null

onMounted(async () => {
  await loadConversations()
  await loadKnowledgeDocs()
})

async function loadConversations() {
  try {
    conversations.value = await fetchConversations()
  } catch (e) {
    console.error('加载对话列表失败:', e)
  }
}

async function loadKnowledgeDocs() {
  try {
    knowledgeDocs.value = await fetchKnowledgeDocs()
  } catch (e) {
    console.error('加载知识库文档失败:', e)
  }
}

async function handleNewChat() {
  const conv = await createConversation('新对话')
  conversations.value.unshift(conv)
  currentConversationId.value = conv.id
  messages.value = []
}

async function selectConversation(id) {
  currentConversationId.value = id
  const msgs = await fetchMessages(id)
  messages.value = msgs
    .filter(m => m.role === 'user' || m.role === 'assistant')
    .map(m => ({ role: m.role, content: m.content }))
}

async function handleDelete(id) {
  await deleteConversation(id)
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (currentConversationId.value === id) {
    currentConversationId.value = null
    messages.value = []
  }
}

function handleSend(text) {
  if (loading.value || !text.trim()) return

  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  loading.value = true

  const assistantIdx = messages.value.length - 1

  streamController = chatStream({
    conversationId: currentConversationId.value,
    message: text,
    enableRag: enableRag.value,
    enableTools: enableTools.value
  }, {
    onConversationId(id) {
      currentConversationId.value = Number(id)
      loadConversations()
    },
    onContent(chunk) {
      messages.value[assistantIdx].content += chunk
    },
    onToolCall(name) {
      messages.value[assistantIdx].content += `\n\n🔧 *正在调用工具: ${name}*\n`
    },
    onToolResult(result) {
      messages.value[assistantIdx].content += `\n> ${result.substring(0, 200)}...\n\n`
    },
    onDone() {
      messages.value[assistantIdx].streaming = false
      loading.value = false
    },
    onError(err) {
      messages.value[assistantIdx].content += `\n\n❌ 错误: ${err}`
      messages.value[assistantIdx].streaming = false
      loading.value = false
    }
  })
}

async function handleUpload() {
  if (!selectedFile.value || uploading.value) return

  uploading.value = true
  uploadMessage.value = ''
  uploadStatus.value = ''

  try {
    await uploadKnowledge(selectedFile.value)
    uploadMessage.value = `「${selectedFile.value.name}」上传成功`
    uploadStatus.value = 'success'
    selectedFile.value = null
    if (fileInputRef.value) fileInputRef.value.value = ''
    await loadKnowledgeDocs()
  } catch (err) {
    uploadMessage.value = err.message || '上传失败'
    uploadStatus.value = 'error'
  } finally {
    uploading.value = false
  }
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function onFileSelected(e) {
  const file = e.target.files[0]
  selectedFile.value = file || null
  uploadMessage.value = ''
  uploadStatus.value = ''
}

async function handleDeleteDoc(id) {
  await deleteKnowledgeDoc(id)
  await loadKnowledgeDocs()
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  return (bytes / 1024).toFixed(1) + ' KB'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 280px;
  background: #18181b;
  border-right: 1px solid #27272a;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 20px 16px 12px;
}

.logo {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 12px;
}

.btn-new {
  width: 100%;
  padding: 10px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s;
}
.btn-new:hover { background: #4f46e5; }

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}
.conv-item:hover { background: #27272a; }
.conv-item.active { background: #3f3f46; }

.conv-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-delete {
  background: none;
  border: none;
  color: #71717a;
  font-size: 18px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.conv-item:hover .btn-delete { opacity: 1; }
.btn-delete:hover { color: #ef4444; }

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid #27272a;
}

.toggle-group {
  margin-bottom: 10px;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #a1a1aa;
  cursor: pointer;
  margin-bottom: 6px;
}
.toggle input { accent-color: #6366f1; }

.btn-upload {
  width: 100%;
  padding: 8px;
  background: #27272a;
  color: #a1a1aa;
  border: 1px solid #3f3f46;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.btn-upload:hover { background: #3f3f46; }

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.knowledge-panel {
  position: fixed;
  right: 20px;
  top: 20px;
  width: 360px;
  max-height: 80vh;
  background: #18181b;
  border: 1px solid #27272a;
  border-radius: 12px;
  z-index: 100;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #27272a;
}
.panel-header h3 { font-size: 15px; }
.panel-header button {
  background: none;
  border: none;
  color: #71717a;
  font-size: 20px;
  cursor: pointer;
}

.panel-body { padding: 16px 20px; overflow-y: auto; }

.upload-area {
  display: block;
  padding: 24px;
  border: 2px dashed #3f3f46;
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  font-size: 13px;
  color: #a1a1aa;
  margin-bottom: 12px;
  transition: border-color 0.2s;
}
.upload-area:hover { border-color: #6366f1; }

.selected-file { color: #e4e4e7; }

.upload-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.btn-select, .btn-confirm-upload {
  flex: 1;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  border: none;
}

.btn-select {
  background: #27272a;
  color: #a1a1aa;
  border: 1px solid #3f3f46;
}
.btn-select:hover { background: #3f3f46; }

.btn-confirm-upload {
  background: #6366f1;
  color: white;
  font-weight: 500;
}
.btn-confirm-upload:hover:not(:disabled) { background: #4f46e5; }
.btn-confirm-upload:disabled { opacity: 0.5; cursor: not-allowed; }

.upload-message {
  font-size: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 6px;
}
.upload-message.success {
  background: #064e3b;
  color: #6ee7b7;
}
.upload-message.error {
  background: #450a0a;
  color: #fca5a5;
}

.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #27272a;
  font-size: 13px;
}

.doc-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.doc-info span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-info small { color: #71717a; }

.btn-doc-delete {
  background: none;
  border: none;
  color: #71717a;
  font-size: 18px;
  cursor: pointer;
  padding: 0 4px;
  flex-shrink: 0;
}
.btn-doc-delete:hover { color: #ef4444; }

.empty-hint {
  text-align: center;
  color: #71717a;
  font-size: 13px;
  padding: 20px 0;
}
</style>
