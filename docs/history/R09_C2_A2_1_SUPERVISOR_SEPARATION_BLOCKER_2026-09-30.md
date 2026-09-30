# R09 C2 A2.1 阻断与诊断：更正审批门的双主体（AND）拓扑（2026-09-30）

> **状态：已编码、未提交、待裁定。** V50 迁移与相关测试改动**停在主树工作区**，未进入 `codex/development`（原因见 §3：单独提交 V50 会让更正审批**对任何角色都不可满足**）。
>
> 背景裁定：用户 2026-09-30 给定业务答案——新增独立 `SUPERVISOR`，与 `WAREHOUSE_ADMIN` **分离**；审批权＝`correction:approve` ＋ `order:handover`；不设额度；`WAREHOUSE_ADMIN` **不再持有**审批权；两个草案码定稿；不支持代理。

## 1. 用户问题的直接回答

用户问："`CORRECTION_CONTEXT_CHANGED` 是产品缺陷，还是权限门/context 校验顺序问题？"

**答：都不是。** 它是**两个内核的授权拓扑不一致**，被 V50 的"只切审批码"暴露出来：

| 内核 | 主体要求 | 语义 |
| --- | --- | --- |
| 交接 `HandoverCommand.Actor.current`（`:78`） | `order:handover` **OR** `order:resume` | **一个 OR 集合**；把两个码一起给 SUPERVISOR 即可 |
| 更正 `CorrectionCommand.Actor.current`（`:91`） | `outbound:correct`（**无条件，所有命令**）→ 之后 `mayApprove()` 再要求 `correction:approve`（`:98`、`CorrectionCommandService`:95） | **两个 AND 的主体**：审批人必须**同时**持有"能进更正面"的码**和**"能批"的码 |

`CORRECTION_CONTEXT_CHANGED` 是**我 772 轮的测试瑕疵**（`expected` 块按 `orderId` 锚定，而 APPROVE 必须按 `documentId/documentSha256/status/expectedResultVersion` 锚定），与本次阻断**无关**；修正锚定后它消失了。

**校验顺序（顺带结论）**：`CorrectionCommandService.executeOnce` 的顺序是
`状态闸 → 锁(document→order) → 仓库校验 → Expected.validate(上下文) → mayApprove(权限闸)`。
⇒ 上下文**先于**权限被校验。这是 **fail-closed 的合理设计**（不先泄露权限信息），**不是缺陷**；副作用是"文档不存在/上下文陈旧"会先于 `PERMISSION_DENIED` 返回，写反例用例时必须先满足上下文。

## 2. 实证（单条 SQL，零 provision）

复用**已存在的** run 773 数据目录（该库已应用 V49/V50 并有已播种角色），启动同一实例后直接查询 `role_permission`：

```text
SUPERVISOR      correction:approve,order:handover,order:resume
WAREHOUSE_ADMIN device:manage,inventory:import,inventory:publish,inventory:read,inventory:reconcile,
                mapping:maintain,order:cancel,order:dispose,order:publish,order:read,order:recall,
                order:write,outbound:correct,pc:operate,pda:assign,pda:login,pda:recover,pda:scan,
                pda:submit,policy:manage,submission:review,temporary_order:manage,user:manage
=== 同时持有 outbound:correct 与 correction:approve 的角色 ===
(空)
flyway V49=1 success, V50=1 success
```

**⇒ `(空)` 就是阻断的证明**：V50 之后**没有任何角色**同时持有这两个主体，而更正 APPROVE **要求两者**。相对 V50 之前（`WAREHOUSE_ADMIN` 两码都有、可批）这是一处**能力回归**：更正审批路径对所有人不可用。

与 773 的测试结论一致：`WAREHOUSE_ADMIN` 得 `CORRECTION_PERMISSION_DENIED`（缺 `correction:approve`，符合裁定），`SUPERVISOR` **也**得 `CORRECTION_PERMISSION_DENIED`（缺 `outbound:correct`）。

## 3. 为什么 V50 现在不能单独提交

V50 按裁定收回了 `correction:approve`，但更正内核的"进门码"是 `outbound:correct`（裁定**未**要求把它也给主管）。结果：**审批门对所有人关闭**。把这种状态单独提交，等于在分支上留一条已知不可用的授权路径；按项目纪律（不得以局部通过代替）**不提交**，停在工作区并在此登记。开关默认 false，故**无生产影响**。

## 4. 两个可选修法（**请裁定，我不自作主张**）

### 选项 A（只动种子，不改代码）：给 SUPERVISOR 也授 `outbound:correct`
- 改动：V50 第 3 步的码列表加 `outbound:correct`。
- 好处：几行、当日可用、完全在"种子/绑定"范围内。
- 代价：主管**同时**能建单与批单 ⇒ **maker-checker 被削弱**，与"分离"的立意相悖；且这多授的一个码**超出你裁定的"可批范围＝两个码"**。

### 选项 B（改授权判决，推荐）：把进门码改为**按命令类型**
- 改动：`CorrectionCommand.Actor.current`（`:91`）由"无条件要求 `outbound:correct`"改为**按命令类型要求对应主体**：
  - `APPROVE`/`WITHDRAW`（即 `requiresApproval()` 为真）⇒ 要求 `correction:approve`（随后 `mayApprove()` 自然通过）；
  - 其余命令 ⇒ 保持要求 `outbound:correct`。
- 好处：**这才是真正的分离**——审批人不必是建档人；与交接内核"一个 OR 集合"的语义对齐；主管不需要多授任何码。
- 代价：属**授权判决的产品改动**（约 3 行 + 反例用例），**超出"A2.1 只做角色/权限映射"的字面边界**，故需你明确授权。

**我的建议：选 B。** 理由：①A 会用"主管既能建又能批"抵消分离，等于没做 maker-checker；②B 让裁决在**两个内核上语义一致**；③B 的改动面极小且可加反例用例（非主管建档被拒、主管批单通过、主管建档是否允许＝按 B 是**不允许**，正合分离）。
如果你希望**先不动产品代码**，则退路是选项 C：**本次只切交接码**（`order:handover`＋`order:resume` → SUPERVISOR），更正审批**暂留** `WAREHOUSE_ADMIN`，并在看板记为"更正审批分离待 B 授权"。C 不会造成能力回归。

## 5. 工作区停在哪一步（本次收口状态）

**已移至暂存区（不在源树内，故不会被 Flyway/编译看到）**：`.local/parked-a21-20260930/`

| 暂存路径（相对 `.local/parked-a21-20260930/`） | 内容 |
| --- | --- |
| `server/src/main/resources/db/migration/V50__supervisor_approval_separation.sql` | 新增迁移：SUPERVISOR 角色 + 三个审批码切换 + 定稿注释 + 除零断言（防 V44/V45 式的静默 0 行） |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aSupervisorApprovalSeparationIT.java` | 分离验收 IT（当前正卡在"主管过更正审批门"一步，即 §2 的阻断） |
| `.../E2aFormalRegisteredOperationsIT.java`／`.../E2aHandoverConcurrencyIT.java`／`.../E2aHandoverOutboxIT.java` | 审批夹具由 `WAREHOUSE_ADMIN` 改绑 `SUPERVISOR` 的**版本**（交接侧改法正确） |

**为什么移出源树而不是提交**：①单独提交 V50 会在分支留下**不可用的授权路径**（§2/§3）；②V50 留在 `db/migration` 会让 `version_facts` 报 `flyway V50 / migrations 50`，而该迁移并未提交 ⇒ **事实块与实际提交不一致**（正是看板漂移的类型）。移出后已提交树恢复 `V49 / 49`，事实块自洽。

**恢复方式（裁定后）**：把上表文件按相对路径拷回源树（三个夹具用暂存版覆盖），再按裁定的 A/B/C 调整 V50 的码列表或 `Actor.current`，然后跑定向验证。**注意**：`.local` 不随 git 分发，故本暂存仅在本机有效；若本机丢失，V50 的语义可由本页 §1/§2/§4 重建。

**已实证、与 V50 无关、无需重做的部分**：`seededAuthoritySplit` 断言（SUPERVISOR 恰好 3 码、`WAREHOUSE_ADMIN` 无一审批码、recall/dispose 留任）；交接 REQUEST 门（主管通过、`WAREHOUSE_ADMIN` 得 `PERMISSION_DENIED`）。

**明确未做**：未提交 V50 与上述测试；未改任何产品授权代码；未处置 I3 共享树；**未删除任何 `.local` 内容**（223 个 `e2a-cross-*` 全部保留）。

## 6. 最小验证方式（省磁盘，替代全套 provision）

用户关切的磁盘问题（每轮 provision ≈202 MB，已 223 个 `e2a-cross-*`）有直接解法：

| 目的 | 方式 | 成本 |
| --- | --- | --- |
| 授权/绑定类断言（**本次 §2 即用此**） | 复用**已有** attempt 数据目录起同一 mysqld（该库已迁到最新），用 `mysql.exe` 直接查 `role_permission` | 秒级，**0 新目录** |
| W-2-d 与恢复链逻辑 | 纯 JUnit（`@TempDir`，无 DB）：`gradlew -p server test --tests '*RecoveryLeaseVisibilityTest'` | ~11 s |
| 需要真实服务的行为 | 复用已有 datadir **不重播种、不重迁移**，只起服跑单类 | 分钟级，0 新目录 |
| 必须全新 schema 时 | 才用 `r09-e2a-entry-rerun.ps1`（新建 attempt） | ~200 MB |

**建议**：后续 A2.1/B4 的验证一律优先走"复用已有 datadir + 单条 SQL/单类单测"，仅在需要干净 schema 时才新起 attempt。

## 7. 边界声明

- 未改产品授权代码；未提交 V50 与相关测试；未部署、不接原库现网、未合 main、未打标签；`localDeletionAllowed=false`。
- **未删除任何 `.local` 内容**（含 223 个 `e2a-cross-*`）；未处置 I3 共享树。
- 暂停 ≠ 放弃 A2.1：代码已在工作区，等 §4 的 A/B/C 之一裁定后即可继续。
