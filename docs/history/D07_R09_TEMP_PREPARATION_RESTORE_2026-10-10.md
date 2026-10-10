# D07/R09 原 TEMP 准备跨截点恢复

输入主树 D:/acrylic-pda-outbound、codex/development、2886dec774080b8d8e483b6baf2a4640b00bd493。原受约束权限批次12条路径已正常提交推送，回执在 controlled-permission-precommit-06；接续核2531个测试输入hash不变、69次继承快照比较一致、其他项目聊天notLoaded。版本事实仅见看板生成块。总目标active、未完成。

## 范围与实现

`RecoveryTemporaryPreparationRestore` 是显式离线消费者，通过既有 RecoveryClosedAuthority/RecoveryClosedTransaction 补原 temporary_preparation_part 和 temporary_preparation_ack。截点原authorization已在，五原compressed parts和ACK尚缺；新目标实测空schema后导入原dump，不能迁移、重建原authorization或续期。原source pin、独立原准备观察pin、原public key、五原件、manifest/token与ACK native纳秒command摘要全部重验。

目标authorization FOR UPDATE，原ID/warehouse/device/operator/epoch/window/版本/key/token/manifest/签档完全一致才可写。库存快照版本/完整原条码内容/bale ID及仓库、原政策ID/版本/执行参数、原映射ID/产品/版本/内容一并锁定核对；不使用恢复库“当前权限”推断历史。分片/ACK只INSERT缺件，已在完全一致可幂等，冲突拒绝，绝不UPDATE原件。提交前重读原source/独立观察、目标授权/基线和全部写入原件；原件变化、末SQL失败回滚所有本事务新行。未知commit仍沿既有耐久毒化协议，此批未重新注入未知commit。

没有HTTP/Spring自动接线、迁移、客户端或新签发能力。恢复库保持RECOVERY_PENDING，普通写权和localDeletionAllowed均false。本步仅恢复准备/ACK；不代正文、E2A持久化、占用/终态重建或主管故障围栏/接收机最终链。

## 实际验证与失败

- compile-01 exit0/sourceUnchanged，编译不是业务验证。定向02原准备/ACK/闭事务36全过、3XML、exit0/sourceUnchanged。
- 1175全新源真实MySQL1全过：原authorization持久化、五parts/ACK为0时保存完整dump及全表/文件快照，再在源持久化完整原parts/ACK并只读采集独立观察。1176新空目标实际导入并完成多项冲突反例，后因DriverManager反复全表快照耗尽Windows临时套接字失败；原XML/log/datadir/dump/binlog保留且认证停机。改验收夹具复用单连接，没有改共享产品连接配置。
- 1177跨33树目录子串守卫拒绝、未建库，拒绝JSON保留。1178另一个全新目标1全过、exit0/sourceUnchanged，实际导入、闭事务五原part/ACK逐字节补齐、幂等、缺pin/授权/库存/政策/part/ACK冲突、末ACK INSERT触发器失败及beforeCommit原件变化回滚均验；独立Node10输入hash不变/三RSA/gzip/纳秒ACK/全表守恒exit0。
- 作者自审补bale ID/仓库和历史映射关系校验；1178只计补校验前输入。最终另分配1179源、1180目标各真实MySQL1/0/0/0、1XML、exit0/sourceUnchanged，重做实际截点dump→新空目标导入→恢复并补mapping冲突反例；其他表/私有文件/DDL/Flyway历史保持。两实例身份各自认证，未复用失败库、原库或现网。
- 最终Node1179/1180独立工具消费者exit0、10输入hash不变，核原canonical/pin、真实dump导入/原授权、三RSA、五原gzip、纳秒原command和毫秒回执、逐原数据库列及全表/文件守恒。合成开发源/身份、内存开发RSA与工具消费不能证明原人、原设备、独立人员或真实异地介质。
- 1180已确认成功目标认证停机后，显式重启同一datadir；新JVM普通main消费原控制/authority/pin/公钥，再执行相同恢复，0parts/false ACK、所有表/文件未变、RECOVERY_PENDING。compile/run exit0、5输入hash不变，认证shutdown0/重启PID退出/19330及19434空。这是成功目标的重开验证，不是失败库重用。
- 完整普通server回归03为4130/0/0/1、240XML、exit0/sourceUnchanged；新增入口5项执行，普通test不含IT。Windows符号链接skip未执行，不计通过。没有新HTTP/Android/APK/模拟器/真机/正式签名或部署证据。

证据：设计在 `.local/d01-d18-20261010/temporary-preparation-restore-design.md`；当次命令/hash/log/XML/result在 `.local/d01-d18-20261008/r09-temp-restore-*` 和 `r09-temporary-preparation-restore-mysql-<run>`；跨树分配/runner/provision/dump/认证停机在 `.local/d01-d18-20261010/temporary-preparation-restore-mysql-<run>/`；原datadir/binlog/dump/截点/逐表文件快照在 `.local/e2a-cross-20260925-<run>/`。Node原脚本/命令日志/输入hash/result在 `temp-restore-consumer-<source>-<target>-01`；重启main/source/class/compile、命令classpath/运行回执在 `temp-restore-reopen-source` 和 `temp-restore-reopen-1180-01`。35份必要产物hash及五dump独立重算见 temp-restore-original-artifact-hashes.json、temp-restore-dump-recheck.json，全部一致，失败证据未删。

## Git、环境与接续

接续实际核HEAD/远端一致，temp-restore-resume-01保存45条dirty完整副本/hash/diff和环境。FORMAL final、磁盘final及controlled-permission-postcommit-07合计69次hash比较，除本批三个文档索引/看板外继承项无漂移；旧HEAD均为祖先，完整普通回归的2545个源码/构建输入当次重算0漂移，沿同输入有效结果不重跑一次性库。作者自审和git diff --check通过；接续在server目录调用不存在的wrapper未执行测试，随后核对根目录原runner与回执，此失败未算验证通过。

明确范围源码/测试/本页/索引/看板提交推送，最终精确SHA/实际远端及全部dirty副本/hash/diff以temp-restore-precommit-02、temp-restore-postcommit-03为准。继承25条dirty中本批仅更新看板/进度索引/历史索引，其余逐hash保持，不混入范围；数据库、私钥/本机配置、原资料和产物没有上传或异地备份。默认单助手、唯一活动写者。

1175/1176/1178/1179/1180均dump保全/认证停机/PID退出/隔离端口空，五dump当次独立重算一致。1180重启后再次认证停机。既有3306/13316/ADB不操作；C/D实测余量及所有进程/监听见提交断点disks/processes/listeners，产物/TEMP/Java/cache均D盘，本批没有清理。

下一步同源原登记根/独立pin、受约束权限连续窗口、原E2A完整字节及原SOURCE_RESERVED/无hash ABORTED终态绑定后，接原正文和原ID/序号/回执的closed恢复；逐保存时间只能来自原连续窗口，不能造时间或把两端观察当历史。实际截点恢复后接D07主管故障围栏→接收激活→最终提交，保留每阶段尾SQL/幂等/冲突/重启反例。不得无限新增只读观察代重建。

D06/D07整体、FORMAL各阶段产品入口、D08–D18及第8节未完成；原FORMAL准备缺失/高轮/续期撤销/跨来源、D04/D05政策、原资料/旧BCD/526、失败/恢复限制和C01–C08继续单列。1150/1151/UI-06不重复，1100–1102不重跑、不复用失败库。生产默认/签名/七类生产决定保持关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名，不绕设备/审批。
