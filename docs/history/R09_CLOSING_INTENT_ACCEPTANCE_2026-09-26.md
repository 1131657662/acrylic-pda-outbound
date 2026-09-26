# R09 耐久关闭意图与接纳排空验收

2026-09-26。两树仍为主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`、服务端 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95` 加未提交差异；未commit/push/main/发布/部署。

## 实现与完成标准

见[关闭意图设计](../parallel/R09/CLOSING_INTENT.md)和[操作说明](../parallel/R09/LOCAL_CONTROL_GUIDE.md)。有效推进命令先在协调锁内验证原正文、CAS和阶段，追加不可变意图并耐久推进摘要头，再争写者独占锁。已有共享租约可以完成；新的Java租约在DB连接前拒绝。原命令重试在最后写者退出后完成原历史/指针；意图永久保留，由原控制历史证明完成。

写者协议version2绑定意图目录与摘要头；version1或更早无协议原件只读，不自动升级，不重写原历史/请求/回执。意图追加、头暂存/提交三个进程死亡点均保持新写者关闭，对应原命令恢复；坏hash、缺文件、错版本、冲突命令不能重造。

## 本批测试及自审修复

证据根 `outputs/r06-product-20260925-01a0d8fa/`。

- Python专项首轮发现历史已追加、current未提交时错误命令被提前报CAS冲突，修复为原COMMAND_INCOMPLETE顺序。第二轮新测试把Path传进canonical JSON失败，修正为显式字符串。首两轮日志保留。
- 自审发现Python字典比较把布尔True等同整数1，已在摘要头调用严格version校验，新增双语言反例。最终Python全套 `closing-intent-python-full-02.log`：125项=114pass、11专用恢复环境skip、0失败；跳过不计通过。
- Java定向51/51；最终 `closing-intent-full-01.log` 及98份XML：676项=675pass、1既有符号链接条件skip、0失败，bootJar成功。
- Windows首次Java路径不存在失败、Linux首次测试过程中共享编译目录被Gradle更新导致1次ClassNotFound失败均保留；后来冻结两份class树并保存runtime manifest。最终Windows03/Linux02各31项真实跨进程全部通过、无skip：双Java共享租约、已持租约结束、新租约被意图拒绝、原键重试、死亡/引用计数/改名/替换/硬链接及三个意图落盘故障。Linux原件tar已保存。
- attempt81实际事务/HTTP及投影大部分通过后，“整套目录回退”旧夹具只回退history未回退新增intents，被CONTROL_INTENT_GAP正确拒绝；补全整套回退资料，失败库/XML保留且认证关闭。
- **attempt82最终输入**：新独立MySQL，非空V34 seed→V38→V39。真实事务提交前、afterCommit可见但清理未结束、回滚前这些窗口，Python原命令均保留停止意图且因旧租约拒绝推进；新Java实例在连接/回调前拒绝，新写不会继续加入。旧事务提交/回滚完成后原键推进。SQL错误/内层事务、旧实例和真实commit后抛08007 UNKNOWN持锁等反例通过。
- 同一82复验V39四阶段、并发登记、完整回退/错库/坏映射/回滚/溢出，投影前后原业务表逐字节与文件保持；真实HTTP/浏览器原TEMP导出兼容各1/1，无skip。测试进程退出后该账户其他连接/INNODB_TRX为0/0，UNKNOWN原命令再推进revision2且ordinaryWritesAllowed=false。
- 81/82认证shutdown；19330/19434/19435/19436空闲，共享3306 PID5448/父4412未动。private配置、签名、数据库、快照均保留服务端`.local/e2a-cross-20260925-81/82`，不上传。
- 1010项 `closing-intent-source-manifest.json` SHA256 `29dc77669fef902753b1d229d7323497e5fed2d1d75851ce2c8b881072d98adb`；JAR SHA256 `47d83a7ab5f2c3de85557fd1387863cb31f3ca48171c5209e7c4f3d520a5d0e2`。最终全套后无Java源码漂移，Python严格version修复已经纳入最终125项及Windows/Linux、82运行输入。

## 边界及接续

作者/自审当前助手，Python→Java为独立实现消费，MySQL/HTTP/浏览器为组合验证，无独立人员。HTTP仍是原接口兼容，未声称全应用门禁完成。没有Android变化，未新跑APK/lint；现场/Keystore/真实断电/长时未验。

下步接API/Session/Worker/Flyway/文件全部写入口、NORMAL首次登记及PREPARE→DB→COMMIT证据协议、完整UUID传输与恢复；当前不能操作生产关闭/放行。原件全部保留，localDeletionAllowed=false；R01–R09总目标仍未完成。
