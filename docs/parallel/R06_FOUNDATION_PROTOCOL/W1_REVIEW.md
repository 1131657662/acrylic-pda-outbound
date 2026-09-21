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

## 最终补测提交复审

2026-09-21 追加只读复审 W1 `27b69a7a7fb719f5f449206930d9f6dc684a605f`。相对 `0311dfa` 仅 3 个测试文件和 3 个文档文件；`server/src/main` 完全无差异。4 份最终测试源码的 Git blob LF SHA-256 全部匹配作者 `final-summary.json`，没有发现该补测范围整合阻断。

作者最终 XML 为 56 单元通过，以及 29 个不同 IT 通过：来源基础 5、投影 2、竞争 2、条码同步 6、PC 9、TEMP 可用性 5。29 项取来源最终 9 与首轮已通过 20；原初始 5 已包含，不累计。补测首轮 29 中 5 个失败是合成 fixture 事务外调用 BarcodeChanges，XML 栈与源码修正一致。修前 fixture 未独立保存源码快照，报告明确没有修前 SHA，未把后来源码伪称为首轮运行版本。

两份 `.local/evidence/source-concurrency/source-first-{true,false}.json` 已读取：均 READ-COMMITTED，连接 39 持有、连接 40 等待 `user_management_guard` 的 `X,REC_NOT_GAP` 锁，属于真实 G 等待而非直接 K 行锁等待。来源先提交后，正式服务报告占用冲突，唯一 K 仍为 SOURCE_RESERVED、原 evidence set 保留；正式先提交后，合成来源写者报告冲突，唯一 K 为 DISPATCHED、evidence set 为 null。两份最终证据中 dispatch_cycle 均为 1。

来源竞争一方明确是合成 SQL 窄写者，按现有 E/G/U/W/B 锁顺序并在同一事务保存 K 与 BarcodeChanges；不证明生产证据 API、真实来源权限或完整 C09/C15 已实现。正式一方调用现有 MockMvc 提交服务。最终状态、等待对象和测试边界在作者指南中相符。

投影实际覆盖 PC 候选 RESERVED、录入 409/PC_BALE_UNAVAILABLE 且无 pc_entry，条码增量、TEMP 可用性指定 bale/cycle、库存重发后原 evidence set 保留。正式 PreparationPayloads 仅断言响应出现 available=false，未按目标 bale 逐项断言，不能扩大为更细粒度专项验收。它与静态只要 K 存在便不可用的旧查询一致，当前没有生产改动阻断。

上述精度限制随后由最终 `04a679beb9b5e250d0c67101edb97fdf1c264fac` 闭合：仅测试与作者记录两文件，delta 与 PreparationPayloads 都按目标 baleId 过滤唯一条目，分别断言 RESERVED 和严格 JSON false。独立读取新增 XML，2 项全部通过，0 失败/错误/跳过；SHA-256 为 `f608603eae204a635994eb7169f2e2f7f045a27413fba3b53fe55d6311cd5c47`。生产/V34 与 `0311dfa` 仍完全一致，本地/远端最终 SHA 一致、工作树干净。作者不同 IT 仍为 29，不把两项重跑加成 31；没有新增阻断。

读取作者正常停机日志及四份失败/成功库留存说明；未启动或操作实例，未重跑单元、IT、JAR、数据库、HTTP 或设备。上述均是独立源码/原始证据审查，不能冒充组合新跑。W3 `dbb0693` 的作者复审报告也已随该提交保存，内容与本目录 233 项及生产基础限制一致。
