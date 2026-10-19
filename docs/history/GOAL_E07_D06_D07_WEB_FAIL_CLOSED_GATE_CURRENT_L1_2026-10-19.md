# E07/D06-D07 Web 故障撤回 fail-closed 当前源 L1

日期：2026-10-19。本次只验证工作树中现有故障撤回 Web 门控与类型边界，没有修改该功能代码，也没有覆盖既有 `web/dist`。

## 当前输入

- 分支 `codex/development`，HEAD `91b4ef754676c2bfadcd856f2d0ff10216f3b9c6`；工作树包含继承 WIP，当前路径未提交。
- `web/src/orders/fault-withdrawal-feature.ts`：SHA-256 `666bbf511fda5434bcbe68fe4a15f47c84c15b22cf93aef184b39b8575a3da6c`。
- `web/tools/fault-withdrawal-feature.test.mjs`：SHA-256 `967278876ea993c1cd860996fa712ac57529a4105202f8c29fe515311448c18f`。
- `web/src/orders/OrderFaultWithdrawal.vue`：SHA-256 `87175c97a39a8f2ea8e491b6d582610b91fc040c4b435bcaace004d9b0d82885`；`web/package.json` SHA-256 `8dbec48e88276893be828378af1ff689443fffd986c4bdb9c07f85e089d713b6`；`web/package-lock.json` SHA-256 `87264c1b84d159caea7fc62a84683e9543c1c37ca3251177aebf3bb1e6a484e7`。
- Node `v24.14.1`。旧 run1614 只列过相同路径，未保存逐文件内容 SHA，因此不作为当前源码复用证明。

## 验证

- `node --experimental-strip-types --test tools/fault-withdrawal-feature.test.mjs`：退出 0，1 passed / 0 failed / 0 skipped。精确字符串 `true` 才开启；未设置、`false` 与 `TRUE` 均关闭。
- `npm run typecheck`：退出 0，`vue-tsc --noEmit` 通过。
- 代码审视确认：默认关闭时撤回入口不显示；有未决原请求时只保留原记录并显示关闭提示，不自动 POST；开启仍需 `order:recall` 和允许的订单状态。服务端鉴权、幂等与业务验收不由本 L1 证明。
- 既有 `web/dist` 在检查前为 55 文件、2,074,495 字节，manifest SHA-256 `432b2121bc02b9fb6790c318b7ea317f887814a8fd403bd89d56ebf227133674`；为保护继承产物，本次没有运行会覆盖其默认输出目录的 Vite build。
- 14:00 UTC 后核：仅系统 MySQL `3306/33060` 与共享 ADB `5037` 监听；无项目 Java、模拟器、任务 MySQL 或任务 Web/HTTP 进程。C/D/E 可用字节 `74343481344 / 44464123904 / 195634053120`。

## 边界

这是当前工作树的 Web fail-closed 有界 L1，不计 D06/D07 L2、E07 整体、真实 HTTP/Android/Chrome/Python 组合、现场身份、D17、E06 L2、D18 L3 或第 8 节。源代码仍是继承 WIP，测试证据不改变其提交状态；`localDeletionAllowed=false` 与生产边界保持关闭。
