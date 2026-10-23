# D13 已有绑定实际跨实例后继死亡与原命令续作

六个真实Python进程死亡点分别在全新双实例输入完成：pending期间Java读取及实际API/非Web worker严格拒绝，换commandId拒绝且不改文件；仅原命令续作、同文重放字节相同。每个输入均继续完成追加后继准入、真实API/独立生产worker/P5及XLSX/PDF下载，由独立Python原件消费者核验。最终六项各1/0/0/0，六消费者exit0；只计合成test-only权威的有界组合，非D13整体、最终候选或第8节通过。

## 输入、实现与复用

- 固定主树codex/development，输入HEAD及实核开发远端`2bcf98b7947b7ab9f6cebf0a2900ff4b58bddd72`。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。总目标active、未完成；没有新增代理或发送聊天消息。
- 证据根`.local/d13-cross-instance-death-20261023/run01/`。decision.md记录已批准最小设计及退出标准；baseline、resume-snapshot02、before-cleanup、final-snapshot保全全部dirty原字节/SHA/patch与环境。首轮resume-snapshot01因Windows PowerShell 5缺Path.GetRelativePath失败，原残留保留；改用已装PowerShell 7在新目录成功，Write/Check随后成功，见resume-notes01.json。
- 六个测试/消费者路径：RecoverySuccessorDatabaseFixture、RecoverySuccessorRuntimeSupply、RecoverySuccessorDeathProbe、E2aSuccessorCrossInstanceDeathIT、E2aSuccessorPendingStarts及verify_d13_cross_instance_death.py。逐路径原件/SHA见originals、before-*-fix与source-review-final。产品、迁移、契约、生产配置及Android未改；1138产品/资源/渲染器输入与前片逐路径SHA相同。
- compile04冻结2736输入/110依赖，无漂移；adjacent-compile04核25直接调用点编译exit0；unit04同次103/0/0/0。独立javac和定向JUnit不称完整Gradle回归；产品未变的既有业务结果只按上述输入关系复用，不冒充本片重新执行。
- 原23条dirty中的看板可更新，其余22条继承改动逐字节保护；另一聊天Android及取消链WIP不改、不暂存、不清理。完整路径以快照为准，不以Git压缩目录项数替代展开文件数。

## 六个最终新输入

| 最终轮次 / 死亡点 | 新隔离源 / 目标 | API及worker实际拒绝码 | 结果 |
| --- | --- | --- | --- |
| it06 / intent_head_committed | 1777 / 1778 | CONTROL_CLOSING_INTENT | 1/0/0/0，consumer exit0 |
| it07 / intent_appended | 1779 / 1780 | CONTROL_INTENT_INCOMPLETE | 1/0/0/0，consumer exit0 |
| it08 / intent_head_staged | 1781 / 1782 | CONTROL_UNKNOWN_ARTIFACT | 1/0/0/0，consumer exit0 |
| it09 / history_appended | 1783 / 1784 | CONTROL_COMMAND_INCOMPLETE | 1/0/0/0，consumer exit0 |
| it10 / pointer_staged | 1785 / 1786 | CONTROL_COMMAND_INCOMPLETE | 1/0/0/0，consumer exit0 |
| it11 / pointer_committed | 1787 / 1788 | RECOVERY_DATABASE_ADMISSION_REFUSED | 1/0/0/0，consumer exit0 |

每行使用不同实际UUID、端口、datadir和合成账号，源已有binding/NORMAL，非空完整dump/storage在第二实例实际恢复。不是将不同输入的分段正向拼成一条通过链。两个pending子JVM及后续API/worker各自实核目标身份；真实Python os._exit(73)，换ID exit2，原命令续作/重放均exit0。六点读者语义分列：intent头提交时旧revision仍可读但新写者拒绝；pointer提交时控制revision已前进而数据库尚未投影，实际产品按数据库准入拒绝，其余半态按精确控制错误拒绝。

每个输入均保全七组控制目录原字节/哈希，interrupted、换ID前后及观察后完全相同；resumed与replayed完全相同，原current/admission以外旧原件保持。数据库/原文件在pending观察前后守恒。只追加四表4/1/1/1、server_state代次加一；原binding/NORMAL/任务全部列及源事实保持。181表、8原文件、185触发器含definer逐项核同。

同一输入完成后继后，实际API与独立生产非Web定时worker持有各自活P5租约，旧任务保持失效，新键双格式成功，原顺序/整数克及实际下载回读。再将目标设RECOVERY_PENDING，六HTTP503精确拒绝且全表/文件守恒，恢复模式由测试夹具设置不代表生产开放。P5提升仍syntheticTestOnly=true/productProvisioning=false；没有增加生产故障开关或改产品拒绝逻辑。

- `it06` proof：`1f82b241c2e7b2d0685e3a0432d34ab7dc24ec66fdecf31796cbd1eb5c58f9d2`。
- `it07` proof：`2da4066eac562eb8d1f8080eb7dd3acff99688a79a0d113499b96032d15a1538`。
- `it08` proof：`7414b3eae8f6ee1db4649c50ed5058f7560f92fe234829ff0a3ad9131200aa76`。
- `it09` proof：`1d0aa43ecb81dd956c64b81c396dd8e9d12f7305490bae85936af625e8a13b11`。
- `it10` proof：`ec335e3c75f0390f89b5817a4d78928ab7f939ccc41f7f874be999aac3202321`。
- `it11` proof：`542ed8776fe7cefcf7082349efcf34f72d6b7f80ccde4862b9da4af419e30df1`。

## 失败与实际命令

- it01/1767–1768、it02/1769–1770各1项失败：测试JSON泛型在记录ArrayNode命令时被推断为ObjectNode并触发ClassCastException；原样重跑只有一次，随后显式JsonNode参数修测试记录，不改产品。原字节见before-fixture-fix。
- it03/1771–1772各1项失败：pending子进程重建一次性profile触发One-shot attempt already used；改为精确校父进程分配的私有目录及真实目标身份，不重用profile，不放松产品/数据库校验。原件见before-pending-fix。
- it04/1773–1774及consumer-it04曾在compile03输入通过，只作历史有界结果，不列入最终六点矩阵。
- it05/1775–1776各1项失败：产品实际正确返回CONTROL_INTENT_INCOMPLETE，测试旧宽码集合遗漏此码。修测试为逐死亡点精确预期，最终compile04重新分配全部六个新输入。没有忽略异常、降低围栏或把启动失败当业务正向。
- compile01–04/unit01–04各自结果分列保留，最终只引用compile04/unit04。最外层batch01因it05失败中止。batch02前五轮及消费者exit0；it11业务1/0/0/0之后尾检发现浏览器新增debug.log，驱动exit1，原失败保留。所有失败日志/退出码与源快照原样保留，不改成通过、不复用失败库。
- 外部尾检单独审计见external-drift-review01及batch02-reconciled：六轮原305个renderer/browser输入全部逐字节相同，无改写或缺失；唯一新增153字节Chromium GPU运行错误debug.log（SHA f25ebdc64934e4f1a6d42f610520ee17c40fa4eae32497181fe64891773b96fd）已复制保全。另调用consume.py it11 compile04 consumer-it11实际exit0，原件全部核验；离线审计exit0不改写原驱动exit1，原日志不清除。本片仍不称新增可见布局通过。
- 清后首个after-cleanup-final.py运行exit1，原consumer-after-cleanup目录及脚本保留。诊断after-cleanup-diagnosis01逐字段定位七份证明仅identities为Python tuple与JSON list的内存类型差异，字段值无差异；新after-cleanup-json.py先按原save序列化，再同时比较完整JSON与原始文件字节/SHA，七份全部一致，实际exit0。it04按其原compile03核源码/依赖，其余六项按原compile04核验；没有忽略身份、跳项、重跑IT或恢复已清库。原失败不改写为成功。
- 实际最终驱动为`C:/Python314/python.exe -B .local/d13-cross-instance-death-20261023/run01/run_final.py`，调用run_product.py compile04 it06–it11及consume.py；精确argv、cwd、环境身份、源码/依赖SHA和退出码在各command/result/allocation/input/output JSON。控制死亡由既有tests/control_process.py注入真实os._exit；无产品故障开关。作者/自审与多工具原件消费仍同一助手，不称独立人员。

## 保存、清理与Git

- 1767–1788共22个本片新分配实例均认证停机，清前重核目标端口及进程无消费者。cleanup01只清明确datadir、binlog/index与可重建完整dump；逐文件输入SHA/路径/命令/依赖/结果、停机身份和余量齐全。关键恢复SQL行先逐字提取为retained-recovery-rows.sql.txt，非执行脚本。
- 删除逻辑长度`4926915572`字节，D窗口`22225391616→27247611904`，观察净增`5022220288`字节；窗口外及背景变化不归因本片释放。consumer-after-cleanup02逐项解释22份输出清单缺件，七份成功原proof与清前逐字节相同（其中一份历史it04，六份最终矩阵）。
- 保留原件/身份/签名/HTTP/文件、失败日志、控制目录、全量表/结构/触发器/文件事实、冻结源码/依赖、最终APK、全部WIP及其他聊天产物。已清库不重启/复用；无其他库/共享缓存清理。localDeletionAllowed=false。
- 六代码/消费者及五文档按明确路径作“开发备份”；看板含其他聊天继承文档WIP，不整文件暂存。实际提交/推送及远端回执见closeout-submit；收尾事实刷新留看板WIP，不循环提交。旧叙述[归档](ORCHESTRATION_STATUS_BEFORE_CROSS_INSTANCE_DEATH_2026-10-23.md)，完整原字节/SHA在closeout-docs。

## 未完成与下一步

下一有界缺口是已有binding实际跨实例后继输入上的真实worker死亡/孤儿P5及拒绝恢复组合；先按同输入守恒、实际进程/OS锁、六HTTP、显式worker、退役后fresh启动标准复用既有worker崩溃工具，分配新双实例，不重跑旧IT。其余worker/文件入口、OS拒绝自halt、D13整体、D16整体、D18最终候选和第8节仍未满足，不能挪为现场项或将局部结果拼成最终通过。

无本片设备、模拟器、APK/正式签名或部署操作。原库/现网、生产开关、main、Release及部署关闭；取消链、多跳、接收者确认/激活、旧run03不恢复。文档/清理/开发备份不计验收；总目标保持active且未完成。
