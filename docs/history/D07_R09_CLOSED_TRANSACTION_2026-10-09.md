# D07/R09 关闭恢复权威与事务前置验收

本批沿[可信来源读取层](D07_R09_TRUSTED_SOURCE_2026-10-09.md)补关闭恢复事务前置；不是跨截点业务恢复或D07/D13/D14整体通过。版本事实只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。总目标active、未完成，生产默认、签名及七类生产决定能力关闭，localDeletionAllowed=false。

## 输入、写权和范围

树D:/acrylic-pda-outbound，codex/development，输入HEAD/实际远端e64526e163e304c48480642f96b0b34b8ba21def；本聊天01a11ea9-ce8d-70d1-8182-ccf4ca0a945a为唯一活动写者，其他项目聊天notLoaded。规则、看板、FORMAL与磁盘final已核；不重做1150/1151/UI-06，不重跑1100–1102，不复用失败库，不接原库/现网/main/发布部署或正式签名。

设计`.local/d01-d18-20261009/closed-authority-design.md`固定如下退出标准：独立kind/pin/原来源与控制截点；独占租约及耐久事务开始/完成；实际实例/关闭投影、提交/回滚/末SQL/并发/未知提交重开拒绝；非空旧业务行和文件不变；实际HTTP拒绝且无新签名。合成来源和probe不能代表原占用、权限或恢复账务。

## 实现及作者自审

- RecoveryClosedAuthority要求独立CLOSED_RECOVERY_SESSION文件pin和source manifest pin，根位于控制/全部restore roots/checkpoint之外，逐次核installation/session/epoch/head/source；closed-sessions登记只是一致性见证，不提供独立授权。
- RecoveryClosedProjection改为显式接收上述权威；不再借普通NORMAL身份执行关闭投影。RecoveryClosedTransaction仅在RECOVERING/VERIFYING且原source/control完全匹配时执行离线回调，DML前及提交前核真实实例、安装、逐版投影和RECOVERY_PENDING。
- RecoveryWriterLease的closed路径取独占kernel lease。RecoveryLeasedTransaction先发布耐久开始、提交前重验控制，只有已确认提交/回滚才发布完成；未知结果或完成发布失败毒化租约并固定会话，重开仍拒绝，不能自动重试未知DML。
- RecoveryClosedJournal/Disk/Witness用追加digest、独立固定文件身份/头摘要及耐久发布核事务前缀、丢尾、替换和部分写入。witness属于受保护且不可随恢复物料回退的独立权威输入；本批未证明攻击者原地同时回写全部独立权威材料的防篡改能力，也未证明真实断电或异地介质。
- Python control_history严格识别Java生成的closed-sessions布局，限制数量、文件名、canonical格式、字段、非零pin及绝对根，保持已有重定向/single-link防护。未知或额外权限字段保持拒绝，不能升级为NORMAL。

作者自审覆盖上述生产源码、共享租约改动、测试/fixture与协议互通；记录在`.local/d01-d18-20261009/closed-self-review-01.md`。无Spring bean/新HTTP写路由/私钥/生产配置/迁移/契约/Android/Web改动；probe及DML回调只作为下一步事实重建的受控机制，不是事实证明或安全放行。

## 当批验证与失败保全

计数为tests/failures/errors/skipped；普通test不含IT。

| 运行 | 结果与实际边界 |
| --- | --- |
| closed authority定向05 | 49/0/0/0、exit0/sourceUnchanged；本次核固定输入并复用，不重跑。01–04及03/04失败原件保留 |
| 1152 E2aClosedRecoveryAuthorityIT | 1/0/0/0、exit0/sourceUnchanged；真实MySQL投影/commit/rollback/尾SQL/rollback-only/并发/错实例与schema/投影拒绝，实际commit后合成丢应答并重开拒绝；非空旧业务行/文件不变；实际HTTP 401 UNAUTHENTICATED、RECOVERY_PENDING，公私钥为空 |
| 1153 E2aClosedRecoveryProjectionIT | 1/1/0/0、exit1/sourceUnchanged；Python控制推进在Java closed-sessions目录上返回CONTROL_UNKNOWN_ARTIFACT，尚未完成四阶段。原先非空迁移猜测已撤回，原因以原Python输出为准 |
| closed-python-01 | unittest test_control*.py实际46项通过、exit0/sourceUnchanged；含六项登记布局/权限字段/硬链接/控制推进反例 |
| 1154 E2aClosedRecoveryProjectionIT | 1/1/0/0、exit1/sourceUnchanged；Python已推进，旧权威进入RECOVERING因epoch变更返回CONTROL_CLOSED_SESSION_MISMATCH，断言误期待HEAD_MISMATCH；只修精确预期 |
| 1155 E2aClosedRecoveryProjectionIT | 1/0/0/0、exit0/sourceUnchanged；新库四个关闭阶段/原键重放/并发登记拒绝/错实例及复制schema/初始回滚/溢出/缺版和整体权威替换；非空旧业务行/文件守恒，无NORMAL登记 |
| r09-closed-server-regression-01 | 3615/0/0/1、220份原XML、exit0/sourceUnchanged；唯一skip为SourceEvidenceIntakeFilesTest Windows符号链接条件反例，未执行不算通过 |

1152和1155是不同的一次性隔离输入，不能拼成业务重建完整链。1155没有实际HTTP，HTTP关闭态证据仅为1152；无当批客户端、页面、APK/模拟器/真机、签名、恢复到截点前或接收最终提交验证。

原Gradle命令、源码before/after hash、log/XML/result在`.local/d01-d18-20261008/r09-closed-mysql-1152/`至`1155/`及`r09-closed-server-regression-01/`；Python证据在`.local/d01-d18-20261009/closed-python-01/`。原SQL/控制/probe/HTTP/非空表和文件快照在`.local/e2a-cross-20260925-1152/`至`1155/`。环境分配/源码/认证停机在`.local/d01-d18-20261009/closed-mysql-1152/`至`1155/`，均跨33树核编号后新建；失败库永不复用。

1152的六份及1153/1154/1155各十份dump另行逐SHA256复算一致；四次shutdownExit=0、原PID退出、19330/19434无监听。失败datadir/dump/binlog、原log/XML/HTTP及所有原件保留；只有本人作者验证和独立工具核验，没有独立人员审查，不称已异地备份。

## 断点与下一动作

回归完成后52条全量dirty副本/hash/diff/Git/进程/监听/C-D余量在`.local/d01-d18-20261009/closed-regression-complete-11/`，22条继承逐hash一致。该快照C/D余量81852727296/99456688128字节；无重型Gradle、模拟器或页面运行器在途，既有3306/13316/ADB未操作。看板替换前原文在该快照board.md；提交前后及Git回执另按明确范围保存，排除全部22条继承路径、本机产物与私有材料。

下一步补原occupancy/permission语义全集及更高轮原激活、TEMP preparation/ACK/基线证明；读取层现仅固定其字节库存。然后全新跨树隔离输入实际恢复截点前→原ID/正文/序号/回执重建→D07围栏→接收激活/最终提交，补回滚/原键重放/重启。D06/D07整体、FORMAL产品入口覆盖、D08–D18及第8节未完成；政策、原资料/旧BCD/526、历史失败和C01–C08继续单列，不外推本前置通过。

32条明确路径已提交并正常推送cdca9f14445c047af3653b25f8b35c92b0f4f1a8，实际远端一致；command/staged-reviewed.diff/commit.log/push.log在closed-precommit-12。提交后23条dirty全量副本及22条继承保护见closed-postcommit-13；额外RecoveryControlPaths.java仅换行差异未暂存，保留原字节，不重置。随后只补Git断点文档，不改已验代码输入。
