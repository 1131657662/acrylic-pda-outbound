# 已确认首版快照适配

2026-09-25 当前结论：TEMP_PDA首轮mapping1原确认已适配，核原签名BLOB/提交/快照并保留原TEMP顺序；报表123项、服务端493通过/1skip、attempt53真实HTTP/Chrome及12文件回读通过。此为原确认，不代表后续净结果、多设备或完整R08完成。无总调度/子代理、无生产部署；下方旧日期记录仅历史。详[验收](../../history/R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md)及[当前看板](../../ORCHESTRATION_STATUS.md)。

2026-09-20，依赖核对基线：`codex/development` 的 `32ff90f`。R08工作树仍从 `2369392076083a5a4f59d8ea3d6edebe5067a03f` 起步，没有合入或复制其他任务代码。

## 实现范围

`ConfirmedV1Adapter.adapt(ConfirmedV1Input)` 将调用方提供的固定首版事实投影为现有 `ReportSnapshot`，可直接交给Excel/PDF生成器。CLI新增 `--confirmed-v1` 模式，原三参数模式保持兼容。

这份输入包装是**本模块自有协议**，不是现有服务器响应或已发布API。首版成功回执中的字段来自现有 `SubmissionReceipts` / `PcAccounting`；`orders`、`dispatches`、`result` 等包装需要后续一致性快照提取方组装。本轮没有查询数据库、调用API、注册Worker或生成真实业务报表。

只接受 `ORIGINAL_CONFIRMED`、正式订单、持久化结果版本1及成功回执版本1。原抬头与有效抬头相同，每条确认事实派生1包及原克数，无调整。即使以后订单已有版本2，使用此入口也只表示**原确认版本1**，不能被标记为最新净结果；后者仍需要固定当前版本及R07数据。

## 数据映射

| 输入 | 已存在的来源 | 提取方必须保证 |
| --- | --- | --- |
| metadata | 调用方固定的成员、期间、截点及来源说明 | 一致性快照、成员授权、原出库日口径；不是只记录一个时间 |
| order.id / number / revision / header / products | 原订单、固定revision/publication、order_line | 原发布内容；`header_snapshot`在现代码中保存的是回执，不能假定其中含车柜号与完整抬头 |
| confirmedAt | 调用方明确选定的原确认时刻 | 不能用导出/扫码/调整时间替代；PC回执不含此字段，需要从首版确认事实冻结；提取规则仍待服务端接入确认 |
| dispatches[].orderId / submissionId | dispatch_record.order_id 与 submission_id 或 pc_submission_id | 原始外键归属，与成功提交和首版结果对应，不能用包装里的订单ID代填来掩盖混入 |
| dispatches[].line | dispatch_record、原submitted_capture/冻结正文或原pc_entry的字段投影 | 仅确认事实；输入顺序已为order_scan_seq递增，保留VOID/失败造成的空缺；详见INTEGRATION映射 |
| result.orderId / submissionId / version / actualCount / weightGrams | order_result_version版本1及相应提交外键 | 包数、克数均使用无损整数字符串，不读取当前版本指针代替版本1 |
| result.products | order_effective_line版本1 | 包括计划中的零实绩产品；完整且无重复 |
| successReceipt | PDA成功回执或PC `PcAccounting.commit` 返回/保存的内层成功回执 | 外层PC命令receipt envelope的`result`需要上游解包；不传分页/订单详情响应冒充回执 |
| sequenceReference | 提取方保存的持久化序号依据引用 | 可追溯到固定输入；模块附加submission引用，不因有字符串就声称证据验真 |
| exceptions | 冻结的VOID、失败或处置记录 | 单独列示，不计入确认包数；模块无法仅凭dispatch表证明异常完整 |

### PDA

核对 `orderId`、`submissionId`、`SUCCEEDED`、字符串 `resultVersion="1"`、`actualCount`、`actualWeightGrams`、FORMAL来源及sourceOrderId、COMPLETED、orderNo、整型receiptVersion=1、首尾orderScanSeq、高水位和items产品汇总。回执completedAt不得晚于快照截点，并且与传入原确认时刻的上海日期必须一致；跨日分歧拒绝，交给源快照提取方解决，模块不任选一个时间覆盖另一个。

每条原设备必须等于freezeDeviceId，inputSource为BROADCAST/MANUAL，时间质量仅DEVICE_UNVERIFIED/MISSING。多原设备/换机交接不从这个入口接入；原通用快照仍保留R07的VERIFIED_HANDOVER接入提案。

### PC

核对 `channel=PC`、orderId、submissionId、SUCCEEDED、字符串resultVersion=1、actualCount、`weightGrams`和workId。PC没有PDA采集设备；原来源使用模块约定 `pc-work:<原workId>`，deviceCaptureSeq使用原pc_entry.sequence_no，与orderScanSeq一致；captureId投影原pc_entry.id。inputSource使用本模块标签 `PC_MANUAL`，timeQuality使用 `SERVER_CAPTURED`，receivedAt/savedAt均保留原entry.created_at。这些是报表投影约定，不是正式设备接口字段。

## 校验与安全边界

逐包精确克数先按产品求和，再与持久化产品/整单汇总核对，最后与回执核对；PDA还核对回执items。错单、错批次、未成功、版本不一致、产品遗漏/重复、总数或重量不符、混设备、乱序均失败，不发布文件。原始条码、重量文本、顺序、异常和TEST_SAMPLE标记沿用现有生成器。

回执附加字段允许保留以兼容现有完整回执，但不会写入报表。不解释或验证signedReceipt、payloadSha256、inventoryDeducted、授权、审批及签名，也不能证明底层数据没有被篡改。适配器通过只表示输入之间结构与数值一致。提取方必须从可信原事实获取，并完成适用的验签、正文摘要和完整性检查。

普通报表字段仍最多1000字符，条码256字符；此模式JSON解析允许回执中单个不透明字符串最多64KiB，以容纳原JWT，整个JSON仍限制512MiB。回执解析使用严格重复键检查；用到的字符串字段禁止数字/浮点/布尔隐式转换。Java对象也进行相同业务校验，但JSON文件大小/词法限制只适用于文件入口。

## 运行与样例

从本工作树根目录运行：

```powershell
./report-rendering/run.ps1 -GradleArguments @('run', '--args=--confirmed-v1 examples/confirmed-v1-test-input.json xlsx build/confirmed-cli.xlsx')
./report-rendering/run.ps1 -GradleArguments @('run', '--args=--confirmed-v1 examples/confirmed-v1-test-input.json pdf build/confirmed-cli.pdf')
```

本机隔离依赖缓存的离线运行参数见PROGRESS中的实际命令。目标文件存在会拒绝，不覆盖旧产物。

`samples`生成的 `examples/confirmed-v1-test-input.json` 与 `build/samples/confirmed-v1.{xlsx,pdf}` 全部是明确标注的虚构样例。含PDA/PC两单、A/B/A三包顺序1/3/5、零实绩第三产品、前导零、VOID与恶意单元格文本。每单3包/901.503kg，总计6包/1803.006kg，原上海出库日2026-09-18。签名字段故意是无效样例文本，不能用作恢复或验签证据。

## 后续接入清单

- 已具备：原始首版事实/成功回执的字段基础、本模块适配、独立文件生成；R05的持久任务基础另已完成。
- 仍需：服务器在一致性快照中固定成员、版本、产品、抬头、原确认时刻、明细和异常；复核每种来源的关联及真实样本。只完成此层后才能宣称真实首版报表接入完成。
- R05整合：后续单独编写handler，复用已有租约/fencing/短事务发布接口，并提供每token暂存路径、续租和资源限制。本轮没有新增任务类型或修改共享调度。
- R06/R07临时来源、换机、更正和逐包净结果仍是接入提案。后续已增加[原确认文件Web/API/Worker接入](WEB_INTEGRATION.md)，复用现有读权限；完整导出中心、最新净结果适配、真实业务验收和部署仍未执行。
