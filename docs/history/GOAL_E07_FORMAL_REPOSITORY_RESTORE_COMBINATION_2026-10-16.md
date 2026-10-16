# E07 产品提交仓库与原未知批次恢复组合开发备份

667 在产品 PdaSession/SubmissionRepository/SubmissionTransport 上完成真实提交成功但下行响应丢失、原 Room 重开查询、整库/文件恢复及第二 Android JVM 原 ID 查询成功。两阶段各 1 test、0 failures/errors/skipped，独立 Python 消费通过。本单元仅受影响组合开发备份，非 E07 整体 L2、D13–D16 整体或 D18 L3。

## 输入、决定及影响范围

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`；运行输入 HEAD/远端 `79c549e758f1320b34a84e32869e9ba7594e9c69`。本恢复聊天唯一写者，清理聊天明确 complete-released 并实际 idle，进度聊天 idle，无子代理。87 项继承改动保留。
- 证据根 `.local/goal-20261016/e07-formal-repository01/`。input06/07 共5383项、SHA `e32368253b48cbef7f840c1a63896109f943c2e0e85740c9dd66ec8ea3bc4b26`；同号 binary diff、未跟踪原字节、status/worktrees、旧看板、写权、进程/监听/磁盘全部保全。`review-result01.json` 核5376项继承输入仅本聊天看板变化；新增七测试路径与运行输入一致。
- `design01.md` 记录补665报告明确缺少的产品会话/提交仓库入口。`allocation667.json` 在目录不存在、监听及独占 socket bind 核空闲后分配 `.local/e2a-cross-20260925-667/`、DB19377/HTTP19437/proxy19438、19439预留；源schema及空伴随恢复schema身份按 connected/shutdown 原件核对。未复用666或其他失败库。
- 准备身份与已登录令牌为合成装配；真实 SessionVault AES/GCM/AtomicFile 执行，JVM测试JCA只供应进程内AES key，不替换设备身份或声称设备Keystore、登录/刷新通过。服务端是同Java驱动中 Spring context 关闭再启动，不声称独立服务JVM。
- 运行依赖本树继承的未提交恢复helper/端口运行器（含未跟踪 `E2aTestPorts.java`），逐路径SHA已包含于input06；本提交只保存七个新测试与报告/索引，不混入这些继承内容，不声称该提交脱离本树即可复现。提交前断点input08及附件保存全部输入/环境；最终Git回执见本根后续快照。

| 改动路径 → 影响层 | 本切片必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| Android retention测试目录 `JvmSessionKeyStore.kt`、`FormalRepositorySession.kt` → 合成已登录会话、真实产品网络与持久保管 | 实际PdaSession/SessionVault读写、源/恢复两Android JVM真实HTTP | `new-code-sha01.json` 绑定七新增路径；1562产品路径固定于 `product-input-sha01.json`，清单SHA `1a0c09051e9c7e5782af5866ff5456d6fdf223d39312afa8b2fbed6e94560b24` |
| 同目录 `FormalRepositoryPreparation.kt`、`FormalRepositoryRestoreHttpTest.kt` → 产品激活/扫码/冻结/未知恢复/原签归档 | 产品形成原批次、成功响应丢失后持久RESULT_UNKNOWN；原ID查询/重开/无重传、全表及guard守恒 | input06逐路径；665报告及 `reused-input-sha01.json` 133项依赖未变；另4个后续留存工具变化明确排除，不虚称复用清单全不变 |
| server e2averify测试目录 `E2aFormalRepositoryProxy.java`、`E2aFormalRepositoryAndroid.java`、`E2aFormalRepositoryRestore.java` → loopback断响应、顺序驱动与完整恢复 | Java/Android编译、非空升级、源/恢复真实HTTP/MySQL、SQL/私有文件/验签配置恢复、独立Python | 665正式报告与输入SHA、原591件复核不变；权限/事务/并发等未改产品链只按原范围复用；本次无产品源码、API、迁移、页面或布局变化 |

## 实际执行及独立消费者

1. `build03-result.json`、`compile03-result.json`、`run667-result.json` 均退出0。`formal-source.xml` 与 `formal-restored.xml` 各1/0/0/0，单次一个重型Gradle，两次实际Android JVM；源日志23秒、恢复22秒。没有复制旧XML充当执行。
2. 非空旧结构升级后，产品 LocalActivation/WorkProcessor/SubmissionFreeze 形成两包原事实和原批次 `58331f78-366a-4955-a31c-c1e952867ecc`。产品首次查询404后POST真实成功；代理先保存上游原字节，再断下行。产品持久 `RESULT_UNKNOWN`、原ID/原payload、receipt null、autoRetry=false，关闭Room后保存真正未知副本。
3. 源Room重开经产品原ID查询200、ReceiptVerifier核原签并归档；完整SQL/7私有文件/验签配置实际恢复到空伴随schema。第二Android JVM从原未知Room副本恢复，经同原ID查询200并核原归档，不重传submission POST。两端SYNCED再重开重放，无新HTTP或Room事实变化。
4. 源请求5次（download-ack、GET404、唯一POST200且下行丢失、GET200、archive-confirmation200），恢复请求2次（GET200、archive-confirmation200）。服务恢复HTTP前后179业务表223行、DDL/迁移历史及文件完全一致；dump665291 bytes、SHA `fa024e4683d9471fd329107614993d4bec0368a6c919ae333f9bb363a8bcca30`。
5. `consume66702.py` / `consumer02/result.json` 独立只读核原SQL、SQLite完整性/FK、83本地表26行、原签与固定公钥、原正文/成功/归档及请求序列。未知→成功仅local_submission与local_submission_archive变化，两个原有效guard、capture/scan/event/设备序号全部保持。源/恢复唯一差异是归档本机观察时间confirmedAt，分别1792131796237/1792131838079，显式核在各自真实执行时间窗且不早于服务签名归档时间；其余全部表/列相同，各自重开全表逐字相等。物理SQLite字节不同不冒充事实不同。
6. 原payload SHA `02b00e3c5d083a63509c530f429e88b4040d0c2394da251a95e1abe614c09d01`，固定公钥SHA `f99d5592936c04f39486965370c6b5516b76714b68c4b469a1a1a5c37b68fbe7`；RSA成功与归档原签均核验。604原件261439064 bytes读取前后与自审再次逐路径/长度/SHA相同，清单SHA `180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3`。

## 失败原件与自审

- build01 JsonNode/ObjectNode类型及compile01 Provider构造版本类型失败保留，随后最小修正编译通过；本地生成脚本过宽替换的原稿以 `.failed-generator01` 保留。所有旧源码、命令和日志保留，未清零同根因计数。
- 666有真实POST成功业务写入，产品正确持久RESULT_UNKNOWN，但测试误期autoRetry=true失败。该库未复用；`preserve666.py` / `failure666-preservation01.json` 核认证停机、零监听、83表25行完整性/FK及383原件223477679 bytes不变，清单SHA `1b117169ed05df2fde0f3f60ae58e2e3d72d2851b1f2df224e5fdc3a5ba1fba6`。旧 `failure-run666.json` 第二根因判断错误明确由保全文档纠正，旧原件未改。
- consumer01 首次把源/恢复实际confirmedAt也要求相等而失败；目录/脚本/失败说明保留。只读逐表定位唯一差异后，新consumer02仅显式核本机观察时间，其余字段仍严格相等；没有重跑667业务或放宽原签/guard/事实标准。只读诊断的GBK解码、尾行PY/误读summary路径错误也留原输出，不作为产品失败或通过。
- `review-result01.json` 为作者自审与独立Python算法核对，不冒充独立人员审查。七测试文件≤200行、方法体≤30行；边界合成标识、无嵌入秘密、生产不新增注入；`git diff --check` 通过。665/666/667原件分别591/383/604项再次不变，未改继承Android/UI/工具/文档或last-request原件。

## 环境与未完成

667 connected/shutdown身份原字节一致，认证停机；19377/19437/19438/19439零监听，无本批Java/MySQL残留。原TEMP AVD/ADB及共享mysqld仅观察保留，身份未核清共享库不连接/停止。disks-07采样C/D余量82997571584/22294990848 bytes；新增证据/DB/dump/binlog/临时均D盘，无清理。

无新Chrome、模拟器/原PDA、APK、原Keystore、全普通回归或真实异地介质结论；未变页面不新增Chrome通过。D16正常有效guard目标合法退出、复杂引用/可取回及D13–D15完整组合、E06/E07/E08剩余L2、U组合、E09/D18最终候选仍未完成。七类生产能力/TEMP生产归档保持关闭，localDeletionAllowed=false，不接原库/现网、不合main、不发布部署、不恢复已取消换机链。总目标第8节未满足，保持active，继续独立工作。
