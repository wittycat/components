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
