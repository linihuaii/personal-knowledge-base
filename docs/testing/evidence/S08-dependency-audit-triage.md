# S08 前端依赖安全告警影响分析与修复

- 日期：2026-10-09（Asia/Shanghai）
- 范围：`frontend/package.json`、lockfile 与 `npm audit` 完整树/生产树。
- 审计工具：npm 11.19.0 / Node 24.21.0；registry 请求成功。

## 修复前基线

在升级前 Git 锁文件快照 `7971281` 上重新运行 npm audit，原始 JSON 保存在本目录。首次复审记录的 17 个全树、13 个 `--omit=dev` 受影响依赖路径，来自 npm 对上游 advisory 沿依赖链的影响汇总；审计 JSON 中的可复核根节点如下：完整树 7 个根包（2 moderate、3 high、2 critical），生产依赖树 4 个根包（1 moderate、3 high、0 critical）。其中 Vue/Vite 的告警会传播到其依赖者（例如 Naive UI、Pinia、Vue Router、Vite Vue 插件），故 affected dependency path 数大于根包记录数。

| 根包/路径 | 锁定版本 | Advisory、影响范围 | 调用面与影响判断 |
|---|---|---|---|
| `vite`（直接生产依赖；Vite 影响插件、Vitest、Vue 构建链） | 6.0.11 | 12 条 advisory：moderate `GHSA-x574-m823-4x7w`（`>=6.0.0 <6.0.12`）、`GHSA-356w-63v5-8wf4`（`>=6.0.0 <6.0.15`）、`GHSA-859w-5945-r5v3`（`>=6.0.0 <=6.1.5`）、`GHSA-xcj6-pq6g-qj4x`（`>=6.0.0 <6.0.14`）、`GHSA-93m4-6634-74q7`（Windows `server.fs.deny`，`>=6.0.0 <=6.4.0`）、`GHSA-4r4m-qw57-chr8`（`>=6.0.0 <6.0.13`）、`GHSA-4w7w-66w2-5vf9`（`<=6.4.1`）；high `GHSA-p9ff-h696-f583`（dev server WebSocket 任意文件读取，`>=6.0.0 <=6.4.1`）及 `GHSA-fx2h-pf6j-xcff`（Windows alternate path deny bypass，`<=6.4.2`）；另有 2 low、1 moderate 传递自 `launch-editor`。 | 是这组中最接近本机开发攻击面的部分。Vite 只用于本地开发/构建，Spring Boot 运行时不加载；配置未设 `server.host`，默认只监听 localhost。高危两个公告要求显式网络暴露开发服务；Windows 文件读取公告还要求敏感文件位于允许路径。不得用 `--host 0.0.0.0` 或将开发服务器放到不可信网络。修复 Vite 后所有相关 advisory 消失。 |
| `vue` → `@vue/server-renderer`（并传播至依赖 Vue 的 UI 包） | 3.5.13 | high `GHSA-g2v6-rqmx-r4w6`，`@vue/server-renderer <3.5.42`。 | 漏洞位于 SSR 属性名输出。项目是纯浏览器 SPA：入口用 `createApp`，源码无 `createSSRApp`、服务端 renderer 调用或 `v-html`；Spring Boot 不执行此包，因此当前业务没有对应 SSR 输出面。升级到修复版仍可消除传递依赖风险。 |
| `vitest` / `tinypool`（devDependencies） | Vitest 3.0.5；tinypool 1.1.1 | Critical `GHSA-5xrq-8626-4rwp`（Vitest `<3.2.6`，Windows 上 UI 或 Browser Mode 文件读/写与执行面）；tinypool Critical `GHSA-5gmw-xhrv-c9v3`（`<=2.1.0`）和 `GHSA-85c8-ppgw-ccpr`（`<2.1.2`）；moderate `@vitest/mocker` `GHSA-82fw-gwwq-j7x9`（`>=2.1.0 <4.1.11`）。 | 全部仅供本地测试工具使用，不进入浏览器 bundle 或 Spring Boot 运行时。本项目只运行 `vitest run`，未启用 Vitest UI/Browser Mode；未把测试服务暴露到网络。仍然属于开发机工具链风险，故升级并回归。 |
| `esbuild`（Vite 构建传递依赖） | 0.24.2 | moderate `GHSA-67mh-4wv8-2f99`，`<=0.24.2`。 | 只在本地开发/构建服务中使用；公告描述恶意网页向 esbuild 开发服务器请求并读取响应。升级 Vite 后 esbuild 到修复版本，告警清除。 |

修复前原始审计证据：[`S08-npm-audit-before.json`](S08-npm-audit-before.json)、[`S08-npm-audit-production-before.json`](S08-npm-audit-production-before.json)。修复前 `npm audit fixAvailable=false` 反映锁定版本范围内 npm 没有给出自动修复操作；这不代表不存在经过手动核验的安全版本。

## 修复与回归

根据 advisory 的受影响范围及兼容 peer 范围，更新固定依赖：

- Vite：6.0.11 → **6.4.3**，覆盖截至 6.4.2 的 Windows deny bypass，并高于 dev-server 修复线。
- Vue：3.5.13 → **3.5.42**，修复 server-renderer advisory。
- Vitest：3.0.5 → **4.1.11**；Vitest 4 接受 Vite `^6.0.0`，修复 Vitest UI 路径和 `@vitest/mocker` 告警；Vitest 4 不再依赖 tinypool，移除两条 Critical worker advisory 路径。
- Vite 升级同时将 esbuild 提升至 **0.25.12**，超过 `0.25.0` 修复线。
- 运行 `npm install --save-exact vite@6.4.3 vue@3.5.42 vitest@4.1.11` 更新 package 与 lock。没有 `--force` 或不兼容 override。

验证结果：

| 命令 | 结果 |
|---|---|
| `npm test -- --run` | 4 个测试文件、9/9 项通过（Vitest 4.1.11） |
| `npm run build` | 通过；Vite 6.4.3，42 modules transformed |
| `npm audit --json` | 退出码 0，0 项漏洞 |
| `npm audit --omit=dev --json` | 退出码 0，0 项漏洞 |

修复后 JSON：[`S08-npm-audit-after.json`](S08-npm-audit-after.json)、[`S08-npm-audit-production-after.json`](S08-npm-audit-production-after.json)。

## 部署边界和残余风险

- S02 的原始版本基线已因 S08 审核要求更新；本次只变更前端直接版本与 lockfile，未改 API、架构或业务代码。
- 交付仍是本机单用户系统，Spring Boot 默认监听 `127.0.0.1`；Vite 默认 localhost。不要将开发服务或 Vitest UI/Browser Mode 暴露到公网/不可信 LAN。
- 当前完整与生产 npm audit 均为零告警。上游后续可能披露新公告，需按 lockfile 定期重审；这属于一般性未来风险，不是当前已知开放项。

## 溯源

- `frontend/package.json` 与 `frontend/package-lock.json` 中锁定精确版本和传递依赖路径。
- `frontend/src/main.ts`、`frontend/vite.config.ts`：确认 SPA 启动方式与 Vite host 配置。
- `rg -n "v-html|createSSRApp|renderToString|renderToWebStream" frontend/src`：源码无 SSR 或原始 HTML 输出调用。
- 公告原文：[@vue/server-renderer GHSA-g2v6-rqmx-r4w6](https://github.com/vuejs/core/security/advisories/GHSA-g2v6-rqmx-r4w6)、[Vite GHSA-p9ff-h696-f583](https://github.com/advisories/GHSA-p9ff-h696-f583)、[Vite GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff)、[Vitest GHSA-5xrq-8626-4rwp](https://github.com/advisories/GHSA-5xrq-8626-4rwp)、[Vitest/tinypool GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3)、[tinypool GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr)、[@vitest/mocker GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9)、[esbuild GHSA-67mh-4wv8-2f99](https://github.com/advisories/GHSA-67mh-4wv8-2f99)。
