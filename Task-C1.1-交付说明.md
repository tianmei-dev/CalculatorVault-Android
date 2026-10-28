# CalculatorVault · C1.1 隐私同意机制修复交付

## 完成范围

仅修复首次告知、政策版本管理、统一有效同意判断及撤回竞态。不修改密码算法、媒体、应用锁或试用规则，不新增无障碍、不接入支付/注销、不修改 AuroraAppServer，不执行 C1.2、C1.3 或 I3.1。

工作区原先存在的四份配套政策文件和核查报告均保留，未在本任务修改；本任务没有自动提交/推送。

## 修改文件清单

以下主 Kotlin 文件位于 `app/src/main/java/com/aurora/calculatorvault/`：

- `core/privacy/PrivacyPolicy.kt`（新增）：唯一政策版本及有效同意规则、进程范围并发门禁。
- `feature/onboarding/data/OnboardingRepository.kt`：明确同意后写当前版本和时间；旧同意不触发密码修复；保留原密码算法。
- `feature/onboarding/domain/StartupDestination.kt`：旧版、缺失或无效同意优先进入告知页。
- `app/CalculatorVaultApp.kt`：注入同一门禁；Application、微信、试用及重新同意入口统一判断。
- `core/navigation/AppNavHost.kt`：同意成功后调用统一入口，不提前恢复会话/SDK。
- `core/thirdparty/ThirdPartySdkInitializer.kt`：整体初始化检查；撤回前关闭门禁，串行停用 SDK；持久化失败也在当前进程停用。
- `core/analytics/AnalyticsInitializer.kt`：同一门禁保护初始化和最后一次同步检查。
- `core/analytics/UmengAnalyticsTracker.kt`：事件必须已初始化且门禁有效，撤回后丢弃事件，不补发。
- `core/monitoring/BuglyInitializer.kt`：同一版本门禁和原子保护 SDK 初始化。
- `feature/login/data/AndroidWeChatSdkBridge.kt`：有效门禁内才创建/准备 SDK 或发起授权。
- `feature/login/domain/WeChatAuthorizationCoordinator.kt`：发起和回调交接均检查运行时门禁，保留 state 校验及一次性消费。
- `feature/login/session/AccountSessionRepository.kt`：登录及 /me 的迟到响应再次检查有效同意，不能恢复被撤回的账号状态。
- `feature/trial/AndroidTrialInfrastructure.kt`：SSAID 实际读取也置于同一门禁内；存储和计时规则不变。
- `app/src/main/res/values/strings.xml`：首次告知及更新提示。
- `app/src/test/java/.../core/privacy/PrivacyConsentTest.kt`（新增）：16 项版本/迁移/撤回/竞态测试。
- 既有 `StartupDestinationResolverTest.kt`、`OnboardingRepositoryTest.kt`：合法同意夹具显式携带当前版本/时间。
- 既有 `app/src/androidTest/java/.../core/privacy/PrivacyWithdrawalPersistenceTest.kt`：更新版本夹具，验证撤回后事件门禁及本地材料保留。
- 本说明及 `evidence/task-c1.1/build.log`、`verification.json`。

未修改 Gradle 上传开关、Manifest、SDK 版本及四份配套法律文档。

## 隐私版本迁移规则

唯一源为 `PrivacyPolicy.CURRENT_VERSION = "2026-10-27"`，对应当前主政策版本。OnboardingRepository.PRIVACY_VERSION 保留为该常量的兼容别名，没有第二份独立版本值。

有效同意须同时满足：

1. privacyAccepted=true；
2. privacyVersion 与当前版本精确相等；
3. privacyAcceptedAt 存在且大于 0。

旧 `1.0`、空版本、未知/未来版本、时间缺失、已拒绝或已撤回均无效。不按版本字符串大小推断许可，不把旧记录静默改成已同意新版。首次启动新代码时先显示当前完整告知，已有完整密码材料不会因此被清空或重新配置。

用户明确点击同意且 DataStore 原子写入成功后，保存当前版本、同意状态和新时间，随后重新校验并允许 SDK/必要会话恢复。写失败不授予许可。拒绝入口保持退出；旧同意记录即使仍在磁盘，也不能通过统一规则放行，重新启动仍需告知。

只更新三个隐私字段；不清除密码、相册、应用锁、账号密文或设备试用记录。重新同意后保持原来的试用起点和剩余上限，不新增 72 小时。

## 首次告知文案

> 计算器密码、私密相册和应用锁配置主要保存在本地；首次启动会在本地记录设备试用时间和状态。微信登录由你主动发起，需处理必要的服务端账号和会话信息；设备试用联网校验启用后，可能向服务器发送 Android ID。取得当前版本隐私政策的有效同意前，不初始化需要同意的统计和异常 SDK，不上传设备试用标识。请查看完整《隐私政策》后选择是否同意。

简介同时提醒政策更新时重新选择是否同意。原有完整隐私政策、用户协议和儿童规则入口，以及同意/拒绝按钮保留；不新增默认勾选或自动同意。

## 同意门禁与并发处理

`PrivacyPolicy.hasValidConsent` 是唯一持久规则；Application 的 `hasPrivacyConsent()` 委托同一个 `PrivacyConsentGate.check()`，不再由模块各自只读 privacyAccepted。

- **启动/SDK**：启动路由检查统一持久规则；Application 与 ThirdPartySdkInitializer、友盟、Bugly 使用同一个进程门禁，实际初始化在许可保护区内执行。友盟事件上报另有同步门禁，未初始化/版本不符/撤回均丢弃。
- **微信**：主动发起前重读有效同意；SDK 准备/发送与回调 Coordinator 再次门禁；WXEntryActivity 原有 hasPrivacyConsent 检查自动获得版本规则。迟到的登录和 /me 成功也不能绕过撤回。
- **设备试用**：联网同步先检查开关及有效同意，才读取身份；AndroidDeviceIdentityProvider 的 SSAID 读取本身也受门禁保护。同步响应仍再次校验同意。本地首次打开记录和计时不需要上传许可，继续运行，不因隐私更新重置。
- **撤回**：等待初始化锁及异步写入之前，先撤销进程许可并改变 generation；旧的异步同意读取即使返回 true，也因 generation 变化被拒绝。现有串行 SDK 停用与进程替换流程保留，撤回后旧进程不可由重新读取或回调启用。
- **保存撤回失败**：控制器显示失败，SDK 在当前进程仍保持停用，可重试持久化。不能声称写入失败已完成持久撤回；若此时强制终止后重启，磁盘仍可能保留先前有效记录。正常成功撤回会保存拒绝状态，并进入进程替换/重新告知。
- 已进入网络传输的请求不能追回；保证的是禁止新的受保护操作及迟到结果重新建立状态，不承诺瞬间撤销已发送的数据。

`TRIAL_DEVICE_UPLOAD_ENABLED` 保持原配置默认 false。已核对生成的 Debug 与 Release BuildConfig 均为 false；本次没有开启上传，也不借更新政策进行联网试用联调。

## 自动化验证

最终执行成功：

```powershell
$env:ANDROID_HOME='D:\AndroidSDK'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:generateReleaseBuildConfig
```

**320 项 JVM 测试通过，失败/错误/跳过均为 0**。其中新增 PrivacyConsentTest 16 项；现有会话测试16项、设备试用测试27项、撤回控制器测试3项及其他回归继续通过。

新增覆盖：全新安装；首次明确同意；旧版本升级阻止隐式迁移；重新同意只改隐私字段；拒绝后冷读仍无效；缺失/未知版本和时间；保存同意失败；版本不符 SDK 工作门禁；撤回和旧进程不能重启；撤回与迟到同意读取竞态；读取失败；旧版本零设备标识读取/零上传（测试中显式开启 Fake 上传门禁以检验版本限制）；重新同意不重置试用；撤回后试用迟到结果；微信发起/回调门禁；登录迟到结果不得建会话。

测试使用 Fake 数据源/SDK 边界、Fake Clock、Fake TrialApi 与测试凭证，不依赖真实微信 AppID、不调用生产服务、不修改设备时间。SDK 工作的 JVM 门禁测试不代表已经动态检测第三方 SDK 的所有内部行为。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。SHA-256 见 verification.json。设备测试仅编译通过；当前 adb 无连接设备，未标记安装、真机视觉或真实 SDK 动态采集检测为已完成。

## 真机人工验收步骤

1. 使用专用设备，全新安装，确认首次文案可读、完整政策链接有效、同意和拒绝均可操作；未操作/仅阅读/拒绝时，观察没有统计/异常 SDK 初始化和试用标识请求。
2. 从保留 `1.0` 已同意记录的旧测试 APK 覆盖安装（不清除数据），确认先显示新版告知，SDK 不因旧记录自动启用；拒绝并重开仍显示告知。
3. 升级前预置测试密码、照片/视频、应用锁配置及已消耗一段的试用记录；重新同意后检查这些数据完整、PIN 仍生效、试用起点和消耗未重置。
4. 同意后确认持久化当前版本和新时间，再冷启动仍识别有效同意；已有密码不重新创建。需要同意的 SDK 在该步骤之后才可初始化。
5. 在设置撤回，检查事件停止、微信未完成回调不能登录、试用异步结果不能改成已验证；进程替换后重新出现告知。取消撤回不改变记录；保存失败时提示失败并允许重试。
6. 在无真实私密数据的测试设备运行 `:app:connectedDebugAndroidTest`，执行持久化与事件门禁测试；本次未执行。
7. 检查 Debug/Release 设备上传开关仍 false，不为本任务打开开关或调用真实试用接口。开启 Fake 的单元测试只验证逻辑，不是正式 APK 上传入口。
8. 真机观察 SDK/网络应使用受控测试数据，不在日志保留真实 Token、微信 code 或完整 Android ID；静态门禁验证不能替代 SDK 动态检测。

本次仅交付 C1.1，停止等待人工验收，不自动继续后续任务。
