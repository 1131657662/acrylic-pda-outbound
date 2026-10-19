# E07/O05 TEMP 与 FORMAL 归档恢复组合（run724，2026-10-18）

本报告登记一次新的隔离恢复候选。它验证了归档文件、原审批闭合、故障注入拒绝和恢复后真实 HTTP 读取；所有身份、文件和数据库均为一次性隔离输入。该候选不恢复已取消的换机承接、接收确认或激活链。

## 输入与隔离

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；本次没有产品源码改动。
- 候选：run `724`；attempt `.local/e2a-cross-20260925-724/`；数据库 `acrylic_e2a_cross_20260925_724`；账号 `e2a_cross_20260925_724@127.0.0.1`。
- 端口：MySQL `19334`，HTTP `19454`，辅助端口 `19455/19456`。结束时四个端口均无监听，脚本通过专用账号执行认证停机。
- 命令：`powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 724 -ArchiveRecovery -DbPort 19334 -HttpPort 19454`。
- 断点和运行前后快照：`.local/goal-20261018-resume/e07-archive-recovery-run01/`；输入清单、状态、进程、端口、磁盘和候选文件哈希均保留。

## 实际结果

- JUnit `integration-result.xml` 的 `E2aTemporaryArchiveRecoveryIT` 为 `tests=1, skipped=0, failures=0, errors=0`，Gradle 退出 0，`test-completed.marker` 存在。
- 运行器在非空迁移后创建 TEMP V2 与 FORMAL V1 原件，保存审批闭合、原件引用和归档证明；恢复器将 SQL/存储文件复制到恢复库后再次启动真实 Spring HTTP。
- `archive-inspect-source.log` 与 `archive-inspect-restore.log` 均为 PASS：TEMP V2/FORMAL V1 归档、提交归档文件引用和临时归档命令均由独立 Python 消费者实际解析。
- `archive-restore-faults.log` 的 11 项故障注入均通过：损坏/缺失原件、缺失命令审计、未知历史签名、错误命令事件、错误幂等引用、错误归档身份或能力、当前撤权不改历史审批等均被拒绝或保持闭合。
- `archive-restore-summary.json` 为 `{"tables":179,"files":15,"status":"PASS"}`；恢复前后快照相等。迁移探针记录于 `archive-probe-schema.txt`，不把具体版本数字作为本报告状态事实重复维护。
- 恢复后 HTTP 检查通过，原审批闭合与归档字节可读取；没有开启生产归档、原库/现网、正式签名或真实外置介质。

## 关键证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `tools/e2a-cross-source-verify.ps1` | `66F9FCF696B7DDE1B0B8CD4F14791E07AA7A5B2BCAA76D289FA47B7FEFCCA8C4` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryArchiveRecoveryIT.java` | `DBB55C39A7B27D10F43C6FFD8C01FA747A090A2AA47A111F235868C590D8C019` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aArchiveRecoveryPython.java` | `A6F432390FCB836A57FEF77C7C67AEC0311581182A77757110A1E5162BF7D332` |
| `.local/e2a-cross-20260925-724/integration-result.xml` | `338755DA97F6946CC895982DC1F3CA473F09631B9936B57D96568771FFAD510D` |
| `.local/e2a-cross-20260925-724/archive-restore-summary.json` | `DDB9942BD32183477E25E5FEB383597A7D409A89E87C2012794278CB1B4F5477` |
| `.local/e2a-cross-20260925-724/archive-inspect-source.log` | `497AD2DD5F8A89DF589E7FFB10570D3D6B60C0B81FB2BD7094F3BDBDB5BD9887` |
| `.local/e2a-cross-20260925-724/archive-inspect-restore.log` | `497AD2DD5F8A89DF589E7FFB10570D3D6B60C0B81FB2BD7094F3BDBDB5BD9887` |
| `.local/e2a-cross-20260925-724/archive-restore-faults.log` | `217E382AEF09C0DBFC86B56B4AF4EF0553CDCF2CCABD555C6F987937CF1AB6E3` |
| `.local/e2a-cross-20260925-724/connected-identity.tsv` | `513A32C186FC0B19ABB64B0097C2A690D2D6A923A4DDE46A7E487CE48A1D7FEB` |
| `.local/e2a-cross-20260925-724/shutdown-identity.tsv` | `513A32C186FC0B19ABB64B0097C2A690D2D6A923A4DDE46A7E487CE48A1D7FEB` |

## 范围结论

这是 E07/O05 的隔离组合开发证据，补足一次新的 TEMP/FORMAL 归档恢复输入。未执行 Android 产品会话、Chrome、原模拟器、真实 PDA/原身份或外置介质取回，因此不计 E07 L2、D14 整体、O05 整体、D18 L3、第 8 节闸门或总目标完成。attempt、日志、数据库和运行前后快照继续保留，后续不得把本候选当作统一最终候选。
