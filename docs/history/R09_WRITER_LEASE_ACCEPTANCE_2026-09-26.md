# R09 本机跨进程写者租约验收

2026-09-26。沿既有两树HEAD及未提交差异，未提交/推送/main/部署。完整共享门禁与R01–R09目标继续，不能将本层通过视为业务恢复权限。

## 标准、实现和自审

多个Java写者可以共享租约，Python控制推进须获独占；任一写者活跃时原历史/指针不变，最后写者释放或死亡后可重试原命令。嵌套引用、跨线程关闭、控制读取及目录改名不得通过关闭第二个POSIX描述符提前放锁。缺失、坏长度、硬链接、同名替换均拒绝，原件不自动修复。

- `control_writers.py/control_writer_file.py`与`RecoveryWriterProtocol/RecoveryWriterLease`：永久writers.lock字节0/长度1，文件与coordinator.lock分开；Java必须先持协调锁才可接纳写者，按原文件系统身份复用唯一描述符并引用计数。Python推进/原命令修复先协调锁再独占写者锁。
- 新authority显式初始化writers.json，绑定原安装/identity摘要、Windows卷序号及文件索引或POSIX dev/inode。控制读取只读锁文件元数据，不打开第二个POSIX锁描述符。缺失两文件的旧authority仍只读，但拒绝推进/修复；不重写旧identity/历史，不自动升级。
- 自审修复两个风险：同名新锁文件可能绕过旧写者，故持久绑定原文件系统身份；目录改名可能让同一inode出现两个路径，故Java按文件身份引用计数。Windows实际拒绝含活跃Java channel的目录改名，测试断言拒绝且写者仍排他；Linux实际改名后取得/关闭嵌套引用，原租约仍有效。
- 关闭失败保留进程内毒化条目，不能再开第二描述符；同一引用重复close无副作用。基础租约不提供ordinary授权、不注册Spring bean或API、不替代DB投影和全部容器排空。

## 当批验证

证据根 `outputs/r06-product-20260925-01a0d8fa/`：

1. `writer-lease-java-focused-03.log`及XML：原消费者35项+租约6项，共41/41，无skip。focused01发现Windows BasicFileAttributes.fileKey为空，已改用原生文件身份；失败XML保留。
2. `writer-lease-python-03.log`：33/33控制专项。`writer-lease-python-full-01.log`：118项=107pass、11专用恢复环境skip，0失败。python02发生复制旧目录时锁身份先于外部锚点报错，已将可信锚点校验放在布局检查前；失败记录保留。
3. `writer-lease-windows-04.log`与Linux02：各25/25真实Python原件→Java、两语言双向锁/正常释放/进程强杀/原命令重试，无skip。windows03的目录改名遭OS拒绝按失败保留，修正测试为该平台的预期拒绝边界，没有跳过。Linux01及Windows01/02是补强前输入，不替代最终结果。
4. `writer-lease-server-full-02.log`及XML：666项=665pass、1既有符号链接条件skip，0失败/错误；bootJar成功。该批没有改变报表/Android，也未运行MySQL或HTTP；attempt74仅是上一容器子项证据。
5. Linux原件`/var/tmp/acrylic-r09-writer-lease-linux-01/02`及`writer-lease-linux-evidence.tar`保留；Windows每例transcript、命令、原件和校验都保留。最终来源见`writer-lease-source-manifest.json`，两树全部dirty路径见`writer-lease-final-main/server-status.txt`。

作者/自审同一助手；两语言进程消费是独立实现，不是独立人员。无生产、现场、真机、断电/长期验证。localDeletionAllowed=false及七类生产决定/关闭禁用保持。

## 下一项

将写者租约绑定真实数据库事务至提交/回滚及连接清理结束，验证旧快照不能开始回调、外层事务不能造成锁序倒置。随后接关闭意图/固定实例/UUID与DB投影、API/Session/Worker/文件入口、NORMAL初始化与证据绑定释放；旧authority停机迁移及跨主机仲裁仍未完成。进程死亡释放锁不能证明DB连接或独立子进程都停止。
