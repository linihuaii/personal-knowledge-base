# 交付说明与许可证核查

日期：2026-10-09（Asia/Shanghai）

## 交付状态

- 本地源码和课程材料已整理；可复现启动命令见根目录 README。
- S08 干净源码副本在 Windows 完成验证。其他操作系统未做本轮实机部署验证；GitHub Actions 配置 Linux runner，但在本地没有线上运行结果。
- 当前分支没有配置 Git remote，也未提供 GitHub 仓库地址。因此没有创建仓库、推送分支、CI 链接或 PR；S09 GitHub 交付项保留为阻塞。
- 本轮没有创建 Release、版本标签或发布受保护分支。

## 许可证选择

项目新增根目录 MIT License。该许可证允许复制、修改、再分发和商业使用，并要求保留版权与许可声明。项目代码本身没有随该许可证声明为第三方所有的代码；第三方依赖保留其各自许可证。

## 依赖核查范围

- 前端锁文件在 S08 固定；当前 `frontend/node_modules` 中 142 个包的 `package.json` 均提供 `license` 元数据。直接依赖包括 MIT（Vue、Vite、Naive UI、Pinia、Vue Router、Vue 插件等）和 Apache-2.0（TypeScript）。完整传递依赖许可证清单没有作为独立 SBOM 归档；发布前应再次依据锁文件生成并审阅第三方声明。
- 后端依赖版本固定于 Maven POM/父 POM；主要组件包括 Spring Boot（Apache-2.0）、Apache Tika/PDFBox（Apache-2.0）、Flyway（Apache-2.0）及 H2（其项目声明的多许可证）。Maven 传递依赖的完整许可证报告没有作为独立清单归档，不能据此声称已完成法律合规审查。
- 仓库只包含项目源代码和自制测试资料；不纳入真实用户资料、API 密钥、数据库文件、上传目录、构建产物、缓存或模型文件。`.gitignore` 排除本地运行数据和构建输出。
- S08 使用的真实模型资料为项目自制的合成 TXT；未把第三方受限教材或文档作为样例提交。

## 发布前待办

1. 提供并核验正式 GitHub 仓库地址及推送凭据，设置 `origin` 后推送交付分支。
2. 在线检查 GitHub Actions 实际状态，并保存仓库/CI 链接。
3. 若要公开发布，再生成完整传递依赖 SBOM/许可证声明并逐项检查；按计划另行取得 Release/标签授权。
