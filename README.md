# PRism — AI PR Review Assistant

PRism 是一个 AI 驱动的 Pull Request 代码审查工具。用户输入 GitHub PR 链接，系统自动获取代码变更并调用 AI 进行智能分析，输出变更摘要、风险代码识别和 Review 建议，帮助开发者提升 Review 效率与质量。

## 技术栈

- 后端：Java 21 + Spring Boot 3 + Maven
- 前端：Vue 3 + Vite + TypeScript
- AI：阿里云百炼 DashScope API（qwen-max，通过 Spring AI 兼容 OpenAI 接口调用）
- 数据库：MySQL 8 + MyBatis-Plus
- 认证：Spring Security + JWT
- 接口文档：knife4j（OpenAPI 3）
- GitHub 数据：GitHub REST API v3

## 项目结构

```
PRism/
├── backend/   # Spring Boot 后端
└── frontend/  # Vue 3 前端
```

## 核心功能

- **PR 变更总结**：自动提炼 PR 的变更目的与主要改动
- **风险代码识别**：按 CRITICAL / HIGH / MEDIUM / LOW 四级标注风险，给出文件、行号、描述
- **Review 建议生成**：针对变更给出可操作的改进建议
- **置信度评分**：每条风险附带置信度（HIGH / MEDIUM / LOW），辅助识别误报
- **风险反馈机制**：用户可对每条风险标记"误报 / 确认"，用于统计误报率
- **异步分析**：提交后立即返回，前端轮询查看进度，避免大 PR 长时间阻塞
- **历史记录**：分析结果持久化，支持按用户分页查询

## 快速启动

### 后端

```bash
cd backend
# 初始化数据库
mysql -u root -p < sql/init.sql
# 配置环境变量
export DASHSCOPE_API_KEY=your_key
export GITHUB_TOKEN=your_token
export DB_USERNAME=root
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret_at_least_32_bytes
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录，返回 JWT |
| POST | /api/review | 触发 PR 分析（异步，立即返回 reviewId） |
| GET | /api/review/{id} | 查询分析进度与结果（前端轮询） |
| GET | /api/review/history | 分页查询当前用户的评审历史 |
| POST | /api/review/{reviewId}/feedback | 提交风险反馈（误报/确认） |
| GET | /api/review/{reviewId}/feedback | 查询风险反馈统计 |

完整接口文档：启动后访问 http://localhost:8080/doc.html

## 设计思路

### 一、模型选择

PRism 选用阿里云百炼平台的 **qwen-max** 作为代码审查模型，主要基于以下考量：

| 维度 | 说明 |
|------|------|
| 代码理解能力 | qwen-max 是通义千问的旗舰模型，对主流编程语言和代码语义有较强理解，能识别逻辑错误、安全隐患等深层问题 |
| 访问稳定性 | 国内直连，无需代理，延迟低，相比 GPT-4 等海外模型在国内网络环境下更稳定可靠 |
| 接口兼容性 | DashScope 提供 OpenAI 兼容接口，通过 Spring AI 的 `ChatClient` 调用，后续若要切换到 GPT-4、Claude 等模型，仅需调整配置，无需改动业务代码 |
| 成本与速度 | 相比 GPT-4，qwen-max 在保证分析质量的同时，单次调用成本更低、响应更快，适合实时分析场景 |

**为何不直接用 GPT-4 / Claude**：这两者代码能力同样出色，但国内访问需代理、延迟高、成本更高。考虑到本工具面向国内开发者、需要稳定的实时体验，qwen-max 是更均衡的选择。得益于 OpenAI 兼容层，模型并非强绑定——可按部署环境灵活替换。

### 二、上下文获取策略

代码审查的准确性高度依赖上下文的完整性。仅凭 diff 片段容易产生脱离上下文的误判，因此 PRism 采用**多维度上下文聚合**策略：

1. **PR 基本信息**：标题、描述，理解变更目的
2. **Commit 历史**：拉取 PR 所有 commit message，理解开发者意图与变更历程
3. **代码 Diff**：核心变更内容，是分析的主体
4. **修改文件完整内容**：不止看 diff，还获取被修改文件的完整源码，让模型理解函数所在的类结构、调用关系，减少"只见树木不见森林"的误报
5. **已有评论**：拉取 PR 的 review 评论，避免重复指出已被讨论的问题

**Token 控制与裁剪策略**（避免超出模型上下文窗口）：

- diff 内容上限约 **8000 tokens**，按文件顺序累加，超限即停止
- 文件完整内容上限约 **3000 tokens**，最多取 **5 个文件**
- 文件按**修改行数降序**排序，优先纳入改动最大的核心文件
- 自动跳过非代码文件（.md / .json / .xml / .lock / 图片等），把有限的上下文预算留给真正需要审查的代码
- 超过单项预算时按比例截断并标注，保证 prompt 结构完整

这种"分层 + 优先级裁剪"的策略，在有限的 token 预算内最大化保留高价值上下文，兼顾分析准确性与响应速度。

### 三、误报与漏报控制

- **置信度评分**：要求模型为每条风险评估置信度（HIGH / MEDIUM / LOW）。上下文越完整，置信度越高；不确定的风险标注较低置信度，便于使用者快速过滤可能的误报
- **上下文增强降误报**：通过提供完整文件内容，减少因上下文缺失导致的误判
- **避免重复反馈**：将已有评论纳入上下文，提示模型不要重复指出已讨论的问题
- **用户反馈闭环**：提供反馈接口，用户可标记风险为"误报"或"确认"，系统统计误报/确认次数，为后续评估和改进分析质量提供数据支撑

### 四、响应速度优化

PR 分析涉及多次 GitHub API 调用和 AI 推理，耗时较长。PRism 采用**异步处理**架构：

- `POST /api/review` 先落库一条 `pending` 记录并立即返回 reviewId（实测 ~80ms），不阻塞 HTTP 请求线程
- 分析任务提交到独立线程池后台执行，状态流转：`pending → processing → completed / error`
- 前端通过轮询 `GET /api/review/{id}` 获取进度与结果

这样既避免了大 PR 分析导致的请求超时，又能给用户实时的进度反馈。

### 五、未来扩展方向

- **多代码托管平台**：当前支持 GitHub，可扩展至 GitLab、Gitee、Bitbucket，通过抽象统一的 PR 数据获取接口实现
- **本地 diff 文件分析**：支持上传本地 diff / patch 文件，无需依赖在线 PR，适配内网或私有仓库场景
- **多模型可选**：基于 OpenAI 兼容层，开放模型选择，让用户按需在 qwen / GPT / Claude 等模型间切换
- **Webhook 自动审查**：监听 PR 创建/更新事件，自动触发分析并将结果回写为 PR 评论
- **规则与 AI 结合**：引入静态分析规则（如 SpotBugs、ESLint）作为 AI 分析的补充，进一步降低漏报
- **误报率统计看板**：基于已有的反馈数据，构建准确率/误报率统计，持续优化 prompt 和模型选择

## Demo 视频

> 待补充（项目完成后上传至 bilibili / 云盘，链接放在此处）

## 第三方依赖说明

| 依赖 | 用途 |
|------|------|
| Spring Boot 3 | 后端基础框架 |
| Spring AI | 调用 AI 模型（OpenAI 兼容） |
| Spring Security | 认证与授权 |
| MyBatis-Plus | ORM 与数据持久化 |
| JJWT | JWT 生成与校验 |
| knife4j | OpenAPI 接口文档 |
| Vue 3 / Vite / Ant Design Vue | 前端框架与 UI 组件 |

PR 变更总结、风险识别、置信度评分、上下文聚合、异步分析、反馈机制等核心逻辑均为本项目原创实现。

