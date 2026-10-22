# D13完整恢复后P5启用与独立API/worker stale拒绝组合（2026-10-22）

## 输入与范围

输入提交ace1941a2a02941ca9dc4f549974885e4eafa180，固定树D:/acrylic-pda-outbound。8个代码路径仅涉及测试、独立测试JVM、封闭runner选项及独立Python原件消费者；产品源码、生产配置、契约、迁移、Android/Web无改动。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。

证据根：.local/goal-20261022-resume/d13-restored-p5-stale01/；实际库根：.local/e2a-cross-20260925-1648/。baseline.json/inherited-wip.zip保全40条继承路径；edited-originals.zip保全四已有测试改前字节，edited-inputs01.zip和execution01-source.json/zip绑定执行输入；acceptance-design.md先定退出标准。不重跑[前片非P5 stale组合](GOAL_D13_RESTORED_STALE_COMBINATION_2026-10-22.md)及旧失败运行。

## 设计与实际执行

既有P5正向独立进程与非P5恢复stale分别通过不能证明组合。本片复用生产准入/租约/跨进程effect gate和生产协调锁/排他writer，以run内合成Java控制夹具供应测试身份；独立IT JVM推进控制记录。Python v1生产控制协议不改为支持测试v2身份。Python在本片是独立原件消费者，**不是生产控制供应者**；独立工具消费者也不等于独立人员审查。

非空历史迁移→真实HTTP建立合成TEMP原双格式PENDING→完整SQL/storage/合成验签资料恢复→独立API/非Web生产定时worker启用P5及cross-process effect gate→两JVM分别实测活租约→正常完成双格式并HTTP下载/回读→排空本片scheduler但保留JVM→HTTP第三PENDING→持锁控制推进→六HTTP与显式生产worker拒绝→业务及P5全树守恒→正常关JVM/认证停库→独立Python消费原件。

| 执行 | 隔离输入 | 实际结果 | 适用结论 |
| --- | --- | --- | --- |
| execution01/run1648 | DB23648、HTTP24648、build .local/s22j | IT 1测试/0失败/0错误/0跳过，66.885秒，exit0、completed=true | 本有界合成P5恢复stale组合通过 |
| compile | 同次源码与外部依赖 | testClasses/recoveryControlClasspath/report-rendering installDist，exit0 | 仅编译，不替代上述IT |
| consumer01 | 同run原HTTP/控制链/P5/SQL/文件 | Python exit0、stderr空 | 独立原件读取通过，不是独立生产权威 |

精确命令/参数/退出码见execution01-launch.json、execution01/compile-command.json、execution01/integration-command.json、consumer01-command.json、原stdout/stderr及XML。首次实际IT即通过，未为统计重复运行；原件不被覆盖。

## 原件、角色与守恒

- 恢复原快照179表；正常任务消费仅background_job、operation_event、spring_session、spring_session_attributes变化。stale前后完整快照逐字相同，含179表、DDL、迁移、15文件；源全快照与原8文件保持。
- 双原任务正常完成；原键/异文/跨仓拒绝及真实下载绑定。XLSX7表/2明细/0公式、原顺序/整数克，SHA-256：10c026fd4ec0939fc717ab3d559f1c4b7b558a3895c18dd893557bf60bf1b000。
- PDF2页/2明细/0注解/原顺序，SHA-256：cb97b2b6a2d39147032dea073826dd7a01411f4c755fd3a983af2f6954cfb30a。两页实际Poppler渲染并目视检查，无裁切/重叠/缺字；明确测试样例/非业务凭据，原/有效2包601.5kg。渲染命令与两图SHA见pdf-visual01/render.json及review.json。
- HTTP三阶段7/16/11条；最后六条准确503 CONTROL_INSTANCE_STALE。第三任务保持PENDING/attempt0/无artifact/lease；显式生产poll、enqueueReviewReminderSweep两处拒绝日志及直接admission准确码齐全，不以停scheduler替代拒绝证明。
- API/worker运行原件PID16100/6856，各自实际生产operation活租约、对应process record/index及HMAC由Python核验；不是只读enabled选项。控制推进IT运行PID12712不同于两者；以上仅运行历史，不推定仍活跃。
- 控制revision2由IT JVM持生产协调锁和排他writer推进；ordinaryWritesAllowed=false。控制链逐项hash、实际datadir绑定、P5 anchor/index/process/lease HMAC原件在本run private/p5-stale-authority/。stale前后P5全树字节/SHA相同，最终租约0、两process record保留，两JVM正常关闭。
- 独立消费者输出SHA-256：ca0344056bd2c219f87a56cae601249a52b13b02f97d353e942b10009c9c1ad8；最终dump SHA-256：3654e77315b911d3075317470042167577efecbef259ae03abed7378f8aca8a3。
- 离线mysqlbinlog exit0，source/restore Table_map为374/408；恢复切点后source Table_map及全限定源行引用均0。input SHA-256：258e622f1bcd7f810154822e2c1c5f25fe1f4abf3cfc4492a27c470a62cbbda0；decoded SHA-256：cefe02965ca6ad815e8a4745382f015b6f34d65ed671a62b18229478b8cbf5d9。见binlog-consumer01.json。

源码2633文件、外部输入275文件执行前后逐SHA保持，见execution01-source.json、external-after.json、source-review01.json；收口再核于closeout。作者自审与独立Python消费者的范围分别记录；没有新增独立人员审查。

## 环境、问题与空间

本片隔离库认证身份、datadir、端口、UUID保存在本run原件；MySQL认证shutdown exit0、API/worker正常关闭，专属端口/进程以closeout fresh实查为准。系统3306/33060只观察，未SQL连接，内部身份未认证。ADB仅只读枚举；Debug Keystore仅只读SHA，不用正式签名。

离线解析初次把含二进制行值的mysqlbinlog输出按UTF-8读取失败，随后在相同原字节中查ASCII数据库token；未重跑解码命令或业务。另一次工具运输JS含模板反引号导致解析前失败、无命令执行；这两项均不改业务IT的首次通过事实。删除策略此前已拦截，本片未换通道重试。

仅对已结束自有.local/s22j及run1648/data无损NTFS压缩；4105文件长度/SHA全保持，减少分配225301461字节，删除0字节。D卷观测36180123648→36415004672字节；卷净变234881024字节与分配减少分列，不累计前片。命令、绝对路径、输入SHA、依赖锁、停机/进程/监听及结果在compact-plan.json、compact-result.json、dependency-inputs.json等原件中。原资料、签名/身份、唯一证据、继承源码及最终APK均保持。

## 未验、Git与接续

仅本次合成P5启用恢复后stale组合通过；**不计独立Python生产控制权威/生产ops协议通过**，不代表D13–D15整体、P5 UNKNOWN/crash/OS拒绝kill、错根/错库/水位全部入口、Android/Chrome/模拟器/现场或D18最终候选。D16正常有效guard/复杂引用和总目标第8节仍未完成。

仅本片8测试/runner/消费者、此报告、片前看板叙述归档及两个索引共12路径作开发备份；看板只本地维护。40条继承WIP保持，history仅暂存本片精确入口，不提交其继承内容。提交/推送/文档/压缩不计验收；实际Git与远端回执、最终dirty全清单/SHA/ZIP及manifest见本片closeout/。证据含本片根及整个run1648根，本机被忽略原件不虚称已异地备份。

总目标active、未完成；生产开关/TEMP归档、正式签名、原库现网、main、部署/Release关闭，localDeletionAllowed=false。换机承接/多跳/接收者确认激活/旧run03不恢复。下一步从上述未验矩阵选有界独立项，先定义影响和退出标准；不重跑1648及既有一次性运行。
