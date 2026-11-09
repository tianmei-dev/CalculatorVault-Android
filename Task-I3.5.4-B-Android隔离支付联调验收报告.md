# Task I3.5.4-B Android 隔离支付联调验收报告

日期：2026-11-09。在专用模拟器上通过真实 HTTP 连接 8081 隔离服务完成联调。未连接真实悦森、未付款、未修改服务端代码或数据库数据、未进入真实支付联调。

## 结论

B01–B10 主链路已在真实环境跑通（真实 UI、真实 Bearer、真实 HTTP）：

> 选择套餐 → 创建订单 → TEST 等待 → PC 模拟支付 → 服务端发放 → Android 自动刷新 → 应用锁 / 相册获得权益

- 发现并修复 1 个 Android 缺陷：**B05 会员刷新竞态**。
- 另记录 2 个低优先级体验问题和 1 个既有风险（见 §6），均未修改。
- 测试环境下没有出现重复订单，也没有重复发放会员。
- **B10 的 Release 运行时验证未在设备上执行**（BLOCKED）：Release 包未签名，且与已安装 App 签名不同，又禁止卸载。改为静态扫描 + JVM 验证。

---

## 1. 测试设备、构建版本和服务端环境

| 项 | 内容 |
|---|---|
| 设备 | `CalculatorVault_Test` / `emulator-5554`，Android 15（测试内断言 `ro.kernel.qemu=1` 和 AVD 名称） |
| 同时连接的物理手机 | `ZY226GPVRG`，**全程未操作**；所有 adb 命令都固定使用 `-s emulator-5554` |
| App 数据 | 仅 `adb install -r`；未卸载、未 `pm clear`、未使用 connectedDebugAndroidTest |
| Android 版本 | HEAD `96d042c` + 工作区改动（I3.5.4-A 及本任务 B05 修复） |
| 构建参数 | `-PISOLATED_MEMBERSHIP_TEST=true` |
| 实际生成的 BuildConfig | `API_BASE_URL` = `LOCAL_HTTP_BASE_URL` = `TRIAL_API_BASE_URL` = `http://10.0.2.2:8081`；`PAYMENT_TEST_KIND_ALLOWED=true`；`TEST_SESSION_INSTALL_ENABLED=true`；`TRIAL_DEVICE_UPLOAD_ENABLED=false` |
| 运行时确认 | 每个用例 `setUp` 都断言 `BuildConfig.API_BASE_URL == http://10.0.2.2:8081` |
| 服务端 | AuroraAppServer HEAD `62432f6`（工作区有未提交改动，本任务未修改） |
| 服务端运行配置 | 进程参数 `--spring.profiles.active=s32it`；日志显示 `aurora_app_test@127.0.0.1:3307`；`aurora.payment.provider=FAKE`、`environment=TEST`；订单返回 `pay.kind=TEST`（无 URL） |
| Fake 控制入口 | `/internal/fixture/fake-payment/...`：仅限 loopback，且需要每次启动生成的 token（`.local/s32it/payment-control.token`），只由 PC 端调用 |
| 8080 / 开发库 | 未访问、未修改 |

测试账号均由服务端 `scripts\i343d1-fixture.bat new-pay-accounts` 创建，没有执行 seed / clean，也没有删除任何账号。

| 批次 | userId | 用途 |
|---|---|---|
| 20261109124634 | 1066–1071 | B01–B09 主要场景 |
| 20261109131043 | 1072–1073 | B05、短有效期过期测试 |

- 旧批次 20261109120732 中，1063 和 1064 已不是 NONE，因此没有使用。
- 生成新批次会覆盖 token 文件，所以每次生成前都先把原文件备份到 `.local/i343d1/payment-accounts.batch-*.local.json`（git 忽略）。
- Token 只通过 `adb push` 放到模拟器 `/data/local/tmp`，测试结束后**已删除**。Token 未进入源码、日志、报告或 Git。
- 证据目录逐一比对了全部 12 个账号 / 控制 token，**命中 0**。

会话注入复用 D2-A 的 Debug-only `IsolatedTestSessionBridge`，没有新增 Release 测试入口。

---

## 2. B01–B10 执行结果

分级：**PASS(R)** 真实 8081 + 模拟器端到端；PASS(J) JVM；PASS(S) 仅服务端验证；BLOCKED 未在设备执行。

| 编号 | 场景 | 结果 | 关键证据 |
|---|---|---|---|
| **B01** | 套餐展示 | **PASS(R)** | 浅色、深色下月卡 ¥5.90 / 30天、年卡 ¥19.90 / 365天、永久会员 ¥29.90 / 永久有效，均与服务端 `/plans` 一致；没有微信/支付宝选择，显示悦森收银台说明。断网时显示「套餐加载失败」，按钮为不可用的「立即开通」且无价格；恢复网络后点「重新加载」，按钮变为「立即开通 ¥19.90」 |
| **B02** | 创建订单（月卡，1066） | **PASS(R)** | 连点 5 次只建 1 单（库中 orders=1、requests=1）；状态 PENDING_PAYMENT；面板显示 `套餐：月卡 \| 金额：¥5.90 \| 订单号：…B634KTTAMG \| 待支付 \| 剩余支付时间 29:59 \| 当前为测试支付环境…`；没有离开 App、没有打开浏览器 |
| **B03** | PC 模拟支付 | **PASS(R)**，附注 | 服务端事件 `->PENDING_PAYMENT, PENDING_PAYMENT>PAID, PAID>FULFILLED`；Android 自动从「待支付」变为「会员已开通」→「会员开通成功」；本地 VIP 为 MONTHLY，到期 `2026-12-09T12:58:36.472Z`，与服务端 `/me` **完全一致**；发了 4 次 notify，最终只有 1 条 grant |
| **B04** | 权益联动 | **PASS(R)** | 应用锁 Denied → Allowed，监控服务运行且被锁应用弹出遮罩；相册导入 Denied → Allowed（合成 JPEG 导入 1 张后已删除）；设置页显示「月卡会员 / 有效期至 2026-12-09」；权益判断逻辑未改 |
| **B05** | 会员刷新竞态 | **PASS（J 复现 + 修复；R 回归）** | 见 §6 F1。设备上用 146 次并发 `/me` 施压，8.9 秒自动完成同步，没有出现手动重试按钮 |
| **B06** | 重复操作与幂等 | **PASS(R)**（建单超时重试为 PASS(J+S)） | 连点只建 1 单；PC 用相同 clientRequestId 重放建单，返回**同一 orderNo**；多次手动刷新、多次回前台查询；重复 notify 4 次只发放 1 条 grant。建单请求超时或断网后重试同一 UUID：设备上未执行，由 JVM 测试和服务端重放覆盖 |
| **B07** | 生命周期（年卡，1067） | **PASS(R)** | 旋转（Activity 重建）后面板和订单号不变；回到桌面再返回、同账号 revalidate 后订单流程都还在；PC 执行 `am force-stop`（确认进程已不存在）后，新进程（pid 5773）凭恢复元数据续查原单，**没有新建订单**；支付后 YEARLY 到期 `2027-11-09T13:01:22.764Z` 与服务端一致 |
| **B08** | 账号隔离（1068 → 1069） | **PASS(R)**（在途迟到响应为 PASS(J)） | 等待中切换账号，旧订单面板立即消失；随后 PC 给**旧账号**订单付款，服务端 1068 变为 ACTIVE，而新账号 1069 仍是 `source=None`、没有成功提示；1069 自己下单后在等待中退出登录，面板消失，服务端会话已撤销（再请求返回 401）。同账号 revalidate 见 B07 |
| **B09** | 异常情况 | **PASS(R)**（会员刷新失败恢复为 PASS(J)） | 断网：16.2 秒后出现「暂时无法查询订单」，状态全程保持「待支付」，没有误判为失败或已支付；恢复网络 2.5 秒后正常。401：PC 撤销 1070 会话后，下一次查询得到 401 → SessionExpired，面板消失，无 VIP。过期与迟到支付见 §3 |
| **B10** | Release 安全 | **PASS（静态 + J）/ 运行时 BLOCKED** | 见 §5 |

B03 附注：Android **没有**观察到 PAID 中间态。服务端在同一次 notify 处理里同步完成 PAID → FULFILLED，PAID 只在服务端事件中出现。这不是 Android 的问题。

---

## 3. 关键 HTTP 请求和响应（脱敏）

### 建单与查单（Android → 8081，PC 用同一账号复核）

```
POST /api/v1/payment/orders   X-App-Code: calculator_vault   Authorization: Bearer <redacted>
body: {"planCode":"MONTHLY","clientRequestId":"<UUID>"}        ← 只有这两个字段
→ 200 {"code":0,"data":{"orderNo":"CV202611091257535YB634KTTAMG","planCode":"MONTHLY","amountFen":590,
       "amountDisplay":"5.90","currency":"CNY","status":"PENDING_PAYMENT",
       "expiresAt":"2026-11-09T13:27:53.363Z","serverTime":"2026-11-09T12:58:10.46Z","pay":{"kind":"TEST"}}}

同 clientRequestId 重放 → 200，orderNo 相同（幂等）
```

请求体只含两个字段，在设备侧的依据有两条：服务端请求表 `payment_order_requests` 只记录 plan 和 clientRequestId；Android 请求体由 JVM 测试和内嵌真实 HTTP 服务器测试断言。

### Fake 支付（仅 PC loopback）

| 操作 | 结果 |
|---|---|
| 首次 `POST …/notify`，未先设置 PAID | `ack=success`，但订单**仍是 PENDING_PAYMENT** |
| `POST …/behavior?mode=PAID` 后再 `POST …/notify` | 订单变为 FULFILLED |

首次 notify 没有生效的原因：服务端会用 provider 查询二次确认每一条 notify，Fake 默认返回 UNPAID。这是服务端正确的防伪逻辑，不是缺陷。

### 订单过期（真实服务端判定）

| 订单 | 方式 | 结果 |
|---|---|---|
| 1071 `…G5EHFWUCHZUO` | 自然 30 分钟过期 | 21:43:02 GET 返回 `status:"EXPIRED"`，不再带 `pay`；事件 `PENDING_PAYMENT>EXPIRED`；迟到支付后变为 `EXPIRED>PAID>FULFILLED`，1 条 grant |
| 1073 `…LZTXBKO4WM7N` | 经批准临时 2 分钟有效期 | 库中 `expires_at − created_at = 119 s`；t+125 s GET 返回 `EXPIRED`；事件 `PENDING_PAYMENT>EXPIRED` |

### `/membership/me`（发放后）

| 账号 | 结果 |
|---|---|
| 1066 | ACTIVE MONTHLY，`2026-12-09T12:58:36.472Z` |
| 1067 | ACTIVE YEARLY，`2027-11-09T13:01:22.764Z` |
| 1072 | ACTIVE MONTHLY，`2026-12-09T13:11:30.537Z` |
| 1071（迟到支付） | ACTIVE MONTHLY |
| 1069 | 发放前 NONE；退出登录后 401 |

---

## 4. 订单与会员状态变化证据

数据来自 `aurora_app_test` 的只读查询。

| 用户 | orders | requests | grants | 服务端事件 |
|---|---|---|---|---|
| 1066 | 1 FULFILLED | 1 | 1 MONTHLY | ->PENDING, PENDING>PAID, PAID>FULFILLED |
| 1067 | 1 FULFILLED | 1 | 1 YEARLY | 同上 |
| 1068 | 1 FULFILLED | 1 | 1 MONTHLY | 同上（切换账号后才付款） |
| 1069 | 1 EXPIRED | 1 | 0 | ->PENDING, PENDING>EXPIRED |
| 1070 | 1 PENDING→EXPIRED | 1 | 0 | – |
| 1071 | 1 FULFILLED | 1 | 1 MONTHLY | ->PENDING, PENDING>EXPIRED, EXPIRED>PAID, PAID>FULFILLED |
| 1072 | 1 FULFILLED | 1 | 1 MONTHLY | ->PENDING, PENDING>PAID, PAID>FULFILLED |
| 1073 | 1 EXPIRED + 1 PENDING | 2 | 0 | PENDING 那笔是恢复 30 分钟有效期后由 PC 创建的校验单，将自然过期 |

### 过期后停止自动轮询

先在原订单上尝试按 UID 统计流量，结果无法下结论：流量里混有第三方 SDK 对外部 443 地址的连接，而 8081 连接又是 keep-alive 复用。

于是新增了 androidTest 专用的**查询计数探针**：通过反射包装 App 进程内 `PaymentRepository` 的 API，只计数、原样透传，不改生产代码。在 2 分钟订单上实测：

| 阶段 | 订单查询次数 |
|---|---|
| 待支付的前 20 秒（2 秒间隔） | 9 |
| 页面在 132.6 秒时**自动**显示过期（没有任何点击） | – |
| 过期后静置 45 秒 | **0** |
| 手动点一次刷新 | 恰好 1 |

---

## 5. 会员权益联动与 Release 安全

### 权益联动

- 设备试用为 EXPIRED，所以联动结果不受试用干扰。
- VIP 生效前：`APP_LOCK_PROTECTION=Denied`、`PRIVATE_ALBUM_IMPORT=Denied`。
- VIP 生效后：两者均为 Allowed；应用锁监控服务启动，打开被锁应用后出现遮罩；相册成功导入 1 张合成 JPEG。
- 权益仍然只来自 `/me` → `ServerMembershipProvider` → `EntitlementRepository`，判定代码未改。

### B10 Release

| 检查 | 结果 |
|---|---|
| Release BuildConfig | `API_BASE_URL=""`（未改）；`PAYMENT_TEST_KIND_ALLOWED=false`；`TEST_SESSION_INSTALL_ENABLED=false` |
| Release DEX 中不存在的内容 | `IsolatedTestSessionAccess/Bridge`、`EntitlementInstrumentationBridge`、`/internal/fixture`、`X-Fixture-Control-Token`、`fake-payment`、`cv_i354b`、`10.0.2.2:8081`、`192.168.0.107:8081`，以及全部 12 个测试 / 控制 token |
| 拒绝 TEST、http 链接、未知 kind | PASS(J)：`PaymentPoliciesTest`、`VipPurchaseControllerTest.releasePolicy_rejectsTestAndHttpLinks` |
| 拒绝明文 HTTP 接口 | PASS(J)：`HttpPaymentApiTest.releaseRules_rejectCleartextBaseUrl_withoutSending` |
| 本地直接开通 VIP | 不存在这样的路径 |
| Release 运行时 | **BLOCKED**：未签名，且与已安装 App 签名不同，又禁止卸载 |
| 既有风险 | `FakeMembershipProvider` 类在 `main` 源码集里，因此进入了 Release DEX，但生产 DI 没有使用它（见 F5） |

---

## 6. 发现的问题、原因和修复

| 编号 | 归属 | 问题 | 处理 |
|---|---|---|---|
| **F1** | Android | **B05 竞态**：FULFILLED 后的会员刷新会被 Provider 去重并合并到一个更早发出的同账号 `/me` 请求上，而那个请求的服务端读取发生在发放之前。结果是拿不到 VIP，页面停在「同步中」，只能等用户手动重试 | **已修复**：`VipPurchaseController.startMembershipSync` 在每次刷新结束后，如果仍没有服务端确认的 VIP，就主动再取一次，最多 3 次、间隔 1.5 / 3 秒；仍受账号 / generation 保护，客户端不会自行赋予 VIP。新增 `PurchaseMembershipRaceTest`，用真实 Coordinator + Provider + Entitlement 确定性复现；`VipPurchaseControllerTest` +1，另有 1 个用例按新行为更新 |
| F2 | 测试流程 | 首次 notify 被服务端以 provider 二次查询拒绝 | 不是缺陷。之后的流程都先设置 `behavior=PAID` 再 notify |
| F3 | Android（低，未改） | 断网时「查询失败」提示要等连接 / 读取超时（10 s / 15 s）后才出现，实测 16 s；手动刷新会取消正在进行的查询并重新计时，连续点刷新会让提示继续推迟 | 建议后续把订单查询超时调短 |
| F4 | Android（低，未改） | EXPIRED 订单的恢复元数据会一直保留（为了迟到支付），用户点「返回套餐」后再进页面，又会回到这笔过期订单，直到下一次购买或订单变为 FULFILLED | 建议提供「放弃此订单」，或在迟到支付窗口结束后清理 |
| F5 | 既有（未改） | `FakeMembershipProvider` 位于 `main`，进入了 Release DEX，生产 DI 未使用 | 建议移到 test 源码集，属跨模块调整，本任务不做 |
| F6 | 测试环境 | App 设置了 FLAG_SECURE，截图全黑 | 改为保存可访问性层级 XML 作为证据 |

### 关于 8081 的重启

按你的批准，为了做 2 分钟过期测试重启了两次 8081。

1. 我第一次用了普通的 `bootRun`，它会解析到开发库 `aurora_app`。s32it 隔离守卫**在连接数据库之前就拒绝了启动**，开发库没有受到影响，但 8081 因此中断了约 40 秒。
2. 随后改用服务端提供的 `bootRun-s32it.bat -Ps32itExtraArgs=--aurora.payment.order-ttl=2m`（fixture classpath）启动。
3. 测试结束后用原脚本、不带额外参数重启：进程参数恢复为 `--spring.profiles.active=s32it`，并用一笔校验单确认 **TTL = 1799 s（30 分钟）**。

两次重启都只停了 8081 的进程（重启前确认库中没有处于 PAID 发放中的订单），8080 全程未动。8081 现在是从我这边的会话启动的（pid 7448），原来由谁启动的已无法恢复。

### 本任务修改的文件

| 类型 | 文件 |
|---|---|
| 生产代码 | `VipPurchaseController.kt`（F1 有界重取） |
| 测试 | `VipPurchaseControllerTest.kt`、新增 `PurchaseMembershipRaceTest.kt` |
| androidTest | 新增 `feature/payment/IsolatedPaymentE2ETest.kt`、`PaymentQueryProbe.kt` |
| 证据与脚本 | `evidence/task-i3.5.4-b/`（`fixture.ps1`、各场景的 PC 操作日志、instrument / logcat 输出、UI 层级 XML） |

服务端代码、配置和数据库均未修改。

---

## 7. 回归结果

| 项 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **546 个测试，0 失败**（4 个隔离 HTTP 用例需显式开启，跳过） |
| 本任务新增 / 调整的 JVM 用例 | `PurchaseMembershipRaceTest`、`fulfilled_staleFirstRefresh_autoRefetchSyncs`，以及更新后的 `fulfilled_membershipSyncFails_thenRetrySucceeds` |
| Instrumentation（全部手动 `am instrument`） | 按场景执行的 `IsolatedPaymentE2ETest` 各用例最终均为 OK；`zz_cleanup` OK |
| 构建 | Debug（隔离参数）、androidTest、localIntegration、Release 均成功；默认 Debug 由单元测试编译覆盖 |
| 环境复原 | 模拟器飞行模式关、浅色、自动旋转开；8081 恢复 30 分钟有效期 |

Instrumentation 过程中出现过的失败都是测试代码或测试流程问题，已定位修正后重跑通过：导航等待不够、断言时机、把未确认的 notify 当成支付、计数方式。

---

## 8. 尚未覆盖的场景和原因

| 场景 | 原因 |
|---|---|
| 设备上的建单请求超时 / 断网后重试同一 UUID | 只在 JVM 中验证，并由服务端同 clientRequestId 重放确认幂等 |
| 换号时正在途中的旧账号 HTTP 响应迟到 | 设备上无法可靠制造在途延迟；JVM 覆盖。设备上已验证旧账号订单迟到付款不影响新账号 |
| FULFILLED 后 `/me` 失败再恢复 | 设备上无法单独让 `/me` 失败；JVM 覆盖 |
| 服务端响应慢导致的查询超时 | 没有慢响应注入手段；断网场景覆盖了连接超时这条路径 |
| 设备上观察到 PAID 中间态 | 服务端在 notify 中同步发放，PAID 只在服务端事件中出现 |
| Release 安装运行 | 签名限制且禁止卸载（BLOCKED） |
| URL 类型（悦森真实收银台） | 8081 只返回 TEST；URL 分支只有 JVM 验证 |
| 深色主题的像素级视觉 | FLAG_SECURE 阻止截图；只验证了文字层级与渲染 |
| 真实微信登录 | 使用的是隔离测试 Session |

---

## 9. 真实悦森支付联调前的剩余风险

1. **URL 分支从未在设备上跑过**：系统浏览器打开收银台、从浏览器返回后 `ON_RESUME` 查询、悦森域名白名单（目前只校验 https 和 host）、各厂商浏览器的回跳行为。
2. **Release 基础配置未就绪**：`RELEASE_API_BASE_URL` 为空，需要配置正式 HTTPS 地址并验证证书；Release 包从未在设备上运行过。
3. **真实网关时序**：真实 notify 和回跳的先后、网关延迟下 PAID 停留的时间，以及服务端对账任务与客户端轮询的配合，在 Fake 环境中没有体现。
4. **F3 / F4 体验问题**：断网提示偏慢；过期订单会反复出现在页面上。
5. **F5**：`FakeMembershipProvider` 仍在 Release DEX 中（未使用）。
6. **账号路径**：真实微信登录 → 购买的完整路径未联调；物理机上的后台限制和省电策略未验证。

---

**本任务停止，等待审核。不进入真实悦森支付联调。**
