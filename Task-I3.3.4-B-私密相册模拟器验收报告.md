# Task I3.3.4-B 私密相册模拟器验收报告

日期：2026-11-03。基于 I3.3.4 双层 `PRIVATE_ALBUM_IMPORT` 门禁，在 **CalculatorVault_Test / emulator-5554 / API 35** 上做真实 ContentResolver / 加密落盘 / Room 验收。未进 I3.3.5。

**结论：7/7 instrumentation PASS。正常导入、无权益拦截、Picker 返回失权、批量中途失权、单项失败回滚、到期后已有内容管理、VIP 返回后再导入均通过。Release DEX 不含 Fake VIP / androidTest 注入类。**

---

## 1. 模拟器身份与安装信息

| 项 | 值 |
|---|---|
| AVD | `CalculatorVault_Test`（`adb emu avd name`） |
| serial | `emulator-5554` |
| `ro.kernel.qemu` | `1` |
| API / SDK | `35` |
| 包名 | `com.aurora.calculatorvault` |
| versionName / versionCode | `1.0.0` / `1` |
| lastUpdateTime | `2026-11-03 13:50:04` |
| 安装方式 | `adb install -r`（app-debug + app-debug-androidTest） |
| 运行方式 | `adb shell am instrument -w -e class …PrivateMediaEntitlementInstrumentationTest …/AndroidJUnitRunner` |
| 未使用 | `connectedDebugAndroidTest`、物理机、uninstall/clear、试用库改写、8080/8081 |

合成媒体前缀：`CV_I334B_`（MediaStore Pictures/Movies/CalculatorVaultTest）。基线：`media=0 albums=1`（默认相册）。`@After` 仅删除本用例创建的 media/album/合成 URI。

---

## 2. 用例结果一览

| # | 用例 | 验证级别 | 结果 |
|---|---|---|---|
| 1 | `normalImport_realFilesAndRoom` | COMMAND（真实 URI→加密文件→Room） | **PASS** |
| 2 | `forceNone_blocksPickerAndCreatesNoMedia` | UI gate（effects）+ 文件/Room 不变 | **PASS** |
| 3 | `pickerReturnAfterRevoke_doesNotImport` | COMMAND 边界（失权后 `importMedia`） | **PASS** |
| 4 | `batchMidEntitlementLoss_keepsSuccessesStopsRest` | COMMAND（`actionDecisionOverride` 预算） | **PASS** |
| 5 | `singleItemFailure_rollsBackOnlyThatItem` | COMMAND | **PASS** |
| 6 | `forceNone_existingContentStillManageable` | COMMAND（查看/移/恢复可读/删/文件夹/cleanup） | **PASS** |
| 7 | `vipNavigateAndReturn_listIntact_thenRestoreEntitlementImports` | UI gate + COMMAND 再导入 | **PASS** |

```text
Time: 5.167
OK (7 tests)
```

**说明：** 系统 Picker UI 本身未做 Espresso/UiAutomator 自动化。用例 2/3/7 验证的是 ViewModel effects（`OpenMediaPicker` / `NavigateToVip`）与 Repository 命令边界；用例 1/4/5/6 用 Instrumentation 提供真实 MediaStore URI，**不得与「Picker UI 已点选」混称**。

---

## 3. 图片/视频真实导入与恢复证据

Logcat tag：`CV_I334B_EVIDENCE`（完整跑次 13:51:02–13:51:07）

### 3.1 正常导入（图 + 视频）

```text
normal_import :: ids=[9, 10] files=[
  935c52e5-4035-4eed-9063-8bd145b2881b.mp4:288,
  b9087c54-d85a-4fae-916d-c6d33e160c12.jpg:1179
]
```

- ContentResolver 读取合成 JPEG / video/mp4。
- 加密目录 `files/vault_media/` 落盘，长度 > 0；Room 记录类型 IMAGE + VIDEO。
- 查看路径：`observeAlbumMedia` + `file.exists()`（COMMAND；未做 Compose 播放器截帧）。

### 3.2 恢复到系统相册（ForceNone 下）

```text
manage_without_entitlement :: view/move/restore/delete/cleanupOK cleaned=0
```

- `restoreMediaToSystem` successCount ≥ 1。
- 按 `originalDisplayName` 查 MediaStore Images，`openInputStream` 可读。
- 恢复后私密侧记录与加密文件仍在；随后删除本用例媒体与测试文件夹。

---

## 4. Room / 加密文件前后对比

| 场景 | 导入前 | 导入/操作后 | 清理后 |
|---|---|---|---|
| 基线 | media=0, albums=1 | — | 基线 media 集合保持 |
| 正常导入 | 无本用例 id | +2 Room + 2 加密文件（jpg/mp4） | 删除本用例 id；vault 无残留测试文件 |
| ForceNone 拦截 | 快照 ids/files | **完全不变**；effect=`NavigateToVip` | — |
| Picker 返回失权 | 快照 | **无新 Room / 无新文件** | — |
| 批量中途 | — | success=2, skipped=3；2 个 jpg 落盘 | 仅清本用例 2 条 |
| 单项失败 | — | success=2 fail=1；无 `.tmp` 孤儿 | 清 2 条成功项 |
| 到期管理 | 1 条种子 | move/restore/cleanup(cleaned=0)/delete | 文件夹删除；基线完好 |

跑完后 `run-as … ls files/vault_media`：空目录（仅 `.` / `..`），与基线 media=0 一致。

---

## 5. 批量中途失权与失败回滚

### 5.1 批量中途失权

- 检查点：androidTest `EntitlementInstrumentationBridge.armImportEvaluationBudget(budget=2)`。
- 实现：`stateOverride` 保持 Fake VIP 快照；**仅** `ActionPolicy.evaluate(PRIVATE_ALBUM_IMPORT)` 经 `actionDecisionOverride` 计数，避免 `combine`/refresh 偷预算。
- 结果：`success=2 skipped=3`；已成功 2 项可 `file.exists()`；剩余 3 URI 未建记录。

```text
mid_batch :: success=2 skipped=3 files=[0fa3b433-….jpg, cd126fc0-….jpg]
```

### 5.2 单项失败回滚

- 序列：`good1, invalid URI, good2`（需不同 URI；Repository 会对 `uris.distinct()`）。
- 结果：`success=2 fail=1`；失败项无 Room、无孤立 `.tmp`；成功项不受影响。

```text
item_failure :: success=2 fail=1
```

---

## 6. 修复的缺陷与修改文件

| 文件 | 变更 |
|---|---|
| `EntitlementRepository.kt` | 新增 androidTest 用 `actionDecisionOverride`（生产恒为 null；非公开 Fake VIP） |
| `EntitlementInstrumentationBridge.kt` | `armImportEvaluationBudget`：按 ActionPolicy 导入评估计数 |
| `SyntheticMediaFactory.kt` | **新增** 合成 MediaStore JPEG/MP4 |
| `PrivateMediaEntitlementInstrumentationTest.kt` | **新增** 7 场景验收 |
| （测试修正） | 失败回滚用例改用两个不同 good URI，避免 `distinct` 合并 |

未改：应用锁、支付、登录、`EncryptedTrialStore`、试用 72h 记录、8080/8081。未新增生产 Fake VIP 入口。

---

## 7. Release 测试代码隔离检查

对 `app-release-unsigned.apk` 解包扫描 `classes*.dex` ASCII 子串：

| 模式 | Release | 期望 |
|---|---|---|
| `ForceVip` / `ForceNone` | **MISS** | 不得出现 |
| `EntitlementInstrumentationBridge` | **MISS** | 不得出现 |
| `InstrumentationEntitlementOverride` | **MISS** | 不得出现 |
| `SyntheticMediaFactory` / `PrivateMediaEntitlementInstrumentationTest` / `CV_I334B_` / `armImportEvaluationBudget` | **MISS** | 不得出现 |
| `stateOverride` / `actionDecisionOverride` | **HIT** | 允许：可空钩子字段名，生产不赋值 |
| `PRIVATE_ALBUM_IMPORT` / `VaultMediaRepository` | **HIT** | 生产功能 |

Debug 主包同样 **MISS** Fake/Bridge 类名（Fake 仅在 `androidTest` APK）。`androidTest` APK 含 Bridge / ForceVip 构造逻辑属预期。

主源码检索：`ForceVip`/`ForceNone`/`EntitlementInstrumentationBridge` **仅**出现在 `androidTest`；`main` 仅有空的 `stateOverride` / `actionDecisionOverride` 字段。

---

## 8. 未覆盖场景与风险

1. **系统 Picker 真实点选 UI** 未自动化（厂商 Picker / Intent 不稳定）；已用 COMMAND + effects 覆盖门禁与落盘。
2. **视频解码播放 UI** 未截帧；仅验证 VIDEO 类型、加密文件存在且可读。
3. **大批量 / 低存储** 边界未压测。
4. `actionDecisionOverride` 与 `stateOverride` 同属进程内可空钩子：若未来误在生产赋值会绕过策略，需 Code Review 约束「仅 androidTest 赋值」。
5. 恢复验证依赖 MediaStore 按 displayName 查询；同名冲突时可能命中旧项（本轮使用唯一 `CV_I334B_` 前缀）。

---

## 9. 停止声明

I3.3.4-B 模拟器验收完成并停止。**不进入 I3.3.5。**
