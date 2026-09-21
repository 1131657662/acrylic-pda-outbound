# W1 固定生产提交独立复审

审查者 W3，固定 `0311dfaf4255671a605a17aa3defc145df9621a6`，相对 `85cb7cef02fb8728599b6801a28824183305dab8` 共 17 个文件。已只读复审 12 个生产 Java 文件、V34、3 个测试文件及交付指南；旧 V1–V33、API/Android/C0 未改。当前没有发现该基础范围的源码整合阻断；非空升级/关系负向和更广旧消费者实际结果由 W2/W1 分别提供，不能由本静态审查替代。

## 数据关系及旧消费者

- 共同 K 的 `bale_id` 主键保持。只有 SOURCE_RESERVED 新分支允许 null order，旧正式/PC owner 分支显式要求 order，source evidence 与旧 owner 字段互斥；source scope/warehouse 必须同时 null 或同时非 null，避免复合 FK 的 null 绕过。
- TEMP-only scope 由可索引的生成列约束；新证据与原 scope/S/round、freeze device、同仓 bale、set/object、verification/set、receipt/原 S 身份使用复合引用。mapping 和 decision 基线以真实迁移观察时间登记，不伪造旧命令或历史 LINK。
- 迁移 guard 在永久 DDL 前检查所声明的 TEMP 原关系；K 新来源列只由既有 K→S→scope 回填，未重写旧 owner/state/cycle/order/正文/回执。MySQL DDL 中途失败须保留部分 schema 和 Flyway 失败记录，不声称整文件事务回滚。
- 检索全量生产 `bale_commitment` 消费者：正式 reserve/PC 保存/候选、TEMP 待审及条码/准备投影按已有 K 保守阻止；RecoveryStock 对 null/非空同 order 的 SOURCE_RESERVED 均冲突。正式续作先拒绝来源证据 owner，批量转换限制为旧 owner 状态；旧处置按具体 owner/state/cycle 更新，不会删改 SOURCE_RESERVED。
- 全量 `order_submission_slot` 写点已核对：新建显式列名以兼容 slot_version 默认 1，正式完成/续作批准/续作选批递增。正常 TEMP 新来源登记初值、完整接受新增 INITIAL_ACCEPTANCE 映射与 K.source_scope，全部沿原事务执行；未新增业务写入口。

## 字节与结构

生产协议复用原 SubmissionJson 的规范输出，新增 strict UTF-8/孤立代理/大小边界。command exact shape 拒绝客户端 actor、缺失或不适用 E2 字段；NORMAL_CONFIRM 空理由合法。整数版本按 longValue 精确等于 1，`4294967297` 不再可能截断成 1；此问题是主调度的在途静态发现，修正早于本消费者初次执行，没有修前动态误收证据。

最终独立 Python/Java/Node 验证结果见 [交接](HANDOFF.md)，实际编译九份生产源码由 [源码清单](source_manifest.json) 固定。其他生产源码及 V34 为只读审查，本执行者没有运行数据库/Gradle/HTTP。

## 明确保留的后续义务

receipt/control 目前只提供严格对象与域 hash，无完整 DTO、可信 actor/权限/原事实语义、生产业务签发或台账执行器。W1 指南已明确：原 mapping 与 canonical、STOP_PROOF kind、set/hash/verification、K 非空 order 与当时 canonical、request.resource_id 与 receipt.id、原字节/token 一致及不可变更新限制仍由后续窄写者落实。当前表关系和本目录合成签名不能证明这些义务已完成。

SOURCE_RESERVED 新写、完整停止证据、处置/续作、台账原键恢复、最终成功、Android 耐久、R09 fence/新版本恢复及设备/现场验收均未开放或完成。普通正常确认空理由与 localDeletionAllowed=false 保持。

另对 W2 在途夹具提出独立审查意见：修正 DEFAULT_GENERATED 普通时间列被误跳过；GET DTO 与数据库 receipt_text 字节分列；旧定义检查使用已知变化白名单；异 scope/TEMP-only 反例避免同时制造跨仓失败；DDL/脏数据负向在失败后逐旧值比对。前两组已看到作者修正，其余最终以 W2 冻结提交及证据复审记录为准。本段不是 W2 最终复审通过。
