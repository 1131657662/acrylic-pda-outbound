# U04 原生反馈异常与原 ID 恢复 L1（2026-10-17）

单次原生 instrumentation 通过：保存事务提交后反馈回调异常，原输入和唯一有效保存仍耐久保留；新 engine 核验原任务后恢复，不重复保存或回调。独立 Python 核原业务库83表328行不变。本批仅测试 L1 开发备份，不计 U 整体、E08 L2、D18 L3 或总目标完成。

## 输入与必跑映射

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入HEAD/实际远端均为 `206f589a94004bc3ac8990d2bbe0bf7a2226c218`。本聊天唯一写者；清理聊天协调回复已结束且实查idle，其他项目聊天无活动写者。全部继承tracked/untracked内容保全，不混入本切片提交。

证据根 `.local/goal-20261017-resume/native-feedback01/`，设计先存 `design01.md`。全量 `../input02.json` 保存5456项，SHA `ed82ca920f2495aa54df1c6127c2916a671a9c74bfd6406e91755f0c5cef474f`；同号status、binary diff、未跟踪原字节、worktrees、旧看板、写权、进程/监听、C/D磁盘和实际远端齐全。`source-binding01.json` 核运行后全部输入相同，SHA `540c4c69b5b1d57ebb18e3d075e03d68cdca26a7cc05aa26ae5fe4347d0172bd`。

| 路径 → 影响层 | 必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/independent/IndependentFeedbackDeviceTest.kt` → 原生队列反馈 | 编译、两APK、lint、native Room/engine、提交后故障、停扫拒收、原ID恢复、逐表守恒 | 既有fixture、独立产品路径、WorkSound/原队列/声效测试的逐文件SHA在source-binding01，原input35清单SHA固定；不重新生成身份 |
| 本地device/consume/finish → 执行与消费 | 原APK/身份/全数据保全，新命名库，instrumentation，SQLite/WAL/JSON及原文件核对 | [原生队列报告](GOAL_U04_NATIVE_INDEPENDENT_QUEUE_2026-10-16.md)的pending输入原SHA、既有读取算法；运行器和依赖进入清单 |
| 报告/索引/看板 → 断点 | 链接/事实块/上限、继承保护、明确路径提交 | 无产品/迁移/权限/HTTP/Web/电脑消费者变化，不重跑无关链，不豁免L2/L3必跑 |

自主采用确定性反馈异常，不固定原生SoundPool异步加载时序。声音未就绪分支沿[声效报告](GOAL_U04_SOUND_READINESS_2026-10-16.md)及上述SHA复用；本批不称真实音频硬件失败。新增测试只在新合成库注入，不改产品或原库。

## 原生结果与独立消费

原 `independent-cache-retention01/host02-db/pending-before.db` SHA `6fb56ebc2db21db12e28399cf4cd72645313622e320dde9dc8dd015f889d3f00`，明确合成，原签/模拟时间保持。复制到新命名 `synthetic-independent-queue-feedback-native-20261017-01.db`，不改源库、不签发身份、不改系统时钟。

`device01.log` 单次 `OK (1 test)`，0.556秒，ADB退出0：

1. 原RECEIVED→WAITING_CONFIRM→明确确认→COMMITTED。回调时断言事务已结束、capture原字段不变、唯一scan/guard、总数1，再注入 `IllegalStateException("injected feedback unavailable")`。
2. 故障状态ready=false，已保存提示和停止原因保留；暂停后新输入拒绝。`feedback-failed.json`保留原ID、队列0、保存1和唯一SAVED回调。
3. 关闭并新建engine，原任务重新核验后ready=true，原事实/总数保持，新增回调为空；`feedback-recovered.json`独立保存。这是同一instrumentation内engine重建，不冒充OS进程死亡/断电。

Python实际读取83表和前后JSON：仅capture状态、task状态、scan、guard、task事件5表受控变化，原capture逐字段相同；只新增WAITING_CONFIRM、CONFIRM_REQUESTED、SAVED三事件，schema/trigger相同。输出库SHA `95efa95bf29d31a25728f87c6ff5f857c512ecf9f1a584bfdfe396c4b8e93c90`，源SHA未变。

原主业务库83表328行逐值相同。448个既存files/shared_prefs/no_backup文件中446个字节相同，仅WorkManager WAL/SHM变化，前后3个已取消/0活动任务，其中一条已取消任务ID替换，完整事实在consumer01。新增9文件限本批合成输入/证据，无原文件删除。`consumer01.json` SHA `e68cc69fe04cba64f5f1169d94e8e20775281ae86ccbbc5bc217fc0ed6c4a609`。

## 构建、失败和环境

`build.py` 两APK和lint通过，76秒；lint0错误/严重错误、481警告保留。APK按完整脏树构建，不称仅由本测试提交重建。产品SHA `29b5b3e376f2fbc01419b6aa19d6b4b3864abe4b8a077efd03715c0a5103771c`，测试SHA `3b9f54e0e9c3020ad7cfa5ef8f2a7b497adf08ea283cd80e8f80588fc471530c`；覆盖前两原APK及新产物保留，仅install -r，无卸载/清数据/正式签名。

接续另完成恢复确认三个主机类5项全通过，日志/XML/哈希在上级android-test-attempt02附件。运行前继承input57与运行后input01的965项Android输入逐字相同；只计主机复核，不恢复已取消换机/多跳/激活范围。首轮wrapper下载超时，退出1、未进入测试，timeout.json和下载残件保留；改用已安装Gradle后通过。首次新缓存重复下载依赖，后续改回既有共享依赖，无新复制/安装工具或清理失败残件。

954份证据162963066 bytes双次长度/SHA一致，清单SHA `13cfde4c78e454dc0f2ccf04caf6398b13ee09f4793f88f1c8f483f1483f1319`。业务阶段无失败重跑，注入异常为预期验证；原件保留。只读路径/Windows通配错误已改定向读取，无业务写入；报告组合patch因索引锚点不匹配原子拒绝，确认无写入后分开补入。作者/执行/自审同一助手，独立Python不等于独立人员。

App强停、font1.0、IME0、reverse空，原AVD/ADB保留；共享MySQL只观察，无本批新服务/隔离MySQL。结束C/D余量78708842496/16970244096 bytes；证据/tmp均D盘，仅子进程TEMP/TMP/Java临时设置，无清理。

## 未完成及下一步

D16源码核查：正常成功单有效guard继续保护；WorkCorrections仅SCANNING允许VOID，不能用于清理FROZEN成功单。无合法退出依据，不删guard/变状态造资格，不重跑既有拒绝。D13–D15完整组合、复杂引用清理、E06/E07/E08剩余L2、O/U完整组合、E09实际候选和D18最终L3继续。生产七类决定/TEMP归档及localDeletionAllowed=false保持关闭，不接原库/现网、不合main、不发布部署。

下一项核对D17各来源前后台/故障组合与E07剩余恢复矩阵，优先尚未实际覆盖的独立验收单元。Chrome1540审批阻断不换包装重试；历史失败/计数保留。总目标active。
