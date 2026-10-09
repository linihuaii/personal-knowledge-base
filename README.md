# 智能知识库管理系统

一个面向个人的本地知识库应用，支持资料导入、来源可追溯的检索问答、学习计划和练习反馈。知识库、会话、计划及文件保存在运行该应用的计算机上；无需账号。默认仅绑定 `127.0.0.1`，不应作为无认证公网服务部署。

## 功能

- 管理知识库和 PDF、DOCX、TXT、Markdown 资料；上传上限为 50 MB。
- 查看解析状态和提取文本，按知识库检索并查看原文来源。
- 使用严格资料模式或补充通用知识模式进行多轮问答。
- 生成、编辑和调整学习计划；生成单选、判断、简答练习并查看作答反馈。
- 分别配置生成模型和嵌入模型，支持 Ollama 与经验证的 OpenAI 兼容接口。

## 环境要求

- Windows 10/11（本项目的干净副本、浏览器主流程在 Windows 验证）；Java 17；无需单独安装 Maven，仓库包含 Maven Wrapper。
- Node.js 20 或更高版本与 npm。CI 使用 Node 20；S08 干净副本验收在 Node 24.21.0 上完成。
- Ollama（可选，使用本地模型时需要）；或一个已配置的 OpenAI 兼容服务及其 API Key。

项目使用 Spring Boot 3.5.6、Maven 3.9.11、Vue 3.5.42、Vite 6.4.3、TypeScript 5.7.3、Naive UI 2.41.0。锁定依赖见 `backend/pom.xml`、`backend/.mvn/wrapper/maven-wrapper.properties` 和 `frontend/package-lock.json`。

## 最短启动流程（Windows PowerShell）

1. 克隆仓库并准备模型。模型 ID 和生成/嵌入配置必须与下方设置一致：

   ```powershell
   git clone <仓库克隆地址>
   cd 个人项目-智能知识库管理
   ollama pull gemma4:e4b
   ollama pull nomic-embed-text-v2-moe:latest
   ```

   确保 Ollama 服务已启动。若使用其他模型，须在应用的模型设置中分别配置生成模型与嵌入模型，并按嵌入模型输出维度设置维度。已有索引与嵌入维度/模型不兼容时需按应用提示重建索引。

2. 在一个 PowerShell 窗口启动后端：

   ```powershell
   cd backend
   $env:GENERATION_PROVIDER = 'OLLAMA'
   $env:GENERATION_BASE_URL = 'http://127.0.0.1:11434'
   $env:GENERATION_MODEL = 'gemma4:e4b'
   $env:GENERATION_MAX_TOKENS = '4096'
   $env:EMBEDDING_PROVIDER = 'OLLAMA'
   $env:OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
   $env:OLLAMA_EMBEDDING_MODEL = 'nomic-embed-text-v2-moe:latest'
   $env:EMBEDDING_DIMENSION = '768'
   .\mvnw.cmd -B spring-boot:run
   ```

   `GENERATION_MAX_TOKENS=4096` 是 S08 Ollama 三题样本验证使用的输出预算；默认值 1200 曾导致该样本的结构化输出截断。较大的资料范围或题目数量仍可能需要调整预算。环境变量只对当前终端会话生效。`.env.example` 是配置项参考文件，Spring Boot 不会自动读取 `.env`。

3. 在第二个 PowerShell 窗口启动前端：

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

   打开 <http://127.0.0.1:5173>。开发服务器把 `/api` 请求代理到 `http://127.0.0.1:8080`。后端健康检查地址为 <http://127.0.0.1:8080/api/v1/health>。

第一次构建需联网下载 Maven 与 npm 依赖。应用数据默认写入 `backend/data/db/` 和 `backend/data/` 下的文件存储目录；均为本地数据，不应提交到 Git。

## 配置公共模型 API

在启动后端前，将真实密钥放入当前进程环境，不要写入仓库文件或日志。例如：

```powershell
$env:OPENAI_API_KEY = '<你的密钥>'
$env:GENERATION_PROVIDER = 'OPENAI_COMPATIBLE'
$env:GENERATION_BASE_URL = '<服务商提供的兼容 API 根地址>'
$env:GENERATION_MODEL = '<服务商提供的生成模型 ID>'
$env:GENERATION_MAX_TOKENS = '4096'
$env:EMBEDDING_PROVIDER = 'OPENAI_COMPATIBLE'
$env:OLLAMA_BASE_URL = '<兼容嵌入 API 的根地址>'
$env:OLLAMA_EMBEDDING_MODEL = '<服务商提供的嵌入模型 ID>'
$env:EMBEDDING_DIMENSION = '<该模型的实际输出维度>'
```

该兼容协议配置只针对已实现的 OpenAI 兼容端点；不同供应商的兼容程度、模型 ID 和嵌入维度需由使用者核实。公共 API 请求可能发送所选资料相关片段、问题、练习题或简答作答。页面会在适用操作前提示；只有在了解并同意后继续。没有配置公共服务时，Ollama 流程不会隐式回退到公共 API。生成与嵌入服务地址按各自配置独立填写。

## 构建与验证

```powershell
cd backend
.\mvnw.cmd -B "-Dtest=PracticeTests,StudyPlanTests,S04DocumentWorkflowTests,ChatRetrievalTests,ModelSettingsTests,ModelGatewayProtocolTests,UnconfirmedPublicEmbeddingRecoveryTests,FileDatabasePersistenceTests" -DforkCount=0 test
cd ..\frontend
npm ci
npm test -- --run
npm run build
npm audit
npm audit --omit=dev
```

S08 在 Windows 干净源码副本中验证后端 40 项、前端 9 项均通过，前端生产构建通过，完整及生产 npm audit 均为零告警。当前 Maven Wrapper 的完整后端验证采用 `-DforkCount=0`，以避开已记录的 Windows Surefire fork 路径问题。真实 Ollama 流程和确定性测试证据分别见 `docs/testing/TEST_REPORT.md`。公共 API 真实连接、公共嵌入及完整公共 RAG 未验证，仍按 S05 批准记录为有限豁免。

GitHub Actions 工作流位于 `.github/workflows/`，在 push 和 pull request 时运行后端测试、前端测试和构建。S09 尚未完成 GitHub 仓库绑定；本地工作区没有配置 Git remote，因此目前没有线上 CI 链接。

## 备份与恢复

关闭前后端进程后，复制整个 `backend/data/` 目录到安全位置，即可备份本地数据库及资料文件。恢复时，将备份目录放回同一相对位置后再启动。不要在服务运行期间只复制 H2 数据库文件；不要把包含个人资料的备份提交或发送给他人。建议定期备份，并先在副本环境确认可恢复。

## 常见问题

- **后端无法连接模型：**确认 Ollama 正在运行、模型已下载，并检查模型设置中的服务地址、模型 ID 和嵌入维度。
- **练习生成报结构错误或截断：**提高 `GENERATION_MAX_TOKENS`（例如 4096），减少单次题目数或缩小资料范围后重试。
- **文档一直处理中或处理失败：**查看后端控制台的错误信息；确认文件属于支持格式、大小不超过 50 MB，再使用页面的重试操作。
- **浏览器页面打不开或提示后端离线：**分别确认前后端进程仍在运行，并打开健康检查地址确认后端状态。
- **端口已被占用：**启动后端前设置 `SERVER_PORT`；前端开发端口可在 `frontend/vite.config.ts` 修改，或关闭占用进程。
- **公共模型请求失败：**确认供应商兼容 API 地址、模型 ID、Key 环境变量名与服务端配置一致，并检查供应商的网络/额度限制。密钥只在本机环境变量中设置。

## 文档与课程材料

- [课程项目总结](docs/delivery/PROJECT_SUMMARY.md)
- [演示脚本](docs/delivery/DEMO_SCRIPT.md)
- [交付和授权说明](docs/delivery/DELIVERY_NOTES.md)
- 需求与验收：[需求规格](docs/requirements/SRS.md)、[需求追踪表](docs/requirements/TRACEABILITY.md)、[测试计划](docs/testing/TEST_PLAN.md)、[测试报告](docs/testing/TEST_REPORT.md)
- 阶段过程：[阶段索引](docs/process/STAGE_STATUS.md)、阶段日志与审核记录见 `docs/process/`

## 许可证

本项目采用 Apache License 2.0，见 [LICENSE](LICENSE)。第三方组件保留其各自版权和许可证；直接依赖及许可证核查范围见 [交付说明](docs/delivery/DELIVERY_NOTES.md)。
