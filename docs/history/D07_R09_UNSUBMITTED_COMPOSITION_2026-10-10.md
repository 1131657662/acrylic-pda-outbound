# D07/R09 原未收正文同来源组合

输入为 D:/acrylic-pda-outbound、codex/development、3653d72f65d2c9ab47f54e5ee75e98517ae559b8。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)，总目标 active、未完成。

`RecoveryInitialUnsubmittedSource` 是显式离线组合消费者，仅原首轮 TEMP、空 archive 前缀：同一 source pin 原五件/独立 expected，原准备/ACK、原资格逐确认保存、原签名 E2A/冻结与下一 STOP、历史权限窗口语义、原未收正文观察独立 pin/集合及 SOURCE_RESERVED 一次核验。结束重读五原件、控制及占用观察，返回所有原 hash、source pin、occupancy pin、setHash、水位计数；没有写权或删除许可，不改原无hash ABORTED回执。旧组合入口不放宽，无 SQL/迁移/HTTP/Spring 自动接线/签发/客户端开关变更。

设计/作者自审在 `.local/d01-d18-20261010/unsubmitted-composition-design.md`、`unsubmitted-composition-self-review.md`。验证均为合成身份与开发签名材料：

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 定向01 | 80/0/0/0、4 XML、exit0/sourceUnchanged | `.local/d01-d18-20261008/r09-unsubmitted-composition-targeted-01/` |
| 最终recovery普通回归02 | 2850/0/0/0、68 XML、exit0/sourceUnchanged | `.local/d01-d18-20261008/r09-unsubmitted-composition-regression-02/` |

新组合13项全执行，覆盖正向、缺ACK、原窗口缺失/撤权、异E2A、异占用、五原件替换、已收正文及FORMAL拒绝。回归范围只是 recovery 包，普通 test 非 IT，非全部普通server；没有新 MySQL/HTTP/Android/APK、独立原组合消费者或业务恢复证据。前步1164真实SQL/独立消费者按原输入复用未重跑；局部通过不外推。

Git按五条明确路径（产品、测试、本页、验收索引、历史索引）提交推送，回执与全部dirty/22保护/进程监听/磁盘见看板指向的precommit/postcommit。看板本轮断点单独保留，不混继承改动。

权限组合只重验 independently pinned 窗口的业务语义，不自动证明原权限连续历史。用户/设备版本不足以覆盖 role_permission、user_role、user_warehouse、warehouse status 等全局变更；operation_event.server_time 为事务内时间，不能代 commit 边界。原1164 binlog的只读解码发现 actual commit microsecond timestamps/Xid 存在，但对应样本作业时间早于该binlog的启动时间，**不能**用它证明该样本历史权限；边界观察原输出/hash在 `.local/d01-d18-20261010/retained-1164-binlog-observation*`，非权限通过证据。

下一步选择可验证的原权限连续边界：匹配原committed snapshot与日志坐标，完整保全 ROW binlog 的全部事务边界，覆盖人员/设备/角色权限/仓库变更，缺日志或未知格式必须拒绝；不能由两个相同快照、未见撤权或恢复库补窗口。随后将资料独立pin/保管、全新隔离截点实际恢复与闭事务重建接至D07围栏/接收最终提交/尾SQL/重启，不能无限增加只读前置。

原FORMAL准备缺失/高轮/续期撤销/跨来源、D04/D05政策、原资料/旧BCD/526/失败限制及C01–C08继续单列。D06/D07整体、D08–D18及总计划第8节仍缺。生产默认/签名及七类生产决定关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名/标签/Release，不绕设备或审批。
