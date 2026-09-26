# R09 Java库外控制消费者与跨语言锁验收

2026-09-26。本子项完成；整体R09共享门禁、恢复及R01–R09目标仍未完成。作者与自审均为当前单助手，独立消费者指Python生产原件→Java读取，不是独立人员验收。

## 输入与实现

- 主树 `D:/acrylic-pda-outbound`，`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，均含既有未提交改动。
- Java `recovery/RecoveryControl*` 独立验证原规范UTF8、严格字段/时间/仓库/UUID、原命令/记录摘要、bootstrap、完整连续历史、阶段/CAS、不可变上下文和外部可信checkpoint。整体authority回退、缺失/损坏、半提交和未知资料拒绝，不重写原件。
- authority、恢复集和可信checkpoint分开配置；拒绝路径重定向、硬链接、Windows别名/ADS/网络映射盘；Java Linux限制已确认本机文件系统。父目录/ACL和独立checkpoint属于信任前提，不能抵抗管理员同时伪造全部资料。
- Python与Java均使用 `coordinator.lock` 偏移0长度1排他记录锁，先取得进程内门禁再打开描述符；Python POSIX从flock改lockf。空锁文件拒绝。升级前旧flock写者须退出，不能混跑两个锁协议。
- 输出保留范围/证据摘要，关闭阶段固定 `ordinaryWritesAllowed=false`、`localDeletionAllowed=false`；没有生产Spring调用点、放行入口或DB投影接入。

## 当批验证

证据根 `outputs/r06-product-20260925-01a0d8fa/`。

| 验证 | 结果/证据 |
| --- | --- |
| Java定向 | 35/35，无失败或跳过；`control-java-focused-02.log`及独立XML副本 |
| 服务端相关全套 | 654项=653通过、1符号链接条件跳过、0失败/错误；`control-java-server-full-01.log`和654项XML。后续仅测试进程harness增加线程/配置输出，编译成功并在下列真实进程测试覆盖；生产Java没有后续漂移 |
| Python完整R09 | 114项=103通过、11专用恢复环境跳过、0失败；最终Python空锁拒绝输入的`control-java-python-full-02.log`。跳过不算当批恢复通过 |
| Windows原件/进程 | `control-java-windows-03.log`，16/16，无skip；Oracle Java21.0.11/Python3.14，逐案例原件和子进程transcript保留 |
| Linux原件/进程 | `control-java-linux-03.log`，16/16，无skip；WSL Ubuntu本机ext4 `/var/tmp/acrylic-r09-control-java-linux-03`，Python3.12.3/私有OpenJDK21.0.12.1；同一已编译Java类及依赖，原件在Linux本机文件系统生成 |
| 实际竞争 | Python持锁→Java拒绝、Java持锁→Python读取/写入拒绝；正常释放、双方进程被kill后恢复；同进程嵌套和另一线程不得关闭其他描述符释放原锁；以LOCKED管道握手，不依赖sleep猜锁状态 |
| 原件完整性 | 四关闭阶段、旧/新外部锚点、Unicode码点排序路径、回退目录、坏原命令、半指针、缺authority、硬链接和坏锁；Java读取前后原件摘要相同 |

运行器 `docs/parallel/R09/tests/run_control_java.py` 要求显式Java、Gradle生成classpath和全新证据目录；环境缺失直接失败，不能条件跳过。Linux原始失败和最终资料已复制到 `control-java-linux-evidence.tar`，原目录保留。

## 自审与失败保留

1. 初稿缺可信checkpoint、bootstrap/命令/阶段验证、路径/硬链接限制与规范Unicode处理，已补齐；此前无测试匹配失败保留，不能计通过。
2. Windows首轮13项中Unicode参数失败：Java启动器按本机编码把非BMP字符替换为`?`。harness改为独立UTF8配置原件，读取器未绕过路径验证；第二轮13/13，最终补强16/16。`control-java-windows-01`完整保留。
3. Linux首轮13项中9失败：提取的私有JRE安全配置绝对链接仍指系统 `/etc`。只在私有目录将链接迁至包内原配置，原包链接和relocation清单保留；未改系统包/配置。第二轮13/13，最终16/16；`control-java-linux-01`保留。
4. 复审补Java另一线程争锁、历史硬链接和坏锁测试；发现Python接受空锁，统一严格拒绝后Windows/Linux及Python全套复验。

## 输入锁定与限制

`control-java-source-manifest.json`包含957个源码/测试/构建/R09文档文件，SHA256 `574491a195a92461e8d4067d17d49721b1c02ea1ca097a0e2157a17b60326d27`。各文件哈希固定实际测试输入；后续验收说明与管理文档另行追加，不倒填为历史输入。两树全部差异/路径见 `control-java-final-main/server-diff/status.txt`。

本子项无DB/schema/HTTP/Android调用点；没有声称本批MySQL、HTTP、APK/lint或部署通过。Linux仅本机WSL/ext4，非跨主机/网络盘仲裁；进程kill不是OS崩溃/断电；真实PDA、现场、长期耐久、独立人员、不可覆盖异地保管继续未验。没有main/提交/推送/发布动作。

下一子项必须将控制租约接入API/Worker/事务及文件子进程排空，建立独立安装/UUID与DB投影桥梁，并以隔离非空MySQL/真实HTTP验证。一次读取或永久关闭stub不能算共享恢复完成；释放/七类生产决定与删除边界保持。
