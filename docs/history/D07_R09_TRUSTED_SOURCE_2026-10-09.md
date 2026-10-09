# D07/R09 最小可信来源读取层接续记录

范围是缺可信归档/截点外事实恢复的只读前置，不是D07/D13/D14整体通过。版本事实只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。接续依据[FORMAL暂停记录](D01_D18_PAUSE_2026-10-09_FORMAL.md)；总目标active、未完成，不缩小D01–D18或第8节。

## 输入与保护

树D:/acrylic-pda-outbound、codex/development，输入HEAD/实际远端均fcf7e696381149edb1b551a0c8ee7147245c8f31。规则/看板事实/FORMAL和磁盘两个final已核；22条继承dirty与两个final共44个hash全部一致，全量副本/hash/binary diff/Git/写者/进程/监听/C-D磁盘存`.local/d01-d18-20261009/resume-preflight-01/`。本聊天为单一写者，无页面运行器/模拟器/重型Gradle在途；19387旧Harness已无监听，既有数据库/ADB不操作。1150/1151/UI-06不重做，1100–1102不重跑，未启动/复用任何旧或失败库。原件、失败材料及继承改动保留。

## 实现与退出标准

沿既有recovery控制实现显式Java离线只读接口，没有新Spring bean/HTTP入口或生产配置：

- `RecoverySourceReader/Manifest/Identity/PinnedSource`：独立指定manifest SHA256，不从恢复物料推导授权；绑定库外installation/recoverySession/epoch/snapshot/备份摘要/损失窗口与仓库。按原FORMAL/TEMP来源、scope/submission/设备/operator/资格/原creator-client/轮次/epoch/三个高水位匹配，检查五类非空原件的字节数及原摘要。路径不得落在控制、全部restore roots或checkpoint内，复用重定向与single-link防护，每次前后重读控制，输出防御复制，拒绝缺件、坏字节、错范围和未知字段。
- `RecoveryReceiptKeys/Verifier`：显式可信当前/历史RSA公钥DER，只读验签无需私钥；kid派生、DER/exponent/大小、重复/未知key、严格JWT头/claims、RS256/issuer/audience/签名及未签内容完全一致检查。
- `RecoverySourceTerminal`：重验原ABORTED/SUCCEEDED、原ID/freeze device/轮次/epoch/FORMAL order或TEMP creator-client、原正文/LOCAL冻结摘要及成功capture高水位；原payloadPresent语义保持，拒绝恢复扫描/释放实物/删除声称。读取或验签均不修库、不补记账、不颁新资格。

设计/作者自审分别在`.local/d01-d18-20261009/recovery-source-design.md`及`source-reader-self-review.md`。自审已补TEMP完整来源身份、LOCAL原冻结摘要、显式空正文和成功高水位；物料origin/occupancy/permission只保证固定字节，不冒充已完成业务重验。

## 验证与边界

定向首轮`r09-source-reader-targeted-01`为43/1/0/0、exit1/sourceUnchanged；备份摘要反例误用原值，失败XML/log未删除。修后`r09-source-reader-targeted-02`为103/0/0/0、exit0/sourceUnchanged。公钥/终态扩展第一轮`r09-public-receipt-targeted-01`为130/0/0/0，作者补上述边界后`r09-public-receipt-targeted-02`为134/0/0/0、exit0/sourceUnchanged；均为真实本机文件/控制链和合成开发签名的定向测试及受影响控制回归，原command/source-before/after/log/XML/result在`.local/d01-d18-20261008/`同名目录。

新只读消费者读取1151保留的原真实HTTP中止回执及最终成功回执，使用该次开发公钥DER，exit0；只验原签名、原摘要，不加载私钥、不接数据库、不重跑1151。脚本/原hash/report在`.local/d01-d18-20261009/retained-receipts-1151-01/`及`VerifyRetainedReceipts.java`。原HTTP是历史合成开发身份，消费者结果不等于原身份/完整恢复或当次真实HTTP验证，也不等于独立人员审查。

受影响普通server回归`r09-source-server-regression-01`为3494/0/0/1、exit0/sourceUnchanged，211份当次XML；唯一skip为Windows符号链接反例，未执行不计通过。普通test排除IT，不计集成/数据库/客户端/模拟器/真机或部署通过。Java源码新增的离线能力不改变旧产品默认，本阶段未改Android/Web/迁移/契约，无当次APK或页面交付。

## 接续链与限制

下一步从原资格/原审计根/占用/权限语义重验和受控恢复写入接续。必须绑定已关闭恢复会话、可信独立高水位、原终态/原来源和当事务租约，保留完整原ID/正文/事件序号/回执；不能拿本读取接口的结果当NORMAL放行许可或直接调用普通写者重放。现有closed projection purpose保持拒绝，不绕成普通写者。再用全新跨树分配的隔离输入实际恢复到截点前、重建、D07围栏、接收激活/最终提交及尾SQL回滚/重复重启；不以合成控制、fixture签名或一组拒绝反例代替该链。

D06/D07整体、FORMAL产品入口覆盖、D08–D18和第8节仍未完成；候选排除/取消释放政策、原资料/旧BCD/526、历史失败/恢复限制和C01–C08保持单列。生产默认/签名和七类生产决定能力关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。本机证据/产物未异地备份，新增产物在D盘；阶段Git回执另行登记，范围备份不等于整体完成。
