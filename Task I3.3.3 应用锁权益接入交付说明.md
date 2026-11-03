# Task I3.3.3 应用锁权益接入 · 交付说明

日期：2026-11-03。在 I3.3.2 统一权益核心之上接入应用锁实际保护控制。沿用 UsageStats + ForegroundService + Overlay，**未**新增 AccessibilityService，**未**改私密相册 / 支付 / Server / 试用规则，**未**在真机安装卸载，**未**清空 Room。

---

## 1. 修改文件清单

### 新增

| 路径 | 说明 |
|---|---|
| `feature/applock/domain/AppLockProtectionDecision.kt` | 纯函数启停决策 |
| `feature/applock/domain/AppLockProtectionCoordinator.kt` | Application 级保护协调器 |
| `feature/applock/domain/AppLockProtectionDecisionTest.kt` | 决策真值表 |
| `feature/applock/domain/AppLockProtectionCoordinatorTest.kt` | 到期/恢复/FGS 降级/隐私撤回等 |
| `Task I3.3.3 应用锁权益接入交付说明.md` | 本说明 |

### 修改

| 路径 | 说明 |
|---|---|
| `feature/applock/service/AppLockMonitorService.kt` | onStartCommand / 轮询 / Overlay 前提交门禁；FGS 启动失败返回 false；`hideOverlayAndClearSession` |
| `feature/applock/overlay/AppLockOverlayController.kt` | show / PIN 成功提交前复核权益与 generation |
| `feature/applock/presentation/AppLockViewModel.kt` | 开启锁定走 ActionPolicy；启停改走 Coordinator |
| `feature/applock/presentation/AppLockUiState.kt` | `protectionEntitled`、`NavigateToVip` |
| `feature/applock/presentation/AppLockScreen.kt` | VIP 导航回调 |
| `feature/applock/presentation/AppLockVerificationViewModel.kt` | 失权关闭；异步 PIN 结果作废 |
| `feature/applock/presentation/AppLockVerificationActivity.kt` | 注入 Coordinator |
| `feature/vault/presentation/VaultMainScreen.kt` | AppLock → `VipPurchaseSource.AppLock` |
| `app/CalculatorVaultApp.kt` | 注册 Coordinator；前台 `onProcessForeground`；隐私撤回 bump generation |

---

## 2. Coordinator / Service / Overlay 控制流程

```
EntitlementRepository.state ──┐
Room observeLockedPackages ──┤── AppLockProtectionCoordinator.reconcile()
隐私 / Usage / Overlay / 前台 ┘         │
                                       ├─ RevokeRuntime（bump generation + hide Overlay + clear session）
                                       ├─ StopMonitor / StartMonitor / DeferUntilForeground
                                       └─ 绝不改 Room enabled / 删配置

AppLockViewModel.toggleLock(enable)
  → ActionPolicy(APP_LOCK_PROTECTION) Denied → NavigateToVip
  → Allowed → setLocked → coordinator.reconcile()

AppLockMonitorService
  onStartCommand / 轮询匹配 / show 前 → allowsLiveProtection()
  无权益 → START_NOT_STICKY + hide + clearAll + stopSelf

AppLockOverlayController
  show 前 / PIN 校验成功后 → protectionStillAllowed + generation 一致，否则 discard
```

---

## 3. 到期停止与恢复

### 停止（失权 / 隐私撤回 / 权限丢失 / 锁列表为空）

1. `bumpGeneration()`，使进行中的 Overlay/PIN 回调失效  
2. `hideOverlayAndClearSession`（取消 verifyJob、清临时解锁）  
3. `AppLockMonitorService.stop`  
4. **Room 锁定行、`enabled`、PIN、通知引导历史均不动**

### 恢复（重新获得 VIP/试用）

1. `reconcile` 读取原有非空锁列表 + 权限 + 隐私  
2. 前台则 `startMonitor`；后台 FGS 被拒则 `pendingForegroundStart`，回前台再启  
3. **不**恢复旧临时解锁；**不**自动打开用户已 `enabled=false` 的项；**不**重弹已授予权限

### 退出登录

仅撤销账号 VIP 候选；设备试用仍有效时 `allowsLiveProtection()` 仍为 true，保护继续。

---

## 4. 测试结果

| 套件 | tests | failures |
|---|---|---|
| `AppLockProtectionDecisionTest` | 7 | 0 |
| `AppLockProtectionCoordinatorTest` | 11 | 0 |
| 既有 `AppLock*` + `entitlement.*` | 一并通过 | 0 |

覆盖要点：有效试用可保护；到期停 Service；配置保留；恢复按原配置；关闭项不复活；FGS 失败不删配置；隐私撤回停保护；无权益 Deny；Fake VIP 门禁标签明确。

Overlay「即将显示时到期 / PIN 异步期间到期」由 generation + `protectionStillAllowed` 在 show/verify 边界保证（JVM 测 Coordinator/Decision；完整 Overlay Window 交互属真机场景）。

---

## 5. 构建变体

| Variant | 结果 |
|---|---|
| `assembleDebug` | SUCCESS |
| `assembleLocalIntegration` | SUCCESS |
| `assembleRelease` | SUCCESS |

整体：`BUILD SUCCESSFUL`。

---

## 6. 未覆盖的真实设备场景与风险

1. **真机 Overlay 时序**：系统 `addView` 与权益刚好在同一帧失效的竞态，依赖 generation；建议专用模拟器目视确认到期后 Overlay 消失。  
2. **Android 12+ 后台 FGS**：恢复权益时若在后台，仅 defer；需用户回到 App 前台。  
3. **START_STICKY 系统杀进程重建**：无权益时 `onStartCommand` 拒绝并 `START_NOT_STICKY`；有权益且权限仍在时由 sticky/Coordinator 前台恢复。  
4. **未接私密相册门禁**：I3.3.4 范围。  
5. **真实 VIP**：仍仅 Fake/`DefaultMembershipProvider`；无服务端会员联调宣称。  
6. **VerificationActivity**：主链不用；已加失权 finish，防旁路。

---

本任务完成，停止开发，等待验收后再进入 I3.3.4。
