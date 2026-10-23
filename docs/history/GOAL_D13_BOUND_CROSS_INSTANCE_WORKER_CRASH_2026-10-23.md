# D13 已有绑定跨实例后继的真实 worker 死亡与孤儿 P5

本片在一个全新双实例输入完成已有binding/NORMAL完整恢复、追加后继准入、实际API/独立生产worker/P5/双格式下载，再注入真实worker halt71、核OS生命周期锁释放、六HTTP围栏拒绝与孤儿退役，最后由不同新API再次认证同一目标并完成P5准入/正常退役。IT首次1/0/0/0、驱动exit0、原件消费者exit0；只计合成test-only权威有界组合，非D13整体或实际最终候选通过。

## 输入与实现

- 固定主树codex/development，输入HEAD及实际开发远端27b20f9bdbeac5b1f839110e77d3a8b3e0e7f52f；版本只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。证据根.local/d13-cross-instance-worker-crash-20261023/run01/。
- baseline/before-cleanup/final-snapshot保存全部dirty、原字节/SHA/patch、目标原件及实际进程/监听、ADB/Keystore/C/D/E余量；before-implementation在改动前逐路径保存原字节或不存在事实。23项继承中共享看板允许更新，其余22项字节保持；Android、取消链及治理WIP不改、不暂存、不清理。当前助手单写者，无新增代理或发送聊天消息。
- 六路径仅测试/消费者：E2aSplitWorkerProcesses、E2aSplitWorkerMain增加专用测试scope及实际第二实例认证；新E2aSuccessorCrossInstanceCrashIT、RecoveryBoundCrashChecks、RecoveryBoundCrashProof和verify_d13_cross_instance_worker_crash.py完成同输入组合。原单实例探针不改，不新增通用业务框架或产品故障开关。
- compile01为初稿成功，后按单一职责整理参数并纳入离线消费者，compile02才是最终冻结输入：2740源码/资源/工具输入、110依赖，无漂移；1138产品/资源/渲染器输入与前片逐SHA相同。unit02同次103/0/0/0；adjacent-compile02核28个相关直接调用点编译exit0。未声称完整Gradle回归，也未重跑旧IT、旧run03或已清库。

## 实际同输入链

- it01实际源1789/DB23789，目标1790/DB23790，HTTP24789；不同UUID、datadir和合成账号/凭据。完整非空SQL/storage恢复保留原binding/NORMAL、原任务和源事实；恢复后先拒绝错误源身份，再仅追加四表4/1/1/1及server_state代次+1，准入没有重写不可变原行。
- 两个独立子JVM分别认证目标实际UUID/端口/datadir/账号/模式；API是Web，worker是生产非Web定时worker。真实后继新键XLSX/PDF完成并实际下载回读，原任务/原顺序/整数克保持。之后只排空scheduler，不关闭worker JVM；新PENDING为attempt0，无artifact或数据库租约。
- worker持实际operation/P5租约时，API真实checkEffect仍接受；仅自有测试worker Runtime.halt(71)，实际exit71、无正常关闭标记。父进程对原process record核OS lifecycle lock由占用变释放；原process与签名绑定不改。
- 首个HTTP503为RECOVERY_LEASE_OUTSTANDING，后五个503为RECOVERY_LEASE_FENCED；API直接registry.admit/checkEffect均FENCED且liveRoots=0。退役只允许孤儿lease删除及index更新，其余P5路径/长度/SHA守恒；原HMAC、lease/index/process关联由Python原件消费者独立核验。
- old API正常关闭后，不同fresh API实际认证同一目标并启动产品/P5，持有再退役自己的操作后正常退出；原两process记录保持，只新增一个fresh process记录，最终index空。不是仅构造服务DTO或合成STARTED字符串。
- 拒绝前后181表/DDL/Flyway/原文件完整快照相同，pending任务仍attempt0；源全表及8原文件保持，185触发器含definer逐项保持。正常运行仅background_job、operation_event、spring_session及attributes改变，原任务全列保持。

| 结果 | 实际核验 |
| --- | --- |
| unit02 | 103/0/0/0 |
| it01 / it01-launch | 1/0/0/0 / exit0；首次执行，没有失败业务轮次 |
| consumer01 | exit0；374源输出、27目标输出、31控制原件及完整冻结输入核验 |
| XLSX | 7表、2明细、0公式、原顺序及整数克；SHA 66a5e6da00ccb73b0bceffde0aca56571b1c653460486171b43ea809dbd8e7ae |
| PDF | 2页、2明细、0注解、原顺序；SHA e59415922c2d4c6c37c5abf73d58431334a4909fd4377b72eaa0f242f4149274 |
| 原件proof | d15b453ded321e89a379c48c6a30966776c01d22536174f0cd3fc6347f60d7ba；清理后逐字节相同 |

实际命令、argv、cwd、源码/依赖SHA和退出码在compile02/unit02/adjacent-compile02、it01及it01-launch、consumer01内。运行器核外部输入前后相同，本片没有前片浏览器debug.log尾检失败。作者/自审及独立工具消费仍同一助手，不冒充独立人员、可信生产供应或新可见布局验收。P5提升仍syntheticTestOnly=true/productProvisioning=false。

## 清理与开发备份

1789/1790均认证停机、前后身份一致；清前再核实际端口及进程无消费者。cleanup01仅删除9个明确datadir、binlog/index及可重建完整dump路径，逻辑447936508字节；D窗口28977274880→29433962496、观察净增456687616字节，二者分列。before.json保存全部删前逐文件SHA、命令/依赖/停机回执；关键恢复SQL行先逐字提取为retained-recovery-rows.sql.txt，非执行脚本。

consumer-after-cleanup解释两份清单恰好4/3项缺件均与清理审计逐字匹配，其余全部原件不变；完整证明按原JSON序列化字节与consumer01相同。保留失败证据、原资料/身份/Keystore、原签名/回执/HTTP/文件、全表/结构/触发器事实、冻结输入/依赖、最终APK、继承WIP及其他聊天产物。已清库不重启或复用；没有原库/共享缓存清理，localDeletionAllowed=false。

六测试/消费者及五文档按明确路径作“开发备份”，实际Git/远端回执见closeout-submit。共享看板整路径不暂存，提交后事实刷新留WIP，不循环提交。旧D13叙述[归档](ORCHESTRATION_STATUS_BEFORE_CROSS_INSTANCE_WORKER_CRASH_2026-10-23.md)，混合看板完整原字节/SHA仅在closeout-docs。

## 未完成与接续

真实halt的同输入组合不覆盖commit UNKNOWN、OS拒绝halt/文件退役失败、所有产品入口或可信生产权威供给。下片先核这些P5故障的实际产品边界和既有工具，确定有界新输入再执行；D13整体、D16整体、D18实际最终候选、第8节及其他D/O/U未完，不能拼历史通过或挪为现场项。

系统3306/33060身份未知，仅观察；无本片设备/APK、模拟器、正式签名或部署操作。生产开关/TEMP归档、原库/现网、main、部署/Release保持关闭；取消换机承接、多跳、接收者确认/激活及旧run03不恢复。总目标active且未完成；文档/清理/开发备份不计验收。
