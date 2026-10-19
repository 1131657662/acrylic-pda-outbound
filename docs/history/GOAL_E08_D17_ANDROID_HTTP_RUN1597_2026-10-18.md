# E08/D17 Android 真实 HTTP 候选 run1597（2026-10-18）

本报告登记修复 V36 `source_evidence_set` 列兼容后的全新隔离候选。输入为一次性合成隔离数据；未恢复已取消的换机承接、确认或激活链。

## 输入与运行

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`。
- 命令：`powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 1597 -Android -DbPort 19347 -HttpPort 19470`；TEMP/TMP 指向 `.local/goal-20261018-resume/runner-tmp-1597/`。
- attempt `.local/e2a-cross-20260925-1597/`，隔离库 `acrylic_e2a_cross_20260925_1597`；`connected-identity.tsv` 与 `shutdown-identity.tsv` 字节一致，binlog 开启，认证停机成功。
- Flyway 从非空 V34 种子迁移到 V36；Android 子进程执行真实 HTTP/Room 客户端恢复流程。

## 结果

- `integration-result.xml`：`E2aAndroidHttpIT`，`1/0/0/0`，退出 0；`test-completed.marker` 存在。
- `android-result.xml`：`E2aRealHttpRecoveryTest`，`1/0/0/0`；`android-result.json` 为 `PARTIAL`，包含 OPEN/RAW/PAGE/STOP/VERIFY 回执及五次丢回执恢复输入。
- 端口 `19347/19470/19471/19472` 在认证停机后均无监听；未触碰系统 MySQL80、USB PDA 或正式签名。
- 本候选只计 Android + 真实 HTTP/MySQL + 非空迁移的局部证据，不计 D17 整体、E06 L2、D18 L3、第8节闸门或总目标。合成身份、隔离数据库和 Debug/测试签名不能替代原 PDA、原身份或现场事实。

## 代码与定向验证

- `SourceEvidenceIntakeAuditPrefix`：V36 无 continuation 表时使用初始 TEMP 来源查询；仅两张 continuation 表同时存在时使用 continuation 查询。
- `SourceEvidenceHandoverFacts`：仅两张 handover submission/activation 表同时存在时读取 handover 事实。
- `SourceEvidenceIntakeSetWriter`：动态探测 `order_scan_high_watermark`；V36 查询使用 `0` 且 INSERT 省略该列，后续 schema 使用真实列。
- `server compileJava compileTestJava` 通过；受影响定向类 29 项中 28 项通过。唯一失败为继承 Mockito 夹具 `conflictingFormalSubmissionDigestFailsClosed`，与本次 V36 列兼容补丁无关，原 XML/日志保留。
- `git diff --check` 与 `tools/version_facts.ps1 -Check` 通过。

## 证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditPrefix.java` | `25FF6D6319384C0CBF1D746711C57DA4232ACCC34785D6E8E68EB38DCB81AAE0` |
| `server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceHandoverFacts.java` | `A7E3C6C310CB336C53DF4F7AA2B49668BB5BFEB56417625AA1933A4DDFE6173B` |
| `server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeSetWriter.java` | `853FE2E07CF18F9135B8EFE793F88F35FC5C0DF00EB923CEB10FAE10DEEAD27E` |
| `.local/e2a-cross-20260925-1597/integration-result.xml` | `A6BE47D4C3B32EF0B9AA036EEFF40A58B66C6D906B4786CCD44A0851EC44B992` |
| `.local/e2a-cross-20260925-1597/android-result.xml` | `6596D44005B5B9BFA019376D9A20FDB774509BEC6F8035714AE52F6656ED4229` |
| `.local/e2a-cross-20260925-1597/android-result.json` | `8EB366459BFEF546AD38ADE39B489917123952BEEEBCFDB6B9B9E5D2D52FD5E6` |
| `.local/e2a-cross-20260925-1597/private/android-http.log` | `6E5662FBBC255E4D6B9B4B62BF41BFA18398427AA3AD1EBFC08410BE4CC55CAD` |
| `.local/e2a-cross-20260925-1597/connected-identity.tsv` | `051BD33C2DFFBAB876BA3FF46D2B07EA3EA3DAF76EA75DED307AE4DFEF40556D` |
| `.local/e2a-cross-20260925-1597/shutdown-identity.tsv` | `051BD33C2DFFBAB876BA3FF46D2B07EA3EA3DAF76EA75DED307AE4DFEF40556D` |

失败的 run1596、V36 run05、compile 前置 TEMP 目录失败及定向单测 XML/日志继续保留；后续仍需受影响 E06/E07 组合、Chrome/Python/原模拟器和最终恢复全链验证。
