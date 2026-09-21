# R06 第五轮生产协议基础独立消费者

本目录由 W3 在 `codex/r06-foundation-protocol` 独立树维护，起点 `85cb7cef02fb8728599b6801a28824183305dab8`。它消费 W1 唯一生产协议实现；生产代码、Flyway、API、Android、原 C0 提案及旧向量均不在本目录修改范围。W1 的固定提交、实际运行结果及失败见 [交接记录](HANDOFF.md)。

## 验收层次

1. [生产适配器](FoundationProtocolDriver.java) 直接调用 `TemporaryProtocol` 的规范字节、命令结构和三个摘要域接口，运行时编译真实生产源码；没有替身实现、Spring、数据库或网络。
2. [Python 消费者](canonical.py) 自行逐字符编码 JSON，按 UTF-16 排键，用 SHA-256 检查生产输出；65 份新规范向量覆盖全部控制字符、字面反斜杠、无 Unicode 归一化、null/缺字段、安全整数、严格 UTF-8、孤立代理和重复键等。
3. [固定摘要](hash_vectors.json) 对正常空理由、nullable、处置、续作、合成回执和派生控制分别保存规范全文及 Node 独立计算的摘要常量。常规测试不重生成预期。
4. [合成签名](signed_vectors.json) 由现有真实 `OfflineSigner` 和本目录窄驱动生成。仅含公开临时测试公钥/签名；生成器的新 RSA 私钥只写系统临时目录，finally 删除，不读取任何业务签名文件。

本批生产 `receipt/control` 只有严格 JSON 对象与域 hash，没有回执 DTO、可信 actor/权限/原事实绑定或命令签发服务。合成回执外形仅为消费者反例容器，不是另一份生产协议定稿；其中 source、actor、commandId 与 receiptId、E2 全字段变更仍带有效 RSA 签名，用来证明独立消费者不能只验签而忽略调用方给定的原上下文。调用方上下文是本机合成测试输入，不来自真实服务器授权，不可上传以自授权。

[生产源码清单](source_manifest.json) 绑定 W1 精确提交和实际编译的九份生产源码 SHA-256；Git 源码按 LF 固定，允许 Windows 工作树 CRLF 换行。最终本机证据另保存当次工作树原字节 SHA，源码变化须显式重新复审，不静默更新预期。

## 固定合同

协议版本 1，route scope `r06.command.v1`，签名用途常量 `acrylic-r06-command-receipt-v1`；issuer `acrylic-outbound`。摘要域为 `ACRYLIC-R06/COMMAND/1\n`、`ACRYLIC-R06/RECEIPT/1\n`、`ACRYLIC-R06/DERIVED-CONTROL/1\n`，末尾为单个 LF 字节。原响应摘要直接散列原 UTF-8 字节，不规范化、不要求其是 JSON。

七命令 exact schema 明确包含九个 E2 字段，全部参与命令摘要。普通五族这些字段为显式 null；PHYSICAL_DISPOSITION 绑定证据/验证及处置清单，REOPEN_WORK 绑定证据/验证及保留清单，另一类清单为 null。缺字段、错类型、不适用字段非 null、零物理验证版本均拒绝。`NORMAL_CONFIRM.reason=""` 合法；理由不 trim、不归一化。`4294967297` 的 commandVersion 不得截断当成版本 1。

root null 可被通用 canonical 处理，命令/receipt/control 要求对象；输入及规范输出上限为 256 KiB，理由最多 1000 UTF-16 代码单元。原 ABORT 正文和签名不重写，派生控制不冒充 V33 当时已签证明。原 C0 `*-proposal` audience、新 audience 和既有出库成功 audience 分开核验；未知 kid、错 issuer/audience、签名损坏、非规范 Base64URL、内外层不一致和原响应空白变更均拒绝。

## 运行

依赖 Python 3.12+、cryptography 48.0.0、JDK 21；只读取既有 Gradle 缓存中 Jackson 2.21.4/annotations 2.21、Spring 6.2.19 的明确 jar。缺精确依赖直接失败，不自动下载。`JAVA_HOME` 指向 JDK 21；默认消费同仓生产源码，未整合时可用 `FOUNDATION_SOURCE_ROOT` 明示 W1 冻结树。全部离线测试：

```text
python -W error::ResourceWarning -m unittest discover -s docs/parallel/R06_FOUNDATION_PROTOCOL -v
```

[显式向量生成器](freeze_vectors.py) 仅供经协调的夹具修订：`--regenerate-synthetic-fixtures` 会覆盖本目录两份公开向量，运行后必须独立复审；常规验证不会调用它。Node 只在主动重建固定摘要时需要。

本批没有 MySQL/HTTP/设备测试，也不证明真实原键台账、事务原子性、原机停止完整性、实物处置、权限竞态、Android 耐久恢复、最终确认放行或 R09 fence 已实现。localDeletionAllowed 始终不因验签而变真。
