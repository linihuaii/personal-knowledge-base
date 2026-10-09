# 打包应用文档格式读取验证

- 日期：2026-10-09（Asia/Shanghai）
- 应用：修复后的 Spring Boot 可执行 JAR，Java 17，Spring Boot 3.5.6。
- 命令：`cd backend; .\mvnw.cmd -B -DforkCount=0 test package`，随后以 `java -jar target/knowledge-base-backend-0.1.0-SNAPSHOT.jar` 启动；独立监听 `127.0.0.1:18089`，数据目录为忽略的 `backend/target/pdf-runtime-smoke-20261009/data`。
- 模型：本机 Ollama `nomic-embed-text-v2-moe:latest`，768 维；不使用公共 API。
- 样本：S04 集成测试自制的文本型 PDF、DOCX、Markdown、TXT。原件在 `backend/target/s04-workflow-data/raw/`，不提交到仓库。

## 结果

通过生产运行实例的 `POST /api/v1/knowledge-bases/{id}/documents` 上传，并轮询处理状态；四种格式均达到 `READY`。调用 `GET /api/v1/documents/{id}/preview` 后得到：

| 文件 | 状态 | 提取文本（正文标记） |
|---|---|---|
| `sample.pdf` | `READY` | `S04 PDF marker` |
| `sample.docx` | `READY` | `S04 DOCX marker` |
| `sample.md` | `READY` | `# S04 heading`、`markdown marker` |
| `sample.txt` | `READY` | `index recovery marker` |

打包结果 `backend/target/knowledge-base-backend-0.1.0-SNAPSHOT.jar` 中包含 `BOOT-INF/lib/pdfbox-3.0.5.jar`、`tika-parser-pdf-module-3.2.2.jar` 和 `tika-parser-microsoft-module-3.2.2.jar`。修复前 PDFBox 仅为 `test` scope：测试 classpath 有 PDFBox，但生产可执行 JAR 没有 PDFBox 核心类，故 Tika 对 PDF 返回空文本。现在 PDFBox 是正式运行依赖。

## 限制

此次验证证明这四份合成文本样本在生产打包应用中可以提取正文、生成索引并进入 `READY`；不证明扫描版 PDF/OCR、加密 PDF、所有 Word 排版或所有 PDF 变体均可读取。扫描件 OCR 仍不在项目范围内。
