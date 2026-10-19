# E07/D06-D07 Web fail-closed 当前源复核 L1（run02，2026-10-19）

本切片复核当前工作树中的故障订单撤回 Web 门控。它只执行门控单测和 TypeScript 类型检查，不构建或覆盖既有 `web/dist`，不启动 Web/HTTP、MySQL、Android 或模拟器。

## 输入与命令

- 树 `D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD `f02c5a03530558770ffbd4f833a7fa26982e5904`。
- 直接输入路径为 `web/src/orders/fault-withdrawal-feature.ts`、`web/src/orders/OrderFaultWithdrawal.vue`、`web/tools/fault-withdrawal-feature.test.mjs`、`web/package.json`、`web/package-lock.json`；逐文件 SHA 与长度保存在 `.local/goal-20261019-resume/web-failclosed-run02/input.json`，该文件 SHA-256 为 `4122422AEE5F3BB95DDF353A4520C584F8841569D4C2D649E193854F248A6188`。

```text
node --experimental-strip-types --test tools/fault-withdrawal-feature.test.mjs
npm run typecheck
```

## 结果

门控单测退出 0，1/0/0/0；`feature-test.log` SHA-256 `B381CF75A79CFF5FA34960EF26FFE6C490260B6B5AD9F0A3008B1C4C2400E87B`。`vue-tsc --noEmit` 退出 0；`typecheck.log` SHA-256 `BBBDDF44DDEABFA4B4A62A9134FCFB5F3E0DBABEF475FC9C5DAE08F9E0635440`。结果回执 SHA-256 `CEE347813C470C5C1EF9F0351227B73A60C4C875A039E6ED6A6D800AE6DD4ED7`。

当前源的门控行为保持：只有显式字符串 `true` 才开启故障撤回；未设置、`false` 和大小写不同的值关闭。关闭时撤回按钮不显示，已有未决浏览器请求只保留原请求并提示未发送，不自动 POST；开启时仍要求 `order:recall` 和允许的订单状态。`web/dist` 检查前后仍为 55 个文件、2,074,495 字节，本切片未运行会覆盖它的 Vite build。

## 边界与下一步

本轮没有验证真实 HTTP/MySQL、Chrome 页面、Android 消费、Python、非空迁移、权限/并发/事务/末 SQL 或现场身份，因此不计 D06/D07 L2、E07 整体、D17、E06 L2、D18 L3 或总目标。系统 Node repl 与 Codex UI server 仅观察，项目 HTTP/Java/模拟器均未启动；`localDeletionAllowed=false`、生产开关、正式签名、部署、Release、`main`、原库/现网和取消的换机确认/激活链保持关闭。
