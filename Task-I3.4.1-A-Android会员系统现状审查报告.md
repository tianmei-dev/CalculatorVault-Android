# Task I3.4.1-A Android 会员系统现状审查报告

日期：2026-11-04。**只读审查**，未改代码 / Gradle / 试用记录，未跑真实微信授权或支付，未进 I3.4.2。

**结论摘要：** 微信 OpenSDK → code → `HttpAuthApi` 登录会话在客户端已闭环，Token 经 Keystore 落盘；权益层 `VIP > Trial > Free`、登出/换号 generation 防污染已就绪。**生产固定 `DefaultMembershipProvider`（永不授 VIP）**；无会员 HTTP、无 Retrofit；VIP 购买 UI 仅展示，下单恒为 `PaymentUnavailable`。**不得将 `FakeMembershipProvider` / androidTest Bridge 视为真实 VIP 服务。**

状态图例：

| 标记 | 含义 |
|---|---|
| **可运行** | 已有代码且客户端逻辑可执行 |
| **占位** | 接口/UI/默认实现存在，无真实履约 |
| **未实现** | 能力缺失 |
| **待服务端** | 需服务端契约或环境确认 |

---

## 1. 当前代码结构与关键路径

```
app/src/main/java/com/aurora/calculatorvault/
├── app/CalculatorVaultApp.kt              # 手工 DI（无 Hilt/Koin）
├── feature/login/
│   ├── data/AndroidWeChatSdkBridge.kt
│   ├── data/SharedPreferencesWeChatAuthorizationStore.kt
│   ├── domain/WeChatAuthorizationCoordinator.kt
│   ├── domain/WeChatServerLoginEntry.kt
│   ├── presentation/LoginPromptViewModel.kt, WeChatLoginDialog.kt
│   └── session/
│       ├── HttpAuthApi.kt                 # HttpURLConnection，非 Retrofit
│       ├── AccountSessionRepository.kt
│       └── KeystoreTokenStore.kt
├── wxapi/WXEntryActivity.kt
├── feature/membership/
│   ├── domain/VipPurchaseModels.kt        # 本地价目目录
│   └── presentation/VipPurchaseViewModel.kt, VipPurchaseScreen.kt
├── feature/entitlement/domain/
│   ├── MembershipProvider.kt              # Default + Fake（Fake 仅测注入）
│   ├── EntitlementRepository.kt
│   ├── EntitlementModels.kt
│   └── EntitlementDisplayMapper.kt
└── feature/trial/
    ├── DeviceTrialRepository.kt
    └── EncryptedTrialStore / AndroidTrial*  # 试用与账号 Token 隔离

app/src/androidTest/.../entitlement/EntitlementInstrumentationBridge.kt  # 仅测试 Fake 注入
```

配置：`app/build.gradle.kts`（`API_BASE_URL` / `TRIAL_API_BASE_URL` / `WECHAT_APP_ID` / `TRIAL_DEVICE_UPLOAD_ENABLED`）。

---

## 2. 已完成能力（可运行）

### 2.1 微信登录与账号会话

| 能力 | 说明 |
|---|---|
| OpenSDK 授权 | `AndroidWeChatSdkBridge` + `WeChatAuthorizationCoordinator`；`WXEntryActivity` 回调 |
| code 交换客户端 | `POST /api/v1/auth/wechat/login`（`X-App-Code: calculator_vault`，body `code`） |
| me / logout | `GET /api/v1/auth/me`、`POST /api/v1/auth/logout`（Bearer） |
| Token 存储 | `KeystoreTokenStore`：Keystore AES-GCM + `noBackupFilesDir`；logout tombstone |
| 会话状态机 | Guest / Authenticating / Validating / Authenticated / SessionExpired / LoginFailed / … |
| 冷启动恢复 | `accountSession.restore()`；前台 `revalidate()` |
| 登出 / 重登 | Settings 登出；`acceptAuthorization` 已有凭证时先 logout；换号递增 generation |
| Debug 默认可联调 | `API_BASE_URL` 默认 `http://10.0.2.2:8080`（模拟器） |
| localIntegration | 固定 LAN `8080` 登录 + `8081` 试用 |

**说明：** 「已取得微信授权 code」≠「已登录」；以 `AccountSession.Authenticated` 为准。AppId 由 `gradle.properties` / 环境变量注入（仓库侧已有配置位）。

### 2.2 设备试用与权益

| 能力 | 说明 |
|---|---|
| 本地 72h 试用 | `DeviceTrialRepository.initialize` / `refresh` / ticker |
| 试用加密存储 | 独立 `EncryptedTrialStore`，**登出不清试用** |
| 试用上传 API 客户端 | `POST /api/v1/trial/device/activate`（受 `TRIAL_DEVICE_UPLOAD_ENABLED` 门控） |
| 权益合成 | `EntitlementRepository`：`VIP > DeviceTrial > None` |
| ActionPolicy | 付费能力前 `deviceTrial.refresh()`；`APP_LOCK_PROTECTION` / `PRIVATE_ALBUM_IMPORT` 等需权益 |
| 登出回落试用 | 无 Authenticated → 不授 VIP；有效试用仍可授权付费能力 |
| 陈旧 claim 防护 | `accountGeneration` + `VerifiedActive.generation` 双校验；会话侧 generation 丢弃过期异步登录 |
| 应用锁 / 私密相册门禁 | 已接 live `ActionPolicy`（I3.3.x） |

### 2.3 VIP 购买展示

| 能力 | 说明 |
|---|---|
| 套餐 UI | 月/年/终身本地价目（分）；支付方式微信/支付宝选择 |
| 入口来源 | `VipPurchaseSource`：Settings / AppLock / PrivateAlbum / TrialExpired |
| 协议勾选 / 防连点 | ViewModel 本地状态 |

### 2.4 安全基线（相对会员）

- 生产不因登录成功授 VIP。
- `Unavailable` / `Unknown` / `NotEntitled` **均不授 VIP**（fail-closed，无离线宽限）。
- `stateOverride` / `actionDecisionOverride` **仅 androidTest Bridge 赋值**。

---

## 3. 尚未实现 / 仅占位的能力

| 能力 | 分类 | 说明 |
|---|---|---|
| 真实会员查询/刷新 | **未实现** + **待服务端** | 无 `/membership` 等 API；生产 `DefaultMembershipProvider` 恒 `NotEntitled` |
| 登录响应携带会员 | **未实现** | `LoginResult` 仅 `SessionCredential` + `ServerAccount(userId)` |
| VIP 本地缓存 / 离线宽限 | **未实现**（设计上 fail-closed） | `Unavailable` 不授 VIP |
| 异步 MembershipProvider | **占位语义** | 有 `Unknown`，但无加载状态机/协程 refresh |
| 下单 / 支付 / 履约 | **占位** | `requestPurchase()` → 恒 `PaymentUnavailable` |
| 服务端商品目录 | **未实现** | `VipPlan` 为本地硬编码价目 |
| 展示真实套餐类型 | **占位** | `EntitlementDisplayMapper` 对 VIP 硬编码展示 `VipPlan.Monthly` |
| Release 默认可登录 | **未实现（配置）** | `RELEASE_API_BASE_URL` 默认空 → 无法发请求 |
| Retrofit / OkHttp | **未实现** | 全项目 `HttpURLConnection` |
| 鉴权 Interceptor 层 | **占位级内联** | Header 写在 `HttpAuthApi.request` 内 |
| FakeMembershipProvider 作生产 VIP | **禁止** | 类在 main，**仅测试注入**；**≠ 真实 VIP 服务** |

---

## 4. 当前数据流与接口调用流程

### 4.1 微信登录（可运行客户端）

```
LoginPrompt / 业务入口
  → WeChatAuthorizationCoordinator.start()
  → OpenSDK SendAuth
  → WXEntryActivity.onResp
  → Coordinator.onAuthorizationResponse(code, state)
  → AccountSessionRepository.acceptAuthorization(WeChatServerLoginRequest)
  → HttpAuthApi.login(code)
       POST {API_BASE_URL}/api/v1/auth/wechat/login
       Header: X-App-Code: calculator_vault
       Body: {"code":"..."}
  → 解析 accessToken / expiresAt / userId
  → KeystoreTokenStore.write
  → AccountSession.Authenticated(ServerAccount(userId))
  → EntitlementRepository.combine 重算
       membershipProvider.membershipFor → Default → NotEntitled
       → 若试用有效则 DeviceTrial，否则 None
```

### 4.2 会员权益（当前生产）

```
deviceTrial.state ─┐
accountSession ──┼→ EntitlementRepository.computeState
membershipProvider ─┘     resolveVip → 恒空（Default）
                          resolveTrialGrant → 可能 DeviceTrial
                          source = VIP | Trial | None
ActionPolicy.evaluate(capability)
  → 应用锁 / 私密相册导入等
```

### 4.3 VIP 购买（占位）

```
VipPurchaseScreen → VipPurchaseViewModel.requestPurchase()
  → 未勾选协议: AgreementRequired
  → 已勾选: PaymentUnavailable   // 无订单、无支付 SDK、不写 Membership
```

### 4.4 试用上传（可运行客户端，默认关）

```
DeviceTrialRepository.sync()
  → if TRIAL_DEVICE_UPLOAD_ENABLED && 隐私同意
  → POST {TRIAL_API_BASE_URL}/api/v1/trial/device/activate
```

---

## 5. 现有数据模型与字段

### 5.1 登录 / 会话

| 类型 | 关键字段 |
|---|---|
| `SessionCredential` | `token: String`, `expiresAt: Instant` |
| `ServerAccount` | `userId: Long`（无昵称、无会员字段） |
| `LoginResult` | `credential`, `account` |
| `AccountSession` | Guest / Authenticating / Validating / Authenticated / TemporarilyUnavailable / SessionExpired / LoginFailed / StorageFailed |

登录 JSON（客户端解析）：`accessToken`、`tokenType=Bearer`、`expiresAt`、`userId`、`appCode`。

### 5.2 会员占位

| 类型 | 字段 |
|---|---|
| `MembershipResult.VerifiedActive` | `accountId`, `expiresAtEpochMillis?`（null=永久）, `generation` |
| `MembershipResult` 其它 | `NotEntitled` / `Unavailable` / `Unknown` |
| `MembershipProvider` | `membershipFor(accountId, generation): MembershipResult`（**同步**） |

### 5.3 权益

| 类型 | 字段 |
|---|---|
| `EntitlementSource.Vip` | `accountId`, `expiresAtEpochMillis?`, `permanent` |
| `EntitlementSource.DeviceTrial` | `status`, `remainingMillis` |
| `EntitlementState` | `source`, trial 展示字段, `accountId`, `accountGeneration`, VIP 展示字段, `evaluatedAtElapsedMillis` |
| `EntitlementCapability` | 含 `APP_LOCK_PROTECTION`, `PRIVATE_ALBUM_IMPORT`, 免费管理类能力等 |

### 5.4 VIP 购买本地目录

| `VipPlan` | priceFen | durationDays |
|---|---:|---:|
| Monthly | 590 | 30 |
| Annual | 1990 | 365 |
| Lifetime | 2990 | null |

另有：`VipPaymentMethod`、`VipPurchaseSource`、`VipPurchaseSelection`、`VipPurchaseState`。

### 5.5 试用

| 类型 | 要点 |
|---|---|
| `DeviceTrialState` | `status`, `remainingMillis` |
| 持久化 TrialRecord | firstOpened / remaining / wall / elapsed / boot / expired / server* / verified / uncertain |

---

## 6. 需要服务端提供的接口及字段（建议草案）

以下为**接入真实会员前需确认**的契约（客户端当前**均不存在**，除已列出的 auth/trial）。

### 6.1 已有且可复用

| 接口 | 用途 | 客户端状态 |
|---|---|---|
| `POST /api/v1/auth/wechat/login` | code→token+userId | **可运行** |
| `GET /api/v1/auth/me` | 会话校验 | **可运行** |
| `POST /api/v1/auth/logout` | 登出 | **可运行** |
| `POST /api/v1/trial/device/activate` | 试用同步 | **可运行（开关）** |

### 6.2 待服务端确认的会员能力

| 建议接口 | 用途 | 建议响应字段（待确认） |
|---|---|---|
| `GET /api/v1/membership/me`（或等价） | 当前账号会员状态 | `entitled: boolean`；`plan`（month/year/lifetime/…）；`expiresAt`（ISO8601，永久可 null）；`productId`；`updatedAt` |
| （可选）登录/me 嵌入会员摘要 | 减少往返 | 同上子集；须与独立查询一致 |
| `POST /api/v1/orders` 或支付预下单 | 购买 | `orderId`、`plan`、`amountFen`、`payChannel`、预支付参数 |
| 支付结果通知 / 客户端查单 | 履约 | `orderStatus`、`membership` 最新快照 |
| 商品目录（可选） | 替换本地 `VipPlan` | `sku`、`priceFen`、`durationDays`、上下架 |

**服务端规则建议（与现有客户端语义对齐）：**

1. 会员必须绑定 `userId`；换号不得继承。
2. 客户端以服务端确认结果为唯一 VIP 真源；登录成功本身不授 VIP。
3. 明确：断网 / 5xx 时客户端将走 `Unavailable` → **不授 VIP**（若需离线宽限须单独设计签名缓存，当前无）。
4. `generation` 为客户端会话代数，服务端可不存储；客户端用其丢弃换号前的异步结果。

### 6.3 环境配置（待运维/服务端确认）

| 项 | 现状 | 待确认 |
|---|---|---|
| Release `API_BASE_URL` | 默认空 | 正式 HTTPS Base |
| 微信开放平台 | AppId 已有配置位 | 包名、签名、移动应用审核 |
| 试用上传 | Release 硬编码 `false` | 是否正式环境启用及配额 |

---

## 7. 潜在安全风险

| 风险 | 等级 | 说明 |
|---|---|---|
| Release 空 Base URL | 中（可用性） | 正式包默认无法登录；避免误以为「已接通生产」 |
| 无 VIP 本地信任缓存 | 低（产品） | 断网已购用户会暂时无 VIP；符合 fail-closed，需产品确认 |
| `FakeMembershipProvider` 位于 main | 低 | 生产 DI 未注入；仍建议后续迁 test 源集或 Proguard/lint 约束 |
| `stateOverride` / `actionDecisionOverride` 可空钩子 | 低 | 仅 androidTest 赋值；缺 CI 强制禁 main 写入 |
| 微信 pending state 明文 SP | 低 | 无 token/code；可接受 |
| cleartext debug/localIntegration | 中（联调） | 仅调试变体；Release 应 HTTPS |
| 客户端价目可篡改展示 | 低 | 价格仅 UI；真实扣款须服务端定价 |
| 支付未接导致误导 | 中（产品） | 用户可点购买但恒「不可用」——需文案/门禁与后端节奏一致 |
| Token 泄露面 | 低 | Keystore + noBackup；日志侧未见 token 打印（既有审查） |

**防伪造 VIP：** 生产路径无法经登录或购买 UI 写入 `VerifiedActive`；仪器化 ForceVip 不进 Release DEX（I3.3.5 已验）。

---

## 8. 建议的后续开发任务拆分（不自动开工）

| 序号 | 建议任务 | 依赖 | 备注 |
|---|---|---|---|
| I3.4.1-B | 与服务端敲定会员/订单/商品 OpenAPI | 本报告 | 字段、错误码、永久会员、时区 |
| I3.4.2 | `ServerMembershipProvider` + 登录后/前台 refresh | B | 替换 `CalculatorVaultApp.membershipProvider`；处理 `Unknown`→加载→结果；**Fake ≠ 真源** |
| I3.4.3 | `HttpAuthApi`（或新客户端）扩展会员/订单 API | B | 保持与现有 auth Header 一致；评估是否引入 OkHttp |
| I3.4.4 | VIP 购买：下单 → 微信/支付宝 → 查单 → refresh Membership | 2+3 | 改 `VipPurchaseViewModel`；不写本地伪造 VIP |
| I3.4.5 | `EntitlementDisplayMapper` 接入真实 `plan` | 2 | 去掉硬编码 Monthly |
| I3.4.6 | Release/Staging Base URL 与证书钉扎策略 | 运维 | 填 `RELEASE_API_BASE_URL` |
| I3.4.7 |（可选）离线 VIP 宽限设计 | 产品 | 若需要：签名短缓存；默认可维持 fail-closed |
| I3.4.8 | lint：禁止 main 写 entitlement override；Fake 迁出 main | 质量 | |

**最佳接入点（现有架构）：**  
替换 `CalculatorVaultApp.membershipProvider` 的 `DefaultMembershipProvider()` 为服务端实现；`EntitlementRepository` / `ActionPolicy` / 应用锁 / 相册**无需改优先级语义**。勿使用 `stateOverride` 作为生产通道。

---

## 9. 能力总表（审查结论）

| 能力 | 分类 |
|---|---|
| 微信 OpenSDK + WXEntry | **可运行** |
| code→login/me/logout 客户端 | **可运行** |
| Debug/localIntegration 联调登录 | **可运行**（需可达后端） |
| Release 默认登录 | **未实现（空 URL）** |
| Token Keystore 会话 | **可运行** |
| 登录授 VIP | **未实现**（故意） |
| DefaultMembershipProvider | **占位** |
| FakeMembershipProvider / Bridge | **测试专用** ≠ 真实 VIP |
| 会员 HTTP / 真实 MembershipProvider | **未实现** + **待服务端** |
| VIP 页展示 | **可运行** |
| VIP 支付下单 | **占位** |
| 设备试用本地 | **可运行** |
| 试用 Server sync | **可运行客户端** + **待服务端/开关** |
| Entitlement + ActionPolicy | **可运行** |
| VIP > Trial > Free | **可运行** |
| 换号/登出防污染 | **可运行** |

---

## 10. 停止声明

I3.4.1-A 只读审查完成并停止，**等待审核**。未修改任何代码，**不进入 I3.4.2**。
