# Task I3.4.3-B ServerMembershipProvider 交付报告

日期：2026-11-04。基于 I3.4.3-A 网络层与 I3.4.2 协议。**未**替换生产 `DefaultMembershipProvider`，**未**接 UI/支付/自动刷新调度，**未**进 I3.4.3-C。

**结论：`ServerMembershipProvider` + 24h Sticky VIP + SessionInvalid 事件 + 权益层 revision 观察已就绪；JVM 相关测试全绿；三变体构建成功。**

---

## 1. 新增 / 修改文件

| 文件 | 动作 |
|---|---|
| `membership/data/ServerMembershipProvider.kt` | **新增** Provider、Sticky 缓存、refresh/clear、SessionInvalid 事件 |
| `entitlement/domain/MembershipProvider.kt` | **新增** `MembershipStateSignal`（revision Flow） |
| `entitlement/domain/EntitlementRepository.kt` | **最小扩展**：combine 可选 `membershipStateRevision` |
| `membership/data/ServerMembershipProviderTest.kt` | **新增** 28 项 JVM 测试 |

未改：`CalculatorVaultApp`（仍 Default）、应用锁、相册、试用持久化、登录流程、Server、Release 配置。

---

## 2. Provider 状态机

```
NeverQueried → refresh
  ConfirmedActive     → Sticky + VerifiedActive（membershipFor）
  ConfirmedNotEntitled→ 清 Sticky → NotEntitled
  TemporarilyUnavailable → 无 Sticky 则 Unavailable；有 Sticky 则继续评估窗口
  SessionInvalid      → 清 Sticky → NotEntitled + emit 事件（绑定 accountId/generation）

clearAll / clearAccount → Cleared + 作废 in-flight（applyEpoch++）
Sticky 超时 / 会员到期 / 时钟回退 → Unavailable（清 Sticky）
```

同步 `membershipFor(accountId, generation)` 每次用单调时钟重算 Sticky，不依赖 UI ticker。

---

## 3. 24 小时 Sticky

### 3.1 保存字段（ConfirmedActive）

`accountId`, `generation`, `serverTimeEpochMillis`, `confirmedAtElapsedRealtime`, `expiresAtEpochMillis?`, `isLifetime`, 套餐展示字段。

### 3.2 计算

```
estimatedServerNow = serverTime + (elapsedNow - confirmedAtElapsed)
offlineDeadlineElapsed = confirmedAt + 24h
限时有效：elapsedNow < offlineDeadline 且 estimatedServerNow < expiresAt
永久有效：仅 elapsedNow < offlineDeadline
```

### 3.3 失效（立即）

满 24h；限时到期；ConfirmedNotEntitled；登出/换号 clear；SessionInvalid；elapsed 回退；generation/account 不匹配；进程重启（内存不持久化）。

### 3.4 约束

失败/查询中 **不** 刷新 `confirmedAt`；仅新 ConfirmedActive 重置 24h 起点；到期调度 Job 触发 revision，权益层可重算。

---

## 4. 账号与并发

| 规则 | 实现 |
|---|---|
| 请求绑定 accountId+generation | `MembershipQueryScope` + Sticky 校验 |
| 同 generation 单飞 | `Mutex` + 共享 `Deferred` |
| 换号/登出 | `clearAll`/`clearAccount` + `applyEpoch++` + cancel in-flight |
| 迟到结果 | epoch 不匹配则丢弃 apply |
| 迟到 401 | 不覆盖新账号 Sticky；事件带原 generation |
| 取消 | `CancellationException` 传播，不写成 Unavailable 覆盖 |

---

## 5. SessionInvalid 通知

`ServerMembershipProvider.sessionInvalidEvents: SharedFlow<MembershipSessionInvalidEvent>`  
字段：`accountId`, `generation`, `httpStatus`, `businessCode`。  
本阶段 **不** 改 Token / AccountSession；I3.4.3-C 订阅并接入会话状态机。

---

## 6. EntitlementRepository 衔接

- 新增可选 `MembershipStateSignal`；Default/Fake 不实现。
- `combine(trial, session, membershipRevision)`：revision 变化时重算。
- `ActionPolicy.evaluate` 每次 live `membershipFor` → Sticky 边界同步拒绝 VIP。
- **未**使用 `stateOverride` / `actionDecisionOverride` 作生产 VIP。

---

## 7. JVM 测试

| 套件 | tests | failures |
|---|---:|---:|
| `ServerMembershipProviderTest` | 28 | 0 |
| `HttpMembershipApiTest`（回归） | 11 | 0 |
| `MembershipQueryMapperTest`（回归） | 16 | 0 |
| `EntitlementRepositoryTest`（回归） | 13 | 0 |

覆盖：月/年/永久/叠加 ACTIVE、NONE/EXPIRED/REVOKED、首败、1h/23h59 Sticky、24h 失效、永久 24h、限时提前到期、失败不延窗、ACTIVE 刷新窗、登出/换号、同 user 不同 gen、迟到响应/401、当前 401 事件、并发去重、取消、时钟回退、新实例无内存 VIP、revision→权益观察、过期同步拒绝、试用不被 VIP 清理带走。

---

## 8. 构建

| Variant | 结果 |
|---|---|
| Debug | SUCCESS |
| localIntegration | SUCCESS |
| Release | SUCCESS |

---

## 9. 安全检查

| 项 | 状态 |
|---|---|
| 生产仍 DefaultMembershipProvider | **是** |
| 无 Fake VIP 生产注入 | **是** |
| Sticky 仅内存、按账号/generation 隔离 | **是** |
| 未改试用上传开关 / 试用持久化 | **是** |
| SessionInvalid 不私自清 Token | **是** |

---

## 10. 已知限制与 I3.4.3-C 待办

1. 将 `ServerMembershipProvider` + `HttpMembershipApi` 注入 `CalculatorVaultApp`。
2. 登录成功 / 冷启动 / 前台 / 手动刷新调度 `refresh`。
3. 订阅 `sessionInvalidEvents` → AccountSession 正式失效。
4. 登出/换号调用 `clearAll`/`clearAccount`。
5. DisplayMapper 套餐展示（YEARLY/叠加）。
6. Sticky 不跨进程；杀进程后需重新 `/me`。

---

## 11. 停止声明

I3.4.3-B 完成并停止，**等待验收**。不自动进入 I3.4.3-C。
