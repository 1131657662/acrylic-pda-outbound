# 第二轮独立整合记录

日期：2026-09-20。工作树 `D:/acrylic-pda-worktrees/second-wave-integration`，分支 `codex/second-wave-integration`，起点为开发分支已冻结的 `f621160baed0a8ce7ed9432551475fffe20451ad`。

## 合入范围

以 `merge --no-ff --no-commit` 分别合入以下已提交交付，均无文本或业务冲突，保留原提交历史。

| 交付 | 作者提交 | 本树合并节点 |
| --- | --- | --- |
| R06 只读重复正式单候选与计划对照 | `df1a6168b58fc12cc7c2b6a77afbb04a7232ee2d` | `4aa67c1` |
| R06 写阶段锁图、原命令与来源证据设计 | `71a78ba1baccb83c5ead7a673e813c5b150dc611` | `54e3598` |

候选功能见[作者交付](R06_DUPLICATE_CANDIDATES_GUIDE.md)。设计包含[锁图](parallel/R06_M2B/LOCK_GRAPH.md)、[命令证据契约](parallel/R06_M2B/COMMAND_EVIDENCE_CONTRACT.md)和[交接](parallel/R06_M2B/HANDOFF.md)，不表示写锁阻断已关闭，也没有新增写接口或迁移。

本次整合专属改动仅为 OpenAPI 总索引、已实现接口说明和本指南。正式挂载 `GET /temporary-reviews/{id}/duplicate-candidates`，引用 `./temporary-duplicate-paths.yaml#/Candidates`，统一为 **API 契约0.28.0、113条路径**。候选要求当前 `temporary_order:confirm` 与同仓授权，只读且不需要 `order:read`；默认日期、错误码、上下文变化重查及恒 false 写入/关联/放行字段保持作者契约。

未读取或合入 R09 本轮未提交文件，未改主目录及其管理文档、既有电子表格原件。Flyway V33、Android code20/Room17、server/web 工程0.1.0保持，不能与API契约版本混称。

## 内容一致性与验证依据

候选业务、测试及片段共19文件与作者冻结原始字节一致，组合 SHA-256 为 `62675dec0f4f0d14451f48934251de98eec7b07785bbac1cfc48edc23369d063`。计算方式为按相对路径排序、每行 `path<TAB>SHA256(raw bytes)<LF>`，保留尾部LF后对UTF-8整体求SHA-256。清单位于本树 ignored `.local/second-wave-integration-20260920/frozen-manifest.txt`。

首次原始指纹检查在 `TemporaryReviewPage.vue` 中止：作者文件含78个CRLF、7194字节，新树为LF、7116字节。核对规范化文本与Git内容相同后，恢复作者冻结的原始字节，再次检查19文件全部一致。该页Git内容摘要仍为 `82aa49feb15171613946c39af61d4474f0264b27`，无业务修改；未改仓库或全局Git配置。候选范围外的运行源码与起点 `f621160` 一致，作者候选源码与整合提交的Git差异为空。

因此本轮按总调度批准的范围只执行下列必要检查，没有机械重跑59项IT、35项后端单元或未受影响的101项报表测试。作者59项MySQL/MockMvc、35项单元、JAR及此前22项Web通过均属于 `df1a616`，不是本树重新执行的后端证据；其首轮编译/字体、夹具与Web类型失败完整保留在作者交付及原日志中。设计作者的UTF-8/87链接检查属于 `71a78ba`。

## 本树实际执行

执行前在本树 `web/` 运行 `npm ci --ignore-scripts --no-audit --no-fund` 成功，未变更依赖锁。将作者已验证的两份实际MockMvc响应复制到本树 ignored `server/build/`，复制后原始SHA-256相同：

| 响应夹具 | SHA-256 |
| --- | --- |
| `temporary-duplicate-contract.json` | `3ac77c6b8b3f87c62e56eb385a4795d2e6c8524bc9e38973576b7e22ab5e7e10` |
| `temporary-review-contract.json` | `6c018a8c3639e5accac6be431ecf8abaf8768fb7b6b0538d1cbe8f8cc53c2172` |

这些响应来自 `df1a616` 的验证；本轮未创建、初始化或读写数据库，未重新生成后端响应，不将复制夹具描述为本轮后端联调。

| 检查 | 本树结果 | 本树证据 |
| --- | --- | --- |
| `npm run check:contract` | 正式OpenAPI3.1.0的113路径通过 | `openapi.log` |
| `node --experimental-strip-types --test tools/temporary-review.test.mjs tools/temporary-duplicates.test.mjs tools/temporary-duplicate-state.test.mjs` | 22项通过，0失败/取消/跳过：11候选契约、4Vue状态、7原审阅契约 | `web-tests.log` |
| `npm run build` | Vue类型检查及Vite生产构建通过；保留既有大chunk提示 | `web-build.log` |

上述日志在本树 `.local/second-wave-integration-20260920/`，不入Git。限定检查首轮均通过；先前换行指纹中止单独如实记录，不算业务测试失败，也没有删除作者失败证据。当前没有业务冲突或新增运行实现，故未扩大测试范围。

## 未完成及后续

- TEMP写确认、短装/冲突处置、候选排除、受控关联、拒绝实物处置、共享记账/签名成功及归档仍未实现。空候选或空blockers不能用于放行。
- 写阶段须完成设计中的真实并发验证。候选完整版本摘要的大范围性能尚未压测；真实HTTP/浏览器/设备、Wi-Fi/异地/长时及现场未验。
- R09 V33恢复工具属于另一独立批次，本次仍保留已合入的V32工具边界，无新增共享灾后恢复API。
- 本阶段保存并推送整合分支后，等待总调度冻结管理文档并再次派工才合回 `codex/development`。没有合入 `main`、发布、部署或设备安装。
- 原库、原恢复断点、AVD/M71、现网和审批阻断保持；本轮未启动API、Web、Worker或设备。
