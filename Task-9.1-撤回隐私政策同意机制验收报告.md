# Task 9.1 撤回隐私政策同意机制验收报告

项目：CalculatorVault（com.aurora.calculatorvault）  
日期：2026-09-15

## 结论

撤回机制已实现，复用现有 Consent 存储和首次授权页面。撤回仅修改三个隐私字段，不执行数据删除。API 35 模拟器已验证取消、Back、撤回进程重建、冷启动、重新拒绝、重新同意及原密码解锁；204 项单元测试和 2 项设备测试通过。

**尚不能声明全部验收通过：API 37 系统镜像在应用之外反复发生 SurfaceFlinger 崩溃，运行验收受阻；带有完整 AppLock、Shortcut、照片和视频样本的数据回归尚未逐项实测。** 这些项目不能用编译结果或代码审查代替。

## 1. 修改文件

以下路径均相对于项目根目录 `D:/CalculatorVaul`。

| 文件 | 修改内容 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 声明不可导出的独立进程过渡 Activity。 |
| `app/src/main/java/com/aurora/calculatorvault/app/CalculatorVaultApp.kt` | 接入应用级撤回控制器、运行时阻断、Session 清理；过渡进程跳过 SDK 和存储初始化。 |
| `app/src/main/java/com/aurora/calculatorvault/core/datastore/security/SecurityPreferencesDataSource.kt` | 在同一次 DataStore edit 中撤回三个隐私同意字段。 |
| `app/src/main/java/com/aurora/calculatorvault/core/analytics/AnalyticsInitializer.kt` | 关闭事件入口并调用实际 SDK 的 disable。 |
| `app/src/main/java/com/aurora/calculatorvault/core/analytics/UmengAnalyticsTracker.kt` | 同步禁用事件门控，增加可验证的事件发送依赖。 |
| `app/src/main/java/com/aurora/calculatorvault/core/monitoring/BuglyInitializer.kt` | closeBugly 后 enableBugly(false)。 |
| `app/src/main/java/com/aurora/calculatorvault/core/thirdparty/ThirdPartySdkInitializer.kt` | Mutex 串行化初始化与撤回；撤回后阻止旧进程再次初始化。 |
| `app/src/main/java/com/aurora/calculatorvault/core/navigation/AppNavHost.kt` | 撤回完成后移除原导航内容、启动进程重建；隐私页及计算器入口清栈。 |
| `app/src/main/java/com/aurora/calculatorvault/feature/onboarding/presentation/OnboardingViewModel.kt` | 同意后重新使用 StartupDestinationResolver 判断原密码状态。 |
| `app/src/main/java/com/aurora/calculatorvault/feature/legal/presentation/PrivacyDocumentsScreen.kt` | 隐私管理入口、二次确认、重复点击保护和统一错误消息。 |
| `app/src/main/java/com/aurora/calculatorvault/feature/applock/service/AppLockMonitorService.kt` | 启动前检查 Consent；停止时同步取消监控、移除 Overlay；stopService 不再先启动 Service。 |
| `app/src/main/java/com/aurora/calculatorvault/feature/applock/presentation/AppLockVerificationActivity.kt` | 未同意时不展示独立验证入口，返回原隐私流程。 |
| `app/src/main/java/com/aurora/calculatorvault/feature/disguise/shortcut/DisguiseShortcutEntryActivity.kt` | 已有 Shortcut 入口先检查 Consent，不删除 Shortcut。 |
| `app/src/test/java/com/aurora/calculatorvault/core/datastore/security/SecurityPreferencesDataSourceTest.kt` | 验证仅隐私字段改变、写入失败不改原数据。 |
| `app/src/test/java/com/aurora/calculatorvault/feature/onboarding/presentation/OnboardingViewModelTest.kt` | 验证已有用户重新同意后进入计算器，更新旧测试桩的持久化行为。 |

## 2. 新增文件

- `app/src/main/java/com/aurora/calculatorvault/core/privacy/PrivacyWithdrawalController.kt`：应用级执行状态、重复点击防护、失败重试。
- `app/src/main/java/com/aurora/calculatorvault/core/privacy/PrivacyRestartActivity.kt`：独立进程、Binder 死亡通知和主进程重建。
- `app/src/main/java/com/aurora/calculatorvault/core/privacy/PrivacyEntryGuard.kt`：独立 Activity 的 Consent 入口检查。
- `app/src/main/res/values/strings_privacy_withdrawal.xml`：撤回相关文案。
- `app/src/test/java/com/aurora/calculatorvault/core/privacy/PrivacyWithdrawalControllerTest.kt`：顺序、并发点击、写入失败和清理失败测试。
- `app/src/androidTest/java/com/aurora/calculatorvault/core/privacy/PrivacyWithdrawalPersistenceTest.kt`：真实 DataStore 文件重开、原密码、全部 Guide 类型和事件丢弃测试。
- 本报告。

测试中生成的脚本、日志、SDK 检查副本和临时模拟器位于 `build/`，不属于应用功能源文件。

## 3–10. 入口、文案与隐私状态

| 编号 | 验收项 | 实现与结果 |
| --- | --- | --- |
| 3 | 撤回入口位置 | 设置 → 隐私协议 → 隐私管理 → 撤回隐私政策同意。保留现有全部文档，包括儿童个人信息保护规则；使用普通设置 Item，与文档区有明显间距。 |
| 4 | Dialog | 标题“撤回隐私政策同意”；正文见下；按钮“取消”“确认撤回”，确认使用现有 Error 色。取消、Back、外部关闭不写入。确认后按钮禁用；控制器另有原子状态保护。 |
| 5 | 当前 Consent Key | `privacy_accepted`，位于 `files/datastore/calculator_vault_settings.preferences_pb`，复用 DataStoreSecurityPreferencesDataSource。 |
| 6 | 撤回修改的 Key | `privacy_accepted=false`；移除 `privacy_version`、`privacy_accepted_at`。没有其他持久化写入。 |
| 7 | 未修改的 Key | 全部密码及恢复字段、`onboarding_completed`、Guide 和其他业务键不变，详细名单见下。 |
| 8 | Basic Mode | 当前源码没有 `KEY_HAS_ENTERED_BASIC_MODE`、`basic_mode` 或“仅使用基本功能”的实现。真实拒绝行为为“不同意并退出”→ Activity.finish()，本任务保留并实测该行为；没有新增 Basic Mode 或修改试用逻辑。 |
| 9 | 持久化 | 单次 `dataStore.edit` 原子修改三个字段；挂起调用成功返回后才开始运行时清理。写入失败停留当前页，通过 AppMessageHost 提示“撤回失败，请重试”。 |
| 10 | 运行时刷新 | 原存储接口通过 suspend read 暴露快照，并无既有 Consent Flow。本次成功写盘后立即设置进程内 `privacyWithdrawn`，禁用 Tracker，控制器 StateFlow 通知 UI；不等待冷启动。该标记不是第二套持久化 Consent。 |

确认正文：

> 撤回同意后，我们将停止基于隐私政策同意而进行的相关数据处理，并返回首次隐私授权流程。你的密码、应用锁配置和私密相册等本地数据不会因此删除。

完全不改写的安全键：

`password_configured`、`password_hash`、`password_salt`、`password_algorithm`、`password_iterations`、`password_created_at`、`password_updated_at`、`password_recovery_ciphertext`、`password_recovery_iv`、`password_recovery_algorithm`、`password_recovery_version`、`password_recovery_updated_at`、`onboarding_completed`。

完全不重置的 Guide 键：

`guide_vault_entry_shown`、`guide_app_lock_shown`、`guide_app_disguise_intro_shown`、`guide_app_disguise_created_shown`、`guide_private_album_shown`、`guide_app_lock_migration_checked`、`guide_app_disguise_created_migration_checked`、`guide_private_album_migration_checked`、`guide_app_disguise_created_pending`。

除此之外的业务 Preference 同样不改写。不存在 `preferences.clear()`、全局 DataStore 清空或数据库清空调用。

## 11–22. 导航与本地数据边界

| 编号 | 验收项 | 实现与结果 |
| --- | --- | --- |
| 11 | 返回首次 Privacy Flow | 持久化和清理完成后，独立的 `:privacy_restart` Activity 结束旧主进程，等待 Binder 死亡通知，再启动现有 MainActivity。新主进程经现有 OnboardingViewModel 和 Resolver 进入现有 PrivacyConsentScreen。过渡 Activity 不提供另一套授权 UI。 |
| 12 | Back Stack | 完成撤回时原 NavHost 从组合中移除；过渡期间拦截 Back。重建主 Activity 使用 makeRestartActivityTask；原隐私页导航再执行 graph 根节点 inclusive popUpTo。API 35 实测 Back 返回桌面，不能回到设置/Vault。 |
| 13 | VaultSession | 调用现有 `vaultSessionManager.lock()`，结束内存会话。 |
| 14 | Vault Password | 保留；真实文件重开后验证 `0123` 成功；实际 UI 重新同意直接进入计算器，`0123 + =` 可进入 Vault。 |
| 15 | AppLock 数据 | 不调用 AppLock DAO 删除/更新锁定配置；保留所有 AppLockEntry。完整多应用样本回归待设备补测。 |
| 16 | AppLock Service | 同步取消 monitor/lockedPackages/clearUnlock 工作，随后 stopService。Service 冷启动先读取 Consent，未同意不监控。重新同意后沿用 AppLockViewModel.syncMonitorState：有锁定应用且 Usage Access、Overlay 授权可用时启动。 |
| 17 | Overlay | releaseMonitoring 中调用现有 overlayController.hide()，清空 appLockSessionManager 临时会话；进程结束也移除旧窗口。真实悬浮锁层场景尚未实测。 |
| 18 | DisguiseEntry | 不删除、不修改；入口增加 Consent 检查。 |
| 19 | Shortcut | 不删除、不 disable 桌面 Shortcut；未同意点击时进入既有隐私流程。已有 Shortcut 样本场景待实测。 |
| 20 | VaultAlbum | 不访问删除方法，不清空 Room；相册记录保留。 |
| 21 | VaultMedia | 不删除照片、视频、私密文件夹或媒体文件；本任务没有文件删除业务调用。 |
| 22 | Guide | 全部保留；设备测试遍历所有 GuideType，撤回、重新同意后 claim 均为 false；实际 UI 未重播首次入口引导。 |

应用级协程执行撤回，页面销毁/旋转不会取消已确认的操作。初始化与撤回通过同一个 Mutex 串行化。连续确认通过 StateFlow compareAndSet 拦截；进程重建另有 AtomicBoolean 防重复启动。

没有更改系统权限，没有新增权限撤销中心，没有更改 10 秒试用、媒体导入或账号功能。

## 23–29. 第三方 SDK 实际能力及处理

本项目实际依赖版本（来自 `gradle/libs.versions.toml`）：

- 友盟 `common:9.9.8`、`asms:1.8.7.2`。
- Bugly `crashreport:4.1.9.3`。

已访问友盟开发者中心和 Bugly 官方仓库；在线友盟文档页面未返回可提取正文，Bugly 旧文档地址访问失败。因此停止接口的版本证据来自**本项目实际官方 SDK 构建产物**：使用 JDK javap 检查缓存 JAR 的公开方法及字节码，并由应用编译验证调用签名。没有依据第三方示例臆造 API。

| 编号 | 验收项 | 实际行为 |
| --- | --- | --- |
| 23 | 友盟撤回处理 | 先同步关闭 Tracker，再调用 `MobclickAgent.disable()`。撤回期间的主动事件直接返回，不缓存、不补发，不新增撤回埋点。 |
| 24 | 友盟运行时停止能力 | 9.9.8 的公开 `disable()` 将 AnalyticsConfig.enable 设为 false；公开方法中无对应 enable，也没有证明 common/asms 可以完整卸载的 API。不能将 disable 描述为完整反初始化。 |
| 25 | Bugly 撤回处理 | 调用 `CrashReport.closeBugly()`，随后在 finally 中 `CrashReport.enableBugly(false)`。 |
| 26 | Bugly 运行时停止能力 | 4.1.9.3 提供 closeBugly、closeCrashReport、closeNativeReport、startCrashReport、enableBugly。字节码显示 closeBugly 会调用 closeCrashReport 并关闭内部工作组件。closeBugly 会先检查 enabled，因此必须先 close、后 disable。不声称 SDK 类及原生库已卸载。 |
| 27 | 无法完整反初始化策略 | 调用可用停止 API 后立即重建主进程，结束旧 SDK 线程/原生状态。过渡进程不初始化 SDK，也不访问 Room/DataStore。若某 SDK 停止 API 抛错，记录 Debug 日志，仍继续进程替换。未撤销已经完成的历史上报。 |
| 28 | 冷启动 | 复用两个 initializer 的持久化 Consent 检查。API 35 实测撤回后的新进程和再次冷启动均记录 consent=false、skip init。 |
| 29 | 重新同意恢复 | 新进程的初始化状态从初始值开始；成功保存 Consent 后执行既有统一 initializer，两个 SDK 均正常初始化。不在旧进程强行重置 initialized 后重复 init。 |

参考入口：[友盟开发者中心](https://developer.umeng.com/docs/119267/detail/118637)、[Bugly 官方 Android 仓库](https://github.com/BuglyDevTeam/Bugly-Android-Demo)。这两个在线入口不作为版本停止行为的唯一证据。

## 30. 隐私政策需要同步更新

没有重写法律正文。以下现有内容需要由文案/合规审核同步修改：

- `app/src/main/assets/legal/privacy_policy.txt` 第 99–109 行“用户权利”部分：当前第 109 行建议退出并清除数据或卸载来停止处理，已与新增机制不符。
- 建议补充路径“设置 → 隐私协议 → 撤回隐私政策同意”，说明撤回后停止相关采集并返回首次授权流程，本地密码、锁定配置、伪装与私密媒体不会删除，撤回不影响此前基于同意进行的处理。
- `app/src/main/assets/legal/children_privacy_policy.txt` 第 98–104 行也存在“无法通过现有界面完全停止，需清除数据或卸载”的旧描述，应同步审核对应撤回入口及监护人说明。

## 31–35. 构建、测试及限制

| 编号 | 验收项 | 结果 |
| --- | --- | --- |
| 31 | Debug Build | `:app:assembleDebug` 通过；APK：`app/build/outputs/apk/debug/app-debug.apk`。 |
| 32 | Release Build | `:app:assembleRelease` 通过；APK：`app/build/outputs/apk/release/app-release-unsigned.apk`。输出为未签名 Release 包，不代表已完成市场签名或上架。 |
| 33 | API 37 | compileSdk=37、targetSdk=37，编译通过；API 37 运行验收受环境阻断，详见下。 |
| 34 | 数据完整性 | 204 项 JVM 测试通过；新增 2 项设备测试在 API 35 通过。真实临时 DataStore 重开验证了 Consent、原密码、所有 GuideType；UI 验证原密码。Room/媒体/Shortcut/AppLock 保留已代码审查，完整带样本回归未完成。 |
| 35 | 已知问题 | API 37 系统镜像故障；完整业务数据与 Overlay 样本回归待补测；隐私文案需审核同步；当前分支没有 Basic Mode，无法验收不存在的该分支。 |

### 已完成的 UI 验证（独立 API 35 模拟器）

- 首次未同意：首次隐私页显示；两个 SDK 日志均为 skip init。
- 同意：两个 SDK 初始化成功；建立验收密码 `0123`。
- 入口：隐私协议页底部存在隐私管理及撤回 Item。
- 取消与 Back：保持原页面；前后整个 DataStore 文件 SHA-256 相同：`cee27f6f819f4d2e3102d1a2067db132da3cb23362204b2d0b092d97288e6bed`。
- 确认撤回：主进程 PID 从 3896 变为 5052，原首次授权页显示；两个 SDK 均跳过初始化。
- Back：返回桌面，没有回到 Vault 内部页。
- 强制停止后再次启动：仍为首次授权页，两个 SDK 跳过初始化。
- 重新拒绝：沿用“不同意并退出”。
- 重新同意：直接进入计算器；没有创建密码页，没有重复首次引导；两个 SDK 初始化成功。
- 原密码：输入 `0123 + =`，实际到达应用管理/Vault 页面。
- 崩溃日志：该流程未观察到 CalculatorVault 崩溃。

### API 37 环境限制

实际启动了 `CalculatorVault_API_37_Phone`，系统返回 API 37，镜像为 Android 17 `google_apis_playstore_ps16k/x86_64`，模拟器版本 37.1.11.0。

系统反复出现：

```text
Process: surfaceflinger
Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma
```

随后出现 Launcher ANR、UI 自动化无法获取 root node、系统 package/activity 服务暂时不存在。默认渲染、SwiftShader 和关闭 GLDirectMem 均未解决。没有为绕过测试去修改项目的 compileSdk/targetSdk，也没有将该环境故障记录为应用测试通过。已停止故障实例；另建独立 API 35 环境完成可执行验证，没有操作连接的 API 29 真机。

### 验证证据

- `build/task9.1-build.log`：最终构建日志。
- `app/build/reports/tests/testDebugUnitTest/index.html`：204 项单元测试报告。
- `build/task9.1-device-tests-api35.log`：2 项设备测试，OK。
- `build/task9.1-sdk-lifecycle-api35.log`：未同意 → 同意 → 撤回 → 冷启动 → 重新同意的 SDK 日志。
- `build/sdk-inspection/`：实际依赖 SDK 的 javap 检查用 JAR 副本。

本任务未新增 Release Crash 测试入口，未新增 Analytics 事件，未删除数据库、私密媒体或业务 Preference，也未继续其他上架整改。
