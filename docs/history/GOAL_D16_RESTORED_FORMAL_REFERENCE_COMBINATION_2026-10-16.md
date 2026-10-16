# D16 恢复后正式事实与复杂引用组合开发备份（2026-10-16）

本切片在同一新合成输入中组合已恢复的正常FORMAL原批次、有效防重、两代原签审计和独立待删目标，12项实际消费者验证通过。正常正式订单及guard全部保留；被删除的仍是明确标识的无目标guard合成订单。不计D16整体、E07 L2、D18 L3或总目标完成。

## 输入与必跑映射

- 固定树`D:/acrylic-pda-outbound`、分支`codex/development`，输入HEAD/实际远端均为`a39098866e0b8b7bd7aff4fcca0767446f084dd9`。本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`唯一写者；清理聊天idle且complete-released，未创建子代理。
- 证据根`.local/goal-20261016/d16-restored-formal01/`。input01–05及同号附件保存全部tracked/untracked路径、长度/SHA、binary diff、未跟踪原件、worktree、旧看板、写权、进程/监听及C/D余量。87项继承改动保持，不混入提交。
- 运行输入input05共5375项，SHA`a24a53f2020786e94a850e9c7c5983059d76e7b9b4d0ab59644e6405496ff375`。与input03相比仅两个本切片测试路径变化，5373项原字节不变；最终测试代码与input05一致。
- 正常正式事实来自[665正式恢复报告](GOAL_E07_FORMAL_RESTORE_NATIVE_RETENTION_2026-10-16.md)已停止的`retention02/synthetic-source.sqlite`，长度1064960，SHA`d39bdce4bf3b5b8bdd4936375ac9bc836600007532083a1b08ff96dd08cbef4b`。固定原ID复制11张非全局表，含1提交/1归档、2有效guard及原capture/scan/event/订单/设备序号；不改变原源库。
- 665两阶段Android/真实HTTP/MySQL、归档原签和恢复证据沿该报告路径+SHA复用；上传是测试HTTP适配器，未执行PdaSession/SubmissionRepository，本批不新增这两条产品入口覆盖。
- 两代审计来自[无关审计报告](GOAL_D16_UNRELATED_AUDIT_L1_2026-10-16.md)固定停止输入，源SHA/公钥见`tests04-command.json`及既有`d16-audit-reference01/input02/input.json`。组合使用三个来源，不声称原PDA曾存在同一现场库，不改造原身份/签名。

| 改动路径 → 影响层 | 必跑 | 复用依据 |
| --- | --- | --- |
| `tools/pda_retention/tests/restored_formal_case.py` → 正常恢复事实装配 | SQLite FK/完整性、原行/原生trigger、源SHA、装配证据 | 665报告与`restored-input01.json`固定源/清单SHA；消费者、Android、迁移及产品结构不变 |
| `tools/pda_retention/tests/test_restored_formal.py` → Python CLI/事务/引用组合 | 正向删除独立目标及全表恢复、default-off有/无permit、无permit、异文、同键并发、实际锁后撤权、末DELETE回滚、guard/FK/外部引用反例 | tests02内既有101项均通过；前报告消费者/原生回归按路径+SHA复用，新组合12项实际重跑 |
| 报告/索引/看板 → 断点 | 链接、输入/未验界线、生成事实块 | 无Android/Web产品改动，无新Chrome/APK/MySQL或迁移结论，不豁免L2/L3 |

## 实际运行与独立消费

`run_tests.py 04`以真实子进程执行`python -m unittest -v tools.pda_retention.tests.test_restored_formal`，`tests04.log`为12项、0失败/错误/跳过，13.558秒；外层exit0、13.903秒。TEMP/TMP仅该子进程指向本批D盘tmp。

1. prepare→独立permit→实际CLI consume→重放，在正常正式订单/guard、原签审计非空及47项原生trigger同时存在时成功；删除仅影响独立合成目标选定行。原源字节不变，backup恢复到新文件后全表与原事实一致。
2. 正常正式订单11表全部原行、2个guard及审计/检查点保留。另有审计输入的guard，不能将合并库总guard数误认为665原guard数。
3. default-off无论permit真假均拒绝且候选字节不变；显式enable但无permit拒绝。候选异文拒绝。
4. 同计划双CLI仅一次COMMITTED、另一次REPLAY。持有真实SQLite写锁，观测消费者BEGIN且等待后撤销permit，再释放锁，消费者拒绝且全表不变，`lock-wait.json`保存。
5. authorizer拒绝最后prepared_order DELETE，前序至少9条DELETE全部回滚，全表保持；`denied-delete-trace.txt`保存。此为SQLite注入，不冒充HTTP/MySQL回滚。
6. 正常guard周期错、guard引用目标或保留订单JSON引用目标均拒绝；prepare不产生plan/permit，source/candidate一致。DELETE被引用capture由原生FK直接拒绝且全表不变，不关闭FK制造输入。

独立只读`audit_evidence.py 02 tests01 tests02 tests03 tests04`核250case、28次实际合成提交、2130证据文件；每次提交核backup/source完整前事实、candidate精确受控差异和存在的恢复副本。`evidence-audit02.json` SHA`1d77a9abd7f4b2eda44b7142f28d35945125a949eb3aab5f44957d143ba13187`。

`review_current.py`再次逐文件长度/SHA核2130原件，独立核新组合成功路径16个backup/candidate/restored库中的全部正常正式原行及47项原trigger，原source SHA不变。665原591文件共260259262 bytes清单与前报告相同，清单SHA`2a8572301f77c93521065a268dc298c35b96f462207445f70f4fb1a1bcdadc2a`。`review-result01.json`通过；新Python文件≤200行/函数体≤30行，`git diff --check`通过。作者、自审均当前单助手，独立工具消费不等于独立人员审查。

## 失败保全与纠正

- tests01：113项中12个新用例setUp外键错，archive先于submission，该组未进入消费者；其他101项实际执行，原件保留。
- tests02：113项中相同12个setUp外键错，反转列表使guard先于scan。读取实际PRAGMA FK图后明确父表顺序；同根因已两次失败，不清零。连接自动提交，先写一条正常正式work_device后才失败，12份源库与旧spec SHA不一致；没有retention run、计划或消费者写入。
- 独立审计首次严格SHA断言因此失败，`failure-audit01.json`及原脚本保存。后续只读逐表证明这12份失败库恰为tests01固定基线加一条原work_device，无其他增删；标`FAILED_SETUP_ONLY`，不计通过、不改旧spec。其余case仍严格校验SHA。
- tests03：明确父表顺序后11/12通过；唯一错误为负向装配DELETE被引用capture，FK在消费者前拒绝。改断言为原生FK阻断及全表不变，再执行tests04全12项。前轮3次合成业务提交及失败原件保存，不追认整轮通过。
- 原失败脚本、日志、库、回执/签名与指纹全部保留；未机械重跑旧1522/1523或Chrome审批阻断。

## 环境与后续

仅Python/SQLite顺序执行，无新MySQL实例或监听。665认证停机和项目端口零监听沿实际process/listener快照复核；共享mysqld仅观察，不连接/复用/停止，原TEMP AVD/ADB保留。input05磁盘采样C/D余量83010412544/22919581696 bytes；新证据/临时均D盘，无清理，重型动作前复测。

正常有效guard目标未获得删除资格。D16正常目标合法退出、适用续作/更正/独立来源及D13–D15完整组合、E06/E07/E08剩余L2、O/U完整组合、E09实际候选和D18 L3继续。七类生产能力、TEMP生产归档和`localDeletionAllowed=false`保持关闭；不接原库/现网、不合main、不发布部署，无正式签名。总目标active。
