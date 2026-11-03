# Task I3.3.4 私密相册权益接入交付说明

日期：2026-11-03。基于 I3.3.2 `EntitlementRepository` / `ActionPolicy`，仅门禁**新增导入**；不改应用锁、试用规则、登录/支付/Server；未进 I3.3.5。

**结论：双层 `PRIVATE_ALBUM_IMPORT` 门禁已接入；批量中途到期保留已成功项；已有内容管理不受限；相关 JVM 测试与 Debug / localIntegration / Release 构建通过。**

---

## 1. 修改文件清单

| 文件 | 变更 |
|---|---|
| `PrivateAlbumImportAccess.kt` | **新增**：门禁 + 批量循环（逐项 `importAllowed`） |
| `VaultMediaModels.kt` | `VaultMediaImportSummary.skippedDueToEntitlement` |
| `VaultMediaRepository.kt` | 导入走 Batch；`importAllowed` 注入；`cleanupMissingMediaRecords` 注释澄清 |
| `PrivateMediaViewModel.kt` | UI/命令双层检查；`NavigateToVip`；创建后导入也门禁 |
| `PrivateMediaScreen.kt` | VIP 导航回调；权益中断提示文案 |
| `VaultMainScreen.kt` | Factory 注入 ActionPolicy；`VipPurchaseSource.PrivateAlbum` |
| `CalculatorVaultApp.kt` | Repository `importAllowed` → live ActionPolicy |
| `strings.xml` | `private_media_import_entitlement_partial` |
| `PrivateAlbumImportEntitlementTest.kt` | **新增** JVM 覆盖 |
| `EntitlementRepositoryTest.kt` | 登出+试用仍可导入 |

未改：应用锁模块、试用存储、微信登录、支付、Server。

---

## 2. UI 与命令双层权益检查

| 层 | 时机 | 行为 |
|---|---|---|
| UI | `requestImportToAlbum` / 创建后导入 | `PrivateAlbumImportAccess.allowsImport`；Deny → `NavigateToVip`（`VipPurchaseSource.PrivateAlbum`）；不打开 Picker |
| 命令 | Picker 回调 `importMedia` 入口 | 再次 live evaluate；Deny → VIP，**不开始导入** |
| 命令 | Repository 每个媒体项开始前 | `importAllowed()` 再评；Deny → 停止后续项 |

均调用 `ActionPolicy.evaluate(PRIVATE_ALBUM_IMPORT)`，不依赖 30s UI ticker。

返回相册后列表/文件夹状态不变（仅导航 VIP，不改 Room）。

---

## 3. 批量导入中途到期

`PrivateAlbumImportBatch.run`：

1. **每项开始前**检查权益。  
2. 已开始的当前项走完现有 copy→insert；失败则删临时/私密文件（既有回滚）。  
3. 到期后**不再启动**下一项；`skippedDueToEntitlement` 计剩余。  
4. **不回滚**已成功项。  
5. UI：`已成功导入 X 项，剩余 Y 项因会员权益不足未导入`；若 0 成功且全部因权益跳过 → 再导航 VIP。

不在 DB 事务/文件锁内等待 VIP 页面。

---

## 4. 已有媒体安全保护

| 操作 | 权益要求 |
|---|---|
| 查看/播放、恢复、删除 | **无**（`PRIVATE_ALBUM_VIEW_RESTORE` / manage 策略不变） |
| 文件夹创建/重命名/删除、移动已有媒体 | **无** |
| 列表观察 | **无** |
| **新增导入** | 需 `PRIVATE_ALBUM_IMPORT` Allowed |

`cleanupMissingMediaRecords`：仅按**私密文件是否存在**清理；不读权益；权益失效不会触发清理或删加密文件。

登出后若设备试用仍有效：ActionPolicy 仍 Allowed，可导入（JVM 已覆盖）。

---

## 5. 测试与构建结果

```text
:app:testDebugUnitTest
  --tests feature.privatemedia.*
  --tests feature.entitlement.*
:app:assembleDebug
:app:assembleLocalIntegration
:app:assembleRelease
→ BUILD SUCCESSFUL
```

JVM 覆盖要点：有效权益允许、Deny 拒绝、Picker 返回后到期整批跳过、中途到期保留成功项、单项失败不抹成功项、无权益仍可 VIEW/MANAGE、live 翻转（无 ticker）、登出+试用仍可 IMPORT、清理与权益解耦。

---

## 6. 仍需模拟器验证的场景及风险

| 场景 | 说明 |
|---|---|
| 真机/模拟器 Picker + 真实文件导入 | JVM 未跑 ContentResolver/加密落盘端到端 |
| 大批量（上限 50）中途到期体验 | 逻辑已测；需设备观察进度条与文案 |
| VIP 页返回后列表/文件夹态 | 代码不改状态；需手势回归 |
| 试用精确到期边界 | ActionPolicy 已 live；设备 wall/elapsed 与试用同步需联调 |

风险：`importAllowed` 闭包依赖 `entitlementRepository` lazy 顺序（当前安全）；未来若拆进程需再审。

---

## 7. 停止

I3.3.4 开发交付完成，**停止并等待验收**；不自动进入 I3.3.5。
