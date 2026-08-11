# reactNodeTest — CRUD Demo

React + Node.js 增删改查示例，使用 `data.properties` 文件作为数据存储。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 18 + Vite |
| 后端 | Express (Node.js) |
| 数据 | `data.properties` 文件读写 |

## 项目结构

```
reactNodeTest/
├── data.properties          # 数据存储文件
├── server/                  # Express 后端
│   └── index.js             # CRUD API 服务
└── client/                  # React 前端
    └── src/
        └── App.jsx          # 增删改查界面
```

## 快速启动

```bash
# 1. 安装依赖
cd /Users/chenxun/IdeaProjects/github/components/reactNodeTest
cd reactNodeTest/server && npm install
cd reactNodeTest/client && npm install

# 2. 启动后端（端口 3001）
cd reactNodeTest/server && npm start

# 3. 启动前端（端口 5173）
cd reactNodeTest/client && npm run dev
```

浏览器访问 `http://localhost:5173`

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/items` | 查询全部 |
| GET | `/api/items/:id` | 查询单个 |
| POST | `/api/items` | 新增 |
| PUT | `/api/items/:id` | 修改 |
| DELETE | `/api/items/:id` | 删除 |

## data.properties 格式

```properties
# <id>.<field>=<value>
1.name=John Doe
1.email=john@example.com
```

## FAQ

### npm start 入口是哪个文件？

`package.json` 里的 `scripts.start` 定义了入口：

```json
"scripts": {
  "start": "node index.js"
}
```

所以 `npm start` → 执行 `node index.js` → 入口文件是 **`server/index.js`**。

### npm run dev 执行链路？

```
npm run dev
  │
  ▼  package.json scripts.dev → "vite"
vite
  │
  ├─ 读取 vite.config.js    ← 插件（React）、代理（/api → 3001）
  ├─ 读取 index.html        ← 入口页面，里面引用 /src/main.jsx
  │
  ▼  加载 /src/main.jsx
main.jsx
  │
  ├─ import App from './App'
  ├─ import './App.css'
  └─ ReactDOM.createRoot(document.getElementById('root')).render(<App />)
  │
  ▼  渲染 App.jsx
App.jsx  ← CRUD 界面
```

| 步骤 | 文件 | 作用 |
|------|------|------|
| 1 | `package.json` | `scripts.dev` 定义 `"vite"` |
| 2 | `vite.config.js` | Vite 配置（React 插件 + API 代理） |
| 3 | `index.html` | HTML 入口，引用 `main.jsx` |
| 4 | `src/main.jsx` | JS 入口，挂载 React 到 `#root` |
| 5 | `src/App.jsx` | 主组件，增删改查页面 |

### React 和 Node.js 为什么长得像？

因为**它们都是 JavaScript**，只是运行在不同平台：

| | React（前端） | Node.js（后端） |
|---|---|---|
| 运行环境 | 浏览器 | 服务器 |
| 语言 | JavaScript + JSX | JavaScript |
| 操控对象 | HTML 页面（DOM） | 文件、网络、数据库 |

语法完全一样（变量声明、函数、数组、对象、Promise……），只是调用的 API 不同：

- React 用 `useState`、`useEffect`、`fetch`、`document.getElementById`……
- Node.js 用 `fs.readFileSync`、`express()`、`app.get`、`res.json`……

> JavaScript 是语言，React 和 Node.js 是两个不同的"运行平台"。就像 Java 既能写 Android 也能写后端，语言一样，用的库不同。

### React 可以直接调用 SpringMVC 接口吗？Node.js 层意义是什么？

**可以直接调用，不需要 Node.js。**

```
浏览器（React）  ──HTTP──►  SpringMVC  ──►  数据库
```

React 只需要发 HTTP 请求，不管后端是 Java、Python、Go 还是 Node.js，对浏览器来说都是一样的。

| 场景 | 是否需要 Node.js |
|------|-----------------|
| 后端已有 SpringMVC 提供完整 API | ❌ React 直接调 SpringMVC |
| 需要聚合多个后端接口 | ⚠️ 可选，用 Node.js 做 BFF（Backend For Frontend） |
| 需要服务端渲染（SSR）、SEO 优化 | ✅ Next.js（基于 Node.js） |
| 需要 WebSocket 实时推送 | ⚠️ 可选，Node.js 做中转 |

常见的两种架构：

```
① 直连（大多数项目够用）
   React ──► SpringMVC ──► MySQL

② BFF 中间层（大厂常见）
   React ──► Node.js (BFF) ──► SpringMVC ──► MySQL
                     │
                     └─► 其他微服务 API
```

> 有 SpringMVC 后端就不需要 Node.js 层。Node.js 层只在需要聚合接口、SSR 或实时推送时才有价值，普通项目 React 直连 SpringMVC 就够了。
