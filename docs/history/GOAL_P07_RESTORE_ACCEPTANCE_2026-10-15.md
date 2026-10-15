# P07 显式原签还原与规范字节修复 L1 验收

本批提供受限、显式的原签字节还原和独立Python回读，并修复诊断分类的布尔/整数混淆。仅收口工具L1开发切片；未知/pending恢复、外部历史钥保管/引用、E07 L2、D13–D15全部、D18 L3与总目标仍未完成。版本事实见看板生成块。

## 输入、设计及影响映射

固定树`D:/acrylic-pda-outbound`、分支`codex/development`，输入HEAD/实际远端`57ba12634428dcd0ed2962b24fd8990c69cfb467`。唯一源码/库/看板/索引写者为本聊天，无子代理；78项继承改动原字节/hash不变。接续79项原字节/hash、旧看板及自主决定见`.local/goal-20261015/p07-restore01/`；最终2238项源码输入、复用关系和核验在`p07-restore03/`。

自主选择独立离线还原入口，而非手工覆盖或自动继续轮换：必须有精确原请求、相邻原签备份、独立保管的签名清单SHA和外部两代钥。先持原P3协调锁、核验原身份和原生锁身份、完整签名集合、空租约及逐条当前原/新规范字节，再持原fence独占锁并逐个确认process生命周期锁无人持有。无后继记录/未知事实时才允许还原；任何pending均拒绝。不能为使用工具移走/清除unknown或为旧无清单备份补造证明。

改动路径→影响层→必跑与复用：

- `RecoveryRekeyRestorePlan.java`、`RecoveryRekeyRestore.java`、`RecoveryRekeyRestoreCli.java`、`RecoveryRekeyRestoreFailure.java`：新离线入口、验证、故障现场快照和原子替换；实际Python/JVM正向、幂等、并发、持锁、缺件/改锁/后继事实拒绝、末替换失败及强制中止必跑。
- `RecoveryRekeyInventory.java`：新增只读签名全集验证；原封存/供应/集合拒绝链实际复验。`RecoveryLeaseRoots.identity()`仅包内可见，复用原身份规则，运行验证行为不变。
- `tools/recovery_rekey_restore.py`：实际秘密管道供应、产品还原和独立Python回读必跑；不把编译或静态检查当消费者执行。
- `tools/recovery_rekey_inspect_state.py`及`RecoveryRekeyInspectIT.java`：自审扩展的规范字节比较修复；实际当前/pending布尔替换反例、全部分类、原供应及还原回读/中止链复验。
- `RecoveryRekeyRestoreFixture/RestoreIT/RestoreCrashIT`：只操作新合成夹具、真实产品子JVM及其精确PID，不操作旧失败原件/现场设备。
- `RECOVERY_REKEY_RUNBOOK.md`：新增操作入口、拒绝边界和不确定结果保全流程。

33项普通定向回归在restore01运行，后续Java产品输入不变，按`restore03/reuse.json`及两批source-input核对复用。旧2005项的未受影响运行期覆盖见`p07-inspect03/source-input.json`；本批不是再跑2005，不能据此计候选全量回归。没有HTTP/schema/Android/Web/MySQL变化，不机械重跑无关入口，后续L2/L3仍必做。

## 实现行为

原件可信、输入可解释且当前存在新签时，先完整保存当前P5到相邻`.rekey-restore-before-*`，再逐个原子替换process、index，marker最后。保留原签备份、签名清单、故障现场快照；全旧且无后继事实时幂等返回，不重复替换。替换后任何失败退出3、明确记录保留快照路径，不自动回滚/重试，原pending保留且启动拒绝。前置拒绝退出2；没有启动、运行秘密供应、围栏清除、业务放行或数据库恢复。

成功后独立Python核对签名全集、原marker和全部原规范字节，写verified.json；始终`runtimeUpdated=false`、`keepWritersStopped=true`、`recoveryAllowed=false`、`productionApproved=false`。当前写者已退出只在持锁窗口内检查，输出后的观察不当长期停机证明。外部保管、业务截点/数据库事实与生产授权另验。

## 实际运行、失败及复验

| 批次 | 结果 | 证据与说明 |
| --- | --- | --- |
| p07-restore01 | 普通test33/33、IT24/24；0失败/错误/跳过 | CLI17、轮换4、锁8、default-off配置4；Restore10、Inventory7、Python5、Startup2 |
| p07-restore02 | IT14/14；0失败/错误/跳过 | Restore13及真实RestoreCrash1；实际原fence持锁、双侧缺件、活跃索引反例和产品进程终止 |
| p07-bool01 | IT2项均失败，退出1 | 自审复现产品Python分类缺陷：`true == 1`导致当前及pending的布尔替换仍退出0；原测试已实际轮换，51文件/原XML/日志完整保全，不走无写入快速通道 |
| p07-restore03 | 最终IT38/38；0失败/错误/跳过，退出0 | Restore13、RestoreCrash1、Inspect9、Inventory7、Python5、Startup2、RekeyCrash1，受影响实际消费者全部通过 |
| bootJar | 成功 | 最终产品构建，未部署/发布 |

布尔/整数缺陷改为比较规范JSON字节，而非Python对象相等；同样覆盖pending。该根因失败运行计数1，修复后有新源码依据复验；历史失败和暂缓不清零。产品Java自restore01未再修改，33项按源码hash复用；原XML均在覆盖前保全，没有用旧XML冒充当次执行。实际命令/参数、子进程环境、退出码和输入在每批command.json/gradle.log/exit.json/source-input.json中，TEMP/TMP/Java临时目录只为本任务子进程指D盘。

最终实际还原中止PID8628已退出，留下UNKNOWN/停写，故障现场快照和pending不删除，两代运行绑定均实际拒绝启动。另一次新轮换实际中止留下11新签/504旧签，诊断MIXED且全集认证true，仍不得恢复。已知混合和全新无后继事实的还原正向由实际Python供应器→产品JVM→独立Python读回通过；同键并发/重放仅产生一次必要替换。Windows原生禁止删除共享句柄令marker最后一次rename真实失败，退出3、快照与pending保留并启动拒绝。合成515条不是现场历史规模验收。

## 自审、原件、环境及提交

作者/自审为当前助手；Python是独立实现消费者，不等于独立人员审查。检查固定身份/路径、链接/硬链接拒绝、清单HMAC/摘要/全集、原锁身份、实际活跃锁、空索引与后继记录、故障前快照、末替换不确定分类、原件保留、秘密不进参数/env/输出，以及default-off。未接原库/现网、设备或共享MySQL。

四批runtime`.local/rtmp-p07restore01/02/03/`及`rtmp-p07bool01/`，分别763/1910/3447/51文件长度/hash清单；收尾再次逐文件核验一致。所有原签、故障快照、pending、请求/测试合成钥、原XML/日志留本机私有区，不上传Git。最终JAR为`.local/goal-20261015/p07-restore03/server.jar`，280772140 bytes，SHA-256 `07c598cb894b5d361f6f9475d38ce9f6298903fb7915f056485565be6e1949aa`。

结束无项目Java/Python/API/Vite/隔离库/设备重型任务；共享3306/33060 PID5412父4372未操作。C/D可用78,866,391,040 /19,966,418,944 bytes，见restore03的processes-after/listeners-after/disks-after.json。生产七类决定、TEMP归档边界、`localDeletionAllowed=false`、main/部署/Release/正式签名边界保持。

按明确路径开发备份并推送codex/development，实际SHA/远端/tree、index、全部改动/hash和写权见`p07-restore03/post-commit.json`等收尾回执。不以本批计D收口或总目标完成。

下一独立范围：E07跨截点事实重建/历史钥引用与升级回退的实际隔离组合；本工具只完成已知签名集合原字节还原，UNKNOWN/pending不因此通过。原设备/Chrome审批阻断不绕过，旧取消范围不恢复；D/O/U、第8节、E06 L2和D18 L3继续未完成，总目标active。
