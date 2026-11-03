# Task I3.3.3-C 测试权益代码隔离收尾报告

日期：2026-11-03。目标：Release 去除 Fake VIP / Hooks；保留 androidTest 自动化。未进 I3.3.4；未清模拟器数据；未用 `connectedDebugAndroidTest`。

**结论：隔离完成；Release DEX 无 Fake 授予逻辑；JVM + 三变体构建通过；B3 四项 Instrumentation 仍为 OK (4 tests)。**

---

## 1. 修改文件清单

| 操作 | 路径 |
|---|---|
| **删除** | `app/src/main/.../EntitlementTestHooks.kt` |
| **删除** | `app/src/main/.../EntitlementInstrumentationBridge.kt` |
| **改** | `EntitlementRepository.kt` — 去掉 Hooks/`ForceVip`/`ForceNone`；仅保留 `stateOverride: (() -> EntitlementState?)?`（生产恒为 null） |
| **改** | `AppLockOverlayController.kt` — 去掉 Hooks；可选 ctor `passwordVerifierOverride` |
| **改** | `CalculatorVaultApp.kt` — 移除 `armIfUnderInstrumentation` |
| **新增 (androidTest)** | `app/src/androidTest/.../EntitlementInstrumentationBridge.kt` — ForceVip/ForceNone 与状态构造仅在此 |
| **改 (androidTest)** | `AppLockEntitlementInstrumentationTest.kt` — 经 Bridge 注入 `repository.stateOverride` |

生产仍走 VIP > Trial > Free；不写试用存储 / Server / 支付。Coordinator / Service / Overlay 仍读同一 `EntitlementRepository`。

---

## 2. Release DEX 检查

对 `app-release-unsigned.apk` 全 DEX ASCII 扫描：

| 针 | Release | Debug APK | androidTest APK |
|---|---|---|---|
| `EntitlementTestHooks` | 无 | 无 | — |
| `EntitlementInstrumentationBridge` | 无 | 无 | **有** |
| `ForceVip` / `ForceNone` | 无 | 无 | **有** |
| `InstrumentationEntitlementOverride` | 无 | 无 | **有** |
| `SYNTHETIC_ACCOUNT` / `9001333` | 无 | 无 | **有** |

Release / 普通 Debug APK **不再包含**可授予 Fake VIP 的测试逻辑；Fake 仅存在于 androidTest APK。

生产残留：通用字段名 `stateOverride`（无授予语义，生产不赋值）。无公开 Activity / Broadcast。

---

## 3. JVM 测试与构建

```text
:app:testDebugUnitTest --tests entitlement.* --tests applock.*
:app:assembleDebug
:app:assembleLocalIntegration
:app:assembleRelease
:app:assembleDebugAndroidTest
→ BUILD SUCCESSFUL
```

---

## 4. Instrumentation（B3 四项复测）

环境：`emulator-5554` / `CalculatorVault_Test` / `ro.kernel.qemu=1`；`adb install -r`（未卸载、未清数据）。

```text
OK (4 tests)
```

| 用例 | 结果 |
|---|---|
| `repeatedRevokeRestore_noResidue` | PASS |
| `backgroundFgs_defersWithoutWipingLocks_foregroundRecovers` | PASS |
| `fakeVip_overlayLifecycle_roomPreserved_disabledStaysOff` | PASS |
| `pinVerifyDuringRevoke_discardsSuccess` | PASS |

证据：`evidence/task-i3.3.3-c/am-instrument.txt`、`logcat-evidence.txt`（含 `Vip(9001333)`、`overlay_shown`、`after_revoke`、Room 对比）。跑后包仍在。

---

## 5. 剩余风险

1. `EntitlementRepository.stateOverride` 为进程内缝；若未来主码误赋值可注入状态——需靠评审约束（当前无生产写入点）。  
2. 更彻底隔离可用自定义 `TestApplication` + 构造期注入，进一步去掉可变字段（非本轮必须）。  
3. Runner 尾码 `INSTRUMENTATION_CODE: -1` 与正文 `OK (4 tests)` 并存；以用例 STATUS_CODE=0 为准。

---

## 6. 停止

I3.3.3-C 完成并停止；**不进入 I3.3.4**。
