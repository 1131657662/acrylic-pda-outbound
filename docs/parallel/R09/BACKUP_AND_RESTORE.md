# 备份验证与隔离恢复手册

依据：V2.3 §13.3/13.4、[R02 部署记录](../../R02_TEST_DEPLOYMENT.md)及 `deploy/test/backup.sh`、`backup_facts.py`、`verify_restore.py`。本文件不授权生产操作。

## 恢复集清单

下表中的 V21 数量属于 2026-09-18 历史演练。2026-09-20 的 V28 增量见 [R05 接入记录](R05_READY_EXTENSION.md)，最新 V32 的93表/198组外键及原资格/FH计数器证据见 [R06 接入记录](R06_READY_EXTENSION.md)。全库恢复额外包含同步投影、PDA审计/检查点、后台任务/提醒、成功归档和审计归档表；不筛表排除这些材料。

| 材料 | 当前存放/恢复要求 | 本轮检查与未完成边界 |
| --- | --- | --- |
| 数据库 | 本项目业务库全库，含 Flyway 历史、身份/设备公钥、授权、幂等、原始事实、取消终态、结果、占用、账务、同步和审计 | 76 表集合/结构/记录数/CHECKSUM、规范化全量数据 dump SHA-256；170 组外键反向核验。实验执行现有 V1～V21 迁移，文件未改 |
| 导入原文件 | `files/inventory-imports/<sha256>.txt`；由 `inventory_import.file_key` 引用 | 文件存在、摘要和全目录文件清单一致；合成场景覆盖 4 份原文件 |
| PDA 冻结正文和分块 | `files/submissions/`；`submission_payload`、`submission_chunk.file_key` 已是相对完整路径 | 正文摘要/字节数/引用检查；本轮 3 份正文。分块表检查具备实现，但本次数据无分块，不能声称分块场景通过 |
| PC 冻结正文/命令回执 | `pc_submission.payload_text/receipt_json`、`pc_command_receipt` 等在数据库内 | 随全库恢复；本次 PC 表无业务样本，不声称 PC 专项恢复完成 |
| 回执/取消证明 | 当前 PDA 终态在 `submission.receipt_text`；密钥标识与原签名正文不变 | 恢复后实际 RSA 验签并核对成功回执的包数、重量、扣账和正文摘要；3 成功/1 ABORT。没有重建已丢失的回执/墓碑 |
| V27成功归档 | `submission_archive` 原确认及 `files/submissions/<sha>.json` 中原正文、完整签名回执 | 对齐签名/来源/索引/长度/摘要/完成时间和至少5年期限；`localDeletionAllowed=false`。不证明异地副本或旧keyId轮换 |
| V28审计归档 | `pda_audit_archive` 原证明、分段正文、`pda_audit_event`、`pda_audit_checkpoint` | 验签、连续范围、原事件及来源摘要、提交事件冲突、检查点覆盖；不重建截点后遗失审计 |
| V30/V31临时准备及原来源 | 原资格/清单JWT、五个准备包、激活确认、`temporary_work_origin`及TEMP审计 | 原资格与设备/人员/仓库/代次固定，过期证据仍保留；检查原字节摘要和签名，正式/TEMP共用设备连续序号。V32实测3份资格/15个包/2个原来源 |
| V32正式编号 | `outbound_order.order_no`与`order_number_counter`同一截点 | FH北京时间日期/扩展流水及计数器不回退，允许高于现存最大号；截点外已发号的可信高水位仍待独立恢复，不能据快照检查放开发号 |
| 应用配置与 DB 凭据 | R02 `/etc/acrylic-outbound-test`；实验 `source/config` | R02 强制包含 runtime/migration 配置和签名文件；实验保存随机应用凭据。恢复配置先隔离核对，绝不直接 source/执行归档配置 |
| 签名材料 | 当前服务签名私钥/公钥；未来保留所有历史 keyId 的验签公钥和必要受控私钥 | 实验新建 RSA3072 配对，恢复后签名/验签挑战。真实密钥未读。轮换和历史 keyId 密钥环、异地保存待接入 |
| 发布与基础设施 | 发布 manifest、JAR/Web 摘要、API/数据库/客户端兼容表，unit/Nginx/TLS 配置 | 实验保存起点/JAR 摘要，R02 原包有 release/manifest。R02 原备份不包含发布包本体、systemd unit、Nginx站点和 Let's Encrypt 目录，须另存经核验发布包/配置，并安排 TLS 重新签发或加密托管 |
| MySQL 实例材料 | 专用账号/最小权限重建、字符集/时区、版本、必要 binlog | 不备份/恢复共享 mysql 系统库。实验在另一实例新建独立账号。没有 binlog 连续归档/PITR |
| 外部恢复控制 | 独立于业务库备份的权威恢复 UUID、历史和恢复模式控制 | 尚未实现。不得从旧业务备份恢复此控制记录，不得用 R09 实验目录名代替恢复代次 |
| PDA 本机未上传资料、APK签名 | Keystore 身份、Room、冻结文件、取消证明、应用发布签名分别管理 | 本轮完全未操作。服务器备份不包含只在 PDA 的唯一副本；APK 发布密钥不得混入普通服务器配置归档 |

临时文件、原始导入、错误文件、导出成品和有效防重材料不能用同一保留策略。云端业务凭据和审计至少 5 年、PDA 90 天及在途/未决保护依赖 R05/R07；本轮不删除任何业务材料。恢复到多台设备必须走后续受控交接，不复制身份和数据库让两台继续作业。

## 环境与命令

在 R09 worktree 中使用 Python 3.12+、MySQL Server/CLI 8.0、Java 21、Gradle 8.11.1；实验签名/SQL查询需要 [requirements.txt](config/requirements.txt) 中的cryptography和PyMySQL。本次实际版本见接入记录。本轮仅在本worktree的 `.local/r09-venv` 安装PyMySQL1.1.2，复用既有cryptography48.0.0，没有安装或升级共享组件。下面的python须指向包含这两个依赖的专用运行时；可先建立venv并安装requirements，或明确使用 `.local/r09-venv/Scripts/python.exe`。

```powershell
# 从 R09 worktree 运行。setup 会返回新的、唯一的 lab.json 绝对路径。
python docs/parallel/R09/ops/cli.py setup --mysql-bin 'C:/Program Files/MySQL/MySQL Server 8.0/bin'
# 如已有经核验的 Gradle，可设置 R09_GRADLE 为其 gradle.bat，避免重新下载 wrapper。
$lab = '<setup 返回的本轮 lab.json 路径>'
python docs/parallel/R09/ops/cli.py seed --lab $lab
python docs/parallel/R09/ops/cli.py backup --lab $lab
$backup = '<backup 返回的唯一备份目录>'
python docs/parallel/R09/ops/cli.py verify --backup $backup
python docs/parallel/R09/ops/cli.py restore --lab $lab --backup $backup
python docs/parallel/R09/ops/fault_drill.py $lab $backup
python docs/parallel/R09/ops/cli.py api-probe --lab $lab
python docs/parallel/R09/ops/cli.py stop --lab $lab
```

V28演练使用同一命令序列，但将 seed 替换为以下固定已提交源码入口，之后在 backup 前执行专用 Worker 探测。源码通过 `git archive` 只读导出至新实验目录，绝不从原目录执行构建，不改变 worktree 分支或迁移文件。

```powershell
python docs/parallel/R09/ops/cli.py seed --lab $lab --source-commit 32ff90fb8ad4f7c444c0ddf9118479b4689d1928
python docs/parallel/R09/ops/cli.py worker-probe --lab $lab
# restore 成功后、api-probe 产生短期会话之前：
python docs/parallel/R09/ops/cli.py metadata-probe --lab $lab --backup $backup
python docs/parallel/R09/ops/cli.py v28-faults --lab $lab
python docs/parallel/R09/ops/cli.py jobs --lab $lab
```

V28 seed 运行原4项提交测试加1项成功归档、1项连续审计归档测试。Worker探测使用同一已校验JAR、同一专用source实例、worker非Web profile，完成任务后停止确切子进程。它不测Linux unit或退出码143语义。后续API探测也必须校验JAR摘要，不误用另一构建。

V32仅在**新建且为空的**专用环境用`seed --source-commit 87e1893a49b598bbfdacee1a20d5eb455c5a5903`；增加两项既有TEMP审计场景，共8项。恢复后可运行`r06-faults --lab <本轮lab.json>`，仅在已验恢复库注入FH计数器回退/缺失、TEMP原代次/清单/签名/准备包故障，finally复原后全表/文件再比较。已保留的`.local/r09/20260920T084455Z-26ba55`不得重新seed或restore；续接应核对原实例身份后只读审计。V33的正文前ABORT、临时整包/分块待审与正式化目标尚未覆盖，不能将V33库交给当前检查器。

顺序执行，逐条检查退出码；失败后不要继续后续步骤。`seed` 仅允许空 source 库，运行既有 4 个 `SubmissionApiIT` 场景并构建 JAR，不跑默认共享开发/测试库。不把它改成 `tools/development_server.ps1` 的默认验证入口。

专用边界固定：source `127.0.0.1:19309/r09_ops_source`、账号 `r09_source@127.0.0.1`；restore `127.0.0.1:19310/r09_ops_restore`、账号 `r09_restore@127.0.0.1`；API `127.0.0.1:18309`。两个实例的 root 密码均随机生成，只限本机配置文件使用；业务账号仅各自专库权限。MySQL 关闭额外 X 端口和 binlog、缓冲池128MiB/连接上限24。目录是本 worktree `.local/r09/<UTC-随机值>/` 下分别独立的 datadir/files/config。

每次操作核对 `@@port`、`@@datadir`、`DATABASE()`、`CURRENT_USER()`；查询用PyMySQL显式回环/端口/专库/账号/UTC/超时，禁用local_infile、不自动重连或重试、不读取外部默认连接配置。dump/import仍使用MySQL原生程序、显式option file和不存在的login file，去除外部MYSQL_PWD。端口已占用就拒绝新建，不终止占用者。数据库恢复只允许空 restore 库，文件只允许新 `recovered/`；重复执行不会 DROP、清库、覆盖目录或改 source。操作锁存在时拒绝执行，不自动回收所谓过期锁。

Windows 实验根目录撤销继承，只允许当前用户/SYSTEM；Unix 使用0700及 umask077。不把口令放命令行，子进程输出只保存在受控目录或捕获后丢弃；普通 CLI 仅输出固定错误码/汇总。运行时私钥、配置、备份、SQL、日志和原始测试报告均被现有 `.gitignore` 排除。

## 备份成功条件

1. 合成业务进程已经结束。检查专用source实例无其他前台连接，再只对核对后的专用 source 实例开启 `super_read_only`，不对共享实例设置只读。连接检查不能代替停止进程或阻止之后重连；本轮按已结束的夹具/Worker进行人工顺序编排。这只是实验截点控制，不能代替完整 R09 的写入栅栏。
2. 校验结构、业务文件引用、回执、包数/重量/扣账与占用；生成全库逻辑备份、结构 dump、规范化数据摘要、文件及配置归档、版本清单。
3. 前后比较库表和文件状态，所有产物完成后写清单；最后写 `COMPLETE.json` 并校验 gzip CRC、tar 条目与内部文件摘要。重命名 `.partial` 为完成目录。
4. 普通失败在 finally 恢复原专用实例可写状态；失败目录不产生成功标记。进程被强杀、断电或权限变化时 finally 可能不执行，须按故障处理检查只读状态与锁，不能假定恢复成功。

数据库单事务快照不能保证外部文件或其他写入口同步停止；任何实际服务器方案必须先盘点并停止本项目 API/Worker/文件写入者，避免在线写入穿过截点。固定提交32ff90f的R02脚本已通过service_checkpoint同时停止API/Worker，并保存active-services.txt，只恢复先前运行的服务；原V21起点脚本只有API，不混淆两代格式，也不据源码判断公网已启用Worker。

本轮恢复会验证归档摘要、必需组件、gzip、tar 遍历/链接/重复名/特殊条目/8GiB展开上限，再创建新文件目录、导入专用空库，比较全部结构和数据。MySQL 等价的隐式 utf8mb4 声明被规范化，排序规则、长度、NULL、索引、约束不忽略；另有测试证明真实字段差异仍被识别。重新校验外键、文件引用、回执签名和业务汇总，成功才写 `verification.json`。校验恢复不启动普通业务入口，也不开放公网。

## 故障处理与清理

- 完整性错误、SQL导入失败、文件丢失/密钥不匹配：退出2，保留目录和已导入的隔离库；不得尝试跳过约束、只取成功表或覆盖运行库。没有 `verification.json:PASS` 就没有恢复通过结论。
- 备份中断：先核对 lab.json、监听端口、datadir、账号和 `.r09-operation.lock` 所指进程。对确认为本任务的实例，人工检查 `@@super_read_only/@@read_only`。保持原证据；需要重新演练时先通过专用 stop 关闭旧实例，再 setup 新目录。
- 本工具不自动清理实验目录或备份。后续确需清理时，先列出拟删除的绝对目录、解析 junction/symlink、确认严格位于本 worktree `.local/r09/`、确认没有监听进程及需要保留的证据，再使用单一 PowerShell `Remove-Item -LiteralPath`。不得跨 shell 拼接删除，不执行针对根目录/计算失败路径的递归清理。
- 实验故障注入只在已成功恢复的专用库/目录运行；对具体正文文件、测试私钥和一条账务重量作受控变动，finally 恢复原值并再核对完整摘要。它不是线上修复工具。

## R02 只读审计入口

```text
python3 cli.py verify-r02 --backup /opt/backups/acrylic-outbound-test/<既有UTC目录>
```

该命令检查 R02 固定五组件，或加active-services.txt后的六组件，及 SHA256SUMS、gzip、tar路径、配置/签名材料、非NULL表校验和。服务清单只允许本项目API/Worker且不得重复；清单存在但未入摘要则拒绝。旧五组件标为legacy-api-only，不能证明Worker曾停止。不执行恢复或写入服务。成功含义仅为归档完整性，不能替代实际恢复/业务核对。用于服务器前还须在目标 Linux、Python、归档规模及账户权限上验证；本轮仅提供文件。
