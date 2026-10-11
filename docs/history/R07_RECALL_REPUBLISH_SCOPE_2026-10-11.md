# R07 取消换机接续，改为故障订单撤回重发（2026-10-11）

## 用户决定与范围

用户明确要求：“换机这个不做了，用新的方案，由于机器坏了没关系，就算是坏机截单了，后台有权利撤回他接的订单，然后重新发布。然后你看看原来的十步开发步骤更新一下。”其中已领取未完成订单由后台有权人员撤回，新机器按新设备登记并从头作业。

原机可用交接、故障换机进度承接、多跳交接、旧接收者准备/确认/激活/最终承接链不再属于必做开发或验收。旧成果不删除，取消不记为功能实现或验收通过。原机原人R06续作、更正/对账、R08、R09灾后恢复及其他路线范围保留；新方案不依赖从坏机恢复未完成进度。

新政策见[BUSINESS_POLICY_R07.md](../BUSINESS_POLICY_R07.md)，完整范围见[十步路线](../DEVELOPMENT_ROADMAP.md)。D编号保留，D06改为有权撤回坏机领取的未完成订单，D07改为重新发布及新设备独立作业，关联D10/D11/D14和总目标闸门同步。新方案仍需权限、终态、旧资格失效、占用/账务、幂等/并发/丢响应/重启/回滚及实际产品入口验收。

对象限定为后台已发布、已领取且未成功出库的订单；不要求坏机在线或提供STOP/ACK。服务端撤回与提交竞争须核明终态，只能有一个有效结果；已成功出库不撤回重发。旧资格和迟到上传不能影响重发后作业，原订单/领取/已存扫描/回执与审计保留。没有后台发布记录的本机TEMP草稿仍走R06，生产放行、删除和未知事实恢复边界不因此扩展。

## 单写者交接与旧测试保全

本项目聊天“/goal 恢复并继续完整执行 D:/acrylic-pda-outbound/docs/AUTONOMOUS_DEV…”原持有源码、看板及计划/政策写权。用户明确授权本聊天通知其保全停止旧换机工作并交出写权；接收聊天为“最新的APK文件在哪里？”，ID `01a12a33-713f-78e3-89c3-4c2c7fa61ffc`。

原写者已返回goal为paused、不自动接续，并在最终答复交出全部项目写权。交接本机原件：`.local/d01-d18-20261010/received-scope-change-safe-stop-01/HANDOFF.md`、`received-scope-change-pause-final-01/handoff.json`及同目录快照。修改共享文档前已核对原写者idle、交接生效、实际HEAD/远端、全部dirty、任务进程/监听及C/D余量；未在原写者仍运行时写共享计划/政策。

- 1342：原写者记录服务前置及页面单阶段通过；运行器收集失败，第二页面进程与新服务JVM未执行，不计组合通过。
- 1343：服务前置通过；页面运行因用户取消范围中断，无结束/OK、第二页面进程和新服务JVM成功证据，不计组合通过或故障根因已证。
- 原写者完成两份dump、认证停机、运行器/服务/MySQL/模拟器退出、任务监听为空，原日志、tar、datadir、binlog、APK、AVD和失败材料保全不复用。停机结果与哈希以本机HANDOFF及retention.json为准。
- 交接时全部46条未提交路径、原29条非看板继承路径、69项历史保护、22项原STOP均保留；原1233只读，1293 UNKNOWN围栏未改。本次仅修改下列文档，不改源码或数据库，不清理产物。

## 文档改动与历史保存

本批输入树`D:/acrylic-pda-outbound`，分支`codex/development`，输入HEAD及开发远端`775f9e8a34dbe44ea7e57522e122965b62c5b6ec`；main远端`c6e9e0c238c657baecbf699ba6fdbada5d979b86`仅作只读核对。文档批次提交、推送及最终工作区以看板和`.local/r07-recall-republish-20261011/`回执为准，不把输入SHA写成之后的HEAD。

本批明确路径：

- `AGENTS.md`、`docs/GIT_WORKFLOW.md`：用户明确范围变更优先、旧接续不恢复取消范围、取消不计通过的长期规则。
- `docs/DEVELOPMENT_ROADMAP.md`、`docs/REMAINING_DEVELOPMENT_PLAN.md`、`docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md`：十步范围、剩余清单、D06/D07及依赖/完成闸门和可复制接续指令。
- `docs/BUSINESS_POLICY_R07.md`、`docs/OPEN_QUESTIONS.md`：撤回重发的新业务决定及保留的更正/附件政策。
- `README.md`、`docs/WEB_DEVICE_GUIDE.md`：方案入口及设备停用与订单撤回的区别，不暗示既有停用功能已实现新命令。
- `docs/ORCHESTRATION_STATUS.md`、`docs/DEVELOPMENT_PROGRESS.md`、`docs/history/README.md`：断点和范围决定索引，旧验收记录只作原范围历史。
- 本记录，以及[修订前看板](ORCHESTRATION_STATE_before_recall_republish_2026-10-11.md)、[修订前R07政策](R07_POLICY_before_recall_republish_2026-10-11.md)、[修订前总目标](AUTONOMOUS_PLAN_before_recall_republish_2026-10-11.md)。

所有文档修订前副本、原工作区diff/dirty哈希、原staged状态、预览和本次结果保存于`.local/r07-recall-republish-20261011/`。归档文件保留修订前原字节；历史中的旧“继续换机”指令不恢复生效。旧业务源码和未提交开发成果继续保留，不因取消范围删除。

## 验证与限制

本批只做政策/路线/接续文档更新；验证文档差异、相对引用、范围一致性、原继承文件保护、事实块生成与看板上限、明确暂存范围。实际检查结果保存为本机validation.json及相关回执；没有运行业务测试、构建APK、设备联调、真实HTTP或新方案数据库命令。

新方案实现和验收未开始，D06/D07与总目标不计完成；其余开发缺口、原资料/旧BCD/历史失败及现场清单继续保留。原开发goal暂停，后续按修订后的计划重新接续，不自动唤醒旧换机任务。不合main、不发布部署/标签/Release、不接原库/现网、不正式签名；生产默认与localDeletionAllowed=false保持。
