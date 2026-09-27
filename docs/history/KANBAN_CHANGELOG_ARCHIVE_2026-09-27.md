# 看板变更日志归档（2026-09-27）

本文件保存从看板当前区移出的较早变更日志条目（原文，未改），以便当前区保持在 100 行 / 8000 字符上限内。

- 2026-09-27 修 `ExportRendererProcessTest` pid 握手 flake（`9480a21`）：只判 `exists` 就 `parseLong` 会命中 `Files.writeString` 的空文件窗口；改原子发布＋等非空内容（A/B 12998→0）。隔离/负载/全量 718 均 0 失败。
- 2026-09-27 IT 分批评估 attempt 179（`*E2aTemporaryArchive*IT` 3 类 / 1 attempt）：1 通过 2 红，均 `One-shot attempt already used`（结构性，非产品）；构造器 `tests` 计数与 `-Tests` 同名失真（复现 `0111`）→ 需先封装分批入口。
- 2026-09-27 分批入口封装（选 A）：`f34c886` 修构造器 `tests` 计数；新增 `tools/e2a-batch-it.ps1`（每类一 attempt + XML 聚合 + 分类 + `entryIssues`）；runs **181–184** 四类全绿；波次 IT 仍需专用库。
