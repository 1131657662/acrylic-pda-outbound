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
