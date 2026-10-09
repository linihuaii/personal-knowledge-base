# REST 与 SSE API 契约

阶段：S02；版本：0.1；日期：2026-10-08（Asia/Shanghai）。这是业务实现前的候选契约；S03 固定 OpenAPI 文件和版本。

## 1. 约定

- 基础路径：`/api/v1`；JSON UTF-8；时间为 ISO-8601 offset。
- 成功响应直接返回资源或分页对象；错误统一为 `{code, message, details?, traceId}`。
- 用户输入错误 400，资源不存在 404，状态冲突 409，外部模型错误 502/504，服务不可用 503。
- 所有列表支持 `page`、`pageSize`，服务端限制最大页大小。

## 2. 资源接口

| 方法 | 路径 | 用途 | 关键错误 |
|---|---|---|---|
| GET/POST | `/knowledge-bases` | 列表/创建 | `KB_NAME_INVALID`, `KB_NAME_CONFLICT` |
| GET/PATCH/DELETE | `/knowledge-bases/{id}` | 查看/编辑/删除 | `KB_NOT_FOUND`, `KB_DELETE_CONFIRM_REQUIRED` |
| GET/POST | `/knowledge-bases/{id}/documents` | 列表/上传 multipart | `FILE_TYPE_UNSUPPORTED`, `FILE_TOO_LARGE`, `PATH_INVALID` |
| GET | `/documents/{id}` | 文档和处理状态 | `DOCUMENT_NOT_FOUND` |
| GET | `/documents/{id}/preview` | 提取文本预览 | `DOCUMENT_NOT_READY` |
| GET | `/documents/{id}/download` | 下载原文件 | `FILE_MISSING` |
| DELETE/POST | `/documents/{id}` / `/documents/{id}/retry` | 删除/重试 | `DOCUMENT_BUSY`, `RETRY_NOT_ALLOWED` |
| POST | `/knowledge-bases/{id}/conversations` | 创建会话并返回会话 ID | `KB_NOT_FOUND` |
| GET/POST | `/conversations/{id}/messages` | 历史/发起 SSE 问答 | `NO_EVIDENCE`, `MODEL_TIMEOUT` |
| GET/PATCH | `/study-plans/{id}` | 查看/编辑计划 | `PLAN_INVALID`, `PLAN_NOT_FOUND` |
| POST | `/study-plans/generate` | 生成计划 | `PLAN_GENERATION_INVALID`, `INSUFFICIENT_MATERIAL` |
| POST | `/study-plans/{id}/replan` | 调整未完成任务 | `REPLAN_INVALID` |
| GET | `/knowledge-bases/{kbId}/study-plans` | 列出知识库学习计划 | `KB_NOT_FOUND` |
| PATCH | `/study-plans/{id}/tasks/{taskId}/complete` | 标记或撤销任务完成 | `PLAN_NOT_FOUND`, `TASK_NOT_FOUND` |
| GET | `/knowledge-bases/{kbId}/knowledge-explorer` | 从已索引资料读取知识点及来源 | `KB_NOT_FOUND`, `INDEX_UNAVAILABLE` |
| POST | `/practice-sessions` | 生成练习 | `QUESTION_SCHEMA_INVALID`, `SOURCE_INSUFFICIENT` |
| GET | `/practice-sessions/{id}` | 获取不含答案的题目 | — |
| POST | `/practice-sessions/{id}/submit` | 提交并评分 | `ANSWER_INVALID`, `ALREADY_SUBMITTED` |
| GET/PUT/POST | `/settings/models`, `/settings/models/{id}`, `/settings/models/test` | 配置/测试模型 | `MODEL_UNREACHABLE`, `EMBEDDING_DIMENSION_MISMATCH` |

## 3. SSE 问答事件

请求 `POST /conversations/{id}/messages`，`Accept: text/event-stream`，请求体 `{content, mode, topK}`。事件类型：

```text
event: token\ndata: {"text":"..."}
event: citation\ndata: {"citationId":"...","documentId":"...","location":"p. 3"}
event: completed\ndata: {"messageId":"..."}
event: failed\ndata: {"code":"MODEL_TIMEOUT","message":"..."}
```

客户端断开后服务端取消下游请求并释放任务/连接；不得无限等待。`completed` 或 `failed` 只发送一次。

## 4. 安全契约

题目 GET 永不返回 `answer`、`explanation` 或评分内部字段；这些字段仅在提交服务内部读取并在评分响应中返回。所有知识库相关资源通过路径库 ID 与资源库 ID 二次校验，拒绝跨库 ID。

S06 执行补充：计划生成请求为 `{knowledgeBaseId, goal, currentLevel, deadline, dailyMinutes}`；任务含标题、说明、日期、预计分钟、完成状态和来源 chunk ID。生成模型只提交 JSON；服务端校验日期、任务数量、时间范围和当前知识库内的 READY 片段引用后才保存。`PATCH /study-plans/{id}` 保存计划字段及任务排序/编辑，已有完成项不可被覆盖或伪造；重新规划只替换未完成任务。知识探索返回资料标题/段落、解释摘要和原始 `documentId`/`chunkId`，不创建知识图谱。

S06 返工补充：学习计划 API 的计划、任务和来源字段统一使用 camelCase。计划来源以文档名、位置和摘录快照保存；V9 将既有 V8 来源行迁移为快照，并移除来源对文档/片段的外键。删除或重建索引不会阻断资料生命周期；来源片段不再可用时响应仍保留摘录并将 `available` 设为 `false`。重新规划只有在模型输出通过校验后才替换未完成任务；已完成任务允许保留过去日期。

## 5. 模型适配契约

生成和嵌入是两个独立能力，配置、请求、结果和错误不得混用。

配置字段：`provider`（`OPENAI_COMPATIBLE` 或 `OLLAMA`）、`baseUrl`、`model`、`apiKeyRef`、`timeoutMs`、`embeddingDimension`，以及生成专属的 `temperature`/`maxTokens`。`apiKeyRef` 只保存环境变量引用，不保存密钥；生成和嵌入配置分别保存；`timeoutMs` 范围为 1000--120000。

生成请求为 `GenerateRequest {model, messages[{role,content}], temperature, maxTokens, stream, deadline}`，结果为 `GenerateResult {text, provider, model, finishReason, usage, requestId}`。流式事件为 `TOKEN`、`COMPLETED` 或 `FAILED`。

嵌入请求为 `EmbedRequest {model, inputs[], expectedDimension?}`，结果为 `EmbedResult {vectors[][], provider, model, dimension, usage, requestId}`。向量数量必须等于输入数量，每个向量维度必须一致；`expectedDimension` 不匹配立即失败。索引指纹为 `sha256(provider + model + dimension + pipelineVersion)`，加载时逐项比较，禁止静默混用。

统一错误分类：`CONFIG_INVALID`、`AUTH_FAILED`、`MODEL_NOT_FOUND`、`CONNECTION_FAILED`、`RATE_LIMITED`、`REQUEST_TIMEOUT`、`CANCELLED`、`INVALID_RESPONSE`、`DIMENSION_MISMATCH`。客户端取消时关闭下游连接；Ollama 失败不得自动切换公共 API。
