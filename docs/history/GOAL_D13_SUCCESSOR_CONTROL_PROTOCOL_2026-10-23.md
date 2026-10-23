# D13 后继恢复轮次控制协议前置

用户批准最小设计后，Java/Python控制链已支持同一installation的追加后继轮次。正式跨进程套件44项全过，包含六个真实进程死亡边界；Python57项、Java控制57项及既有消费者79项分别通过。本片只证明控制协议前置，不证明已有binding的数据库再次恢复、D13整体、最终候选或第8节闸门。

## 输入与范围

- 固定树`D:/acrylic-pda-outbound`，分支`codex/development`，输入`e03cf5ec6b8089d34d9aa14e9aa73268c261c5f5`；版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。总目标active、未完成。
- 证据根`.local/d13-successor-control-20261023/run01/`；`decision.md`保存设计、备选与退出标准。`baseline/`、`resume01/`、`resume02/`及`closeout01/`保存实际Git、完整dirty原字节/SHA/patch、写权、进程/监听、ADB/Keystore及C/D/E余量；各时点不能互相替代。
- 本片17条明确路径为五个Java控制类、五个Python控制模块、六个测试/运行器及本地控制指南。写前原件分别在`before-implementation/`、extra清单、`before-writer-test.json`、`before-process-test.py/json`；共享文档写前原字节/SHA在`before-closeout-docs/`。
- 22条继承改动哈希保持；另一聊天的Android应急导出选择器及后续文本导出工作独立在途，其路径、APK、设备操作与测试不计本片。没有新建子代理或发送聊天消息。

## 实现的最小协议

1. 首轮命令仍只接受formatVersion=1，版本2初始化在创建文件前拒绝。后继版本2仅从实际上一`RELEASE_READY`进入新`QUIESCING`，严格CAS、新session；QUIESCING保持epoch，RECOVERING生成未使用过的epoch。session及epoch全历史不得复用。
2. 后继命令精确增加`predecessorReleaseSha256`、`predecessorNormalIntentSha256`、`sourceBindingSha256`、`sourceNormalSha256`、`targetServerUuid`、`targetDatabaseSchema`。前驱必须是真实上一release；整轮保持全部新增pin和原上下文，不降级、不跳阶段。
3. 原NORMAL严格校字段、hash、installation、revision、previous hash和release。初始`normal-admission.json`不覆写；更多原件追加`normal-history/<intentSha256>.json`，核精确文件名、内容和无别名。缺原件不补造；源DB两个摘要在此仅作外部pin，尚未核真实数据库。
4. Python提交前、Java/Python完整读取及pending关闭意图均校验全链与前驱原件。原ID同文重放保持，异文/错误前驱/目标变化/复用session或epoch拒绝且不改文件；writer drain与原关闭意图耐久边界继续生效。
5. 兼容首轮普通读取：可选初始NORMAL仍由既有NORMAL消费者处理，进入后继轮次时严格核对；追加history始终验证。没有修改数据库binding/NORMAL、迁移、运行时准入或P5，既有数据库消费者仍拒绝后继轮次。`ordinaryWritesAllowed=false`、`localDeletionAllowed=false`保持。

实现入口：[指南](../parallel/R09/LOCAL_CONTROL_GUIDE.md)、`ops/control_successor.py`、Java `RecoverySuccessorControl`与`RecoverySuccessorEvidence`；正式互操作入口为`tests/run_control_java.py`。

## 实际验证

| 证据 | 结果及适用输入 |
| --- | --- |
| `compile02/` | 独立javac exit0，2701冻结输入、110依赖，冻结时无漂移；85条缺失注解/过时API等编译警告，无错误。非Gradle完整回归或APK构建。 |
| `python02/` | 57项全过，0失败/错误/跳过，exit0。包含新后继原件/CAS/重放/上下文及原有控制回归。 |
| `junit02/` | 57项found/started/succeeded，0失败/跳过/中止，exit0。 |
| `junit-consumers02/` | 79项found/started/succeeded，0失败/跳过/中止，exit0。覆盖准入、身份、closed authority/transaction与initial NORMAL既有消费者。 |
| `extra03/` | 10组重算hash的恶意history/pending由Python及独立Java精确拒绝，文件守恒；旧Java消费者对v2精确返回CONTROL_FIELDS。exit0。 |
| `cross03/` | 44项，43过/1失败/0错误或跳过，exit1。产品返回正确锁替换拒绝及审计警告，旧断言错误要求stderr为空。失败原件保留。 |
| `cross04/` | 正式`run_control_java.py`实际执行44项，0失败/错误/跳过，exit0；含12项后继测试及单独选择的六死亡边界测试，不重复继承类套件。 |
| `review-final01/review.json` | 17条范围、22条继承逐字节守恒；敏感扫描无命中，diff空白检查exit0。 |

`cross04`冻结最新Python源码及正式运行器，复用`compile02`已编译Java字节，核全部110依赖；产品Java源码无漂移。2701清单之后仅三条测试/运行器变化：`control_java_process.py`显式Java UTF-8/英文审计输出、`control_java_writers.py`精确验证原审计两行、`run_control_java.py`注册后继套件。不能把早期2701输入宣称为最终全部无漂移。最新Python范围逐字匹配cross04冻结输入；指南属于纯文档更新。

本机完整命令/参数、cwd/临时目录、运行器SHA、退出码、逐进程transcript及输出哈希见各轮`command.json`、`result.json`、`output-hashes.json`。最终实际命令由`C:/Python314/python.exe .local/d13-successor-control-20261023/run01/run_canonical.py cross04`触发冻结后的正式运行器，未使用本机monkey patch替代正式进程实现。Java进程明确设置file/stdout/stderr UTF-8与英文日志，保留审计输出。

关键摘要：

- `cross04/cases/summary.json`：`edf54cc9a26fd6028b4f7885cafd4e393dbbc2b8e8561ea2d4ef10fcc21766ce`。
- `cross04/frozen-inputs.json`：`b4f74e77293bb6bacdd8bb598835e37c33faf308a359526af3f114c37daa566e`。
- `extra03/proof.json`：`ea24180636e03ba13aa2eb4470129faeeb2993e46d29708c8c894298f4ee729d`。

作者、自审、Python和独立Java进程消费者均由同一助手操作；跨语言消费者独立不等于独立人员审查。NORMAL及DB摘要均为明确的合成测试输入，不冒充可信业务事实或原身份。

## 失败与收口

`compile01/`在javac前因PowerShell逐文件冻结缓慢、期间修正输入而主动停止，不计编译或测试通过。`extra02/`已执行反例但证明文件误用协议64KiB限制，汇总失败；改为本机证明写出后extra03成功。`cross02/`的中文审计UTF-8解码失败及600秒整轮超时保留，不能计通过；后续最小classpath及编码修正见cross03/cross04，审计没有被压掉。

cross03的单项失败只修测试断言：核exit2、精确拒绝码和审计类/事件/实际路径；产品实现未因该失败放宽。版本1兼容修正后实际重跑上述Java既有消费者，不用历史结果替代。本片没有运行旧IT/旧run03，没有连接数据库或操作设备，也未进行Android构建/签名/部署。

公共看板旧叙述已[归档](ORCHESTRATION_STATUS_BEFORE_SUCCESSOR_CONTROL_2026-10-23.md)，完整原字节另存本机；保留另一聊天Android结论。收口快照、暂存/提交/推送精确回执及最终环境以本片`closeout-*`为准，提交为开发备份，不表示整体验收。生产、原库/现网、正式签名、main、Release及取消链保持关闭。

本片清理审查见`cleanup-review01/result.json`：没有分配测试数据库或大型任务缓存，列明冻结源码、失败原件及逐进程记录仍用于精确输入追溯，保留原字节；没有删除文件，本片释放0字节。没有清理另一聊天Android缓存/产物或身份未认证的数据库。C/D/E余量以该审查和最终环境各自时点为准。

## 未完成与接续

下一有界缺口是数据库append-only目标binding及每轮NORMAL协议与迁移。必须保留旧binding/NORMAL/映射/投影原行，不更新不可变表、不删触发器、不放宽实际UUID/schema；随后用已有binding的非空dump在全新隔离目标恢复，证明真实API/worker/P5/文件及失败回滚。不能继续用无旧binding源替代这个正向输入。

D13整体、其余worker/文件入口、OS拒绝自halt、D16整体、D18实际最终候选与第8节仍缺；本片不把这些开发缺口转为现场事项。只在实际最终候选全部闸门满足后才能complete。下一接手先读AGENTS、Git工作流、看板，再读本报告和本机decision/closeout，重核写者、dirty、远端与环境后续作。
