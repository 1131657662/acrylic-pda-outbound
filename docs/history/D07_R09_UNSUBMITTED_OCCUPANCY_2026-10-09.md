# D07/R09 原未收正文 TEMP 占用与 E2A 绑定

本步输入为主树 D:/acrylic-pda-outbound、codex/development、ab5d0b9887032dddfc6246daa4cb09a746116f22。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。总目标 active、未完成；本步只补显式只读前置，不计 D07/R09 实际恢复通过。

## 范围与实现

- `RecoveryUnsubmittedOccupancyCapture` 在指定原 NORMAL 实例/schema/epoch启动独立 repeatable-read 只读事务，拒绝嵌入事务。仅首轮 TEMP、原 current submission、ABORTED、未收正文；核原身份与原回执字段，保留回执精确字符串。
- 原签名 E2A 独立选择公钥和 expected 来源，证明同一冻结正文、完整设备序号及下一 STOP；不能由无正文摘要的 ABORTED 回执自行推断正文。本路径不生成 STOP、ACK 或新回执。旧 `RecoverySourceOccupancy.verify` 仍要求终态正文摘要或 localFrozen 摘要。
- `RecoveryUnsubmittedEvidenceRows` 读取原集合 manifest 字节、三水位、itemCount、原 STOP 对象引用；读取全部 item 及其 object，保留原 object ID，逐仓/来源/轮/原操作者/原设备核对。LEFT JOIN 不过滤缺对象；完整稳定字段 digest 与原 E2A 比较，拒额外、缺失或错误引用。数据库 surrogate ID 只用于关联。
- manifestBytesSha256 是原 manifest bytes 普通 SHA256，setSha256 是 E2A 带域 SET 摘要；不混用 MANIFEST 带域摘要。原完整 item digest 使用 SubmissionJson canonical 数组（无末 LF）；外层观察使用控制 canonical（有末 LF）。
- `RecoveryUnsubmittedOccupancyRows` 读取全部 owner 列及库存事实，仅 SOURCE_RESERVED、原 scope/仓/set、原周期、upstream present、未扣账且无 block；所有链 owner 为空。ACTIVE 与原 scan/capture 保持；VOID 不产生占用。额外同 source/set/submission 占用拒绝。
- `RecoveryUnsubmittedOccupancy` 只消费库外独立 pin，绑定同一 source 的正文、占用内容、终态精确 bytes、原 E2A 与集合；结束重读控制和观察文件。底层无终态 hash 例外为包级，公开入口必须消费原集合观察。Proof 没有普通写权且 localDeletionAllowed=false。
- 外层观察上限 64KiB，ACTIVE 行上限 128（编码上限可能先触发），原 item 上限 65536，原 E2A 输入仍受既有大小限制。无迁移/HTTP/Spring 自动接线/签发/客户端开关变更。

## 当次验证与失败

设计、自审及原断点分别为 `.local/d01-d18-20261009/unsubmitted-occupancy-design.md`、`unsubmitted-occupancy-self-review.md`、`unsubmitted-occupancy-resume-01/`。

| 验证 | 输入/结果 | 范围 |
| --- | --- | --- |
| 定向01 | `.local/d01-d18-20261008/r09-unsubmitted-occupancy-targeted-01/`，120/0/0/0、4 XML、exit0/sourceUnchanged | 新入口、旧入口拒绝顺序、原 E2A 与 native 组合；合成身份/开发钥 |
| 1162失败 | 首次真实 MySQL 1/1/0/0、exit1/sourceUnchanged | 实际条码换入后夹具固定 rawInput 未换，RECOVERY_INITIAL_RAW_INPUT；同时自审发现原 manifest 普通 digest 与带域 digest 混用并修正 |
| 1163失败 | 新库 1/1/0/0、exit1/sourceUnchanged | 作者在启动前漏修 rawInput，再次在同一夹具校验失败；不作成功，不复用 |
| 1164 | 新库真实 MySQL 1/0/0/0、1 XML、exit0/sourceUnchanged | 非空原集合/全部 object 引用、原无hash终态、原 SOURCE_RESERVED，错实例、嵌入事务、恢复模式、水位/scan序号/STOP对象长度/周期/库存反例；每次观察与反例周围核全表/文件保持 |
| Node1164-01 | exit0、27输入 hash 不变 | 独立工具离线核 RSA/P256、canonical/pin、页链、原每条 bytes 与实际数据库全部引用/owner 列、冻结/STOP水位、全量前后快照；非独立人员/原现场 |
| 受影响普通回归02 | `.local/d01-d18-20261008/r09-unsubmitted-occupancy-regression-02/`，3568/0/0/1、169 XML、exit0/sourceUnchanged | recovery、temporary/evidence、temporary、devices、pairing；普通 test 非 IT，非完整普通server；符号链接 skip 未执行；新入口29项全执行 |

1162/1163/1164 已分别 dump 保全并独立重算、认证停机，原 PID 退出、19330/19434 空监听。失败 log/XML/datadir/binlog/dump 保留，不复用；范围 runner/source/hash/allocation/result/retention 在 `.local/d01-d18-20261009/unsubmitted-occupancy-mysql-1162/1163/1164/`。实际数据与原文件在 `.local/e2a-cross-20260925-1162/1163/1164/`。

1164 dump SHA256：`0B1B9D7D9B3F030819EFC0ABAA1C7F5882602F288736989C75D2DA5F8A5BA1E4`。独立消费者脚本 `verify-unsubmitted-occupancy.cjs`、命令/退出码/hash 与输出在当日同名 consumer 路径。没有新的真实 HTTP、Android、APK 或业务恢复证据。

真实库验证后只收窄方法可见性、收拢测试参数和新增普通/带域摘要区别回归；原 SQL、原 E2A/观察 bytes 不变。受影响普通回归已验证最终代码；不机械重跑 1164，不重跑 1150/1151/UI-06 或 1100–1102。

## Git、保护与接续

原 FORMAL final、磁盘 final 与 TEMP preparation postcommit 快照全部逐hash一致，两个旧 HEAD 是实际祖先。resumption 快照保存全部 26 项 dirty、22项保护、diff、Git、全进程/监听/磁盘；当前聊天唯一活动写者，其他项目聊天 notLoaded。未清理或混入继承改动。范围提交与最终全量断点以看板指向的 precommit/postcommit 回执为准。

原 TEMP 准备/ACK、原登记根、已收正文占用、原 E2A 验证沿对应已验输入复用。本步新增的是实际原未收正文集合/占用观察及消费，不能由它证明原权限连续历史、根/资料独立保管或真正截点恢复。

下一步将原未收正文观察接入同来源组合和实际恢复设计，补可信权限连续窗口的原边界依据；不能用两份观察、未见撤权或恢复库权限推断历史。随后完整原件独立 pin/保管 → 全新隔离实际截点恢复 → 原 ID/正文/序号/回执闭事务重建 → D07主管围栏/接收激活/最终提交/尾SQL/重启。不要继续堆只读观察而不接实际重建。

原 FORMAL准备缺失、高轮、续期撤销/跨来源及原资料/旧BCD/526/失败限制仍单列；D06/D07整体、D08–D18及原总目标第8节未完成。生产默认/签名及七类生产决定关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布/部署/正式签名/标签/Release，不绕设备/审批。
