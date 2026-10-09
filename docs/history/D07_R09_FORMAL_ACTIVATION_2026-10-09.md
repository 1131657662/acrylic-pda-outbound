# D07/R09原FORMAL下载确认前置

本批输入`55316d04eeb715e6857e311003a7947921eec28b`，树D:/acrylic-pda-outbound、codex/development；单助手作者/自审。版本事实仅见[看板生成块](../ORCHESTRATION_STATUS.md)。本项只读核验原下载确认，不计D07/D13/D14整体或总目标通过。

原`PreparationService.Ack`无签名，不补造签名。新`RecoverySourceFormalActivation`必须取得库外固定pin和控制上下文，先核完整原准备包及历史公钥凭据，再核原command、未签名receipt、原assignment窄快照与原`ORDER_DOWNLOAD_ACK`操作事实。身份、仓库/订单/assignment/设备/人员、epoch/修订/snapshot、第一份offline授权ID/代次/窗口及持久确认时间精确绑定；审计原actor/order/type/id/request/details与命令匹配。原请求纳秒保留，审计/确认遵循原DATETIME毫秒精度；审计不得晚于确认，激活遵循原300秒容差（含数据库截断丢失的小于1毫秒边界），逐确认/保存不得早于激活。迟到的原确认不改变保存窗口。pin证明原件来源的前提不能由解析器自证，合成固定事实不代真实原身份、权限或占用。

首轮定向01为66/0/0/0、exit0/sourceUnchanged；自审对齐原request_id上限并补时钟截断/延迟确认/原授权反例后，最终受影响Recovery/Draft/Evidence/Preparation回归02为3012/0/0/1、122份XML、exit0/sourceUnchanged。普通test不含IT，Windows符号链接skip未执行。原log/XML/命令/hash在`.local/d01-d18-20261008/r09-formal-activation-*`；设计和作者自审在`.local/d01-d18-20261009/formal-activation-*.md`。新Java文件低于200行，verify正文低于30行，无数据库、HTTP、Spring bean、迁移、签发或客户端改动。

1151原FORMAL准备缺失沿原报告保留，未再次提取、未补造原件、未运行正向原FORMAL消费者；接收assignment不能替代原assignment。1150/1151/UI-06没有重跑，无新DB/HTTP/Android/Web/APK，未复用失败库。原命令回执、原审计/assignment可信采集、续期/高轮/跨凭据准备绑定、独立权限/占用pin和实际截点恢复至接收最终提交仍缺。此项只证明开发解析行为，不能以仅静态读取或合成签名外推实际恢复。

提交前后完整dirty/22继承保护及Git/进程/监听/磁盘见formal-activation-precommit-01/postcommit-02。TEMP ACK上项已推`55316d04eeb715e6857e311003a7947921eec28b`、实际远端一致，原回执在temp-activation-precommit-01。生产默认/签名和七类决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名，原资料/旧BCD/526、失败/skip和现场限制继续保留。
