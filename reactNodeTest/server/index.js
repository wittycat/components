const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = 3001;
const DATA_FILE = path.join(__dirname, '..', 'data.properties');

app.use(cors());
app.use(express.json());

// ─── 工具函数：读写 data.properties ────────────────────────────────────────

/** 读取 properties 文件，解析为 [{id, name, email}, ...] */
function readAll() {
  if (!fs.existsSync(DATA_FILE)) return [];
  const lines = fs.readFileSync(DATA_FILE, 'utf-8').split('\n');
  const map = {};
  lines.forEach(line => {
    line = line.trim();
    if (!line || line.startsWith('#')) return;
    const eqIdx = line.indexOf('=');
    if (eqIdx === -1) return;
    const key = line.substring(0, eqIdx).trim();
    const val = line.substring(eqIdx + 1).trim();
    const dotIdx = key.indexOf('.');
    if (dotIdx === -1) return;
    const id = key.substring(0, dotIdx);
    const field = key.substring(dotIdx + 1);
    if (!map[id]) map[id] = { id: Number(id) };
    map[id][field] = val;
  });
  return Object.values(map).sort((a, b) => a.id - b.id);
}

/** 将数据写回 properties 文件 */
function writeAll(items) {
  const lines = [];
  items.forEach(item => {
    for (const field of ['name', 'email']) {
      if (item[field] !== undefined) {
        lines.push(`${item.id}.${field}=${item[field]}`);
      }
    }
  });
  fs.writeFileSync(DATA_FILE, lines.join('\n') + '\n', 'utf-8');
}

/** 生成新 ID */
function nextId(items) {
  if (items.length === 0) return 1;
  return Math.max(...items.map(i => i.id)) + 1;
}

// ─── REST API ────────────────────────────────────────────────────────────

// 获取全部
app.get('/api/items', (_req, res) => {
  res.json(readAll());
});

// 获取单个
app.get('/api/items/:id', (req, res) => {
  const item = readAll().find(i => i.id === Number(req.params.id));
  if (!item) return res.status(404).json({ error: 'Not found' });
  res.json(item);
});

// 新增
app.post('/api/items', (req, res) => {
  const { name, email } = req.body;
  if (!name || !email) return res.status(400).json({ error: 'name and email required' });
  const items = readAll();
  const item = { id: nextId(items), name, email };
  items.push(item);
  writeAll(items);
  res.status(201).json(item);
});

// 修改
app.put('/api/items/:id', (req, res) => {
  const items = readAll();
  const idx = items.findIndex(i => i.id === Number(req.params.id));
  if (idx === -1) return res.status(404).json({ error: 'Not found' });
  const { name, email } = req.body;
  if (name !== undefined) items[idx].name = name;
  if (email !== undefined) items[idx].email = email;
  writeAll(items);
  res.json(items[idx]);
});

// 删除
app.delete('/api/items/:id', (req, res) => {
  let items = readAll();
  const exists = items.some(i => i.id === Number(req.params.id));
  if (!exists) return res.status(404).json({ error: 'Not found' });
  items = items.filter(i => i.id !== Number(req.params.id));
  writeAll(items);
  res.json({ success: true });
});

app.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}`);
});
