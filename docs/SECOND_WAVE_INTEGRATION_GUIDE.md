# 第二轮独立整合记录

日期：2026-09-20。工作树 `D:/acrylic-pda-worktrees/second-wave-integration`，分支 `codex/second-wave-integration`，起点为开发分支已冻结的 `f621160baed0a8ce7ed9432551475fffe20451ad`。

## 合入范围

以 `merge --no-ff --no-commit` 分别合入以下已提交交付，均无文本或业务冲突，保留原提交历史。

| 交付 | 作者提交 | 本树合并节点 |
| --- | --- | --- |
| R06 只读重复正式单候选与计划对照 | `df1a6168b58fc12cc7c2b6a77afbb04a7232ee2d` | `4aa67c1` |
| R06 写阶段锁图、原命令与来源证据设计 | `71a78ba1baccb83c5ead7a673e813c5b150dc611` | `54e3598` |
| R09 V33 临时提交快照核验与专用夹具 | `e348c52dfc5280c29a81b09e52aee6ac0a16d556` | `aa06fd0` |

候选功能见[作者交付](R06_DUPLICATE_CANDIDATES_GUIDE.md)。设计包含[锁图](parallel/R06_M2B/LOCK_GRAPH.md)、[命令证据契约](parallel/R06_M2B/COMMAND_EVIDENCE_CONTRACT.md)和[交接](parallel/R06_M2B/HANDOFF.md)，不表示写锁阻断已关闭，也没有新增写接口或迁移。

本次整合专属改动仅为 OpenAPI 总索引、已实现接口说明和本指南。正式挂载 `GET /temporary-reviews/{id}/duplicate-candidates`，引用 `./temporary-duplicate-paths.yaml#/Candidates`，统一为 **API 契约0.28.0、113条路径**。候选要求当前 `temporary_order:confirm` 与同仓授权，只读且不需要 `order:read`；默认日期、错误码、上下文变化重查及恒 false 写入/关联/放行字段保持作者契约。

R09仅以明确提交 `e348c52` 作为合入来源，不消费其他未提交成果；未改主目录管理文档及既有电子表格原件。Flyway V33、Android code20/Room17、server/web 工程0.1.0保持，不能与API契约版本混称。

## 内容一致性与验证依据

候选业务、测试及片段共19文件与作者冻结原始字节一致，组合 SHA-256 为 `62675dec0f4f0d14451f48934251de98eec7b07785bbac1cfc48edc23369d063`。计算方式为按相对路径排序、每行 `path<TAB>SHA256(raw bytes)<LF>`，保留尾部LF后对UTF-8整体求SHA-256。清单位于本树 ignored `.local/second-wave-integration-20260920/frozen-manifest.txt`。

首次原始指纹检查在 `TemporaryReviewPage.vue` 中止：作者文件含78个CRLF、7194字节，新树为LF、7116字节。核对规范化文本与Git内容相同后，恢复作者冻结的原始字节，再次检查19文件全部一致。该页Git内容摘要仍为 `82aa49feb15171613946c39af61d4474f0264b27`，无业务修改；未改仓库或全局Git配置。候选范围外的运行源码与起点 `f621160` 一致，作者候选源码与整合提交的Git差异为空。

因此R06整合按总调度批准的范围只执行下列必要检查，没有机械重跑59项IT、35项后端单元或未受影响的101项报表测试。作者59项MySQL/MockMvc、35项单元、JAR及此前22项Web通过均属于 `df1a616`，不是本树重新执行的后端证据；其首轮编译/字体、夹具与Web类型失败完整保留在作者交付及原日志中。设计作者的UTF-8/87链接检查属于 `71a78ba`。

## R06整合树实际执行

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

## R09作者证据与本树限定验证

R09合入范围仅 `docs/parallel/R09/` 与 `server/src/test/java/com/acrylic/outbound/inventory/R09V33FixtureIT.java`，共20文件。全部Git内容与已审提交 `e348c52` 一致，换行差异不冒充实现差异。生产源码、构建配置、契约和五份管理文档相对 `6852da9` 未变；无业务冲突，不重跑未受影响的59项IT、35项单元、22项Web及101项报表测试。

作者验证见[V33恢复指南](parallel/R09/V33_RECOVERY_GUIDE.md)及[脱敏证据清单](parallel/R09/VERIFICATION_V33.json)，其业务基线固定为 `90581375128ee426fcf979a76eb44f4bb7734fef`：

- 作者71项Python检查通过；最终夹具的第二份新lab实际18项MySQL/MockMvc通过，JAR与inspect通过。这是作者已启用夹具的正向证据。
- 第一份lab完成93表恢复、201组外键和35处文件引用核对，最终17项损坏拒绝及全部原值检查通过。第一份数据使用加门控前夹具，与最终第二份lab的夹具字节分别记录；未把两者写成同一轮。
- 原字体等待中断、普通测试隔离修正、错误库验证首次命中缓存、数字原值及Flyway恢复条件两次故障均保留在作者指南。原值已按作者记录恢复并最终核对，整合不重跑这些实验，也不覆盖其失败记录。

本树新增验证仅如下：

| 检查 | 实际结果 | 本树证据 |
| --- | --- | --- |
| `python -W error::ResourceWarning -m unittest discover -s docs/parallel/R09/tests` | 71项通过，3.614秒，ResourceWarning作为错误 | `r09-python.log` |
| `gradle -p server integrationTest --tests com.acrylic.outbound.inventory.R09V33FixtureIT --console=plain --rerun-tasks --no-daemon --max-workers=2 -Pr08FontFile=<已校验字体>` | 编译成功，实际10项全部跳过，0失败/错误；8个任务实际执行，17秒 | `r09-disabled-fixture.log`、`r09-disabled-fixture.xml` |
| `npm run check:contract` | R09合入后正式113条路径再次通过，契约保持0.28.0 | `openapi-after-r09.log` |

命令使用既有Python依赖环境和Gradle8.11.1/JDK21，只复用依赖资产。Gradle进程内移除 `OUTBOUND_*`、`SPRING_*`、`R09_*` 以及额外Java/Gradle选项，明确未设置 `R09_V33_FIXTURE`；没有注入旧数据库配置。字体SHA-256为 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`，沿用现有参数和校验，不绕过构建检查。JUnit XML核对10个skipped且无失败/错误；日志及XML未出现Spring上下文或Hikari/数据库初始化。

这次10项跳过只证明普通禁用环境的编译与隔离，不是10项恢复业务通过。本树没有新建lab、运行seed、启动MySQL/API/Worker或执行恢复/故障注入，也没有实际验证最新候选API下的组合恢复。R09 seed固定基线仍为9058137，当前整合树已增加候选源码，不应直接运行该固定基线seed。V33工具支持受控快照核验，不等于完整灾后恢复API、切代、解除屏障或整单清理。

## 未完成及后续

- TEMP写确认、短装/冲突处置、候选排除、受控关联、拒绝实物处置、共享记账/签名成功及归档仍未实现。空候选或空blockers不能用于放行。
- 写阶段须完成设计中的真实并发验证。候选完整版本摘要的大范围性能尚未压测；真实HTTP/浏览器/设备、Wi-Fi/异地/长时及现场未验。
- R09现支持V33受控快照检查，仍不接TEMP成功/拒绝、完整灾后恢复及清理；正文后取消不能由当前快照证明取消前全部占用，FH当前MAX也不代表截点外高水位，无新增共享恢复API。
- R06契约与整合记录提交 `6852da9` 已先快进合回并推送 `codex/development`，看板及 `docs/out.xlsx` 原字节摘要前后一致。R09追加整合保存并推送后，等待总调度冻结管理文档并再次派工才合回；没有合入 `main`、发布、部署或设备安装。
- 原库、原恢复断点、AVD/M71、现网和审批阻断保持；本轮未启动API、Web、Worker或设备。
