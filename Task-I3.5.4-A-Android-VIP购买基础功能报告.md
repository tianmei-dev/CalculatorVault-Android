# Task I3.5.4-A Android VIP 购买基础功能报告

日期：2026-11-09。只修改 Android 工程；未改 AuroraAppServer、未连真实悦森网关、未发起付款、未开发退款，也没有改动会员权益判定、试用、应用锁和私密相册的规则。

## 结论

套餐展示 → 创建订单 → 打开悦森收银台（URL）或测试支付等待（TEST）→ 轮询订单状态 → FULFILLED 后经现有链路刷新会员，这套基础流程已完成。

- JVM 测试 **544 个，0 失败**，其中本任务新增 / 重写 52 个。
- Debug / localIntegration / Release / androidTest 均**构建成功**。
- 本阶段没有在模拟器或真机上运行；8081 的 Android 联调闭环留给 I3.5.4-B。

---

## 1. 修改文件及模块说明

### 新增（`feature/payment`）

| 文件 | 说明 |
|---|---|
| `data/PaymentModels.kt` | `MembershipPlanOffer`、`PaymentOrder`、`PaymentOrderStatus`（含 `UNKNOWN`）、`PayInstruction`（`toString` 脱敏）、`PaymentError`、`PaymentResult` |
| `data/HttpPaymentApi.kt` | `PaymentApi` 接口；`HttpURLConnection` 实现（不跟随重定向、无重试、无 Cookie）；信封解析与错误码映射 |
| `data/PaymentRepository.kt` | App 级：套餐、建单（`clientRequestId` 生命周期、单飞）、查单、账号绑定、网络恢复信号 |
| `data/PendingPurchaseStore.kt` | 恢复用元数据（userId / planCode / clientRequestId / orderNo），存于私有 SharedPreferences；不含 Token 和支付链接 |
| `domain/PayLaunchPolicy.kt` | `pay.kind` 安全判定（URL / TEST / 拒绝） |
| `domain/PaymentPollPolicy.kt` | 轮询频率与失败退避 |

### 新增（`feature/membership/presentation`）

| 文件 | 说明 |
|---|---|
| `VipPurchaseController.kt` | 页面级状态机：登录门禁、建单、等待、轮询、FULFILLED 后的会员同步、账号隔离、进程恢复 |

### 修改

| 文件 | 修改内容 |
|---|---|
| `membership/domain/VipPurchaseModels.kt` | 移除 `VipPaymentMethod`、本地价格和 `VipPurchaseSelection`；`VipPlan` 只保留展示键（`serverCode`）；新增 `PlansState`、`PurchasePhase`、`OrderView`、`PurchaseNotice` |
| `membership/presentation/VipPurchaseViewModel.kt` | 改为薄封装：把 `viewModelScope` 交给 Controller |
| `membership/presentation/VipPurchaseScreen.kt` | 套餐卡片改为服务端数据；微信/支付宝选择改为悦森收银台说明；新增永久会员横幅和订单等待面板；`ON_RESUME` / `ON_PAUSE` 联动轮询 |
| `membership/data/MembershipRefreshCoordinator.kt` | 新增 `refreshAfterPurchase()`，以及刷新原因 `PurchaseFulfilled` |
| `app/CalculatorVaultApp.kt` | 注入 `paymentRepository`、`vipPurchaseDependencies`；网络恢复回调通知支付模块 |
| `vault/presentation/VaultMainScreen.kt` | ViewModel Factory 传入依赖；未登录时打开现有登录弹窗（`LoginSource.MembershipPurchase`） |
| `app/build.gradle.kts` | 新增 `PAYMENT_TEST_KIND_ALLOWED`（Debug / localIntegration 为 true，Release 为 false） |
| `res/values/strings_membership.xml` | 新增购买流程文案 |
| 测试 | 新增 `HttpPaymentApiTest`、`PaymentPoliciesTest`、`PaymentRepositoryTest`、`VipPurchaseControllerTest`、`PaymentTestFixtures`；重写 `VipPurchaseViewModelTest`；`MembershipRefreshCoordinatorTest` +1 |

未引入任何新依赖：没有 Retrofit / OkHttp / 微信或支付宝 SDK，也没有加 `androidx.browser`。

---

## 2. VIP 套餐展示与支付等待页面

在原页面基础上调整，紫色主题、深色模式（`AppColors`）、权益卡片、非自动续费提示、协议勾选、未成年支付提示均保留。

### 套餐区

- 页面打开即请求 `/api/v1/membership/plans`。卡片上的名称、`¥priceDisplay`、期限（`N天` 或「永久有效」）全部来自服务端；默认选中 YEARLY，「推荐」角标也挂在 YEARLY 上。
- 加载中、加载失败（可重新加载）、空数据都有明确状态。没有拿到有效套餐时，购买按钮不可用。
- 原来的「微信支付 / 支付宝」选择**已移除**，改为「通过悦森统一收银台完成支付，可在收银台中选择付款方式」。
- 已是永久会员（本账号的已验证 VIP 且 `permanent`）：显示横幅，按钮变为「已开通永久会员」且不可点，套餐也不可选。

### 底部按钮

| 状态 | 按钮 |
|---|---|
| 空闲 | 立即开通 ¥{服务端价格} |
| 创建中 | 正在创建订单…（不可点） |
| 创建失败 | 重试创建订单（复用同一 `clientRequestId`） |
| 待支付 + URL | 前往支付 |
| TEST / PAID / EXPIRED | 刷新支付状态 |
| FULFILLED | 完成 |

### 订单等待面板

- 展示套餐名、金额（取订单返回的 `amountDisplay`）、订单号（只显示后 10 位）、状态文案、剩余支付时间，以及「刷新支付状态」「返回套餐」。
- **URL**：用户主动点「前往支付」，用系统浏览器（`ACTION_VIEW` + `CATEGORY_BROWSABLE`）打开，不用 WebView。找不到浏览器时给出提示。返回 App 后在 `ON_RESUME` 立即查询原订单。
- **TEST**：显示「当前为测试支付环境，请在电脑端模拟支付结果」；不打开浏览器，没有「付款成功」按钮。
- **被拒绝的支付信息**（Release 下的 TEST、http 链接、未知 kind、链接缺失）：显示「支付链接无效…」，不会打开。
- **FULFILLED**：显示「会员开通成功」；如果会员同步失败，显示「支付成功，会员状态同步中」并提供「重新同步会员状态」。

---

## 3. API 请求和响应处理

| 接口 | 请求头 | 请求体 | 说明 |
|---|---|---|---|
| `GET /api/v1/membership/plans` | `X-App-Code` | – | 不带 Bearer |
| `POST /api/v1/payment/orders` | `X-App-Code` + `Bearer` | **仅** `planCode`、`clientRequestId` | 测试断言请求体只有这两个键 |
| `GET /api/v1/payment/orders/{orderNo}` | 同上 | – | orderNo 做 URL 编码 |

解析：

- 信封格式 `{code, message, data}`，`code != 0` 或非 2xx 都视为失败。
- `paidAt`、`fulfilledAt`、`pay`、`pay.value`、`durationDays` 允许为空。
- 不认识的 status 映射为 `UNKNOWN`：既不当成已付款，也不当成失败。
- 查询返回的 `orderNo` 与请求不一致时按 `Malformed` 处理，不予采用。

错误映射（`PaymentError`）：

| 情况 | 映射 |
|---|---|
| 40100 / 40101 / 40102 / HTTP 401 | `Unauthorized` |
| 40011 | `ClientRequestConflict` |
| 40012 | `LifetimeAlreadyActive` |
| 40008 | `PlanDisabled` |
| 40401 | `PlanNotFound` |
| 40403 | `OrderNotFound` |
| 50003 | `PaymentConfigMissing` |
| 其他非 2xx | `Http(status, code)` |
| JSON 异常、缺少 data、套餐字段非法 | `Malformed` |
| IO 异常 / 超时 | `Network(timeout)` |

- 可重试的只有：`Network`、`Malformed`、5xx。
- Base URL 规则与登录、会员接口一致：https 总是允许；http 只在 Debug，或与 localIntegration 的 `LOCAL_HTTP_BASE_URL` 完全一致时允许。**Release 不会请求明文 HTTP**；Base URL 为空时直接降级，不发请求。
- 请求对象的 `toString` 不含 Token 和请求体内容；新增代码中没有任何日志输出。

---

## 4. 订单状态机与轮询策略

### 页面阶段

```
Idle ──点击──▶ Creating ──成功──▶ Ordering(PENDING_PAYMENT) ──▶ PAID ──▶ FULFILLED ──▶ 会员同步(Synced / Failed)
  ▲              │                       │
  │          可重试失败 ──▶ CreateFailed(retryKept) ──点击重试（同一 UUID）──▶ Creating
  │          明确失败 ─────────────────▶ Idle + 提示
  └── 账号变更 / 订单不存在 / 返回套餐 ◀──┘
进程恢复：已有 orderNo 的记录 ──▶ Resuming ──首次查询──▶ Ordering
          没有 orderNo 的记录 ──▶ CreateFailed（同一 UUID 可重试）
```

### 轮询（`PaymentPollPolicy`）

- 订单创建成功后**立即查询一次**。
- PENDING_PAYMENT / PAID / UNKNOWN：本轮前 60 秒每 2 秒一次，之后每 10 秒一次。
- 连续查询失败按 2 → 5 → 10 → 30 秒退避。失败**不会改变**已知的订单状态，只显示「暂时无法查询订单，稍后自动重试」。
- FULFILLED：停止轮询，只触发一次会员刷新，只提示一次成功。
- EXPIRED：停止自动轮询，保留手动刷新，并提示「如已完成付款，到账后仍会自动开通会员」。测试覆盖了迟到支付变为 FULFILLED 的情况。
- 页面 `ON_PAUSE`（包括打开收银台）时暂停；`ON_RESUME` 时立即查询并重新计算快速窗口。网络恢复时立即查询。
- 同一时间只有一个轮询任务（手动刷新会替换它）；每次查询都带 epoch，过期的结果会被丢弃。轮询运行在 `viewModelScope`，页面退出即停止。

### 时间

剩余时间按 `elapsedRealtime + (expiresAt − serverTime)` 计算，以服务端时间为锚点，不依赖手机墙钟。

### FULFILLED 后的会员刷新

1. 调用 `MembershipRefreshCoordinator.refreshAfterPurchase()`，不受前台节流和手动点击防抖限制。
2. 由现有链路完成：`/membership/me` → `ServerMembershipProvider` → `EntitlementRepository`。
3. 同时满足以下条件才显示「会员开通成功」：Provider revision 增加、同步状态为 Idle、`ServerMembershipProvider` 对当前账号 + generation 返回 `VerifiedActive`。
4. 否则显示「支付成功，会员状态同步中」并提供重试。
5. 不会自行构造 `VerifiedActive`，也不会改本地到期时间。应用锁、相册会通过现有 `EntitlementRepository` 自动拿到新的权益。

---

## 5. 登录及账号隔离机制

### 下单前的登录检查（基于现有 `AccountSessionRepository`）

| 会话状态 | 处理 |
|---|---|
| Authenticated | 允许下单 |
| Guest | 提示先登录，并打开现有微信登录弹窗 |
| SessionExpired | 提示重新登录，并打开登录弹窗 |
| Authenticating / Validating | 提示账号验证中，不下单 |
| TemporarilyUnavailable | 提示网络或账号验证暂不可用，不下单 |
| LoginFailed / StorageFailed | 提示账号状态异常，并打开登录弹窗 |

没有任何本地伪造登录态的路径。

### 订单归属与隔离

- 订单绑定 `PurchaseOwner(userId, entitlement generation)`。
- 创建订单要求严格的 Authenticated；查询订单也接受 D2-C 中「同一可信账号正在重新校验」的状态，所以回前台的 revalidate 不会中断订单轮询。
- 退出登录、换号、generation 变化都会立即取消轮询和会员同步，并提示「账号已变更，已停止原订单查询」。迟到的响应因 epoch 和归属校验被丢弃，不会更新新账号的界面或权益。
- 建单或查单返回 401 时，只对仍是当前账号的会话调用 `notifyRemoteSessionInvalid`。
- 恢复记录只在 `ownerUserId` 等于当前账号时才会被读出。恢复后必须用当前 Token 重新查询，服务端返回 40403 即丢弃。

### clientRequestId

- 每次新的购买操作生成新的 UUID。
- 因网络 / 超时 / 5xx 失败后重试，复用原 UUID。
- 换套餐视为新的购买，丢弃旧的请求上下文。
- 40011 / 40012 / 40008 / 40401 / 401 会清除上下文。
- 每次请求前，UUID 已经先写入恢复元数据，所以即使进程在请求途中死亡，恢复后也会用同一个 UUID 重试。

### 防重复建单

- 页面层：进入 Creating 后按钮不可用，后续点击直接忽略。
- 仓库层：互斥锁单飞，并发请求返回 `Busy`。
- ViewModel 在旋转 / Activity 重建时保留；进程恢复时根据元数据续查原订单，不会新建订单。

---

## 6. 测试数量、结果及构建情况

`:app:testDebugUnitTest`：**544 个测试，0 失败**（4 个隔离 HTTP 用例需显式开启，跳过）。

| 新增 / 重写套件 | 数量 | 覆盖内容 |
|---|---|---|
| `HttpPaymentApiTest` | 10 | 三种套餐解析、空列表、坏 JSON、请求体只有两个键、Bearer 与脱敏、可空字段、未知 status、错误码映射、401 / 5xx / 超时 / 断网、Release 拒绝 http Base URL、**内嵌真实 HTTP 服务器往返** |
| `PaymentPoliciesTest` | 5 | https 打开收银台、Release 拒绝 TEST / http / 未知 kind / 缺失 / 非法、Debug 识别 TEST、轮询节奏与退避 |
| `PaymentRepositoryTest` | 11 | 元数据持久化、超时重试复用 UUID、新购买换新 UUID、明确错误清除上下文、并发单飞、Guest / Validating 不能下单、建单中途换号丢弃结果、可信重新校验仍可查单、40403 / FULFILLED 清除记录、不暴露其他账号的记录 |
| `VipPurchaseControllerTest` | 25 | 见下表 |
| `VipPurchaseViewModelTest`（重写） | 3 | 初始状态、展示键、路由 |
| `MembershipRefreshCoordinatorTest`（+1） | 1 | `refreshAfterPurchase` 绕过节流 / 防抖，Guest 被拒绝 |

任务要求与 `VipPurchaseControllerTest` 的对应关系：

| 要求 | 用例 |
|---|---|
| 1 / 2 | `plans_displayServerData_defaultYearly`、`plans_failureAndEmpty_blockPurchase_retryRecovers` |
| 3 | `guest_isAskedToLogin_noOrder`、`nonAuthenticatedStates_neverCreate`、`agreementRequired_beforeAnyRequest` |
| 4 / 6 / 13 | `create_doubleTapCreatesOnce_queriesImmediately_urlOpensCashier` |
| 5 | `lifetimeOwned_blocksPurchase_and40012Refreshes` |
| 7 | `createTimeout_retryReusesClientRequestId`、`changingPlanAfterFailure_startsNewPurchase` |
| 8 / 9 | `statusFlow_pendingPaidFulfilled_refreshesMembershipOnce` |
| 10 | `fulfilled_membershipSyncFails_thenRetrySucceeds` |
| 11 | `expired_stopsPolling_manualRefreshSeesLatePayment` |
| 12 / 14 | `testKind_debugShowsTestPayment_neverOpensBrowser`、`releasePolicy_rejectsTestAndHttpLinks` |
| 15 | `pauseStopsPolling_resumeQueriesImmediately`、`pollCadence_fastThenSlow`、`processRestore_*`（3 个） |
| 16 | `logoutDuringQuery_stopsPolling_lateResponseIgnored`、`switchAccount_stopsOldOrder_revalidationKeepsIt`、`queryUnauthorized_stopsAndAsksLogin` |
| 17 | `queryFailures_keepStatus_backOff_networkRestoreQueriesNow`、`serverTimeAnchorsDeadline_notDeviceWallClock` |
| 错误码 | `planErrors_reloadPlans_conflictResets` |

### 构建

`assembleDebug`、`assembleLocalIntegration`、`assembleRelease`、`assembleDebugAndroidTest` 均**成功**。

| 变体 | `API_BASE_URL` | `PAYMENT_TEST_KIND_ALLOWED` | `TRIAL_DEVICE_UPLOAD_ENABLED` |
|---|---|---|---|
| Debug | `http://10.0.2.2:8080` | true | false |
| localIntegration | `http://192.168.0.107:8080` | true | false |
| Release | `""`（未改） | **false** | false |

---

## 7. 现有会员和权益回归情况

- 会员、试用、会话、应用锁、相册等全部既有 JVM 套件均通过，包括 D2-C 的 `SessionRevalidationEntitlementTest`。
- 对 `MembershipRefreshCoordinator` 只新增了一个入口，原有原因的节流和防抖行为不变。
- `EntitlementRepository`、`ServerMembershipProvider`、试用、应用锁、相册代码均未修改。
- `VipPlan` 枚举保留了 `Monthly / Annual / Lifetime`，设置页会员展示不受影响；`AccountUiStateTest` 仍然通过。
- 不存在本地开通 VIP 的路径：唯一的 VIP 来源仍是服务端 `/me` 经 Provider 确认的结果。

---

## 8. 尚未解决的问题及下一阶段联调准备事项

### 已知限制

1. **没有使用 Custom Tabs。**工程里没有 `androidx.browser`，为不引入新依赖，改用系统浏览器 `ACTION_VIEW`。如需 Custom Tabs，需要确认后再加依赖。
2. **没有收银台域名白名单。**目前只校验「绝对 https URL、有 host、无 userinfo」。悦森正式域名确定后可以加白名单。
3. **会员刷新可能合并到旧请求。**如果 FULFILLED 时恰好已有一个同账号的 `/me` 请求在进行中，Provider 会去重并复用它的结果，可能拿到开通前的状态。这种情况下界面会显示「会员状态同步中」，用户点重试即可恢复，不会误授或误撤 VIP。
4. **旧资源未删除。**原来的支付方式图标和字符串（`ic_payment_wechat/alipay`、`vip_payment_*`）已不再引用，但为了不扩大改动范围没有删除。
5. **localIntegration 仍指向 8080。**8081 隔离环境的地址需要在 I3.5.4-B 通过构建参数指定，本阶段未改。
6. **未做设备验证。**本阶段没有在模拟器或真机上运行，也没有做 UI 截图验收。

### I3.5.4-B 准备事项

- 用 `-PISOLATED_MEMBERSHIP_TEST=true`（或 `-PDEBUG_API_BASE_URL=http://10.0.2.2:8081`）构建 Debug，使登录、会员、支付都指向 8081。
- 复用 D2-A 的 `IsolatedTestSessionBridge`，注入 `.local/i343d1/payment-accounts.local.json` 中的测试账号（注意 12 小时有效期，Token 不入库）。
- 待验证的闭环：
  1. 下单得到 TEST 订单，页面显示测试提示；
  2. PC 端通过隔离控制接口模拟支付；
  3. 订单依次变为 PAID 和 FULFILLED；
  4. `/me` 刷新后显示 VIP，应用锁和相册的权益随之变化。
- 补测：
  - 前后台切换和进程恢复（Host 端 force-stop）；
  - 退出登录、切换账号；
  - EXPIRED 后的迟到支付；
  - 永久会员 40012；
  - 断网后恢复。
- 只使用 `adb install -r` + `am instrument`；不卸载、不清数据、不用 connectedDebugAndroidTest。

---

**本任务停止，等待审核。不进入 I3.5.4-B。**
