# D盘空间恢复记录

用户要求先处理D盘磁盘空间不足；D01–D18开发目标保持暂停，不执行新业务验证或数据库恢复。本次输入为D:/acrylic-pda-outbound、codex/development、HEAD/实际远端62001703172d57999d6cc764256c4ace3e0258a1。版本事实只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。

## 定位与保全

证据目录为`.local/disk-recovery-20261009/`。control保存输入SHA、全部22条dirty清单/hash/diff、替换前看板、全部进程/监听、C/D/E/F余量及依赖锁/构建输入hash；原FORMAL final快照保留同字节dirty副本。当前聊天唯一写者，旧接续聊天idle、其他项目聊天notLoaded；无Java/Gradle/emulator/qemu消费者，5560/19330/19434/5173无监听。既有MySQL/ADB环境不操作。

`scan-space.py`使用文件系统遍历，不跟随reparse point；大文件用Win32 GetCompressedFileSizeW读取实际占用，小文件按簇取整仅供排序。全项目space-inventory.json保留无读取错误的清单。主要占用为D01–D18页面证据的22份停止运行AVD RAM快照，每份逻辑大小4295032832字节；R09 control-java-cases含大量保留的可信控制夹具，数据库、原日志/回执、共享依赖和其他项目没有列为垃圾。

## 实际处理与验证

按已授权可逆NTFS压缩处理22份`avd/snapshots/default_boot/ram.img`，包含D02/D03/D06确认、D06接收激活与STOP、新开轮、FORMAL页面成功/失败证据。只压缩RAM文件，路径、逻辑长度、原内容、其余AVD及页面数据库/原件均保持。未执行先前被自动审批拒绝的AVD删除，也没有换包装重试删除。

`compress-ram.py`执行前逐项确认绝对目标在主项目指定D盘证据根内、无运行消费者；每份先保存SHA256/大小/占用/完整compact命令，再执行`compact.exe /c /i /q <明确文件>`，复算SHA256及实际占用。22/22命令exit0，22/22前后hash一致；运行230.3秒。control/ram-plan.json、ram-01至ram-22.json及compact原日志、ram-result.json保存逐项回执。ram.img的大量空白预分配页转为压缩表示，仍能以原路径读取同字节。

按文件API读取的占用减少94490722304字节；磁盘余量实际从5878714368字节（5.475 GiB）增至98128801792字节（91.390 GiB），净增加92250087424字节（85.915 GiB）。分别记录文件占用与实际可用空间，不把两者差异当成未核释放量；最终写入文档/快照后的余量以final/disks.json为准。

未删除文件，没有移动资料或新增项目副本；失败库/datadir/dump/binlog、签名/HTTP原件、原日志/XML、Room、截图/tar、最终APK、原资料及共享工具/缓存继续保留。UI-06原tar哈希与原登记一致；最终APK、1150/1151 dump及结果hash在control/retained-evidence-hashes.json，压缩后再次核验一致。22条继承dirty逐hash不变，无源代码、依赖、业务数据库、签名/生产开关或localDeletionAllowed变更，不重跑1100–1102或其他已验业务。

## 收口与接续

本次仅提交磁盘记录、看板、暂停页和历史索引四条明确文档路径；暂存检查/commit/push及实际远端回执在control，最终Git/全量dirty副本/hash/进程/监听/磁盘在`.local/disk-recovery-20261009/final/`。本机证据没有上传Git或异地备份，开发总目标仍paused、未完成。

业务接续指令仍见[FORMAL暂停记录](D01_D18_PAUSE_2026-10-09_FORMAL.md)；接续时先核本次final的实际HEAD/远端、22条继承保护、全部dirty/进程/监听和磁盘，不用压缩后逻辑目录大小判断余量不足。后续每次页面AVD认证停止后，可沿本次做法先登记输入/原件hash、核无消费者，再对新增空白RAM预分配文件做可逆压缩；不得为节省空间删除失败/唯一证据。若再需归档，先遵守主规则核保管与可取回条件，不直接启动历史失败库。
