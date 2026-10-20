# 真实客户端路径与预检缺口评估

日期：2026-10-20；时间均以北京时间说明，原始回执保留UTC。用户本轮授权先评估M71，再评估真实HTTP；确认M71与PDA03无人使用，可独占设备并重新维护test。实际test部署仍未授权，生产/原库/现网/main/正式签名/Release不动，`localDeletionAllowed=false`。

## 阶段状态

**尚未完成真实演练，不放行。** 用户已确认 admin 为授权真实管理身份、01/02 为授权真实作业身份。M71 历史客户端验签/独立安装/实际打开通过；固定候选 Android 编译失败仍保留。新窗口已快照与完整备份，维护检查失败后按约定停止；未登记、登录或创建订单。test 已完整恢复并核同，不能把此次维护备份当真实演练输入。

本轮证据根：`.local/deploy-real-client-20261020/`。固定服务器候选：`8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd`。主开发树另有在途写者和WIP，不把其结果或改动混入候选。版本数字仅见看板生成事实块。

## M71 路径

- 设备序列号`22D2910042`，ADB为`device`；用户已明确无人使用，另一开发聊天不占用M71/test。
- 原debug应用前台存在，绑定本机开发服务，不改绑、不卸载、不清空。已正常force-stop后通过run-as只读保全其files/no_backup/shared_prefs/databases，归档2483200字节；原件与逐文件哈希仅存本机。AndroidKeystore私钥不导出，该归档不宣称能在卸载后恢复身份。
- 原debug包未更新。新装独立`com.acrylic.outbound.pda.staging`，不覆盖debug的UID、数据和密钥。
- 固定候选独立树执行`assembleStaging`，明确配置授权test HTTPS，调试签名，无正式签名：19:00:02–19:00:59构建exit1，10处Kotlin未解析引用，源码逐SHA不变。错误为`workVehicle`、`workRecentDetails`、`workCandidateDetails`、`content`布局字段和`TemporaryHandoverActionGate`缺失。**这是编译失败，不是环境缺失或跳过**；未修产品代码、未重跑凑通过。
- 独立从test既有发布目录取已发布客户端，SHA256 `c711b225ac8079290a55904c9541e77307aa2e26042ee17e24d7935d0217d4bc`；与发布材料记录一致，apksigner实际验签成功，证书SHA256 `2437447bae29492167a90d7dd2adaf696b226b05438f995475d4fff523e51196`。仅核验已有签名，没有调用正式签名。
- 已发布test APK安装exit0/Success，`am start -W`为Status ok，UI实际显示公网测试环境与设备登记页。只证明这个历史发布客户端的安装/启动；**不是固定候选Android通过证据**。新应用的Keystore挑战签名、真实登录和完整业务仍未验。
- 安装后再次只读保全原debug应用，11个原文件逐SHA与安装前一致；`device-preservation-check.json`的changedPaths为空。仅旧debug进程停下以保全，未清数据；新staging停留设备登记页，未发登记请求。
- 本机debug.keystore存在且沿历史哈希；这是APK调试签名材料，不等于设备内不可导出P-256身份密钥可用。后者只能由正常登记/认证签名成功证明，不能由文件存在替代。

## test 与身份前置

19:00:48只读重核授权test的服务/消费者/配置键名和用户状态，未读口令到输出。API/worker/timer运行；PDA03近期在线，但用户已确认可暂停。当前业务软件声明来源与JAR的历史核验见前报告，本次manifest哈希保持。

存在admin、01、02账户及bootstrap/test-users/seed_test_users来源文件。脚本创建账户只证明创建机制，不能单独证明或否定实际人员归属。用户已明确确认 admin 为本次授权真实管理身份、01/02 为授权真实作业身份；该回复保存在私有授权回执。未新造fixture用户、未重置口令、未绕过设备证明，真实登录与原机挑战尚未执行。

身份前置已确认；window02 实际快照/备份后维护验证失败并完整回退，详见后续记录。后续重开须新快照与新备份，旧维护备份不当演练输入。

## 真实HTTP路径判定

**真实HTTP调用本身不足以证明非合成。** 必须同时有授权真实人员、完整正常认证、正常产品命令与实际采集事实、可追溯订单号和时间线、导出/哈希及test还原证明。

已只读检查实际部署来源的PDA认证控制器及契约：PDA登录要求一次性挑战和登记设备私钥签名；冻结/提交还绑定原设备、人员、授权与审计事实。HTTP可以承担有权限的后台建单和确认，但不能用临时软件密钥、拼装DTO、SQL/seed注入或伪造扫码事件替代原机事实。

真实身份/真实采集来源条件未满足时，纯HTTP重放即使得到200、真实服务器订单号和完整表行，也不能满足本次闸门。当前仅静态评估，无业务HTTP写入，不以未实施认定路径永久不可用。

## 十九项跳过单列

全部来自固定候选保全的原XML，共19项，**计通过为0**；没有拿主开发分支后续通过覆盖。原XML均仅有空`skipped`元素，具体分类由同候选源码条件与原运行输入核对，不伪称XML有详细理由。逐方法/原XML哈希/源码哈希见本机`skipped-classification.json`与`skipped-tests.md`。

| 分类 | 数量 | 条件与限制 |
| --- | --- | --- |
| 环境配置 | 1 | `SkuParserParityVectorTest`未配置`SKU_PARITY_OUTPUT`，生成型测试未启用 |
| 原件条件未启用 | 8 | `RelayIndependentOriginalTest`未配置`E06_REVIEW_ORIGINALS`；不能据此断言原件不存在 |
| 归档条件未启用 | 9 | `RelayIndependentClosureTest`3项及`RelayIndependentCollectionTest`6项未配置`E06_CLOSURE_ARCHIVE` |
| Windows操作系统环境 | 1 | `SourceEvidenceIntakeFilesTest.symbolicLinkTargetCannotBeAccepted`创建符号链接遇FileSystemException后assumption跳过；XML未记录具体OS错误，不超证据归因 |

## 尚需完成与边界

身份归属已确认；M71原机签名与真实登录、业务演练/演练后导出、固定候选真实升级/还原/API-worker链均未完成；window02恢复核同仅证明本次维护回退，不是演练通过；候选Android编译失败与Linux Java运行未验另列。尚不能认定两条路径均不可行，不把真实库闸门标永久C档，也不将准备/设备在线计为验收。

配置/ACL/模拟器启动策略不改，没有重放被拒的模拟器命令。M71正常ADB路径按用户当次明确授权独立评估；如果该路径出现自身策略拒绝，保留原回执并停止，不换包装。


## window02 实际维护与回退（北京时间）

用户授权真实账号归属后，19:29:59 已新取 PDA03 订单号/领取/状态/最后同步快照；只读核对后台近时活跃网页消费者为0，其他登记设备无新近活动，PDA03 的暂停协调已获用户确认。订单明细只存私有证据，不入Git。另一开发聊天不占M71/test，且明确交出共享文档/HEAD写窗，只保留D13测试及私有证据；本轮未运行或重建其服务器测试环境。

19:35:50 开始备份，停API/worker及test备份timer，19:36:04新全量dump/私有文件归档完成，全部SHA及gzip/tar完整性通过；101表129500行、220个私有文件，结构/自增/逐行集合和逐文件摘要已保存。备份目录为test内 `deployment-evidence/business-20261020-02/before/`，未导出任何生产/原库数据。

19:36:32维护脚本 `nginx -t`及reload执行后，首次公网503断言失败，回执exit1。脚本没有保存该次实际HTTP值，不能补写其值；19:37:06再诊断为公网503/回环502（API已停），可能是重载生效时序，但根因未证。**按任一步失败即停止约定，本窗口中止，未继续独立还原proof、登记、登录、建单、采集、冻结、上传或确认。** 不用后续503改写首轮失败，不重跑原attempt。

19:37:07开始回退，先保全停止边界全库/文件到 `rehearsal-end/`；该名称不代表发生了业务演练。前后changedTables为空，未有业务写入。随后从新备份完整恢复库与私有文件，19:37:45逐表行集合/结构及全部私有文件SHA核同；原文件目录改名保全而非删除。PDA03订单/领取与维护前快照一致。

19:38:40原API/worker/timer恢复active，Nginx原字节及release保持，公网CSRF 200。PDA03同步入口已恢复；19:39:24采集尚无恢复后的新last_seen；后续实核19:41:59（UTC11:41:59.490）已再次联机，订单/领取继续与维护前核同。设备原代理设置未改、未建SSH/ADB代理隧道，新staging仍停登记页，原debug数据没有清空。

维护备份dump SHA256 `558acf1d53ebfd4e6f4abaa4cc555cc555db1e3fae77dc2688ee64563570a804`；私有归档SHA256 `76520ffee5b88e1a008ce3e90d13972ed3056b581ba20b6166a14e3773086e99`；文件清单SHA256 `173d942181f1f13e1ecd93c79da906a8d10ed42d4d41cbd7f330212337d39678`。来源是授权test维护前副本，**不是非合成业务演练后副本，不得用于放行真实数据闸门**。

原日志、SSH脚本/退出码、快照、完整dump与文件归档均保全；本机索引 `.local/deploy-real-client-20261020/window02/`，服务器原件留test证据目录。没有清理、产品代码修改、候选部署、正式签名、main或Release。

## 下一步

先核定首次维护检查的稳定观测条件；再次重开窗口须重新检查消费者、取PDA03快照并新备份，不覆盖window02。真实账号归属不再重复询问。M71原机挑战/真实登录和完整业务链仍未验，真实HTTP只能沿正常身份/签名/实际采集，不补造事实。尚无证据认定两条路径均不可用，不记永久C档；固定候选Android编译失败、19skip与LinuxJava运行缺口继续单列。

## 看板旧断点归档

- 本次维护已结束，不再停服；先解决客户端启动审批阻断，以后重开窗口须重核PDA03快照并新备份，不用旧截点覆盖新事实。未获得可信演练导出，固定候选真实库链不放行；代码部署仍须另行授权，其他持续关闭边界不变。

时间核对说明：首份final-comparison的PowerShell比较将SQL无时区字符串当本地时间，导致布尔值误报false；原始UTC值始终保留。后用显式UTC解析核对，实际last_seen晚于恢复时刻，修正回执保留原因，不改原快照。
