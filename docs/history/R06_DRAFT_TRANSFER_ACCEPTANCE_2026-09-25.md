# R06 Room26草稿原键与原回执耐久恢复验收（2026-09-25）

接V37服务器原件接收，本项实现Android仓储恢复链；尚未开放产品按钮/自动任务或接入扫码队列中的停止操作。两树仍未提交、推送、整合、main合入或部署。

## 实现、自审和兼容

- Room25→26新增local_draft_transfer/request/receipt三表。迁移不补造旧请求、回执、停止或签名；保留Room25同事务签名原件。
- 明确prepare在同一Room事务创建OPEN、每件ITEM、VERIFY原键及规范请求、逐请求SHA和集合摘要，随后回读校验。重复prepare/重开复核原计划；已有不完整计划或缺请求失败封闭，不补键。此传输计划晚于停止签名，不称原停止事务的一部分。
- LocalDraftRecoveryRepository由PdaApplication单例持有。每次恢复先核全部本机停止资料/原签名/请求集合，再按顺序处理；网络不持Room事务。RESULT_UNKNOWN先耐久写入，按原键GET；只对DRAFT_EVIDENCE_NOT_FOUND的404重发原请求，其他404/权限/网络错误不换键。
- 原响应先在独立Room事务存为RECEIVED，再验签存VERIFIED。坏回执原字节保留，不重发覆盖；重开重新核可信当前或原准备签名key、规范JWT/base64、完整原响应、来源/操作/原键/请求SHA/根/计数/false能力。所有请求均核验后只返回ORIGINALS_STORED，不改变停止状态、S/K、实物或删除能力。
- PdaSession/TemporaryConnection的Original补RAW字节透传，延续身份刷新边界；未改既有普通JSON行为。
- 自审补计划事务内再次绑定stop与原signature，校验原键唯一及集合摘要；VERIFY比较本机own/other/archive数量，不能仅相信签名中的自报统计。应用单例只串行本仓储；产品扫码与停止同写者接线仍需下一子项。

## 当次验证

- 首轮定向51/51；新增传输5、恢复6、非空25→26迁移1，连同原停止39项通过。
- 首全套420=416通过/4专用HTTP跳过；回执自审补4测试后最终424=420通过/4专用HTTP跳过，106XML。包含有效签名但错误来源/原键/能力/数量、未知key、篡改RAW、已核验回执重开复验、故障回执保留、身份变化后先查原键、分批恢复及无网络事务。
- attempt57使用独立非空V34→V37 MySQL19330和真实HTTP19434，IT1/1与Android1/1。Android用Room26实际原键/原请求和仓储恢复代码，OPEN/ITEM/VERIFY各在服务器提交后丢一次响应，Room重开后继续同键，原机只签1次；原响应逐件与服务器原字节相同，所有状态VERIFIED，不重复POST。服务器重启/恢复模式/坏文件检查同场通过。
- 服务端生产输入未改，完整577项是上一接收子项报告；本项未另称全服务端重跑，只通过attempt57实际重编译/IT。
- Android APK及lint通过，0错误/0fatal/186既有warning，code20/Room26。APK SHA256 a339c78f524b28d6bef73fa3088daafd58533c188bd4d81387c23d4a57abc3ab。
- 当次无测试失败；首全套HTTP测试File?编译warning经显式非空检查消除，attempt57及最终全套复编通过。前项focused1/attempt55等历史失败保留。
- 当前助手作者/自审，无独立人员；Android原Room→Java为独立语言组合验证，合成身份/设备签名边界不冒称真机Keystore。

## 证据与后续

outputs/r06-e2a-takeover-20260925-01a0d612/android-draft-transfer-*、draft-transfer-attempt57-*，完整attempt57库/原HTTP响应在服务端.local；private不复制普通证据/Git。精确输入manifest和全部未提交路径见看板及R06_DRAFT_TRANSFER_WORKTREE_STATUS_2026-09-25.md。

57已认证关闭，专用19330/19434/19435/19436无监听；共享3306/PID5448不动。下一run从58核目录。

下一接产品停止/补传入口与持久任务队列；停止必须排在同一TemporaryEngine写入队列中，先拒收新输入，再耐久处理之前已接收输入，不能仅检查queued==0后在其他线程停止。其他来源可信业务事实、归档历史信任、R07–R09及完整V37恢复继续保留未完。真机/Keystore/OS死亡断电/现场/长时/审批未验；七类生产决定、TEMP生产归档、放行/删除关闭，持续目标active。
