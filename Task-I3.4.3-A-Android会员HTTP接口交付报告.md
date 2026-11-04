# Task I3.4.3-A Android 会员 HTTP 接口交付报告

日期：2026-11-04。基于《I3.4.2 会员查询接口协议 V1》。**未**替换生产 `DefaultMembershipProvider`，**未**改权益授权 / 应用锁 / 相册 / 试用 / 登录 / 支付。未进入 I3.4.3-B。

**结论：会员 `/me` 网络层、DTO、错误分类与领域映射已落地；JVM 27 项全绿；Debug / localIntegration / Release 构建成功。**

---

## 1. 新增与修改文件

| 文件 | 动作 |
|---|---|
| `app/src/main/.../membership/data/MembershipApi.kt` | **新增** API 接口 |
| `app/src/main/.../membership/data/HttpMembershipApi.kt` | **新增** HttpURLConnection 实现 |
| `app/src/main/.../membership/data/MembershipHttpTransport.kt` | **新增** 可注入 Transport + 默认实现 |
| `app/src/main/.../membership/data/MembershipQueryModels.kt` | **新增** Scope / DTO / QueryResult |
| `app/src/main/.../membership/data/MembershipQueryMapper.kt` | **新增** JSON 解析与状态映射 |
| `app/src/test/.../membership/data/MembershipQueryMapperTest.kt` | **新增** 映射单测 |
| `app/src/test/.../membership/data/HttpMembershipApiTest.kt` | **新增** HTTP/Transport 单测 |

**未改：** `CalculatorVaultApp`（仍 `DefaultMembershipProvider()`）、`EntitlementRepository`、应用锁、私密相册、Server、数据库。

---

## 2. HTTP 请求实现说明

- 独立 `MembershipApi` / `HttpMembershipApi`，不并入 `AuthApi`。
- URL：`{API_BASE_URL}/api/v1/membership/me`（与登录同一 Base URL 规则：https；debug 或 `localHttpBaseUrl` 允许 http）。
- Method：`GET`；无 Body；不传 userId/appId/客户端到期时间。
- Headers：`X-App-Code: calculator_vault`、`Accept: application/json`、`Authorization: Bearer <token>`。
- Timeout：connect **10s** / read **15s**。
- `Dispatchers.IO`；无自动重试；无 redirect/cookie。
- Token 由调用方 `MembershipQueryScope.accessToken` 提供；本层不二次持久化。
- `MembershipHttpRequest.toString()` / `MembershipQueryScope.toString()` 对 Token **脱敏**。
- 可注入 `MembershipHttpTransport`（JVM Fake）；默认 `UrlConnectionMembershipHttpTransport`。
- 成功读 `inputStream`，非 2xx 读 `errorStream`；`finally disconnect`。
- `CancellationException` 原样抛出，不映射为网络失败。

---

## 3. DTO 与状态映射

### 3.1 `MembershipMeDto`（对齐服务端 data）

`status`, `planType?`, `contributingPlanTypes`, `isLifetime`, `membershipStartedAt?`, `membershipExpiresAt?`, `serverTime`, `activeGrantCount`。不解析 userId。

### 3.2 `MembershipQueryResult`

| 结果 | 条件 |
|---|---|
| **ConfirmedActive** | `status=ACTIVE` 且（`isLifetime` 或 `expiresAt > serverTime`） |
| **ConfirmedNotEntitled** | `NONE` / `EXPIRED` / `REVOKED`（服务端明确确认） |
| **TemporarilyUnavailable** | 断网/超时/5xx/403/JSON 坏/缺 data/未知 status/ACTIVE 缺到期或到期≤serverTime/业务码≠0 |
| **SessionInvalid** | HTTP 401 或业务码 40100/40101/40102（**不**在此层清会话） |

`accountId` / `generation` 仅来自 `MembershipQueryScope`。

套餐字符串保留服务端原值（`MONTHLY`/`YEARLY`/`LIFETIME`）；**不会**把 YEARLY 当成 MONTHLY。未知 status **不会**当 ACTIVE。

---

## 4. 可信时间基准（为 I3.4.3-B Sticky 准备）

| 数据 | 来源 | 本阶段用途 |
|---|---|---|
| `serverTime` | 响应 Instant | ACTIVE 合法性：`expiresAt.isAfter(serverTime)` |
| `confirmedAtElapsedRealtime` | 注入的 `elapsedRealtime()`（默认 `SystemClock.elapsedRealtime`） | 供后续 24h sticky 起点 |
| `expiresAt` / `isLifetime` | 响应 | 限时封顶；永久无到期 |
| `accountId` / `generation` | 调用方 Scope | 换号/登出作废 |

提供纯函数 `MembershipQueryMapper.isActiveRelativeToServerTime`。  
**未实现** Sticky 缓存、未改 ActionPolicy。

---

## 5. JVM 测试

| 套件 | tests | failures |
|---|---:|---:|
| `MembershipQueryMapperTest` | 16 | 0 |
| `HttpMembershipApiTest` | 11 | 0 |
| **合计** | **27** | **0** |

覆盖协议要求的：月/年/永久/叠加 ACTIVE、NONE/EXPIRED/REVOKED、缺 expires、expires≤serverTime、未知 status、坏 JSON/缺 data、401/403/500、超时/断网、Header 无 userId、Token 不进 toString、serverTime+elapsed 记录、取消传播、错误流消费。全部 Fake Transport / 内存 JSON，**未访问** 8080/8081。

---

## 6. 构建结果

| Variant | 结果 |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:assembleLocalIntegration` | SUCCESS |
| `:app:assembleRelease` | SUCCESS |
| `:app:testDebugUnitTest`（membership） | SUCCESS |

---

## 7. 安全检查

| 项 | 状态 |
|---|---|
| 生产仍 `DefaultMembershipProvider` | **是** |
| 未注入 Fake VIP / Bridge 到生产 | **是** |
| Token 不写第二份存储、toString 脱敏 | **是** |
| SessionInvalid 不擅自 logout | **是** |
| 未改 Entitlement 优先级 | **是** |

---

## 8. 已知风险与 I3.4.3-B 待办

1. 实现 `ServerMembershipProvider`：消费本层结果 → `MembershipResult`；接线登录/前台刷新；24h sticky（用 `confirmedAtElapsedRealtime` + `serverTime`）。
2. 401 → 交由 `AccountSessionRepository` 正式失效会话。
3. 叠加套餐展示文案；去掉 DisplayMapper 硬编码 Monthly。
4. Release 空 `API_BASE_URL` 时本层无法请求（既有配置问题）。
5. 阻塞式 `HttpURLConnection` 在取消后需等当前读返回；已在返回后 `ensureActive`，长阻塞仍可能延迟取消感知。

---

## 9. 停止声明

I3.4.3-A 完成并停止，**等待验收**。不自动进入 I3.4.3-B。
