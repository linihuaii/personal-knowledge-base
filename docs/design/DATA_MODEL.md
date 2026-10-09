# 数据模型与状态设计

阶段：S02；版本：0.1；日期：2026-10-08（Asia/Shanghai）。正式字段以 S03/S04 实现前的 API 契约复核为准。

## 1. 实体关系

```text
KnowledgeBase 1──N Document 1──N DocumentChunk
KnowledgeBase 1──N Conversation 1──N Message 1──N Citation
KnowledgeBase 1──N StudyPlan 1──N StudyTask
StudyPlan 1──N PracticeSession 1──N PracticeAnswer
DocumentChunk N──1 EmbeddingIndexEntry
```

## 2. 核心字段

| 实体 | 关键字段 | 约束 |
|---|---|---|
| KnowledgeBase | id, name, description, createdAt, updatedAt, deletedAt | name 非空；删除为软状态起点 |
| Document | id, knowledgeBaseId, originalName, mediaType, sizeBytes, sha256, storageKey, status, failureCode, parserVersion | 外键有效；sha256+库可提示重复；状态可恢复 |
| DocumentChunk | id, documentId, ordinal, text, startOffset, pageNumber?, section?, pipelineVersion | ordinal 唯一；不能伪造页码；删除随文档失效 |
| EmbeddingIndexEntry | chunkId, indexVersion, modelName, dimension, vectorRef, active | 模型指纹、维度必须匹配 |
| Conversation | id, knowledgeBaseId, mode, title, createdAt | mode 为 STRICT 或 AUGMENTED |
| Message | id, conversationId, role, content, createdAt, completionStatus | role 为 USER/ASSISTANT/SYSTEM_INTERNAL；内容不执行 |
| Citation | id, messageId, chunkId, quote, locationLabel | chunk 必须属于当前库；引用可追溯 |
| StudyPlan | id, knowledgeBaseId?, goal, baseline, deadline, dailyMinutes, status | deadline 不早于当天；生成失败不覆盖旧版本 |
| StudyTask | id, planId, ordinal, title, dueDate, minutes, status, sourceChunkIds | 已完成任务调整时不可删除 |
| PracticeSession | id, knowledgeBaseId, difficulty, questionTypes, documentIds, questionCount, status, createdAt, submittedAt | Flyway V10；状态为 `IN_PROGRESS` 或 `SUBMITTED` |
| PracticeQuestion | id, sessionId, ordinal, type, stem, options?, answer, explanation, sourcesSnapshot | 答案只在提交/复习服务内部读取；来源名、位置、摘录快照无 chunk 外键 |
| PracticeAnswer | questionId, submittedAnswer, score?, feedback?, scoringStatus, submittedAt | 客观题确定性判分；简答 `GRADED/UNAVAILABLE`，评分不可用仍保留作答 |

## 3. 处理状态机

```text
UPLOADED -> PROCESSING -> READY
              │             │
              ├-> FAILED_RETRYABLE -> PROCESSING
              └-> FAILED_FINAL
READY -> DELETING -> DELETED
```

`PROCESSING` 记录 `leaseOwner`、`leaseUntil`、`attemptCount`。启动恢复只处理过期租约。删除对外立即禁止检索，最终 `DELETED` 需要文件和索引清理确认。

## 4. 目录布局

```text
<data-root>/
  db/app.mv.db
  raw/<document-id>/<safe-original-name>
  processed/<document-id>/<parser-version>/text.json
  index/<index-version>/manifest.json
  index/<index-version>/vectors.bin
  temp/<job-id>/
  backups/
```

应用不接受用户提供的绝对存储路径；`storageKey` 是内部相对键。临时目录不可作为可检索数据源。

## 5. 索引变更与重建

索引清单包含 `indexVersion`、`embeddingModel`、`dimension`、`distanceMetric` 和 `pipelineVersion`。启动加载时先校验清单与配置；不匹配则状态为 `REBUILD_REQUIRED`，禁止静默混用。重建流程创建新版本目录，逐片段写入并校验数量，成功后原子切换 manifest，旧版本保留至清理任务确认。
