# E07/D06-D07 Web 故障撤回 fail-closed 入口收口（run01，2026-10-18）

本切片处理一个前端边界缺口：服务端 `outbound.recall.fault-withdrawal-enabled` 默认关闭，
但 Web 订单异常页仍会展示“撤回故障订单”入口，用户点击后才得到服务端拒绝。现改为
只有显式隔离构建变量 `VITE_FAULT_WITHDRAWAL_ENABLED=true` 才显示入口；默认构建保留原
记录/占用且不自动恢复未确认对话框。服务端默认值、权限校验和业务事务没有改动。

## 输入、改动与验证边界

- 树：`D:/acrylic-pda-outbound`，分支和 HEAD/远端以看板事实块及
  `.local/goal-20261018-resume/web-failclosed-run01/input-snapshot.json` 为准；继承改动未清理、
  未还原、未重排，未执行 `git add .`。
- 写权：本聊天是唯一源码写者；本切片没有启动或连接隔离数据库、HTTP、模拟器或原 PDA。
- 改动路径 → 影响层 → 必跑清单 → 复用依据：
  - `web/src/orders/fault-withdrawal-feature.ts` → Web 构建策略；纯函数默认拒绝，单测、`npm run typecheck`、Vite 默认/显式开启构建；复用服务端默认关闭政策和 `OrderFaultWithdrawal.vue` 现有权限/原键持久化。
  - `web/src/orders/OrderFaultWithdrawal.vue` → Chrome 入口；关闭时不显示入口、不自动弹出未决请求，并保留历史回执/原请求；同上 Web 类型检查/构建；复用 `fault-withdrawal-pending.ts` 的原键、正文和恢复代次保全。
  - `tools/e07-d06-d07-emulator-run.ps1` → 隔离运行器；显式向 Vite 传 `VITE_FAULT_WITHDRAWAL_ENABLED=true`，并登记环境；PowerShell 解析；复用既有 run902/739 的隔离 Web/HTTP 运行器边界。
  - `web/tools/fault-withdrawal-feature.test.mjs` → 纯函数消费者测试；缺省、`false`、大小写变体拒绝，精确 `true` 放行；Node 原生测试。

## 实际结果

- `node --experimental-strip-types --test tools/fault-withdrawal-feature.test.mjs`：1 passed，0 failed，0 skipped。
- `npm run typecheck`：退出 0。
- `npm run build` 默认环境：退出 0；显式 `VITE_FAULT_WITHDRAWAL_ENABLED=true`：退出 0；清除变量后恢复默认构建：退出 0。默认与最终 `dist/index.html` SHA 一致；矩阵见 `.local/goal-20261018-resume/web-failclosed-run01/build-matrix.json`。
- PowerShell AST 解析与 `git diff --check` 通过。默认 bundle 中不存在环境变量字面量；构建警告仅为既有 chunk 体积提示。
- `web/dist` 为可重建构建产物，清理前约 2,074,495 bytes；无本项目消费者，但 Windows 执行策略在删除前拒绝命令，未绕过，释放 0 bytes，记录见 `.local/goal-20261018-resume/web-failclosed-run01/cleanup-dist.json`。
- 未执行真实 Chrome/HTTP/MySQL/Android/原 PDA 组合，本切片不计 D06/D07 L1/L2、E07 L2、D17、E06 L2、D18 L3、第8节闸门或总目标完成。

## 保持的边界与下一步

服务端 `FAULT_WITHDRAWAL` 默认拒绝仍是最终业务闸门；显式开启只允许隔离运行器使用，
不开放生产决定能力，不恢复取消的换机承接、多跳或接收确认/激活链，
`localDeletionAllowed=false`、正式签名、原库/现网和发布边界保持关闭。真实 Chrome 入口、
Android 新设备从头作业、非空迁移和完整 D06/D07 组合仍需新的候选输入；旧失败原件继续保全。
