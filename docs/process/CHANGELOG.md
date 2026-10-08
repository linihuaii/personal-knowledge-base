# 范围与技术基线变更记录

## 2026-10-08：后端运行基线改为本机 JDK 17

- 原因：项目所有者要求使用电脑已安装的 JDK；核对 `java -version` 和 `javac -version` 后确认实际版本为 17，并由项目所有者明确选择 JDK 17。
- 变更：计划、SRS、架构与 ADR 的后端基线由 Java 21 改为 JDK 17；保留 Spring Boot 3.x 路线，并要求在 S02 验证已固定的候选依赖组合。原 Java 21 专属 Lucene 候选撤回。
- 影响：R12 本地交付环境要求、S02 技术选型与兼容性验收、S03 工程搭建输入。R01–R11 的功能验收条件不变。
- 验证：本机 `java -version` 为 `17+35`，`javac -version` 为 `17`。Spring Boot 3.5 官方文档要求至少 Java 17；完整构建、Tika/PDFBox/H2 兼容性仍未验证。
- 审核：S02 保持 `CHANGES_REQUIRED`。本变更不使历史审核记录失效或自动放行 S02；后续 S02 复审须审核新技术基线。
