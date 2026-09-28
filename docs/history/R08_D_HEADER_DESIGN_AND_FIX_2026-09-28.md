# R08 抬头导出：设计定性、修复与假设扫描（2026-09-28）

## 1. 第一轮只读定性：A

基线1b03f34，主树D:/acrylic-pda-outbound、codex/development，干净并已推送；验证树仍detached ea4d166，既有改动不重置。单助手，测试监听空闲，无已发现活动重型任务。源码版本事实见看板生成块。第一步仅核对设计与实现，没有改R08代码。

R07_3_IMPLEMENTATION_READY_DESIGN.md §26 B3（493–497行）明确：header_snapshot为冻结成功回执JSON，HEADER_CORRECTION使用允许已有键、排除服务端持有键的标量补丁；未知键/非标量/空操作拒绝，新版本写合并结果，旧版本不变，不产生effect或账务行；补丁只存在该单冻结CREATE命令字节，APPROVE从不可变回执读回，无需新列。§25收口项也明确未实现文本明细模型，correction_line是数值模型；早期§2的HEADER可空dispatch草案不能覆盖后续B3明确收口设计。

实现一致：CorrectionOperations.Create对headerPatch验证，拒绝非空lines，循环主动使用空数组；所以不调用insertLine。Approve读取CorrectionStore.createCommandBytes(documentId).headerPatch合并当前版本header_snapshot，新建版本；空lines使effect/账务循环无写入，但依然复制全部原dispatch有效快照。494真实CREATE/APPROVE、lineCount=0、v2抬头正确及scanCoverage=2是历史实证，不是缺明细的R07写入故障。

**判定A，不是B。**零明细是设计，不改R07，也不补伪造的correction_line/effect/ledger。

## 2. 凭据语义及已授权最小路线

抬头调整是“经批准、已写入该结果版本的字段变化”，不是实物出库差额。权威来源为correction_document身份/审批事实、该文档RECORDED CREATE命令里的headerPatch及真实回执ID、相邻不可变结果版本header_snapshot的前后值；检查补丁、前后版本及文档相互吻合。数量/重量均0差额，dispatchId与不存在的lineId留空，不把回执ID伪装成明细ID。evidenceReference明确引用真实CREATE回执/文档/字段。

现有Adjustment已带headerField/beforeText/afterText/reason/approver/evidenceReference，足以表达，不新增数据库结构或快照DTO字段。R08按文档单独读取零明细抬头，再与实物/撤销调整合并为版本链；渲染校验对抬头以文档+字段去重，只允许抬头无数值明细。保留原单号身份并据已验抬头调整显示该版本单号和前后变化；默认v1无调整时行为不变。若实现中发现必须改变结构，则依用户硬边界停止。

本轮按已明确授权的A分支接续，不重复要求批准；采用测试、安全SQL和单一职责约定，用户最小范围优先于无关重构。只修R08只读消费/渲染，不改R05/R06/R07、ConfirmedV1Adapter、默认开关或发布契约。

## 3. 验收及时间盒

第一轮定性；第二轮最小实现、真实MySQL/HTTP抬头XLSX/PDF逐值回读、默认v1真实HTTP及R08ExportIT14项回归；第三轮只读扫描凭据/明细假设并收口。新真bug立即停报告；其他同类记录，不扩修。attempt从495+、R08专用run31+先查重。494失败及全部历史红证据保留，不通过删除校验或修改成功期望冒充验收。

## 4. 第二轮修复交付：68a43f8

实现68a43f8已推送。HeaderCorrectionSource单独读取无明细抬头文档，使用真实CREATE回执字节及摘要、回执/文档/申请人一致性、相邻header_snapshot补丁合并结果、审批身份/时刻和数重不变约束；不创建数值明细，不借用回执ID冒充lineId。Adjustment现有文本字段表达变化，lineId/dispatchId留空；evidenceReference列真实文档、CREATE回执及headerPatch字段和前后版本。未改任何表/列、OrderSnapshot/Adjustment字段或契约结构。

CorrectionAdjustmentSource合并实物/撤销及抬头凭据，按版本排序，保留非空、版本连续与物理逆转检查。渲染校验仅允许HEADER_CORRECTION无lineId，核对原值/字段/零数重并去重。OrderSnapshot.number仍是原单号身份；文件正文按已校验的orderNo调整链显示本版本单号，并另列原单号和前后值。原确认无调整时显示逻辑不变。支持的显示键限现有orderNo/vehicle/container/remark；其他已有标量键显式拒绝，未用默认空值放行。

## 5. 本轮验收

- attempt495：E2aHeaderCorrectionExportIT 1/0/0/0，真MySQL、真服、PDA Bearer CREATE/APPROVE、真实Web会话/CSRF申请下载XLSX/PDF；没有用服务DTO调用替代导出HTTP。
- 样本仍复用两包原确认，仅orderNo从FH-LINKCONFIRM-0001改为CORR-HEADER-EXPORT-1；lineCount=0、effect=0、v2有效行仍2包/601500g。快照逐项核对前后字段、真实回执ID、原因、审批时刻、审批人原16字节、有效dispatch/数量/重量，以及空明细ID。
- XLSX/PDF只读独立回读PASS：有效明细2行、抬头凭据1行；新单号在标题/汇总/明细正确显示，原单号及前后变化保留；PDF两页2300字符，页图目视无裁切/重叠。
- 同一495还经真实HTTP验证basis=ORIGINAL_CONFIRMED且不传resultVersion：v1原单号、无调整、XLSX下载成功。R08专用run31的R08ExportIT为14/0/0/0（真MySQL+MockMvc），与外部HTTP证据分列。
- 渲染全套134/0/0/0，新增5项包括零明细、前值错误、同字段重复、物理lineId仍必填及默认显示不变；compileTestJava与installDist成功。最终仅移动一处方法注释，运行逻辑与实测代码一致，已同步验证树哈希。
- 作者自审，未独立审查。未改ConfirmedV1Adapter、R05/R06/R07、生产默认值/开关，不发布契约。494失败保留；本轮无红业务attempt，495/run31既有cleanup空值警告保留。
- 主树.local/d-header-fix-20260928/保存编译日志、134项XML、495/run31独立XML/控制台、页图、验证树前像和10文件同步哈希。495真实回执、库事实、快照及文件在验证树.local/e2a-cross-20260925-495/storage/header-correction/，default-v1子目录独立。未提交任何本机密钥/数据/产物。

## 6. 第三轮待做

仅只读扫描correction_line/effect/账行存在性及相关前提，检查撤销/抬头/部分/连续更正场景；分类记录已修、合理约束、风险候选及确认缺口。不强行扩修。四项基本样本已有通过证据，但混合/跨文档、抬头撤销、其他标量字段及独立审查仍未验，D整块是否交付由用户决策。
