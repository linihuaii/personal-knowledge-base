# S08 集成测试与验收计划

日期：2026-10-09（Asia/Shanghai）

## 范围和环境

- 以 `docs/requirements/TRACEABILITY.md` 的 R01–R12 为追踪基线；本轮最终复核使用 Java 17、Spring Boot 3.5.6、H2、Node 24.21.0、Vite 6.4.3、Vue 3.5.42、Vitest 4.1.11 和 Windows Microsoft Edge 154。早期测试使用的依赖版本仅见历史日志。
- 确定性测试使用内存 H2、Mockito 和临时 HTTP 服务；真实模型测试使用本机 Ollama `gemma4:e4b` 与 `nomic-embed-text-v2-moe:latest`，资料均为自制合成 TXT。
- 真实端到端运行使用隔离服务 `127.0.0.1:8180` 和专用临时目录 `backend/target/s08-app-data`；结束后已删除本轮创建的知识库、资料、练习记录与隔离目录。模型 API Key 不存在，公共 API 仍按 S05 审核豁免，不声称已验证。
- 端到端由本机 Microsoft Edge 154 与 Codex bundled Playwright 执行；桌面视口 1440×1000、窄屏 390×844；浏览器通过本地 Vite 页完成点击、上传、问答、计划、练习与反馈。
- 干净交付验证从提交导出 Unicode-safe ZIP 源码副本；在副本执行锁文件安装、回归、生产构建，并从副本启动前后端。

## 验收场景

| ID | 需求/场景 | 验收证据 |
|---|---|---|
| S08-01 | 最终版本完整后端回归 | 源码工作区及干净副本均 40 项通过，0 失败/错误/跳过；`-DforkCount=0` 解决 Surefire Windows fork 根路径问题 |
| S08-02 | 前端回归与生产构建 | 源码工作区及干净副本各 4 个测试文件、9 项通过；Vite 42 模块生产构建通过 |
| S08-03 | 批量导入、提取、真实嵌入及 READY 状态 | Edge 上传合成 TXT；文件 287 B，状态 READY；`UPDATED_AT-CREATED_AT` 为 4.046 s；完整 E2E 截图/数据见 evidence |
| S08-04 | 真实检索、引用与跨库隔离 | Ollama RAG 引用通过；从问答提交到首 citation 12.585 s；跨库/资料不足由确定性回归覆盖 |
| S08-05 | 三种题型、来源、答案隔离、客观判分、简答评分与反馈 | Edge 三题生成、提交前答案隐藏、提交反馈；真实模型评分重试另见测试报告 |
| S08-06 | 重启恢复、嵌入变更后的重建、删除清理 | 文件数据库与版本迁移回归通过；E2E 合成库/文件在验收后经服务 API 删除 |
| S08-07 | 安全边界与依赖审计 | 应用安全测试通过；`npm audit` 发现上游依赖风险，细节与影响列于缺陷清单，需复审接受或另行升级 |
| S08-08 | 桌面及窄屏关键页/反馈状态 | Edge 自动化走完业务主流程；4 类页面在两个视口记录 `scrollWidth<=innerWidth`；10 张截图归档 |
| S08-09 | 性能 | 记录硬件、样本、单次 Ollama embed/chat 及上传、首 citation、学习计划、练习、作答耗时；只作本机小样本观察，不外推 SLA |
| S08-10 | 干净安装与启动 | `git archive HEAD` ZIP 干净副本；`npm ci --offline` 150 packages，前端 9/9/构建通过；后端完整 40/40；README 前后端启动与 Edge 健康页均通过。公共 API 仍按既有批准豁免 |
