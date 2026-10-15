# P07 中止诊断及启动拒绝 L1 验收

本批收口只读中止诊断与P5启动检查的开发切片。实际强制终止产品JVM、独立Python读取和拒绝启动均有新证据；显式恢复/人工还原工具、可信全集保管、E07组合、D13–D15整体及D18 L3仍未完成。总目标保持active，版本事实见看板生成事实块。

## 输入、写权及可验证标准

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入HEAD/实际远端 `faa46a825356cf1fc22c7a05fc08e3cf1a20b85b`。源码/看板/数据库唯一写者为当前聊天；无子代理、无原库/现网/设备操作。接续79项输入原字节及旧看板保存在 `.local/goal-20261015/p07-inspect01/{dirty-files,dirty-hashes-before.json,board-before.md,ownership-input.json}`。除本助手更新看板外78项原字节保持，结束核对见 `p07-inspect04/inherited-check.json`。

退出标准：新工具实际消费产品轮换后的原/新签名，正确区分全旧、全新、混合、pending和未知；每次诊断原树/备份/身份及钥文件hash不变；真实独立JVM在首个原子替换后被本测试精确终止，保存原件并拒绝旧绑定启动；混合记录和半写marker在空租约索引下也拒绝启动。任何分类均不能授权恢复或启动。

改动路径→影响层→必跑及复用依据见 `p07-inspect01/l1-mapping.json`，产品范围扩展前已记录：

- `tools/recovery_rekey_inspect.py`及`recovery_rekey_inspect_state.py`：新增只读CLI，实际Python消费为必跑；复用原请求、秘密外置/路径检查和独立HMAC算法，不改既有轮换入口。
- `RecoveryFenceAuthority.java`、`RecoveryLeaseHistory.java`、`RecoveryLeaseProcess.java`：显式P5启动检查受影响，必须实际正/反启动、生命周期锁及围栏/effect回归；未新增HTTP接线或迁移，不用本批内部文件/权威测试替代后续真实HTTP/客户端L2/L3。
- `RecoveryRekeyInspectFixture/InspectIT/StartupIT/CrashIT`：真实产品文件、独立JVM、Python消费者及仅本测试子进程终止；合成记录不代表真实设备或实际历史进程事实。
- `docs/RECOVERY_REKEY_RUNBOOK.md`：操作边界和工具用法，历史[外部供应L1](GOAL_P07_OFFLINE_REKEY_ACCEPTANCE_2026-10-15.md)按输入与未受影响范围复用。Android、网页和Python D15任务监控未变，不机械重跑无关链。

## 实现与实际发现

诊断要求显式原请求、明确相邻原签备份和外部两代钥，验证固定身份/原marker摘要/旧HMAC及原字段，对每条当前记录与推导的原/新规范字节比较；有界读取、拒绝链接/硬链接、额外布局、缺件或可见并发变化。完整分类退出0；零字节/坏pending、篡改或不可解释输入输出UNKNOWN、退出3。输出始终`keepWritersStopped=true`、`recoveryAllowed=false`及`productionApproved=false`，不删、不修、不重试。

旧备份没有签名的完整process集合清单，因此`processSetAuthenticated=false`；前后hash只是两次观察，不证明全部写者停机或持锁快照，因此`writerLivenessVerified=false`。即使ALL_OLD/ALL_NEW也不等于可恢复。显式还原工具及可信全集/排空前置继续作为开发缺口，不能用手工复制一部分文件冒充完成。

新增测试实际发现原`validateStartup()`在空租约索引下没有验证保留process签名和root未完成布局：旧marker/index与新签process混合、零字节marker pending均被错误接纳。两项确定性原失败及其已新增的合成启动记录完整保全。修复为持协调锁时验证必要root布局及全部保留process HMAC/身份/文件名，再执行原租约扫描；发现pending拒绝，原件不改。当前JVM仍持有生命周期锁时复用原描述符，避免POSIX关闭第二描述符释放原锁；不将PID是否存在当进程身份。

## 运行、失败及复验

证据根 `.local/goal-20261015/`，全部原runtime目录 `.local/rtmp-p07inspect01/02/03/04/`及`.local/rtmp-p07startup01/`保留，不是Git工作树，不清理或复用。

| 批次 | 实际结果 | 原证据与归因 |
| --- | --- | --- |
| p07-inspect01 | 8项全失败，退出1 | Python Windows文本输出把规范LF转CRLF，Java严格消费者拒绝；七次真实轮换和一次实际强制终止已发生，完整保全，未到启动断言 |
| p07-inspect02 | 8项中7通过、1失败，退出1 | CLI改为精确二进制UTF8后分类通过；强制终止留下零字节pending，产品正确UNKNOWN/3，夹具只接受0而失败；原件保留，未到启动断言 |
| p07-startup01 | 2项全失败，退出1 | 两项实际启动错误放行，属于产品缺陷；无恢复或生产接线。原XML、目录和哈希保留 |
| p07-inspect03普通test | 2005/2005，0失败/错误/跳过 | 租约、围栏、effect、配置/default-off及事务代理受影响回归；原XML在`xml-test/`，不是IT/HTTP/设备结果 |
| p07-inspect03专项IT | 14项中6通过、8失败，退出1 | 启动拒绝2/2及既有Python供应4/4通过；8项夹具`int[]`经AssertJ被当单对象，实际0/3退出码均被误判；保留原XML和运行目录，只修夹具 |
| p07-inspect04普通test | 25/25，0失败/错误/跳过，退出0 | RecoveryRekeyCliTest 17、RecoveryControlLockTest 8；跨JVM正向、新钥实际启动、活动writer/lease、并发仅一次、原请求重放和尾替换回滚 |
| p07-inspect04专项IT | 10/10，0失败/错误/跳过，退出0 | 只读诊断7、确定性启动拒绝2、实际强制终止1；真实Python/产品JVM消费及hash守恒 |
| bootJar | 成功，退出0 | `p07-inspect04/bootjar.log`及保留JAR；不是部署/发布 |

实际命令以 `./gradlew.bat -p server recoveryRekeyClasspath test ... integrationTest ... --offline --no-daemon --console=plain`运行；各批`gradle.log`、原XML、`source-input.json`和映射保存选类与输入，前一批XML在覆盖前复制。`P07_PYTHON=C:/Python314/python.exe`、`P07_TOOLS_ROOT`及D盘TEMP/TMP/Java临时路径仅设置本任务子进程。一个Gradle写者。没有复制旧XML当本次执行，普通test与IT分别统计。

最后实际中止由监测首个process原子替换后仅终止本测试精确产品JVM，保留515条目标的原/新状态：110新签、405旧签，Python分类MIXED且保持停写。测试进程PID6532已退出，旧绑定实际启动被拒绝。不同运行时也可能留下零字节pending，UNKNOWN是应有结果，不清pending凑可读状态。所有失败根因和计数分别保留：换行1、零字节pending夹具假设1、AssertJ夹具1、确定性启动产品缺陷1，不把它们串成相同根因或清零历史暂缓。

2005项及既有供应4项在inspect03通过，后续产品Java源码未变；inspect04只改诊断异常封闭和退出码断言。输入关系见`p07-inspect04/source-check.json`，不机械复跑2005项。最后源码输入/哈希全部未变，自审含路径拒绝、私钥不输出、描述符锁语义及失败原件保全；当前助手为作者/自审，Python消费者不等于独立人员。

## 产物、环境、Git和剩余

最终JAR保留 `.local/goal-20261015/p07-inspect04/server.jar`，280,748,519 bytes，SHA256 `53A800C24AEB73B06613A9426366E6A53B89526F129D2B6091A4150E5EC8AAE4`。最后runtime 1436文件的hash/长度清单保留；各原失败批也有独立清单，收尾全量复核。原签副本、零字节pending、请求/输出/测试合成钥和全部日志均在本机私有产物区，不上传Git。

无项目API、Java/Python、隔离MySQL、ADB/emulator/qemu任务或监听；共享3306/33060原MySQL保持。收尾C/D可用78,886,641,664 /20,839,645,184 bytes，`processes-after.json`、`listeners-after.json`、`disks-after.json`记录实际事实；D盘变化含并行冷产物清理，不虚称本批释放。清理聊天本轮只获三处冷Android生成产物检查范围，本聊天已按用户授权发写权协调，保持这三处无消费者；server/build/report-rendering/build、本批原件/失败库/源码继续保护。第二轮清理已结束并释放范围，回执为`.local/disk-cleanup-20261015-rebuildable/task-complete.json`，详见[独立清理记录](D_DISK_REBUILDABLE_CLEANUP_2026-10-15.md)；后续使用既有默认共享依赖缓存，不恢复指向弃用专用cache的旧命令。

本批按明确路径提交/推送`codex/development`，实际SHA/远端与全改动在`p07-close01/post-commit.json`，不自引用循环提交。生产七类决定及TEMP边界、`localDeletionAllowed=false`不变，不合main、不打Release、不部署、不用正式签名。

仍未完成：显式可信恢复/还原入口、签名全集/历史钥引用保管、跨截点重建、升级/回退、Linux/异地及后续真实HTTP/Android/Chrome组合；新增诊断不能取代这些必做开发。下一项从可信原签全集及历史钥引用边界继续，或推进独立升级回退验收；D/O/U、第8节、E06 L2和D18 L3继续未完成，既有设备/Chrome审批阻断及取消范围不恢复。
