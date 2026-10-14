# P07 停机重锚开发运维入口

本入口只轮换已明确供应的 P5 锚及保留进程记录/空租约索引的 HMAC，不创建权威、不清 UNKNOWN、不操作数据库、不改变生产开关。生产执行、原库/原钥、真实停机及异地介质须另有授权；本页示例只用于新建隔离合成环境。

## 选择与退出标准

按总目标 P07 默认，采用显式请求＋外部秘密供应，复用现有 `RecoveryLeaseRekey` 的协调锁及活动写者/租约拒绝。没有自动扫描配置、自动启停、后台任务或 HTTP 接口。运行配置只持有当前钥；历史钥留在独立只读保管区，不作为运行期回退钥，也不因轮换删除。

本切片退出标准：实际 Python 供应器经二进制管道调用产品 JVM，原记录和新树由独立 Python 消费者核对，新当前钥可启动客户端权威；旧请求、活动进程/租约、错身份/错钥、历史钥不可取回及尾替换失败有原字节守恒证据。完整 E07/L2/L3、Linux、生产和真实外部保管仍另验。

## 停写与输入

1. 核实所有 HTTP、Session、worker、文件/导出写者已通过授权流程停写并正常退出；保留进程、监听和原目录事实。工具只在协调锁内再次拒绝活动进程和非空租约，不负责终止服务，也不证明数据库/全部外围进程已停写。
2. 保留数据库/文件及 P3/P5 原始快照；失败库、UNKNOWN、pending 文件和旧日志不清理。现有非空租约或活跃写者导致拒绝时，按原恢复协议处理，禁止删记录凑空索引。
3. 通过既有秘密保管渠道准备旧/新各 32 字节原始二进制钥；文件位于 authority、lease、closed-authority 和全部 restoreRoots 之外。请求同样在树外。历史钥须有独立只读副本且实际可读；`retainedAnchorFile` 指向该副本，产品以只读句柄核对其字节，不代配置 ACL/介质防覆盖。至少五年及仍被引用恢复需求的保管责任继续保留，不能把同机副本称异地备份。
4. 从可信保管的安装绑定取得请求中的路径/安装 ID/身份摘要/原 marker 摘要；不能从待恢复树自动推断或把自报摘要当可信权威。新钥 ID 是新钥 SHA-256，原钥 ID 必须匹配原供应物。

请求是 UTF-8、键按 Unicode 码点排序、紧凑 JSON、恰好一个末尾 LF，字段严格如下（尖括号为说明，不是可执行输入）：

```json
{"authorityRoot":"<absolute P3>","closedAuthority":"<absolute closed-authority>","formatVersion":1,"identitySha256":"<pinned identity SHA-256>","installationId":"<pinned UUID>","leaseRoot":"<absolute P5>","markerSha256":"<pinned original marker SHA-256>","newAnchorId":"<new key SHA-256>","oldAnchorId":"<old key SHA-256>","operation":"REKEY_P5","restoreRoots":["<absolute business root>"],"retainedAnchorFile":"<external read-only historical key>"}
```

## 构建与显式运行

先按 Git 工作流保存输入树和改动哈希，复测磁盘并设置任务子进程的 D 盘 TEMP/TMP/Java 临时目录。复用安装的 JDK/Python/Gradle；不改全局环境。

```powershell
./gradlew.bat -p server recoveryRekeyClasspath --offline --no-daemon
```

该任务只输出 `server/build/recovery-rekey-classpath.json`，不轮换任何内容。普通服务启动不会调用重锚。以下参数均为路径，钥内容不进入命令行、环境变量、日志或请求文件；请替换为已核实的隔离路径。

```powershell
python tools/recovery_rekey.py --java <absolute-java-executable> --classpath D:/acrylic-pda-outbound/server/build/recovery-rekey-classpath.json --request <external-request-json> --current-key-file <external-current-bin> --next-key-file <external-next-bin> --evidence-dir <new-external-evidence-directory>
```

供应器验证新证据目录与外部输入，将恰好 64 字节（旧钥后接新钥）送入 JVM 标准输入并关闭管道；不使用 shell 组合命令。JVM 严格核对长度、请求身份和历史钥取回，再在有界协调锁内轮换。调用者的当前/新钥来源是显式信任输入；操作系统对受信任进程内存/秘密渠道的保护仍由环境负责。

每次轮换保留相邻 `.rekey-backup-*` 目录中的原 process 文件、空 index 和 marker，成功回执给出其路径。成功后 Python 回读原/新 marker、全部进程原字段和两代 HMAC、空索引及文件集合；`verified.json` 仅表示本次轮换回读，不是业务放行/部署审批。该原始目录也须纳入独立只读保管和引用登记，不作为可重建测试垃圾清理。

## 结果与恢复边界

- 退出 `0` 且存在 `verified.json`：本次轮换及独立回读完成；回执 `runtimeUpdated=false`。仍保持服务停止，另按授权将新钥和新 marker 摘要供应给运行配置，核对原资料及围栏后才能按流程启动。历史钥不能重新作为当前钥启动原绑定。
- 退出 `2`：产品明确拒绝；查看原 `stderr.txt`，核对活动写者、租约、身份/旧请求或秘密来源，保留输入，不自动重试。
- 退出 `3`、超时、无回执或回读失败：结果未知，保留全部 stdout/stderr、`exit.json`、原树、pending 和 `.rekey-backup-*`；保持停写，先按请求/回执摘要核对原/新实际状态。不可直接重跑、反向覆盖或按已失败推定无写入。

首次尾替换失败会逐目标尝试恢复，回滚失败作为原异常附加信息保留；备份始终保留。强制进程终止可能留下部分新签记录，旧绑定及后续启动将拒绝不一致状态；工具不自动修复这种状态。成功和失败日志都不包含原钥字节，私有测试钥/运行配置也不得上传 Git。

开发证据及准确未验范围由历史验收页登记；本页只描述操作契约，不声称任何现场/生产验收已经通过。
