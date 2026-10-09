# D07/R09原TEMP激活ACK前置

本批输入`5f029bcc3e4210a9f589250d5b7d5d9e3645cce2`，树D:/acrylic-pda-outbound、codex/development，单助手作者/自审。版本事实仅见[看板生成块](../ORCHESTRATION_STATUS.md)。这里只收口原TEMP ACK只读前置，不计D07/D13/D14整体或总目标通过。

`RecoverySourceTemporaryActivation`先验证完整原准备包、政策和计划基线，再核原ACK command/receipt、历史公钥用途、身份/epoch、bundle/manifest及原请求摘要。摘要复用原生`TemporaryCommands.Ack`字段声明顺序和Instant序列化，保留原请求纳秒；回执激活时间遵循原生毫秒截断。激活须在原准备期限内、不得晚于原确认/保存；原回执遵循300秒时钟容差。延迟ACK可晚于到期，但不延长原保存窗口、不产生今天的作业权限。原origin允许显式activation，旧准备证明不解释ACK。无数据库写入、HTTP入口、Spring bean、迁移、签发或客户端变化。

定向01为72/0/0/0；最终受影响Recovery/Draft/Evidence/Preparation回归02为2982/0/0/1、121份XML、exit0/sourceUnchanged，原XML独立重数一致。普通test不含IT，Windows符号链接skip未执行。原log/XML、命令与源码hash在`.local/d01-d18-20261008/r09-temp-activation-*`，作者自审在`.local/d01-d18-20261009/temp-activation-self-review.md`；测试使用开发RSA及合成原件，不代原身份或可信状态。

新只读提取保留1151非空TEMP准备包与原ACK，输入三份hash不变；读取`private/android-http-input.json`仅取公开activationCommand/activationReceipt，报告明确`privateInputReadForPublicAckFields=true`，未加载、复制或使用私钥。提取目录`retained-temp-activation-1151-01`和新公钥消费者`retained-temp-activation-consumer-1151-01`均在`.local/d01-d18-20261009/`，消费者compile/run exit0、三份输入hash不变。原ACK请求摘要`cadbafbdf27faa7cb1c3a9c21961a2816b542bed9c786fc080ce265d7b05243f`，原激活与确认分别为`2026-10-09T01:09:18.719Z`/`2026-10-09T01:09:18.748Z`。报告的control/pin/scope/terminal/原身份/权限/占用证明仍false；独立工具消费者不等于独立人员，也不计实际恢复。未重跑1150/1151/UI-06，没有新DB/HTTP/客户端/APK。

接续先收FORMAL原下载确认：该服务原ACK没有签名，须核可信原`ORDER_DOWNLOAD_ACK`操作事实、原响应和assignment，不能补造签名。1151原FORMAL准备缺失继续保留，不能借接收assignment替代。续期/高轮/跨凭据、独立原状态pin、截点恢复与接收最终提交仍缺。

看板压缩前旧结论见[断点归档](ORCHESTRATION_STATE_before_temp_activation_2026-10-09.md)，完整原看板/31dirty副本及22继承hash核验在temp-activation-resume-01；提交前后精确Git、全量dirty、进程/监听和磁盘另存本批快照。生产默认/签名和七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名，不重跑1100–1102、不复用失败库。失败原件、原资料/旧BCD/526及现场限制继续保留。
