# 监控基础与资源配置

所有 [config/](config/) 文件均为候选配置，未复制到服务器，未运行 daemon-reload、enable/start、Nginx reload 或外部告警发送。没有更改原 `acrylic-outbound-test-backup.timer`。

## 已有工具与指标

| 检查 | 实现/失败条件 | 解释边界 |
| --- | --- | --- |
| API 健康 | 本机回环 `/api/v1/auth/csrf`，超时、非200、重定向、非OK、非NORMAL、未知epoch、超延迟均失败 | 检查现有响应中的DB依赖状态，不新增正式API；可产生普通短期会话，不输出token或cookie。当前响应模式不是完整恢复栅栏的验收 |
| 端口 | 指定回环TCP连接，连接失败报错 | 端口可连不证明DB/业务健康；必须结合API与unit状态 |
| 最新备份 | 验证最新目录，完整性、gzip、内部文件/必需密钥材料和时间；默认26小时 | 更新的失败/partial不得被旧成功掩盖。R02目录时间采用备份开始UTC，比完成时间保守；新格式采用截点时间，不使用可被复制改变的mtime |
| 已演练恢复证据 | 可选`restore_evidence`绑定最新备份路径、SHA256SUMS、固定源码/数据库版本、表数、业务摘要和报告时间 | 单独的完整性检查始终返回`restore_verified=false`；旧报告不能覆盖更新的备份。成功仅表示本地隔离快照恢复，明确`disaster_reconstruction_verified=false`及`local_deletion_allowed=false` |
| 磁盘 | 对备份/业务文件/DB所在文件系统，剩余字节和百分比分别判断 | 不是目录占用量/长期容量预测；默认至少10GiB且15%，须按实际增长/恢复空间调整 |
| 服务异常 | `systemctl show`：Result、ActiveState、NRestarts | API/已部署Worker必须active且Result=success；备份oneshot正常inactive允许，但上次失败不忽略。重启阈值是本次unit计数，不是每分钟速率 |
| V28任务 | failed、expired_leases、ready、oldest_ready_seconds、retry | 只读聚合，不输出payload、去重键、租约token、last_error。专库jobs命令已实现；服务器仅交付SELECT查询和阈值，尚未接入timer |
| 完整性 | 新/旧两种备份格式均可核验，恢复证据独立 | 监控 `PASS` 不等于备份经过实际恢复，更不等于灾后业务已重建 |

`python cli.py monitor --config <本地配置.json>` 输出脱敏JSON；全部通过退出0，任一失败/配置损坏退出2。异常URL、子进程stderr、SQL、用户名、令牌、文件内容、私钥不输出。系统时钟明显异常或未来备份时间拒绝；接入告警时将非0、探针没有运行/报告缺失一起检测，不能只看“最后一次PASS”。

V32已在原lab用真实最新备份/恢复报告完成监控CLI核验，见[R06记录](R06_READY_EXTENSION.md)。`restore_evidence`需要`backup`、`report`、`source_commit`、`schema`、`max_age_seconds`，其中`backup`必须就是顶层`backup.directory`中最新目录。报告必须由受控演练生成并限制写权限；SHA-256和本地报告不能证明恶意来源真实性。当前仅适配R09实验格式，不能给R02包附一份实验报告冒充服务器恢复已验收。

## 配置审阅与后续验证

- [monitor.r02.example.json](config/monitor.r02.example.json)：R02既有回环API18085、MySQL3306、备份和文件目录；本轮未连接这些服务器地址。只允许127.0.0.1探测，禁用代理和重定向，没有外部HTTP通知。
- [monitor.r05.example.json](config/monitor.r05.example.json)：面向后续V28/Worker部署的候选，额外要求Worker活跃；不要套到未部署Worker的现网，否则应报告inactive。两份配置均兼容旧五组件/新六组件归档，但旧备份不证明Worker已停写。
- [job-health.sql](config/job-health.sql) / [job-limits.example.json](config/job-limits.example.json)：以数据库UTC时间检查任务，初始阈值FAILED=0、过期租约=0、可运行积压≤100、最老到期≤300秒、RETRY≤20。总数不阈值；历史FAILED会持续报告，需后续处理/确认机制，不能通过删除历史任务消警。执行计划/生产数据量成本待验证；服务器接入须独立SELECT-only账号与受限option file，禁止使用实验账号或将密码放参数。现有timer未集成该SQL。
- [worker-resource-limits.conf](config/worker-resource-limits.conf)：沿用R05候选Worker高水位384MiB/上限512MiB、CPU50%、Tasks128，文件描述符4096；不修改JVM启动、迁移或143退出语义。专用Windows Java进程运行结果不等同Linux资源/退出行为验收。
- [ops-check.service](config/acrylic-outbound-test-ops-check.service)：root只读私有归档，严格文件系统保护、256MiB/CPU25%、300秒超时；只输出汇总到journal。Python3.12+和脚本安装路径必须先核对，不能假定现有服务器已有所需运行时。
- [ops-check.timer](config/acrylic-outbound-test-ops-check.timer)：独立候选timer，每15分钟，首次开机延迟5分钟。不替换、不启用既有备份timer。每轮完整读归档，实际大数据量应先测IO成本，必要时分离轻量状态轮询与低频完整验证；本轮不宣称适合所有规模。
- [api-resource-limits.conf](config/api-resource-limits.conf)：保留R02 JVM512MiB、服务高水位768MiB/硬上限1GiB、CPU100%、Tasks256/连接池8的分工；补文件描述符上限8192及300秒内最多5次启动。
- [backup-resource-limits.conf](config/backup-resource-limits.conf)：候选CPU50%、512/768MiB、30分钟超时；仅应用于本项目backup服务。超时/OOM可能阻止原EXIT trap恢复API，处理见回退手册。未修改共享MySQL或Nginx限额。

服务器应用前，应在隔离Linux环境执行 `systemd-analyze verify` 检查unit、核对drop-in合并结果/支持的指令和Python版本，检查只读权限能读归档及磁盘路径。先手动运行探针，模拟API停止、DB不可用、备份过期/损坏、低空间、unit失败和重启超限，再评估读取时间、CPU/内存/磁盘IO。随后才决定timer、权限、监控用户和告警接收人。**本轮没有执行目标Linux/systemd运行验证**，Windows本机静态检查不等同于此项通过。

外部告警接入还需确定负责人、渠道、去重/恢复通知、持续时间阈值、夜间策略及发送授权。本轮仅本地输出/非零退出，不建立告警通道或发送测试消息。

## 本轮未接指标

API请求5xx比例/历史延迟分位数、导入失败率、待审业务超时、设备版本/在线/待上传、PDA剩余空间、云端文件增长及保留任务，依赖R05/R08和后续业务观测接口。任务积压/重试已有本机只读聚合工具，服务器接入/阈值校准未完成。不得读缺失表后把值当0，不能因设备离线自动回收作业锁。完整清单见 [DEPENDENCIES.md](DEPENDENCIES.md)。
