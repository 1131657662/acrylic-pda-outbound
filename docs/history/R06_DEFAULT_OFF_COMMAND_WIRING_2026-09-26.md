# R06 默认关闭的命令执行接线（2026-09-26）

## 范围与结论

本批把"TEMP 决定命令执行"与"服务模式启动告警"接到生产装配上，但**默认行为一字未改**：未配置开关时，命令操作集合仍为空、`POST` 路由不存在，任何 kind 继续返回 `409 COMMAND_EXECUTION_NOT_IMPLEMENTED`。

业主当批明确选择"接线但默认关闭"，未授权开启。**本页不构成开启授权，也不把内部测试当作开启依据。**

## 新增/改动

| 文件 | 内容 |
|---|---|
| `server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryCommandConfiguration.java`（新增） | `@Configuration` + `@ConditionalOnProperty(prefix="outbound.temporary.commands", name="enabled", havingValue="true")`；按 kind 注册三个 `CommandOperation`：`NORMAL_CONFIRM`、`SHORT_APPROVE`（均 `TemporaryConfirmationOperation`）、`LINK`（`TemporaryLinkOperation`） |
| `server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryCommandController.java`（新增） | 同一开关下的 `POST /api/v1/temporary-commands`、`POST /api/v1/pda/temporary-commands`；请求体为规范命令 JSON，直接交给 `TemporaryCommandService.execute`，响应回原存回执字节（`no-store`、`nosniff`） |
| `server/src/main/java/com/acrylic/outbound/common/ServiceModeStartupNotice.java`（新增） | `ApplicationRunner`：启动读 `server_state`；`NORMAL` 记 INFO，非 `NORMAL` 记 ERROR 并说明写入将 503、恢复只能走 R09 控制链；读取失败只记 WARN；**不写库、不改模式、不使启动失败** |
| `server/src/test/java/com/acrylic/outbound/temporary/command/TemporaryCommandConfigurationTest.java`（新增，4 项） | 默认/显式 false 时无 `CommandOperation`、无控制器；`enabled=true` 时恰好三个 kind 各一；三个操作都 `recordsDecision()` 且 `requiresCandidateContext()` |

未改动：`contracts/openapi.yaml` 与路径/字段契约（**没有发布新路径**）、`CommandReceiptCodec.capabilities()`（五个能力仍恒 false）、`CommandUnitOfWork`/`CommandExpected`/`CommandPermissions` 的门禁语义、`SubmissionArchiveService` 的 TEMP 归档拒绝。

## 开启之后会发生什么（供授权决策用，尚未执行）

- `NORMAL_CONFIRM` 会真实调用 `SubmissionStock.reserve` 与 `SubmissionAccounting.commit`（`TemporaryConfirmationOperation.java:36-38`），即**真扣库存与记账**；`SHORT_APPROVE` 只记录审批并要求批准人 ≠ 原作业人；`LINK` 会在同一事务内安装映射 v+1、改指 `submission_scope`/slot 并把被取代的 PDA_TEMP 单置 `LINKED`。
- 三个操作都要求 `expected.candidateContextSha256` 与锁内重算的完整候选集合逐字一致（人工确认闸门），并各自要求 `temporary_order:confirm` / `submission:review` / `temporary_order:link`。
- 回执 `capabilities` 仍全 false，因此即使开启，回执也不自称放行、不释放本机删除权。

## 开启的前置条件（缺一不可）

1. 业主对"七类生产决定 handler/POST"的**明确开启授权**（AGENTS.md 边界；内部测试通过不构成授权）。
2. 全链路客户端：Web 复核页需要能构造规范命令（含 `expected` 与原候选上下文摘要）并处理原字节回执；Android 侧对 `LINK` 的映射安装仍属看板记录的 **M2b-4b，未实现**。
3. 契约同步：`temporary-command-paths.yaml` 增补 POST 路径、`temporary-command-schemas.yaml` 的 `capabilities` 四个 `const` 与 `IMPLEMENTED_API.md` 一并发布——本批有意未做，以保持"默认关闭"与已发布契约一致。
4. 隔离验证：非空 MySQL（V34→V41）+ 真实 HTTP 的开启态确认/短装/关联与失败关闭；本批**未做**。
5. 关联后的确认链：`TemporaryConfirmationSource.read/target` 仍只接受 `source='PDA_TEMP'` + `mappingVersion=1` + `round=1` + slot `FROZEN`，所以已 `LINK` 的来源即使开启也仍不能确认出库（M2b-4b 缺口）。

## 本批证据与边界

- 定向：`TemporaryCommandConfigurationTest` 4/4 通过。
- 全量作者自审：`gradlew -p server test --rerun-tasks` → **103 XML / 718 项 = 717 通过 / 1 既有符号链接条件 skip / 0 失败 / 0 错误**（合并态基线为 714 项，本批 +4）。
- **未做**：隔离非空 MySQL、真实 HTTP、Android/Web 客户端、APK/lint、真机、部署。默认关闭的证明是单元级（bean 与路由缺席）+ 生产 `application.yml` 无该配置键；**未**在运行中的服务上实测 404/409。
- 未授权动作一律未执行：未开启开关、未改契约 `const`、未放行 TEMP 归档、未接线恢复登记。

## 恢复出口的现状（与上条独立）

`RecoveryNormalRegistration` 是唯一能把 `server_state` 写回 `NORMAL` 的组件，但它要求外部控制权威 + 写者租约 + 数据库投影终态 `RELEASE_READY`，且 `baseline` 等参数无自动来源、无 bean、无路由；生产代码里也没有任何路径会把模式置为 `RECOVERY_PENDING`（该状态今天只能由人工 SQL 造成）。因此本批只做启动期告警，**未**接线恢复登记路由：单加一条路由并不能解开门，反而会造出一个前置条件无法满足的写入口。真正的恢复出口属于 R09 运维链落地范围。
