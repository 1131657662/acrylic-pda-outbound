# R06 首轮正常确认与独立短装决定内部子项验收

日期：2026-09-25。当前会话 `01a0d612-1e51-78c2-9eca-f15671339987`；作者及自审为当前助手，无独立人员复审。前九步持续目标 active。本次只收口已收正文、首轮原映射、默认检索范围没有未决正式候选的内部正常确认，以及独立短装批准和历史回执读取；不表示整个 M2b-4a/4b、R06、生产确认或上线完成。

## Git与写权

- 主树 `D:/acrylic-pda-outbound`：`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。
- 服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`：`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。
- 两树 origin 为 GitHub 1131657662/acrylic-pda-outbound。完整未提交/未跟踪清单见 `R06_TEMPORARY_CONFIRMATION_WORKTREE_STATUS_2026-09-25.md`；其他既有修改、out.xlsx和失败原件保留。没有提交、推送、合并、标签或部署；最新服务端尚未整合主树。
- 旧同目标任务均notLoaded，无旧写者；当前助手为两树与专用隔离库唯一写者，无新任务/工作树/子代理。

## 完成标准与实现

1. 普通 `temporary_order:confirm` 允许空理由；`submission:review` 只能批准短装，不能替代TEMP正常确认。原操作者自批拒绝；未配置任何小团队自批例外。某产品实际0允许，整单0仍由原正文证据/记账非空保护拒绝。
2. `TemporaryConfirmationSource` 重新读取原正文/摘要、原控制/资格、计划、输入/事件、实绩与原库存快照；核current/round1/mapping1、唯一PDA_TEMP待审目标/FROZEN槽、原计划revision及当前人机仓准入。资格今日过期不否定原48小时内已保存事实；其他权限、身份版本、恢复代次变化仍拒绝。
3. `TemporaryDecisionCandidates` 在共同E/G/U/W/D/T/C/O/L/S事务中用服务器完整查询重算候选；范围明确为既有审阅默认值：正文受理日（上海时区）前后7日，匹配同车或产品重叠的正式单。该技术检索范围不声称覆盖所有可能重复。摘要使用规范JSON，历史重读不受字段排序影响；不能信任浏览器某页/过滤结果。非零候选仍失败封闭，不自动排除。
4. `TemporaryConfirmationOperation` 只供隔离测试显式构造，无Component/Bean、生产POST或按钮。正常确认经过短装批准引用及库存复核后调用已验共同记账；短装批准只写决定，不扣账/派发。批准要求另一有权人员、同原S/目标/计划/候选和最近决定版本；批准人当前撤权或版本变化不可用于新确认。
5. `CommandUnitOfWork` 保留原锁/expected/原键事务，在同一事务记录 `RECORDED` 业务决定、原签名回执、决定事件及审计；与成功结果/库存/slot/outbox一起提交或回滚。旧 `INTERNAL_TEST_ONLY`/businessDecision=null 保持旧单参数签名接点与回放。原键重放先于新写权限、今日expected和新业务状态核查，当前仍有读取权限时可恢复原字节。
6. `CommandBusinessIntegrity` 与 `CommandReceiptIntegrity` 校验原正文/产品/短装原因/候选上下文、原身份与映射、原命令/回执/事件/审计，以及成功签名和实际结果快照；短装批准引用逐字节hash及决定版本闭合。篡改不重新签发或降级信任。
7. `CommandDecisionEffect` 将历史原件与当前效力分开：真实正常确认显示COMMITTED，旧短装批准显示CURRENT_CONTEXT_MATCHES或CONTEXT_CHANGED，合成回执仍UNKNOWN。当前权限、候选、来源、槽、恢复代次等变化仅列原因，已提交成功不可撤销。E2证据当前评估仍未实现，applicable及全部能力恒false。

接口路径/数据库V36/Android Room21未变；只读命令schema增加真实决定的当前效力枚举。既有外部独立消费者尚不接受真实businessDecision；后续跨端协议与消费者必须单列验收，不能因内部签名通过即开放产品。

## 当次验证

主树证据目录：`outputs/r06-e2a-takeover-20260925-01a0d612/`。

- 作者聚焦实际通过：`server-confirm-focused-final.log`。最终全套：`server-confirm-full.log`、`server-confirm-full-results`、`server-confirm-full-results.json`，77 XML，491项，490通过、1 Windows符号链接条件跳过、0失败/错误。这是本批重新执行的结果，不借用旧463/490项。
- 独立环境attempt28：IT 1/1通过；专用ACL/datadir/schema/user/19330、非空V34 seed→V36、binlog开启。XML SHA256 `E335E29C045B0F814A373A13684AF443F6D758C8F2707312793199170136B568`。原目录 `D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-28/`。
- 准备身份/原资格用既有MockMvc合成夹具；TEMP正文受理、成功提交回执GET、命令详情GET使用真实TCP HTTP；内部命令服务通过真实MySQL事务执行，不冒称生产确认POST。测试签名为合成密钥。详情GET证明原业务回执文本逐字节保持。
- 正常空理由、短装无批准/自批/仅review/撤review、某产品0、候选出现/候选未决、设备准入撤销、槽版本变化、原操作员权限撤销、旧合成台账、无生产operation/GET-only边界通过。
- 晚确认测试只注入资格评估时钟到原到期后，保留数据库固定48小时与原资格/正文/签名；这是时钟边界验证，未运行真实48小时等待。
- 成功签名失败、命令签名失败、最后命令审计的真实FK失败均整笔回滚；比较原S、订单、slot、决定版本、请求/回执/事件/审计、结果、dispatch、库存账、K、barcode pending、outbox。原键随后可成功一次。
- 独立两连接 READ-COMMITTED/非autocommit实际竞争5组：same-key、different-content、different-key、candidate、revoke。`confirmation-race-*.json`保存连接79/80、MySQL performance_schema真实G锁等待与提交/回滚。正式OrderService.create+publish及UserService撤权实际先提交，后到命令拒绝；同键返回原字节，异文和不同键同版本不重复记账。等待实际发生在user_management_guard，不冒称单独隔离了W锁竞争。
- hash/未知签名kid/决定事件/审计类型/成功结果篡改均失败封闭，保留原响应字节。
- `server-confirm-hashes.json`保存本批相关代码/测试/契约/脚本指纹；最终检查变化0。两树diff --check通过。作者自审包括锁顺序、事务范围、旧台账兼容、原签名/摘要、权限与成功不可回退；无独立人员复审。

## 失败原件与环境

| attempt | 结果 |
| --- | --- |
| 25 | 1失败：夹具WAREHOUSE_ADMIN没有独立temporary_order:confirm，在权限检查失败；改为仅测试用户附加专用角色，不改变生产默认角色。 |
| 26 | 1/1通过：初始正常/短装/候选/权限/命令签名回滚；不是最终扩展验收。 |
| 27 | 1失败：晚确认夹具单改expires_at违反固定48小时CHECK；此前场景已运行但不称本轮整体通过。改注入资格评估时钟，不改或禁用数据库约束。 |
| 28 | 最终1/1通过，含扩展效力、原子性、旧合成兼容及五组并发。 |

`server-confirm-attempt-results.json`列全部XML/hash。所有attempt原目录/数据库/日志保留，均核身份后认证shutdown。收口19330/19434/19435无监听，无Java/Gradle/adb/emulator在途，重型槽空闲。共享3306/PID5448（父4412）未操作。

## 未验与下一项

本子项不实现候选排除权限/决定、非零候选确认、LINK/映射2+、TEMP更高轮次、拒绝/实物处置/续作、正式单侧成功TEMP反向提示、Android真实成功验签/归档和产品确认入口。下一沿现有Android原冻结正文/签名/原键耐久接内部成功消费者，再完成上述R06剩余链及R07–R09；不重做已验R01–R05。

E2a仍PARTIAL；未冻结/零有效包/未决输入/其他尚未提交来源的完整恢复、可信完整STOP及生产决定依赖继续单列。真实历史keyring、独立人员、模拟器/真机Keystore、OS进程死亡/断电/真实网络、长时测试、人工/现场/生产权限/审批、R05 code20/Room17与R09限制保持未验。七类生产handler/POST/放行/关闭能力继续关闭，localDeletionAllowed=false；不合main、不部署。
