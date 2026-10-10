# D07/R09 受约束原端权限窗口

输入主树 D:/acrylic-pda-outbound、codex/development、0d75a89b01c107f769200d0fe8f56af5c1532143。沿既有原权限logged接口补显式受约束原源；本批不代业务恢复或D07/R09整体验收。版本事实仅见看板生成事实块，目标active。

## 实现与限制

`captureControlled`与权限同一FTWRL连接内采集原实际账号/GRANT、锁定状态、角色关系、存活会话及后台写者，不读密码/authentication_string。只接受一个活动schema DML账号，无DDL/GRANT/FILE/SUPER或动态管理特权；另外精确允许RELOAD/REPLICATION CLIENT/SHUTDOWN及必要列级只读。其他账号锁定、无roles/mandatory roles、event scheduler关闭、无复制写者。

锁定账号不关闭旧特权连接：采集要求只有当前连接，未知或其他前台线程拒绝；只排除明确的MySQL GTID压缩守护线程（名称、无用户、Daemon三条件）。当前连接还必须对“保持原值设置sql_log_bin”返回准确权限拒绝，防止同账号旧登录缓存特权。原两端profile完全一致，原日志CRC/连续prefix/位置/ROW/XID/半开commit及权限表修改还原拒绝沿原实现核验。

`verifyControlled`只在上述原观察独立pin/日志验证完成后给出受约束历史窗口结论；旧`verify`继续false。Proof改为私有构造、不可由caller布尔值造历史结论。普通写入/本地删除false，没有接Spring bean/HTTP/生产开关、签发/续期/closed业务DML、迁移或客户端。外部pin和原OS/管理员保管仍是明确可信输入；本机测试不能证明恶意OS管理员无篡改、原人/原设备或真实异地保管。

## 验证与失败保全

- 原定向01（继承证据）56全过；补旧连接/后台反例的定向02为59/0/0/0、2XML、exit0/sourceUnchanged。其后补当前连接缓存特权反例、Proof私有构造，recovery普通回归03为2910/0/0/0、70XML、exit0/sourceUnchanged；非IT、非完整server。随后只整理数据库IT长方法，定向04为60/0/0/0、2XML、exit0/sourceUnchanged，核编译/反例；不重跑一次性1174。
- 1170真实MySQL1失败：锁定root仍能用旧连接切换session日志已实际证明且捕获拒绝；正向因内部线程被误计拒绝。1172仍失败，实际线程原件显示GTID压缩守护线程有非空PROCESSLIST_ID；随后按三条件排除。两库保留，不能复用。
- 1171被跨33树目录子串分配守卫拒绝，命中既有fence目录；未建库，原拒绝记录保留，未放宽守卫。
- 1173失败：受约束source capture已过，但原schema触发器由运行账号定义，撤TRIGGER权限后业务DML被拒绝。1174改为初始化root迁移，原触发器由随后锁定的root定义，不给运行账号DDL/TRIGGER权限。失败库/schema/log/XML保留。
- 1174真实MySQL1/0/0/0、1XML、exit0/sourceUnchanged；独占原会话/锁root旧连接拒绝、不能关session日志/改format/global/GRANT/解锁root/写mysql表、一笔业务ROW与权限表修改还原拒绝。非空全表/私有文件快照只有指定submission一行status/receipt改变，其余保持。
- 独立Node日志8输入、数据库4输入、profile4输入均exit0/哈希不变；日志正向1事务/1ROW、权限修改还原2事务/2ROW。官方mysqlbinlog两份原prefix校CRC均exit0/输入不变。消费者是独立工具，非独立人员/原现场身份。

证据：`.local/d01-d18-20261008/r09-controlled-permission-*`（原命令/hash/log/XML/result），`.local/d01-d18-20261010/controlled-permission-mysql-<run>/`（跨树分配、provision、runner、dump/认证停机），`.local/e2a-cross-20260925-<run>/`（datadir/binlog/原canonical边界/pin/schema/全表文件快照），`controlled-consumers-1174-01`（独立消费者原输出）。1170/1172/1173失败和1174均dump重算一致、认证shutdown0、原PID退出、19330/19434无监听。1174 dump SHA256为`EB9DD3C7BDA3AF0C790A51C5D8CD7C9A0BDACC29EE5918507B3F2B91B805F763`；数据dump不含触发器，单独原schema dump完整保留触发器定义，不能将前者称完整结构备份。

## 接续

规则、看板、final/磁盘final/后续postcommit和全部dirty已核。`controlled-permission-resume-02`保存33条dirty副本/diff/hash、全进程/监听/磁盘、项目聊天状态；原final两组44次hash比较、22保护项和后续26路径一致，原HEAD均祖先。当前聊天唯一写者，继承路径不混提交。1150/1151/UI-06及1100–1102不重跑；既有3306/13316/ADB不操作，所有新产物在D盘。

下一步按`.local/d01-d18-20261010/temporary-preparation-restore-design.md`实际恢复原准备截点：新源dump→另一个新空目标→原5分片/ACK closed事务补齐→幂等/异原件/尾SQL/提交前变更回滚/重开；旧授权/epoch/基线不改且RECOVERY_PENDING。随后结合独立原权限、原E2A/占用/终态接正文重建、主管故障围栏、接收激活/最终提交；不能再无限堆观察代实际恢复。

D06/D07整体、FORMAL产品入口各阶段、D08–D18和总闸门未完成；原FORMAL准备缺失/续期撤销/高轮/跨来源、政策阻断、原资料/旧BCD/526、失败/恢复限制和C01–C08保持。生产默认/签名/七类决定关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名，不绕设备或审批。
