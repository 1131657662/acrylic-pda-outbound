# W1 接口卡定向早审：`2e4e660`

审查对象：`r06-e2a-foundation/docs/parallel/R06_E2A/INTERFACE_CARD.md`，基线为 W1 提交 `2e4e660`。本审查为只读协议审查，未修改 W1 文件、未运行生产 codec/Gradle/数据库/HTTP。

## 结果

| 范围 | 结果 | 依据 |
|---|---|---|
| wire 常量 | 通过（修订后） | schema/route、JWT issuer/audience、RS256 base64url、SPKI/keyId 与合同一致；W1 已将 STOP 算法固定为 `ECDSA_P256_SHA256_DER` |
| canonical | 通过 | lexical key order、array order、duplicate/BOM/UTF-8/lone surrogate/floating/safe-int 拒绝边界已明确 |
| page/manifest/set | 通过 | 派生 hash 不回写 core；page→manifestCore→派生摘要→STOP 单向，无环约束和容量边界已覆盖 |
| STOP/EC | 通过（修订后） | P-256、SHA256withECDSA、DER、标准 base64、SPKI/keyId 与合同一致；接口卡明确不使用 JOSE `ES256` raw 编码 |
| RSA receipt/facts | 通过 | RS256、JWT 结构及 unknown key / 合法重签错 actor/source/version/facts 的负向边界已列出 |
| intake seam | 通过 | INSERT-only、自然键、复合父键、组内 nullable CHECK、currentEffect 分离与合同一致 |

## 必须澄清的单项

W1 在修订提交 `2e4e660` 中已将 `stop.algorithm` 固定为 `ECDSA_P256_SHA256_DER`，并明确其不等同 JOSE `ES256` raw `r||s`；该项早审阻断已解除。固定生产向量仍是独立消费的前置条件，尚未生成或宣称签名向量通过。

## 下一步

1. W1 回填无歧义 STOP algorithm 值，并固定至少一组 DER/base64 正向与 raw `r||s`/JOSE 负向向量。
2. W3 按 `CONSUMER_MATRIX.md` 先重算 C01–C06，再消费 C07–C13；每项保存原始 bytes、长度、小写 SHA-256、完整 stdout/stderr 和退出码。
3. 任何 wire/canonical/hash/signature/facts 差异直接回报 W1 与主协调；本树不改生产实现。
