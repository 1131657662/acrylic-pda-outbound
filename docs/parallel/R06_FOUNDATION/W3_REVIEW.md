# W3 独立协议消费者复审

2026-09-21，W1 对 W3 固定 `dbb0693a58cc4ad29e42d054a1ef0611bdf8f551` 只读复审；生产依赖 `0311dfaf4255671a605a17aa3defc145df9621a6`。范围限定其 21 个 `docs/parallel/R06_FOUNDATION_PROTOCOL/` 文件，没有修改 W3 工作树或运行其测试。

结论：没有发现当前离线消费者范围的整合阻断。与 W1 的生产字节基础一致，不能据此启用回执、证据或决定写接口。

- Java driver 直接编译并调用真实 `TemporaryProtocol`、`SubmissionJson`、`ImportFiles` 及 `OfflineSigner`，没有替代生产规范化/摘要实现。逐一计算固定0311dfa的九个Git blob SHA-256，全部与源码清单一致；运行材料另存实际工作树字节摘要，显式消费生产目录。
- Python 编码器自行逐字符处理控制字符和 UTF-16 键序，不导入 C0 或 Java 编码器；65 份静态输入预期与实际 Java 输出比对。6 份 Node 摘要固定在仓库，常规测试不运行生成器覆盖预期；Node 层验证摘要，不宣称独立定义业务规范。
- 七命令及九个 E2 expected 字段具有接受、遗漏、显式 null、不适用清单、零版本和变更摘要反例；正常空理由合法。`4294967297` 负向只能证明修后拒绝，交接正确说明修前没有动态红测。
- 29 份 token 由真实现有 OfflineSigner 对合成 claims 签发，私钥临时生成后删除；消费者使用受信公钥集合核 kid/RS256、issuer/audience、原响应摘要、内外层及调用方提供的合成原上下文。合法签名但错误 actor/source/receiptId/expected 的反例归因为上下文不符，没有写成生产授权服务已拒绝。
- 原响应字节与规范回执摘要分开；空白变更、错误 audience（含 C0 proposal）、未知 kid、签名损坏、内外层不一致等反例不互相混用。receipt/control 仅 object/hash、合成签名不等于可信真实业务回执的限制在 README/HANDOFF 中明确。

读取了作者 `.local/protocol-evidence/final-fixed-0311dfa.txt` 与 `final-summary.json`：最终233项，0失败/错误/跳过；188生产基础、6固定hash、38合成签名、1源码清单分项一致。首次176/36仅在途记录，不累计。此为作者证据核对，本复审未重跑 Python/javac/Gradle/MySQL/HTTP/设备。

台账原键事务、真实权限/可信语义、完整停止、原机耐久、签名业务成功和R09仍待后续实现；这些不在当前基础离线复审结论内。
