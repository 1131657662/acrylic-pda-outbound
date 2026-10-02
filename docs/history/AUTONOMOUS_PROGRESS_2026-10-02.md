# 自主推进记录（2026-10-02）

## 子项状态与SHA

| 子项 | 状态 | 提交/证据 |
| --- | --- | --- |
| P08原主键保护 | 已验证推送，接续未重跑 | 6faf322；10月1日记录及commit-review |
| R06来源分页 | 已验证推送，远端一致 | 33533e4；bindings-closeout/git-final.json |
| R07首次同键并发 | 已验证推送，远端一致 | dce5fe7；handover-closeout/git-final.json |
| R07交接同步status兼容 | 已验证推送，远端一致 | bfcf471；sync-status-closeout/git-final.json |
| R07实际账务贡献 | 离线、迁移、HTTP与导出验证通过，已推送 | 458a943；accounting-repair/closeout/git-final.json |
| R06 Web原命令恢复 | 构建、hook/契约/Chrome回归通过，已推送 | e2fa8e8；web-recovery/closeout/git-final.json |
| R07更正原键actor回放 | 编译、离线全量、真实隔离HTTP通过，已推送 | 880808e；e2a-cross-20260925-841、correction-replay/offline-2 |
| R07 HEADER撤回入口保护 | 编译、离线全量、真实隔离HTTP与导出回归通过，已推送 | 8b203d0；e2a-cross-20260925-847；R07_3_HEADER_WITHDRAW_BLOCKER_2026-10-02.md |
| R07历史更正outbox同步兼容 | 单测、真实隔离HTTP、完整服务端回归通过，已推送 | b87433d；e2a-cross-20260925-849；R07_LEGACY_CORRECTION_OUTBOX_COMPAT_2026-10-02.md |
| R07 CYCLE_REOPENED/历史 REVERSAL 审计 | 已核对；取消派发重开有组合证据，历史无引用账务保持 fail-closed | R07_CYCLE_REOPENED_REVERSAL_AUDIT_2026-10-02.md |
| R07周期重开占用迁移 | 编译、离线回归、真实隔离HTTP通过，已验证推送 | 38ff2ba；e2a-cross-20260925-852；cycle-reopening-completed.json |
| R06 TEMP拒绝保留占用 | 离线全量、命令回归与真实隔离MySQL通过 | eb7ac94、f015954；attempt 856；R06_TEMP_REJECTION_2026-10-02.md |
| mapping3+/R1及R06/R07余项 | 未完成 | 沿优先级接续 |

## 自主决策记录

1. 单助手沿修订授权实施、自审、隔离验证及开发分支推送；保护预存PDA文档、更正链IT和旧脚本，仅明确范围暂存。
2. 来源分页传递limit+1给Page.from，末个返回项作游标；真实HTTP逐项比对55来源验证无重漏。
3. 交接首次同键在案件/来源行锁后重查回执，核对正文及签名后返回原字节；新键仍须expected一致。823至825是服务层MySQL证据。
4. 交接新同步消息补ASSIGNED，历史缺字段读取兼容，坏值拒绝，不修改不可变原件。
5. 828包装器URL插值错误，保留失败库/日志；修包装器自检后829通过。早期行补丁纪律偏差已改整文件写入。
6. 账务按实际原扣及贡献图：无扣不虚构、增重负差、归零逐笔反向；撤回独立恢复引用，原账/原结果不改写。
7. 撤回更早更正沿不可变引用链找当前代表；校验唯一、深度奇偶、来源及逐笔逆值，拒绝冲突。
8. 零账务更正仍导出，撤回导出追溯贡献/恢复链；验收分离身份、签名回执、SQL投影、真实XLSX/PDF下载。
9. full-2无结果、full-3编码失败、full-5编译失败均保留；中文整文件脚本统一pwsh。
10. 修复旧主管夹具documentId路径与撤回导出引用；导出IT自包含SUPERVISOR身份，原创建者确认，保护预存更正链IT差异。
11. 830 renderer配置503、831确认凭据409整体失败；不得用前半链替代导出验收。
12. 应用brainstorming、单一职责、测试、安全、SQL schema/joins技能；技术选择直接执行，用户自主授权覆盖设计确认门槛。
13. TEMP确认采用测试内完整服务组装及扣库政策前置；验证签名完整性、原字节回放与唯一决定事件，生产入口仍阻断。
14. 非空旧schema/旧账升级验证全部旧列守恒、重复迁移、精确约束及append-only；833真实MySQL通过。
15. 完整性验证器返回unsigned正文，修夹具断言后完整离线回归通过，产品校验未放宽。
16. 非空账链净零不能等同未扣库；只有entries为空可跳过结果匹配，反例先红后绿，832真实HTTP验证拒绝及回滚。
17. 837审批前401源于测试身份缺pda:login；仅补隔离SUPERVISOR前置。
18. 838 Web登录403源于隔离HTTP和Secure cookie，缺CSRF会话；仅在导出IT启动参数加cookie.secure=false，生产默认不变。
19. 839完成Web登录及真实恢复结果XLSX/PDF下载后再收口；明确暂存37文件，排除预存更正链IT及PDA差异，458a943提交推送。
20. Web恢复把身份、仓库、提交和命令类型纳入localStorage键；保存原JSON并读回核对，未确认回执时禁止换键，Web Locks缺失时拒绝发送。
21. 重试只回放保存的原JSON和原恢复代次；CSRF异步读取后再次校验身份，跨页并发保留首个原请求，匹配回执才持久完成。
22. 自审发现UI打开时会在存储读取失败后继续拉取事实并清除错误，改为仅无错误时加载；hook读失败反例证明不POST。
23. 真实契约测试缺失样本导致首轮回归2项失败；隔离MySQL分配840，MockMvc生成真实样本，认证停机并保全dump/日志。
24. Web schema原先允许writeConfirmationAvailable=true，契约反例先红；按服务端恒false政策收紧，最终7项契约回归通过。
25. Chrome回归首次在异步清理完成前读取localStorage造成2项假失败；改为等待可观察结果后10项全部通过，失败证据保留。
26. e2fa8e8提交推送15文件；校验最终源码哈希及远端。后续长命令全部放脚本。
27. 更正回放原实现只按原键读取，不绑定actor且不校验完整回执；修为actor_id绑定、规范化原字节比较及签名/存储完整性验证，保留统一404防止身份枚举。
28. 841真实HTTP反例覆盖原 actor 四类命令回放、同仓库 outsider、改正文冲突和前后状态不变；产品提交880808e并推送。
29. R1 故障注入改用测试域 BeanPostProcessor 替换同名 AuditWriter，遵守 Primary 冻结白名单；842 的注入歧义失败保留，843/844 真实隔离 MySQL 通过后才收口。
30. R1 写后回滚验收按 16 张表逐字快照、审计后中间状态、原键提交/重放和上下文重建复核；生产默认与原库边界不变。
31. mapping3+ 只记录 fail-closed：版本字段可表达高值，但没有安全的重复关联/旧目标失效政策；不凭字段或自动重链入口宣称完成。
32. HEADER 撤回沿确认端既有 CORRECTION_LEDGER_CONFLICT 在发起端提前拒绝，避免生成必然无法确认的 WITHDRAW_PENDING；实际表头恢复语义未定义，保持 fail-closed。run 846 的渲染器缺失失败保留，run 847 配置渲染器后 1/0/0/0 通过。
33. 历史更正 outbox 缺少 status 时只对三个已知更正事件回退 COMPLETED，显式坏值和未知事件继续拒绝；依据原订单保持 COMPLETED 的 R07 规则。run 848 夹具占位符失败保留，run 849 真实 HTTP 1/0/0/0 通过，完整回归为 148 套件/2964 项/0 失败/0 错误/1 既有 skip。
34. CYCLE_REOPENED 只按已编码的 DISPATCH_CANCELLATION 收口；历史无引用 REVERSAL 没有可审计归属政策，继续由贡献图返回 CORRECTION_LEDGER_CONFLICT，不回填、不猜测、不把取消派发覆盖成完整重扫能力。
35. 周期重开先按稳定顺序锁定派发、包和占用记录，校验取消派发的周期/订单/状态后删除旧 DISPATCHED 占用并推进包周期；任一包冲突或账务失败事务整体回滚，重复请求返回 CYCLE_REOPENING_EXISTS。run 850 的旧夹具缺少后续账务列、run 851 缺渲染器均保留，修正后的 run 852 真实 HTTP 通过后才收口。
36. R06 TEMP 拒绝先独立落地：复核人绑定完整候选上下文后写 `REJECTED` 和原因，订单保持待处置状态，`REVIEW_RESERVED` 占用不由拒绝事件自动释放；真实隔离 MySQL 通过 attempt 856，现场身份与停止边界仍未替代。
37. `TemporaryRejectionIT` 内部使用真实 HTTP，因此固定 `19434` 的 Spring Boot `DEFINED_PORT` 上下文；attempt 856 以 1 类/1 测试通过，853 的 NPE 与 854/855 的 runner 收尾失败均保留。
38. 首轮清理只针对可再生构建缓存和未引用的三份 Demo 数据库快照；后续按用户要求删除三个无活动进程的 AVD 快照、历史运行时 JAR 及旧检出/快照的 `build/`，保留 `.local` 中的数据库与证据，runner 在 `localDeletionAllowed=false` 下跳过自动 datadir 删除。

## 验证与证据索引

已交付：[分页验收](R06_SOURCE_BINDING_PAGINATION_REPAIR_2026-10-02.md)、[原键验收](R07_FIRST_REPLAY_REPAIR_2026-10-02.md)、[同步验收](R07_HANDOVER_SYNC_STATUS_REPAIR_2026-10-02.md)、[账务验收](R07_ACTUAL_ACCOUNTING_REPAIR_2026-10-02.md)、[Web原命令验收](R06_WEB_ORIGINAL_COMMAND_REPAIR_2026-10-02.md)、[更正回放验收](R07_CORRECTION_REPLAY_ACTOR_REPAIR_2026-10-02.md)、[R1写后回滚验收](R1_HANDOVER_WRITE_AFTER_ROLLBACK_2026-10-02.md)、[mapping3+审计](R06_MAPPING3_FAIL_CLOSED_AUDIT_2026-10-02.md)、[HEADER撤回入口验收](R07_3_HEADER_WITHDRAW_BLOCKER_2026-10-02.md)、[TEMP拒绝验收](R06_TEMP_REJECTION_2026-10-02.md)。

账务证据根.local/autonomous-20261002/accounting-repair/：offline-full-9/10/11各148套件2956项0失败0错误1既有skip。832至836及839隔离验证成功，原库/dump/日志保全停机。

Web证据根.local/autonomous-20261002/web-recovery/：final-2/unit为35项通过；final-2/regression包含build、7项temporary-review、OpenAPI及Chrome 10项，均exit 0。840为TemporaryReviewContractIT真实隔离MySQL+MockMvc 1项，零失败/错误/跳过；失败证据保留。

更正回放证据根.local/autonomous-20261002/correction-replay/：compile-1成功、offline-2为2956项0失败0错误1既有skip；841为真实隔离HTTP 1项零失败/错误/跳过，dump、XML、原回执和认证停机记录保全。

R1证据根`.local/e2a-cross-20260925-842/`、`843/`、`844/`：842为配置歧义失败且无完成标记；843与844各1项真实隔离 MySQL 通过，16表快照、原键回执和认证停机保全。完整`server test`为148套件/2956项，0失败、0错误、1既有skip。

HEADER撤回证据根`.local/e2a-cross-20260925-847/`：E2aCorrectionAccountingHttpIT为1项通过、0失败、0错误、0 skip；`accounting/header-withdraw/`含拒绝回执和状态不变断言输入。846因渲染器缺失失败，未计入完成，证据保留。

历史更正 outbox 兼容证据根`.local/e2a-cross-20260925-849/`：`E2aHandoverSyncHttpIT`为1项通过、0失败、0错误、0 skip；新/旧交接消息与缺 status 的历史 CORRECTION_EFFECTIVE 均只发布一次，回放响应相同，投影状态为 COMPLETED；首轮848的夹具失败证据保留。专项记录见[R07历史更正outbox同步兼容](R07_LEGACY_CORRECTION_OUTBOX_COMPAT_2026-10-02.md)。

周期重开占用迁移证据根`.local/e2a-cross-20260925-852/`：`E2aCorrectionAccountingHttpIT`为1项通过、0失败、0错误、0 skip；重开完成回执、旧占用删除、包周期推进、账务投影和重复重开 `CYCLE_REOPENING_EXISTS` 均保全。run 850 的旧 `E2aCorrectionChainIT` 夹具只迁移到早期 schema、缺少后续账务列而失败；run 851 因未配置报表渲染器返回503，均保留为失败证据。

TEMP拒绝真实隔离证据根`.local/e2a-cross-20260925-856/`：`TemporaryRejectionIT`为1项通过、0失败、0错误、0 skip；隔离 MySQL 数据目录、初始化/测试日志、认证停机和 XML 保全，19330/19434监听均已释放。

周期/历史账务审计见[R07 CYCLE_REOPENED 与历史 REVERSAL 审计](R07_CYCLE_REOPENED_REVERSAL_AUDIT_2026-10-02.md)：取消派发重开、不可用快照和重复重开已有更正链断言；PHYSICAL_RETURN 触发与 PDA 重扫未定义/未验，历史无引用 REVERSAL 保持 fail-closed。

## 仍未解决与边界

mapping3+及R06/R07余项。PHYSICAL_DISPOSITION、完整停止证据、释放占用、HEADER撤回实际恢复语义、历史无引用REVERSAL的归属、PHYSICAL_RETURN周期策略与CYCLE_REOPENED后的 PDA 重扫仍未完成；TEMP政策/高轮次/跨来源、526、R05原资料/旧BCD、R09恢复限制单列。fixture/HTTP/SQL不是现场；PDA/Keystore、独立JVM/OS死亡、断电/长时与完整UI未完成。R1写后回滚与TEMP拒绝均已完成隔离验收，但不替代现场边界，目标按用户指令暂停。

## 磁盘与环境

842/843/844/846/847/848/849/850/851/852/856隔离库均已认证停机；无在途重型任务，19330/19434/19435测试监听0，系统MySQL不动，emulator/qemu不在运行，`.local` 证据删除0，设备动作0。删除 AVD 快照、历史运行时 JAR 和旧 `build/` 后 D 盘可用约52.4GiB，仍高于20GiB；`demo-artifacts`/`outputs`及数据库证据保留。未合main/标签/Release/部署、未接原库/现网、未改生产默认开关。目标按用户指令暂停。
