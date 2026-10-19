# 重启后继续开发指令（2026-10-19）

请在 `D:/acrylic-pda-outbound` 接续无人值守开发总目标，继续完成 `docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md` 的 D01–D18、第8节全部闸门、O01–O05、U01–U04 与 UI 重设计。只有第8节全部闸门在实际最终候选输入上满足才可标记 complete。旧暂停不覆盖本次恢复。

先读 `AGENTS.md`、`docs/GIT_WORKFLOW.md`、看板当前区和生成事实块、路线/进度/剩余计划、总目标第4/5/7/8节、相关业务政策与验收报告，以及 `docs/history/GOAL_E08_D17_UI_CLOSEOUT_HANDOFF_2026-10-17.md` 和本文件。

重启后接管前先只读核实：分支/HEAD/origin/远端 SHA、所有 tracked/untracked 改动及 SHA、活动写者、重型任务和进程、监听与数据库身份、ADB/模拟器/Keystore，以及 C/D/E 余量。旧机器上的数据库、模拟器和监听在重启后状态未知，不能按重启前事实推定已停机。确认唯一写者后登记新会话，并运行 `powershell -NoProfile -File tools/version_facts.ps1 -Write`、`powershell -NoProfile -File tools/version_facts.ps1 -Check`。无法确认唯一写者时仅只读保全。

当前断点：`codex/development`，HEAD/origin/远端为 `622d38bdb018af8f4fdb3540a2e718476a08217d`；本次复核为64个tracked修改、152个untracked文件，共216项，完整路径与SHA-256见 `.local/goal-20261019-resume/pre-restart-closeout01/`。较早的继承快照仍见 `.local/goal-20261019-resume/fault-final-consumer-run01/final/` 和 `takeover/`。重启后必须重算准确状态和哈希；不要清理、还原、重排或覆盖任何继承WIP。

重启前只读环境采样保存在 `pre-restart-closeout01/`：当时监听包括ADB 5037、MySQL 3306/13316/13317/33318及Vite 5173；未发现Gradle/Java/模拟器进程。PID/进程名、worktree、完整status、tracked patch、216项文件哈希、当前会话写权及C/D/E余量均在快照中。注意：其中 `adb-devices.txt` 是快照脚本因 PATH 未找到 `adb` 的错误，不能当作设备枚举；随后以 `C:\Users\qq113\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l` 做了只读核实，结果为 `22D2910042 device`，仅作为设备连接事实，不构成业务验收。USB M71 `22D2910042`本轮未操作。重启后不要按旧PID或监听推定状态，也不要连接/停止服务来复现旧状态。

用户已确认按 fail-closed 修复实施。现行范围遵守 D06/D07 撤回坏机未完成订单→重新发布→新设备从头作业；不恢复换机进度承接、多跳、接收者确认/激活链。D13/D14仅为灾后原件、截点、恢复写入和只读核对。七类生产决定、TEMP生产归档、正式签名、部署/Release、原库/现网保持关闭，`localDeletionAllowed=false`，不得降低D16标准。

最先继续 U04 run02 的只读证据审计：对 `.local/goal-20261019-resume/u04-retained-details-run02/private/` 中四档字号的 before/after tar 做逐成员比较，定位 Room DB/WAL、应用资料和运行元数据的变化，记录路径、命令、输入 SHA、逐项结果及输出哈希。四次 Android instrumentation 均记录 `OK (1 test)`，但 `archiveUnchanged=false` 且每组 tar 哈希不同，因此不能声称 Room 未变或据此判定 L1 通过；不得重跑旧用例、覆盖或删除 APK/tar/日志。之后推进下一个独立有效子项，不等待暂缓项。

继承恢复确认 WIP 的逐路径范围和 caller 事实见 `docs/history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-19_TAKEOVER12.md` 与 `.local/goal-20261019-resume/fault-final-consumer-run01/cancelled-wip-review.json`。普通 TEMP 开发链需与已取消的 recovered-confirmation 链区分；不得新增取消链 caller、启动旧 run03、bridge 或设备测试，也不得把合成 fixture/身份/回执当现场事实。

每个切片先记录改动路径→影响层→必跑清单→路径+SHA复用依据。L1必须实际跑受影响产品入口；D步骤的L2覆盖受影响Android、真实HTTP/MySQL、Chrome、Python消费和非空迁移；D18最终候选必须执行Android、真实HTTP/MySQL、Chrome、Python、非空迁移、原模拟器和恢复全链L3。适用时覆盖权限、事务、幂等、并发、回滚、末条SQL回滚、同键并发、异文拒绝、锁等待撤权及default-off有/无权。每个阶段/可控中断前更新看板当前区、证据索引、Git/工作树哈希、写权、进程/监听/数据库、磁盘、验证/失败/未验和下一步，并沿用用户授权的D盘清理规则。

禁止 `git add .`、合并 main、发布/部署/Release、使用正式签名、连接原库/现网、绕过设备/审批/Chrome策略阻断，或删除唯一证据/原资料/身份/回执/签名/最终APK/继承源码改动。连续同根因3次无新证据或诊断60分钟未收敛时保全并暂缓该项及确实依赖项，推进独立工作，不清零失败计数。
