# Minicat

数据库结构与数据比对同步工具

## 项目结构

```
minicat/
├── backend/        # Spring Boot 后端 (Java 21)
├── frontend/       # Vue 3 + Vuetify 前端
├── docs/           # 项目文档
└── data/           # 数据存储目录
```

## 技术栈

### 后端
- Java 21
- Spring Boot 3.x
- Spring Data JPA
- SQLite (任务存储)
- MySQL/PostgreSQL 驱动

### 前端
- Vue 3
- Vuetify 3
- Vite
- Vue Router
- Axios

## 快速开始

### 前置要求
- Java 21+
- Node.js 18+
- Maven 3.8+

### 启动开发环境

```bash
# 安装前端依赖
cd frontend
npm install

# 启动后端（在项目根目录）
cd backend
mvn spring-boot:run

# 启动前端（新终端）
cd frontend
npm run dev
```

或者使用便捷脚本：

```bash
# macOS/Linux
./start-dev.sh

# Windows
start-dev.bat
```

## 开发访问地址

- 前端：http://localhost:5173
- 后端 API：http://localhost:8080
- API 文档：http://localhost:8080/swagger-ui.html

## 核心功能

1. **数据库连接管理** - 支持 MySQL、PostgreSQL
2. **表结构比对** - DDL 层差异检测
3. **数据内容比对** - 行级数据差异
4. **同步任务管理** - 实时进度追踪（SSE）
5. **历史记录** - 任务日志与结果查询

## 许可证

MIT
