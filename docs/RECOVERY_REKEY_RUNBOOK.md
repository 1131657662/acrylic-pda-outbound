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

每次轮换保留相邻 `.rekey-backup-*` 目录中的原 process 文件、空 index 和 marker，成功回执给出其路径。首个替换前，产品在已持有的P3协调锁内封存`rekey-inventory.json`，以旧钥HMAC绑定固定身份、原marker、两代钥ID、原记录数量/字节数及全集摘要；回执另绑定清单SHA-256。清单生成失败时不开始替换，不靠删除缺件重新生成一个较小集合。成功后 Python 回读原/新 marker、全部进程原字段和两代 HMAC、空索引及文件集合；`verified.json` 仅表示本次轮换回读，不是业务放行/部署审批。该原始目录也须纳入独立只读保管和引用登记，不作为可重建测试垃圾清理。

## 结果与恢复边界

- 退出 `0` 且存在 `verified.json`：本次轮换及独立回读完成；回执 `runtimeUpdated=false`。仍保持服务停止，另按授权将新钥和新 marker 摘要供应给运行配置，核对原资料及围栏后才能按流程启动。历史钥不能重新作为当前钥启动原绑定。
- 退出 `2`：产品明确拒绝；查看原 `stderr.txt`，核对活动写者、租约、身份/旧请求或秘密来源，保留输入，不自动重试。
- 退出 `3`、超时、无回执或回读失败：结果未知，保留全部 stdout/stderr、`exit.json`、原树、pending 和 `.rekey-backup-*`；保持停写，先按请求/回执摘要核对原/新实际状态。不可直接重跑、反向覆盖或按已失败推定无写入。

首次尾替换失败会逐目标尝试恢复，回滚失败作为原异常附加信息保留；备份始终保留。强制进程终止可能留下部分新签记录，旧绑定及后续启动将拒绝不一致状态；工具不自动修复这种状态。成功和失败日志都不包含原钥字节，私有测试钥/运行配置也不得上传 Git。

开发证据及准确未验范围由历史验收页登记；本页只描述操作契约，不声称任何现场/生产验收已经通过。

## 中止后的只读诊断

`tools/recovery_rekey_inspect.py`只读消费原请求、明确指定的相邻原签备份和外部两代钥，不扫描或挑选“最新”备份，也不修复文件、供应运行秘密、启动服务或授权继续。请求/备份来源仍须由操作者核对：

```powershell
python tools/recovery_rekey_inspect.py --request <external-request-json> --backup <exact-adjacent-rekey-backup> --current-key-file <external-old-bin> --next-key-file <external-next-bin>
```

工具核对固定身份、旧marker摘要、两代钥ID、原HMAC、进程原字段、空索引和文件集合，对当前每条记录与预期原/新字节比较。完整读回可报告`ALL_OLD`、`ALL_NEW`、`MIXED`或`PENDING`；部分/零字节pending、缺失文件、错钥、篡改、额外布局或可见并发变化返回`UNKNOWN`及退出3。原有非法或半写pending必须保留；不能为取得可读状态而删除。退出0仅表示本次可解释的只读分类，所有结果均`keepWritersStopped=true`、`recoveryAllowed=false`、`productionApproved=false`。

带有效签名清单的备份，独立Python逐项重算原记录全集后返回`processSetAuthenticated=true`；同时丢失当前及备份的同名原件也会因全集摘要不符而拒绝。旧无清单备份继续返回`processSetAuthenticated=false`，不得为其补造清单或凭剩余HMAC证明完整。此证明只覆盖轮换持锁快照时实际保存的集合，不能证明更早已丢失的历史。`writerLivenessVerified=false`说明前后hash相等只是两次观察，不是持锁快照或全部写者已退出的证明。该工具不能替代独立保管清单、P3锁身份核验、数据库/文件/业务事实对账或人工恢复审批。即使`ALL_OLD/ALL_NEW`也不能直接启服务。显式恢复工具、清单外部保管与实际进程排空仍是后续开发范围，不允许用手工覆盖完成局部测试后记全部恢复通过。

产品P5启动还会在持协调锁的检查内验证全部保留进程记录的HMAC/身份/文件名，并拒绝未完成root/process布局；空租约索引不再掩盖部分轮换。该检查不重签、不清pending、不生成新权威；同JVM活动进程使用原持有描述符验证，避免POSIX关闭第二句柄释放生命周期锁。旧秘密不能启动含新签进程记录的混合树。

## 签名全集的规范与保管

全集摘要按相对路径升序，对每个原文件的规范描述`{"bytes":<整数>,"path":<相对路径>,"sha256":<原字节摘要>}`加单个LF后连续送入SHA-256；只含原marker、空index和原process记录，不含清单自身，也不改变单条记录格式。最多9,998条记录；签名清单本身沿用有界规范JSON。此上限为当前离线工具的显式拒绝边界，不是已测真实规模或产品历史留存上限。

清单、原签文件、请求及轮换回执需一并进入独立只读保管；两代钥仍位于控制/业务树之外，至少五年及仍有恢复引用时继续保留。清单可读和HMAC通过不证明异地可取回、当前写者退出、数据库截点一致或人工保管完成，也不允许启服务。删除新备份的清单会失去全集证明；带清单SHA的成功回执消费会拒绝这种降级。

## 显式还原原签字节

`tools/recovery_rekey_restore.py`是独立离线工具，只把完整签名快照内的已知原/新规范字节还原为原签字节。必须显式给原请求、精确相邻备份、独立保管的清单SHA及两代钥；不能只根据目录中的“最新清单”自行选输入。没有签名清单的旧备份不可用此入口，不补造历史证明。

```powershell
python tools/recovery_rekey_restore.py --java <absolute-java> --classpath <absolute-classpath-json> --request <external-original-request-json> --backup <exact-adjacent-backup> --inventory-sha256 <independently-pinned-inventory-sha256> --current-key-file <external-old-bin> --next-key-file <external-next-bin> --evidence-dir <new-external-evidence-directory>
```

供应器按二进制管道送两代秘密；产品持原P3协调锁，验证固定身份、原锁原生身份和完整签名集合，再持原fence独占锁逐一探测process生命周期锁。当前每个文件只能是清单对应的原签或可推导新签规范字节，空租约必须保持；任何新增process、活跃锁、新租约、改换锁、异文、缺件或未知文件均拒绝。首版拒绝所有pending，包括可解释的rekey pending；不移走或清除未知状态来满足前置。

所有前置满足且存在新签时，先保存完整当前P5字节到相邻`.rekey-restore-before-*`，再逐个原子替换process、index，最后marker。原签备份、签名清单和故障前快照始终保留。已经全旧且无新事实时，返回`alreadyOriginal=true`，不重复替换文件。还原后Python独立验原HMAC、全集摘要、原marker和每条原字节，写`verified.json`；它依然不能替代数据库/业务截点、外部保管或重新启动审批。

退出0只说明本次原签字节还原与回读，`runtimeUpdated=false`、`keepWritersStopped=true`、`recoveryAllowed=false`。前置拒绝退出2；I/O、超时、回读失败或替换后任何不确定性退出3。替换后失败在stderr给出保留快照路径；不自动重试或反向覆盖，失败pending继续保留，启动检查拒绝。再次执行前先保全并核对原始输入和所有事实；未知状态不能原样机械重跑。历史钥仍需保留，本工具不供应运行配置、不启动服务、不放行业务。
