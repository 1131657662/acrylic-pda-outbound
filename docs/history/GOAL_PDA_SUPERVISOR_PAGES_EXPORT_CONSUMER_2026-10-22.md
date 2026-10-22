# 非空原订单页面主管新卡导出与电脑消费

日期：2026-10-22。作者自审与独立电脑工具消费者；没有独立人员审查。本片只补有界合成组合证据，不计D/O/U或总目标第8节验收进度。

## 缺口与输入

输入提交33595c7ed14533ec2b91c8e5d529ea8ac4c95090，树D:/acrylic-pda-outbound，分支codex/development；完整测试输入含继承WIP，不能称clean HEAD验证。版本见看板事实块。

旧[E03主管Android](GOAL_E03_SUPERVISOR_ANDROID_2026-10-12.md)与[委托救援](GOAL_E03_DELEGATED_RESCUE_2026-10-12.md)已有手工主管口令的非空导出/电脑证据。本片补的是后来新增的随机主管卡生成、重选回读和读卡入口，与非空原订单页面、独立解密材料及电脑消费者的同输入组合；不是重新宣称整个主管功能首次具备非空证据。

新增SupervisorPagesCardSetup.kt、SupervisorPagesExportEvidence.kt、SupervisorPagesExportTest.kt三个测试；既有RescueSignedSupervisorFileActivityTest.kt只为原断言添加实际消息，不改变成功条件。不改产品源码、权限、协议、依赖、schema或迁移。试验过的fixture静态工厂reset未解释失败，已恢复本片前原字节。

证据根：.local/goal-20261022-resume/supervisor-pages-export01/。baseline.json、inherited-wip.zip保存接管40项展开继承WIP和原字节；closeout01/source-review.json确认文档维护前40项全数未变，以及最终1125项输入无漂移。相较上片输入只有旧测试诊断断言改变、产品变化0。完整输入、命令、环境、退出码及逐方法统计见各run目录与closeout01/verification.json。

## 实际组合与边界

TemporaryWorkFixture只构造合成非空TEMP原件：3 captures、2 scans、12 events，ACTIVE 1包/301000克、VOID 1包、failed 1条。它没有经过扫码硬件或原扫码Activity。另在populate前建立同原人机仓但未获主管授权的空草稿，并重新载入原单，保持原输入守卫。

合成RSA授权绑定原设备、登记公钥摘要、原操作员、仓库和唯一原TEMP单。原签名文件页面→专用随机主管卡保存/重选回读→人工保管声明→原在线GET与加密vault→停止回环服务→原设置页注销→原TEMP详情只读入口→选主管卡→原快照读取→独立随机解密材料保存/回读/保管声明→复制取消及重试→电脑CLI。

授权列表只含原单。注入未获授权草稿为preferred后，原readSnapshot明确拒绝“救援凭据不适用于本单”，canGenerate/canCopy均false，没有新增包。原操作者未被救援人替换；包内未出现卡口令、包口令、Bearer/refresh、主管token或私钥。原主管卡、签名文件及解密材料保持；注销后vault为空，凭据输入框为空。

Robolectric NATIVE Room、合成JCA身份、MockWebServer和file URI不是原身份/Android Keystore、真实服务端、SAF、USB/MTP/OTG、实体设备或物理保管证明。独立电脑进程不等于独立人员验收。继承ContractFixture及其他WIP仍参与已记录工作树，不混入本片提交。

## 五轮结果分别记录

| 轮次 | tests/failures/errors/skipped | exit | 结论 |
| --- | --- | --- | --- |
| run01 | 16/2/0/0 | 1 | 新夹具在失败输入后创建另一草稿，原守卫正确拒绝；旧签名文件正向另失败 |
| run02 | 16/2/0/0 | 1 | 创建另一草稿替换内存catalog；需重新载入原单；旧正向仍失败 |
| run03 | 2/1/0/0 | 1 | 产品正确拒绝越界，测试误期待“范围”字样；旧正向单方法通过 |
| run04 | 16/1/0/0 | 1 | 新组合与主管卡通过；旧正向明确SQLITE_CANTOPEN/code14 |
| run05 | 16/0/0/0 | 0 | 同次三类全过，作为最终本片输入结果 |

各轮1125项输入前后均不变；不跨轮累计通过数。run04诊断增加了新依据：同旧方法单独通过、整类失败，原日志首次明确code14，历史也有短TEMP恢复记录。run05只将子进程TEMP/TMP/java.io.tmpdir缩短到.local/s05；未改全局环境、NATIVE模式、产品或断言。通过与路径假设相符，但没有做受控因果实验，不声称已确定所有SQLite失败根因。

原失败日志/XML保留，另无损归档closeout01/original-test-evidence.zip。未再次重跑旧取消链run03；此处run03仅为本片独立证据目录。

## 最终同输入守恒与电脑结果

run05全部83张Room表使用SQLite存储类型和排序行摘要，前后均00e457986115ffa70553b6ac089b797e6fa63ff8cee2bf6432c530fb3e2fc4a6。最终加密包8120字节，SHA-256为9a64e0a1d551ec1d4e15a2ddc6efa4f8ca18c678e29386e5da0bd01b1df7f94e。

run05/pc01独立新进程的三场景退出码：可信输入0、缺包外信任1、既存目标防覆盖1。原captures/scans字段与顺序、事件ID、来源、summary及TSV文本字段逐项核同；原输入及既有目标哈希不变，不可信目标未创建。integrityVerified=true、sourceTrusted=true、businessReceiptVerified=false；无submission/receipt，不能称业务确认。run04也有独立PC结果，分开保留，不冒充run05向量。

密包、解密材料、包外合成信任及预期原行留在私有.local，不提交。命令和全部输入/输出哈希见各pc01/result.json及closeout01/verification.json。

## 保存、清理与接续

文档原字节及SHA在closeout01/documents-before.zip和document-preimages.json，旧看板叙述另归档。五处本片专用Gradle缓存共17397378逻辑字节逐文件无损NTFS压缩，减少实际分配10851970字节；全部长度/SHA保持，删除0字节。D盘余量39574126592→39585021952字节；命令、绝对路径和结果见cache-before.json、cache-outcome.json与cache-compact.log，余量还受系统并发活动影响。

已批准四状态筛选沿原证据，不重复实施。原资料、身份/Keystore、原回执/签名、唯一证据、最终APK和继承源码保持。未连接SQL/原库，未构建或安装APK，未正式签名、部署、Release或合main。

本片开发备份不代表最终候选；下一步先核候选输入与继承编译依赖，按总目标D18及实际缺口安排组合验证，避免继续用微切片代替整体验收。原身份/设备、外置通道与现场回执保持外部待验；其余D/O/U开发缺口和最终候选闸门仍须独立满足。总目标active、未完成，localDeletionAllowed=false及生产边界不变。
