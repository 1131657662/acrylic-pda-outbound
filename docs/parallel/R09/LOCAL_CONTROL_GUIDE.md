# 库外恢复控制本地模块

2026-09-26。实现入口为 `ops/control_store.py` 与常规 `ops/cli.py` 的 `control-init/read/apply` 子命令；基础验收见[本地模块记录](../../history/R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)，关闭投影及耐久意图见[进度索引](../../DEVELOPMENT_PROGRESS.md)。Java消费/事务租约/V39投影为显式组件，尚未接入全部生产入口或释放能力。

## 配置与信任前提

- Python≥3.12。本机固定磁盘受保护目录，与所有业务恢复目录互不包含；禁止网络/映射盘、路径别名、junction、符号链接和文件硬链接。Windows初始化限制ACL为当前操作人及SYSTEM；运行账户权限和目录父级须由运维保护。
- `--installation-id` 为原安装UUID，不随重启生成。每次传入同一完整 `--restore-root` 集合；工具只验证已声明集合，无法发现漏声明的业务恢复路径。
- CLI的 `--checkpoint` 必须位于控制目录与恢复集之外，内容须来自先前已可信保存的响应。模块内没有外部不可变存储或签名认证能力；本地摘要只能发现与可信锚点不符，不能抵抗管理员同时伪造整个历史和可信锚点。
- 目录和锁文件永久保留，OS排他锁随进程死亡释放。任何未知文件、部分历史或身份不符都关闭；不会自动清除所谓陈旧锁/坏文件。
- Java/Python互操作使用同一锁文件偏移0长度1记录锁（Windows msvcrt/FileChannel，POSIX lockf/FileChannel）。升级前必须退出旧flock写者；不得混跑。进程内门禁先于描述符打开，拒绝嵌套/另一线程并发，防止POSIX关闭第二描述符释放原锁。坏长度锁文件拒绝；锁文件不可改名替换。

## 原请求与输出

新建authority生成永久writers.lock与version2的writers.json，后者绑定原安装identity摘要和锁文件系统身份，同时建立intents目录和admission.json摘要头。有效控制推进先耐久保存原停止意图，再尝试写者独占锁；活跃Java共享租约返回CONTROL_WRITERS_ACTIVE，原控制历史/指针不变，停止意图保留并拒绝新租约。最后写者退出后只接受原命令续作；有效待完成意图允许只读状态，不能取得新写者租约。

Java按文件身份复用描述符，禁止直接开第二个POSIX描述符。缺协议或version1的旧authority继续只读，不能自动补文件/升级；已有原请求、历史、回执不重写。复制authority会改变锁文件身份，需要显式停机迁移，不得改marker绕过。没有所有入口接线之前，仍不能把组件租约当作真实系统已排空。

原请求保存UTF-8、对象键排序、无多余空格、末尾单换行的JSON（可使用 `control_format.canonical`）。字段严格固定：

| 字段 | 格式/含义 |
| --- | --- |
| formatVersion | 首轮命令为整数1；后继轮次命令为整数2，附加字段见下节 |
| installationId / commandId / recoverySessionId / epochId | 小写规范UUID；原commandId及完整正文永久保留 |
| expectedRevision | 十进制字符串；初始化为`0`，后续使用已读revision |
| phase | QUIESCING、RECOVERING、VERIFYING、RELEASE_READY |
| snapshotId / manifestSha256 | 原备份标识及64位小写SHA-256 |
| affectedWarehouseIds | 非空、排序去重的仓库标识数组 |
| possibleLossFrom / possibleLossThrough | UTC `Z` 时间，开始≤结束≤requestedAt |
| evidenceSha256 | 本阶段声明证据摘要，不替代证据本身验证 |
| actorId / requestedAt | 原操作人和请求UTC时间 |

第一次必须显式 `control-init`，创建QUIESCING。QUIESCING→RECOVERING必须更换原命令中的UUID epoch，后续阶段保持同epoch；同会话范围、备份和损失窗口不变。缺少共享写者排空/审批验证前，这些仅为本地声明，不代表实际业务阶段已经完成。

```powershell
python docs/parallel/R09/ops/cli.py control-init --control-root D:/isolated-control/authority --installation-id <原安装UUID> --restore-root D:/isolated-business --request D:/isolated-control/requests/init.json
python docs/parallel/R09/ops/cli.py control-read --control-root D:/isolated-control/authority --installation-id <原安装UUID> --restore-root D:/isolated-business --checkpoint D:/trusted-witness/checkpoint.json
python docs/parallel/R09/ops/cli.py control-apply --control-root D:/isolated-control/authority --installation-id <原安装UUID> --restore-root D:/isolated-business --request D:/isolated-control/requests/next.json --checkpoint D:/trusted-witness/checkpoint.json
```

示例路径不是现有生产路径，命令不自动运行。初始化前父目录须存在。同键同正文返回原记录和稳定响应；同键异正文、过期revision或未完成的其他命令拒绝。JSON输出的 `checkpoint` 应经独立可信保管后供下次使用；不应以当前指针自身充当可信锚点。

所有输出固定 `ordinaryWritesAllowed=false`、`localDeletionAllowed=false`、`sharedGateIntegrated=false`；不提供进入NORMAL或业务放行的命令。

## 后继轮次控制协议

同一installation保留首轮原件，通过 `control-apply` 从真实上一 `RELEASE_READY` 开启新 `QUIESCING`。新轮次仅接受命令formatVersion=2；记录、identity、writers及停止intent封装沿既有协议，不自动升级或覆写。初始化仍只接受首轮命令。旧消费者遇到新命令须拒绝，不能混用旧消费者推进后继轮次。

版本2在上述命令字段外精确增加：

| 字段 | 含义 |
| --- | --- |
| predecessorReleaseSha256 | 实际上一RELEASE_READY记录摘要，必须等于前驱recordSha256 |
| predecessorNormalIntentSha256 | 原NORMAL外部intent的内容摘要，必须绑定该前驱release及其revision/previous hash/installation |
| sourceBindingSha256 / sourceNormalSha256 | 非零SHA-256外部pin；本地控制模块不验证实际数据库行，不能据此称恢复可信 |
| targetServerUuid / targetDatabaseSchema | 规范非零UUID与1–64位字母数字下划线schema；整轮固定，不代表已登记数据库 |

新QUIESCING更换session，保持前驱epoch；RECOVERING才换epoch。session与RECOVERING epoch均不得复用任何历史值。该轮后续三阶段保持命令版本、范围、snapshot/manifest、损失窗口及全部新增字段；不降级、不跳阶段。CAS、原ID原正文重放、同ID异文拒绝、先耐久停止意图再排空写者保持。提交前与Java/Python读者均核完整历史和pending意图，不靠重新计算hash掩盖语义冲突。

初始 `normal-admission.json` 保持原字节。更多外部NORMAL原件只能追加在 `normal-history/<intentSha256>.json`；目录与文件禁止别名/硬链接、文件名精确匹配内容hash，原件必须指向已存在的RELEASE_READY及其原previous hash。原NORMAL尚未产生或缺失时拒绝后继，不从数据库或历史摘要补造原件。这里仅读取和核验，未提供NORMAL原件供应或数据库登记工具。

控制模块仍只验证外部原件。后继数据库登记已另有显式 Java primitive：`RecoveryDatabaseSource.inspect` 在原 closed authority 事务中核完整前缀、原 NORMAL 与真实源身份后给出源 pin；`RecoveryClosedProjection` 追加后继目标绑定/投影；`RecoverySuccessorNormalRegistration` 追加该轮 NORMAL。新表为空起步，保留首轮原表、历史投影/映射和触发器。原行摘要含全部逻辑列及原登记微秒时间，具体规范见 `RecoveryDatabaseHashes` / `RecoverySuccessorRows`，不能自行用 SELECT 展示文本或部分列 hash 代替。

这些 primitive 无 bean/路由/自动调用；必须显式提供固定 closed authority、原 baseline 及完整外部原件。登记在真实目标 UUID/schema 上核前驱 release/intent 和源 binding/NORMAL 摘要，严格下一条投影、全历史映射、原子追加及幂等；更多 NORMAL 按指定 revision/hash 从原件目录读取，不覆盖初始原件。实际非空 dump 的新 schema 两轮验证见[专项报告](../../history/GOAL_D13_SUCCESSOR_DATABASE_ROUND_2026-10-23.md)。这不是可上线运维脚本或可信生产材料。

后继运行时数据库观察已接完整 lineage：`RecoveryAdmissionVerifier` 按指定轮次读取 NORMAL，`RecoverySuccessorAdmissionSql` 在同一条只读 SELECT 中取得真实 UUID/schema、原 binding/NORMAL、全部投影/映射、后继 binding/NORMAL 和 server_state，并复用登记路径的完整前缀及源 pin 校验；缺原件或矛盾即拒绝，初始原件不覆写。两轮新隔离非空恢复的 verifier/连接入口、单 SELECT/0 DML 及反例见[只读准入报告](../../history/GOAL_D13_SUCCESSOR_READ_ONLY_ADMISSION_2026-10-23.md)。真实API/独立worker/P5/文件已有[同输入后继组合](../../history/GOAL_D13_SUCCESSOR_P5_COMPOSITION_2026-10-23.md)：已有binding/NORMAL的非空实际恢复、原任务失效/新键双格式下载及六HTTP拒绝守恒。其权威和P5提升仍为显式合成测试供应，不是生产工具；该P5组合仍仅同实例；另有[真实双实例已有绑定恢复](../../history/GOAL_D13_BOUND_CROSS_INSTANCE_SUCCESSOR_2026-10-23.md)，保持不同UUID、全部原行与触发器，该片只计数据库后继准入；后续[实际跨实例API/P5同输入组合](../../history/GOAL_D13_CROSS_INSTANCE_API_P5_COMPOSITION_2026-10-23.md)由新双实例、独立API/worker实际目标身份、P5和文件共同核验，仍是合成test-only权威，不作生产供应或整体通过。`ordinaryWritesAllowed=false`、`localDeletionAllowed=false`保持，不因观察或测试组合开放生产入口。

已有binding的[实际跨实例六死亡点与原命令续作](../../history/GOAL_D13_CROSS_INSTANCE_DEATH_RESUMPTION_2026-10-23.md)已分别用新双实例核pending API/worker拒绝、原命令恢复和同输入API/独立worker/P5/文件；仅合成test-only权威有界结果，不作为生产恢复或D13整体通过。旧控制原件和精确错误保持，不能删半文件或重造命令绕过。

已有binding的[实际跨实例后继worker死亡与孤儿P5](../../history/GOAL_D13_BOUND_CROSS_INSTANCE_WORKER_CRASH_2026-10-23.md)已用新双实例核真实halt71、OS锁释放、精确六HTTP拒绝和不同fresh产品API/P5启动；仅合成test-only权威有界结果，原process/控制/数据库/文件必须守恒，不作为生产恢复或D13整体通过。

已有binding的[跨实例后继原生退役失败/UNKNOWN](../../history/GOAL_D13_BOUND_CROSS_INSTANCE_NATIVE_RETIREMENT_2026-10-23.md)已用新双实例核真实Windows拒绝lease改名、产品halt70及UNKNOWN持久阻断；孤儿lease/index可退役但UNKNOWN原件仍使不同fresh产品API拒绝，不能以空index解除围栏。只计合成test-only权威有界组合；当时commit UNKNOWN跨实例组合未验；后续见下项，实际OS拒绝自halt及D13整体仍未验。

## 崩溃与恢复

历史文件先独占创建、flush/fsync，再写指针暂存、原子替换current。Windows使用WRITE_THROUGH替换，POSIX尝试目录fsync；真实断电/底层存储耐久性需现场验证。

推进命令在上述历史写入之前，先追加intents中的原命令及摘要，再原子推进admission摘要头；它只标记最新接纳意图，完成状态仍由控制历史证明。意图追加/头暂存/头提交死亡时均不接纳新写者；对应原命令可以补完整摘要头并等待已接纳写者结束。坏原件或摘要冲突不能重造，完成意图不删除。

历史完整但指针尚未提交时，读取保持 `CONTROL_COMMAND_INCOMPLETE`；重新提交同一原commandId和正文可推进原指针，不能生成另一个epoch/createdAt。初始化在完整bootstrap或完整首历史之后死亡，也只接受原初始化请求。指针提交后响应丢失直接重放原响应。

部分文件、缺历史、未知版本或检查点冲突保留现场并拒绝续作；不能用文件mtime、删锁、删除半文件或重新初始化来绕过。初始化在bootstrap完成之前死亡可能需人工核查，工具不会覆盖残余目录。

## 尚未完成

共享API/Session/Worker/Flyway/文件门禁接线、NORMAL初始化/释放两阶段证据、完整UUID传输协议、跨主机仲裁、异地不可覆盖证据和真实断电/网络存储仍待完成。V39只建立关闭阶段的UUID数字映射，不回填原事实，也不开放生产写。Windows和WSL本机ext4的跨进程结果不替代完整R09恢复。

已有binding的[跨实例后继真实产品提交/UNKNOWN](../../history/GOAL_D13_BOUND_CROSS_INSTANCE_COMMIT_UNKNOWN_2026-10-23.md)已用新双实例核实际worker完成事务的提交前后独立连接可见性、驱动成功返回后合成08007、产品halt70与UNKNOWN持续拒绝；原lease/index/process及数据库/文件拒绝守恒。真实提交与合成丢ACK分列，不称网络断开；仅test-only权威有界组合，不作为生产供应或整体通过。

已有binding的[真实TCP提交应答丢失组合](../../history/GOAL_D13_BOUND_CROSS_INSTANCE_WIRE_COMMIT_2026-10-24.md)已用新双实例、原COMMIT/OK包及独立提交前后连接核验：ACK仍扣留时服务器已提交，随后断流产生原driver 08S01，产品halt70、UNKNOWN和不同fresh产品API持续拒绝。代理仅worker显式测试Hikari接线、只连本片隔离目标；原包/原异常与13组篡改消费者分列。P5仍syntheticTestOnly=true/productProvisioning=false，不是可信生产供应或D13整体通过；OS拒绝自halt及其余入口/最终候选仍未验。
