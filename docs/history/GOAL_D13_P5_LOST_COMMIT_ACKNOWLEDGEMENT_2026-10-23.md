# D13 P5 真实提交成功后回执丢失：有界验证

输入 `f9f4bc061435fa92441ba0c2d0e1df5867c5d872`，树 `D:/acrylic-pda-outbound`、分支 `codex/development`。仅测试、隔离运行器和独立原件消费者变化，产品实现、配置、契约、迁移及 Android/Web 未改；版本事实见看板生成块。总目标 active、未完成，本片不是 D13 整体、完整恢复应用准入或最终候选验收。

## 判据与实际结果

按总目标第3.1节已授权默认，复用测试边界 `LostCommitDataSource`：真实 MySQL driver 的 commit 已返回成功后，才模拟丢失确认的 SQLState 08007；不增加产品故障开关。安装身份及旧 writer admission 为 **MODEL 夹具**；P5 Authority、JDBC、MySQL、独立进程、OS owner 锁及产品 halt 为实际执行。合成身份不能证明原身份或实际错库准入。

新隔离 run1657 的专项 **1 测试、0 失败、0 错误、0 跳过**，3.386秒，Gradle exit0；独立 Python 原件消费者 exit0。具体行为如下：

- 正常事务由独立连接观察：提交前0行、提交后1行，driver commit计数1；子进程插入第二行但尚未提交时仍只能观察1行，P5效果门正向允许且OS owner锁持有。
- 子进程真实commit后观察2行，产品输出围栏四阶段日志并实际halt70，owner锁释放；未生成“commit正常返回”标记，UNKNOWN原记录存在。
- 扫描前保全完整P5原树7文件。旧句柄先拒绝 `RECOVERY_LEASE_OUTSTANDING`，再拒绝 `RECOVERY_LEASE_FENCED`；两次delegate和观察行数增量均0。
- 同JVM新Authority严格拒绝FENCED，符合恢复根缓存不可逆本地围栏的合同；独立新JVM严格拒绝OUTSTANDING、exit1，无 `EFFECT` 标记，行数保持2。
- 扫描后6文件：只有孤儿lease退休及index变化，其余路径、长度、SHA保持。原lease/process/index的HMAC及相互绑定由独立消费者核验；**UNKNOWN仅为未签名审计记录，没有HMAC**，不称签名原件。
- 独立SQL原输出严格为两行；离线binlog恰有该隔离表的id1/id2两次INSERT，无UPDATE/DELETE，未出现被拒绝的id99效果。启停前认证端口、datadir、数据库、账号及UUID相同，认证shutdown exit0，端口19833释放。

原件消费者仅读取文件，不调用产品observer或决策API；它与作者自审由同一助手执行，不冒充独立人员审查。没有运行真实HTTP、Android、APK、模拟器、真机或部署；本片不是完整恢复组合。

## 执行与失败保全

证据根 `.local/goal-20261023-resume/d13-p5-lost-commit01/`。每次attempt使用全新证据、构建、隔离库及随机凭据；不覆盖旧日志/XML/datadir，不连接系统3306/33060或原库/现网。

| 输入 | 结果及定性 |
| --- | --- |
| run1655 / 19831 / `.local/s23g` | 编译exit0；IT1/1/0/0、0.072秒。默认server工作目录触发nearest `.local`守卫，尚未进入SQL/P5。只修运行器显式ROOT工作目录，不弱化守卫 |
| run1656 / 19832 / `.local/s23h` | 编译exit0；IT1/1/0/0、2.405秒。真实commit/halt70/UNKNOWN/锁释放及旧句柄拒绝已到达，失败是误把同JVM新对象当独立进程；修正同JVM期待，独立子进程仍严格OUTSTANDING |
| run1657 / 19833 / `.local/s23i` | 编译exit0；IT1/0/0/0、3.386秒；runner PASS，独立原件消费者最终exit0 |
| consumer03 | 首次离线消费者exit1：把binlog二进制字段当UTF8读取，未输出通过结论；原脚本ZIP、失败stdout/stderr及输入SHA保全 |
| consumer04 | 仅将结构token改为ASCII逐字节匹配，同一binlog原字节不变，exit0；没有重跑IT或启库 |

两次IT失败不同根因各1次，离线消费者失败另列，不计通过，不按会话清零。`failure-run1655.md`、`failure-run1656.md`、`consumer03-failure.json`及三份改前ZIP保存依据；三个隔离实例均认证停库exit0。旧封存用例及旧run03未重跑。

执行入口：`docs/parallel/R09/tests/run_p5_lost_commit.py --run 1657 --port 19833 --output D:/acrylic-pda-outbound/.local/goal-20261023-resume/d13-p5-lost-commit01/run1657-p5-lost-commit --build D:/acrylic-pda-outbound/.local/s23i`。编译与专项的完整命令及退出码见各attempt的 `compile-command.json`、`integration-command.json`；离线命令见 `consumer03/04-command.json`及结果。运行期间2833源码输入及109文件依赖逐SHA保持；离线消费者的读字节修复发生在IT结束后，单独记录执行脚本SHA，不冒充同次产品变更。

## 输入、原件与收口

`baseline.json`、`inherited-originals.zip`保存40条继承路径；`resume03-input.json`及ZIP保存续接48路径原字节/长度/SHA。正式报告及两索引修改前字节保存于 `closeout/document-originals.zip/json`；本报告原先不存在也有记录。原P5树在 `run1657-p5-lost-commit/fence-*/originals-before-observation/`，SQL/dump/binlog、原XML、进程回执和全部失败原件保留。

| run1657原件 | SHA-256 |
| --- | --- |
| 专项XML | c4454c7fec9e925e145b07e167f3e41e6b29c7466c399e09c4c1eea2758e2ce1 |
| binlog原解码输出 | 439d39a53ed1eb12e609fed1d47ed61fec56f0e9f1806a51139068d50eb1dc1a |
| 观察汇总TSV | 0510ff522f957441f1677d667068181c295719951ff3fa47a5922f5b13afe5c6 |
| 独立消费者输出JSON | c27fcb195778d9728b489348b7f115866376c5d8e2a86dd26fcf87f9664aab60 |

`closeout/p5-tree-conservation.json`保存原树逐路径差异，`source-review04.json`保存作者自审与7代码输入SHA。敏感扫描、精确暂存、Git提交/推送回执、最终全部dirty路径/SHA/ZIP和证据manifest也保存于closeout；忽略的私有原件仍在本机，不虚称已异地备份。history索引只暂存本片新增入口，继承内容留在工作树；看板不混入开发备份。

fresh环境快照记录实际进程、监听、ADB、Debug Keystore及C/D/E余量，不据旧PID推定。系统数据库只观察、内部身份未认证；初次PATH中adb不可用的快照保留，随后显式既有SDK路径只读枚举成功，未安装或操作设备。本聊天唯一源码/索引/看板写者；另一个清理聊天显示active，但其授权仅磁盘、避开活动证据及共享看板。

删除曾被执行策略拦截，本片未换通道重试。仅对本片已结束s23g/h/i构建及已认证停机19831/32/33的data执行无损NTFS压缩：**11171文件长度/SHA全部相同，减少分配570440566字节，删除0**。D卷观测33401720832→33977450496字节；卷净变化与文件分配减少分列，不累计此前释放量。`compact-script-provenance/plan/result`保存绝对路径、活动消费者检查、重建输入、命令、退出码及逐文件前后SHA/分配；身份/私有凭据/原回执/唯一证据/最终APK/继承源码保持。

## 未完成与接续

本片只证明真实commit成功、确认丢失后P5严格围栏的有界合成路径；不证明全部JDBC/文件入口、OS拒绝kill、物理断电、完整应用恢复或实际错库运行准入。下一有界项先核OS拒绝kill的产品控制边界及可在隔离环境执行的判据，避免把模拟异常当真实OS证据；实际错库仍需运行准入的数据库证据供应及正向验证，不把全拒绝当已实现。

D13–D15整体、全部入口/其余故障、D16有效guard与复杂引用、D18实际最终候选及总目标第8节仍未满足。生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release继续关闭，`localDeletionAllowed=false`；取消链和旧run03不恢复。提交及压缩不计业务验收，总目标保持active、未完成。
