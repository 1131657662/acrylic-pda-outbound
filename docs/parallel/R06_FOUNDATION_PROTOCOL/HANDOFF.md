# 第五轮 W3 协议消费者交接

2026-09-21：本子项已完成固定生产提交 `0311dfaf4255671a605a17aa3defc145df9621a6` 的独立离线验收及只读源码复审。最终整套 **233 项通过，0 失败/错误/跳过**。本结论仅覆盖本目录范围，不代表第五轮非空升级、业务写或组合整合完成。

| 最终新跑范围 | 项数与证据 |
| --- | --- |
| 真实生产规范字节/shape/字段绑定/大小及新常量 | 188 项；65 份新静态 canonical 输入，完整七命令及 E2 字段反例 |
| Node 固定 hash → Python/真实 Java 新域对照 | 6 项；正常空理由、nullable、处置、续作、合成 receipt、派生 control |
| 真实 OfflineSigner 合成签名的独立消费者 | 38 项；29 份公开合成 token，包含合法签名但错误上下文及完整性反例 |
| 编译源码固定清单 | 1 项；九份生产 Git blob 的 LF SHA-256 与实际工作树一致 |

最终日志 `D:/acrylic-pda-worktrees/r06-foundation-protocol/.local/protocol-evidence/final-fixed-0311dfa.txt` 和 `final-summary.json`，运行 2.917 秒；Python 3.14.4、cryptography 48.0.0、JDK 21。`production-raw-source-hashes.json` 保存该次实际工作树原字节 SHA；仓库 [清单](source_manifest.json) 固定 LF 源码摘要。未运行 Gradle、数据库、HTTP 或设备。

- 分支 `codex/r06-foundation-protocol`，独立树 `D:/acrylic-pda-worktrees/r06-foundation-protocol`，基线 `85cb7ce`。
- 独占 `docs/parallel/R06_FOUNDATION_PROTOCOL/**`。生产规范由 W1 制定，本目录只读编译真实源码，原 C0 未修改或重跑，153 项历史成绩不计本轮。
- 第一次非冻结诊断运行 176 项，全通过，无失败/错误/跳过；证据 `.local/protocol-evidence/first-production.txt`。第一次合成签名检查 36 项通过，`signature-first.txt` 保留。两次均被上述最终 233 取代，不能累计。
- commandVersion 整数截断由主调度静态审查发现，W1 在本目录第一次编译前已改为 longValue 判断；独立 `4294967297` 负向通过，没有修前动态误收证据。
- receipt/control 是对象+域 hash 基础，无完整可信回执语义。合成签名样例不能算实际业务签发、台账或权限验证。
- 另受派三次精确保存主调度管理文件：`797efad`（26 新增行）、`aeb6fb2`（14 新增行）、`6fc4219`（15 新增行），均仅五文件并已推送 development；实际本地引用检查分别 204/205/209 条。第二提交正文误沿用 204，实际工具输出为 205，第三提交正文已登记更正而不改写已推历史。out.xlsx 原 SHA256 保持；未触碰其内容或暂存。

本子项没有动态测试失败；管理提交文字数字笔误已如实更正。W1 数据库/业务作者失败由其指南单列，不冒充本目录失败或通过。W1 固定源码/V34及补测最终 `04a679beb9b5e250d0c67101edb97fdf1c264fac` 独立审查见 [报告](W1_REVIEW.md)：没有发现当前基础范围阻断；29 个不同作者 IT、目标包精确投影补测 2 项及真实 G 等待证据均按原始 XML/JSON 复核，未重跑作者测试。

W2 最终 `8bdf1b87c3b18110c2551bdc89ff9c1f7d6728b3` 已完成 [独立复审](W2_REVIEW.md)，当前基础范围无整合阻断。原列/文件/Flyway 历史、五类负向与 10.59 秒 metadata lock、15 护栏、8 schema/70 新 fixture API 以及 19324 正常停机证据已核对；原编译、复制和旧 GET 401 失败保留，DTO 服务检查不能替代旧身份重新鉴权。复审建议中的种子唯一源库护栏、反例归因和锁等待绑定均已修正。此处是只读审查，非新增产品测试或组合结论。

主目录管理保存与本目录协议测试是分别授权的范围，不意味着 W3 可自行整合 development。本目录独立提交后等待主调度交叉复审和整合派工；不合 main、不部署、不恢复旧 B/C/D。
