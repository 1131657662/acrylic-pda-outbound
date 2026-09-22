# W1 接口卡定向早审：`2e4e660`

审查对象：`r06-e2a-foundation/docs/parallel/R06_E2A/INTERFACE_CARD.md`，基线为 W1 提交 `2e4e660`。本审查为只读协议审查，未修改 W1 文件、未运行生产 codec/Gradle/数据库/HTTP。

## 结果

| 范围 | 结果 | 依据 |
|---|---|---|
| wire 常量 | 有条件通过 | schema/route、JWT issuer/audience、RS256 base64url、SPKI/keyId 与合同一致；STOP 算法命名需消除 JOSE 歧义 |
| canonical | 通过 | lexical key order、array order、duplicate/BOM/UTF-8/lone surrogate/floating/safe-int 拒绝边界已明确 |
| page/manifest/set | 通过 | 派生 hash 不回写 core；page→manifestCore→派生摘要→STOP 单向，无环约束和容量边界已覆盖 |
| STOP/EC | 有条件通过 | P-256、SHA256withECDSA、DER、标准 base64、SPKI/keyId 与合同一致；算法字段不应把 JOSE `ES256` 与 DER wire 编码混称 |
| RSA receipt/facts | 通过 | RS256、JWT 结构及 unknown key / 合法重签错 actor/source/version/facts 的负向边界已列出 |
| intake seam | 通过 | INSERT-only、自然键、复合父键、组内 nullable CHECK、currentEffect 分离与合同一致 |

## 必须澄清的单项

接口卡当前写作：`STOP signatures use ES256/P-256 SHA256withECDSA; signature bytes are standard ASN.1 DER and are base64`。JOSE `ES256` 的常见 wire 定义是 raw 64-byte `r||s`，而合同明确禁止该形式并要求 `SHA256withECDSA` ASN.1 DER。为避免独立消费者误实现，建议把 `stop.algorithm` 固定为非歧义枚举，例如 `ECDSA_P256_SHA256_DER`，或明文规定 `ES256` 仅为曲线/摘要语义标签、不得套用 JOSE 编码；同步给出 algorithm 字段的精确 canonical 值和负向向量。

在上述命名澄清前，W3 将 EC 正向消费保持 `WAITING`，不生成或宣称签名向量通过。其他早审范围无硬阻断，收到修订接口卡及固定生产向量后再进入独立重算。

## 下一步

1. W1 回填无歧义 STOP algorithm 值，并固定至少一组 DER/base64 正向与 raw `r||s`/JOSE 负向向量。
2. W3 按 `CONSUMER_MATRIX.md` 先重算 C01–C06，再消费 C07–C13；每项保存原始 bytes、长度、小写 SHA-256、完整 stdout/stderr 和退出码。
3. 任何 wire/canonical/hash/signature/facts 差异直接回报 W1 与主协调；本树不改生产实现。
