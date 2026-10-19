# E08/D17 服务端 fail-closed 配置 L1（run01，2026-10-19）

本轮消费既有服务端配置测试，核对取消的恢复接收确认入口和 TEMP 接收确认入口仍保持 default-off。未启动 HTTP/MySQL、旧 bridge、模拟器或原 PDA。

## 输入与验证

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD `9c6feaffe39633206734accac7c945a4b4bfcc1f`。
- 命令：

```text
.\gradlew.bat -p server test --tests com.acrylic.outbound.handover.HandoverRecoveredConfirmationConfigurationTest --tests com.acrylic.outbound.handover.HandoverTemporaryConfirmationConfigurationTest --no-daemon --offline --console=plain
```

- 结果：Gradle 退出 0；恢复确认配置测试 3/0/0/0，TEMP 确认配置测试 2/0/0/0，跳过 0。
- 直接断言：默认配置不创建恢复 controller/endpoint；仅开启 `outbound.handover.commands.enabled` 不创建恢复信任 endpoint；同时开启恢复确认但缺少独立信任时 context 启动失败；TEMP 确认默认/显式 false 均不暴露 consumer。
- 证据根：`.local/goal-20261019-resume/failclosed-server-run01/`，含 `gradle.log`、`result.json` 和两份 JUnit XML。

## 范围结论

该切片只证明服务端配置层 default-off 和缺少独立信任时 fail-closed；没有真实 HTTP/MySQL、权限矩阵、事务/并发/回滚、Chrome、Python、Android 或现场身份事实。与 Android gate L1 run02 共同补强取消链边界，但不计 D06/D07、D13/D14、D17、E06 L2、D18 L3、第8节或总目标通过。`localDeletionAllowed=false`、七类生产决定、TEMP 生产归档、正式签名、main、部署和现网边界保持关闭。
