# 本机既有数据副本溯源与真实数据闸门

日期：2026-10-20。仅搜索本机已存在文件，未连接任何MySQL/SQLite、原设备或远端，未启动库、未导出新数据。按用户要求，候选须同时满足“非合成、已获授权、来源可证明”，且任何合格候选使用前须再由用户确认。

## 结论

**本机没有符合闸门要求的真实数据副本。** 此结论指本次已检索项目、已知项目归档及本地WSL路径内，没有可证明满足该闸门的MySQL备份与配套私有文件；不把文件名带original、非空、真实HTTP、原模拟器Keystore或哈希完整等同非合成业务来源，也不声称已穷尽机器上每个无关目录。未认证文件不擅判为合成，而是“来源不足，不能选用”。本轮无可交用户确认使用的合格清单。

真实非空数据库升级、备份还原和依赖该可信输入的API/worker验证继续**阻断/未验**。不使用fixture补足，不放宽闸门；继续固定候选的代码、完整普通测试、构建和整包自洽性预检。实际test部署仍需用户另行明确授权。

## 搜索范围与证据

- D:/acrylic-pda-outbound/.local、outputs、server/private-files；demo-artifacts目录不存在。核已知1514/1445/1457、原M71与模拟器归档、来源脚本、导出日志、摘要及报告。
- E:/acrylic-archive-20261006、E:/acrylic-archive-unique-20260927、D:/acrylic-outbound-system；另检查本机Downloads/Documents、D:/Desktop和D:/disk-audits顶层项目/备份候选名，没有新增候选。
- 既有Ubuntu WSL内/opt/backups、/var/backups/acrylic-outbound-test、/opt/acrylic-outbound-test/deployment-evidence均不存在；历史远端路径不能视为本地副本，未SSH。
- 文件名扫描最大深度6，排除源码工作树、依赖/构建/浏览器缓存；原始路径与元数据清单在.local/deploy-remediation-20261020/local-data-paths.txt、local-data-candidate-inventory.json。大量自动测试SQL/SQLite按来源家族剔除，逐路径清单只保留本机，未上传数据库或业务内容。两次低效遍历已停并留命令，未完成部分不算已检索。
- 对103个E盘retention归档只读列成员，98个含测试SQL/DB；命名和来源为retention开发验证，不是现场业务导出。结果在local-data-retention-archive-index.json；不恢复、不连接。
- 重点dump重新算SHA并核历史dump-sha256；1514/1445/1457私有目录逐文件摘要在local-data-private-file-hashes.json（39份），不输出钥内容。

## 重点候选逐项裁定

下表路径以D:/acrylic-pda-outbound为根；时间为原dump尾行记录的墙钟时间，原件无时区时不补造时区。mtime只作文件属性，不冒充导出时间。

| 候选与路径 | 来源与导出时间 | 来源证明 | 是否符合闸门 |
| --- | --- | --- | --- |
| 1514：.local/goal-20261013/e05-preserve-1514-e06-closure-completed01/database.sql；配套.local/e2a-cross-20260925-1514/storage、private及data | acrylic_e2a_cross_20260925_1514，E06封存开发批次；2026-10-14 04:33:20 | 同目录identity.txt、dump-sha256.json、dump-stderr.log、result.json、shutdown.log；GOAL_E06_CLOSURE_ACCEPTANCE_2026-10-14.md | 否。dump来源/保全可证，含实际模拟器签名原件及明确合成隔离主管；非合成业务数据来源未证，不能把“原包”提升为现场库存/人员事实。 |
| 1445：.local/goal-20261013/returned-stock-preserve-1445-01/database.sql；配套.local/e2a-cross-20260925-1445/storage、private及data | acrylic_e2a_cross_20260925_1445，由P05/1401备份还原后做quarantine-history-09；2026-10-13 05:24:49 | 同目录identity/dump-sha256/result/shutdown；.local/goal-20261013/quarantine-history.ps1和quarantine-history-09/original-storage-hashes.json；GOAL_E04_P06_QUARANTINE_2026-10-13.md | 否。P05库存/人员由TemporaryNonzeroCancellationFixtureIT生成，purpose=SYNTHETIC_P05_NATIVE_DEVICE；真实模拟器签名不改变种子属性，分页另有rollback-only合成候选。 |
| 1457：.local/goal-20261013/returned-stock-preserve-1457-01/database.sql；配套.local/e2a-cross-20260925-1457/storage、private及data | acrylic_e2a_cross_20260925_1457，PC解隔离browser02开发批次；2026-10-13 06:36:56 | 同目录identity/dump-sha256/result/shutdown；GOAL_E04_P06_PC_QUARANTINE_2026-10-13.md；PC runtime/browser记录 | 否。报告明确库存/用户为合成隔离引导，真实HTTP审批与附件不是非合成来源证明。 |
| 1401上游：.local/goal-20261012/p05-device-1401/final-database.sql，配套.local/e2a-cross-20260925-1401/storage | P05原模拟器产品取消开发种子；2026-10-12 21:39:58 | 同目录database-identity.txt、dump-sha256.json、fixture-command.json；p05-provision.ps1；TemporaryNonzeroCancellationFixtureIT.java；GOAL_E04_P05_NONZERO_CANCEL_2026-10-12.md | 否。种子直接创建隔离人员、库存/政策，再由原模拟器生成设备事实；不是非合成库。 |
| M71原应用：.local/r02-original-upgrade/private-before.tar、backup.json及before/result JSON | 实体22D2910042的debug包私有目录；精确导出起止未单独记录，mtime为2026-09-18 13:48:39（不当导出时间） | .local/r02-original-backup.py明确adb序列号/归档命令；backup.json原哈希；capture-v3.log、verify-v7-final.log；docs/R02_TEST_DEPLOYMENT.md | 原设备出处与字节可证，但**不是MySQL**。结果只有claim_intent一条，业务工作/扫码/提交表为空，不能满足服务端真实非空升级/还原；未使用。 |
| M71安装前：.local/m71-preinstall-20260923/outbound-pda.db及-wal/-shm | 实体M71安装前保全；导出精确时间未录，DB mtime为2026-09-23 16:09:58 | docs/PDA_TEST_RELEASE_CODE22_2026-09-23.md记录稳定DB/WAL/SHM备份；本次重算DB SHA | Android原设备副本；非MySQL，业务非合成来源/完整外部来源链仍未证；不选用。 |
| M71其他：.local/m71-update-databases-outbound-pda.db、.local/r05-sync-m71.db | M71升级/同步本机归档；精确导出时间未证，mtime分别2026-09-19 00:10:26、02:48:48 | .local/m71-update-data-fingerprint-after.json与前者重算SHA相符；后者仅已有DB及历史R05记录 | 不能证明真实MySQL备份或非合成业务数据；不选用，保留原件。 |
| 开发库快照：.local/physical-local-20261018-01/development-before.sql | 既有acrylic_outbound_dev开发库备份；2026-10-18 18:33:33 | 同目录backend-preview.json、source-manifest.json；本次文件摘要 | 导出字节与开发来源可定位，未找到非合成业务来源证明及针对本闸门的使用依据；不选用，绝不连接其原库。 |
| 1392预览：.local/session-handoff-20261012-155634/preview1392-after-readonly-check.sql；.local/port-close-20261012-171722/isolated-1392-final.sql | P04隔离预览/结束保全；2026-10-12 16:05:19、17:18:09 | 相邻handoff/端口保全记录；docs/history/D01_D18_HANDOFF_2026-10-12_P04.md；本次SHA | 隔离开发链；非合成来源未证明，不选用。 |
| R05提醒：.local/r05-reminder-process/database-final.sql；R05恢复：.local/r05-archive-recovery/database-before.sql | 本机提醒/模拟器归档故障注入库；2026-09-19 18:47:36、17:40:51 | docs/R05_REMINDER_PROCESS_GUIDE.md、R05_ARCHIVE_RECOVERY_E2E_GUIDE.md与本次SHA | 否。前者报告明示专用合成数据，后者对合成设备注入审计丢失，不是完整真实数据恢复点。 |
| R06d副本：.local/r06d-audit-20260920/v30-synthetic-copy.sql、.local/r06d-submit-20260920/v32-synthetic-copy.sql | 本机合成审计/提交副本；文件名含synthetic，精确时间未另核 | 对应R06d本机日志/导出脚本；文件路径/字节列入清单 | 否。合成副本，不能换名当真实来源。 |
| .local/received-checkpoint-originals-1233/及.local/private-acrylic_outbound_test/ | 原接收JSON/bin与私有库存导入/提交文件，未发现配套可信MySQL完整dump | 原文件名/摘要与历史1233报告可追；本轮只读目录，不输出正文 | 单独业务/签名文件不构成可还原完整库，非合成来源未证；不选用。 |
| .local/r05-v28-stage/remote-evidence/、outputs/r05-v28/r05-backend-v28-20260919-02.tar.gz | 远端历史还原演练的本地报告/后端软件包 | R05_V28_REHEARSAL_GUIDE.md及prepare_rehearsal.py；原脚本备份在远端/opt/backups，tar只含软件20成员 | 本地没有该远端数据库/私有文件备份；不访问远端补取。软件包不是数据库副本。 |
| 其余e07、D/O/U、e2a、retention隔离dump/datadir与Android tar | 测试运行器、fixture、故障保全与回放家族；时间各原目录证据 | 逐文件完整路径元数据与各家族生成脚本、历史报告 | 未找到可认证非合成来源；不能用数量、非空或历史通过替代来源。未选择任何一个。 |

## 复核哈希

- 1514 dump：f519bc290c900f9bcbec55bbd24605945b3441a8fb3f45e0f1438b0bc871ced4。
- 1445 dump：0ee12b9e204b911db2acedd7de615e5bd449293b343c359b5cc1e27a444b2e11。
- 1457 dump：7580b1a0d7c4705390bae92ab31a086c0e1e6718b0951a512414d30b95c9ee85。
- M71 private-before.tar：65b384fc8aeb0ce49c04d972d54ff1ec0db4f640cbc2155a71d3ba823cb063b5，与backup.json一致。
- 其余重点摘要见.local/deploy-remediation-20261020/local-data-provenance-additional.json；所有dump/原目录/失败日志/XML均保留，无清理。

## 下一步与关闭边界

仅在新增可信来源证据并由用户确认具体候选后，才可创建独立可回退副本进行真实升级/恢复/API/worker；现阶段不执行。无需用户自行查找或导出资料。四断言审查、工具链统一及同SHA候选包验证独立推进；结果分别记录，不拼不同SHA。main、生产开关、正式签名、原库/现网、部署、Release保持关闭，localDeletionAllowed=false。
