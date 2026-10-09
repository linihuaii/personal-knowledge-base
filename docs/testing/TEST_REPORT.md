# S08 集成测试与验收报告

- 日期：2026-10-09（Asia/Shanghai）
- 前置阶段：S01–S07 均为 `APPROVED`；S07 最新审核为 `b9f58ad`。
- 实现版本：见阶段日志与 S08 实现提交。

## 自动化验证

| 命令/证据 | 结果 |
|---|---|
| `cd backend; .\mvnw.cmd '-Dtest=PracticeTests,StudyPlanTests,S04DocumentWorkflowTests,ChatRetrievalTests,ModelSettingsTests,ModelGatewayProtocolTests,UnconfirmedPublicEmbeddingRecoveryTests,FileDatabasePersistenceTests' -DforkCount=1 test` | 前一轮最终功能回归 40 项通过；本轮完整复跑在首个测试类启动阶段被 Surefire 的 Windows 子进程 classpath “different root” 问题中止。最终修改后的 `PracticeTests` 独立重跑 7 项通过；本轮完整复跑不记为通过。 |
| `cd frontend; npm test -- --run` | 4 个测试文件，9 项通过 |
| `cd frontend; npm run build` | 通过；Vite 6.0.11，42 个模块 |
| `git diff --check` | 通过 |

后端用例覆盖跨库及非 READY 过滤、资料不足拒答、删除不命中、索引快照不兼容失败关闭、提示注入提示构造、模型连接/超时/取消、文件格式/大小/恢复/重试/删除、计划保留完成项、练习答案隔离/重复提交/格式校验/服务失败保留作答。新增用例覆盖模型将 JSON 包在代码围栏、单选答案文本唯一匹配与大小写/自然语言真假标签归一化。

## 真实本机服务抽查

- 隔离知识库含两份自制资料：Java 集合速查和 Java 并发基础；嵌入模型 `nomic-embed-text-v2-moe:latest`，维度 768，生成模型 `gemma4:e4b`。
- RAG 问题“List 有哪些存储特点？”实际检索到 `java.txt`，余弦分数 `0.4372`，收到 citation、token、completed SSE；回答与插入顺序和允许重复的原文一致。
- 真实三题生成起初发现 Ollama 输出兼容问题：答案标签写法变化、Markdown JSON 围栏和默认输出上限不足。加上格式提示、外围代码围栏剥离、无歧义答案归一化后，隔离配置 `maxTokens=4096` 的三题生成通过；返回单选、判断、简答各一题，均引用已索引合成资料。答案和解释未出现在生成前 GET。
- 合成答卷提交后客观题由服务端判分，学生作答已持久化；首次真实简答评分响应因格式问题为 `UNAVAILABLE`。最终代码重启后重试成功，练习从 `gradedCount=2` 更新为 `3`、`scoringPending=false`，简答评价与题目资料一致。
- 模型抽查为单资料、单配置、小样本，不能代表其他资料、难度、模型或长时间负载。

## 浏览器与安全观察

- Microsoft Edge 154 无头真实浏览器打开知识库与练习页面；1440×1000 桌面视口双栏、390×844 窄屏练习页单栏。页面截图见 [桌面练习页](evidence/S08-practice-desktop.png) 和 [窄屏练习页](evidence/S08-practice-mobile.png)。
- 窄屏截图中导航可换行、知识库选择和资料/题型配置可见；本轮没有 Playwright 包，npm registry DNS 不可用，因此未执行脚本化浏览器完整点击流，也未对所有视口逐元素做自动溢出度量。jsdom 测试不作为浏览器 E2E 替代。
- 安全回归：配置 API 只公开环境变量引用和是否配置状态；HTTP 对外部带密钥 API 会拒绝；路径存储/删除受 data root 检查；上传类型、损坏文件和 50MB 边界有集成测试；知识库内容在生成提示中作为不可信资料；Vue 模板文本插值未使用 v-html。默认服务监听 `127.0.0.1`，见 `application.yml`。

## 遗留与影响

1. 当前未配置公共 API 凭据，当前环境 DNS 无法解析 npm registry；真实公共供应商调用、跨供应商 RAG 以及干净克隆安装未验证。S05 已批准的外部接入豁免边界继续适用，本报告不将其写作通过。
2. 本轮真实 Edge 完成知识库页与练习页的页面/视口检查并留存截图，但没有脚本化完整 UI 端到端交互；上传、问答、计划和练习业务链由隔离 REST 请求及后端/组件测试覆盖。
3. 本机单样本耗时不构成性能承诺；没有稳定硬件规格和大规模数据集，未形成负载测试或吞吐上限结论。

## 最终完整套件复跑异常

2026-10-09 20:05（Asia/Shanghai）运行完整 Maven 套件时，首个 `ChatRetrievalTests` Spring 上下文启动后，Surefire fork 写出 `Boot Manifest-JAR contains absolute paths in classpath ... 'other' has different root` 并退出。该次没有产生本轮全套摘要。已单独对最终 PracticeService 解析改动运行 `PracticeTests`：7 项通过。既有上一轮 40 项结果仍可参考，但当前最终代码的全套回归需在 Surefire fork 路径问题排除后重新执行。
