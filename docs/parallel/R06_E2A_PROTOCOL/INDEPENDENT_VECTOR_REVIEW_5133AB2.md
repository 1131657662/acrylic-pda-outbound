# W1 E2a 生产向量独立消费记录

审查对象：W1 完整生产基线 `5133ab21a3302d53dd7d9cf362f0018b4e25777f`（包含向量生成提交 `1374da535b204a6ec918573131475451b5717775` 的空集向量及 V35 引用完整性修订）。输入文件为：

`server/src/test/resources/e2a/e2a-v1-empty-set-vector.json`

本记录仅报告协议树的独立 Python 消费；未启库、未运行 Gradle/HTTP/文件写/K 服务/设备或决定入口。输出来自一次性只读命令，未把 W1 作者测试结果冒充独立结果。

## 正向重算

使用合同域前缀 `UTF8(domain + "\\n") || coreUtf8`，逐字段重算得到：

| 项目 | 向量值 | 独立结果 |
|---|---|---|
| page hash | `fc36c7cb01e57aa6fb9876109ce1447f41e0e2dc3bd82d92c2e7fac22be95934` | PASS |
| manifestBytesHash | `0f847400b1735e9b21f848b4c87b4015fa86f73fbacd9f55cb9dada454ba5923` | PASS |
| setHash | `34b4620369483cb613f81cc4730c85bf6af1962868429cf3a7a364dd9ae0cb0f` | PASS |
| EC SPKI DER length | 91 bytes | PASS |
| `SHA-256(SPKI DER) = ecKeyId` | `1ff9da44cb1c2c20bc921a996bb2e6d4d5a4ed53313df31a889a17091d0c43f4` | PASS |
| STOP signature | DER/base64, 72 decoded bytes | `ECDSA_P256_SHA256_DER` verify PASS |

The signature input was exactly `ACRYLIC-R06/E2A/STOP/1\\n || stopCoreUtf8`; no manifest/server response bytes were included. The independent SHA-256 of this input was `9543f3953bf52e361624c8b739ddfd8bfe15249a2f6d728c7e13445281696ca9`.

独立命令完整 stdout：

```text
page_hash_match True
manifest_hash_match True
set_hash_match True
spki_der_len 91
keyid_match True
stop_sig_valid True sig_der_len 72
mutated_core_rejected True
derived_in_core_rejected True
der_not_raw_jose True
```

## Negative checks

The following were performed in memory against the same vector:

- Mutating `setHash` in `stopCoreUtf8` caused ECDSA verification rejection.
- Injecting a derived `setHash` field into `manifestCoreUtf8` produced a hash different from the frozen `manifestBytesHash`, so derived fields cannot be written back into the core.
- The decoded STOP signature begins with DER SEQUENCE `30 46` and is 72 bytes, therefore it is not a JOSE raw 64-byte `r||s` signature.

These are protocol-consumer checks only. They do not establish receipt persistence, V35 migration execution, trusted occupancy, `VERIFIED_COMPLETE_AT_STOP`, or evidence acceptance.

## Cross-review note

The latest W1 V35 revision `5133ab2` atomically enforces all-or-none object/set/verification references. The consumer review found no additional wire/hash/signature or no-cycle blocker. Remaining independent work is the wider negative matrix (duplicate/out-of-order pages, mixed manifest/snapshot, unknown historical key, RSA re-sign with mismatched facts, and limits) once corresponding fixed fixtures are supplied.
