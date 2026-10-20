# D04 首轮 STOP 审计绑定的结构分支修复

## 输入与实际修复

输入提交b818830faf214dcfcf104f53d65b12127c9dd06a，只叠加本次产品/测试两路径，33份非看板继承不参与。证据根.local/goal-20261021-resume/d04-schema-branches01/。

四结构诊断发现双表/完整结构错误接受scope轮次和submission操作员错配。诊断exit0仅表示异常记录成功；原诊断SHA 5bfc2d6f69ad9da3e7e34d18fdebeeca0efcead1785c126806e08eafa1cbcca9。产品仅在两后续表存在分支增加首轮EXISTS约束：scope首轮、无assignment/continuation、原来源设备/订单、授权ID、设备、操作员、仓库和恢复代次一致。保留非首轮历史分支、参数化SQL及错误码，不改迁移或生产能力。

## 实际验证与失败

- attempt02：BeforeAll直接canonicalize JDBC原类型失败INVALID_SUBMISSION。JUnit发现1、启动0、容器失败1，不是业务通过或1项业务失败。原源码/日志/failure-reason保留。
- attempt03：typed canonicalRow修复夹具；新非空隔离库逐阶段升级，直接JUnit Launcher发现/启动/成功各1，失败/跳过0、exit0。各结构正向及五类反例为scope轮次、资格epoch、submission操作员、submission epoch、审计hash；反例事务回滚全表hash守恒。跨迁移仅比较原关键事实投影，不宣称新增列全表hash不变。各阶段表快照数103/135/137/180。两后续表始终空，不调用取消链。
- unit01：53类直接JUnit，335发现/启动、334成功、1符号链接权限assumption中止、0失败、exit0。旧JSON未单列aborted，原stdout明确，不写335全绿。
- ordinary01：Gradle exit1，310 suites、XML4804项/64失败/0错误/19跳过；缺原sku样本、两个固定相对子进程classpath及Windows长路径。native-path-scan.json核711个常规属性失败路径，扩展路径可读且样本属性32。保留原失败，不修改产品安全围栏。
- ordinary02：短D盘工作目录、原sku同SHA及两个classpath桥接，Gradle exit1，XML4804项/10失败/0错误/19跳过；前64失败消除。新10失败是静态扫描src/contracts未供给。原终端4802 completed/17 skipped与XML相差2个disabled容器，两统计均保留。
- ordinary03-static：补齐同输入src/contracts并逐文件核SHA，直接JUnit仅复验上述5类（含其原已过项），15发现/启动/成功、0失败/跳过、exit0。此为分开补验，不改写两轮全量exit1，不宣称一次完整普通回归全绿。

## 证据与限制

三新库已dump并认证停机，19394/19395/19396无监听；conservation-partial.json与runtime-conservation.json核33继承和前片3563 runtime守恒。attempt03成功dump SHA 75f234d3205aed2e32046bfe032232e9eba2279d405dc96ea0cb11f8a3525853。所有原命令、源码、stdout/stderr、JUnit结果、四stage快照和失败保留；resume-closeout02保存本次原字节、失败摘要与路径诊断。直接JUnit与Gradle分别报告，无本片HTTP/Android/设备/APK或原身份，非完整D04、D18和第8节。

清理0字节；此前自动审批拒绝的同目标不换包装重试。生产开关、原库/现网、正式签名、main、部署、Release关闭，localDeletionAllowed=false。总目标active未完成。M71明细订单号与领取前真实预览已接看板，安全收口后优先修复，不冒充已修复或设备已验。
