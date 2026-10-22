# 独立扫码页原凭据卡消费者

日期：2026-10-22。有界开发备份；不记D/O/U或第8节验收通过。版本事实见看板生成块。

## 范围与决定

- 从原独立任务页打开扫码页时，扫码页仍只支持手输随机口令。本片在原扫码页加入受控原卡选择，保留原手输方式，不引入跨页缓存授权。
- 选卡固定原任务对象及原身份七字段/登记钥摘要；选卡时先暂停扫码、清除旧processor，等待期间禁用可能改变任务或授权状态的控件。取消、重建、后台停留、身份/任务变化均不复用旧授权。
- 文件流只在一次性串行队列闭包内交给原卡校验器；沿原恢复围栏、期限/撤销/持久尝试限制、原ScanStore及审计/保存路径。UI和硬件键在待选状态均不能绕过暂停。
- 额外核对授权安装的生命周期竞态：任务Room快照检查在engine monitor外，最后的epoch/前台/任务比较与processor安装在同一monitor内；Room访问和drain不持锁。
- 退出范围：任务页原卡创建的合成原任务，在原扫码Activity选原卡后沿原保存路径处理一条原条码；原卡和任务身份不变。真实SAF、硬件扫码、设备Keystore和完整导出页面链不在本次主机消费者证明内。

## 实际验证

- attempt01：1项RED，缺原扫码页入口；日志与XML保留。
- attempt02：原页面正向及原engine/最近记录/封存4类同次8/0/0/0；输入1106项前后不变。
- attempt03：正向1项、边界5项及原engine4项，三类同次10/0/0/0；输入1107项前后不变。覆盖取消、身份切换、恢复围栏、期限、Activity重建。七身份字段逐项变化在后续边界扩展中验证。
- attempt04：边界和并发竞态两类同次10/0/0/0；输入1108项前后不变。覆盖撤销/限速、旧processor、读卡时离开/恢复及错原任务快照。
- attempt05：最终产品输入六类同次20/0/0/0；输入1108项前后不变。包含原卡建任务、扫码页恢复、手输条码、显式确认、Room保存及300500克原任务汇总；末审计失败后重试保持原capture、原序号和唯一保存审计。测试以合成authority/JCA/file URI和NATIVE Room运行。
- attempt06：单独新增按钮几何测试1/0/0/0，输入1109项前后不变。100%/130%/150%/200%字号下按钮高52/72/80/99像素、文字无省略裁切且能滚动到达；这与attempt05分次执行，不拼接为同次通过，也不代设备/IME验收。
- build01：独立offline `:app:lintDebug :app:assembleDebug`退出0，lint 0 errors/559 warnings。APK为14,501,138字节，SHA-256 `2f11416f4e8d6cb9b8bd8cfba25da27b1061be45b64b28e58b8548b47e567abd`；`apksigner verify --print-certs`通过，证书为既有Android Debug证书（SHA-256 `2437447bae29492167a90d7dd2adaf696b226b05438f995475d4fff523e51196`）。未安装、上传、部署或Release。
- 产品构建后只增加了几何测试源码；最终产品代码/构建脚本/依赖/Room输入与attempt05前后清单相同，故保留其构建证据，没有重建。APK不是干净提交树的最终候选证明。

## 失败、输入与清理

- 原XML/日志、每次独立命令和输入清单均保留在`.local/goal-20261022-resume/independent-work-card01/attempt01..06/`。本片attempt01是预期RED；attempt02–06均退出0。任务页前片的主线程Room错误、时钟前置断言及测试编译失败仅引用前片报告，不归入本片运行。收口摘要脚本初次因默认GBK解码失败，改为显式UTF-8后完成；没有据此改产品或重跑业务测试。
- 作者自审和逐路径SHA见`closeout01/source-review-final.json`；最终结果、所有XML与主要证据哈希见`closeout01/validation-results-final.json`及`evidence-manifest01.json`。文档收口前基线40项中39项字节未变；收口后仅看板和历史索引增加本片内容，其他38项字节不变。提交前完整WIP清单和SHA在`closeout01/wip-before.json`，字节快照在`all-wip-before.zip`。旧看板完整原字节在`closeout01/preimage/`，仓库历史页只归档旧叙述、不重复生成版本事实。
- 收口识别7个任务专用Gradle `project-cache`，合计25,341,416逻辑字节；逐成员哈希、路径、前盘余量及无活动消费者清单保存在`closeout01/cleanup-before.json`。清理命令被自动审批策略拒绝，实际没有删除、释放0字节；没有对该阻断目标换包装重试。所有原XML/日志/APK/build输出、原资料、身份、失败证据及继承源码改动保留。
- 运行器、fixture、证据及APK均在项目`.local/`；没有连接原库/现网或实际测试服务，没有操作设备，没有正式签名。数据库配置指向的datadir仅作只读观察，实际数据库身份未认证。`localDeletionAllowed=false`；取消链、旧run03、生产开关、正式签名、main、部署和Release保持关闭。

总目标仍active、未完成。本片只补扫码页一个主机消费者；完整D/O/U、最终候选和第8节闸门未满足。
