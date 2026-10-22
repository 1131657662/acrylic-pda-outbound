# 非空FORMAL原页面注销后本人卡导出与电脑消费

日期：2026-10-22。本片为开发备份，只补同一非空合成输入的页面/文件消费者，不记D/O/U或总目标第8节验收进展。版本事实见看板事实块。

## 范围与输入

输入提交431cbd5140fab7f4ff7bff8e944dea7e2a4a8cd6，分支codex/development。新增五个FormalPages测试；产品、依赖、迁移和schema未改。使用既有签名FORMAL fixture及WorkFixture生成VOID、ACTIVE和INVALID，不冒充实际扫码页面完整采集链。

原本人卡预置Activity实际保存/回读文件、声明保管后在线预置；停止本片Loopback authority，再经原设置页确认离线注销。原明细页在无登录时拒绝读取，仍能经只读入口到原救援页；选原本人卡解锁、读取原快照、生成并回读解密材料、声明保管、取消一次复制后重试成功。卡片/材料字节、原ID/序号/输入/事件守恒；包内无Bearer、refresh token、离线口令、本人卡口令、submission或receipt。

此处只有Robolectric NATIVE Room、合成JVM JCA keys、Loopback HTTP和file URI。不是实际Android Keystore、原身份、真机、SAF、USB/MTP/OTG或现场回执证明；不恢复任何已取消链。

## 实际验证

证据根.local/goal-20261022-resume/formal-pages-export01/；每轮输入、命令、环境、退出码、XML/日志独立保存，不跨轮拼接。

| 轮次 | 结果 | 说明 |
| --- | --- | --- |
| run01 | 未执行测试 | PowerShell拆分Gradle带点参数，命令失败；原日志保留，没有虚构测试计数或捕获退出码。 |
| run02 | exit1，未执行测试 | 测试误读WorkCapture字段，编译失败；改为context字段。1117项输入本轮前后不变。 |
| run03 | 同次2/0/0/0，exit0 | 新FORMAL页面消费者1项及已批准筛选守恒回归1项。1117项输入本轮前后不变。 |
| run04 | 同次2/0/0/0，exit0 | 自审将测试源码原始NUL改为等价Unicode转义后复验上述两项；1117项输入本轮前后不变。修前字节及SHA保留。 |

最终run04导出前后83张Room表采用SQLite存储类型及排序行摘要，均为0b6e6c2b06dd96ae3aff6cf7580c409ae7eaee55a74ec6ec8452ba9334be928a。3 captures、2 scans、10 events，ACTIVE 1包301000克、VOID 1包、failed 1条，原卡和材料字节不变。run03自己的摘要另存其XML，不与run04跨轮比较。

电脑端先消费run03：pc01 CLI完成但PowerShell后处理裸true报错，未声称捕获CLI退出码；独立逐字段及TSV核对记录保留。pc02正式捕获可信成功0、缺少包外信任拒绝1、既有目标防覆盖1，原输入与目标哈希守恒。最终run04/pc01重新消费run04新包，三场景退出码仍为0/1/1，逐字段/顺序/事件/汇总及防覆盖守恒。完整性和合成包外登记来源信任通过；businessReceiptVerified=false。

run04加密包SHA-256为f39eaa4fa5473d42b4c101c85b64b911d4d684349bff406839715f5dd2fc9e89，长度8616字节；包、解密材料、外部信任及原记录预期保留在各轮private-vector，仅在本地，不提交秘密或产物。逐输出SHA、工具SHA和原件比较见run04/pc01/result.json。

继承ContractFixture只增加无效/缺字段诊断，成功分支返回同一有效fixture；该继承改动不入本片提交。最终输入已记录其精确SHA，不把工作树测试说成干净提交测试。本片未构建/安装APK，不将历史构建替代本片验证。

## 自审、保存与清理

五测试自审、最终源码/输入/依赖及输出清单见closeout01。所有继承源码及未跟踪资料原字节保护；共享历史索引只暂存本片两个入口，继承七行保持工作树，不混入提交。文档改前字节/哈希在documents-before.zip，旧看板叙述另归档，生成版本事实不复制。

run01–03专用project-cache共5,716,448逻辑字节无损NTFS压缩，实际分配减少3,573,888字节，前后长度/SHA全部核同，删除0字节；D盘余量40,201,334,784→40,204,906,496字节。既有删除策略拒绝后未换通道重试。run04专用project-cache另有3,454,084逻辑字节，分配减少2,159,748字节，前后SHA/长度一致，D盘余量40,289,050,624→40,291,213,312字节。两次本片合计分配减少5,733,636字节，删除0字节；细节见run04-compact-before/outcome.json。不把其他清理任务释放量计入本片。原日志/XML、唯一证据、私有包、原资料/身份/Keystore、最终APK与继承改动均保留。

## 未验与接续

本片只完成有界合成主机页面/电脑消费者；原身份、设备/真实文件通道、现场、最终候选整合和总目标第8节闸门仍未完成。下一片先核TEMP原页面注销后本人卡非空导出组合的既有覆盖，仅补实际缺口，不重复筛选或已验文件消费者。总目标保持active；localDeletionAllowed=false，生产开关、正式签名、原库/现网、main、部署和Release保持原关闭边界。
