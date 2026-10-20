# 固定候选十九项跳过分类

日期：2026-10-20。仅消费固定候选 `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd` 的保全XML及同SHA源码；未重跑，不拼其他分支的通过。19项全部不计通过。

原XML只有空skipped标记，没有逐项理由。下表原因来自同SHA启用注解、assumption及原运行输入核对，不伪称XML本身写明原因。环境类2项，条件入口未满足17项；未配置路径不等于相关原件不存在。

| # | 测试类 | 方法/用例 | 分类 | 原因 |
| --- | --- | --- | --- | --- |
| 1 | `SkuParserParityVectorTest` | `sameOriginalBytesProduceFixedServerResultsForAndroidConsumer()` | 环境配置 | SKU_PARITY_OUTPUT 未配置；向量生成入口未启用 |
| 2 | `RelayIndependentClosureTest` | `missingAuditPrefixCannotInheritSignedClosure()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 3 | `RelayIndependentClosureTest` | `changedPlanCannotInheritCompleteBoundary()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 4 | `RelayIndependentClosureTest` | `actualAndroidClosureBindsItsFullCollectionPrefix()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 5 | `RelayIndependentCollectionTest` | `originalWeightCannotBeReplacedByAnotherClaim()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 6 | `RelayIndependentCollectionTest` | `savedFactCannotPrecedeOriginalConfirmation()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 7 | `RelayIndependentCollectionTest` | `originalRowsMatchSkuAndAuthorization()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 8 | `RelayIndependentCollectionTest` | `changedRawInputCannotInheritOriginalScan()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 9 | `RelayIndependentCollectionTest` | `AuditOutsideOriginalBootCannotCertifyClosure()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 10 | `RelayIndependentCollectionTest` | `duplicatePlanCannotInflateACompleteBoundary()` | 归档条件未启用 | E06_CLOSURE_ARCHIVE 未配置；原封存包消费者未运行，不能推断归档不存在 |
| 11 | `RelayIndependentOriginalTest` | `independentMembersInFormalArchiveAreRejected()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 12 | `RelayIndependentOriginalTest` | `reusableMaterialInTaskIsRejected()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 13 | `RelayIndependentOriginalTest` | `alteredOriginalSkuIsRejected()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 14 | `RelayIndependentOriginalTest` | `actualPublicOriginalRetainsPendingIdentityAndSkuBytes()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 15 | `RelayIndependentOriginalTest` | `missingIndependentMemberIsRejected()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 16 | `RelayIndependentOriginalTest` | `actualSignedAndroidArchiveIsAcceptedWithoutFrozenControl()` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 17 | `RelayIndependentOriginalTest` | `addedPublicCredentialFieldIsRejected(String)` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 18 | `RelayIndependentOriginalTest` | `independentArchiveCannotManufactureFormalEvidence(String)` | 原件条件未启用 | E06_REVIEW_ORIGINALS 未配置；原件/归档条件入口未运行，不能推断原件不存在 |
| 19 | `SourceEvidenceIntakeFilesTest` | `symbolicLinkTargetCannotBeAccepted()` | 操作系统环境 | Windows 创建符号链接触发 FileSystemException 后 assumption 跳过；XML 未记录具体 OS 错误 |

## 证据和边界

- 原XML目录：`.local/deploy-remediation-20261020/fixed-8cb51ded-final-server-xml/`。
- 逐项XML及源码SHA、类别、方法名：`.local/deploy-real-client-20261020/skipped-classification.json`；生成明细见同根`skipped-tests.md`及`classify_skips.py`。
- 1项SKU输出环境未配置；8项原件条件未启用；9项封存归档条件未启用；1项Windows符号链接创建异常后假设跳过。
- 参数化测试在类级条件禁用时，XML按其已有测试描述节点计数；8项不冒充所有参数组合已枚举或执行。
- Windows符号链接XML没有具体FileSystemException文本，故只记录已走到该assumption分支，不能证明具体ACL或某项系统策略根因。
- 主开发分支的后续条件消费者通过不改写这些固定输入的跳过结果；补齐后必须在同一候选/固定原输入重跑，失败和跳过继续单列。
- Android staging另有实际编译失败，见[真实客户端路径评估](DEPLOY_REAL_CLIENT_PATH_ASSESSMENT_2026-10-20.md)，不能写为“除真实数据库链外其余通过”。
