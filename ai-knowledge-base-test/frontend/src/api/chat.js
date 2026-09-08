const BASE_URL = '/api'

export async function fetchConversations() {
  const res = await fetch(`${BASE_URL}/conversations`)
  return res.json()
}

export async function createConversation(title) {
  const res = await fetch(`${BASE_URL}/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  })
  return res.json()
}

export async function fetchMessages(conversationId) {
  const res = await fetch(`${BASE_URL}/conversations/${conversationId}/messages`)
  return res.json()
}

export async function deleteConversation(id) {
  await fetch(`${BASE_URL}/conversations/${id}`, { method: 'DELETE' })
}

export async function uploadKnowledge(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE_URL}/knowledge/upload`, {
    method: 'POST',
    body: formData
  })
  const data = await res.json()
  if (!res.ok) {
    throw new Error(data.error || `上传失败 (HTTP ${res.status})`)
  }
  return data
}

export async function fetchKnowledgeDocs() {
  const res = await fetch(`${BASE_URL}/knowledge/documents`)
  return res.json()
}

export async function deleteKnowledgeDoc(id) {
  await fetch(`${BASE_URL}/knowledge/documents/${id}`, { method: 'DELETE' })
}

/**
 * SSE 流式对话
 */
export function chatStream(request, callbacks) {
  const controller = new AbortController()

  fetch(`${BASE_URL}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: controller.signal
  }).then(async (response) => {
    if (!response.ok) {
      callbacks.onError?.(`HTTP ${response.status}`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (!data) continue
          try {
            const event = JSON.parse(data)
            handleEvent(event, callbacks)
          } catch (e) {
            // ignore parse errors
          }
        }
      }
    }
    callbacks.onDone?.()
  }).catch(err => {
    if (err.name !== 'AbortError') {
      callbacks.onError?.(err.message)
    }
  })

  return controller
}

function handleEvent(event, callbacks) {
  switch (event.type) {
    case 'CONTENT':
      callbacks.onContent?.(event.content || '')
      break
    case 'CONVERSATION_CREATED':
      callbacks.onConversationId?.(event.content)
      break
    case 'TOOL_CALL':
      callbacks.onToolCall?.(event.content)
      break
    case 'TOOL_RESULT':
      callbacks.onToolResult?.(event.content)
      break
    case 'DONE':
      callbacks.onDone?.()
      break
    case 'ERROR':
      callbacks.onError?.(event.content)
      break
  }
}
