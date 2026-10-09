# D07/R09原准备包字节与扫描基线前置

本批输入`0a9529b8c7819f9332f23018026ea1d8e573cf15`，树D:/acrylic-pda-outbound、codex/development，单助手作者/自审。版本事实仅见[看板生成块](../ORCHESTRATION_STATUS.md)。只读准备前置不计D07/D13/D14整体或总目标通过。

## 实现与范围

`RecoverySourcePreparation`复用外部控制/独立pin读取结果，origin显式封装offlineToken、preparationReceipt、parts，保留原manifest字符串和原base64 gzip字节，不扩五类原件清单。历史公钥验原凭据与FORMAL/TEMP manifest用途、身份、摘要、期限、固定snapshot及revision；FORMAL plan/inventory另与签名凭据hash一致。分片严格集合/长度/hash/有界解压/行数；原库存及availability集合、bale重复、scan原产品字段/周期/可用状态、ACTIVE总数/克数和FORMAL计划上限校验。纳秒stateCapturedAt保留，原保存不可早于原准备时点。

只支持单个初次原凭据、第一轮，无交接/续作/恢复引用或处置链。FORMAL续期字段明确拒绝：原业务会复用首次准备包，不能把较后凭据窗口冒充原准备窗口；跨凭据准备绑定仍待实现。TEMP只核原分片字节、身份和scan库存基线，mapping/policy/rules的执行语义与ACK尚缺。Proof不提供完整作业资格、当前权限/库存或恢复写入授权。没有Spring bean、路由、DML、迁移、签发、Android/Web改动，双false保持。

## 验证与原件缺口

七个Java范围文件使用库外控制/文件/pin及内存开发RSA夹具，定向01为27全过、02为44全过；03受影响回归2938/0/0/1。自审拆scan总计并补明确续期拒绝后04为46/0/0/0，最终05为2939/0/0/1、119份XML、exit0/sourceUnchanged。Windows符号链接skip未执行；普通test不含IT。原命令/log/XML/result与源码前后hash保存在`.local/d01-d18-20261008/r09-source-preparation-*`。首次外层powershell -File数组传参失败未启动Gradle，直接调用脚本后执行成功。

新只读提取1151保留材料，01“Unique immutable original preparation required”失败保全。02完整报告matchingRows=0、totalRows=1，唯一准备属于接收assignment；迁移前后没有FORMAL原准备。三份输入前后hash一致、没有接数据库/私钥。报告`.local/d01-d18-20261009/retained-preparation-1151-02/missing-original.json`，originalPreparationVerified=false，不能借用接收准备包或补造原件。新消费者`VerifyRetainedPreparation.java`已去固定高水位但因缺原输入未编译/执行，不计原资料正向验证；未重跑1150/1151/UI-06或1100–1102。

设计与作者自审在`.local/d01-d18-20261009/source-preparation-design.md`及`source-preparation-self-review.md`。未取得新HTTP/MySQL/客户端/模拟器/APK/部署证据；独立工具不等于独立人员。原失败库、log/XML/datadir/dump/binlog与唯一原件保持。

## Git、环境与接续

接续快照`source-preparation-resume-01`保存33条全量dirty副本/hash/diff，22条继承hash一致；开发HEAD/实际远端一致，FORMAL final HEAD仍祖先。本聊天唯一活动写者；准备回归已收取exit0，无页面/模拟器专项，既有3306/13316/ADB不操作。C/D余量82566959104/99160350720字节是该快照时点值，提交阶段精确环境见precommit/postcommit。所有新增证据、TEMP/TMP/Java临时及可配构建缓存留D盘，无清理、本机证据未异地备份。

下一补原TEMP policy/mapping/rules及原ACK，只能消费可信原事实；FORMAL ACK原服务不签发，需固定原操作证据而非造新签名。之后新隔离原资料采集/独立pin、实际截点恢复及业务重建、主管围栏/接收最终提交。续期/高轮原激活、成功/取消/更正、产品入口和D08–D18仍缺，政策阻断/原资料/旧BCD/526/C01–C08不被局部通过覆盖。

生产默认/签名与七类生产决定能力关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。总目标active、未完成。

本批11条明确路径已提交推送`26a87b99bea1239dbb4de8d26556a301fb6d84ee`，实际远端一致，原commit/push与git-result.json在source-preparation-precommit-01。Windows PowerShell将git成功推送stderr当终止错误，中断收尾；单独ls-remote核实际远端成功，未重推/强推。提交后25条全量dirty/22继承保护在source-preparation-postcommit-02，C/D余量82071973888/99060047872字节；后续只补本页实际回执，不重跑本批业务。
