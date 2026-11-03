# Task I3.3.3-B2 应用锁 Instrumentation 联调报告

日期：2026-11-03。方案 **A**（androidTest Fake 权益注入）已实现并完成编译；**未**进入 I3.3.4；**未**操作物理手机；**未**改 8080/8081；**未**改试用规则 / EncryptedTrialStore；**未**加 Debug Harness / ADB 广播 / 生产试用重置入口。

**结论：测试隔离与 APK 构建已完成；首轮 Instrumentation 未完整通过；随后 Gradle `connectedDebugAndroidTest` 卸载了目标包并清空设备数据目录 → 按任务「不得卸载/清数据；必须重装则停止」**立即停止**，等待确认后再继续。**

---

## 1. 模拟器身份（首轮运行前 PASS）

| 项 | 值 |
|---|---|
| ADB serial | `emulator-5554` |
| AVD | `CalculatorVault_Test`（`adb emu avd name` + `ro.boot.qemu.avd_name`） |
| `ro.kernel.qemu` | `1` |
| API | 35 |
| 包名 | `com.aurora.calculatorvault` |
| 物理机 | 无 |

---

## 2. 测试隔离实现（PASS）

| 组件 | 接入 |
|---|---|
| `EntitlementInstrumentationBridge` | 仅当 `InstrumentationRegistry` 可用时 arm；Release/普通启动 no-op |
| `EntitlementRepository.computeState` | `ForceNone` / `ForceVip` 覆盖，不写试用存储 |
| `CalculatorVaultApp.onCreate` | `armIfUnderInstrumentation()` |
| `AppLockProtectionCoordinator` | 读同一 `EntitlementRepository` + `ActionPolicy` |
| `AppLockMonitorService` / Overlay / ViewModel / Verification | 经 Coordinator `allowsLiveProtection` / generation |
| `AppLockOverlayController` | 可选 instrumentation unlock override（PIN 竞态） |

真实 Service + WindowManager Overlay；无 Mock Overlay。Fake 不进 Release 业务入口。

---

## 3. 构建结果（PASS）

| APK | 路径 | 约大小 | SHA-256 前缀 |
|---|---|---|---|
| 被测 Debug | `app/build/outputs/apk/debug/app-debug.apk` | ~22.3 MB | `67A7B0F6DC625E77…` |
| androidTest | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | ~744 KB | `B3B027C201A565EC…` |

完整哈希见 `evidence/task-i3.3.3-b2/verification.json`。  
`compileDebugKotlin` / `compileDebugAndroidTestKotlin` / `assembleDebug` / `assembleDebugAndroidTest`：**BUILD SUCCESSFUL**。

首轮执行前曾用 `adb install -r` 覆盖安装（签名兼容，**当时未清数据**，Room DB 仍在）。

---

## 4. Instrumentation 首轮结果（不完全 / FAIL）

命令：`:app:connectedDebugAndroidTest` + class filter `AppLockEntitlementInstrumentationTest`。

| 用例 | 结果 | 说明 |
|---|---|---|
| `repeatedRevokeRestore_noResidue` | **FAIL** | `cycle=0 service`：`AppLockMonitorService.isRunning == false` |
| `backgroundFgs_defersWithoutWipingLocks_foregroundRecovers` | **FAIL** | 前台恢复后既无 Service 也无 `pendingForegroundStart` |
| `fakeVip_overlayLifecycle_roomPreserved_disabledStaysOff` | **未跑完** | 进程在前两例后崩溃 |
| `pinVerifyDuringRevoke_discardsSuccess` | **未跑完** | 同上 |

进程崩溃：

```text
ForegroundServiceDidNotStartInTimeException:
Context.startForegroundService() did not then call Service.startForeground()
… AppLockMonitorService
```

根因判断（测试侧 + 实现侧）：

1. Instrumentation 进程未必处于 `ProcessLifecycleOwner` STARTED → Coordinator 可能 `Defer`/`Start` 条件不稳。  
2. `lockedPackageCount` AtomicInteger 可能晚于 Room 写入，立即 `reconcile` 时 count=0 → 不启动 Service。  
3. API 35 缺 `POST_NOTIFICATIONS` 时 `startForeground` 失败曾被吞掉 → 触发系统杀进程。

已做补强（待你批准重装后再跑）：

- 测试：拉起 Vault Activity、授予 overlay/usage/notifications、等待 Room/Service。  
- Service：`startForeground` 失败时 `stopSelf()`，避免半启动 FGS 杀进程。  
- 后续建议用 `adb install -r` + `am instrument`，**避免** Gradle connected 任务收尾卸载。

**不得**把 JVM 单测当作本轮 Instrumentation PASS。

---

## 5. 阻塞：框架卸载并清空数据（STOP）

首轮结束后：

| 检查 | 结果 |
|---|---|
| `pm path com.aurora.calculatorvault` | 空（包已卸载） |
| `/data/user/0/com.aurora.calculatorvault` | **不存在**（数据已清空） |
| `ro.kernel.qemu` / AVD 名 | 仍为 `1` / `CalculatorVault_Test` |

任务要求：**不得卸载或清除模拟器现有 App 数据**；若测试框架必须重装/清数据 → **立即停止并报告**。

已停止，**未**再次 `adb install`，**未**重建隐私同意/试用档案。

---

## 6. 验收证据对照

| 要求 | 状态 |
|---|---|
| 测试 APK / 被测 APK 构建 | **有** |
| 模拟器身份 | **有**（运行前） |
| Fake 注入覆盖真实 Service/Overlay 证明 | **代码已接线**；运行时 Overlay 启停证据因用例 FAIL/崩溃 **未完整拿到** |
| Overlay 出现/消失实测 | **未完整** |
| Service 启停 | **未完整**（断言失败） |
| Room 前后对比 | **未完整** |
| 每用例 PASS/FAIL | **见 §4** |
| JVM 冒充 Instrumentation | **无** |

---

## 7. 未覆盖 / 风险

- Gradle `connected*AndroidTest` 可能卸载目标包 → 与「不清数据」冲突。  
- FGS + 通知权限在 API 35 上敏感。  
- Coordinator `lockedPackageCount` 与 Room 异步，测试需等待。  
- 设备原试用/隐私/锁定配置已因卸载丢失，需你确认恢复策略。

---

## 8. 待你确认（二选一或组合）

1. **批准**在已清空的 `CalculatorVault_Test` 上 `adb install -r` 重新安装 Debug + androidTest，重新走引导/隐私同意后，用 **`am instrument`（不经 Gradle uninstall）** 重跑 B2。  
2. **从 AVD 快照/备份恢复**原应用数据后再跑（当前本机 `~\.android\avd` 目录未见可用 snapshot 文件，需你指定恢复源）。

确认前本任务停止，不进入 I3.3.4。
