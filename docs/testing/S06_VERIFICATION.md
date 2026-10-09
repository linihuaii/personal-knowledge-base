# S06 学习计划与知识探索验证记录

日期：2026-10-09（Asia/Shanghai）

## 已实现与可核对行为

- 计划生成：输入知识库、目标、基础、截止日期和每日分钟；只从当前知识库 `READY` 文档片段提供模型上下文；结构化 JSON 校验日期、时长、任务数和 chunk 引用，全部通过后才创建计划。
- 编辑与排序：任务由稳定 ID 更新；新增/删除/改标题、改说明、调整日期、时长与顺序不会替换其他任务身份；完成项通过独立接口更新，不能由编辑请求伪造。
- 重新规划：模型先成功生成且通过校验，再事务性地移除旧未完成任务并新增调整任务。已完成任务及其来源行保持原样；模型失败时不写入。
- 来源：计划任务保存 chunk/document 外键，读取时只展示仍为 `READY` 的来源；知识探索从索引片段中的标题或段落整理条目并提供文档、chunk 和定位信息。
- 资料不足：没有已索引资料时返回 `INSUFFICIENT_MATERIAL`；索引缺失/损坏返回 `INDEX_UNAVAILABLE`。

## 执行记录

| 命令/检查 | 结果 |
|---|---|
| `backend/.\mvnw.cmd -DskipTests package` | 通过；Java 17 编译、测试源码编译及打包成功 |
| `frontend/npm run build` | 通过；Vite production build 成功 |
| `git diff --check` | 通过；无空白错误 |
| `backend/.\mvnw.cmd test -q` | 未完成；Surefire fork dump 报本机路径错误 `'other' has different root`，无本轮测试报告 |
| `backend/.\mvnw.cmd -Dtest=StudyPlanTests test` | 新测试编译成功；Spring 测试 fork 在上下文启动时同样挂起，本轮未产生测试结论 |
| `java -jar ...`（隔离 DATA_ROOT） | Flyway 验证并执行 V1–V8 成功；Tomcat 启动因沙箱 loopback selector `Permission denied: connect` 退出，故未能作 HTTP 冒烟 |

## 自动化测试范围

新增 `StudyPlanTests` 覆盖非法结构化输出不创建计划、重新规划保留已完成任务与来源、索引缺失有明确错误。测试源代码已编译，但本轮无法执行；审核时可在正常本机执行 `cd backend; .\mvnw.cmd -Dtest=StudyPlanTests test`。

## 限制

- 没有在本轮调用真实生成模型产生学习计划；模型服务真实可用性仍取决于用户本机 S05 配置。
- 知识探索当前使用标题/段落抽取，不调用模型生成语义关系，也不创建知识图谱。
- 由于测试 fork 与 loopback 被当前执行环境限制，数据库迁移已验证，完整 HTTP/API 验收尚未完成。
