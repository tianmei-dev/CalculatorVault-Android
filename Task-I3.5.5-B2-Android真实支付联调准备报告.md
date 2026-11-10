# Task I3.5.5-B2 Android 真实支付联调准备报告

- 日期：2026-11-10
- 基线：`75a4192` + I3.5.4-C 未提交改动（开始前 16 项，全部保留，见 `evidence/task-i3.5.5-b2/git-status-before.txt`）
- 证据目录：`evidence/task-i3.5.5-b2/`（检查点与用例表：`progress.md`）
- 未访问悦森网关或收银台，未创建任何真实订单；未改 AuroraAppServer、8080/8081/8082、服务端数据库；未操作设备；**未提交 Git**。

## 0. 执行前检查

| 项 | 结果 |
|---|---|
| Git 状态 | 13 个已修改文件 + `FakeMembershipProvider` 测试目录 + I3.5.4-C 报告 + 证据目录，共 16 项，均未覆盖、未丢弃 |
| FakeMembershipProvider | 只在 `app/src/test`，main / debug / release 源码集无定义 |
| 构建类型 | 原有 debug / release / localIntegration；Release 未开启 R8 |
| 支付 URL 策略 | 原策略只校验 https，不限制主机 |
| 会话 | `AccountSessionRepository` 应用级单例；退出登录前不清除本地待恢复订单记录 |

## 1. 修改文件清单（仅本阶段）

| 文件 | 改动 |
|---|---|
| `feature/payment/domain/PayLaunchPolicy.kt` | 增加收银台主机白名单（见第 2 节）；新增拒绝原因 `CashierHostsNotConfigured`、`HostNotAllowed` |
| `app/build.gradle.kts` | 新增 `paystaging` 构建类型；所有构建类型增加 `PAYMENT_CASHIER_HOSTS`；抽出生产 HTTPS 地址校验；联调签名从外部注入 |
| `app/src/paystaging/`（新增） | `AndroidManifest.xml` + `res/xml/paystaging_network_security.xml`（全部禁止明文）；`AuthTrace.kt` / `TrialTrace.kt`（与 Release 相同的静默实现） |
| `app/src/release/AndroidManifest.xml`、`res/xml/release_network_security.xml`（新增） | Release 同样显式禁止明文，包括 localhost |
| `app/CalculatorVaultApp.kt` | 白名单注入支付策略；退出登录时清除该账号的待恢复订单；测试会话安装开关来自 BuildConfig |
| `feature/login/session/AccountSessionRepository.kt` | 新增 `onLoggedOut` 回调（只在已知账号主动退出时触发）；`installServerIssuedSession` 默认拒绝，只在 Debug 隔离构建中允许 |
| `feature/membership/presentation/VipPurchaseController.kt` | 「前往支付」2 秒防抖；未配置地址时提示 |
| `feature/membership/presentation/VipPurchaseScreen.kt`、`res/values/strings_membership.xml` | 未配置支付地址时，套餐区显示「当前版本未配置支付服务地址，暂不支持购买」 |
| `feature/payment/data/PaymentModels.kt`、`HttpPaymentApi.kt`、`PaymentRepository.kt` | 新增 `EndpointNotConfigured`：地址为空或不允许明文时明确拒绝，不发送请求，不保留下单上下文 |
| `.gitignore` | 忽略 `*.jks`、`*.keystore`、`keystore.properties`、`signing*.properties` |
| 测试 | `PaymentPoliciesTest`（重写）、`VipPurchaseControllerTest`（+5）、`PurchaseMembershipRaceTest`（+2，并改为使用真实 Session 的端口）、`InstallServerIssuedSessionTest`（+3）、`HttpPaymentApiTest`、`AccountSessionTest`、`SessionRevalidationEntitlementTest`、`PaymentTestFixtures`；新增 `src/testPaystaging/PaystagingBuildConfigTest`、`src/testRelease/ReleaseBuildConfigTest` |

## 2. 域名白名单实现（B2-01）

`PayLaunchPolicy(allowTestKind, cashierHosts)`：

1. 白名单条目必须是小写 ASCII 域名，至少两段，最后一段含字母。带协议、端口、路径、大写、尾随点、通配符、IP 的条目会被丢弃，不会放宽白名单。构建脚本也做同样校验，非法配置直接构建失败。
2. URL 只允许 RFC 3986 字符，拒绝空白、反斜杠、引号、尖括号、控制字符和非 ASCII，长度上限 4096。
3. 必须以 `https://` 开头；`http://` 拒绝（`NonHttpsUrl`），`javascript:`、`intent:`、`file:`、`data:` 等其他方案均拒绝。
4. 主机直接从原始文本截取：`https://` 之后、第一个 `/ ? #` 之前的整段，必须与某个白名单条目**完全相等**。因此用户信息（`@`）、端口（含 `:443`）、子域名、前缀伪装（`cashier.example.evil.example`）、大小写和尾随点都无法匹配。
5. 再用 `java.net.URI` 交叉校验：解析出的 authority、host 必须与截取结果一致，且没有 userinfo 和端口。这样校验器与浏览器之间的解析差异不能把请求带到其他主机。
6. 白名单为空时拒绝所有链接（`CashierHostsNotConfigured`）。
7. 只校验首次打开的收银台 URL，不干预浏览器内的微信、支付宝跳转。
8. 白名单按构建类型配置：`DEBUG_` / `LOCAL_` / `PAYSTAGING_` / `RELEASE_PAYMENT_CASHIER_HOSTS`（Gradle 属性，逗号分隔）。**所有构建默认都为空**，没有猜测悦森正式域名。

## 3. paystaging 构建类型（B2-02）

| 项 | 配置 |
|---|---|
| 基础 | `initWith(release)`，不可调试，`versionNameSuffix = -paystaging`，不开 R8（与 Release 一致） |
| applicationId | 与 Release 相同（`com.aurora.calculatorvault`）：微信登录绑定已注册的包名，加后缀会导致无法登录 |
| API 地址 | `-PPAYSTAGING_API_BASE_URL=https://...` 注入；构建时校验必须为 https 且是公网域名（拒绝 http、IP、userinfo、`localhost` / `.local` / `.lan` / `.internal` / `.test` / `.invalid`）。登录、会员、支付、试用都使用该地址 |
| 未配置地址 | 仍可构建，但运行时所有支付请求返回 `EndpointNotConfigured`，不发送请求；页面显示「当前版本未配置支付服务地址，暂不支持购买」。不会回退到任何开发地址 |
| 支付开关 | `PAYMENT_TEST_KIND_ALLOWED=false`，`TEST_SESSION_INSTALL_ENABLED=false`，`TRIAL_DEVICE_UPLOAD_ENABLED=false`，`LOCAL_HTTP_BASE_URL` / `TRIAL_HTTP_BASE_URL` 为空 |
| 明文流量 | `usesCleartextTraffic=false`，并有专用网络安全配置：所有主机（含 localhost / 127.0.0.1 / ::1）禁止明文 |
| 测试会话桥 | 不在 paystaging 源码集；`installServerIssuedSession` 在该构建中直接抛异常 |
| Fake 支付类型 | TEST 类型被拒绝（策略与 BuildConfig 测试双重验证） |
| 联调签名 | `PAYSTAGING_STORE_FILE` / `PAYSTAGING_KEY_ALIAS` 可来自 Gradle 属性或环境变量；两个口令**只从环境变量读取**。四项必须同时提供；keystore 必须存在、不能是 `debug.keystore`、必须在仓库外。未提供时输出未签名 APK。当前没有联调签名材料，所以没有生成或覆盖任何密钥 |

构建脚本校验（`build-config-validation.txt`）：http 地址、IP 地址、`.lan` 主机、白名单带协议、白名单大写、白名单带端口、只提供部分签名参数，这 7 种配置都在配置阶段失败，合法配置通过。

## 4. 支付按钮防抖与订单恢复（B2-03）

| 行为 | 实现 |
|---|---|
| 前往支付防抖 | 控制器记录上次打开时间，2 秒内重复点击直接忽略，只打开一次浏览器；离开订单页后重置。打开收银台不改变订单状态，也不创建订单 |
| 浏览器返回 | 回到页面时立即查询一次订单状态（沿用 I3.5.4-C 的去重：查询进行中则复用） |
| 退出登录 | `AccountSessionRepository.logout()` 在已知账号主动退出时回调，App 只清除该账号的本地待恢复记录（`discardPending(userId)`）。会话过期、取消登录、身份未知时不触发；回调异常不影响退出 |
| 切换账号 | 订单页按账号与会话代际隔离：切换后当前页回到套餐页，新账号看不到旧账号的订单；旧账号未退出时再次登录仍可恢复 |
| 不变的规则 | 同一 `clientRequestId` 的幂等重试规则不变；会员刷新逻辑不变；EXPIRED 订单迟到支付的服务端履约不受影响（客户端只清本地记录） |

注意：退出登录会结束本地下单上下文。如果退出前有一次下单未得到确认，重新登录后不会复用原 `clientRequestId`，服务端可能多出一笔未支付的 PENDING 订单。该订单会按 30 分钟自然过期，不影响资金和会员。

## 5. JVM 测试结果

| 范围 | 结果 |
|---|---|
| `testDebugUnitTest` 全量 | 69 个测试类，575 个用例，0 失败，4 跳过（I3.5.4-C 为 559，本阶段新增 16） |
| `testPaystagingUnitTest` / `PaystagingBuildConfigTest` | 3/3，针对生成的 paystaging BuildConfig |
| `testReleaseUnitTest` / `ReleaseBuildConfigTest` | 2/2，针对生成的 Release BuildConfig |

AGP 默认只为被测构建类型创建单元测试任务，以上两项通过命令行 `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` 临时开启，未改项目配置。

B2-04 用例覆盖（全部 PASS，详见 `progress.md`）：

| 编号 | 覆盖内容 |
|---|---|
| B2-T01 | URL 类型解析；6 种合法 HTTPS 形式允许打开 |
| B2-T02 | 非白名单、子域名、前缀伪装主机被拒绝 |
| B2-T03 | 大小写、尾随点、端口、userinfo、`#@` / `?@` 绕过被拒绝 |
| B2-T04 | http 与 20 种恶意或畸形 URL（javascript、intent、file、data、反斜杠、CRLF、非 ASCII、IP、超长等）被拒绝 |
| B2-T05 | 空白名单或只含非法条目时拒绝所有链接 |
| B2-T06 | TEST 类型在 Release 策略中被拒绝；paystaging / Release 的 BuildConfig 测试再次确认 |
| B2-T07 | 前往支付连续点击 6 次只打开一次，2 秒后可再次打开 |
| B2-T08 | 浏览器返回后立即查询，FULFILLED 后通过会员刷新端口同步 |
| B2-T09 | 非白名单链接或未配置白名单时不打开浏览器，提示支付链接无效 |
| B2-T10 | API 地址未配置：套餐、下单、查询都不发请求 |
| B2-T11 | 切换账号后不展示其他账号订单 |
| B2-T12 | 真实 Session 退出登录清除本账号记录，重新登录不恢复、不自动下单 |
| B2-T13 | FULFILLED 但 `/membership/me` 未确认时不发放 VIP；服务端确认后才获得 VIP |
| B2-T14 | 默认构建拒绝安装测试会话；退出登录回调语义 |
| 已有用例 | 进程恢复（不新建订单）、FULFILLED 通过 `/membership/me` 获得 VIP（B05、F4）全部通过 |

所有测试 URL 都使用 RFC 2606 保留的 `.example` 域名，没有任何测试指向或打开真实悦森域名。

## 6. 构建结果

见 `build-summary.txt`，全部 exit 0：

| 构建 | 结果 |
|---|---|
| `assemblePaystaging`（注入占位地址 `https://paystaging-api.example.com`，白名单 `cashier.example`） | 成功 |
| `assembleDebug` | 成功 |
| `assembleLocalIntegration` | 成功 |
| `assemblePaystaging`（默认、未配置） | 成功 |
| `assembleRelease` | 成功（加入网络安全配置后又重建一次，也成功） |
| `assembleDebugAndroidTest` | 成功 |
| `-PISOLATED_MEMBERSHIP_TEST=true` 的 Debug + AndroidTest | 成功 |

占位地址只写入构建配置用于验证注入，没有任何网络访问。

## 7. APK / DEX 安全扫描

方法：`evidence/task-i3.5.5-b2/apk-scan.ps1` 解压每个 APK，在全部 DEX 以及 XML / arsc / assets 中按 UTF-8 和 UTF-16 搜索。Release 与 paystaging 未开启混淆，类名保持原样，所以按类名搜索是可靠的。秘密值只报告是否命中，不输出。

| APK | 测试类与入口（16 项） | 隔离地址（8081 / 8082） | 商户密钥特征（14 项） | 测试 Token 与 openid（37 个） |
|---|---|---|---|---|
| **release** | 0 | 0 | 0 | 0 |
| **paystaging（默认）** | 0 | 0 | 0 | 0 |
| **paystaging（注入）** | 0 | 0 | 0 | 0 |
| localIntegration | 0 | `192.168.0.107:8081`（既有试用地址设计） | 0 | 0 |
| debug（默认 / 隔离） | `IsolatedTestSessionAccess`（Debug 设计内容） | `:8081` / `10.0.2.2:8081` | 0 | 0 |
| androidTest | 9 项命中（测试本身） | 命中 | 0 | 0 |

- 测试类与入口包括：`FakeMembershipProvider`、`IsolatedTestSessionAccess` / `Bridge`、`EntitlementInstrumentationBridge`、`PaymentQueryProbe`、隔离 E2E 测试类、`/internal/fixture`、`X-Fixture-Control-Token`、fixture 标记等。
- 商户密钥特征包括：`-----BEGIN`、`PRIVATE KEY`、`merchantKey`、`mchKey`、`appSecret`、`signKey`、`yusen` 等。
- 非发布构建中的命中证明扫描脚本能检出目标。

BuildConfig 与清单（`buildconfig-manifest.txt`、`network-security.txt`）：

| 项 | paystaging | Release |
|---|---|---|
| `PAYMENT_TEST_KIND_ALLOWED` | false | false |
| `TEST_SESSION_INSTALL_ENABLED` | false | false |
| `PAYMENT_CASHIER_HOSTS` | 默认空；注入后为 `cashier.example` | 空 |
| `API_BASE_URL` | 默认空；注入后为 https | 空 |
| `LOCAL_HTTP_BASE_URL` / `TRIAL_HTTP_BASE_URL` | 空 | 空 |
| `usesCleartextTraffic` | false | false |
| 网络安全配置 | 所有主机禁止明文 | 所有主机禁止明文（本阶段新增） |
| debuggable | 否 | 否 |
| 签名 | 未签名（无联调签名材料） | 未签名 |

`DEBUG` 字段在静态初始化里赋值，`dexdump` 读不出实际值；paystaging / Release 的 BuildConfig 单元测试已在运行时断言为 false。

构建脚本中没有任何口令或 keystore 字面量（`git-status-after.txt` 中的检索结果），口令只以环境变量名出现。

## 8. 未完成的人工配置

1. **悦森收银台主机名**：需人工确认正式域名（可能有多个）后填入 `PAYSTAGING_PAYMENT_CASHIER_HOSTS`；在此之前 paystaging 拒绝打开任何收银台。
2. **paystaging API 地址**：服务端 paystaging 部署后提供 HTTPS 域名（需要有效 TLS 证书）作为 `PAYSTAGING_API_BASE_URL`。
3. **联调签名**：在仓库外生成联调 keystore，用环境变量提供四项参数。
4. **微信开放平台**：paystaging 与 Release 使用同一包名。联调签名的 MD5 须在微信开放平台登记（或联调改用已登记的签名），否则 paystaging 无法微信登录；该构建不允许测试会话注入，所以登录是联调前提。
5. **Release**：`RELEASE_API_BASE_URL`、`RELEASE_PAYMENT_CASHIER_HOSTS` 和正式签名仍待上线前配置。

## 9. 进入 B3 的阻塞项

1. 服务端 paystaging 尚未部署，没有可用的 HTTPS API 地址。
2. 悦森收银台域名未确认，白名单为空。
3. 无联调签名材料，paystaging APK 当前未签名，无法安装。
4. 联调签名未在微信开放平台登记，paystaging 无法登录。
5. 联调设备未确定：当前限制不能操作物理手机；模拟器需要安装微信并能登录，且需要真实浏览器访问收银台，这只能在 B3 授权后进行。
6. 需确认服务端 paystaging 对真实悦森下单只返回 `pay.kind=URL`，且链接主机在白名单内。

## 10. 风险与说明

- `installServerIssuedSession` 方法本身仍在 main（Release / paystaging DEX 中存在该方法名），但没有任何调用方，且在这两个构建中一调用就抛异常。彻底移除需要开启 R8 或重构会话仓库，本阶段未做。
- Release 与 paystaging 仍未开启 R8。上线前建议评估开启，开启后需用 mapping 重新做类名扫描。
- 防抖基于 2 秒时间窗；用户从浏览器很快返回后，2 秒内再次点击需要再点一次。
- 白名单要求完全相等，如果悦森收银台使用多个子域，必须逐个列入。

## 11. 最终验收结论

B2-01 至 B2-05 的代码与构建准备均已完成并通过验证：

- 白名单默认拒绝，且有 30 余种绕过与恶意形式的测试。
- paystaging 构建类型具备 HTTPS 注入、禁止明文、禁止 TEST、禁止测试会话、外部签名。
- 前往支付防抖、退出登录清除记录、账号隔离均有测试覆盖。
- 全量 JVM 通过，所有构建成功，Release 与 paystaging 产物扫描 0 命中。

进入 B3 前仍有第 9 节的环境与人工配置阻塞，需人工完成。

---
本阶段到此结束：未部署、未访问悦森、未进入真实支付联调；改动未提交 Git，等待审核。
