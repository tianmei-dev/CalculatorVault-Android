# Task I3.5.4-C Android 支付体验与 Release 安全整改报告

- 日期：2026-11-10
- 基线：`75a4192 支付第二阶段，联调中`（开始前工作区干净，见 `evidence/task-i3.5.4-c/git-status-before.txt`）
- 范围：仅 F3 / F4 / F5。未修改支付业务规则、服务端、8080/8081、服务端数据库；未操作真实悦森网关；**未提交 Git**。
- 证据目录：`evidence/task-i3.5.4-c/`（进度与用例表：`progress.md`）

## 1. 结论

| 项 | 结论 |
|---|---|
| F3 断网反馈慢 / 重复刷新打断查询 | 已修复。断网后立即提示「网络连接不可用，请检查网络后重试」；查询进行中重复点击「刷新支付状态」、页面回前台、网络恢复都复用正在进行的请求，不再取消重发 |
| F4 过期订单本地恢复记录永久保留 | 已修复。EXPIRED 订单页提供「结束查看此订单」，点击后只清除该订单的本地恢复记录，再次进入 VIP 页显示套餐 |
| F5 FakeMembershipProvider 进入 Release DEX | 已修复。类迁移到 `app/src/test`；Release APK 三个 DEX 均不包含该类及其他测试入口、8081 地址、测试 Token |
| JVM | 559 个用例，0 失败，4 跳过（基线 546，新增 13） |
| 构建 | Debug / localIntegration / Release / DebugAndroidTest / 隔离参数 Debug+AndroidTest 全部成功 |
| 模拟器 | 未执行（按任务要求优先 JVM、静态扫描与构建验证） |

## 2. 根因

**F3**
1. 订单查询与套餐、下单共用 10s 连接 / 15s 读取超时，网络不可达时一次查询最长约 15s 才失败，加上轮询间隔，体感约 16s。
2. App 只监听网络 `onAvailable`，不知道网络已断开，只能靠 HTTP 超时发现。
3. `refreshOrder()`、网络恢复、页面回前台都调用 `restartPolling()`，会取消正在进行的 HTTP 并重新发送；连点刷新时请求反复被打断，结果更慢。

**F4**：`closeOrderView()` 有意保留本地恢复记录（为 PENDING 订单可恢复而设计），但对 EXPIRED 订单同样保留，于是 `tryResume()` 每次进入 VIP 页都会恢复这个已过期订单。

**F5**：`FakeMembershipProvider` 定义在 `app/src/main` 的 `MembershipProvider.kt`，虽然只有 JVM 测试引用，但 Release 未开启 R8（`isMinifyEnabled = false`），类被原样打入 Release DEX。

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `feature/payment/data/HttpPaymentApi.kt` | 新增仅用于订单查询的超时：连接 5s、读取 8s；套餐和下单仍为 10s / 15s |
| `feature/payment/data/PaymentRepository.kt` | 新增 `networkAvailable` 状态、`onNetworkLost()`；新增 `discardOrderRecord(userId, orderNo)`，只在用户和订单号都匹配时清除 |
| `app/CalculatorVaultApp.kt` | 复用现有默认网络回调，增加 `onLost`（仅当没有替代网络时上报断网）和启动时无网的初始状态 |
| `feature/membership/domain/VipPurchaseModels.kt` | `Ordering.lastQueryFailed` 改为 `queryIssue: OrderQueryIssue?`（`NetworkUnavailable` / `Temporary`），保留 `lastQueryFailed` 计算属性 |
| `feature/membership/presentation/VipPurchaseController.kt` | 查询进行中去重；已知断网时立即提示并跳过注定失败的请求（每 3 次跳过仍真实探测一次，防止断网信号滞后）；失败按网络状态分类；EXPIRED 订单结束查看时清除本地记录 |
| `feature/membership/presentation/VipPurchaseScreen.kt` | 查询中显示「正在查询支付状态…」并禁用刷新；断网 / 超时分别提示；EXPIRED 按钮改为「结束查看此订单」，并附迟到支付说明 |
| `res/values/strings_membership.xml` | 新增 4 条文案 |
| `feature/entitlement/domain/MembershipProvider.kt` | 删除 `FakeMembershipProvider` |
| `app/src/test/.../entitlement/domain/FakeMembershipProvider.kt` | 新增（同包名，内容原样迁移） |
| `androidTest/.../IsolatedPaymentE2ETest.kt` | B09 同时接受新的断网文案（仅改期望文本） |
| 测试 | `VipPurchaseControllerTest`（+9）、`PaymentRepositoryTest`（+2）、`HttpPaymentApiTest`（+1）、`PurchaseMembershipRaceTest`（+1，提取了共用的真实会员链路构造） |

未引入新依赖；未改动轮询策略 `PaymentPollPolicy` 的间隔与退避。

## 4. 修改前后行为

| 场景 | 修改前 | 修改后 |
|---|---|---|
| 订单页断网 | 约 16s 后才提示「暂时无法查询订单…」 | 断网回调到达即提示「网络连接不可用，请检查网络后重试」，不等待 HTTP 超时 |
| 断网期间轮询 | 每次都发 HTTP 并等待超时 | 按现有退避间隔跳过注定失败的请求，每 3 次仍真实探测一次 |
| 有网但服务端超时 | 15s 读取超时 | 8s 读取超时，提示「暂时无法查询订单，稍后自动重试」，按 2/5/10/30s 退避 |
| 查询中连点刷新 | 每次取消并重发 | 复用进行中的请求，按钮显示查询中并禁用 |
| 网络恢复 / 回前台 | 取消进行中请求并重发 | 无进行中请求时立即查询一次，有则复用 |
| 查询失败 | 不改变订单状态 | 不变：不改变状态、不判为支付失败或过期、不新建订单 |
| EXPIRED 订单 | 「返回套餐」后再进入仍恢复该订单 | 「结束查看此订单」后清除本地记录，再进入显示套餐；结束前仍可「刷新支付状态」 |
| PENDING 订单返回套餐 | 保留记录并可恢复 | 不变 |
| 结束查看后迟到支付成功 | — | 服务端订单不动；会员仍通过 `/membership/me` 同步生效 |

## 5. JVM 测试

- 全量：`:app:testDebugUnitTest`，69 个测试类，559 个用例，0 失败，0 错误，4 跳过（`evidence/task-i3.5.4-c/jvm-full-summary.txt`、`jvm-full.log`）。
- 新增用例（详见 `progress.md` 用例表）：

| 编号 | 覆盖 |
|---|---|
| C-F3-01 | 断网立即提示；断网 15s 内无 HTTP；状态保持 PENDING；网络恢复立即查询并清除提示；不新建订单 |
| C-F3-02 | 断网信号一直未恢复时仍会真实探测 |
| C-F3-03 | 查询进行中连点刷新 5 次、回前台、网络恢复：HTTP 次数不变，结果只应用一次 |
| C-F3-04 | 有网超时归类为临时故障，退避生效，状态不变 |
| C-F3-05 | 前后台切换 4 次后只有一个轮询循环 |
| C-F3-06 | 查询进行中切换账号：旧结果被丢弃，不触发会员刷新 |
| C-F3-07 | 只有订单查询使用 5s/8s，套餐与下单仍为 10s/15s |
| C-F3-08 | 网络断开 / 恢复信号 |
| C-F4-01 | EXPIRED 可手动刷新；结束查看清除记录；重新进入显示套餐且不发恢复查询 |
| C-F4-02 | PENDING 返回套餐保留记录，重新进入恢复且不新建订单 |
| C-F4-03 | 结束查看只清除本订单记录，不影响其他订单或其他用户 |
| C-F4-04 | 真实 Session、Entitlement、会员 Provider 与刷新协调器链路：结束查看后迟到支付成功，VIP 通过 `/me` 生效；重新进入显示套餐 |

已有的会员、试用、应用锁、相册、订单、账号隔离测试全部通过，无回归。

## 6. 构建

见 `evidence/task-i3.5.4-c/build-summary.txt`，全部 exit 0：

| 任务 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功 |
| `:app:assembleLocalIntegration` | 成功 |
| `:app:assembleRelease` | 成功 |
| `:app:assembleDebugAndroidTest` | 成功 |
| `-PISOLATED_MEMBERSHIP_TEST=true` 下 `assembleDebug` + `assembleDebugAndroidTest` | 成功 |

## 7. Release APK / DEX 扫描

- 产物：`app-release-unsigned.apk`，SHA256 `91ECFDFE…D45931`，包含 `classes.dex`、`classes2.dex`、`classes3.dex`。
- 方法：脚本 `evidence/task-i3.5.4-c/release-dex-scan.ps1` 直接解压 APK，在 DEX 字节中搜索。Release 未开启混淆，类名保持原样，所以按类名搜索是可靠的。Token 从本地文件读取，只记录是否命中，不输出明文。
- 结果（`release-dex-scan.txt`）：**0 命中**。
  - 类：`FakeMembershipProvider`、`IsolatedTestSessionAccess`、`IsolatedTestSessionBridge`、`TestSessionInstaller`、`EntitlementInstrumentationBridge`、`PaymentQueryProbe`、`IsolatedPaymentE2ETest`
  - 测试支付控制：`/internal/fixture`、`X-Fixture-Control-Token`、`fake-payment`、`fixture/payment`、`cv_i354b`、`cv_i343d1`、`CV_I354B_MARKER`
  - 地址：`10.0.2.2:8081`、`192.168.0.107:8081`、`:8081`
  - 37 个测试 Token / openid / fixture 控制 Token 值
- 对照（`control-debug-isolated-dex-scan.txt`）：同一脚本扫描隔离参数构建的 Debug APK，命中 `IsolatedTestSessionAccess` 和 `10.0.2.2:8081`（均为 Debug 设计内容），证明脚本能检出目标；Debug 中同样已无 `FakeMembershipProvider`。
- Release `BuildConfig`（`dexdump` 读取，`release-buildconfig-dexdump.txt`，统计 Key 已脱敏）：`PAYMENT_TEST_KIND_ALLOWED=false`、`TEST_SESSION_INSTALL_ENABLED=false`、`DEBUG=false`，`API_BASE_URL` / `LOCAL_HTTP_BASE_URL` / `TRIAL_*_BASE_URL` 均为空。
- Release 继续拒绝 TEST、HTTP 和未知 `pay.kind`：由 `PaymentPoliciesTest`（`allowTestKind=false`）与 `HttpPaymentApiTest` 中的 Release 规则用例覆盖，全量通过。

## 8. 模拟器验证

未执行。本任务按要求优先 JVM、静态扫描与构建验证；未连接任何设备，未安装 APK，未操作 8081 和物理手机。如需运行时确认，建议在 emulator-5554 上用 `adb install -r` 后复测 B09（断网提示时间）和过期订单「结束查看此订单」。

## 9. 订单、会员与账号隔离回归

- 查询失败（断网、超时、5xx）都不会改变已知订单状态，不判为支付失败或过期，不新建订单，`clientRequestId` 规则不变。
- 账号切换或会话失效时，进行中查询的结果按 generation 丢弃（C-F3-06 与已有测试）。
- 结束查看只删除本地记录中用户和订单号都匹配的一条；不触碰会员状态、Sticky 缓存、账号数据或服务端订单。
- 没有新增后台订单轮询服务；轮询仍只在 VIP 页可见时运行。

## 10. 未解决问题与风险

1. **断网信号的局限**：「立即提示」依赖系统默认网络的 `onLost`。如果设备显示已联网但实际不可达（如强制门户、服务端不可达），仍走 HTTP 超时，最长约 5s 连接 + 8s 读取，提示为「暂时无法查询订单，稍后自动重试」。
2. **结束查看后的迟到支付**：页面不再追踪该订单，会员到账依赖现有 `/membership/me` 刷新时机（回前台、手动刷新、会话刷新），没有即时通知。这是任务要求的行为，页面已提示用户。
3. **Release 未开启 R8**：本次通过源集隔离排除了 Fake 类；Release 仍未做代码收缩与混淆，后续上线前建议评估开启。
4. **Release 服务端地址为空**：`API_BASE_URL` 为空是既有状态，正式地址需在 I3.5.5 前配置。
5. 模拟器运行时未复测（见第 8 节）。

## 11. I3.5.5 真实悦森接入准备项

1. 配置 Release 正式 HTTPS 服务端地址，并确认收银台链接域名白名单。
2. 服务端切换真实悦森 Provider 后，确认 `pay.kind` 只返回 URL 类型；Release 已拒绝 TEST / HTTP / 未知类型。
3. 确认真实网关下单与查询的延迟分布，评估 5s/8s 查询超时是否需调整（只改订单查询超时，不动其他请求）。
4. 确认真实支付回跳 App 后触发的查询时机（回前台立即查询已具备，且不会重复发送）。
5. 小额真实订单验收：支付成功、超时未付、过期后迟到支付三条路径，确认会员通过 `/membership/me` 到账。
6. 上线前评估开启 R8，并对最终签名包重跑 `release-dex-scan.ps1`。

---
本阶段到此结束，不进入 I3.5.5；改动未提交 Git，等待审核后再确定提交范围。
