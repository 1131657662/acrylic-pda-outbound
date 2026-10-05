# 无人值守开发进度（2026-10-06）

执行范围沿[无人值守计划](AUTONOMOUS_PLAN_2026-10-05_UNATTENDED.md)。前日 B1/B2 交付及 B3 开局见[前日进度](AUTONOMOUS_PROGRESS_2026-10-05.md)。版本事实只引用主树看板生成块。

## B3：来源、停止保全与候选消费（开发过程与保留断点）

输入 HEAD/远端为 `dc7a01ad4395cb1263ad3e87927ac8cbd39d3d8d`，树 `D:/acrylic-pda-outbound`，分支 `codex/development`。当前聊天为唯一写者；13 条既有受保护脏路径哈希未变。跨日断点 `.local/autonomous-20261005/b3-checkpoint-20261006/` 保存全部脏路径、diff、哈希、监听、进程、磁盘及旧看板。范围和退出标准沿前日 B3，不扩大生产授权。

141. 1006 真实 HTTP 暴露 VERIFY 签名正文为 `VERIFIED_COMPLETE_AT_STOP`，数据库 `receipt_status` 却固定写为 `PARTIAL`，导致同键 POST 成功但原件 GET 返回 `E2A_RECEIPT_INTEGRITY_FAILURE`。修正持久化为回执原状态；只允许 VERIFY 的历史 PARTIAL 和完整停止状态，并严格核对数据库、原文、来源、引用与签名。不自动改写或重签历史不一致记录，无新迁移。
142. Android VERIFY 验签兼容上述两种状态；OPEN/PAGE/STOP 的状态规则保持。完整停止回执也不授予业务决定能力，本地队列仍结束为 PARTIAL、保留冻结资料，全部能力与删除保持关闭。契约说明区分完整停止证据与历史 PARTIAL，旧回执不能升级资格。
143. 非空迁移夹具补齐历史 work_event schema，去除后来引入的交接表与排序列后才执行迁移；比较迁移前后真实发送快照，替换原来的自等式断言。保留原键、正文、回执、签名和暂停状态。另把 E2A HTTP 测试私有路径守卫扩展至已批准的四位隔离编号。
144. 新增服务端签名回执往返、原字节、状态篡改、非法状态及缺引用反例；新增 Android 完整停止 VERIFY 丢回执后重开恢复、STOP 冒充完整 VERIFY 被拒绝。JDBC mock 仅用于存储边界，组合另用真实隔离 MySQL/HTTP。

当次计数为 tests/failures/errors/skipped；证据根目录 `.local/autonomous-20261005/`，隔离库 `.local/e2a-cross-20260925-<编号>/`：

- `b3-draft-http-1005/`：服务端包装与 Android 子进程各 1/0/0/0；停止写者、原件队列、OPEN/ITEM/VERIFY 丢回执、Room 重开、身份暂停、服务重启/换钥/原字节与坏文件拒绝。1860 条源码前后哈希一致；该轮发生在状态修复之前，不能替代修复后的最终组合。
- `b3-unsubmitted-http-1006/`：1/1/0/0，VERIFY 及重放 200、原回执 GET 409；失败响应、日志和库全部保留。
- `b3-receipt-focused/`：306/5/0/1，5 个新增测试因 Mockito varargs 取参方式错误失败；改取原始参数数组，未据此更改产品语义。
- `b3-receipt-focused-v2/`：306/0/0/1、50 XML；唯一跳过为既有 Windows 符号链接条件。1341 条源码前后哈希一致。
- `b3-unsubmitted-http-1007/`：1/0/0/0；GET 原件、同键 POST 与数据库/签名状态一致，缺失/伪造/错仓/损坏来源拒绝且其它来源不误占，本来源 SOURCE_RESERVED。1861 条源码前后哈希一致。
- `b3-android-focused/`：启动参数把两个类名当成一个 filter，0 测试；失败日志保留。修正 PowerShell 数组传参后使用新目录。
- `b3-android-focused-v2/`：9/0/0/0、2 XML；完整停止恢复与非空来源绑定迁移通过。549 条源码前后哈希一致。

上述哈希仅证明各轮运行期间无漂移，轮间有明确修复，不能合称相同最终输入。1005～1007 均已 dump、认证停机；原失败库保留。既有 3306/13316 未操作，无在途测试；D 盘可用 25452441600 字节，高于 20 GiB 阈值。

本批未提交改动：Android E2aReceiptVerifier、E2aRealHttpRecoveryTest、LocalDraftEvidenceHttpTest、LocalDraftSourceBindingMigrationTest、LocalE2aRecoveryTest；服务端 SourceEvidenceIntakeVerifyReceipts、SourceEvidenceIntakeReadIntegrity、SourceEvidenceIntakeReadServiceTest、新增 SourceEvidenceIntakeVerifyReceiptTest、E2aUnsubmittedChecks；来源证据契约及本批文档。全部清单以断点快照为准，受保护路径不代提交。

下一步：新编号运行 E2aAndroidHttpIT 和 E2aTemporaryDecisionProtocolIT 实际非零候选消费者；核对写后 SQL 故障的停止/上传/占用/审计回滚证据，再跑 Android 全套/APK/lint 和服务端受影响全套，满足全部标准后才提交推送。

未验与边界：物理设备、原身份/Keystore、APK 安装、现场、断电与长时未验；TEMP 高轮次、候选排除/跨来源权限、LINK currentEffect 契约遗漏（B8）、R1 生产放行、R05 原资料/旧 BCD/526、R09 恢复限制继续单列。不合 main、不发布/部署、不接原库/现网、不修改生产 gate 默认值、不删除 .local；长期目标 active。

## B3 后续验证

145. `b3-e2a-android-http-1008/` 服务端包装及 Android E2aRealHttpRecoveryTest 各 1/0/0/0：真实回环 HTTP 的 OPEN/RAW/PAGE/STOP/VERIFY 五次丢回执及 Room 重开、原键原文恢复、数据库原响应字节与 Android 保留字节一致；本地冻结队列仍为 PARTIAL。1861 条源码前后哈希一致，隔离库已 dump/认证停机。
146. `b3-candidate-consumer-1009/` 服务端包装 1/0/0/0，独立 Python 消费算法 54/0/0/0；真实 HTTP 候选/原件读取、21 条非零候选的五页遍历、原始 SQL 事实重建、过期游标拒绝、候选未解决时正常确认拒绝。命令执行由隔离夹具内部调用，身份由夹具生成；不能称生产 POST 或现场验收。1861 条源码前后哈希一致，库已 dump/认证停机。
147. 来源反例原先主要证明写前拒绝，不能代替写后事务回滚。新增测试专用 E2aIntakeSqlRollback：在实际 OPEN/RAW/PAGE/STOP/VERIFY 插入后用 MySQL trigger 抛错，读取服务器日志中的原 SQLException，严格断言消息、SQLSTATE 与错误码；PAGE/STOP/VERIFY 还先确认对应阶段写入已发生。完整比较来源证据、占用、条码待同步、库存、提交/slot、审计及 outbox 等 18 张表。失败只留下可重用的原件文件，不删证据；恢复后同键 POST/GET 字节一致且再次重放不增行。
148. `b3-intake-sql-rollback-1010/` 1/0/0/0，五个阶段的精确 SQL 故障均命中，18 表逐项守恒；随后原请求恢复成功，并重复来源缺失/伪造/跨仓等反例。1862 条源码前后哈希一致。日志 `1010/http/sql-rollback.log` 和各阶段前后/提交/重放快照保留，库已 dump/认证停机。新增内容仅测试，未扩大产品事务或权限。
149. Android 新增验签更新后队列写入触发 SQL 失败：回滚验签状态但保留已耐久 RECEIVED 原回执，原请求、冻结资料与审计不变；移除隔离 trigger 后 Room 重开恢复，无重复发送。`b3-android-sql-rollback/` 23/0/0/0、3 XML，同时覆盖既有停止签名存储失败及队列插入失败的原子回滚。

150. `b3-android-full/` 完整 Android 137 XML、577/0/0/7，7 项为专用实时环境条件跳过；Debug APK 构建、嵌入 metadata/源码核对、签名检查通过。lint 0 error、199 warning；549 条源码前后哈希一致。APK SHA256 为 `9C75B3C011A6AF1E4891FFCFAAC0CEFF25D418287D0D18CA9C8DBE64B84A72F2`，保存在该目录，未安装或发布。首个 metadata 辅助检查使用字面赋值正则未匹配 Gradle provider 表达式，空值误报；改用事实脚本相同提取规则后核对 APK 实际嵌入值通过，失败说明保留。

151. `b3-server-regression/` 服务端全套 173 XML、3113/0/0/1，唯一既有 Windows 符号链接条件跳过；1011 新隔离库上接收机续作/最终提交 HTTP 1/0/0/0，普通续作/并发/提交/归档集成 41/0/0/0。修改输入哈希前后一致，1011 已 dump/认证停机。1008 后只有测试改动，其组合输入中的 926 条产品源码与交付准备输入哈希一致；详细核对保存在跨日断点目录。

## B3 当次交付

范围：修复完整停止 VERIFY 回执的数据库/原文状态不一致，保留历史合法 PARTIAL 原件读取，Android 消费合法签名状态；补非空迁移、原请求恢复、来源拒绝、实际非零候选消费者及写后 SQL 回滚证据。不增加候选排除权限、跨来源新权限、生产决定能力或删除能力。

退出标准核对：非空迁移保留原键/正文/回执及暂停状态；停止/上传丢回执与重开恢复通过；缺失/伪造/跨仓来源拒绝且不误占；21 条非零候选由独立 Python 算法消费真实 HTTP/原始 SQL 事实；Android 停止、传输计划与回执进度 SQL 失败回滚通过，服务端五阶段精确 SQL 故障的 18 表守恒与原键恢复通过；Android/服务端受影响全套、APK/lint 和续作集成通过。满足本批退出标准，待明确路径提交并推送开发分支后记录回执。

自审：原回执不改写、不重签，不从 PARTIAL 升级完整停止资格；签名、来源/仓库/身份/引用校验保留，所有能力仍关闭。本批只涉及既有状态持久化/消费与验证缺口，没有新路径、迁移或生产开关变化。作者自审及生成身份的隔离组合验证不能替代物理设备、原身份、现场和生产批准。此前所有失败轮、库和日志继续保留。

交付准备快照为 `.local/autonomous-20261005/b3-delivery/`，将保存明确暂存路径、完整 diff、敏感项排除、受保护哈希、提交/推送及远端核对。下一批 B4 先重新 B0，再盘点处置、跨来源续作与成功链，复用既有实现和证据。
