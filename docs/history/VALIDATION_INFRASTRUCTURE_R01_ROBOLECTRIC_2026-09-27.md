# 验证基础设施 R01：Robolectric 基线（2026-09-27）

独立子项（10 轮时间盒第 ① 项，预算 3 轮，实用 2 轮）。**验收独立于业务**：支撑件由自身测试验证。

## 位置与用法

- 支撑件（仅测试源，不改生产行为）：`android/app/src/test/java/com/acrylic/outbound/pda/support/TestInfrastructure.kt`
- 自验：`android/app/src/test/java/com/acrylic/outbound/pda/support/TestInfrastructureTest.kt`
- `memoryDatabase()` 内存 Room 库（用完 `db.close()`；`PdaDatabase` 不是 `Closeable`，不能用 `.use{}`）
- `page(items, nextCursor)` 构造分页（null 会写成 JSON null）
- `assertLastPage(result)` 断言最后一页（拒绝幽灵 `more=true`）
- `openRawVersionedDatabase(context,name,27,ddl)` 开旧版本裸库跑迁移
- `assertDatabaseVersion(PDA_DATABASE_VERSION, actual)` 断言数据库版本

## 固化的两条经验

1. **JSON null ≠ 字符串 "null"**：`optString("nextCursor")` 对 JSON null 返回字面量 `"null"`，曾导致分页误判 `more=true`（R06 LINK 映射安装批次实测抓到并修复）。
2. **迁移测试只引用共享常量**：版本断言必须用 `com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION`，不得写死字面量（此前加一张表要改 14 个测试文件）。

## 四条可复用断言模板

① 拉取→安装→本地一致；② `installRequired=false` 不安装；③ 重复拉取幂等；④ 迁移数据保留。
可执行范例：`temporary/SourceBindingRepositoryTest.kt`（四条全绿）。

## 本轮实测

- `--tests '*TestInfrastructureTest'` → BUILD SUCCESSFUL（5 项自验）。
- 全量 Android 单测：XML=115 tests=460 failures=0 errors=0 skipped=4。
- 第 1 轮编译失败：`PdaDatabase` 非 `Closeable`，`.use{}` 无法推断 → 改 try/finally。

## 未覆盖边界

- 未做抽象框架（只有函数 + "文档即代码"的 Templates 常量）。
- MockWebServer 未进入本轮基线（留给第 ②/③ 项）。
- 既有 14 个迁移测试只共享版本常量，尚未改用 `openRawVersionedDatabase`。
- 仪器化/真机仍为独立子项。