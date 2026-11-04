# I3.4.2 会员查询接口协议 V1

日期：2026-11-04。基于 I3.4.1-A（Android）与 I3.4.1-S（AuroraAppServer）审查结论，以及服务端 S4.1 **已实现代码**核对定稿。

**本阶段：仅协议设计与双端核对。未改代码、未改数据库、未启支付、未进下一开发任务。**

**权威原则：** 会员真源在 Server；Android 不得因登录成功、本地价目或 Fake 注入伪造生产 VIP。`FakeMembershipProvider` / androidTest Bridge **不是**本协议的真实会员通道。

字段标记：

| 标记 | 含义 |
|---|---|
| **服务端已有** | 已存在于 AuroraAppServer 实现 / 响应 / 表结构 |
| **建议新增** | 本协议要求后续实现或产品确认后再加；**本阶段不得改服务端字段去迁就客户端** |

---

## 1. 服务端真实接口（以代码为准）

### 1.1 Method / URL / 鉴权

| 项 | 值 | 标记 |
|---|---|---|
| Method | `GET` | 服务端已有 |
| URL | `/api/v1/membership/me` | 服务端已有 |
| Header | `X-App-Code: calculator_vault` | 服务端已有 |
| Header | `Authorization: Bearer <accessToken>` | 服务端已有 |
| 用户归属 | **仅**从认证 Session 取 `appId` + `userId`（`SessionPrincipal`） | 服务端已有 |
| 客户端指定 userId / 到期时间 | **忽略**；query 参数不能改写状态（IT 已覆盖） | 服务端已有 |
| 公开 grant/revoke HTTP | **无**（POST 返回 404） | 服务端已有 |

实现：`MembershipController.me` → `MembershipService.getStatus(principal.appId(), principal.userId())`。

### 1.2 统一响应信封

| 字段 | 类型 | 说明 | 标记 |
|---|---|---|---|
| `code` | int | `0` = 成功 | 服务端已有 |
| `message` | string | 默认 `"success"` 或错误文案 | 服务端已有 |
| `data` | object / null | 成功时为会员状态；失败时常为 null | 服务端已有 |

类型：`ApiResponse<T>(code, message, data)`。

### 1.3 `data` 字段（MembershipMeResponse）

| JSON 字段 | 类型 | 语义 | 标记 |
|---|---|---|---|
| `status` | string | 见 §1.4 | 服务端已有 |
| `planType` | string \| 省略 | 单一套餐类型可完整描述时：`MONTHLY` / `YEARLY` / `LIFETIME`；**叠加多种限时套餐时为 null 并因 `@JsonInclude(NON_NULL)` 省略** | 服务端已有 |
| `contributingPlanTypes` | string[] | 参与当前有效计算的套餐代码列表（有序去重） | 服务端已有 |
| `isLifetime` | boolean | 是否永久会员（未撤销 LIFETIME 优先） | 服务端已有 |
| `membershipStartedAt` | Instant string \| 省略 | 当前覆盖段起始（ISO-8601 UTC） | 服务端已有 |
| `membershipExpiresAt` | Instant string \| 省略 | 到期时刻；永久会员为 null/省略 | 服务端已有 |
| `serverTime` | Instant string | 服务端权威时钟（UTC） | 服务端已有 |
| `activeGrantCount` | int | 未撤销 grant 数量 | 服务端已有 |

**不在响应中出现（有意）：** `userId`、`appId`、`id`、订单号、支付信息。

时间格式：**UTC Instant**，Jackson 默认如 `2026-01-01T00:00:00Z`。客户端解析用 `Instant.parse`；**不信任设备 wall clock 决定是否 ACTIVE**（以 `status` + `membershipExpiresAt` vs `serverTime` 辅助校验，见 §3）。

套餐代码（`PlanCode`，服务端已有）：`MONTHLY` | `YEARLY` | `LIFETIME`。  
时长规则（服务端已有）：月=30×24h、年=365×24h、非自然月/年；叠加账本重放见 `docs/membership.md`。

### 1.4 `status` 全量枚举（服务端已有）

| status | 含义 |
|---|---|
| `NONE` | 从未有过发放记录 |
| `ACTIVE` | 当前有效（含永久） |
| `EXPIRED` | 有未撤销限时权益，但已到期 |
| `REVOKED` | 曾有发放，当前全部已撤销 |

### 1.5 异常 HTTP / 业务码（与会员查询相关）

| HTTP | `code` | 场景 | 标记 |
|---|---|---|---|
| 200 | 0 | 成功 | 服务端已有 |
| 401 | 40100 | 未认证 / Unauthorized | 服务端已有 |
| 401 | 40101 | Session 过期 | 服务端已有 |
| 401 | 40102 | Session 已撤销 | 服务端已有 |
| 403 | 40301 | `X-App-Code` 与 Token 所属 App 不一致 | 服务端已有 |
| 403 | 40002 | App DISABLED | 服务端已有 |
| 403 | 40300 | Forbidden（通用） | 服务端已有 |
| 404 | 40400 | 无此路由（如伪造 grant） | 服务端已有 |
| 5xx | 50000 | 内部错误 | 服务端已有 |

（会员发放内部错误码如 `40008`/`40401` 等不出现在 `/me` 正常路径，仅列明存在。）

### 1.6 合成 JSON 示例（脱敏）

**A. 无会员（NONE）**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "NONE",
    "contributingPlanTypes": [],
    "isLifetime": false,
    "serverTime": "2026-01-01T00:00:00Z",
    "activeGrantCount": 0
  }
}
```

**B. 年卡 ACTIVE（单一 planType）**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ACTIVE",
    "planType": "YEARLY",
    "contributingPlanTypes": ["YEARLY"],
    "isLifetime": false,
    "membershipStartedAt": "2026-01-01T00:00:00Z",
    "membershipExpiresAt": "2027-01-01T00:00:00Z",
    "serverTime": "2026-01-01T00:00:00Z",
    "activeGrantCount": 1
  }
}
```

**C. 永久会员**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ACTIVE",
    "planType": "LIFETIME",
    "contributingPlanTypes": ["LIFETIME"],
    "isLifetime": true,
    "membershipStartedAt": "2026-03-01T12:00:00Z",
    "serverTime": "2026-11-04T11:00:00Z",
    "activeGrantCount": 1
  }
}
```

**D. 月+年叠加 ACTIVE（planType 省略）**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ACTIVE",
    "contributingPlanTypes": ["MONTHLY", "YEARLY"],
    "isLifetime": false,
    "membershipStartedAt": "2026-01-01T00:00:00Z",
    "membershipExpiresAt": "2027-02-01T00:00:00Z",
    "serverTime": "2026-06-01T00:00:00Z",
    "activeGrantCount": 2
  }
}
```

**E. 已到期 EXPIRED**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "EXPIRED",
    "planType": "MONTHLY",
    "contributingPlanTypes": ["MONTHLY"],
    "isLifetime": false,
    "membershipStartedAt": "2025-10-01T00:00:00Z",
    "membershipExpiresAt": "2025-10-31T00:00:00Z",
    "serverTime": "2026-11-04T11:00:00Z",
    "activeGrantCount": 1
  }
}
```

**F. 会话过期**

```json
{
  "code": 40101,
  "message": "Session expired",
  "data": null
}
```

---

## 2. Android 字段映射表

### 2.1 HTTP → 领域结果（MembershipResult）

| 条件 | → `MembershipResult` | 说明 |
|---|---|---|
| HTTP 200 且 `code==0` 且 `data.status=="ACTIVE"` 且（`isLifetime==true` **或** `membershipExpiresAt` 解析成功且 **>** 用于比较的权威时间） | **VerifiedActive** | 见下表字段 |
| 200/`code==0` 且 status ∈ {`NONE`,`EXPIRED`,`REVOKED`} | **NotEntitled** | **服务端确认无有效会员** |
| 200/`code==0` 且 status=`ACTIVE` 但非 lifetime 且缺少/非法 `membershipExpiresAt` | **Unavailable** | 响应异常，**无法确认** |
| 200 但 `code!=0` / data 缺失 / JSON 损坏 | **Unavailable** | **无法确认** |
| HTTP 401 / 业务码 40100·40101·40102 | 触发会话失效流程；会员仓清除后等价 **NotEntitled** | 非「服务端确认无会员」，而是会话作废 |
| HTTP 403（40301/40002 等） | **Unavailable**（可伴随登出/提示） | **无法确认**；禁止猜 VIP |
| 连接超时 / 读超时 / 断网 / 5xx | **Unavailable** | **无法确认** |
| 尚未发起过查询 / 登录后首次刷新未完成 | **Unknown** | 加载中；**不得**当 VIP，也**不得**当成「已确认无会员」去清试用展示优先级误判 |

**VerifiedActive 字段映射（建议新增客户端 DTO，服务端字段已有）：**

| Android | 来源 | 规则 |
|---|---|---|
| `accountId` | 当前 `AccountSession.Authenticated` 的 `userId` | **禁止**用响应里不存在的 userId 字段 |
| `expiresAtEpochMillis` | `null` if `isLifetime`；else `Instant.parse(membershipExpiresAt).toEpochMilli()` | 服务端已有 |
| `generation` | 发起请求时的 `entitlementRepository.currentAccountGeneration()` | 客户端会话代数；回写时必须仍匹配 |
| （展示缓存）`planType` / `contributingPlanTypes` | 响应字段 | **仅展示**；不进入 ActionPolicy |

**权威时间比较（建议）：** 优先用响应 `serverTime` 判断 ACTIVE 限时会员是否已过期（防本地改时）；若缺省则 `System.currentTimeMillis()` 作二次校验。若 `status==ACTIVE` 但 `membershipExpiresAt <= serverTime` → 视为异常 → **Unavailable**（或保守 **NotEntitled**，见待确认 §6）。

### 2.2 套餐枚举映射（展示）

| 服务端 `PlanCode`（已有） | Android `VipPlan`（已有） | 备注 |
|---|---|---|
| `MONTHLY` | `Monthly` | |
| `YEARLY` | `Annual` | 名称不同，必须显式映射 |
| `LIFETIME` | `Lifetime` | |
| `planType` 省略（叠加） | 展示用「多套餐叠加」或取 `contributingPlanTypes` 文案；**禁止默认 Monthly** | **建议新增**展示文案 |

### 2.3 EntitlementSource

| MembershipResult | EntitlementSource |
|---|---|
| VerifiedActive（未过期） | `Vip(accountId, expiresAt, permanent)` |
| NotEntitled / Unavailable / Unknown | 不产生 Vip → 回落 DeviceTrial 或 None |

优先级保持：**VIP > Trial > Free**（`EntitlementRepository.resolveEffectiveSource` 已有）。

---

## 3. 会员状态转换规则

### 3.1 确认态 vs 不确定态

| 语义 | MembershipResult | 付费能力（APP_LOCK / PRIVATE_ALBUM_IMPORT） |
|---|---|---|
| 服务端确认有效 VIP | VerifiedActive | 允许（直至本地墙钟/serverTime 到期边界） |
| 服务端确认无有效 VIP | NotEntitled（NONE/EXPIRED/REVOKED） | 不允许 VIP；可回落有效试用 |
| 当前无法确认 | Unavailable / Unknown | **不授 VIP**（fail-closed）；试用规则不变 |

### 3.2 场景矩阵

| 场景 | 结果 | 权益 | 展示 |
|---|---|---|---|
| 永久会员 ACTIVE | VerifiedActive(permanent) | VIP | VipPermanent；不得标到期日 |
| 月/年单一 ACTIVE | VerifiedActive(expiry) | VIP | VipActive(对应 plan) |
| 月+年叠加 ACTIVE | VerifiedActive(合并 expiry) | VIP | 叠加文案；planType 可空 |
| EXPIRED / REVOKED / NONE | NotEntitled | 回落试用或免费 | 试用卡或开通 VIP |
| 会员被服务端撤销后再查 | NotEntitled | 立即无 VIP | 同上 |
| 网络超时 / 5xx | Unavailable | 不授新 VIP；见 §4.3 sticky | 「会员状态暂时无法同步」类文案（建议新增） |
| Session 401 | 清会话 + 清 VIP claim | 仅试用/免费 | 游客/试用展示 |
| 字段异常 | Unavailable | 不授 VIP | 不确定态文案 |

### 3.3 到期边界

- **授权：** ActionPolicy 每次 evaluate 对限时 VIP 用 wall/`expiresAt` 实时判断（已有）；**不得**只靠 30s UI ticker。
- **刷新：** 到期后下一次成功 `/me` 应得 `EXPIRED` → NotEntitled；即使缓存未清，墙钟过期也不得继续 Allowed。

---

## 4. 查询时机、并发与异常处理

### 4.1 触发时机（协议约定）

| 时机 | 是否查询 `/me` | 标记 |
|---|---|---|
| 登录成功进入 Authenticated | **是**（必选） | 建议新增客户端调度 |
| 冷启动 `restore()` 恢复有效会话后 | **是** | 建议新增 |
| App 回到前台且仍 Authenticated | **是**（可节流） | 建议新增 |
| 用户主动刷新（设置页） | **是** | 建议新增 |
| 未来支付成功 / 订单完成回调后 | **是**（预留入口 `refreshMembership(reason=Purchase)`） | 建议新增（本阶段不实现支付） |
| 游客 / Guest | **否** | — |
| 仅 UI ticker | **否**（禁止作为唯一校验） | — |

### 4.2 并发与代数

| 规则 | 约定 |
|---|---|
| 去重 | 同一 `generation` 同时仅一条 in-flight `/me`；后来者共用 Deferred 或取消旧请求 |
| 超时 | 对齐现网：`connectTimeout` 10s、`readTimeout` 15s（`HttpAuthApi` 已有量级） |
| 重试 | **默认不自动重试** `/me`（防风暴）；前台/用户手动能再触发 |
| 换号 / 登出 | `accountGeneration++`；进行中的旧请求结果 **一律丢弃** |
| 登出 | 立即 `MembershipProvider`→NotEntitled（清空缓存）；**不**等网络 |
| 旧响应验收 | 仅当 `request.generation == currentGeneration` 且 `accountId` 仍为当前用户 |

### 4.3 查询过程中是否沿用旧会员（Sticky）

| 状态 | 协议 V1 默认 |
|---|---|
| 同账号同 generation 刷新中（Unknown 加载） | **允许沿用**上一笔 VerifiedActive，但须仍满足墙钟未过期 |
| 收到 NotEntitled | **立即**撤销 VIP claim |
| 收到 Unavailable | **沿用**未过期 sticky（若有）；无 sticky 则不授 VIP |
| 换号 / 登出 | **禁止**沿用 |
| Unknown 且从未成功 | 不授 VIP |

> Sticky 为体验体验体验体验体验体验体验体验体验体验体验体验体验体验体验**产品可选项**；若产品要求「断网立即失去 VIP」，改为 Unavailable 亦清 VIP（更严 fail-closed）。见 §6。

### 4.4 Base URL（环境）

| Variant | 会员查询 Base | 规则 |
|---|---|---|
| Debug | `BuildConfig.API_BASE_URL`（默认 `http://10.0.2.2:8080`） | 与登录同一主机；**服务端已有**路径，客户端配置已有 |
| localIntegration | `http://192.168.0.107:8080` | 与登录一致（试用可仍为 8081） |
| Release | `RELEASE_API_BASE_URL`（须 HTTPS；当前默认空） | **配置待补**；空则无法查询 |

`X-App-Code` 固定 `calculator_vault`；Token 仅用 Keystore 会话。

---

## 5. 权益与展示规则

| 条件 | 授权（ActionPolicy） | 设置页展示（EntitlementDisplayMapper） |
|---|---|---|
| VerifiedActive 有效 | VIP 能力允许 | 永久 → `VipPermanent`；限时 → `VipActive(plan, localDate)`，**plan 来自映射，禁止写死 Monthly** |
| VIP 失效 + 试用有效 | 试用能力允许 | `TrialActive` |
| 无 VIP 无试用 | 付费能力拒绝 | 开通 VIP / 试用到期文案 |
| Unavailable 且有 sticky 未过期 | 仍按 VIP（若采纳 sticky） | VIP + 可选「同步异常」提示（建议新增） |
| Unavailable 无 sticky | 不授 VIP；试用照旧 | 试用或「无法同步会员」 |

展示优先级与 `AccountUiState.visibleMembership` 一致：登录态账号 VIP > 设备试用 > 免费。

---

## 6. 待产品确认的问题

1. **Sticky：** 断网/5xx 时是否允许沿用未过期 VIP，还是立即降级？
2. **ACTIVE 但 expiresAt ≤ serverTime：** 映射为 Unavailable 还是 NotEntitled？
3. **叠加套餐展示文案：** 「会员有效至 {date}」是否足够，是否需列出 MONTHLY+YEARLY？
4. **前台刷新节流间隔：** 建议 5 分钟；是否接受？
5. **Release 正式 Base URL** 与是否证书钉扎。
6. **设置页主动刷新**入口是否在本迭代提供。
7. **YEARLY↔Annual** 文案是否统一为「年卡」。

---

## 7. 双端需要修改的文件清单（实施阶段，本阶段不改）

### 7.1 Android（建议新增 / 修改）

| 文件 | 动作 |
|---|---|
| `feature/entitlement/data/ServerMembershipProvider.kt`（新） | 实现 `MembershipProvider` + 异步刷新/缓存/generation |
| `feature/login/session/HttpAuthApi.kt` 或新 `MembershipApi` | `GET .../membership/me` 解析 |
| `app/CalculatorVaultApp.kt` | 注入 `ServerMembershipProvider` 替换 Default |
| `AccountSessionRepository` / App 前台回调 | 登录成功、restore、onStart 触发 refresh |
| `EntitlementDisplayMapper.kt` | 去掉硬编码 Monthly；映射 YEARLY/叠加 |
| `VipPurchaseViewModel.kt` | 预留下单成功后 `refreshMembership`（仍不接支付） |
| JVM/AT 测试 | Fake 与契约测试；标注非真实服务端 |

### 7.2 Server

| 文件 | 动作 |
|---|---|
| **本协议范围内** | **无需为适配客户端改字段** |
| （可选文档）`docs/membership.md` | 链到本 V1 协议 |
| 支付相关 | **不在本 Task** |

---

## 8. 后续开发任务拆分

| ID | 端 | 内容 |
|---|---|---|
| I3.4.3-A | Android | `MembershipApi` + DTO + 错误映射（含 Unknown/Unavailable/NotEntitled） |
| I3.4.3-B | Android | `ServerMembershipProvider`、缓存、generation、去重、sticky 按产品结论 |
| I3.4.3-C | Android | 登录/冷启动/前台/手动刷新接线；展示 mapper 修正 |
| I3.4.3-D | Android | Debug/localIntegration 联调 `/me`；契约测试 |
| I3.4.3-S | Server | 仅当联调发现问题；**禁止无故改响应字段名** |
| I3.4.4+ | 双端 | 订单/支付/grant 回调后刷新（本协议仅预留入口） |

---

## 9. 安全核对（协议声明）

- userId/appId **仅** Session；客户端不可指定会员归属。
- 无公开 grant/revoke。
- Android 生产禁止 `stateOverride` 授 VIP。
- 登出立即清账号 VIP claim；试用存储隔离保留。
- 本阶段不启用正式支付、不改 `TRIAL_DEVICE_UPLOAD_ENABLED`、不访问真实账号库数据。

---

## 10. 停止声明

**I3.4.2 会员查询接口协议 V1 已定稿并停止，等待审核。** 未修改代码或数据库，不进入下一开发任务。
