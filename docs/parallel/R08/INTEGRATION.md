# R08 文件模块接入清单

状态：本页描述 `report-rendering` 自有 Java/JSON 输入协议，**不是正式 OpenAPI**。生成器本身不查询数据库；新增服务器适配和订单页接入见[原确认文件接入](WEB_INTEGRATION.md)。不修改授权模型、同步、记账或应用版本。

2026-09-20续接：已有[首版PDA/PC快照适配](CONFIRMED_V1.md)，后续依用户授权在 `8f566e9` 依赖上增加数据库首版提取、R05 Worker及受权下载。下表中的R06/R07字段继续为提案；本轮联调使用专用数据库内的合成正式订单。

## 快照要求

`ReportSnapshot` 的 metadata 固定 snapshotId、dataVersion、cutoff、generatedAt、上海日期期间、basis、provenance、sourceDescription、orderSortDescription。orders 数组就是固定成员顺序，不按设备序号、UUID或客户端时钟重新排序。快照本身为不可变集合，不能把“仅保存一个截止时间”当作完整快照。

`basis=ORIGINAL_CONFIRMED` 必须每单结果版本1、无更正；`CURRENT_EFFECTIVE` 使用调用方选定的固定结果版本。期间口径本模块固定为 `SHIPMENT_DATE_NET`，上海日界由原确认时间确定，调整发生日不改变归属。不支持 MOVEMENT_DATE，不能混入其记录。

每单含原/净抬头、固定产品计划、确认时间、来源、revision/resultVersion、sequenceProof、originalLines、effectiveLines、adjustments、exceptions。原始明细严格递增且唯一的 orderScanSeq 可以有缺口。effectiveLines 必须覆盖所有原始 dispatchId（退出者 count=0/weightGrams="0"），可按任意顺序提供；渲染按原事实顺序查找，绝不重编号。

跨设备同单需要 `sequenceProof.kind=VERIFIED_HANDOVER` 与凭据引用；单来源为 `PERSISTED_ORDER_SEQUENCE`。上游必须实际核验交接、全部高水位、原设备身份和来源。模块只校验结构，不将非空引用当作密码学或业务真实性证明。历史文件行序及无法恢复高水位的资料必须使用后续单独模板，本模块拒绝缺失依据的标准严格顺序导出。

## 与起点代码的映射

| 本模块字段 | 当前代码中可取的内容 | 接入状态/限制 |
| --- | --- | --- |
| order.id / number / revision | outbound_order、order_revision / order_publication 的固定修订 | 已接入首版成功提交对应修订，一致性事务内固定 |
| products | order_line.delivery_code / model_snapshot / name_snapshot / planned_count | 必须原发布版本，不使用当前主数据 |
| resultVersion | order_result_version.result_version；outbound_order.current_result_version | 现有首版1；后续指针必须在接受导出时固化 |
| originalLines.dispatchId / captureId / orderScanSeq | dispatch_record.id / capture_id / order_scan_seq | 原确认事实；不混入提交前VOID/已卸货记录 |
| productCode / weightGrams / rawWeight | dispatch_record.delivery_code / weight_g / raw_weight | 整数克字符串；BigInteger计算；不从浮点kg反推 |
| barcode | 由dispatch原包身份核对的条码；PDA冻结原文 / bale.barcode | 输入始终String，不能经过Excel或JSON浮点中转 |
| originDeviceId / deviceCaptureSeq | submitted_capture.origin_device_id / device_capture_seq，原冻结captures | 不取当前上传/承接设备；现服务尚无完整换机来源核验，不冒充R07已完成 |
| receivedAt / savedAt / timeQuality | PDA冻结正文 receivedWallMs、savedAt；接收时钟质量需从既有证据评估 | 保留原时钟，质量无法确定时写DEVICE_UNVERIFIED，不伪造服务器可信采集时间；缺失时间用空串且质量写MISSING |
| operatorName / inputSource | 原冻结上下文与人员快照，BROADCAST/MANUAL；PC原operator_name | 不取更正后人员或当前登录人 |
| confirmedAt | dispatch_record.confirmed_at 与固定成功结果/回执核对的整单确认时刻 | 不用上传、扫码或调整日替代 |
| 原/净车柜号及备注 | 固定发布/首确认header快照 | 净抬头R07未实现前与原抬头一致；更正后字段为提案 |
| effectiveLines | 现有order_effective_line只有按产品汇总，不含逐包状态 | 首版无更正时可由原dispatch投影全部count=1，并与汇总核对；更正逐包净结果为R07接入提案 |
| adjustments | correction effect 的凭据行、前后值、结果版本、发生时间、审批人与凭据引用 | **R07接入提案**；不能直接累加inventory_ledger（一个结果差额可对应多条流水） |
| source=TEMPORARY_PROPOSAL | 临时来源、正式化/关联关系 | **R06接入提案**；本次仅测试样例，不创建临时单接口 |
| sequenceProof=VERIFIED_HANDOVER | 经批准handover manifest/高水位依据 | **R07接入提案**；不同设备局部序号碰撞样例不代表真机交接验收 |
| exceptions | PDA原冻结失败/VOID/处置证据；PC原撤销/处置记录 | 单独表，不计入原始确认包数或净包数；上游须负责完整性 |
| PC原始明细 | PcAccounting写dispatch_record，原pc_submission中的entries及operatorName/createdAt | 已接入原冻结正文及摘要、回执核对；没有PDA设备字段，使用pc-work:ID、PC_MANUAL及SERVER_CAPTURED |

## 调整输入

adjustments 按 resultVersion 非递减、版本完整排列。每行唯一 correctionId/lineId，实物行每个版本只调整一次同一dispatch。beforeCount/beforeWeightGrams必须匹配前一版本贡献；重量更正仍为1包；退货/错误出库变为0包0克；HEADER_CORRECTION只允许vehicle/container/remark且没有重量/数量效果。最后净结果必须逐包完全匹配，缺失、重复、版本不连续等均拒绝。

这些是渲染前一致性检查。批准、实物确认、幂等记账、签名及可信业务版本由R07负责，生成器不承担业务裁决。

## 整合状态

下列为最初清单；其中 Worker、租约、受权下载与单订单原确认入口的最新状态以[本轮接入记录](WEB_INTEGRATION.md)为准。跨订单汇总、导出中心、R06/R07与正式契约仍待后续。

1. R06/R07正式模型就绪后核对上表及原出库日政策；实现服务端适配器，从一致性快照获取权限过滤后的固定成员与逐单版本。真实更正联调单独验收。
2. 独立Worker引用Java库或分发包，固定模板/字体/Chromium版本。可选择Gradle composite build或内部发布库；本次未改server、根或Android共享构建。
3. R05持久任务基础已于`32ff90f`核对为已具备；后续R08应复用其租约/fencing与短事务发布，单独接入handler、并发1、超时/进程终止、磁盘配额、取消及原文件版本保护。本轮未注册任务。PDF每次独立浏览器实例，JS关闭、网络请求阻断，仍须对Worker施加OS级资源限制。
4. 导出中心、鉴权下载、审计、私有文件目录、成员范围授权复核与过期清理由后续任务负责。渲染成功不等于获准下载，也不代表订单确认。
5. 固定真实数据源后回归原始/调整/净值三层、上海日界、分批/分页不漂移、跨机交接、旧文件不覆盖、权限变更及恢复代次。

本会话后续“继续”仅续接本独立文件子项；公共路线和总进度留给主开发会话维护。
