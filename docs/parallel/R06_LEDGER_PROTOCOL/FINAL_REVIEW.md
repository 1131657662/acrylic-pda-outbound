# C1b 固定生产独立协议验收

生产输入固定为 W1 提交 `05ed1d3c4f121d9462d0077bd607c130f64cf562`（远端 `codex/r06-ledger-runtime`，37 文件，树干净）。W3 以合并提交 `9fe75e590f9b015507b6a1b45628a800aa165e7c` 接入该固定版本；生产源码没有在 W3 树改写。

## 实际动态结果

W3 直编译真实生产 `TemporaryProtocol`、`CommandReceiptCodec`、`CommandReceiptShape`、`CommandSignature` 与所需依赖，编译输入由 [source_manifest.json](source_manifest.json) 绑定到固定 SHA 的 LF 字节摘要；源清单共 16 个实际编译生产文件。Java 生成器使用临时 RSA-2048 私钥，运行后删除私钥目录，不读取数据库、服务器签名资料或设备资料。

运行 `python -W error::ResourceWarning docs/parallel/R06_LEDGER_PROTOCOL/freeze_vectors.py --freeze-fixed-production-vectors` 实际生成并独立核验：

- 4 个实际生产 codec 回执：Web/PDA 操作者、无正文、仅声明摘要、已接收正文及短装合成输入；均为 `INTERNAL_TEST_ONLY`、`businessDecision=null`、全能力 `false`。
- 47 个合法 RSA 重签负向：可信 actor、原操作员、来源、mapping、设备/session、权限/角色快照、版本、时间、状态、路由、能力等被替换后，独立 facts 消费者拒绝；签名本身仍可合法验证的样本与密码学层分开。
- 4 个 Node 独立 canonical receipt 字节/摘要向量，与 Python 预期和 Java 实际 unsigned bytes/hash 一致。

随后运行 `python -W error::ResourceWarning -m unittest discover -s docs/parallel/R06_LEDGER_PROTOCOL -v`：**68 项通过，0 失败、0 错误、0 跳过，2.755 秒**。其中 strict shape 的 33 个正/负场景、密码学/用途和负向场景均是该 68 项中的子场景，不能与 68 再相加。测试同时覆盖原始 UTF-8/摘要、尾部字节变化、损坏签名、未知 kid、错 issuer/audience、token 内外不一致、Web null 设备字段、PDA actor 字段、版本字符串/浮点/溢出、mappingVersion=0 与 currentEffect 独立性。

## 作者交付只读复核

[author_review_manifest.json](author_review_manifest.json) 逐项绑定固定 SHA 的 37 个作者交付文件和 5 份 XML 证据摘要。W1 记录的 17 单元、9+2 作者/边界 IT、JAR、OpenAPI 0.30/120 路径与 XML 一致；首轮 9 IT 失败（权限夹具及 JDBC 时间类型）和修后复跑均保留。W3 未重跑作者 Gradle、数据库、MockMvc 或真实 HTTP。

W1 的生产边界、request/receipt/event/audit 同事务、原键回放、原 BLOB、PDA 重新登录、坏 BLOB、无生产 participant 等结果仅作为作者证据复核，不转写为 W3 动态结果。W1 19326 已正常停止；原作者数据库、密钥、设备资料仍在 ignored 路径，不进入 Git。

## 限制与未覆盖

本验收证明的是固定生产源码的协议字节、严格 shape、签名用途及独立可信 facts 消费。它不证明 W2 负责的 MySQL 非空迁移、双连接锁等待、唯一键竞争、故障全回滚、历史身份 SQL 闭环或真实新会话。也不证明真实 HTTP 丢响应、Android 耐久、E2a 证据写、七类业务决定、记账、放行、R09 恢复/fence 或现场设备验收。

历史 Web 完整角色/权限快照和传输分类在生产中是锁内可信写者的签名断言，暂无独立历史表重建；PDA 历史 session 复核保留用户/设备/版本/仓库及原时间窗口，但不单独证明当时全部实时授权。未知历史 kid 继续 fail-closed，未实现历史 key ring。所有能力仍为 `false`，不因本验收开放生产 POST 或决定 handler。
