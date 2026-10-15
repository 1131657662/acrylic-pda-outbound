# D 盘第三轮清理记录（2026-10-15）

用户要求继续清理 D 盘无必要内容、项目测试垃圾和项目外可再生资源，并明确确认《英雄联盟》已不用、可以卸载。本记录只描述本轮实际操作，不重复前两轮释放量。

## 结果和计量

16:21:00 +08:00 采样：D 盘可用从 16,470,511,616 增至 68,164,751,360 字节，即从约 15.34 GiB 增至 63.48 GiB，净增加 **48.14 GiB**。同期开发聊天运行隔离验证，阶段空间变化不能直接相加作为净增量，也不能把逻辑长度或硬链接重复计数当成释放空间。完整结束采样另见清理回执。

| 实际操作 | 删除内容 | 操作前后 D 可用增加 |
| --- | --- | ---: |
| 游戏文件清理 | 《英雄联盟》本体 9,984 文件、对应乱码目录 7 个缓存文件 | 46,446,534,656 字节 |
| 闲置依赖清理 | 13 处 node_modules，545,454 普通文件，另包含指向本目录内部的链接 | 3,489,837,056 字节 |
| pnpm 官方清理 | 88,014 缓存文件、2,250 个无引用包 | 1,280,643,072 字节 |
| 项目临时 DLL | 获开发写者精确放行的 356 个旧 Conscrypt DLL | 674,246,656 字节 |
| Windows 回收站清理 | 官方 Clear-RecycleBin 清理 D 盘回收项目；955 个文件变为 2 个残留文件 | 2,146,304 字节 |

所有路径操作均使用 PowerShell；删除前校验固定绝对目标、祖先重解析点、活动进程和输入。未搬迁或新装工具，未修改全局环境。第三轮脚本、清单、Git 快照、命令、退出码、校验和及失败记录在 `.local/disk-cleanup-20261015-round3/`。输入快照包含 83 项未提交/未跟踪文件哈希与 tracked patch，初始分支 `codex/development`、HEAD `68e9852a8691a63605b2a8709c86d4e12d706963`；结束核对 HEAD `ee98d4cd5aeb34b877aefdfad5c5b0e78e5da2ea` 是开发聊天推进的提交。本聊天没有提交或改动业务源码。

## 依赖输入和恢复

清理的 node_modules 分别属于：

- `D:/warehouse-pc`、`D:/wms-new/wms-frontend-pure`；
- `D:/acrylic-outbound-system/admin`、`mobile`、`server`；
- `D:/paper-mes-unified/frontend`、`D:/roll-paper-mes/mes-frontend`、`D:/paper-processing/frontend`；
- `D:/warehouse-app`、`D:/Pencil/uniapp`、`D:/warehouse-server`；
- `D:/wms-new/wms-backend`、`D:/wms-new/wms-miniapp`。

对应原始 package.json、锁文件副本、Git HEAD/状态/patch、源文件和配置哈希、目录统计及链接目标均保存于 `deps-inputs/`，精确对应关系见 `deps-plan.json`。npm 锁记录使用公共 registry.npmjs.org URL 和 integrity；26 个代表性锁定 tarball HEAD 请求均返回 200，结果见 `deps-registry-check.json`，这不代表逐包重新安装验收。

前两处使用保留的 pnpm 锁执行 `pnpm install --frozen-lockfile` 恢复，其余使用保留的 npm 锁执行 `npm ci`，必要时沿旧安装约束加 `--legacy-peer-deps`。两份原始 pnpm 输入在独立副本中完成 frozen/offline/lockfile-only 校验，退出码均为 0；旧 acrylic server 的锁根条目多列了两个依赖，保留了差异及原始输入，原始输入的 npm ci 离线 dry-run 退出码同为 0。此验证不执行安装脚本，也不代表已重新构建旧项目。

复核 3,973 个源文件及配置的长度和 SHA256，全部与清理前一致；13 个目标均不存在。保留所有旧项目源码、Git、环境文件、原数据及交付 dist/APK。`D:/wms-project/backend/node_modules` 存在 Git 跟踪文件，未清理；`D:/wms-project/wms-frontend-pure/node_modules` 缺少相邻安装输入，未清理；`.mimocode` 及其他运行时/共享依赖继续保留。开发聊天已定向确认本轮在途 Java/Node/Python 消费者未引用上述已清理目录。

## 项目 DLL 窗口与证据保护

项目内唯一删除范围是 `conscrypt-candidates.json` 中早于本轮输入时刻的 356 个 `.local/t/conscrypt_openjdk_jni-windows-x86_64*.dll`。每个文件先核对时间/长度，在拒绝其他读取写入的句柄下重算 SHA256，再逐项删除；无占用保留，删除后全部不存在。保留的已安装 Conscrypt JAR 和其成员哈希重验一致，可由库在下次运行时自动提取相同字节。名单、JAR/成员哈希、脚本参数、结果见 `conscrypt-source.json`、`remove-conscrypt.ps1`、`dll-delete-results.jsonl`、`dll-delete-summary.json`。

该窗口 15:51:57 +08:00 已释放并通知开发聊天。首轮日期字符串精度比较导致全部跳过、未删除任何文件；原失败记录保留于 `dll-delete-*-attempt1.*`，修正为日期值比较后通过。

原始 83 项输入最终 81 项哈希不变、无缺失；另两项为开发聊天在途维护的看板和 E2aReceivedCheckpointIT.java，比较清单见 `project-inputs-comparison.json`。源码、隔离库、共享看板和索引写权始终属于开发聊天；本聊天只新写本报告与自身清理证据。保留 `.local/e2a-cross-*`、`.local/goal-*`、`.local/d01-d18-*`、control-java-cases 原件、所有失败库/datadir/dump/binlog、原日志/XML/HTTP/签名证据、密钥、唯一 APK、AVD 身份及用户盘。未扩大既有业务删除政策、生产授权或验收结论。

## 限制和留存

- 已验证签名的 NSIS 游戏卸载器 `/S` 在 Windows 管理员确认阶段返回取消，未启动；随后按用户授权通过现有文件权限清理精确游戏目录。本体已不存在，未宣称完整系统卸载。HKLM 游戏卸载记录删除被 Windows 权限拒绝，保留了注册项备份和错误；WeGame 本体、common_apps、共享服务/驱动均未操作。
- 三个 C 盘失效游戏快捷方式删除被自动审批拒绝，工具只返回 `blocked by policy`、未提供进一步原因，快捷方式保留，见 `game-shortcuts-policy-block.json`。
- `D:/ProgramData/QuarkCloudDrive/videoCache` 与 `D:/android-offline-pack/__MACOSX` 共约 84 MiB，经只读识别为视频缓存和 AppleDouble 元数据，但删除被同一自动审批机制拒绝，未改用其他删除途径，见 `outside-cache-policy-block.json`。
- 回收站仍有 2 个文件，其中一个约 21 MiB 的特殊名称文件和 desktop.ini，未强行绕过 Windows 原生清理行为。较旧 pnpm store、缺少重建依据的目录、共享工具/缓存以及原资料继续保留。
- `final-disks-attempt1-invalid-selection.json` 是误选 C 盘输入的无效统计，已明确标记保留；有效统计按盘符 D 选择输入，见 `final-disks.json` 和最终完成回执。

本轮已结束；无清理任务在途、无本项目目录写权窗口占用。后续看板/索引由开发写者按本报告与完成回执维护。
