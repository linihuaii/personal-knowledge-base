# S08 集成测试与验收报告

- 日期：2026-10-09（Asia/Shanghai）
- 前置阶段：S01–S07 均为 `APPROVED`；S07 最新审核为 `b9f58ad`。
- 实现代码未改动；复审核整改提交为纯验收文档与证据补充，代码版本为 `797128185930f228136c0bb291a5017a7dbf2f9a`。

## 自动化验证

| 命令/证据 | 结果 |
|---|---|
| `cd backend; .\mvnw.cmd "-Dtest=PracticeTests,StudyPlanTests,S04DocumentWorkflowTests,ChatRetrievalTests,ModelSettingsTests,ModelGatewayProtocolTests,UnconfirmedPublicEmbeddingRecoveryTests,FileDatabasePersistenceTests" -DforkCount=0 test` | 当前实现提交完整回归 40 项通过，0 失败/错误/跳过；关闭 Surefire fork 后通过，原 Windows manifest/fork 根路径问题已绕开。 |
| 从 ZIP 干净副本运行同一 Maven 命令 | 编译 16 个主源文件、9 个测试源文件；40 项通过，0 失败/错误/跳过。 |
| `cd frontend; npm test -- --run` 与干净副本同命令 | 每处 4 个测试文件、9 项通过。 |
| `cd frontend; npm run build` 与干净副本同命令 | 每处通过；Vite 6.0.11，42 个模块。 |
| 干净副本 `npm ci --offline --cache <backend/target/s08-npm-cache>` | 从提交的 ZIP 源码副本全新安装 150 个锁定包；依赖 cache 为本轮网络安装所得，避免 npm 全局缓存权限问题。 |
| 干净副本 README 启动 | `mvnw -B spring-boot:run` 监听 8181，健康 API 返回 `UP`；`npm run dev` 监听 5175；Edge 首页显示后端 `UP`。 |
| `npm audit` 与 `npm audit --omit=dev` | 全依赖树：17 个受影响依赖节点（3 moderate、12 high、2 critical）；生产树：13 个（1 moderate、12 high、0 critical）；当前范围 `fixAvailable=false`，详见缺陷清单。 |
| `git diff --check` | 通过 |

后端用例覆盖跨库及非 READY 过滤、资料不足拒答、删除不命中、索引快照不兼容失败关闭、提示注入提示构造、模型连接/超时/取消、文件格式/大小/恢复/重试/删除、计划保留完成项、练习答案隔离/重复提交/格式校验/服务失败保留作答。新增用例覆盖模型将 JSON 包在代码围栏、单选答案文本唯一匹配与大小写/自然语言真假标签归一化。

## 真实本机服务抽查

- 隔离知识库含两份自制资料：Java 集合速查和 Java 并发基础；嵌入模型 `nomic-embed-text-v2-moe:latest`，维度 768，生成模型 `gemma4:e4b`。
- RAG 问题“List 有哪些存储特点？”实际检索到 `java.txt`，余弦分数 `0.4372`，收到 citation、token、completed SSE；回答与插入顺序和允许重复的原文一致。
- 真实三题生成起初发现 Ollama 输出兼容问题：答案标签写法变化、Markdown JSON 围栏和默认输出上限不足。加上格式提示、外围代码围栏剥离、无歧义答案归一化后，隔离配置 `maxTokens=4096` 的三题生成通过；返回单选、判断、简答各一题，均引用已索引合成资料。答案和解释未出现在生成前 GET。
- 合成答卷提交后客观题由服务端判分，学生作答已持久化；首次真实简答评分响应因格式问题为 `UNAVAILABLE`。最终代码重启后重试成功，练习从 `gradedCount=2` 更新为 `3`、`scoringPending=false`，简答评价与题目资料一致。
- 模型抽查为单资料、单配置、小样本，不能代表其他资料、难度、模型或长时间负载。

## 浏览器与安全观察

- 使用 Microsoft Edge 154 与 Codex bundled Playwright 完成真实浏览器主流程：首页健康 → 创建/选择知识库 → 上传 TXT 并等待 READY → 新建问答/提交问题/验证来源与回答 → 生成并保存学习计划 → 生成三题练习/验证提交前答案隐藏 → 填答/提交/查看判分、答案、解析及反馈。
- 桌面 1440×1000 与窄屏 390×844 均检查知识库、问答、计划、练习配置与反馈页面。各页面 `documentElement.scrollWidth` 等于视口宽度，没有横向溢出。截图包括 [桌面知识库](evidence/S08-audit-knowledge-desktop.png)、[桌面问答](evidence/S08-audit-qa-desktop.png)、[桌面学习计划](evidence/S08-audit-study-desktop.png)、[桌面练习](evidence/S08-audit-practice-desktop.png)、[桌面反馈](evidence/S08-audit-feedback-desktop.png)、[窄屏知识库](evidence/S08-audit-knowledge-mobile.png)、[窄屏问答](evidence/S08-audit-qa-mobile.png)、[窄屏学习计划](evidence/S08-audit-study-mobile.png)、[窄屏练习](evidence/S08-audit-practice-mobile.png)、[窄屏反馈](evidence/S08-audit-feedback-mobile.png)。详细交互与尺寸结果见 [E2E 结果](evidence/S08-audit-e2e-results.json)。
- 安全回归：配置 API 只公开环境变量引用和是否配置状态；HTTP 对外部带密钥 API 会拒绝；路径存储/删除受 data root 检查；上传类型、损坏文件和 50MB 边界有集成测试；知识库内容在生成提示中作为不可信资料；Vue 模板文本插值未使用 v-html。默认服务监听 `127.0.0.1`，见 `application.yml`。

## 遗留与影响

1. 公共 API 凭据仍未配置；真实公共供应商调用/跨供应商 RAG 继续按 S05 已批准豁免边界，不写为已通过。
2. 真实浏览器完整业务链和关键页面窄屏检查已完成；浏览器主流程使用本机 Ollama，生成配置 `maxTokens=4096`。生产默认 1200 的输出预算限制仍记录为 S08-03。
3. `npm audit` 当前发现的上游依赖安全告警没有锁定范围内自动修复方案；不在本次无审核条件下强升依赖，需独立复审评估接受或要求单独依赖升级。
4. 性能值为单机、单文档、单次小样本观察，没有多文档/高并发负载测试，不能作为吞吐上限或 SLA。

## 已解决的最终完整套件复跑异常

2026-10-09 初次默认 fork 及禁用 manifest classpath 运行曾失败/停滞。审核复跑中以 `-DforkCount=0` 绕开 Windows fork 根路径问题，最终源码工作区与 ZIP 干净副本分别完整运行 40 项，均 0 失败、0 错误、0 跳过。Maven 对 no-fork 发出准确性提示；干净副本运行与源码运行结果一致。

## 性能测量

- 机器：Lenovo 83DG，Intel Core i7-14700HX，20 核/28 线程，16,160 MB RAM；测量时 Windows 可用 RAM 约 355 MB，系统其他进程负载较高。
- 数据：隔离库单份自制 TXT，287 B、1 文档、1 个短文本段；Ollama `nomic-embed-text-v2-moe:latest`（768 维）与 `gemma4:e4b`。
- 上传/解析/分段/嵌入/写索引整体：数据库 `UPDATED_AT-CREATED_AT` 观测 4.046 s。该数包含 Tika、embedding API、持久化与索引，不把 embedding 以外的系统时间精确拆分。
- Ollama 单文本 embedding 请求：3.352 s；单提示生成请求：0.215 s。两者是单次 API round trip，非平均值。
- UI 问答到首个 citation：12.585 s（查询 embedding 与本地索引读/过滤/相似度排序）；到完整回答 12.589 s。两项均为端到端，不把差值当作模型纯生成精确时间。
- 学习计划生成：17.132 s；三题练习生成：25.242 s；答卷提交到反馈：10.606 s。均包含模型与应用处理的端到端墙钟时间。
- 首引时间较单独 embedding 时间高，测量样本数只有 1 且系统可用内存较低；该结果仅记录观测值，不代表稳定性能或 SLA。
