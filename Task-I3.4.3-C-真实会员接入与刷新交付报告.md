# Task I3.4.3-C 真实会员接入与刷新交付报告

日期：2026-11-04。基于 I3.4.3-A HTTP `/me` 与 I3.4.3-B `ServerMembershipProvider`。**未**开发支付、**未**改 Server、**未**改变 VIP > Trial > Free 优先级、**未**进入 I3.4.3-D。

**结论：生产 DI 已切换为 `HttpMembershipApi` + `ServerMembershipProvider`；登录 / 冷启动 / 前台 5 分钟节流 / 设置页手动刷新已接入；SessionInvalid 与账号会话联动；会员套餐展示去硬编码 Monthly；相关 JVM 测试与 Debug / localIntegration / Release 构建通过。**

---

## 1. 新增 / 修改文件清单

| 文件 | 动作 |
|---|---|
| `membership/data/MembershipRefreshCoordinator.kt` | **新增** 刷新协调器（LoginSuccess / SessionRestore / Foreground / Manual + SessionInvalid） |
| `app/CalculatorVaultApp.kt` | **修改** 生产 DI 接入 ServerProvider；启动协调器；前台 `onForeground` |
| `login/session/AccountSessionRepository.kt` | **修改** `peekAccessToken()`、`notifyRemoteSessionInvalid(accountId)` |
| `membership/data/ServerMembershipProvider.kt` | **修改** `VerifiedActive` 透传 planType；注释改为已接线 |
| `membership/data/HttpMembershipApi.kt` | **修改** 空/非法 Base URL 安全降级为 TemporarilyUnavailable |
| `entitlement/domain/MembershipProvider.kt` | **修改** `VerifiedActive` 增加展示用套餐字段 |
| `entitlement/domain/EntitlementModels.kt` | **修改** `Vip` / `EntitlementState` 套餐字段 |
| `entitlement/domain/EntitlementRepository.kt` | **修改** resolveVip 透传套餐 |
| `entitlement/domain/EntitlementDisplayMapper.kt` | **重写** YEARLY/月卡/年卡/永久/叠加映射 + sync hint |
| `settings/presentation/AccountUiState.kt` | **修改** VipActive 可空 planType；sync hint；membershipText 标题 |
| `settings/presentation/SettingsAccountContent.kt` | **修改** 登录态刷新入口 |
| `settings/presentation/SettingsScreen.kt` | **修改** `onRefreshMembership` |
| `vault/presentation/VaultMainScreen.kt` | **修改** 收集 syncState、接线手动刷新 |
| `res/values/strings_account.xml` | **修改** 月卡/年卡/永久会员与刷新文案 |
| `membership/data/MembershipRefreshCoordinatorTest.kt` | **新增** 协调器 / 展示 / DI 相关 JVM 回归 |
| `settings/AccountUiStateTest.kt`（testDebug） | **修改** 月卡/年卡标题期望 |

未改：试用上传开关、Release Base URL、支付 SDK、私密相册文件、应用锁 Room 配置 Schema、Server。

---

## 2. 生产 DI 接入说明

`CalculatorVaultApp`：

1. `HttpMembershipApi(BuildConfig.API_BASE_URL, DEBUG, localHttpBaseUrl = LOCAL_HTTP_BASE_URL)`
2. 单例 `serverMembershipProvider: ServerMembershipProvider`
3. `membershipProvider` 指向同一实例（不再使用 `DefaultMembershipProvider`）
4. `EntitlementRepository` 注入该 Provider（`MembershipStateSignal` revision 仍由 combine 观察）
5. `membershipRefreshCoordinator` 绑定同一 Provider + Session + Entitlement
6. `onCreate`：`membershipRefreshCoordinator.start()`；ProcessLifecycle `onStart` 调用 `onForeground()`

约束落实：

- 生产路径无 `FakeMembershipProvider` 注入
- 不把 `stateOverride` / `actionDecisionOverride` 作为生产入口（仍仅为 androidTest 预留字段，生产从不赋值）
- 登录成功本身不授 VIP；仅服务端 ConfirmedActive 后 Sticky 才授 VIP
- Debug / localIntegration / Release 均走真实 Provider
- `API_BASE_URL` 为空：协调器 `baseUrlConfigured=false` 跳过请求并 Failed；`HttpMembershipApi` 对非法 URL 也不崩溃

---

## 3. 登录 / 冷启动 / 前台 / 手动刷新流程

```
AccountSession 状态变化
  Authenticating → Authenticated     → refresh(LoginSuccess)     // 不节流
  Validating(冷启) → Authenticated   → refresh(SessionRestore)   // 不节流
  Validating(前台 revalidate 后) → Authenticated → refresh(Foreground) // 5 分钟节流
  Authenticated → Authenticated      → 忽略（防请求风暴）
  → Guest / SessionExpired / …       → clearAll() + 权益重算；试用保留

ProcessLifecycle.onStart
  accountSession.revalidate()
  membershipRefreshCoordinator.onForeground()  // 仅 Authenticated；5 分钟节流

设置页「刷新会员状态」
  refreshManual()  // 绕过节流；Syncing 中拒绝连点
```

请求规则：

- Token 来自 `peekAccessToken()`；accountId / generation 与当前 Session + `EntitlementRepository.currentAccountGeneration()` 一致
- Guest 不请求
- 同 accountId+generation 并发由 Provider 去重
- 查询失败不清除设备试用；Sticky 有效时断网保留 VIP（Anomaly 提示）

---

## 4. SessionInvalid 处理流程

```
ServerMembershipProvider.refresh → SessionInvalid
  → emit sessionInvalidEvents(accountId, generation, …)

MembershipRefreshCoordinator
  1. 当前必须仍为 Authenticated 且 userId == event.accountId
  2. entitlement.currentAccountGeneration() == event.generation
  3. clearAccount(accountId, generation)
  4. accountSession.notifyRemoteSessionInvalid(accountId) → expire（仅匹配当前账号）
  5. entitlement.refresh()；登录 UI 随 SessionExpired 更新
```

不得项：旧账号迟到 401 不影响新账号；Provider 不改 Token；403/5xx/超时仍为 TemporarilyUnavailable；不清理相册文件与应用锁 Room 配置。

---

## 5. 会员展示规则

| 服务端状态 | 展示 |
|---|---|
| ACTIVE MONTHLY | 月卡会员 + 到期日 |
| ACTIVE YEARLY | 年卡会员 + 到期日 |
| ACTIVE LIFETIME | 永久会员（无到期日） |
| ACTIVE 多套餐叠加 | VIP会员 + 有效期（planType=null） |
| NONE / EXPIRED / REVOKED | 回落试用或免费 |
| 查询中且无可信 VIP | 「会员状态同步中」 |
| 查询失败且无可信 VIP | 「会员状态暂时无法同步」 |
| 查询失败但 Sticky 有效 | 继续 VIP + 同步异常提示 |

登录状态与会员状态分行展示；有效设备试用不因登录成功消失；购买页套餐/价格未改。

---

## 6. JVM 测试结果

执行：

```
:app:testDebugUnitTest
  MembershipRefreshCoordinatorTest
  ServerMembershipProviderTest
  EntitlementRepositoryTest
  AccountUiStateTest
  AccountSessionTest
```

**结果：BUILD SUCCESSFUL（上述套件全绿）。**

`MembershipRefreshCoordinatorTest` 覆盖任务要求的主要场景（未登录不请求、登录/冷启刷新、前台节流、手动绕过、并发去重、月/年/永久/叠加展示、撤销、Sticky 24h、SessionInvalid、旧 401、登出/换号/同 user 新 generation、应用锁门禁与相册已有能力、手动刷新状态、空 Base URL、Fake 不进 Server 路径等）。

---

## 7. 三变体构建结果

```
:app:assembleDebug
:app:assembleLocalIntegration
:app:assembleRelease
```

**结果：BUILD SUCCESSFUL。**

未执行 `connectedDebugAndroidTest` / 自动安装卸载。

---

## 8. 安全检查

| 项 | 结果 |
|---|---|
| 生产无 Fake VIP 注入 | 通过（App 仅 ServerMembershipProvider） |
| Token 不进日志 | 通过（Scope toString REDACTED；无额外日志） |
| 会员查询不能指定 userId | 通过（仅 Token + 本地 Session accountId 范围） |
| 不修改试用上传开关 | 通过 |
| 不修改 Release Base URL | 通过 |
| 不引入支付 SDK | 通过 |
| 不访问真实微信/生产会员数据 | 通过（JVM Fake HTTP） |
| 空 Base URL 不崩溃 | 通过 |

---

## 9. 已知问题与 I3.4.3-D 联调建议

1. **Sticky 仅进程内**：杀进程后需重新 `/me`；符合 B 阶段设计，D 阶段可验证冷启+恢复会话链路。
2. **前台刷新依赖 ProcessLifecycle + Session 回 Authenticated**：与现有 `revalidate()` 顺序耦合；JVM 已覆盖节流，D 建议真机快速前后台切换验证无风暴。
3. **应用锁 Room 配置保留 / 相册已有数据恢复**：权益门禁 JVM 已断言 VIP 失效后 `APP_LOCK_PROTECTION` Denied 且 VIEW/MANAGE 仍 Allowed；真实 Overlay / Service / Room 端到端留给 **I3.4.3-D 模拟器**。
4. **设置页刷新失败文案**：以 `membershipSyncHint` 展示，未弹 Toast；若产品要 Snackbar 可在 D 微调。
5. **Nickname**：登录 UI 仍为「微信用户」占位，与本任务会员接入无关。

---

**本任务到此停止，等待审核。不自动进入 I3.4.3-D。**
