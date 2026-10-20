# E06 当前源封存原件条件消费者（2026-10-20）

## 输入与结论

本轮输入 `codex/development` / `70df324de66084c3c2f975d1d065981abe88a607` 与保留继承 WIP。没有修改源码、测试、权限或生产配置；选择[普通完整回归](GOAL_SERVER_ORDINARY_FULL_REGRESSION_2026-10-20.md)中因未配置外部路径而跳过的四类，使用已保存且能对照历史 SHA 的原件，单独在新证据目录 `.local/goal-20261020-resume/e06-current-sealed-consumers01/` 实跑。

本轮 **24 tests / 0 failures / 0 errors / 0 skipped**、Gradle exit 0：原件 14、采集 6、闭合 3、SKU服务端向量 1。参数化模板展开后实际计数为24；这不是将前轮19条skip直接改成通过，也不是新的完整回归。Windows符号链接1条仍未验。

## 既有原件与可追溯复用

原件来源与输入形态沿[2026-10-18探测报告](GOAL_E08_D17_NEXT_INDEPENDENT_PROBE01_2026-10-18.md)，本轮只读复用，不重导出、不生成身份或签名：

| 材料 | 原路径 | SHA-256 |
| --- | --- | --- |
| 既有Android公开原件目录 | `.local/goal-20261013/e06-export-computer-03/verified-original/` | 33文件逐项见本轮input-manifest.json |
| 已有明文派生归档 | `.local/goal-20261018-resume/next-independent-probe01/derived-relay-archive.zip` | `27f014c186db606bde4600dedb15835ecae644a6781a4309505316a5a179de26` |
| 已有完整闭合归档 | `.local/goal-20261013/e06-closure-computer-02/actual-original.zip` | `1577709afac180638244ea37623e13d63a57a2f73c520df595a7df6ba9c0c8e6` |

派生归档16个成员逐字与既有目录对应，匹配历史哈希；闭合归档单独使用原批次文件，不从缺少闭合元数据的目录制造闭合包。目录中的33文件及两个归档在前后逐 SHA 不变。它们是历史合成隔离 Android 产物，不能称原生产设备身份、可信现场或现时业务状态。加密 `.pdarescue` 不作为明文 ZIP 输入。

## 实际命令与结果

本次子进程显式设置：

```text
E06_REVIEW_ORIGINALS=D:/acrylic-pda-outbound/.local/goal-20261013/e06-export-computer-03/verified-original
E06_REVIEW_ARCHIVE=D:/acrylic-pda-outbound/.local/goal-20261018-resume/next-independent-probe01/derived-relay-archive.zip
E06_CLOSURE_ARCHIVE=D:/acrylic-pda-outbound/.local/goal-20261013/e06-closure-computer-02/actual-original.zip
SKU_PARITY_OUTPUT=D:/acrylic-pda-outbound/.local/goal-20261020-resume/e06-current-sealed-consumers01/sku-parity-current.json
D:/acrylic-pda-outbound/gradlew.bat -p server test --tests com.acrylic.outbound.inventory.SkuParserParityVectorTest --tests com.acrylic.outbound.relay.RelayIndependentOriginalTest --tests com.acrylic.outbound.relay.RelayIndependentCollectionTest --tests com.acrylic.outbound.relay.RelayIndependentClosureTest --offline --no-daemon --console=plain --max-workers=1 -Pr08FontFile=D:/acrylic-pda-outbound/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf
```

TEMP/TMP 和 Java 临时目录只在本子进程指向任务 D 盘 tmp。启动前实查无 Java/Gradle，先前另一会话构建已退出后才开始；未停止其他会话进程。

| 类 | 本轮实跑 | 主要行为 |
| --- | ---: | --- |
| RelayIndependentOriginalTest | 14 | 真实产品读取保存的合成签名原件；公开身份/SKU保持，FORMAL伪造、秘密字段、复用材料、缺成员等拒绝 |
| RelayIndependentCollectionTest | 6 | SKU/授权及原记录对应，重量、计划重复、boot、期限、原扫码输入反例 |
| RelayIndependentClosureTest | 3 | 完整前缀与闭合绑定，变更计划/缺审计前缀拒绝 |
| SkuParserParityVectorTest | 1 | 当前服务端解析47组字节（含授权原sku样本）生成新向量；未计Android消费 |

当前生成 `sku-parity-current.json` 为47组、10453671字节，SHA `641d2a444b9332999fc44bc68b35b29e1f46149870795aa9b07dfe1838191a2d`；文件保留作后续交叉运行时消费者输入，不提交 Git，不把生成本身当作跨端一致性通过。

## 守恒、层级与证据

输入清单绑定2438条服务端源码/资源/构建及报告/样本/字体，原件和31条非看板继承改动逐SHA守恒。生产源码未改；合成反例仅在测试内存中，不改变原件、原清单或签名。作者/自审均本助手。

原始XML已独立封存至 `xml-consumers01/`，完整XML/HTML/binary在 `test-reports-consumers01.zip`；每类计数、逐源码和原件SHA、向量输出及守恒结果在 `test-result-consumers01.json`，SHA `89ad076e54f5eb4a87a90bee037b9975233f5ef15eae0894f8d9ed960fc601f9`。原完整回归XML/跳过清单仍在原任务根，不覆盖或拼成一次全量通过。

本轮没有业务HTTP/MySQL、Room/设备、APK、SAF/外置介质、原生产身份或现时净值验证。不关闭E06/O04/O02/O03、D13/D17/D18、第8节或部署闸门。完整UI/可信事实、必要业务组合和实际最终候选仍沿原范围保留。

## 接续与边界

下一有界消费者可使用本轮47组向量验证Android当前解析，须另列实际源SHA、输入、退出码与范围；不能以历史客户端结果替代。Windows符号链接条件缺口单列。独立预检包工具链由另一会话处理，不代合其隔离源码。

仅本报告与两个索引作开发备份；看板刷新及全WIP/环境见任务 `closeout.md`、`final-manifest.json`。无隔离数据库或可回收大产物；向量是未消费的绑定输入，暂保留。本轮释放0字节，不重复前轮9.44GiB；前轮清理策略拒绝仍按原记录保留，不换包装重试。

总目标active、未完成。生产决定、原库/现网、正式签名、main、部署与Release关闭，localDeletionAllowed=false；不恢复已取消换机承接、多跳、接收者确认/激活链，不启动旧run03；原资料/身份/Keystore/签名回执/唯一证据/最终APK及继承WIP保持。
