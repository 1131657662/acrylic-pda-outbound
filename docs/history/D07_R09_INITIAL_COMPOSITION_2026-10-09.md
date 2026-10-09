# D07/R09 第一轮同一来源组合核验前置

输入树 D:/acrylic-pda-outbound、codex/development、976d06b5f565a16e4351abddf9668af921abdd28，加本批六份Java。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。设计、自审、Git/环境及继承保护在`.local/d01-d18-20261009/initial-composition-design.md`、`initial-composition-self-review.md`和`composition-resume-01`。本聊天单写者，无独立人员审查。

## 实现与退出标准

`RecoveryInitialSource`从库外manifest独立pin及显式预期身份重读同一五类原字节，在同一来源上核原准备/ACK、逐保存资格、ABORTED终态、全量连续审计、历史权限与原占用；完成后重新读取固定原件和控制。接口不接受调用者拼装的通过Proof，无缓存、自动取pin、Spring bean、HTTP、DML、迁移、契约、客户端或签发变化；返回摘要及计数，无普通写权或删除权。

`RecoveryInitialRecords`区分设备deviceCaptureSeq与来源orderScanSeq，正文captureHighWatermark对应来源水位；来源从1连续、设备序号严格递增，分别与独立水位匹配。原正文submission/source、审计全序列/原事件顺序、summary与有效数绑定。`RecoveryCaptureHistory`逐原capture重放既有合法迁移，要求原接收/确认意图/保存localSeq、失败确认/原因、VOID实物事件与原rawInput一致。原权限和占用仍由已有验证器重验，不能由恢复DB反证历史。

本子项支持同一来源、第一轮、未续期、ABORTED冻结正文；TEMP/FORMAL非空原编码、签档前缀、手输/广播、合法失败/放弃/VOID及带原摘要的LOCAL冻结正文有正向组合。错原身份/正文/双水位/序号、缺ACK、未知公钥、跨原件权限、竞争占用、错终态、缺capture审计/乱序/错保存引用/失败原因均拒绝。返回只读证明不证明原设备签名、独立保管取得过程或现场事实。

## 当次验证

真实本机文件和库外控制、内存开发RSA/合成身份夹具。定向01为40/26/0/0：夹具更新manifest后再次发布仍按旧简化身份读取，及合法VERIFYING被错误期待拒绝。改为直接发布一致原件，02为40/1：非法epoch变化先被控制历史拒绝，修期待后03为40全过。扩边界04为46/3：TEMP短装夹具缺必要原原因，补原字段后05为46全过。自审补LOCAL/未知钥/原source/广播审计与ASCII输入边界，最终06为52/0/0/0、3XML、exit0/sourceUnchanged。

受影响Recovery/Draft/Evidence/Preparation普通回归07为3080/0/0/1、125XML、exit0/sourceUnchanged。普通test不含IT，Windows符号链接skip未执行；全部原命令/log/XML/source前后hash/result在`.local/d01-d18-20261008/r09-initial-composition-*`。失败轮均保留，03之后新增覆盖后继续复验；不复制旧XML冒充当次执行。

无新HTTP/MySQL/Android/Web/模拟器/APK/真机/部署证据，没有正向保留原FORMAL组合消费者。1151原assignment准备缺失仍保持，不能借接收assignment或补造；1150/1151/UI-06和1100–1102未重跑，失败库未复用。作者自审不等于独立人员，独立工具及开发签名不等于原身份。

## Git、环境与接续

composition-resume-01保存刷新事实后的26条全部dirty副本/hash/binary diff/Git/进程/全部监听/磁盘，22继承逐hash一致；输入实际远端与HEAD一致，续期976d06b已推送，未重造提交。既有3306/13316/ADB不操作，本批无新数据库和产物清理。临时/Java/cache/日志和证据仍D盘，本机证据未异地备份。明确路径暂存检查及真实commit/push回执见`initial-composition-precommit-08`，提交后精确SHA、dirty和环境见`initial-composition-postcommit-09`。

下一批：设计并实现故障前原准备/ACK/原设备证据、权限区间和占用采集点及独立pin保管；全新跨树分配隔离编号后实际截点恢复→原ID/正文/顺序/回执重建→D07主管围栏→接收激活/最终提交、尾SQL回滚和重启。FORMAL现有入口依赖原recall/package、TEMP依赖原E2A集合和原验签根，组合证明不能替代这些原件或补造STOP/ACK。原机不可用与未决实物仍须主管有权记录。

续期撤销/替代窗口、原续期操作审计、高轮/混合/跨来源、成功/取消/更正及D08–D18仍必做；D06/D07/R09和总目标active、未完成。D04/D05政策缺口、原资料/旧BCD/526、历史失败/恢复限制和C01–C08保持，不转现场或外推局部通过。生产默认/签名与七类生产决定能力关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
