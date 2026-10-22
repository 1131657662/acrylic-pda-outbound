# 独立凭据卡—非空采集—注销后只读导出—电脑消费者

日期：2026-10-22。测试消费者开发备份；不是完整D/O/U、第8节或现场验收通过。

## 输入与标准

- 固定树 D:/acrylic-pda-outbound；分支 codex/development，输入HEAD d4679f15021970a9baa3710e46460a47dcf14561，加本片新测试与受保护继承WIP；不是干净提交独立构建。
- 证据根：.local/goal-20261022-resume/independent-card-work-export01/。baseline.json保存40条继承WIP状态/SHA，preimage.json记录新测试原不存在；收口原字节及SHA在closeout01/preimage/和preimage-manifest.json。
- 标准：原卡生成/保存/重新选卡/保管声明/原vault预置支持同一非空独立任务采集；清vault模拟注销后，原本人救援凭据只读导出，由独立Python进程消费原包。
- 唯一新增仓库代码：android/app/src/test/java/com/acrylic/outbound/pda/ui/IndependentCardWorkExportTest.kt，162行；SHA-256 2e3ebc4ea805db97b7ebd1d3b833d9dbe0177b0e201d405e84d0cac91788ff74。产品、schema、契约和权限未改。
- 原[独立卡消费者](GOAL_PDA_INDEPENDENT_CARD_CONSUMER_2026-10-21.md)与[电脑材料消费者](GOAL_PDA_PACKAGE_SECRET_PC_CONSUMER_2026-10-21.md)按各自输入复用，本片只补同一非空输入之间的组合缺口，不重跑或拼接旧计数。

## Android实际执行

1. 复用IndependentCardUiFixture的原页面/生成器/卡文件/回读/保管确认/vault；合成回环HTTP只预置独立凭据与本人只读救援凭据两个请求。
2. 停合成server后原页面重新选卡核验；测试从原卡解析凭据走IndependentCredentials.unlock，不手造采集grant。
3. 原SKU仓库导入57字节合成GB18030，原任务仓库创建计划2包任务；原ScanStore/Reception/Saving保存条码003A0000001的1包，原序号1、300500整数克。
4. 清vault模拟注销，原采集grant.recheck拒绝；事先原流程预置的本人凭据通过RescueCredentials.unlock取得只读grant，不手造授权。
5. 原RoomSnapshot/PackageDraft/ExportFiles生成并recover加密包；前后全表逻辑digest相同、原卡字节不变；server请求总数仍2。
6. 解密档案逐成员不含采集signedCredential、Bearer/refresh、独立卡随机密码或救援口令；维持pendingReconciliation=true、serverAuthorizationVerified=false及待主管对账提示。

- android01/setup-failure.json：准备时误寻android/gradle/wrapper/gradle-wrapper.properties；未启动Gradle，不计业务失败；原件保留。下一次改读根gradle/wrapper后单独执行。
- android02/command.json：已缓存Gradle，-p android :app:testDebugUnitTest --offline --no-daemon --max-workers=1 --no-build-cache，仅新类；独立build/XML/project-cache，子TEMP为.local/t22ic02，不改全局环境。
- android02/exit.json退出0；同次原XML为1 tests / 0 failures / 0 errors / 0 skipped；执行约2分12秒，XML测试时间12.906秒。
- XML：android02/xml/TEST-com.acrylic.outbound.pda.ui.IndependentCardWorkExportTest.xml；SHA-256 50f0b717d296744fb7b0f4e288e28b26ab3ba92b7ed062c7c2621c8ab6167c17。
- verification01.json：1099执行输入前后SHA不变，其中39份schema核同；测试前后40项继承WIP保持。后续文档收口差异另列，不倒填为测试输入变更。

## 独立PC实际消费

- pc-consumer.py是本片.local独立核验驱动，不是提交的产品工具；SHA-256 2c486f0bf91cf80e8111694f1ac2844459f197a547aae8b33bf8cc1f9cf5e3ed。
- pc01/execution-input.json固定Python/驱动SHA/依赖/TEMP。实际C:/Python314/python.exe -X utf8 -B另进程运行原python -m tools.pda_rescue.cli，带--secret-file、--trust、--require-trusted，输出全新pc01/verified-copy/；未请求网络。
- CLI与外层消费者退出0；integrityVerified=true、sourceTrusted=true。信任仅指包外已知合成登记公钥，businessReceiptVerified=false。
- INDEPENDENT/SCANNING、待对账、1有效包/300500克；原序号/条码/状态/SKU57字节核同，order.json与Android捕获的order-original.json逐字一致。
- 输出不含材料密码、Bearer/refresh或救援口令；四输入前后字节/SHA保持，43个Python源文件SHA未改。
- pc01/保存命令、stdout/stderr、consumer.log、exit.json、result.json及全部输出SHA，未复制旧输出冒充当次结果。

## 当次原文件链

以下位于android02/private-vector/，均为本轮合成材料，不是原身份或正式信任依据。

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| independent.pdarescue | 6526 | f4e70db8c505a5d96b80627e8094551ffa2ad34c8713769facb778f2464d304d |
| package-secret.json | 780 | a432354066fbd3469b799545f3e0bfafe9056b845d6c3ec9fe7ed58ac17f8e13 |
| external-trust.json | 302 | 5fce28a22774d08a02275e3c581d8b39964e0218ccf5871e496e8d26437be359 |
| order-original.json | 428 | 8fc9c1e46e5d22a39c766c4f185b7b16228102a6815afe7ecd0b2a0e7e6e7433 |

package-secret.json含合成解密材料，只留被忽略private证据，不打印正文、不暂存或上传。四原文件和当次PC输出保留，不在清理中替换字节。

## 作者自审与限制

- 作者完成自审；独立Python进程是工具消费者，不是独立人员审查。复用原fixture/grant/签名/限速/撤销/仓库及删除门。
- 新任务/扫码/导出是原产品API消费者，未验证完整任务/扫码/导出Activity、真实外部SAF、USB/MTP/OTG或物理保管。页面覆盖限卡生成/预置及重新读卡。
- 清vault仅模拟注销，没有真实网络logout、进程死亡或设备重启；Room为全表排序逻辑digest，不是DB/WAL原字节证明。
- authority、回环响应、JCA、file URI、SKU/身份均合成，不冒充原设备/Keystore/真实服务/可信现场。未新增MySQL、实际服务HTTP、模拟器/真机、APK、完整集合、lint、安装、上传或部署结果。
- 仅此有界主机/文件链，不计完整O02/O04/U/D17/D18、第8节或最终候选。真实通道/Keystore/设备及统一候选仍未验。
- localDeletionAllowed=false。取消链、旧run03不恢复；原库/现网、正式签名、main、生产开关、部署和Release保持关闭。

## 保全和接续

- 收口依据原XML/原文件/命令/SHA，未重跑已过用例。closeout01保存运行环境、原字节、自审、清理审查及提交后全WIP断点。
- 替换前旧看板原字节在closeout01/preimage/，叙述另见[历史归档](ORCHESTRATION_STATUS_BEFORE_CARD_WORK_EXPORT_2026-10-22.md)；版本事实只见看板生成块。
- 清理结果见closeout01/cleanup-review.json，不冒领其他清理聊天释放量；输入/XML/日志/private向量/唯一证据/继承源码保持。
- 下一项先核任务/扫码/导出实际页面消费者缺口，复用未受影响输入，只补直接受影响组合；不重跑已过筛选或旧链。
