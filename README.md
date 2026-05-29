# PRism — AI PR Review Assistant

PRism 是一个 AI 驱动的 Pull Request 代码审查工具。用户输入 GitHub PR 链接，系统自动获取代码变更并调用 AI 进行智能分析，输出变更摘要、风险代码识别和 Review 建议。

## 技术栈

- 后端：Java 21 + Spring Boot 3 + Maven
- 前端：Vue 3 + Vite + TypeScript
- AI：阿里云百炼 DashScope API（qwen-max）
- 数据库：MySQL 8 + MyBatis-Plus

## 项目结构

```
PRism/
├── backend/   # Spring Boot 后端
└── frontend/  # Vue 3 前端
```

## 快速启动

### 后端

```bash
cd backend
# 配置环境变量
export DASHSCOPE_API_KEY=your_key
export GITHUB_TOKEN=your_token
export DB_USERNAME=root
export DB_PASSWORD=your_password
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 核心接口

- `POST /api/review` — 触发 PR 分析
- `GET /api/review/{id}` — 查询分析结果
- `GET /api/review/history` — 查看历史记录
- `POST /api/auth/register` — 用户注册
- `POST /api/auth/login` — 用户登录

## 接口文档

启动后访问 http://localhost:8080/doc.html
