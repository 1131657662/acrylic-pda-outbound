# D13 完整恢复、生产闭链登记与实际P5同输入组合

本片补齐完整恢复→生产四阶段投影/NORMAL登记→实际P5→独立API/非Web worker→双格式下载的同输入组合。新隔离IT一项成功、零失败/跳过/中止，离线原件消费者通过。仅合成、源截点无旧binding、显式测试夹具身份提升的有界证据，不是产品供应工具、已有binding再次恢复、D13整体或最终候选通过。

## 输入、决定与改动

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 `55e1877a0ada0c90b42ff02f1dc6da3c58d937c7`；版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。
- 证据根 `.local/d13-restored-closed-p5-20261023/run01/`。有效完整初始快照是 `baseline02/`；`baseline/` 是ADB定位失败留下的部分快照，不覆盖。`before-docs/`、`closeout-resume01/` 分别保全41条展开dirty及原字节/SHA/patch；共享文档另存 `closeout-docs/originals/` 和输入清单。
- 只改七个Java测试路径、新增离线消费者及本片收口文档。产品源码、契约、迁移、Android及生产开关未改。22条继承改动逐字节保持；另一聊天的10条Android应急导出选择器路径不修改/暂存/提交。
- 最小决定见 `decision.md`、`decision-amendment01.md`。生产闭链登记完成后，显式调用既有test-only `RecoveryInitialAuthorityProvision`，将合成v1 identity提升为v2 `INITIAL_NORMAL_INSTALL`，重绑writers的identity SHA并另供P5动态树。控制根只允许identity.json和writers.json两文件变化，保留变换前后原字节/SHA、其余控制文件及DB守恒。
- 不改产品身份/准入安全门；已有不可变binding再次恢复所需后继轮次协议仍单列。旧IT默认非P5范围保持，新组合使用独立新IT，旧一次性用例/库未重跑。

## 实际运行与离线核验

| 证据 | 实际结果 |
| --- | --- |
| `compile01/`、`run1707-compile01/` | 编译成功；IT一项失败、exit1且未完成。未初始化运行时权威的夹具调用checkEffect，正确得到RECOVERY_LEASE_FENCED；失败输入、原件、日志及最终dump保留。 |
| `compile02/` | 直接javac exit0，冻结2695输入、110依赖，无漂移；不是Gradle完整回归或APK构建。 |
| `run1710-compile02/` | 新实例1710，DB23710/HTTP24710；found=started=succeeded=1，failed=skipped=aborted=0，exit0、completed=true。 |
| `consumer01/`、`consumer-after-cleanup/` | 均exit0，proof逐字相同；核2695冻结输入、110依赖、308原输出、65控制原件、实际租约/进程/index HMAC和双格式文件回读。无数据库或网络调用。 |
| `review02/review.json` | 1122产品/资源输入及110依赖与上一组合相同，22继承不变。`review/` 首版反斜杠过滤产生count0，已保全；有效复核为review02，不引用空比较。 |

完整命令/参数、执行器SHA、输入与依赖锁、退出码及输出哈希在各轮command/result、source-inputs/dependencies及output-hashes中；本机运行器 `compile.ps1`、`run.py`、`consume.ps1` 同存。编译命令为 `powershell -NoProfile -File .local/d13-restored-closed-p5-20261023/run01/compile.ps1 -Label compile02`；运行器以本机Python执行run.py并指定compile02，实际分配及命令以 `run1710-compile02/` 原记录为准。离线消费者使用 `consume.ps1 -Label consumer01 -Run 1710 -Compiled compile02`，清理后另用新Label。

proof SHA256：`b55e9d7beb155ce8da2071f0a11191f00cc1a6c529d39d88a0fd0591a5241ae2`。作者、自审及离线消费者均由同一助手执行；工具独立核验不等于独立人员审查。上一片77项仅作历史引用，未重跑，不称本片完整回归。

## 组合证明及实际限制

1. 非空合成源真实dump/storage及合成验签物料完整恢复至同实例新schema，179表、schema、原文件及迁移历史核同；源截点无不可变binding。生产投影实际经历QUIESCING、RECOVERING、VERIFYING、RELEASE_READY并登记NORMAL；仅五张协议表变化。
2. 显式夹具身份提升前后数据库不变，控制根18文件仅上述两项改变。两个实际产品JVM从Spring初始化同一闭链、checkpoint、恢复schema及P5/HMAC；API为Web，worker为非Web，记录真实活租约和不同OS进程身份，运行时checkEffect执行，正常退出/退役，最终租约index为空、原P5文件保持。
3. 恢复权威误用于原schema冷启动时精确六gap拒绝：DATABASE_BINDING_MISSING、DATABASE_IDENTITY_MISSING、DURABLE_INTENT_SCHEMA、DURABLE_INTENT_BASELINE、NORMAL_ADMISSION_MISSING、PROJECTION_MISSING；源表/文件无写。不是跨实例绑定恢复正向证明。
4. 旧代次任务所有列、payload和epoch保持；实际API呈现INVALIDATED，旧下载/重试/旧幂等键拒绝。重新查询与新键产生当前代次XLSX/PDF，各一次成功并实际下载，独立解析核原顺序、整数克、members/快照；原任务不自动恢复资格。
5. 实际产品CSRF和持久Session生效；运行期表变化只在background_job、operation_event、spring_session及spring_session_attributes。RECOVERY_PENDING六HTTP均JSON503/RECOVERY_DATABASE_ADMISSION_REFUSED，拒绝区间179表/文件守恒，最终源快照及65控制原件保持。

## 失败、自审与保存

首轮失败不是产品拒绝已登记恢复：夹具尚未validateStartup就调用checkEffect，产品按合同拒绝。修复仅让离线供应/收尾执行RecoveryLeaseRoots根/marker校验及index HMAC/空租约核验；实际产品JVM仍执行初始化和运行时权威检查，不放宽产品。失败实例1707未复用。

运行器snapshot首轮PowerShell变量冒号解析失败、次轮ADB子进程定位失败、一次apply_patch未匹配均见 `runner-failures.md`；没有业务启动的失败不计业务测试。首版自审空比较见上表，修正检查器后得非空输入核验；这些失败原件均保留。

收口按testing-best-practices/security-practices复核：新IT使用独立实例，真实恢复/HTTP/产品进程/数据库，不以Mock替代；P5启用仅test代码，实际产品拒绝链不变。明确路径敏感扫描及逐项定性见本机 `closeout-review/`；只提交源码测试/消费者/文档，合成秘密、cookie、DER、数据库、APK、本机配置及输出不入Git。

收口复核13条明确路径、2695输入、110依赖、308原输出及两份proof保持。敏感扫描两处命中分别为从环境读取的测试DB口令字段、loopback动态测试URL，无硬编码真实密钥/密码、真实用户数据或生产连接。首次本机收口检查器缺括号，解析失败原脚本/SHA/结果保存在 `closeout-review-parser01/`，未启动业务；修正后复核成功。文档引用与diff空白检查通过。

## 停机、清理与交接

1707、1710均认证数据库身份、保存最终dump及shutdown exit0，相关监听消失。仅清本片两座已停库的data目录，逻辑508,937,640字节；D余量21,147,090,944→21,672,767,488，观察净增525,676,544字节（约0.49GiB）。命令/绝对路径/哈希/无消费者/前后余量见 `cleanup/`；保留dump/binlog、原HTTP/签名控制原件、下载文件、冻结源码及失败日志。1708/1709预先存在，不属本片，未动。

fresh环境、ADB枚举、Keystore SHA及C/D/E余量在 `closeout-resume01/environment.json`，后续时点见最终closeout。系统3306/33060内部身份未认证，只观察，未连接/停止。本助手仅枚举ADB，不操作设备/构建/签名APK；另一聊天正在Android选择器及设备验证，其结果不计本片，亦不从历史设备状态推定当时状态。

下一接手先读[AGENTS](../../AGENTS.md)、[Git工作流](../GIT_WORKFLOW.md)、[看板](../ORCHESTRATION_STATUS.md)，再读本报告及本机closeout，fresh核全部dirty/远端/写者/进程监听后再写。[前看板叙述](ORCHESTRATION_STATUS_BEFORE_RESTORED_CLOSED_P5_2026-10-23.md)只作历史，完整原字节/SHA另存，不恢复旧指令。

## 未完成与下一步

已有不可变binding再次恢复仍缺：须保留旧binding/旧NORMAL，明确RELEASE_READY后的合法后继轮次；Java/Python现有一次四阶段协议不足，不能靠放宽UUID、改不可变表或删触发器解决。下一有界任务先只读核对后继轮次协议与最小设计/退出标准，再决定新隔离验证输入。

D13整体、其余worker/文件入口、OS拒绝自halt、D16整体、D18实际最终候选及第8节仍未满足；合成夹具不能代替可信业务恢复或原身份。既有测试部署/Android证据按原报告独立继承，本片未做完整普通回归、Android、模拟器、真机或部署。

总目标active、未完成。生产开关/TEMP归档、正式签名、原库/现网、main、生产部署与Release关闭，localDeletionAllowed=false；取消链、多跳、接收者确认/激活和旧run03不恢复。清理、文档与开发备份不计验收进展。
