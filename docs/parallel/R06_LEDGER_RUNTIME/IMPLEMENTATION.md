# C1b 最小实施卡

本卡先经主协调确认，随后本树完成首生产实现和必要作者验证；准确结果/失败/未验范围见[作者验证](VALIDATION.md)。下列“拟”描述保留方案形成时含义，不替代该交付记录。

2026-09-21，W1 唯一生产写者；新树 `D:/acrylic-pda-worktrees/r06-ledger-runtime`，分支 `codex/r06-ledger-runtime`，起点 `82a2e13cac306f877ed5e2c887970adf3d8e57f0`。沿已审 C1 四稿和第五轮 V34 实施；本卡是具体接点，不是已完成结论。V34 不改，不预占 V35；尚未发现必须新增 schema 的缺口。

## 可执行边界

- 生产应用没有任何七命令决定 handler，也没有 POST 执行入口；内部运行器找不到已注册实现必须拒绝新执行。LINK、排除、REJECT、实物处置、续作和最终记账语义本批不实现。
- 测试通过 test-only Spring 配置注入窄 `CommandOperation`，只支持 NORMAL_CONFIRM/SHORT_APPROVE 的内部合成事务演练；回执明确 `status=INTERNAL_TEST_ONLY`、`businessDecision=null`，事件 kind 为 INTERNAL_TEST_ONLY，不伪造确认/短装成功。所有能力 false；生产打包不包含这些 bean。
- 合成操作也须过真实当前身份、锁、同仓原来源、全部受支持 expected、签名、数据库约束及完整性验证。候选摘要和物理证据/manifest 没有生产语义，本轮新执行拒绝非 null 值，不能只验格式就签成功。

| 族 | 已有真实权限码 / 本轮处理 |
| --- | --- |
| NORMAL_CONFIRM | temporary_order:confirm；只用于上述内部测试，新产品执行仍关闭 |
| SHORT_APPROVE | submission:review；只用于上述内部测试，不据此批准TEMP确认或放行 |
| LINK | temporary_order:link；可作该已存族的本人读资格，未实现目标/双源语义，新执行拒绝 |
| PHYSICAL_DISPOSITION | order:dispose；可作该已存族的本人读资格，未实现完整证据/去向语义，新执行拒绝 |
| REOPEN_WORK | order:resume；可作该已存族的本人读资格，未实现原机/保留集合/续作语义，新执行拒绝 |
| DUPLICATE_EXCLUDE | temporary_order:link 尚为专项技术建议，本批不冻结该写授权；新执行拒绝，已存族只走confirm历史读资格 |
| REJECT | 当前C1未锁定TEMP独立拒绝写授权，本批不猜测；新执行拒绝，已存族只走confirm历史读资格 |

所有历史读取仍限当前同仓和本人；confirm只提供历史读取替代资格，不扩张上述各族的新写权限。角色/权限快照和当时session在原签名中固定，重登录只影响当前鉴权。

## 生产接点

1. `temporary.command.TemporaryCommandService.execute(byte[] commandUtf8, String requestId)`：内部入口，返回已提交的精确原响应 bytes。顶层调用必须没有外部未完成事务；内部 TransactionTemplate 使用 REQUIRED/READ_COMMITTED，拒绝借嵌套事务提前返回可见成功。先独立只读预查，再一次 REQUIRED 写事务；唯一键异常离开并完整回滚写事务后，另开只读恢复。无结果保持未知/冲突，不修补孤立记录。
2. `CommandOperation`：仅 Java 服务端扩展接点，当前生产 bean 集为空。测试配置可声明上述两种合成族，执行阶段只写合成事实/注入异常；可信 `CommandContext` 来自锁内数据库，不接受客户端 actor/permission/currentEffect。协作者可 spy 签名/仓储/审计边界注入故障，不能增加面向用户的故障开关。
3. `CommandStore`：窄 request/receipt/event 仓储，复用唯一 actor/`r06.command.v1`/key；服务端独立分配 request/receipt/event UUID。仅 INSERT 原字节，无 UPDATE/DELETE 原台账方法。每次读取核 request.resource_id↔receipt.id、receipt.api_request_id↔request.id、actor/route/key/hash、命令 canonical bytes、原 response bytes/hash、签名/内外一致、event 原上下文及相关源 FK 语义。
4. `CommandReceiptCodec`：使用真实 OfflineSigner，audience/域沿第五轮；可信原事实、actor/权限与角色快照、原操作员、原设备/资格/映射、服务器毫秒时刻及 false 能力进入签名。历史 key 不在当前可信 key 中时拒绝核验，不回退/重签。`currentEffect` 不在签名或原 BLOB 中。
5. `CommandReadService`：Web/PDA 复用现有 Access/PdaAccess 当前身份复核。本人精确原键，当前同仓、原族权限或 confirm 历史读资格；原族由已存命令决定。读取不要求旧 expected/epoch 仍当前，不把旧权限快照授予新权。其他人/跨仓/无读权与不存在均不给原内容。

## 锁和 expected

身份/原仓读资格后的首次已提交原键查询，与 E S→G X→排序完整 U→W→D→H 后的第二次查询，都早于新执行 handler/写权限/expected 判断。身份前缀用既有 IdentityWriteGuard、PdaSessionLocks 和仓/设备锁，不借会补建来源的旧 SubmissionStore.lock。

提示取自真实已存在 S/T/C，收集 actor 和 original operator、原设备与当前 PDA 设备；固定身份集合后锁 T→C/decision→O/已有 slot→S，提示原人/来源变化回滚，不补未排序锁。无 canonical 跳过 O/slot；不建订单、assignment、scope 或 submission。

expected 的 canonical/mapping/currentSubmission/round/slot/decision/received/declared/plan/targetRevision/userAuthVersion 逐项比对真实记录；nullable 保持原意。输入 source/S/round/authorization/freezeDevice/sourceEpoch 全部与原事实交叉校验，transportEpoch 只对新写执行 E 门禁。候选及 E2 字段本轮仅支持 null。正常空 reason 合法。

## 读取 wire

拟新增并文档化四个 GET，Web 与 PDA 共享同一原键域：

- `/api/v1/temporary-command-receipts/{commandId}?warehouseId=...`
- `/api/v1/pda/temporary-command-receipts/{commandId}?warehouseId=...`
- 上述各自的 `/original` 子路径返回 `application/json` 原业务回执 bytes，无 envelope/重序列化。
- 详情使用既有 ApiEnvelope，data 为 `{originalCommandReceiptUtf8, originalResponseSha256, currentEffect}`。字符串 UTF-8 编码后等于原 bytes；currentEffect 单独包含 evaluatedAt、当前上下文、多项 reasons 与全 false 能力，不能更改旧 status 或重开来源。
- 全部 no-store；401 当前身份无效，404 不存在或无可见权（不证明未执行/可放行），409 完整性/未知 key/不可核验。无 POST，预计契约升至 0.30.0 / 120 paths，Android 不变。

## 独立验收接点与交付

W2 独占 ledgerverify 与 LedgerVerifySeed；可注入 test-only CommandOperation、调用内部 execute 并走真实 GET/挑战重登，测双次原键优先、不同 actor 同键、同版本竞争、request/receipt/event/audit 全有或全无、坏关系/字节、签名与审计失败。需要真实等待时由服务端协作者 spy 在指定边界阻塞，不在生产添加计时开关。新 EC 私钥由各树 ignored 夹具保存。

W3 独占 R06_LEDGER_PROTOCOL，消费冻结的真实 codec/命令/读取 DTO；新生成回执与原 command/response 向量绑定服务端可信事实，不能只验 RSA。固定源码 SHA 由 W1 完成首作者验证后发送，当前无源码完成或测试通过承诺。

本树仅新19326/`acrylic_outbound_ledger_20260921`/`outbound_ledger_20260921`及独立 datadir/private/signing，原端口/实例/数据不动。首重型时段归 W1，转交前通知 W2/W3；MockMvc 不计真实 HTTP，结束正常停库并保留失败证据。
