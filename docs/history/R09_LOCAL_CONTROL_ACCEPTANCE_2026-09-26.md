# R09 库外恢复控制本地模块验收

## 范围与完成标准（实现前固定）

输入：主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c` 及已有未提交改动；两树完整路径见 `outputs/r06-product-20260925-01a0d8fa/control-before-main-status.txt` 与 `control-before-server-status.txt`。当前助手单写者，不创建任务/工作树/子代理。

1. 在现有 `docs/parallel/R09/ops/` 增加独立库外控制存储与常规 CLI 子命令。控制目录与显式业务恢复集不相交，拒绝路径别名、符号链接、junction、非普通文件；首次安装须显式初始化且初态 QUIESCING，普通读取/重启绝不初始化 NORMAL。
2. 版本、安装身份、UUID epoch/session、十进制 revision、原 commandId/规范正文摘要、前记录摘要、时间、恢复范围/备份清单及证据摘要严格校验。历史追加且不覆写；完整链验证，无按 mtime 猜当前记录。
3. 所有读写持进程排他锁。命令同 ID/同正文返回原结果；异正文冲突；过期 revision 冲突。并发进程不能双写；进程死亡释放 OS 锁，锁文件不删。
4. 历史先 fsync，再原子替换当前指针；历史已落盘/指针未提交、指针暂存未提交、响应丢失均保持可检测，并仅由原命令恢复同一原记录，不换 UUID/时间/请求。损坏/部分文件保留并拒绝继续。
5. 外部调用者传入已信任 checkpoint（revision+hash）；指针回退、链缺失、同目录旧副本与新 checkpoint 不符均关闭。整目录与 checkpoint 同时回滚无法由本地摘要自证，须外部不可覆盖证据，不冒称解决。
6. 本模块只记录关闭阶段 QUIESCING→RECOVERING→VERIFYING→RELEASE_READY，不实现 NORMAL/生产释放；始终返回 ordinaryWritesAllowed=false、localDeletionAllowed=false。声明证据摘要不是自动核实写者排空、业务条件或审批；共享门禁/DB PREPARE-COMMIT/UUID桥梁后续接入。
7. 本批以真实文件、独立 Python 子进程并发/崩溃及完整 R09 Python 回归验证；对条件跳过、OS断电、跨主机/网络存储、真实远端不可覆盖、独立人员分别记未验。没有修改业务Java/Android则不将历史MySQL/HTTP/APK/lint算本批通过。

## 设计取舍

沿已有恢复控制契约采用追加历史文件及原子当前指针；与只存当前 JSON 相比可核对原命令及回退，与新增服务数据库相比不依赖被恢复的业务库。OS锁处理当前本机单写者；受保护目录和外部checkpoint构成信任前提，哈希不替代认证。Windows落盘使用文件flush/fsync和MoveFileExW WRITE_THROUGH，POSIX使用fsync目录；仅将实际进程崩溃测试计为已验，不声称真实断电已验。

## 当批结果

本模块已完成实现、自审修复与复验，完整R09及总目标仍未完成。

- 实现：`control_format/model/paths/disk/history/store/cli.py`，常规CLI接入；严格原请求与记录链、固定安装/恢复集、外部检查点、追加历史、OS排他锁、关闭阶段CAS。没有新增Java/Android/数据库迁移或生产能力。
- 自审修复：首次CLI重放字段顺序不稳定，改为固定规范对象后保证响应字节一致；补身份format布尔值拒绝、Windows路径别名/固定盘/junction约束、检查点不可置于控制/恢复集、首次初始化崩溃恢复及子进程就绪超时。
- 最终新增控制测试29/29通过、0跳过；完整R09测试114项=103通过/11专用恢复环境跳过，0失败/错误。11项不是本批通过；其归档恢复只保留原历史证据。
- 真实本地文件/独立Python子进程：同键原响应恢复、同ID异正文、CAS竞争单赢家、持锁进程被终止后OS解锁；bootstrap、history、pointer staged、pointer committed死亡点；损坏、截断、旧指针/旧目录与新锚点冲突、硬链接/junction及秘密不泄露均有反例。
- 失败证据：`control-focused-01.log`保留24项中1个响应顺序失败；`control-r09-full-01.log`保留缺PyMySQL的两项导入错误。通过`.local/r09-control-venv`安装既有固定依赖后复跑；没有修改全局Python、共享3306或删除失败目录。
- 最终输入96文件manifest：`outputs/r06-product-20260925-01a0d8fa/control-final-source-manifest.json`，SHA256 `e3ebe8ff8f902e6cc808047b099c49abcfe4d30a446b2cffc2ec264cb47b7805`。本文件/管理索引随后追加，未更改已测代码。
- 最终日志：`control-focused-03.log`、`control-r09-final.log`、`control-final-summary.json`；两树完整未提交/未跟踪路径：`control-final-main-status.txt` / `control-final-server-status.txt`。依赖安装日志`control-dependencies.log`；原case资料在`.local/r09/control-tests/`保留，不上传。
- 本阶段无业务Java/Android变更，未跑本批MySQL/HTTP/APK/lint；不以attempt67或原APK代本批。3306 PID5448未动，无19330/19434/19435/19436监听、无在途重型任务。两树HEAD不变，无提交/推送/main/发布/部署。

## 限制与下一项

作者/自审为同一助手，独立子进程不是独立人员。合成操作人/证据摘要只证明存储协议，不核准实际排空/业务复核/主管运维审批。全目录与可信锚点同时被回滚、恶意本机管理员替换、外部不可覆盖存储、真实断电、POSIX及跨主机仲裁未验。初始化在bootstrap完成前死亡保留残余目录，不自动覆盖修复。

当前只允许QUIESCING→RECOVERING→VERIFYING→RELEASE_READY；NORMAL初始化/放行必须等待共享门禁、DB投影和两阶段证据。下一项接服务端共享事务/Worker fence及旧数字epoch桥梁，保持生产决定/释放/关闭与localDeletionAllowed=false边界。
