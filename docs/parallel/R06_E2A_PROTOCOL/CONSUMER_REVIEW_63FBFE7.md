# W1 `63fbfe7` 独立消费者复验准备

状态：`PREPARED / WAITING_FOR_W1_REVISED_SHA`。

本记录绑定 W1 修正版基线 `63fbfe7d02d39d5bf640d487958772cbaf6b90ef`。桥接入口为同目录的 `ProductionBridge.java`，只调用生产 `E2aProtocol`、`SubmissionJson` 和 `OfflineSigner.PublicKeyView`，不启动 Spring、数据库、HTTP、文件写事务或业务决定入口。

## 固定生产源码清单

以下源文件必须从待运行的 W1 固定 SHA 取出并编译；不能使用旧 `bridge-classes` 代替生产源码：

```text
server/src/main/java/com/acrylic/outbound/temporary/protocol/E2aProtocol.java
server/src/main/java/com/acrylic/outbound/temporary/protocol/ProtocolJson.java
server/src/main/java/com/acrylic/outbound/submissions/SubmissionJson.java
server/src/main/java/com/acrylic/outbound/pda/OfflineSigner.java
server/src/main/java/com/acrylic/outbound/inventory/ImportFiles.java
```

编译前记录：

```powershell
git show <fixed-sha>:<path> | Set-Content .local\e2a-consumer-source\<leaf>
git show <fixed-sha>:server/src/main/java/com/acrylic/outbound/temporary/protocol/E2aProtocol.java | Get-FileHash -Algorithm SHA256
```

实现类路径和依赖必须来自同一个 `<fixed-sha>`；桥接输出不得混用当前工作树生产 class。输出保存到 `.local/e2a-consumer/`，class 文件不得提交。

## 窄负向矩阵

`ProductionBridge` 每次运行重新生成 EC/RSA key pair 和 JSON 对象，逐项输出 `case=<id> result=<true|false>`：

- STOP 正向 P-256/SPKI/keyId；缺 `schemaVersion`、字符串数字 `schemaVersion`、错误 `keyId`、空 core、P-384 公钥均应拒绝。
- RSA 正向 RS256 receipt；合法 RSA 重签但 actor/source/epoch facts 改变、以及空 facts 均可在签名层自洽，但独立 `trustedFacts` 比较必须为 false；未知 `kid` 必须拒绝。该项明确区分“签名有效”和“可信事实匹配”。
- PAGE/manifest 合法 shape；派生字段写回 core、数字字段改成字符串、manifest `setHash` 写回均应拒绝。

本矩阵只验证生产纯函数的可观察返回值；不替代 V35 迁移、持久化、可信占用、HTTP/设备或决定/放行验收。W1 修复空 STOP core、宽松数字/空 facts 后，才执行一次实际桥接并保存完整 stdout、stderr、退出码、JDK 版本和 SHA-256。

## `6beb4f6` 窄跑结果

实际消费 SHA：`6beb4f6c45c37f63fe416e62653a5bc98991cb53`。生产源码从该 SHA 归档到临时目录后执行 `:server:compileJava`（成功），再用同一编译产物执行 `:server:runBridge`（退出 0）。完整日志保存在 `.local/e2a-bridge-6beb-rerun.log`，临时 class 和源快照在 `.local/e2a-run-6beb/`，均不入 Git。

通过项包括：合法 P-256/keyId、STOP 缺字段/字符串数字/错误 keyId/空 core/P-384 拒绝；RSA 合法重签错 facts 仍能验签但 `verifyTrustedRsaReceipt` 返回 `UNTRUSTED_FACTS`；未知 `kid`、空 facts 不可信；PAGE/manifest shape、派生字段和字符串数字负向拒绝。

明确失败/阻断项：

- `stop_schema_overflow_rejected=false`：`schemaVersion=4294967297` 被 `asInt()` 截断后接受，未满足严格溢出拒绝。
- `stop_zero_epoch_accepted_by_contract=false` 与 `stop_zero_high_watermark_accepted_by_contract=false`：当前生产 `positive()` 拒绝合同允许的零值边界。
- 固定向量 `e2a-v1-empty-set-vector.json` 的 page hash 重算通过；manifest/set hash 在 `workRound` 字符串上触发生产 `NUMBER`，两项为 false；完整 STOP 也为 false，因为该资源的 `stopCoreUtf8` 仍只有旧四字段，不能满足 15 字段 `validateStopCore`。向量文件 SHA-256 为 `FF3764A7814909C9F80CA4B5D6711953F7F300A6A54480ECA11939225C70EB9F`。

生产源 SHA-256：`E2aProtocol.java` `C8D2A4E53DCBC8C37796B311A055555CF8511CA2EC9075E78587559F2C36A40F`；`ProtocolJson.java` `731DB23EBE0C9E3B9DFFFC4A8F6455FE4CA0FA96BD75C851BF8864996C9B5070`；`SubmissionJson.java` `BD35AF05BB0AC0523DD9A1E4E0F959C488A193242D0F29342A206E750190A7DC`；`OfflineSigner.java` `BC0D19EE94D856A3E10EB4D163F6094EDB9482D0A3C88F7C940AD20DCE89C818`；`ImportFiles.java` `F69E3E612A299A8368C552CAB2F6E77D0212B6E5901456A294C9A56870BE83DC`。
