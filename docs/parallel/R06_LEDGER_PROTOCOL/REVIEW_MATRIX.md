# C1b 独立协议与原回执读取验收矩阵

基线：`82a2e13cac306f877ed5e2c887970adf3d8e57f0`。本矩阵依据已审 [C1 设计](../R06_C1_LEDGER/DESIGN.md)、[事务合同](../R06_C1_LEDGER/TRANSACTIONS.md) 和 [验收合同](../R06_C1_LEDGER/MIGRATION_ACCEPTANCE.md) 准备。当前尚未取得第六轮固定生产 SHA 或实际回执结构，以下均为待验门槛，不是通过结论。第五轮 233 项只可作为已有基础回归。

W3 只写本目录的独立消费者、向量与审查材料；W1 唯一写生产，W2 负责数据库故障、权限并发及新设备真实会话。W3 不操作数据库、HTTP、设备或旧环境，Java 构建前与 W1/W2 协调。

## 1. 固定生产接口前必须明确

| 编号 | 待核合同 | 独立检查方式 |
| --- | --- | --- |
| P01 | `principal + r06.command.v1 + commandId` 是唯一原键；request.id、receipt.id 是服务器 ID | 复审原键精确查询和写入；不同 principal 相同 commandId 的数据库证明由 W2 提供 |
| P02 | 原命令规范字节、原响应原始 UTF-8 字节、无签名回执摘要分别保存 | Python 与 Node 独立计算实际结构字节/hash；加入只改变空白的原响应，证明 raw hash 不等同规范 hash |
| P03 | unsigned receipt 来自锁内服务器可信事实，actor 与 originalOperator 分离 | 实际生成器输入/数据库固定事实与输出逐字段对照；不将签名可验等同原事实可信 |
| P04 | Web 当前 device/session 为 null；PDA 当前设备会话与来源设备资格分别绑定 | 固定实际合法样本及逐字段替换负向；不从来源设备补出当前会话 |
| P05 | 权限快照包含本族实际所需权限、有序完整 grants/roles、仓库及适用设备/资格版本 | 审查锁内数据来源；合法 RSA 但错误 actor/原人/权限/会话的样本必须被可信事实对照拒绝 |
| P06 | source、canonical/slot/assignment、received/declared 独立表达，null 不等于缺字段 | 实际无正文 ABORT、部分块 ABORT、完整来源向量；W2 核数据库原值与文件保留 |
| P07 | request.resource_id == receipt.id，receipt.api_request_id == request.id，反向键/hash均一致 | 每次读取核对双方，不只依赖单向 FK；W2 注入损坏并保存 fail-closed 结果 |
| P08 | 原响应 byte[] 直接返回；签名内部 receipt 等于原外层去 signedReceipt | 对真实生成器/读取器测试，不只造一份消费者自签回执；不重新序列化原响应冒称原字节 |
| P09 | exact issuer/audience、RS256、已知 kid；未知历史 kid 不回退当前密钥 | 独立消费者验证错 domain/audience、损坏签名及可信私钥签出的错语义；历史 keyring 未实现须保留限制 |

## 2. 读取与事务复审

| 编号 | 待核合同 | 证据归属 |
| --- | --- | --- |
| R01 | 当前身份、原仓库和本人历史读资格实时核验；命令族取自存量命令 | W3 源码审查；W2 当前撤权、换人、跨仓、无原族写权但有 confirm 读权场景 |
| R02 | 两次精确原键查询均先于新写权限/expected；第二次在 E/G/排序 U/W/D/H 后 | W3 逐分支审查；W2 真实两连接证明锁前未命中、锁后已提交时仍返回原字节 |
| R03 | 历史读不要求旧 expected 仍当前或旧 session 仍有效 | W3 样本/读取分支检查；W2 保存新 EC 私钥并真实 challenge 重登，旧 session 字段不改 |
| R04 | 只读恢复能力与新写 E 门禁分开；旧 POST 不自动替换 epoch | W3 审查 RECOVERING/epoch 条件；W2 当前恢复状态、旧 epoch 与原键命中/未命中分组 |
| R05 | 同键异文冲突；未知/损坏/缺边不修复、不重签、不执行业务 | W3 实际读取器负向向量；W2 原值及 request/receipt/event 计数前后对照 |
| R06 | `currentEffect` 在原响应外，汇总全部当前不适用原因，能力全部 false | W3 实际 envelope 与消费者；W2 同时改变多个当前事实，原字节/hash/签名保持 |
| R07 | 旧 SUCCEEDED 是历史事实；当前失效不能回退原决定或产生放行权 | W3 审查状态投影与能力；本批生产七类决定 handler/POST 关闭，合成 handler 必须明示 |
| R08 | 事务 REQUIRED/READ_COMMITTED，原父锁后预留 request；失败全回滚 | W3 事务注解/调用边界审查；W2 故障点和真实原子结果 |
| R09 | 唯一异常整事务退出后另行只读恢复，不能在失败事务续写 | W3 异常捕获与事务边界；W2 同键同文/异文实际碰撞 |

## 3. 固定样本与结果纪律

固定向量必须注明生产 SHA、驱动器来源/摘要、实际生产生成器和读取器入口、合法样本可信输入，以及独立预期生成方式。消费者不能把被测 Java 输出直接复制为唯一预期。合法 RSA 错语义向量需要外部固定可信事实作对照，不能只检查签名位损坏。

本批分开登记：新回执结构/字节/签名消费者；生产可信事实来源的静态审查；W2 数据库与真实会话验证；旧 233 项基础回归。合成 handler、离线签名、MockMvc 不代表生产决定、真实 HTTP 丢响应、Android 耐久或现场放行。

当前尚无本目录动态测试结果。未完成的历史 keyring、HTTP/设备、E2a 证据写、最终业务决定和 Android 落盘继续保留；原 seed GET401 不被新 fixture 通过替代。
