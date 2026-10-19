# D 盘开发产物清理（2026-10-19）

执行树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `622d38bdb018af8f4fdb3540a2e718476a08217d`。本清理聊天为 `01a151d2-6cf2-79a0-be47-d132f7ca4cc6`；其他项目聊天未加载，未启动新聊天或代理。版本事实只见看板生成事实块。用户本次再次明确要求按持续清理授权，以文字摘要替代不必要的大文件。

开始采样 D 盘可用 **3,376,967,680 bytes**，收口采样 **61,290,782,720 bytes（57.08 GiB）**，净增加 **57,913,815,040 bytes（53.94 GiB）**。这是卷可用空间净变化，不把文件逻辑量冒充实际释放量。C 盘收口可用 `73,283,104,768` bytes；E 盘未操作。

## 实际完成

- 删除首批 5 个项目产物目录，共 18,580 文件、2,362,210,115 逻辑 bytes：`.local/acrylic-pda-outbound-isolated`（其中 4 个旧 retention 测试目录）、`server/build`、`android/app/build`、`report-rendering/build`、根 `build`。精确路径与大小见 `candidates-before.json`，删除结果见 `project-delete-results.json`。
- 8 个停用 AVD 的 `ram.img` 通过独占句柄作 NTFS 无损压缩，实际分配减少 **34,360,262,656 bytes**。8/8 前后长度和 SHA-256 相同，未删除 AVD 身份或 userdata；近期 U04 run02 整个输入排除。`ram-plan.json`、逐项 `ram-01` 至 `ram-08` 与 `ram-summary.json` 保存证明。重用旧压缩实现，其原文件与 SHA 已记录。
- 84 个旧隔离测试库保留运行目录，只删除清单里的数据/redo/undo/临时表空间/binlog/表定义二进制文件。生成器使用新建 `--initialize-insecure` datadir，schema 名称检查均属于测试前缀或系统 schema；每个入选目录有最后一次正常关闭日志和未监听端口证据，删除时重新独占打开并核对每份文件 SHA。配置、PEM/DER、SQL、日志、storage、签名/原回执均不在删除清单。63 个不足以确认、被源码引用或近期未决输入的数据库目录保留；保护集合及逐项理由见 `test-database-facts.json`。
- 重复 JAR 与 Robolectric 原生运行库/字体/ICU 仅在存在相同字节 donor 时删除，目标及 donor 均核对 SHA，删除期间固定 donor 句柄。保留副本映射见各 manifest，恢复时复制 donor 回原路径；所有 donor 在收口时再次核验。
- `pnpm store prune --store-dir D:\.pnpm-store` 退出 0，只清元数据，删除 0 个包/文件；缓存包仍保留。项目外目录只盘点，未删除其他项目、共享工具或回收站。

| 逐文件删除类型 | 实际删除文件数 | 逻辑 bytes |
| --- | ---: | ---: |
| test-database | 31602 | 19,538,153,301 |
| duplicate-jar | 7 | 1,621,184,702 |
| duplicate-runtime | 151 | 3,044,338,212 |

## 文字记录与保全核验

本机证据根为 `.local/disk-cleanup-20261019/`。`snapshot.json`、`originals/`、Git status/diff 保存清理前 217 个脏/未跟踪文件、原字节、SHA、准确分支与 HEAD；`rebuild-inputs-complete.json` 补齐实际独立 Gradle 工程、wrapper、配置及依赖锁哈希。命令、结果、路径、逻辑长度、正常关闭日志摘录/哈希、schema 名与排除理由已文本化保留；后续不要求保留已删的测试数据二进制字节。

`verification.json` 通过：原清单文件全部存在，业务源码/继承 WIP 字节未因清理改变；仅本次授权的看板更新允许变化。最终交付 APK 与开始哈希一致；U04 run02 的 5 组 before/after tar 及 instrumentation log 共 15 项与原 result.json 历史哈希相符。它们仍是原来的未决证据，清理不改变 `archiveUnchanged=false` 结论，也不算业务验收。

所有逐文件成功删除项均确认不存在，全部 donor 校验通过，原 MySQL 进程和 3306 服务保留。未启停数据库/设备，未连接原库或现网，未构建或运行产品测试；未提交/推送、合 main、部署或发布。没有修改全局环境或 HOME/CODEX_HOME。

重建构建产物使用保留的源码/配置/锁和已安装依赖：`gradlew.bat -p server bootJar`、`gradlew.bat -p android :app:assembleDebug`、`report-rendering/run.ps1 installChromium installDist`。临时目录/浏览器目录按该脚本和项目规则仅对子进程配置。测试库须用生成器分配新的隔离 run、重放对应 fixture 和原验证命令；不得把重建库冒充相同字节原证据。RAM 无需恢复，NTFS 透明读取原字节，未来写入可能重新占空间。

## 执行记录缺口与保留项

首批按目录删除前只保留目录路径、文件数、逻辑量、源码/WIP 和构建输入快照，未先生成已删历史 retention/build 内容的完整逐文件摘要/哈希。这一缺口不可事后补造，不能声称首批具有完整逐文件证据；后续批次已改为 manifest、独占锁、前后哈希和逐项回执。原始执行失败、JSON 数组读取错误及数据库临时文件白名单拒绝均保留于 `validation-failures.md` 和初次失败回执；相关失败调用未删除文件。

AVD userdata/身份、近期 U04 输入、被脚本依赖的测试库、无法证明正常关闭的数据库、原资料、Keystore、原回执/签名、最终 APK、源码/继承改动、生产边界文件均继续保留。高数量 `control-java-cases` 合成原件及系统不可读目录未纳入完整盘点，不把可见目录统计当全卷实际分配证明。原业务 `localDeletionAllowed=false`、D16、生产七类能力和开发目标完成标准不改变。
