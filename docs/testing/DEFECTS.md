# S08 缺陷清单

日期：2026-10-09（Asia/Shanghai）

| 编号 | 严重度 | 发现 | 处理 | 当前状态 |
|---|---|---|---|---|
| S08-01 | P1 | Gemma 对单选答案返回选项文本、对判断题返回 `True`，结构化题目被拒收 | Prompt 明确要求大写 A–D/TRUE/FALSE；仅在单选答案文本精确唯一匹配一个选项时归一化；明确真假同义写法归一化；增加集成测试 | 已修复；模拟回归通过，真实三题生成通过 |
| S08-02 | P1 | Gemma 将题目 JSON 包在 Markdown 代码围栏中，解析器拒绝 | 出题和简答评分只剥除外围代码围栏，内部内容仍经过 JSON 和业务 schema 严格校验；增加围栏集成测试 | 已修复；真实出题及简答评分重试通过 |
| S08-03 | P2 | 默认输出预算下真实三题 JSON 被截断 | S08 隔离验收配置调至 4096 后成功；生产默认预算尚未改动，长资料/更多题目输出可能需要调参 | 配置限制已确认；不阻断当前三题验收，建议后续根据规模评估默认值 |
| S08-04 | P2 | 当前环境没有公共 API 凭据 | 保持 Ollama 真实验证；公共服务的允许豁免继续引用 S05 审核，不把公共 API 写成通过；用 Edge + 本机 bundled Playwright 完成真实 UI 流程 | 外部公共 API 按已批准范围豁免；不阻断 S08 |
| S08-05 | P2 | 性能结果只代表单机单资料小样本，且机器当时可用内存偏低 | 补测 Lenovo 83DG / Core i7-14700HX / 16 GB RAM、287 B 单 TXT 的 READY、RAG 首引、计划、练习、答卷与单次模型请求耗时 | 已具备可复现实测口径；未测试多文档/高并发/吞吐上限，不宣称 SLA |
| S08-06 | P1 | 升级前 `npm audit` 发现 Vue/Vite 生产链与 Vitest/tinypool 开发工具链 advisory；复审要求给出公告、受影响版本、路径、调用面和本机暴露分析。 | 新增 [依赖安全告警影响分析](evidence/S08-dependency-audit-triage.md) 和升级前/后 full/prod JSON。按核实修复线将 Vite 更新至 6.4.3、Vue 更新至 3.5.42、Vitest 更新至 4.1.11；Vite 带动 esbuild 至 0.25.12，Vitest 4 移除 tinypool 路径。前端回归 9/9、构建通过；full 与 `--omit=dev` audit 均 0。 | **已修复**；升级后生产与完整依赖树均无 npm audit 告警。 |
| S08-07 | P2 | 原 `S08-audit-feedback-mobile.png` 显示练习配置与历史列表，没有已提交反馈；此前 E2E JSON 只有宽度记录。 | 提升权限后使用 Edge 154 + bundled Playwright 在 390×844 打开 `/practice`，加载已提交答卷确定性 API fixture 并点击历史项；真实 Vue UI 截图显示判分、作答、参考答案、解析、反馈和来源；记录 document/body scrollWidth 均为 390。fixture 来源和边界见 JSON。 | **已补证**；确认反馈页面无横向溢出，完整页面截图包含来源。业务 live Ollama 完整 E2E 仍见原 S08 结果 JSON。 |

## S09 本地人工复核新增缺陷

| 编号 | 严重度 | 发现 | 复现与证据 | 当前状态 |
|---|---|---|---|---|
| S09-01 | P1 | 有文字的 PDF 经页面上传后解析/索引失败，状态为 `FAILED_RETRYABLE`，后端详情为 `PROCESS_FAILED / EMPTY_TEXT`。 | 根因是 PDFBox 3.0.5 被声明为 `test` scope，测试 classpath 可解析而生产 JAR 缺少 PDFBox 核心类。已改为正式运行依赖；S04 测试改为断言正文标记。以修复后的可执行 JAR 和本机 Ollama 上传 PDF、DOCX、Markdown、TXT，四者均 `READY` 且预览含预期文本，详情见 [打包应用格式读取验证](evidence/S09-document-format-runtime-smoke.md)。 | **已修复并完成生产包实测**；S04 已复审通过。 |
