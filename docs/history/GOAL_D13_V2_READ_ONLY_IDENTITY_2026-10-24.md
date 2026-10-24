# D13 v2 身份的 Python 只读互操作

## 结论与范围

输入 `codex/development` / `7e1f3664d6c31f7aad55cad5bd7c6a6cc06085c2`，固定树 `D:/acrylic-pda-outbound`。本片仅修 Python 本地控制读者：`control-read` 可以严格读取 Java 已生成的 `INITIAL_NORMAL_INSTALL` v2 身份，不再因旧 v1 字段集返回 `CONTROL_FIELDS`。原身份不降级、不重写，未增加身份供应、自动提升或修复工具。

`control-init` / `control-apply`（包括原键重放）仍拒绝 v2，拒绝前不追加意图、历史、指针或描述符。结果三标志 `ordinaryWritesAllowed`、`localDeletionAllowed`、`sharedGateIntegrated` 均为 false。**这里只证明控制历史与外部可信 checkpoint 的一致性，不判断 P5 活性/UNKNOWN，不授权业务写入，也不是可信生产供应或恢复放行。**

仅同助手作者/自审及独立 CLI 工具消费，不是独立人员审查。没有运行 Java 构建、数据库、真实 HTTP、Android/APK、模拟器、真机或部署；先前结果只按各自输入引用。D13 整体、D16/D18 和总目标第8节仍未完成，总目标 active。

## 设计与保护

- 决定在 `.local/d13-v2-readonly-20261024/run01/decision.md`，依总目标3.1授权采用最小读者兼容；OS拒绝自halt仍无新安全隔离方法，按既有暂缓条件不重跑。
- `ops/control_identity.py` 只解析精确 v1/v2 字段、整数版本、已知 kind、安装ID、完整恢复集合、非零首命令摘要、外部绝对规范路径。closedAuthority / leaseRoot 与控制树、恢复集及彼此不能重叠，沿既有固定本机盘/别名/重解析拒绝规则。
- `ops/control_history.py` 默认仍为关闭控制写用途；只有 `ControlStore.inspect` 显式选择允许 v2 只读。原完整历史、首命令绑定、NORMAL前缀、writer文件身份/摘要、意图、指针和 checkpoint 检查保留。
- 新合成工厂 `tests/control_v2_factory.py` 仅为测试输入，不是产品供应器；13项专项在 `tests/test_control_v2_identity.py`。
- baseline / before-compat-fix / before-docs 保存全部脏路径原字节/SHA/patch、目标原件和实际环境；新增3代码路径原先不存在有记录。22条非看板继承逐SHA保持，1141个前片产品/资源/renderer/构建输入保持。本片没有改 Java、Android、Web、契约、迁移或生产开关。
- 前片1799/1800 Java生成的控制、P5、外部checkpoint与options共40件逐SHA守恒。这里只从options抽取控制参数，不输出或复制密码；未启动已清数据库，不接系统库或现网。

## 最终输入与实际结果

根 `.local/d13-v2-readonly-20261024/run01/`。green02 / related02 冻结同一179个 Python 输入；输入、实际解释器与脚本SHA、argv、环境、退出码、原日志和夹具逐成员哈希均保留。TEMP/TMP仅对子进程指向本片D盘目录。

| 当次运行 | 实际结果 |
| --- | --- |
| red01 专项，旧读者 | 13测试，3失败、1错误、0跳过，exit1；预期暴露缺口，不能记通过 |
| original-java-v2-red01，旧真实CLI | exit2 / CONTROL_FIELDS，40件原件保持 |
| green01 专项 | 13/0/0/0，exit0 |
| related01 相关回归 | 70项，2失败、0错误、0跳过，exit1；旧v1拒绝码兼容回归 |
| green02 最终专项 | 13/0/0/0，exit0 |
| related02 最终相关回归 | 70/0/0/0，exit0；包含专项13项，不相加冒充83项 |
| original-java-v2-green01 / green02 | 新真实CLI均exit0 / LOCAL_CONTROL_ONLY，原40件逐SHA保持，三个放行标志false |

最终专项覆盖合法v1/v2读取、重启后原字节守恒、v2 init/apply/重放拒绝、未知/非整数版本、缺失/额外字段、错误kind/安装/恢复集合、零或错首摘要、路径别名/重叠、原writer绑定、历史/checkpoint破坏和真实CLI脱敏失败。相关回归保留原70项断言，未降低旧口径。

related01两个错误码回归分别为：v1恢复集合变化原应 `CONTROL_INSTALLATION_MISMATCH`；只把v1字段形状版本改成2原应 `CONTROL_FORMAT_UNSUPPORTED`。修复解析器保留v1原整体比较及伪v2旧形状拒绝，未改旧测试或失败原件。

最终CLI stdout SHA：`db115a9a855a36de02901fce3b1329e65f45ff1ac30a312a8e1555fd579a2592`。专项日志 SHA：`a65a2d116e5b5c1c76b0eda6c264d1822576931dc1d940186eaffa2ebb40f196`；相关回归日志 SHA：`a8abc902cca61d11f766855ab0d3bc9287fedc411576432940ceb9edb9f49824`。完整输入与保护结果在 review01/result.json。

实际命令（解释器绝对路径及哈希见 command.json；不是重跑旧输出的指令）：

```powershell
python -B .local/d13-v2-readonly-20261024/run01/run_python.py green02 targeted
python -B .local/d13-v2-readonly-20261024/run01/run_python.py related02 related
python -B .local/d13-v2-readonly-20261024/run01/consume_final.py
python -B .local/d13-v2-readonly-20261024/run01/review_inputs.py review01
```

## 清理、断点与未完成

仅清本片已结束的5次单测 `fixtures` / `tmp`，10个明确目录、2857个合成文件，逻辑1121675字节。每次原命令、179/178输入、原失败/成功日志、结果及逐文件哈希保留；冻结源未清，前片原身份/P5/UNKNOWN/回执、最终APK/Keystore、未提交和继承改动及共享缓存均未清。D余量窗口34021957632→34024841216字节，观测净增2883584字节，与逻辑删除分列。

清理首轮数组枚举错误、次轮发现两条fixture junction均在删除前停止，原脚本/错误分别保留cleanup01/02；只读核对源码证明链接由既有拒绝测试创建，均指向同case空target。cleanup03先无递归删除链接本身并核原空target仍在，再清普通目录。原result中的逻辑字节因PowerShell有序map统计为空，未改原件；byte-count-supplement.json从原逐文件plan求和补证，不重跑删除。这些是清理工具结果，不计业务失败或验收。

closeout-docs先保存完整混合看板和受影响文档原字节/SHA，再归档旧D13叙述；Android继承整段字节保持。看板事实由 version_facts.ps1 -Write/-Check 生成；共享看板不整文件暂存。明确路径开发备份及push回执另存 closeout-submit01，不计验收，保留22条非看板继承。

接续仍须核实际写权/进程/监听和全部dirty SHA；本片无活跃测试句柄、不分配库、不操作设备。系统3306/33060及新增33318监听身份未由本片认证，只观察不连接/停止。其他本项目聊天的idle/notLoaded只记采样，不当永久写权；不清共享资源。

尚缺可信生产权威供应、P5真实OS拒绝自halt及其他未覆盖入口/故障、最终实际候选整合；Android原身份导出、TEMP/独立SKU简单出口、D16有效资格与D18、第8节及其他D/O/U继续按完成标准，不由只读兼容豁免。取消的承接/多跳/接收者确认激活及旧run03不恢复；生产开关、正式签名、原库/现网、main、部署/Release继续关闭，localDeletionAllowed=false。
