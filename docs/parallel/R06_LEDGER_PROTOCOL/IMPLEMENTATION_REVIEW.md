# C1b 最小实施卡早期复审

2026-09-21，W3 只读审查 W1 `r06-ledger-runtime` 树的 `docs/parallel/R06_LEDGER_RUNTIME/IMPLEMENTATION.md`。审查对象尚未是固定生产提交；本记录不表示源码/测试通过，最终仍须按 [矩阵](REVIEW_MATRIX.md) 消费固定生产。

当前卡片符合本批范围：生产七类决定 handler 集为空、没有 POST；测试配置仅注入 NORMAL_CONFIRM/SHORT_APPROVE 的合成操作，回执 `INTERNAL_TEST_ONLY`、`businessDecision=null` 且全部能力 false。四个 GET 将原响应 byte[] 与详情 envelope/currentEffect 分开，历史键使用本人精确 principal/route/key，未新增迁移。未发现需要阻止按该卡实施的范围问题。

已发送 W1 的实现核对点：

1. 原 mapping 必须从 receipt.original_mapping_id 指向的不可变历史事件取得；当前 scope 的 mapping/target 改变应进入 currentEffect，不能以当前值否定合法原回执。
2. 历史 actor authVersion、session、角色/权限快照保留原值。当前可变记录不得作为历史完整性相等门槛；当前身份、仓库与读资格仍实时检查。原来源身份、request/receipt/event 双向关系及签名内外一致须另按不可变原事实核验。
3. 两次已提交原键查询先于新写 mode/transportEpoch/handler/expected 检查。RECOVERING 下当前允许的只读能力与新写门禁分开；旧 POST 不修改 epoch 重新执行，本批生产 POST 仍关闭。
4. currentEffect 对多个已知变化汇总原因，对候选/物理证据/生产操作等未实现维度明确未知或未启用。全 false 能力不允许搭配误导性的“已具备业务执行条件”。
5. codec 固定后，必须以实际生产构造器/读取器生成和验证回执；独立消费者用另行固定的可信服务器事实对照合法 RSA 的错 actor/原人/权限/设备/会话样本。只让签名损坏失败不足以证明可信语义。

第 1 至 4 点属于既有 C1 合同的实现检查，不新设业务审批。当前密钥只能核验已知 kid，未实现历史 keyring 时未知 kid 继续 fail-closed；不重签、不改原 BLOB、不以当前权限快照修复历史。

本文只记录早审和反馈，尚无源码交付、动态验证或最终通过结论。

## 在途源码补充复核

W3 静态发现 `CommandReceiptShape.context` 曾将合法无 canonical 的 `mappingVersion="0"` 放入正整数列表；已通知 W1，随后读取确认改为独立非负检查。主协调发现的 `receiptVersion.asInt()` 宽松转换也已改为 integral + canConvertToInt + 值等于 1。两项都是未冻结源码的静态发现，未运行或保存修前红测。

补读确认：`CommandFacts.hint` 仅查询已有 submission/scope 的身份及锁集合提示，不读取 payload 文件、当前 mapping 或资格判定；写事务仍在身份锁后先复查原键，再取得来源锁并读取完整 facts。`CommandHistoricalIdentity` 核对原资格、保留 PDA session 的用户/设备/版本/原时间窗口，不要求其今天未撤销或仍有效。当前 `PdaSessions.rotate` 只改 token hash/access expiry，原 session 的身份与长期期限保持；既有设备入口未见重分配 warehouse 的写法。

`CommandCurrentEffect` 已补当前 session/device/deviceVersion 与原 actor 的差异原因，并加入独立来源资格观察；这些仅改变外部派生评估。未知业务/候选/证据维度保持明确未实现，applicable 和能力全部 false。`CommandSyntheticEligibility` 的现时资格限制只作用内部合成新执行，不作为未来正常确认业务规则，不成为历史读取门槛。

完整旧权限/角色集合和 Web 传输分类没有独立历史表，仍是已验证写者的签名断言。PDA 稳定 session 关联与原创建/长期期限能独立复核，不宣称这些字段单独证明当时所有实时授权条件。固定源码、真实数据库故障/会话测试与独立消费者运行仍待完成。
