# 智能知识库管理系统

一个面向个人学习的本地知识库应用：导入资料，进行有来源的检索问答，制定学习计划，再通过练习与反馈巩固知识。无需注册或登录，资料和业务数据保存在本机。

## 功能特性

- **知识库与文档：**创建、编辑和删除知识库；上传、筛选、预览、下载、删除文档，并查看处理状态和重试失败任务。支持文本型 PDF、DOCX、TXT、Markdown，单文件上限 50 MB。
- **检索问答：**知识库范围内的多轮对话，支持严格资料模式和补充通用知识模式；回答以流式方式显示，并可打开引用来源。
- **学习计划：**根据目标、基础、截止日期和每日时长生成计划，支持编辑、排序、完成标记、进度查看、重新规划和知识点探索。
- **智能练习：**按资料范围、难度、题数和题型生成单选、判断、简答题；提交后查看评分、解析、来源和练习历史。简答评分属于模型辅助评价。
- **模型配置：**生成模型和嵌入模型独立配置与测试，支持 Ollama 和已实现协议的 OpenAI 兼容服务。

应用默认只监听 `127.0.0.1`，没有账号与访问控制，不适合直接部署到公网。扫描 PDF 的 OCR、旧版 `.doc`、多用户协作和云端托管不在当前范围内。模型生成内容可能有误，重要结论请核对来源。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Naive UI |
| 后端 | Java 17、Spring Boot 3.5.6、Maven Wrapper |
| 存储 | H2 文件数据库、Flyway、本地文件存储与单机向量索引 |
| 文档解析 | Apache Tika、PDFBox |
| 接口 | REST JSON、SSE 流式问答 |

具体依赖版本见 [`backend/pom.xml`](backend/pom.xml) 和 [`frontend/package-lock.json`](frontend/package-lock.json)。

## 快速开始

以下步骤面向 **Windows 10/11 + PowerShell**，这是已进行干净副本部署和浏览器流程验证的环境。其他系统尚无同等实机验证记录。

### 前置条件

- JDK 17：`java -version` 显示 17，并将 `JAVA_HOME` 指向该 JDK。
- Node.js 20+ 和 npm：可用 `node --version`、`npm --version` 检查。
- Ollama：用于本地生成和嵌入。首次构建和拉取模型需要联网及足够磁盘空间。仓库自带 Maven Wrapper，无需单独安装 Maven。

### 1. 获取源码并准备模型

从 [GitHub 仓库](https://github.com/linihuaii/personal-knowledge-base)获取源码并进入项目根目录：

```powershell
git clone https://github.com/linihuaii/personal-knowledge-base.git
cd personal-knowledge-base
```

```powershell
ollama pull gemma4:e4b
ollama pull nomic-embed-text-v2-moe:latest
ollama list
```

确认 Ollama 服务在 `http://127.0.0.1:11434` 运行；若未自动启动，在单独终端运行 `ollama serve`。若模型在自定义目录，启动服务前在同一终端设置 `OLLAMA_MODELS` 为实际模型目录，再用 `ollama list` 确认模型可见。

### 2. 启动后端

在项目根目录打开一个 PowerShell 终端：

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

检查健康接口：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health
```

预期返回 `status: UP`。`GENERATION_MAX_TOKENS=4096` 是已验证的本地三题练习样本使用的预算；后端默认值 1200 曾使该样本输出截断。

### 3. 启动前端

在项目根目录打开另一个 PowerShell 终端：

```powershell
cd frontend
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173>。前端开发服务器默认将 `/api` 代理到 `http://127.0.0.1:8080`。停止服务时，在相应终端按 `Ctrl+C`。

### 4. 体验完整流程

创建知识库 → 上传有可提取文字的文档并等待 `READY` → 在“模型设置”测试两个模型 → 在“问答”提问并打开来源 → 生成学习计划 → 生成练习、提交并查看反馈。

PDF、DOCX、TXT、Markdown 各有打包后端和真实本地嵌入服务的上传、`READY` 与正文预览记录，见[格式读取验证](docs/testing/evidence/S09-document-format-runtime-smoke.md)。该记录不代表扫描件或所有复杂排版均可读取。

## 配置

配置参考位于 [`.env.example`](.env.example)，实际默认值见 [`application.yml`](backend/src/main/resources/application.yml)。Spring Boot **不会自动加载** `.env.example`；请在启动后端的终端设置环境变量，或通过应用“模型设置”页保存模型配置。已保存的页面配置应以页面显示值为准。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `8080` | 后端监听地址和端口。 |
| `DATA_ROOT` | `./data` | 数据目录；相对路径基于后端启动目录。 |
| `GENERATION_PROVIDER` / `EMBEDDING_PROVIDER` | `OLLAMA` / `OLLAMA` | 可选 `OLLAMA` 或 `OPENAI_COMPATIBLE`。 |
| `GENERATION_BASE_URL` / `GENERATION_MODEL` | 本地 Ollama 地址 / `gemma4:e2b` | 生成服务地址与模型；上面的启动命令改用 `gemma4:e4b`。 |
| `OLLAMA_BASE_URL` / `OLLAMA_EMBEDDING_MODEL` | `http://127.0.0.1:11434` / `nomic-embed-text-v2-moe:latest` | 嵌入服务地址与模型。公共嵌入服务也使用这两个配置项。 |
| `EMBEDDING_DIMENSION` | `768` | 必须与实际嵌入向量维度一致。 |
| `GENERATION_MAX_TOKENS` | `1200` | 模型输出预算。 |
| `GENERATION_TIMEOUT_MS` / `EMBEDDING_TIMEOUT_MS` | `60000` / `60000` | 模型请求超时，单位毫秒。 |
| `GENERATION_API_KEY_ENV` / `EMBEDDING_API_KEY_ENV` | `OPENAI_API_KEY` | 公共服务密钥所在的环境变量**名称**。 |

若后端更换端口，启动前端前设置代理地址，例如 `$env:VITE_API_PROXY = 'http://127.0.0.1:18092'`。前端端口可用 `npm run dev -- --host 127.0.0.1 --port 15175` 指定。更换嵌入模型或维度后，应按模型设置页提示重建旧索引。

### 使用公共模型服务

项目仅实现指定的 OpenAI 兼容协议，不能保证所有供应商完全兼容。启动后端前，在本机进程环境设置真实密钥和相应的 `GENERATION_PROVIDER`、`GENERATION_BASE_URL`、`GENERATION_MODEL`、`EMBEDDING_PROVIDER`、`OLLAMA_BASE_URL`、`OLLAMA_EMBEDDING_MODEL`、`EMBEDDING_DIMENSION`；生成和嵌入可选不同服务。不要把密钥写入仓库、日志或截图。

公共服务请求可能发送所选资料片段、问题、练习内容或简答作答；界面在适用操作前提示。请在知晓后使用。Ollama 流程不会隐式回退到公共 API。已有 DeepSeek 公共生成真实连接记录；公共嵌入及完整公共 RAG 尚未完成真实验证，详见[测试报告](docs/testing/TEST_REPORT.md)。

## 构建与测试

在项目根目录执行：

```powershell
cd backend
.\mvnw.cmd -B -DforkCount=0 test
.\mvnw.cmd -B -DskipTests package
cd ..\frontend
npm ci
npm test -- --run
npm run build
```

后端 JAR 输出到 `backend/target/knowledge-base-backend-0.1.0-SNAPSHOT.jar`；在 `backend/` 下可运行 `java -jar target/knowledge-base-backend-0.1.0-SNAPSHOT.jar`。前端构建产物在 `frontend/dist/`。`-DforkCount=0` 用于避开本项目记录的 Windows Surefire fork 路径问题。

2026-10-10 本地复核：后端 42 项测试、前端 9 项测试及前端构建通过；四种文本型文档可读取，严格资料问答返回正确来源。证据和边界见[项目复核记录](docs/process/reviews/PROJECT-20261010-复核记录.md)。推送后的 [GitHub Actions 构建](https://github.com/linihuaii/personal-knowledge-base/actions/runs/38037221486) 中，前后端任务均通过；同一提交的远程重新克隆也通过后端测试、前端测试与构建。

## 数据与备份

从 `backend/` 启动时，默认数据目录是 `backend/data/`，包含 H2 数据库、原始文件和处理结果；已由 [`.gitignore`](.gitignore) 排除。备份前停止后端，然后复制**整个** `DATA_ROOT` 目录。恢复时先停止后端，再将完整备份放回原位置，或让 `DATA_ROOT` 指向恢复目录。不要仅复制运行中的数据库文件；备份可能包含私人资料，应妥善保存。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `ollama list` 为空 | 检查 Ollama 服务、模型下载状态及服务进程所用的 `OLLAMA_MODELS` 目录。 |
| 模型连接失败 | 在“模型设置”分别测试生成与嵌入；核对地址、模型 ID、维度及超时。 |
| 文档处理失败 | 核对格式、50 MB 限制和文本是否可提取；查看后端日志，修正后重试。扫描 PDF 不支持 OCR。 |
| PDF 上传成功但没有正文 | 确认使用含 PDFBox 运行依赖的当前后端包；旧版包的解析缺陷已修复。 |
| 练习生成内容截断 | 提高 `GENERATION_MAX_TOKENS`，减少题数或缩小资料范围。 |
| 页面显示后端离线 | 打开 `/api/v1/health` 检查后端；若更换后端端口，设置 `VITE_API_PROXY` 并重启前端。 |
| 端口被占用 | 设置 `SERVER_PORT`，或使用 Vite 的 `--port` 参数，并同步调整代理地址。 |

## 项目结构与文档

```text
backend/              Spring Boot 后端、数据库迁移和测试
frontend/             Vue 前端、组件测试和构建配置
docs/requirements/    需求规格与追踪
docs/design/          架构、数据与 API 设计
docs/testing/         测试报告、缺陷与验证证据
docs/process/         阶段状态、执行日志和审核记录
docs/delivery/        演示脚本、项目总结和交付说明
```

项目执行与验收以 [`PLAN_MASTER.md`](PLAN_MASTER.md) 为准。S01–S08 已通过审核；S09 的远程仓库、推送与线上 CI 证据已补齐，原 `BLOCKED` 审核结论尚待独立复核，见[阶段状态](docs/process/STAGE_STATUS.md)和[交付说明](docs/delivery/DELIVERY_NOTES.md)。

## 许可证

本项目采用 [MIT License](LICENSE)。第三方依赖保留各自的许可证；核查范围见[交付说明](docs/delivery/DELIVERY_NOTES.md)。
