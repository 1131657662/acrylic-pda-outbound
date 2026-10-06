# 本机历史资料归档位置

2026-10-06 用户授权整理 D 盘空间：三个旧目录归档到 E 盘，主项目 `.local` 清退已停用历史测试资料。归档保留原文件、失败记录及校验清单，不改变开发完成或验收结论。

归档根目录：`E:/acrylic-archive-20261006/`。仅当前 Windows 用户可访问，包含私有测试配置与签名备份，不提交 Git 或上传。

| 原路径 | 归档路径 |
| --- | --- |
| D:/acrylic-entry-verify | E:/acrylic-archive-20261006/old-directories/acrylic-entry-verify |
| D:/acrylic-pda-worktrees | E:/acrylic-archive-20261006/old-directories/acrylic-pda-worktrees |
| D:/acrylic-snapshots | E:/acrylic-archive-20261006/old-directories/acrylic-snapshots |
| D:/acrylic-pda-outbound/.local/e2a-cross-20260925-编号（编号小于1000，含失败后缀） | E:/acrylic-archive-20261006/main-local/相同目录名 |

历史文档引用旧路径时按此表查找，文件内部相对路径不变。逐项清单为归档根目录 `control/plan.json`，每项的复制日志、SHA-256 清单、验证结果和清退回执在 `control/目录名/`。清单路径采用 Base64 编码以安全保存长路径和特殊字符；`ArchiveManifest.cs` 记录格式。

归档已完成：`old-directory` 3/3、历史 `.local` 434/434 均有 `verified.json` 和 `retired.json`。仅存在 `retired.json` 的项目表示已校验并完成 D 盘清退；控制清单和每项回执仍保留在 `control/`。

完成后实测 D 盘可用 `156703195136` 字节（约 145.941 GiB），E 盘可用 `226000691200` 字节（约 210.480 GiB）。主 `.local` 仍保留运行库、签名目录、B4 断点及编号 1000 之后的 31 个近期目录。

主树 `.local/mysql`、签名目录、当前 B4 断点和编号1000及之后的近期测试目录不在本批清退范围。任何历史数据库恢复必须复制到新的隔离目录并按原协议核验，不直接启动归档里的数据库。归档中测试用 NTFS 链接保留原目标文字但不跟随复制，其原绝对路径可能失效；不得把归档根当新运行目录。

旧 Git 工作树移到 E 盘后须修复 Git 登记并比较各树 HEAD 与未提交清单。归档工作树仍依赖主项目 `.git`，不是独立仓库。历史部分验证副本和快照的旧 `.git` 指针按证据原样保留，不将其作为可提交检出使用。
