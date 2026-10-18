# D13 恢复事务与导出浏览器组合（run742）

本记录登记一个全新隔离候选，继续验证不依赖已暂缓 UI 夹具的 D13 恢复事务边界。它不恢复 recovered-confirmation、换机进度承接、多跳或接收者确认/激活链。

## 范围与输入

- 工作树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`，运行输入 HEAD/远端为 `639eaa0c62565328294582e74bce9929271b6fc2`。
- 接管与继承输入保全在 `.local/goal-20261018-resume/takeover06/`；本切片没有覆盖或重排继承改动。
- 命令：`powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-cross-source-verify.ps1 -Run 742 -RecoveryWriterTransaction -DbPort 19650 -HttpPort 19651`。
- 隔离实例为 `.local/e2a-cross-20260925-742/`，数据库为 `acrylic_e2a_cross_20260925_742`，账号、datadir、端口和认证停机身份由 `connected-identity.tsv` 与 `shutdown-identity.tsv` 绑定；两文件 SHA 相同。

## 实际结果

- `E2aRecoveryWriterTransactionIT` 集成 XML 为 `tests=1, failures=0, errors=0, skipped=0`，Gradle、runner 均退出 0，`test-completed.marker` 存在。
- 运行器先在非空种子上执行恢复事务检查，再完成全量 Flyway 迁移；XML 记录迁移成功。`admitted-datasource-check.txt` 覆盖真实 MySQL DataSource 借用、更新、自动提交、关闭和拒绝 unwrap；`leased-transaction-checks.txt` 覆盖 commit、afterCommit、rollback、SQL 错误、outer/nested、stale instance 与 unknown commit。
- 同一集成测试生成 XLSX、SCAN_XLSX、PDF 及 summary 导出，保留逐导出快照、重放和失败围栏文件；内置 Python/Java 只读检查实际执行。`localDeletionAllowed=false` 和 `ordinaryWritesAllowed=false` 仍由检查结果保留。
- Chrome 产品入口 `web/tests/temporary-export.spec.ts` 实际运行 `1 passed`；浏览器日志和账号/订单绑定输入保存在 `private/browser.log` 与 `private/temporary-export-browser.json`。
- MySQL 由隔离账号认证停机；`19650/19651/19652` 无监听，浏览器进程已退出。共享 `3306/33060`、ADB、外部 Vite/uvicorn 仅观察，未停止或复用。

## 证据绑定

| 路径 | SHA-256 |
| --- | --- |
| `.local/e2a-cross-20260925-742/integration-result.xml` | `62B0A197AF8C00F18FE22D32A3CC6CF8A0BF8B6E61EAA31C5FDE5C4C162DB8B8` |
| `.local/e2a-cross-20260925-742/integration.log` | `C39A4C713C22319B0D31BAFBD1EBBD532511E0F8A4C3FEAB2582362F44BEA212` |
| `.local/e2a-cross-20260925-742/private/browser.log` | `6AC1FF53051FBEE35D2BA5A96B52FDBEB900444ABB3526A1B88E123917850CDF` |
| `.local/e2a-cross-20260925-742/connected-identity.tsv` | `AF62B656402C12D55184579EBFCD4A6275B030ACFCAADEC140AB6608FFFBEAC7` |
| `.local/e2a-cross-20260925-742/shutdown-identity.tsv` | `AF62B656402C12D55184579EBFCD4A6275B030ACFCAADEC140AB6608FFFBEAC7` |
| `.local/e2a-cross-20260925-742/test-completed.marker` | `7B54E0CB2542FFACA425F72EDE143327404FE5B8FBF4FFC1CD8D759BA225466B` |

运行输入绑定的直接受影响路径及 SHA：`tools/e2a-cross-source-verify.ps1` (`66F9FCF696B7DDE1B0B8CD4F14791E07AA7A5B2BCAA76D289FA47B7FEFCCA8C4`)、`E2aRecoveryWriterTransactionIT.java` (`AF6AA304B7D2E961336A8F0BD7767295E9244FEEBD7C1F4D8A7178871CA01EA6`)、恢复租约/效果门/SQL选择器及 `RecoveryLeaseRetirementTest`（详见 takeover06 dirty-hashes）、`TemporaryExportChecks` (`F9159EB8FF373BC3CE055F7365C16062E816E8F6C7786DB83A7905861C9C450F`)、`E2aTemporaryExportBrowser.java` (`7FC5A004B7C76CA6D9B6AC8B646A7DBD4548B752D908EA7F43418A3A287C88C6`) 与 `temporary-export.spec.ts` (`C1DDA9581658AB8067FF1030F8AFAF9DCF93494A1FA9C30FD01F5C52080648FF`)。

## 结论与边界

run742 是当前工作树上的 D13 恢复事务/导出/Chrome 有界组合证据，满足本切片的真实 HTTP/MySQL、非空迁移、事务失败路径、renderer/export、浏览器和隔离停机检查。输入身份、密钥、订单、仓库和浏览器会话均为合成隔离事实；内置消费者不是独立人员或现场回执，也没有 Android、原 PDA、原模拟器、外置介质或生产数据库证据。

因此本候选不计 D13 整体、D14–D18、E06/E07/E08 L2、D06/D07 L2、D18 L3 或第8节闸门。生产默认关闭、七类生产决定、TEMP 生产归档、`localDeletionAllowed=false`、原库/现网、正式签名、部署与发布边界均未改变。
