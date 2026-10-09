# S06 学习计划与知识探索验证记录

日期：2026-10-09（Asia/Shanghai）

## 2026-10-09 复审意见返工验证

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 前端生产构建 | `cd frontend; npm run build`（在授权的本机命令环境执行） | 通过：Vite 6.0.11，40 个模块完成转换并生成生产资源 |
| S06 与 S04 集成回归 | `cd backend; .\mvnw.cmd '-Dtest=StudyPlanTests,S04DocumentWorkflowTests' -DforkCount=1 test` | 通过：17 项，0 失败、0 错误、0 跳过 |
| 后端生产打包 | `cd backend; .\mvnw.cmd -DskipTests package` | 通过：编译并生成 Spring Boot jar |
| 差异空白检查 | `git diff --check` | 通过：无空白错误；仅有 Git LF/CRLF 提示 |

本轮验证配合页面返工完成。UI 修改人工核对：计划基础字段传入 `saveStudyPlan` 的 PATCH payload；计划列表读取 camelCase `completedCount`/`taskCount`；任务标题、说明、安排日期和预计时长均可编辑，并由保存按钮一并提交。保存前验证目标、截止日期、每日分钟和任务必填/范围。保存后刷新计划读取接口，页面表单和任务值以服务端返回为准。

## 真实模型边界（复审后仍适用）

本次仅按复审要求返工 S06 页面与相应验证，没有调用真实 Ollama 或公共模型重新生成计划 JSON。结构化输出与服务端格式校验仍由确定性 `ModelGateway` 集成测试覆盖；供应商真实计划 JSON 未现场验证，不记为通过。

## 本次复审返工验证

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| S06 与 S04 集成回归 | `cd backend; .\mvnw.cmd '-Dtest=StudyPlanTests,S04DocumentWorkflowTests' -DforkCount=0 test` | 通过：17 项，0 失败、0 错误、0 跳过 |
| 前端生产构建 | `cd frontend; npm run build` | 通过：Vite production build 成功 |
| 后端编译与打包 | `cd backend; .\mvnw.cmd -DskipTests package` | 通过：生产代码和测试源码编译，jar 打包成功 |
| 空白检查 | `git diff --check` | 通过：无空白错误 |

S06 集成测试在 Spring MockMvc 中验证真实 HTTP 控制器序列：生成计划、列表/读取 camelCase 响应、知识探索、打开文档预览、编辑和排序任务、完成标记及重新规划。其它测试验证模型格式错误和资料不足错误、READY 数据库片段不在当前索引时不会作为来源、格式错误的重新规划不改变旧计划、已完成历史日期可保存、删除资料后保留来源摘录、重建索引后旧来源诚实标记不可用，以及 V8 数据库中已有来源行升级至 V9 快照。

S06 `StudyPlanTests` 的 8 项测试与 S04 `S04DocumentWorkflowTests` 的 9 项回归全部通过，覆盖上传解析、重试幂等、文档删除、处理中删除、索引发布恢复和数据库迁移。V8→V9 专项测试确认原始来源文件名与摘录被迁移，删除原 chunk 后来源快照仍存在。

## 真实模型边界

计划生成/重新规划的集成测试用确定性 `ModelGateway` 替身提供 JSON 输出；本轮没有调用真实 Ollama 或公共模型生成计划。因此，字段校验、事务和 HTTP 行为已有自动化通过证据，模型供应商在实际部署中的 JSON 一致性仍未现场验证。原阶段 S05 的模型接入验证与豁免边界不变。

## 修复对应关系

- 来源外键：新增 Flyway `V9__study_source_snapshots.sql`，复制 V8 来源到持久快照表后替换旧表；`study_task_source` 仅保留任务外键。计划和知识探索只从当前索引快照与 READY 数据库片段交集取来源。文件删除/索引重建测试通过，来源显示摘录与 `available=false`。
- API 响应：计划、任务、来源和计划列表显式构造 camelCase DTO；MockMvc 验证 `/generate`、计划列表/读取、知识探索和任务操作响应字段。
- 历史日期：仅未完成任务限制在今天至截止日；已有完成任务允许过去日期，并由集成测试通过。
- 失败保护：非法 JSON 的首次生成不创建记录；非法重新规划保留原任务 ID、完成状态和数据。

## 保留限制

- 知识点探索继续按资料标题/段落抽取，不创建知识图谱。
- 真实模型生成计划未执行；见“真实模型边界”。
