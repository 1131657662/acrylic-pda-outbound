# E07/D14 Android 与真实 HTTP 运行失败（run727）

## 结论

run727 是一次隔离 Android/真实 Spring HTTP/MySQL 候选运行，结果失败，不能计入 E07、D14、D17、E06 L2、D18 L3 或第 8 节闸门。失败发生在 Android 首次读取请求阶段，服务端 `SubmissionStore.find` 访问了 V36 尚不存在的后续表；没有发生提交、账务、占用或回执写入。本次失败不重跑，按后续全新输入继续验证。

## 运行与输入

- 运行命令：`-Run 727 -Android -DbPort 19339 -HttpPort 19464`。
- 隔离数据库：`acrylic_e2a_cross_20260925_727`，Flyway 从非空 V34 迁移至 V36；MySQL 已由 `e2a_cross_20260925_727` 身份认证停机。
- Android 输入：`.local/e2a-cross-20260925-727/private/android-http-input.json`，目的为合成 Android Room 真实 HTTP 恢复验证；不代表原 PDA、原身份或现场回执。
- 运行前后快照与命令：`.local/goal-20261018-resume/e07-android-http-run02/`。快照记录继承 dirty 树及未提交保护，未在 run727 之后修改产品源码。

## 失败事实

- `E2aRealHttpRecoveryTest.actualRoomFreezeAndFiveLostReceiptsRecoverOverRealHttp`：1 test / 1 failure，Android 收到 `ApiFailure`。
- `E2aAndroidHttpIT.androidOriginalRoomRequestsSurviveLostHttpReceiptsAgainstNonemptyMysql`：1 test / 1 failure，首次服务端读取返回 `INTERNAL_ERROR`。
- `SubmissionStore.find` 的静态 SQL 同时 `LEFT JOIN temporary_source_continuation_submission`、`temporary_source_continuation_activation`、`temporary_handover_submission` 与 `temporary_handover_activation`。这些表在该 V36 输入中不存在，MySQL 抛 `SQLSyntaxErrorException`。
- 服务器随后正常 graceful shutdown；隔离 `mysqld` 日志记录认证关闭，端口已释放。
- 诊断查询未发现新的 `submission_payload` 或 `source_evidence_intake_receipt` 写入；失败发生在首次读取，未进入业务提交。

## 证据绑定

| 路径 | SHA-256 |
| --- | --- |
| `.local/e2a-cross-20260925-727/android-result.xml` | `FA69D47B7F89858B9848CE958FA956423B61A6E1150804884D9B058373E57A36` |
| `.local/e2a-cross-20260925-727/integration-result.xml` | `80D96C2D489ACEF3189BC1224953B5445E126812D5A5D48450AB578DF1BDFD12` |
| `.local/e2a-cross-20260925-727/private/android-http.log` | `6F0B7C7160DC3D77DFC9F1148303F6F0A0F5F826168D07D62CFF73505E72F01A` |
| `.local/e2a-cross-20260925-727/private/server.err.log` | `7E3A7CB22531EC7826B52A856A82EE780FFBC18B9CBE76193C647D52849F87F9` |
| `.local/e2a-cross-20260925-727/connected-identity.tsv` | `56A08A84746FB90DAD677952348CDF1A88B0D1670B73D46AC7134D0CDF78CF5B` |
| `.local/e2a-cross-20260925-727/shutdown-identity.tsv` | `56A08A84746FB90DAD677952348CDF1A88B0D1670B73D46AC7134D0CDF78CF5B` |
| `.local/e2a-cross-20260925-727/private/android-http-input.json` | `F0E51F2B97F806ABD9AA070BC6736C4589B9978DCA4CAC691E2B00FDCE1E6F3D` |
| `.local/goal-20261018-resume/e07-android-http-run02/command.txt` | `D6563E464DE99E3C393F130424F3BF98B2A4474AD7AE71B07E7087B7E76F0E8A` |
| `.local/goal-20261018-resume/e07-android-http-run02/status-after.txt` | `D6F66F2A943A458F4D1C0F74DA8D492A04CDDE2B5785CA66753A82FC000CEE2A` |
| `.local/goal-20261018-resume/e07-android-http-run02/tracked-after.patch` | `8FEBF696F5B1D27BDA56143A2184B65A61781FF2D5F4112991A488AD0F6B2BBF` |

## 处置

`SubmissionStore` 后续已改为按实际 schema 动态选择兼容 JOIN，随后全新 run1597 通过了其受影响的 Android/真实 HTTP 局部验证；run727 仍作为原始失败保留，不能用修复后的运行替代本次失败事实。未恢复已取消的确认/激活/换机承接链，也未连接原库或现网。

