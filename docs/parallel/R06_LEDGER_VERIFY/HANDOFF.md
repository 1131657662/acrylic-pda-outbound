# 第六轮 C1b 独立数据库与故障验收

W2 `/root/foundation_migration_verify`；新树 `D:/acrylic-pda-worktrees/r06-ledger-verification`，分支 `codex/r06-ledger-verification`，固定起点 `82a2e13cac306f877ed5e2c887970adf3d8e57f0`。旧第五轮树、19324 和旧资料只读保留。

本批只写 `server/src/test/java/com/acrylic/outbound/ledgerverify/**`、本目录和新增测试桥 `inventory/LedgerVerifySeed.java`。生产、Flyway、OpenAPI 与作者测试由 W1 独占；W2 不派下级、不合 development/main、不部署。本页当前为准备状态：测试桥已编译，首批15项环境护栏与r2新增5项护栏分别通过；两次准备IT均失败，尚无C1b运行器验收通过。

## 待验矩阵

| 组 | 独立预期与实际证据要求 |
| --- | --- |
| 原键 | 同 actor/scope/key 同文只产生一次原记录，异文冲突；不同 principal 同 commandId 使用不同服务器 receiptId；核原 bytes 而非重编码 DTO |
| 版本竞争 | 新 key 同 decisionVersion 两连接竞争，只一个合成内部效果提交；输家无 request/receipt/event 残留。不是七类真实决定已实现 |
| 两次优先回放 | 初查与锁后复查命中都先于写资格/expected；已提交后撤写权但留读权、改变 expected 现状，仍返回原 bytes，不追加签名或审计 |
| 权限/会话 | 当前身份、仓库、角色及精确会话集合变化用真实两连接及锁等待证据；无读权、换人、异仓不泄露原键存在性 |
| 事务故障 | request、版本/效果、receipt、event、audit 各阶段及签名边界异常全部回滚；保留原事实。不持久伪 RUNNING，不在失败事务续写 |
| nullable 来源 | 完整 TEMP、A 无正文 ABORT、B 部分块 ABORT、C 完整后 ABORT。新台账不改旧 S/receipt/payload/upload/null，不创建订单/S/assignment 或放行 |
| 原字节与完整性 | request/resource/receipt 双向不符、坏 BLOB、坏摘要、错误 token/外层、缺边、未知 kid 均失败关闭，不修补或重签；另用本轮合成可信私钥合法重签错误字段类型/actor Web-PDA session-device 绑定，验证生产读取拒绝，不能只引用 W3 独立消费者拒绝 |
| 重新登录 | 新合成设备 EC 私钥只存 ignored；真实 challenge/login 换 session 后本人原键可读，回执旧 session 保留。不修改数据库鉴权/令牌/时间解决旧 seed 401 |

W1 最小卡已明确 `TemporaryCommandService.execute(byte[],requestId)`、测试专用 NORMAL_CONFIRM/SHORT_APPROVE 操作、4 个 Web/PDA GET（含原 byte[]）和 false 能力。历史 mapping 应核原不可变事件，不能用当前 canonical 替代；历史权限快照也不能直接等同当前权限，当前多原因效力另算。只核当前 schema 可证的稳定归属及原 event/audit 闭合，不为验收新造权限历史 schema。

当前缺口：等待 W1 固定内部接口具体签名及生产 SHA；最终验收只消费固定提交，未提交源码的预审不记通过。生产决定 handler 与 POST 新执行保持关闭，E2a 证据写本批不落。NORMAL_CONFIRM 空理由及 localDeletionAllowed=false 保持；R09 V34 恢复、旧 seed GET401、真实 HTTP 丢响应和设备耐久行为未验。

## 环境约束

唯一实例 `127.0.0.1:19327`，datadir 固定本树 `.local/mysql/data`。初始库 `acrylic_outbound_ledger_verify_20260921`、账号 `outbound_ledger_verify_20260921`、签名/私有文件/证据保持原样；该库仍 0 表。主调度随后分配 r2：库 `acrylic_outbound_ledger_verify_20260921_r2`、账号 `ledger_verify_r2_20260921`、`.local/private-files-r2`、`signing-private-r2.der`/`signing-public-r2.der`、`evidence-r2`、`seed-keys-r2`。当前夹具只接受精确 r2 配置；端口、库、账号、datadir、密钥和存储交叉检查，无前缀放行。单次种子要求空 schema 和不存在本次 marker；默认禁用。

| 运行 | 实际结果与保留 |
| --- | --- |
| seed-first | Java/测试编译通过；prepareFont 下载在约10分钟后 SocketTimeoutException；尚未跑测试。原日志/线程快照保留。 |
| seed-second | 使用已核SHA256字体；15护栏单元通过，1准备IT失败。默认 OUTBOUND_MIGRATE=false，查询本空库 flyway_schema_history 不存在；写入 seed-started 后、构造业务种子前失败。不是V34迁移执行失败或C1b事务失败。日志/XML/marker及0表库保留。 |
| r2 seed-first | 新增5护栏通过，V1→V34初始化成功；首complete经既有真实MockMvc API生成身份、准备、S与原正文。快照工具仍引用旧 private-files，NoSuchFileException，故1准备IT失败；尚未执行真实重新登录，尚未建立完整原值快照。日志/XML/marker/新库/密钥/文件保留。 |

快照目录已修到精确 r2。原单次 LedgerSeedIT 不重跑，不删marker、不建r3。随后对现有complete只读定位并在明示时点建立保留基线；不能声称第一次失败之前已有原值前后核对。其余场景由固定C1b后的独立用例追加新材料。每个验收用例使用独立真实来源，不修改旧身份/鉴权解决旧seed401。结束精确停19327并保留全部材料；原13316、19321–19326及其他实例不动。重型槽当前归W1，后续实际运行先协调。
