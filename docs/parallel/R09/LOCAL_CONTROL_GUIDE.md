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
| formatVersion | 整数1 |
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

## 崩溃与恢复

历史文件先独占创建、flush/fsync，再写指针暂存、原子替换current。Windows使用WRITE_THROUGH替换，POSIX尝试目录fsync；真实断电/底层存储耐久性需现场验证。

推进命令在上述历史写入之前，先追加intents中的原命令及摘要，再原子推进admission摘要头；它只标记最新接纳意图，完成状态仍由控制历史证明。意图追加/头暂存/头提交死亡时均不接纳新写者；对应原命令可以补完整摘要头并等待已接纳写者结束。坏原件或摘要冲突不能重造，完成意图不删除。

历史完整但指针尚未提交时，读取保持 `CONTROL_COMMAND_INCOMPLETE`；重新提交同一原commandId和正文可推进原指针，不能生成另一个epoch/createdAt。初始化在完整bootstrap或完整首历史之后死亡，也只接受原初始化请求。指针提交后响应丢失直接重放原响应。

部分文件、缺历史、未知版本或检查点冲突保留现场并拒绝续作；不能用文件mtime、删锁、删除半文件或重新初始化来绕过。初始化在bootstrap完成之前死亡可能需人工核查，工具不会覆盖残余目录。

## 尚未完成

共享API/Session/Worker/Flyway/文件门禁接线、NORMAL初始化/释放两阶段证据、完整UUID传输协议、跨主机仲裁、异地不可覆盖证据和真实断电/网络存储仍待完成。V39只建立关闭阶段的UUID数字映射，不回填原事实，也不开放生产写。Windows和WSL本机ext4的跨进程结果不替代完整R09恢复。
