# Task I3.3.3-B3 模拟器复测报告

日期：2026-11-03。授权范围：仅 `emulator-5554` / `CalculatorVault_Test`；`adb install -r` + `am instrument`；合成初始化；**未**使用 `connectedDebugAndroidTest`；**未**操作物理机；**未**改 8080/8081；**未**进入 I3.3.4。

**结论：4/4 Instrumentation 用例 PASS；应用锁 Fake 权益 ↔ 真实 Service/Overlay 联调通过。**

---

## 1. 模拟器与安装信息

| 项 | 值 |
|---|---|
| serial | `emulator-5554` |
| AVD | `CalculatorVault_Test` |
| `ro.kernel.qemu` | `1` |
| API | 35 |
| 包名 | `com.aurora.calculatorvault` |
| 安装方式 | `adb install -r` Debug + androidTest（保留数据目录；本轮为重建档案） |
| 试用上传 | `TRIAL_DEVICE_UPLOAD_ENABLED=false` |
| 跑后包仍在 | 是（`am instrument` 未卸载） |

APK SHA-256：

- Debug：`537AE88B…52B137E`
- androidTest：`F1310078…5DC5724`
- Release（unsigned）：`3D992AB5…2AAFE521`

初始化：Instrumentation `@Before` 经 `OnboardingRepository` 写入隐私同意 + 合成 PIN `135790`；应用锁目标 `com.android.chrome`（合成配置）。

执行命令：

```text
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.aurora.calculatorvault.feature.applock.AppLockEntitlementInstrumentationTest \
  com.aurora.calculatorvault.test/androidx.test.runner.AndroidJUnitRunner
```

原始输出：`evidence/task-i3.3.3-b3/am-instrument-r4.txt` → **OK (4 tests)**。

---

## 2. 首轮（B2）失败最终根因

| 根因 | 说明 |
|---|---|
| A. `lockedPackageCount` 滞后 | Coordinator `reconcile` 读 AtomicInteger，Room 写入后未及时反映 → Service 不启动 |
| B. FGS `startForeground` 未带 `specialUse` 类型 | API 35 上失败被吞 → `ForegroundServiceDidNotStartInTimeException` |
| C. `resolveEffectiveSource` 破坏 ForceNone | `source=None` 仍用 snapshot 中试用剩余回退为 DeviceTrial → 失权无效 |
| D. Overlay `hide()` 非主线程 | LifecycleRegistry 抛错（tearDown/stop） |
| E. 测试用 dumpsys 判 Overlay | API 35 窗口 dump 不可靠；以 `AppLockMonitorService.isOverlayShowing()` 为准 |
| F. Gradle `connected*` | 会卸载目标包并清数据（B2 已停） |

---

## 3. 修复文件与变更

| 文件 | 变更 |
|---|---|
| `AppLockProtectionCoordinator.kt` | `lockedPackageCount` 改为 `suspend`，`reconcile` 先读 Room 再决策 |
| `CalculatorVaultApp.kt` | `lockedPackageCount = { observeLockedPackages().first().size }` |
| `AppLockMonitorService.kt` | `ServiceCompat.startForeground(..., SPECIAL_USE)`；失败 `stopSelf`；`isOverlayShowing()` |
| `AppLockOverlayController.kt` | `hide()` 切主线程；hide 时 `finishVerification` |
| `EntitlementRepository.kt` | `resolveEffectiveSource`：显式 `None` 直接返回（修复 ForceNone） |
| `EntitlementTestHooks.kt` | 可注入 provider；无 Instrumentation 时 `setProvider` no-op |
| `EntitlementInstrumentationBridge.kt` | 测试门面 → Hooks |
| `AppLockEntitlementInstrumentationTest.kt` | 合成初始化、前台等待、controller 断言、`am instrument` 友好 |
| 单测 | Coordinator 实时 count；`resolveEffectiveSource` 显式 None |

---

## 4. Instrumentation 用例 PASS/FAIL

| 用例 | 结果 | 覆盖验收项 |
|---|---|---|
| `fakeVip_overlayLifecycle_roomPreserved_disabledStaysOff` | **PASS** | 有效权益启 Service；Overlay 出现；失权 Overlay/Service 停；可进被锁应用；Room 保留；恢复再生效；用户关闭不自动开 |
| `backgroundFgs_defersWithoutWipingLocks_foregroundRecovers` | **PASS** | 后台场景不抹 Room；前台恢复 |
| `pinVerifyDuringRevoke_discardsSuccess` | **PASS** | PIN 验证中失权，旧成功不授予临时解锁 |
| `repeatedRevokeRestore_noResidue` | **PASS** | 3 轮失权/恢复无残留崩溃 |

证据标签 `CV_B3_EVIDENCE`（节选，完整见 `logcat-r4-evidence.txt`）：

- `service_started :: running=true source=Vip(accountId=9001333,…)`
- `overlay_shown :: showing=true target=com.android.chrome` + `SYSTEM_ALERT_WINDOW` 窗口
- `after_revoke :: running=false overlay=false`
- `room_after_revoke :: locked=[com.android.chrome]`
- `overlay_restored` / `user_disabled_stays_off :: locked=[]`
- `cycle_0/1/2 :: service=false overlay=false`
- `pin_revoke :: tempUnlock=false`

---

## 5. Overlay / Service / Room 验证

| 维度 | 证据 |
|---|---|
| Fake → 同一 Repository | `source=Vip(accountId=9001333)` 与 `allowsLiveProtection`/Service 同步 |
| Service | `AppLockMonitorService.isRunning` 启停断言 + log `service started/destroyed` |
| Overlay | `isOverlayShowing()==true`，target=`com.android.chrome`；dumpsys 见 `appop=SYSTEM_ALERT_WINDOW` |
| Room | 失权前后 `locked=[com.android.chrome]`；用户关闭后 `locked=[]` |

**非** JVM 单测冒充；本轮为设备 `am instrument`。

---

## 6. 测试入口 Release 隔离检查

| 检查 | 结果 |
|---|---|
| 公开 Activity / Broadcast / 试用重置入口 | **无** |
| 普通 Debug 启动能否启用 Fake | **否**：`EntitlementTestHooks.setProvider` 要求 `InstrumentationRegistry` |
| 测试结束后 Fake 残留 | **否**：`@After` `clear()`；断言 `!isActive()` |
| 测试后再启 App 真实权益 | Hooks 清空后走试用/账号真实路径（本轮未连 8080/8081） |
| Release DEX 是否含符号 | **是**（`EntitlementTestHooks` / `ForceVip` 等字符串仍在 unsigned Release） |
| Release 运行时可激活 | **否**（无 Instrumentation；provider 默认 null） |

剩余改进：后续可用 TestApplication/DI 把 Fake 类型移出 `main`，使 Release 不再含测试符号（R8 目前因 Repository 引用 Hooks 无法剥离）。

---

## 7. 剩余风险

1. Release 仍含 Hooks 符号（运行不可激活，但增大审计面）。  
2. UsageStats 轮询时效依赖系统；极慢设备上 Overlay 断言需更长等待。  
3. `INSTRUMENTATION_CODE: -1` 与正文 `OK (4 tests)` / 各用例 `STATUS_CODE: 0` 并存（Runner 尾码怪异）；以用例流为准。  
4. 专用模拟器为重建档案，与 B2 前旧数据无关（已授权）。

---

## 8. 停止声明

I3.3.3-B3 完成并停止；**不进入 I3.3.4**。
