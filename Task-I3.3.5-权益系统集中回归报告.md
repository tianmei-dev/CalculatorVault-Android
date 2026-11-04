# Task I3.3.5 权益系统集中回归报告

日期：2026-11-04。覆盖 I3.2 / I3.3.2 / I3.3.3 / I3.3.4 已交付能力的跨模块回归。以复用 JVM + Instrumentation 为主，并新增 Fake 环境跨模块仪器化用例。未进入下一开发阶段。

**结论：矩阵项均有 PASS 或明确 BLOCKED；本轮 JVM 与 Instrumentation 无失败；Debug / localIntegration / Release 构建成功；Release 不含 Fake VIP / Bridge / 合成测试类。真实微信登录与服务端 VIP 持久化未执行（BLOCKED）。**

---

## 1. 环境

| 项 | 值 |
|---|---|
| AVD | `CalculatorVault_Test` |
| serial | `emulator-5554` |
| API | 35 |
| `ro.kernel.qemu` | `1` |
| 安装 | `adb install -r`（debug + androidTest） |
| 运行 | `am instrument`（**未**使用 `connectedDebugAndroidTest`） |
| 禁止项 | 物理机、卸载/清数据、改真实试用、改 8080/8081 |

**环境标记约定**

| 标记 | 含义 |
|---|---|
| **Fake** | `FakeMembershipProvider` / `EntitlementInstrumentationBridge`（ForceVip/ForceNone/budget） |
| **JVM-RealPolicy** | 内存试用 + 假登录/Fake VIP 声明，非微信 SDK、非会员 HTTP |
| **Device-RealIO** | 真 Room / Service / Overlay / ContentResolver / 加密文件 |
| **BLOCKED** | 需真实微信、正式会员服务端或卸载重装，本轮未执行 |

---

## 2. 回归用例矩阵（PASS / FAIL / BLOCKED）

### A. 权益与账号

| # | 场景 | 结果 | 证据（层 / 环境） |
|---|---|---|---|
| A1 | 游客 + 有效试用：锁与导入均允许 | **PASS** | JVM `EntitlementRepositoryTest` guest+trial；导入能力同测。AT 未单独跑「仅试用无 Bridge」双模块 |
| A2 | 登录 + 无 VIP + 有效试用仍允许 | **PASS** | JVM guest/signed-in trial |
| A3 | Fake VIP 优先于试用 | **PASS（Fake）** | JVM priority Fake VIP |
| A4 | Fake VIP 失效 + 试用有效 → 回落试用 | **PASS（Fake）** | JVM Fake VIP expiry fallback |
| A5 | VIP 与试用均无效 → 付费能力禁止 | **PASS** | JVM expired/untrustworthy；AT CrossModule D4 ForceNone |
| A6 | 退出登录撤销 VIP、保留设备试用 | **PASS（Fake VIP + JVM 试用）** | JVM logout keeps trial；AT `EncryptedTrialStoreTest` |
| A7 | 切换账号：旧异步会员不污染新账号 | **PASS（Fake）** | JVM account switch stale claim |
| A8 | 试用到期 / 离线 / 需重验 | **PASS** | JVM precise expiry、offline、VERIFICATION_REQUIRED |
| A9 | 生产路径登录成功不自动授 VIP | **PASS** | JVM `DefaultMembershipProvider never grants VIP`；设置页 `login alone does not imply membership` |
| A-微信真登录 VIP | 真实微信 + 服务端会员 | **BLOCKED** | 未操作真实账号/8080 |

### B. 应用锁

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| B1 | 有效权益下保护正常 | **PASS（Fake+Device）** | AT `fakeVip_overlayLifecycle…`；JVM coordinator |
| B2 | 到期后 Service/Overlay 停止 | **PASS（Fake）** | AT ForceNone；JVM expiry |
| B3 | Room 锁定配置保留 | **PASS** | AT + JVM |
| B4 | 权益恢复后按原配置再保护 | **PASS（Fake）** | AT restore overlay |
| B5 | 用户主动关闭的锁不自动开启 | **PASS** | AT + JVM |
| B6 | PIN 验证期间失权，旧结果无效 | **PASS（Fake）** | AT `pinVerifyDuringRevoke_discardsSuccess` |
| B7 | 前后台 / FGS 限制 | **PASS** | AT `backgroundFgs_…`；JVM defer |

### C. 私密相册

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| C1 | 有效权益允许图/视频导入 | **PASS（Fake+RealIO）** | AT `normalImport_realFilesAndRoom` |
| C2 | 无权益 UI + Repository 阻止 | **PASS（Fake）** | AT `forceNone_blocks…`；JVM gate |
| C3 | Picker 返回后失权 | **PASS（COMMAND）** | AT `pickerReturnAfterRevoke…`（非系统 Picker UI 自动化） |
| C4 | 批量中途失权保留成功项 | **PASS（Fake budget）** | AT mid-batch；JVM batch |
| C5 | 无权益仍可查看/恢复/删除/移动 | **PASS** | AT manage under ForceNone |
| C6 | 文件夹管理不受限 | **PASS** | AT create/rename/delete folder |
| C7 | 权益变化不误清理媒体记录 | **PASS** | AT cleanup cleaned=0 |

### D. 跨模块联动

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| D1 | 锁开 + 相册有数据 → 失权：锁停、已有相册仍可访问 | **PASS（Fake）** | **新增** AT `lockAndAlbum_revokeStopsLockKeepsExistingMedia` |
| D2 | 权益恢复：锁再保护、相册可再导入 | **PASS（Fake）** | AT `restoreEntitlement_reprotectsAndAllowsImport` |
| D3 | 退出登录但试用有效：两模块仍允许 | **PASS（JVM）** | JVM logout+trial 锁/导入；**未** AT 真登录退出 |
| D4 | VIP 无 + 试用无：锁停、相册仅拦导入 | **PASS（Fake）** | AT `bothPaidDenied_lockStopsAlbumImportOnly` |
| D5 | 连续切换权益不崩不乱 | **PASS（Fake）** | AT `rapidFakeSwitch_lockAndAlbumStable` + 锁 `repeatedRevokeRestore` |
| D6 | 设置页会员展示与 ActionPolicy 一致 | **PASS（JVM 展示映射）** | `AccountUiStateTest` 14；Entitlement display mapping。**无** Settings Compose 仪器化 |

---

## 3. JVM 与 Instrumentation 执行结果

### 3.1 JVM（`testDebugUnitTest`，相关包）

| 套件 | tests | failures | skipped |
|---|---:|---:|---:|
| `EntitlementRepositoryTest` | 13 | 0 | 0 |
| `PrivateAlbumImportEntitlementTest` | 10 | 0 | 0 |
| `AppLockProtectionCoordinatorTest` | 12 | 0 | 0 |
| `AppLockProtectionDecisionTest` | 7 | 0 | 0 |
| `AppLockSessionManagerTest` | 6 | 0 | 0 |
| `AppLockNotificationCoordinatorTest` | 16 | 0 | 0 |
| `AppLockRepositoryImplTest` | 3 | 0 | 0 |
| `DeviceTrialTest` | 27 | 0 | 0 |
| `DeviceTrialCapTest` | 10 | 0 | 0 |
| `IsolatedDeviceTrialJvmHttpTest` | 4 | 0 | 4（需 opt-in） |
| `AccountUiStateTest`（testDebug） | 14 | 0 | 0 |
| **合计（上表）** | **122** | **0** | **4** |

### 3.2 Instrumentation（emulator-5554）

| 类 | 结果 |
|---|---|
| `AppLockEntitlementInstrumentationTest` | **OK (4)** · ~106s |
| `PrivateMediaEntitlementInstrumentationTest` | **OK (7)** · ~4.4s |
| `CrossModuleEntitlementInstrumentationTest`（新增） | **OK (4)** · ~29s |
| `EncryptedTrialStoreTest` | **OK (1)** |
| **合计** | **16 PASS / 0 FAIL** |

首轮 CrossModule 曾因 `tearDown` 返回 `Boolean`、`pickLockablePackage` 选中不可锁包失败；已在 androidTest 内修复后重跑全绿。

---

## 4. Fake vs 真实覆盖范围

| 能力 | Fake 覆盖 | 真实覆盖 | 未覆盖 |
|---|---|---|---|
| VIP 优先级 / 失效回落 / 登出 VIP | Bridge / FakeMembership | — | 微信会员 HTTP |
| 设备试用规则 | JVM 内存试用 | EncryptedTrialStore AT；上传开关关闭 | 真机 72h 耗尽、卸载重装 HTTP |
| 应用锁 Service/Overlay/Room | Bridge ForceVip/None | 真 Service/Overlay/Room | 真实试用 wall-clock 到期 |
| 私密相册导入/恢复 | Bridge | 真 MediaStore URI、加密文件、Room | 系统 Picker UI 点选 |
| 跨模块 D1–D5 | Bridge 同进程 | 真锁+相册 IO | 登出真会话 + 试用双模块 AT |
| 设置展示 | JVM AccountUiState | — | Compose Settings 仪器化 |

**不得将本报告中的 Fake VIP 结果表述为真实会员服务端验证。**

---

## 5. 发现问题与修复

| 问题 | 处理 |
|---|---|
| 跨模块 AT 缺口（D1/D2/D4/D5） | 新增 `CrossModuleEntitlementInstrumentationTest.kt` |
| `@After tearDown() = runBlocking { … pressHome() }` 推断为非 void | 改为块体 + 内部 `runBlocking` |
| `setLocked` 对 Settings 等返回 `Rejected` | 改为与应用锁 AT 相同的 `pickAndLockPackage` |
| Service 启动偶发慢 | 与 B3 一致：`onProcessForeground` + 轮询 reconcile |

**无生产功能新增；无应用锁/支付/登录规则变更；未改 `EncryptedTrialStore` 生产逻辑。**

本轮生产侧仅既有可空钩子字段名出现在 DEX（见下）；赋值点仍仅 androidTest Bridge。

---

## 6. 三种构建结果

| Variant | 命令 | 结果 |
|---|---|---|
| Debug | `:app:assembleDebug` | **SUCCESS** |
| localIntegration | `:app:assembleLocalIntegration` | **SUCCESS** |
| Release | `:app:assembleRelease` | **SUCCESS** |
| androidTest | `:app:assembleDebugAndroidTest` | **SUCCESS** |

---

## 7. Release 安全检查

### 7.1 Fake / 测试类隔离（Release DEX ASCII）

| 模式 | Release |
|---|---|
| `ForceVip` / `ForceNone` / `EntitlementInstrumentationBridge` / `InstrumentationEntitlementOverride` | **MISS** |
| `SyntheticMediaFactory` / `*EntitlementInstrumentationTest` / `CV_I334B_` / `CV_I335_CROSS` / `armImportEvaluationBudget` | **MISS** |
| `stateOverride` / `actionDecisionOverride` | **HIT**（可空钩子字段名；生产不赋值） |

### 7.2 生产覆盖字段写入点

全仓库 `stateOverride =` / `actionDecisionOverride =` **仅**出现在  
`app/src/androidTest/.../EntitlementInstrumentationBridge.kt`。  
`app/src/main` 无赋值。

### 7.3 正式试用上传开关

| Variant | `TRIAL_DEVICE_UPLOAD_ENABLED` |
|---|---|
| debug | `false`（默认；可用 `-P` 覆盖） |
| localIntegration | `false`（本构建未开 `-PLOCAL_TRIAL_UPLOAD_ENABLED`） |
| **release** | **`false`（硬编码）** |

符合既定「正式包默认不上传」配置。

### 7.4 日志敏感信息

- PIN/口令：仅 `BuildConfig.DEBUG` 下记录验证**状态**文案，不输出 PIN 明文。
- 未发现 accessToken / openId / deviceId / 私密文件绝对路径的 `Log.*` 泄露点（抽查 applock/privatemedia/entitlement）。
- 相册加密路径仅 debug 业务 tag 下的非用户路径日志（既有 `CV_VAULT_MEDIA` 风格）。

### 7.5 权益绕过入口

- 应用锁 / 私密相册 main 源码无 ForceVip、无 DEBUG 跳过 `ActionPolicy` 的导入/保护后门。
- 付费能力仍经 `ActionPolicy.evaluate`；免费能力（查看/管理已有）按设计保持 Allowed。

---

## 8. 剩余风险与后续建议

1. **真实微信登录 + 会员服务端 VIP**：需安全沙箱账号与只读/隔离后端；本轮 **BLOCKED**。
2. **仅设备试用（无 Bridge）的 AT 双模块**：建议增加「不装 ForceVip、依赖当前模拟器试用态」用例（注意勿改真实 72h 记录）。
3. **系统 Picker UI E2E**：厂商差异大；继续以 COMMAND + effects 为准，或单独 UIAutomator 专项。
4. **Settings Compose 与 live ActionPolicy**：补仪器化对照 `settings_membership` testTag 与 `evaluate`。
5. **钩子字段治理**：`stateOverride` / `actionDecisionOverride` 建议 lint/CI 禁止 main 赋值。
6. **IsolatedDeviceTrial\*Http**：4 个 JVM 用例 skipped；仅在确认 8081 隔离环境后 opt-in。

---

## 9. 交付物与停止声明

| 交付 | 路径 |
|---|---|
| 本报告 | `Task-I3.3.5-权益系统集中回归报告.md` |
| 新增跨模块 AT | `app/src/androidTest/.../CrossModuleEntitlementInstrumentationTest.kt` |

**I3.3.5 集中回归完成并停止，等待验收。不自动进入下一开发阶段。**
