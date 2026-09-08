<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">🤖</div>
        <h2>AI Agent 智能助手</h2>
        <p>支持 RAG 知识库检索、工具调用（Shell / 文件读写）和对话记忆</p>
        <div class="examples">
          <button v-for="ex in examples" :key="ex" @click="$emit('send', ex)" class="example-btn">
            {{ ex }}
          </button>
        </div>
      </div>

      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="message"
        :class="msg.role"
      >
        <div class="avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="bubble">
          <div
            class="markdown-body"
            v-html="renderMarkdown(msg.content)"
          ></div>
          <span v-if="msg.streaming" class="cursor-blink">▊</span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-wrapper">
        <textarea
          ref="inputRef"
          v-model="inputText"
          placeholder="输入消息，Shift+Enter 换行，Enter 发送..."
          rows="1"
          @keydown="handleKeydown"
          @input="autoResize"
          :disabled="loading"
        ></textarea>
        <button
          class="btn-send"
          @click="send"
          :disabled="loading || !inputText.trim()"
        >
          <svg v-if="!loading" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
          <span v-else class="spinner"></span>
        </button>
      </div>
      <div class="input-hints">
        <span v-if="enableRag" class="hint-tag rag">RAG</span>
        <span v-if="enableTools" class="hint-tag tools">Tools</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  enableRag: { type: Boolean, default: true },
  enableTools: { type: Boolean, default: true }
})

const emit = defineEmits(['send'])

const inputText = ref('')
const inputRef = ref(null)
const messagesRef = ref(null)

const examples = [
  '帮我列出工作目录的文件',
  '创建一个 hello.txt 文件，内容为 Hello World',
  '介绍一下你自己'
]

marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(text) {
  if (!text) return ''
  const rawHtml = marked.parse(text)
  return DOMPurify.sanitize(rawHtml)
}

function send() {
  const text = inputText.value.trim()
  if (!text || props.loading) return
  emit('send', text)
  inputText.value = ''
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
  })
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function autoResize(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}

watch(() => props.messages, () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}, { deep: true })
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}

.welcome {
  text-align: center;
  padding: 80px 40px;
  max-width: 600px;
  margin: 0 auto;
}

.welcome-icon { font-size: 48px; margin-bottom: 16px; }
.welcome h2 { font-size: 24px; margin-bottom: 8px; }
.welcome p { color: #71717a; font-size: 14px; margin-bottom: 24px; }

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.example-btn {
  padding: 8px 16px;
  background: #27272a;
  border: 1px solid #3f3f46;
  border-radius: 20px;
  color: #a1a1aa;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.example-btn:hover {
  background: #3f3f46;
  color: #e4e4e7;
  border-color: #6366f1;
}

.message {
  display: flex;
  gap: 12px;
  padding: 12px 24px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.message.user { flex-direction: row-reverse; }

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  background: #27272a;
}

.message.user .avatar { background: #6366f1; }

.bubble {
  padding: 10px 16px;
  border-radius: 12px;
  max-width: 85%;
  font-size: 14px;
  line-height: 1.6;
}

.message.user .bubble {
  background: #6366f1;
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  background: #27272a;
  border-bottom-left-radius: 4px;
}

.cursor-blink {
  animation: blink 1s step-end infinite;
  color: #6366f1;
}

@keyframes blink {
  50% { opacity: 0; }
}

.input-area {
  padding: 16px 24px 24px;
  border-top: 1px solid #27272a;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 800px;
  margin: 0 auto;
  background: #27272a;
  border: 1px solid #3f3f46;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: #6366f1; }

textarea {
  flex: 1;
  background: none;
  border: none;
  color: #e4e4e7;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  line-height: 1.5;
  max-height: 150px;
}

.btn-send {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #6366f1;
  border: none;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s;
}
.btn-send:hover:not(:disabled) { background: #4f46e5; }
.btn-send:disabled { opacity: 0.4; cursor: not-allowed; }

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.input-hints {
  display: flex;
  gap: 6px;
  max-width: 800px;
  margin: 8px auto 0;
}

.hint-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}
.hint-tag.rag { background: #064e3b; color: #6ee7b7; }
.hint-tag.tools { background: #451a03; color: #fbbf24; }
</style>
