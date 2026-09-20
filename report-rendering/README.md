# 独立报表文件生成器

Java21库及CLI，仅接收固定数据快照生成 `.xlsx` / `.pdf`，无数据库、HTTP服务或Spring依赖。模型和输出不是正式接口。设计与数据映射见 [R08说明](../docs/parallel/R08/INTEGRATION.md)。

## 运行

需要JDK21、根目录现有Gradle 8.11.1包装器。以下从本 worktree 根目录运行，缓存、浏览器和输出均在任务目录。

```powershell
./report-rendering/run.ps1 installChromium test samples verifySamples installDist
./report-rendering/run.ps1 -GradleArguments @('run', '--args=examples/corrected-test-snapshot.json xlsx build/my-report.xlsx')
./report-rendering/run.ps1 -GradleArguments @('run', '--args=examples/corrected-test-snapshot.json pdf build/my-report.pdf')
```

`samples`生成3组PDF/Excel（empty/corrected/multipage）及50000条大型Excel，落在`report-rendering/build/samples/`；`verifySamples`重新打开这些PDF，检查每页标记/页码、嵌入字体、长条码/文本、合计和签字归属。`examples/corrected-test-snapshot.json`是明确标记的虚构输入，R06/R07字段用于验证接入提案。

另提供已确认首版适配 `--confirmed-v1 <input.json> <xlsx|pdf> <output>`：核对固定PDA/PC原始事实、版本1持久化汇总及原成功回执，派生无更正的原确认报表。`samples`同时生成 `confirmed-v1` 的JSON/Excel/PDF虚构样例。输入包装为模块自有协议，详见[首版接入说明](../docs/parallel/R08/CONFIRMED_V1.md)。服务器的数据库提取、任务和Web下载已另行接入，见[接入记录](../docs/parallel/R08/WEB_INTEGRATION.md)。模板r08-general-2明确标注原确认不含后续更正，净贡献页签名为“版本有效明细”。

本机已有Gradle发行包可通过 `-GradleExecutable '.../gradle.bat'` 只读复用，仍使用本任务GRADLE_USER_HOME。Gradle带单字母选项时统一用 `-GradleArguments @('-I', '...', 'test')`，避免PowerShell公共参数缩写冲突。

首次构建下载并校验固定 Noto Sans CJK SC Regular 2.004，SIL OFL许可证随包分发。字体SHA256为`2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`。网络受限时可先取得相同字体，在Gradle参数中传 `-Pr08FontFile=绝对路径`，仍执行同一摘要校验，不使用系统字体回退。不要把任意字体冒充固定版本。

`installDist`产生标准Java应用分发目录。直接运行分发脚本时须设置任务私有`TEMP`/`TMP`、`JAVA_OPTS=-Djava.io.tmpdir=... -Dpdfbox.fontcache=...`、`PLAYWRIGHT_BROWSERS_PATH`和`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，运行前单独安装Chromium。部署时也须各Worker独立临时目录。推荐开发验证通过run.ps1，已有输出路径会被CLI拒绝覆盖。

## Java调用

```java
ReportSnapshot snapshot = SnapshotJson.read(inputPath);
try (OutputStream out = Files.newOutputStream(privateOutputPath)) {
    new ExcelRenderer().render(snapshot, out);
    // 或 new PdfRenderer().render(snapshot, out);
}
```

首版输入可用 `SnapshotJson.readConfirmedV1(inputPath)`，或用 `ConfirmedV1Adapter.adapt(fixedInput)` 转成同一ReportSnapshot。只允许ORIGINAL_CONFIRMED；不把首版投影当作最新净结果。R06/R07数据未通过此入口接入。

库API把文件原子发布与临时目录控制交给调用方；CLI先写同目录 `.part`，成功后移动且不覆盖已有文件，异常时删除本次临时文件。不关闭调用者OutputStream。业务文件应由后续私有导出Worker管理，不公开目录。

## 明确边界

- 原始明细顺序严格为固定订单成员顺序 + 单内持久化订单序号；跨设备需要已核验交接证明引用。生成器不替代上游交接验真，也不重建未知的硬件先后。
- 输入快照最多512MiB JSON、10000单、200万条原始明细；快照验证/索引驻内存。Excel写入窗口100行，达到1048576总行上限自动拆表，可配置更低行数做受控分片。规模上限是拒绝条件，未全部做上限负载验收。
- 每文本最多1000字符、条码最多256字符；Excel单元格估算换行超过30行会拒绝，避免静默裁切。超大文本需上游另附受控附件，不能偷偷截断。
- 整数克字符串最多30位；任意长度合计使用BigInteger。最多15位克数安全写Excel数值，所有重量均附精确kg文本；高精度时数值列空白。没有工作簿公式、宏或外部链接。
- PDF最多20000渲染行（两层明细+产品+调整+异常），超出明确拒绝，不截断。浏览器启动/加载超时60秒，完整渲染进程的硬超时和内存/CPU约束留给后续Worker。
- 仅确认后的原实绩/指定净版本，暂不实现计划单、待审预览、历史文本原行顺序或MOVEMENT_DATE模板。
- 本目录独立测试无需服务和数据库；服务器接入测试另用专用数据库及合成订单。真实更正、PDA、现场打印机、实际Microsoft Excel客户端或公网部署仍未验收。

锁文件固定Java依赖，Chromium版本由Playwright 1.55.0固定。PDF中文字体内嵌；Excel按客户端可用中文字体显示，阅读端仍需安装适当中文字体。
