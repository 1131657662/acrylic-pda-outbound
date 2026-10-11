# D07/R09 限定恢复 Android 原件耐久暂存

输入为主树codex/development、f5fa28d6ffe500cb937aaed2f1a80d03ffea0371与本批明确Android/测试改动。单助手唯一写者；resume-01/pre-http-01保存全部dirty、副本/hash/diff、Git、写者/进程/监听/磁盘，69历史比较与22原STOP保持。版本事实只见看板生成事实块；D01–D18及第8节未完成。

## 实现与边界

新增独立恢复暂存父表及有界原件chunks，保留原manifest/正文byte、source与target代次映射、本人会话/本地登录代次、签名key及限定资格摘要。Room非空迁移不伪造通用TEMP准备，不放宽原外键或NORMAL门。debug显式pdaIsolatedRecoveredHandover开关默认关闭，staging/release保持关闭。

下载在事务外；入库/精确重试/重读在真实Room事务中核身份、版本、期限、签名key、公钥文本摘要及RECONCILING围栏。原字节、RS256 FAULT_FENCE/APPROVE/PREPARE、源映射/高水位/基线与全部RETAINED清单核对；新target描述与旧source资格分开。所有能力false，不创建普通准备、confirmation、activation、guard或work；缺料或身份变化保留资料并拒绝。

既有交接页面按恢复围栏进入限定暂存，列示本人/机/仓库资料并仅提供核对；关闭开关时按钮不可用。页面代码已编译及lint，本批无模拟器页面操作/截图或真机验证，repository组合不代完整UI/真实Keystore验收。

## 当次验证

- 首次专项14测试全过、零失败/错误/跳过：原字节文件重开、精确重试/并发、payload插入失败整笔回滚、提交尾部围栏变化回滚、损坏拒绝且保留父行；旧用途/错代次/身份/仓库/基线/持钥/期限/能力、半下载和登录切换拒绝；非空迁移/FK/synchronous及有界大块重开通过。签名/会话为明确合成fixture，Room真实。
- 中间相邻范围57测试，零失败/错误，1条live HTTP条件skip。核实际契约后将公钥绑定修正为服务端的标准Base64公钥文本SHA-256；最终全量/HTTP均在修正后的相同输入运行，中间输入差异保留。
- Android全量703测试、169XML，零失败/错误、12条live HTTP条件skip，实际通过691。跳过清单received-download-android-skips.json保留；本项新恢复HTTP后续专项单独执行，其他skip继续单列。debug APK/lint通过；lint有Warning，未称零警告。
- 1325全新隔离目标只读重建原1233，非空迁移原行/files保持；前置服务专项1/0/0/0。后继服务JVM消费原LOGIN/pin，两个Android测试JVM分别1/0/0/0，实际PdaNetwork/产品GET→生产repository→native Room。首次实际payload响应后注入丢响应，无半包；重试/重复读及新JVM从原db文件重开，原manifest/正文byte一致，无新LOGIN/签发。
- 本人会话来自实际密码/P-256持钥LOGIN，Android vault/Keystore边界由隔离输入替代；开发公钥、运维签名及主管实物声明不代现场。服务JVM核前后151表/raw/files完全一致，正常关闭exit0；dump成功、认证shutdown=0、PID退出、任务监听空。1325保全不复用。1324守卫匹配既有fence目录拒绝，未建库/未绕守卫。
- 独立Node原生SQLite只读核两份db、7输入及10语义拒绝，原字节、三份RS256回执、会话/source-target/公钥摘要、空普通准备/confirmation/activation/guard/work及全服务快照通过，输入不变。工具消费者不代独立人员或现场。
- 最终全量/服务/两个Android HTTP各2768输入，运行内和收口逐hash无漂移。unit-01/02为中间输入，不代最终代码；服务生产源码未改，先前本人HTTP/普通recovery-handover证据按未影响输入复用，不机械重跑。

## 原件与保存

本机根D:/acrylic-pda-outbound；私有材料/数据库/产物不上传。

- .local/d01-d18-20261008/received-download-android-{compile-01,compile-02,server-compile-01,unit-01,unit-02,regression-01}/保存命令、源码hash/diff、日志/XML、结果及APK/lint；r09-temporary-evidence-restore-mysql-1325/与received-download-android-http-1325-{stage,restart}/保存实际组合。
- .local/e2a-cross-20260925-1325/保存原manifest/正文、HTTP原件、两份native Room、服务全量快照、原LOGIN私有输入及datadir/dump/binlog；.local/d01-d18-20261010/temporary-evidence-restore-mysql-1325/保存跨树分配、运行器/命令/hash、服务PID/退出和认证停机。
- .local/d01-d18-20261010/received-download-android-consumer-01/保存独立消费；received-download-android-design.md、received-download-android-self-review.md、received-download-android-close-verification.json保存设计/作者自审/核对。[收口前看板](ORCHESTRATION_STATE_before_android_stage_close_2026-10-11.md)保留原结论。

debug APK SHA-256为249ff0f376d1d40fbb477ac2a35f9f5866170f4d0811bafc3ca0b2b0b1025cf1，保存在regression-01/app-debug.apk；这是本项默认关闭开发构建，不是D18最终候选/正式签名。C/D余量及实际路径见快照；无清理，本机证据未异地备份。

## 接续

本项仅恢复资料耐久暂存。继续精确签名接收确认及全原表效果核对→激活→原前缀/最终提交；丢token后本人新LOGIN/替换、页面操作、其他无可信归档来源、D06/D07余项及D08–D18和全部闸门继续。原正文/ID/source代次/映射保留，旧票据不激活；普通TEMP/NORMAL、首轮/扫描/释放/删除及生产默认/签名关闭，localDeletionAllowed=false。

原1233只读、1293UNKNOWN hash保持，保全/失败库不复用。不重跑1150/1151/UI-06或1100–1102，不重启原设备、不借跨仓设备、不删原表守恒、不接原库/现网、不合main、不发布部署或正式签名。
