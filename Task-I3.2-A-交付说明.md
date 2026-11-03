# Task I3.2-A 交付说明

日期：2026-11-02。已完成测试端准备及无需真实Server的验证；实际隔离S3.2 HTTP/数据库持久化联调未完成，不标为通过。

## 文件清单

- 新增 `app/src/androidTest/java/com/aurora/calculatorvault/feature/trial/IsolatedDeviceTrialHttpTest.kt`：真实HTTP测试harness，3个可独立执行的测试；复用原DeviceTrialRepository、HttpAuthApi、TrialStore接口。
- 修改 `app/src/test/java/com/aurora/calculatorvault/feature/trial/DeviceTrialCapTest.kt`：新增60秒上限随注入时钟到期回归。
- 本说明和 `evidence/task-i3.2-a/`：构建日志、试用模块XML、验证摘要。

生产源码、普通Release、微信、真实标识生成、试用命名空间、应用锁、私密相册、会员及支付均未改动。未开启正常App上传，生成Debug配置仍为false；未执行安装、卸载、清数据或手机时钟操作。

## 场景与结果

完整JVM回归348通过，0失败/错误/跳过；包括既有Fake HTTP契约回归。Instrumentation APK编译/打包通过，尚未安装执行；其3个真实HTTP测试不是上述348项，也未计作已跳过或已通过。

| 场景 | 无真实Server验证 | 实际隔离S3.2 |
|---|---|---|
| 首次连续72小时 | JVM通过 | harness有断言，未执行 |
| 无隐私同意不读标识/不发包 | JVM通过 | harness有计数断言，未执行 |
| 离线48小时首次cap86400 | JVM通过 | harness有断言，未执行 |
| 本地与Server更严格值 | JVM通过 | harness有断言，未执行 |
| 并发合并、15分钟冷却 | JVM通过 | harness有断言，未执行 |
| 离线计时、恢复及不延长 | JVM通过 | harness用可控传输故障模拟，未执行 |
| 73小时首次cap0、EXPIRED | JVM通过 | harness有断言，未执行 |
| 过期后更大cap不复活 | JVM Fake模型通过 | harness直接HTTP读回验证，未执行 |
| 剩余60秒到期 | 新JVM测试通过 | harness使用隔离Server cap60并等61秒，未执行 |
| 同合成标识模拟重装 | JVM通过 | harness新内存Store，未执行 |
| 撤回同意及迟到响应 | JVM通过 | harness覆盖撤回阻止新上传；真实迟到响应未执行，需隔离代理/可控延迟 |
| 登录退出不改变trial记录 | 既有账号Fake测试通过 | 不调用真实微信登录；真实环境状态核对未执行 |

本轮执行：`./gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest`，BUILD SUCCESSFUL。证据见evidence目录。

## Harness的隔离与行为

每个真实HTTP案例使用新的i32a-前缀UUID合成标识，不使用、读取、替换真实Android ID；UUID代表各自测试fixture，不是重置真实设备资格。同一案例中重试/模拟重装坚持同一合成标识。

Clock仅改变测试内存时间；Memory仅保存本次TrialRecord，seen标记也只在内存。没有Application的试用仓库、Keystore或私密数据读写。测试自身的scope在finally取消，无新增正式APK入口。

真实HTTP必须显式提供isolatedTrialBaseUrl及isolatedTrialConfirmed=true，否则JUnit Assume跳过。URL禁止凭据、query、fragment、path，要求独立端口，拒绝8080，包括任何主机别名上的8080；不会默认读取BuildConfig.API_BASE_URL。仅测试代码给HttpAuthApi传入HTTP例外，普通Release网络策略未变。

**这些参数及端口校验不构成数据库隔离的技术证明。** 执行前必须由服务维护者核对独立进程的实际数据源、隔离库名及稳定测试HMAC配置；不得仅将当前8080代理到另一端口后就声称隔离，也不得连接包含真实设备记录的库。

回读API用于断言同fixture记录期限未延长；即使未来该断言通过，也不能代替数据库事务/实际行内容的独立核查。

## 环境阻塞与持久化核对

只读发现Server仓库已有docker-compose.test.yml，定义3307上的aurora_app_test；这只能证明存在测试配置，不能证明已运行、为空、专用，或已有独立S3.2进程连接它。当前未获得独立Server端口、实际数据源确认及测试HMAC就绪证明。因此没有发送任何真实S3.2测试请求，没有读写测试或正式试用表，没有读取密钥值。

实际HTTP：未完成。Server数据库持久化：未核对。不能使用既有Fake通过结果代替这两项验收。

最小后续条件：

1. Server维护者提供专用S3.2实例地址（非8080）、只含测试数据的独立数据库确认、calculator_vault启用及测试HMAC非空/稳定的确认；无需提供Secret值。
2. 提供专用模拟器或无真实数据测试设备。不要在当前含数据手机运行connectedAndroidTest；安装目标App会正常执行其Application本地初始化，因此即使harness不引用真实Store，也应避免对现有手机安装/覆盖测试目标包。
3. 仅在确认环境运行指定harness类，不运行全部androidTest，既有其他instrumentation可能操作存储。未传确认参数的真实HTTP测试应明确记录为跳过，不能算通过。
4. 由Server维护者对合成fixture作用域做只读数据库核对：首次一条、重复不新增、期限只缩短、cap0已过期、再次请求/实例重启后仍保留。不得导出设备标识、HMAC Secret或真实用户记录；无需把数据库凭据交给Android。

## 后续执行示例（目前未执行）

在专用模拟器/设备与上述隔离实例确认后，用现有测试流程构建/安装Debug目标包和androidTest包。只执行测试类，并传instrumentation参数：

- class=com.aurora.calculatorvault.feature.trial.IsolatedDeviceTrialHttpTest
- isolatedTrialBaseUrl=<已确认的专用HTTP/HTTPS地址，非8080>
- isolatedTrialConfirmed=true

可由Android Studio测试Run Configuration添加这些instrumentation参数；测试端点不写入正式配置。本轮不提供可直接误连现用数据库的默认地址，也未代为启动/改配Server或数据库。

60秒真实Server用例自然等待61秒，只改变该合成fixture的期限，不改变产品72小时配置或任何机器系统时钟。JVM同场景立即推进Fake Clock，不需等待。若需完整真实迟到响应验收，应由隔离测试代理定向延迟该合成请求，然后撤回测试同意；现有JVM Deferred用例已验证客户端逻辑。

完成测试端准备后停止。待隔离环境条件具备再完成实际HTTP/持久化验收，不进入B方案、不开发统一权益模块。
