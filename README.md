# 智能知识库管理系统

当前完成至 S03：本地单机工程骨架、后端健康检查、H2 文件数据库迁移、前端 Vite/Vue 页面、环境示例和 CI 基线。

## 环境

- Java 17
- Maven Wrapper（后端已提交，固定 Maven 3.9.11）
- Node.js 18+ 与 npm

## 启动后端

```powershell
cd backend
./mvnw.cmd -B spring-boot:run
```

默认监听 `127.0.0.1:8080`，健康检查：`http://127.0.0.1:8080/api/v1/health`。数据默认写入 `./data/db`，该目录不会提交到 Git。

## 启动前端

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://127.0.0.1:5173`。前端开发服务器将 `/api` 代理到后端。

## 验证

```powershell
cd backend
./mvnw.cmd -B test
cd ..\frontend
npm run build
```

S03 不实现文档、问答、学习计划或练习业务；这些按计划分别在 S04-S07 完成。
