# R06 TEMP共同记账内部子项验收

日期：2026-09-25。当前会话 `01a0d612-1e51-78c2-9eca-f15671339987`，作者及自审为当前助手；无独立人员复审。持续目标 active。本记录只收口共同记账原语的 TEMP 原资格政策适配，不代表七类生产决定、TEMP确认业务或上线完成。

## Git、范围与完成标准

- 主树 `D:/acrylic-pda-outbound`：`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。
- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`：`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。
- 全部未提交/未跟踪文件见 `R06_TEMPORARY_ACCOUNTING_WORKTREE_STATUS_2026-09-25.md`。本次没有提交、推送、合并、标签、部署、APK构建或安装；最新服务端代码尚未整合主树。其他既有修改及 out.xlsx 保留。
- 完成标准：已有原正文、首轮原映射PDA_TEMP待审批次使用原资格的固定政策；共同库存/出库/结果/成功签名保持同事务；重复提交/迟到取消不重记；异常全部回滚；正式普通及续作路径回归。生产决定handler、API、权限不因内部测试开放。

## 实际代码

服务端树实际新增/修改共6文件：

1. `submissions/SubmissionAccounting.java`：要求现有事务，拒绝无目标/空有效明细；复核current；使用来源政策解析；订单结果与槽位更新加条件检查；TEMP不关闭空assignment；派发时核对原order+S+cycle+REVIEW_RESERVED。
2. `submissions/SubmissionAccountingPolicy.java`：TEMP由原T/authorization绑定policy_version，交叉核原S/人/机/仓/原来源/当前映射1/round1/待审状态及唯一PDA_TEMP目标、原正文摘要/政策版本/扣账标志；不使用今日最新政策、不创建assignment。FORMAL仍使用原publication。关联后的TEMP、更高轮次和其他状态失败封闭，须后续专门接入。
3. `inventory/TemporaryAccountingChecks.java`（测试）：实际TEMP正文HTTP受理→测试内部事务调用共同记账→HTTP原回执读取/重复正文/迟到ABORT；原政策之后发布相反新政策仍按旧政策；验签、唯一结果与全局包占用；正式普通两政策和正式续作回归。真实应用上下文断言 `CommandOperation` bean集合为空。
4. `inventory/TemporaryAccountingFailureChecks.java`（测试）：具体错误码负例；无外层事务拒绝；正文/政策/仓/映射/current/状态/空明细/槽/包目标/cycle/库存冲突；缺签名密钥与真实末尾审计FK失败。每例事务回滚并完整比对提交、订单、槽、库存/包、条码同步及outbox原状态。
5. `e2averify/E2aTemporaryAccountingIT.java`（测试）：受保护独立非空MySQL V34 seed→V36与真实HTTP应用上下文；一步一用started/completed marker。
6. `tools/e2a-cross-source-verify.ps1`：增加 `-Accounting` 选择该验收。原独立ACL、端口/datadir/schema/user/binlog核验及认证shutdown保持。

记账原语不替代正常/短装权限、独立决定、候选集合、映射失效、当前资格或来源/库存共同锁；调用方必须完成这些检查。仅测试直接调用内部原语，没有注册生产确认handler，也没有改变CommandUnitOfWork的合成台账语义。数据库V36、API、Android Room21均未变。

## 作者验证与独立环境

证据根（主树ignored本地）：`outputs/r06-e2a-takeover-20260925-01a0d612/`。

- 聚焦：`server-accounting-focused.log`，实际通过。
- 全套：`server-accounting-full.log`、`server-accounting-full-results`，76 XML、490项：489通过、1 Windows符号链接条件跳过、0失败/错误。本轮重新实际执行，不借用旧463/481/490结果。
- 两树 `git diff --check`通过，`main-accounting-diff-check.log`、`server-accounting-diff-check.log`。
- 独立环境最终attempt24：1个IT通过，覆盖上述场景。XML SHA256 `C67C1A1D07983A81C45087A32439702734307B58AEA109F577AEA8E7D28706DB`；原位 `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-24/integration-result.xml`。
- attempt23是扩展状态比对/正式续作前的1/1通过；attempt24为最终。`server-accounting-attempt-results.json`保存19–24结果和XML哈希；`server-accounting-hashes.json`保存本轮6业务/测试/脚本文件哈希。
- 准备/身份/原资格通过既有MockMvc夹具创建。TEMP提交正文、读取、原正文重放、迟到ABORT以及正式普通/续作最终提交使用真实TCP HTTP；TEMP最终记账与异常注入为Spring内部调用+真实MySQL事务，不冒称生产确认HTTP或独立人员复审。测试身份与密钥合成。
- 主树Android本轮没有业务代码变化，继承ABORT轮3个测试文件哈希变化数0。历史Room21全套338项/APK/lint及Android组合属于此前证据，本轮未重跑。

## 失败证据不删除

| attempt | 当次结果与处理 |
| --- | --- |
| 19 | 真实HTTP受理成功后，原记账器将TEMP空assignment传入Ids.bytes导致NPE，1失败，红测保留。 |
| 20 | 新policy类同名require遮蔽静态require造成编译失败，无本轮IT XML；显式SubmissionJson.require并拆出查询后修复。 |
| 21 | 成功路径已通，扣账后重复调用被测试夹具reserve断言提前拦住；改直接测试记账器拒绝，1失败保留。 |
| 22 | ABORT负例夹具仅改status触发schema要求receipt的CHECK，未到目标业务保护；改用真实签名取消回执同时落状态，1失败保留。 |
| 23 | 1/1通过；来源、政策、库存、签名、末尾审计回滚及FORMAL两政策。 |
| 24 | 最终1/1通过；增加完整状态比对、迟到ABORT、无生产operation及正式续作。 |

所有attempt保留原目录与日志，核身份后认证shutdown；19330/19434/19435无监听，无Java/Gradle/adb/emulator在途。共享3306/PID5448及父4412未操作，重型槽空闲。

## 未完成与下一断点

该子项不包含真实TEMP正常确认/短装批准决定记录、候选排除及映射失效、LINK、拒绝/实物处置/续作、成功TEMP反向重复提示、Android成功验签/归档耐久。下一沿既有C1/M2b台账与锁顺序接内部确认决定及最终失效重查，再消费共同记账；不把合成 `INTERNAL_TEST_ONLY` 台账伪装成业务成功。

正常有权确认允许空理由；短装须独立权限和职责，未定排除/拒绝权限不擅自授予。七类生产handler/POST/放行/关闭能力仍关闭，`localDeletionAllowed=false`。E2a未冻结/零有效包/未决输入/其他尚未提交来源等未验，真实历史keyring、独立人员、模拟器/真机、OS进程死亡/断电/真实网络/长时、人工/现场/生产权限/审批、R05与R09限制保留。继续R06后R07–R09可独立事项；不重做R01–R05有效完成项，不合main、不部署。
