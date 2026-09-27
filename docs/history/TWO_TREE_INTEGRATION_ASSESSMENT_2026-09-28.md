# 两树整合评估（2026-09-28，自主运行 v11 / C3）

**只评估，不执行整合。** 对象：主树 `D:\acrylic-pda-outbound`（`codex/development`）与守卫星树
`D:\acrylic-pda-worktrees\r06-e2a-migration`（detached `ea4d166`）。

## 一、提交层关系

- 守卫星树 HEAD `ea4d166` **已被 `codex/development` 包含**（`git branch --contains ea4d166` 命中本地与远端
  development）⇒ 其提交历史已在主线，没有"未合并分支"。
- 该树比主线落后 383+ 提交，且有 **53 个未提交改动**（M2b-4b 服务端冻结态 + R07-3 冻结态）。

## 二、内容层差异（逐文件比对，忽略行尾）

53 个改动项中：

| 类别 | 数量 | 说明 |
| --- | --- | --- |
| 两树内容**相同** | 52 | 这些文件的改动**已全部进主线**（含 correction/、handover/、V42-V48 等目录级未跟踪项） |
| 内容**不同** | **1** | `server/src/main/java/com/acrylic/outbound/temporary/command/CommandReceiptIntegrity.java` |
| 仅存在于守卫星树 | 0 | — |

### 唯一差异的实质（**不是待合并业务改动**）

守卫星树那份多了一段 **attempt 117 的临时诊断**：

```java
catch(RuntimeException damaged) {
    // TEMPORARY DIAGNOSTIC (attempt 117): name the failing invariant instead of swallowing it.
    System.err.println("RECEIPT-INTEGRITY-CAUSE: " + damaged);
    damaged.printStackTrace(System.err);
    throw CommandErrors.corrupt();
}
```

主线版本是干净的 `catch(RuntimeException damaged) { throw CommandErrors.corrupt(); }`。

⇒ **该差异是探针，不是成果**；整合方向是"以主线为准"，无需把它带过去。

## 三、整合结论与前置条件

**结论：两树在业务内容上已经一致（52/53 相同，唯一差异是应在整合时丢弃的临时诊断）；
不存在"缺提交"或"需要合并的业务改动"。**

整合前置（若将来要退役该树）：

1. **确认该树的 `CommandErrors.java` 增强是否已在主线**（本会话早期核对过：`corrupt(String detail)` /
   `integrity(valid,label)` 两处增强在**两树都存在**，属已进主线内容）——退役前再跑一次逐文件比对即可；
2. 该树 `.local/` 持有**全部 e2a attempt 证据**（datadir 已于 2026-09-28 为解除磁盘写满而删除，日志/marker/
   storage/私钥/provision.sql 全部保留）⇒ 退役前须把这些证据归档到工作区外（做法见
   [唯一文件回收记录](UNIQUE_FILE_RESCUE_2026-09-27.md)）；
3. 该树是**本仓库所有 e2a / R08 IT 的运行树**（`tools/e2a-provision-and-run-106.ps1 -Workspace` 指向它），
   退役即等于放弃这条验证通道 ⇒ 需先决定新的验证树（或把该树正式登记为主树的一个长期测试工作树）；
4. `docs/sku.txt`、字体等本机样本在该树 `.local`，不入 Git。

**建议：暂不整合/不退役**。它当前是唯一可用的真服验证环境，收益（省下几十 GB）远小于失去验证通道的代价；
等 R08/R09 收口、且有替代验证树后再按上述 4 条退役。
