# Task I3.3.2 统一权益核心开发 · 交付说明

日期：2026-11-02。已按 I3.3.1 批准方案实现权益领域层、统一状态与设置页展示接入。**未**修改应用锁 Service/Overlay/Room，**未**修改私密相册 Repository/导入流程，**未**改微信登录/支付/套餐，**未**安装卸载真机，**未**重置试用，**未**启用正式环境试用上传。

---

## 1. 修改文件清单

### 新增

| 路径 | 说明 |
|---|---|
| `app/src/main/java/.../feature/entitlement/domain/EntitlementModels.kt` | Capability / Source / State / DenialReason / ActionDecision / trial 映射 |
| `app/src/main/java/.../feature/entitlement/domain/MembershipProvider.kt` | VIP 领域接口、`DefaultMembershipProvider`、测试用 `FakeMembershipProvider` |
| `app/src/main/java/.../feature/entitlement/domain/EntitlementRepository.kt` | 统一组合 + 同步 `ActionPolicy` |
| `app/src/main/java/.../feature/entitlement/domain/EntitlementDisplayMapper.kt` | 权益 → 设置页 `AccountUiState` / sessionMessage |
| `app/src/test/java/.../feature/entitlement/EntitlementRepositoryTest.kt` | 真值表与 Fake VIP 用例 |
| `Task I3.3.2 统一权益核心开发交付说明.md` | 本交付说明 |

### 修改

| 路径 | 说明 |
|---|---|
| `app/.../app/CalculatorVaultApp.kt` | 手工 DI：`membershipProvider` + `entitlementRepository` |
| `app/.../settings/presentation/AccountUiState.kt` | 修复 `visibleMembership`：登录用户无 VIP 时仍展示有效试用 |
| `app/.../vault/presentation/VaultMainScreen.kt` | 设置页订阅 `entitlementRepository`，展示走 DisplayMapper |
| `app/src/testDebug/.../AccountUiStateTest.kt` | 补充登录+试用、VIP 优先展示用例 |

---

## 2. 统一权益架构与关键接口

```
DeviceTrialRepository.state ──┐
AccountSessionRepository.state ┤── EntitlementRepository.state : StateFlow<EntitlementState>
MembershipProvider ──────────┘         │
                                       ├── ActionPolicy.evaluate(capability)  // 同步；付费能力前 refresh trial
                                       └── EntitlementDisplayMapper → AccountUiState
```

### 接口要点

- **`EntitlementCapability`**：`APP_MANAGEMENT`、`APP_LOCK_PROTECTION`、`PRIVATE_ALBUM_VIEW_RESTORE`、`PRIVATE_ALBUM_IMPORT`、`PRIVATE_ALBUM_MANAGE_EXISTING`
- **`EntitlementSource`**：`None` / `DeviceTrial` / `Vip`
- **`MembershipProvider`**：生产默认 `NotEntitled`；Fake 仅测试注入
- **`ActionPolicy`**：可同步调用；付费能力判断前调用 `deviceTrial.refresh()`，并按 wall/elapsed 截止点重算，不依赖 30s UI ticker
- **账号 generation**：登出 / 换号递增；Fake `VerifiedActive` 必须匹配 `accountId + generation`，防止旧异步 VIP 复活

本阶段 **只建立判断**，不在 Service / 相册 Repository 拦截。

---

## 3. 权益状态真值表

| 条件 | 有效来源 | APP_MANAGEMENT | VIEW/RESTORE/MANAGE | IMPORT / APP_LOCK |
|---|---|---|---|---|
| Fake VIP 有效（已登录） | VIP | Allow | Allow | Allow |
| Fake VIP 到期 + 试用仍有效 | Trial | Allow | Allow | Allow |
| ACTIVE / PENDING_SYNC / TEMP_UNAVAILABLE 且 remaining>0 | Trial | Allow | Allow | Allow |
| VERIFICATION_REQUIRED / EXPIRED / remaining≤0 | None | Allow | Allow | **Deny** |
| 游客 + 有效试用 | Trial | Allow | Allow | Allow |
| 登录 + 无 VIP + 有效试用 | Trial | Allow | Allow | Allow |
| 登出（Fake VIP 撤销）+ 试用仍有效 | Trial | Allow | Allow | Allow |
| 默认 `DefaultMembershipProvider` + 已登录 | Trial 或 None | Allow | Allow | 仅试用有效时 Allow |
| 无试用无 VIP | None | Allow | Allow | **Deny** |

优先级：**有效 VIP > 有效设备试用 > 免费**。VIP 无离线宽限；`Unavailable`/`Unknown` 不授予 VIP。

试用授予条件：状态 ∈ {ACTIVE, PENDING_SYNC, TEMPORARILY_UNAVAILABLE} ∧ remaining > 0 ∧ 非粘性过期 ∧ 非 VERIFICATION_REQUIRED。

---

## 4. 自动化测试结果

```
EntitlementRepositoryTest  tests=11 failures=0 errors=0
AccountUiStateTest         tests=14 failures=0 errors=0
```

命令：

```text
.\gradlew.bat :app:testDebugUnitTest --tests "com.aurora.calculatorvault.feature.entitlement.*" --tests "com.aurora.calculatorvault.feature.settings.AccountUiStateTest"
```

**凡 VIP 正向断言均明确使用 `FakeMembershipProvider`，不宣称真实会员服务端联调通过。**

---

## 5. 构建结果

| Variant | 结果 |
|---|---|
| `assembleDebug` | SUCCESS → `app/build/outputs/apk/debug/app-debug.apk` |
| `assembleLocalIntegration` | SUCCESS → `app/build/outputs/apk/localIntegration/app-localIntegration-unsigned.apk` |
| `assembleRelease` | SUCCESS → `app/build/outputs/apk/release/app-release-unsigned.apk` |

整体：`BUILD SUCCESSFUL in 1m 43s`。

---

## 6. 与旧会员展示逻辑的兼容说明

| 旧行为 | 新行为 |
|---|---|
| 登录用户 `visibleMembership` **只看** `accountMembership`，忽略有效试用 | VIP > 试用 > 免费；登录无 VIP 时展示 `TrialActive`/`TrialExpired` |
| 游客永不展示账号 VIP | **保持**；游客仍只看 `deviceTrial` |
| PENDING_SYNC / 网络暂不可用若 remaining>0 被粗映射为 TrialActive | **保持为 TrialActive**（不展示为已过期）；sessionMessage 区分「等待联网验证 / 暂时无法验证」 |
| VERIFICATION_REQUIRED（remaining=0） | 映射 `NotSubscribed`，文案说明需重新验证，**不**显示为有效试用 |
| 购买页 / 套餐 / `requestPurchase` | **未改**；仍恒 `PaymentUnavailable` |
| Preview 假 VIP 场景（debug） | 仍可用；生产路径 VIP 仅来自 Entitlement（当前默认不授予） |

布局、购买入口、设置项结构保持不变。

---

## 7. 遗留问题与下一阶段建议

1. **I3.3.3 应用锁**：在 `AppLockProtectionCoordinator` + `AppLockMonitorService` / Overlay 提交边界调用 `actionPolicy.evaluate(APP_LOCK_PROTECTION)`；失权停保护、保留 Room 配置。
2. **I3.3.4 私密相册**：在开 picker 与 `importMedia` 命令边界调用 `PRIVATE_ALBUM_IMPORT`；查看/恢复/管理已有数据保持允许。
3. **真实 VIP**：待确认 Server 契约后实现非默认 `MembershipProvider`；本阶段禁止用登录成功冒充会员。
4. **产品待确认**（沿用 I3.3.1 §9）：无权益时新建文件夹矩阵、批导入中途到期策略、FGS 后台启动失败降级文案。
5. Application 的 `membershipProvider` 目前固定 `DefaultMembershipProvider`；若 debug 需要注入 Fake，应仅通过测试构造 `EntitlementRepository`，不要在生产 Application 暴露可写后门。

---

本任务完成，停止开发，等待验收后再进入 I3.3.3。
