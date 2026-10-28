# CalculatorVault · Task C1.4 隐私专项验收缺口修复交付

日期：2026-10-28（Asia/Shanghai）
范围：仅 C1.3 的 F-01 Release 日志最小化和 F-02 通知权限流程。

## 一、结果

- F-01：应用锁日志全部限制在 Debug；Debug 文案也移除了前台/保护包名、切换目标和临时解锁目标，保留必要状态、计数、耗时及异常类名。最终 Release DEX 与可执行路径已核查。
- F-02：用户主动开启应用锁并保存成功后，Android13+首次按需说明通知用途，再由用户确认申请；拒绝/跳过不阻止原有保护逻辑，后续提供系统通知设置，不重复自动弹窗。
- Debug、未签名 Release 构建成功；完整 JVM 回归 **336 项通过，失败/错误/跳过均为0**，新增通知状态/请求时机测试16项；设备测试源码编译通过。
- 最终 Manifest 仍为原10项权限；AD_ID/MSA未恢复，未新增权限/无障碍。Debug/Release设备试用上传开关均false。
- 未连接设备，实际系统弹窗、通知展示、ROM行为及真机日志仍待验收。没有声称整项隐私动态验收已完成。

未改变72小时规则、密码算法、相册、应用锁监测/匹配/验证/临时解锁判断；未修改AuroraAppServer、未接支付或注销、未开始I3.1。未自动提交或推送，先前C1.1～C1.3待提交内容均保留。

## 二、本次修改文件清单

主Kotlin路径基于 `app/src/main/java/com/aurora/calculatorvault/`：

1. `feature/applock/service/AppLockMonitorService.kt`：Debug日志门禁、去除敏感日志参数；提供已运行服务的状态通知重新发布方法，通知渠道ID改为模块内可复用常量。
2. `feature/applock/overlay/AppLockOverlayController.kt`：Debug日志门禁，去除包名/解锁目标输出。
3. `feature/applock/domain/AppLockNotificationCoordinator.kt`（新增）：可纯JVM测试的通知用途提示、一次性申请和设置回退状态机。
4. `feature/applock/presentation/AndroidAppLockNotifications.kt`（新增）：系统权限/通知与渠道状态、单比特本地提示标记、系统通知设置Intent。
5. `feature/applock/presentation/AppLockViewModel.kt`：仅在用户成功开启保护后触发通知提示，观察系统状态及授权后刷新已有状态通知。
6. `feature/applock/presentation/AppLockScreen.kt`：用途说明、Activity Result通知请求、拒绝提示及通知设置入口。
7. `app/src/main/res/values/strings_app_lock_notifications.xml`（新增）：通知用途、跳过、拒绝/未授权说明、设置入口文案。
8. `app/src/main/assets/legal/permission_description.txt`：通知权限段与实现一致，说明本地提示标记、按需申请和拒绝不阻止原保护逻辑。
9. `app/src/test/java/.../feature/applock/domain/AppLockNotificationCoordinatorTest.kt`（新增）：16项通知状态与触发时机测试。
10. 本说明及 `evidence/task-c1.4/` 验证材料。

本轮相对验收前的文件摘要差异仅上述9个源码/文档文件（其中含新增文件）；Manifest、Gradle配置、试用实现、统一政策版本与主政策没有本轮修改。

## 三、F-01 修复与证据

### 处理方式

所有应用锁Log.d/Log.w均显式置于 BuildConfig.DEBUG 条件内。即使Debug，也不再在日志中传入packageName、targetPackageName、oldPackage、unlockedPackage或临时解锁目标；改用诸如“foreground state updated”“protected target matched”“temporary unlock granted”的状态文字。权限布尔、配置数量、覆盖层耗时仅Debug保留，不输出候选密码或异常正文。

删除仅为日志读取的旧包名局部变量；`session.updateForegroundPackage` 仍按原路径执行。监测轮询间隔、保护列表筛选、验证调用、临时会话和清理条件保持原有逻辑。通知新增的重新发布只更新已有前台状态通知，不重新启动监测或重置解锁状态。

全量检查主源码Log/println/堆栈输出位置：媒体、应用选择、统计、Bugly等业务日志已有Debug门禁；微信使用静默SDK日志实现，账号/设备标识业务没有新增日志。此次没有发现其他业务代码中向Release Android日志输出密码、媒体路径、Token或设备标识的可执行调用。第三方SDK内部动态日志不由此作全量保证，仍属于真机专项范围。

### Release核查

- 两种最终APK的DEX均不存在C1.3记录的敏感诊断前缀，包括 foreground changed: old=、locked target matched=、begin verification=、overlay show requested=、temporary unlock granted= 等10个前缀。
- 检查Release编译的 **1332个本应用Kotlin类（包括协程/内部类）**，没有android/util/Log引用，避免仅依据源码DEBUG条件认定已裁剪。
- 使用SDK apkanalyzer从最终Release APK反汇编 AppLockMonitorService 与 AppLockOverlayController，没有 `Landroid/util/Log;` 调用；且前台状态更新的updateForegroundPackage调用仍存在。
- 未启用R8/minify，也不依靠打包后额外删日志规则；Release的BuildConfig.DEBUG=false令这些诊断分支不形成可执行日志路径。

证据：verification.json、release-monitor.smali、release-overlay.smali。以上为源码、编译字节码与最终DEX检查；尚未执行Release真机Logcat观察，也不控制系统或第三方SDK自己的所有日志。

## 四、F-02 通知授权流程

1. 进入页面/从后台恢复，只查询Android当前权限、应用通知及应用锁渠道状态，**不自动请求权限**。
2. 用户主动开启某个应用的锁定，先沿用原有使用情况访问、悬浮窗检查和保存逻辑。只有保存成功且此次是开启操作，才考虑通知用途提示；保存失败、只是浏览或关闭保护不会自动发起申请。
3. Android13+尚无运行时通知许可，且本地尚未记录提示时，先显示说明：允许通知用于查看应用锁运行状态；不允许仍可继续使用应用锁，通知栏可能看不到状态。
4. 用户点击“允许通知”后，通过ActivityResultContracts.RequestPermission申请POST_NOTIFICATIONS。实际发起前还检查当前进程有效隐私同意和API>=33，重复确认不会重复请求。
5. 用户允许后从Android读取状态，并重新发布当前已运行服务的状态通知；不重新配置、暂停或重启监测。
6. 用户拒绝、返回或系统未授予时，显示“通知尚未开启，可能无法在通知栏看到应用锁运行状态；这不代表应用锁或前台服务无法运行。”同时提供“前往系统通知设置”。通知许可未加入保护状态判定，也不作为停止/拒绝启动应用锁的条件。
7. 本地只保存用途提示已进入过的布尔标记，避免每次页面/冷启动反复弹出。授权结果始终以系统为准，不另存“已授权”凭据；该标记不含包名、账号或设备标识，不上传。提示标记读取异常时不自动申请，写入失败回退到设置入口，避免重复打扰。
8. 拒绝、跳过或系统不再显示弹窗后，不猜测永久拒绝原因，也不循环重试；直接提供设置入口。系统通知设置不可打开时回退应用详情，仍失败则给出手动管理提示。
9. Android12及以下不发起POST_NOTIFICATIONS。若用户在系统中禁用了通知，仍可从设置入口管理。
10. 应用通知或应用锁通知渠道被关闭时显示设置提示；从系统设置返回重新读取，不用旧缓存当成授权。允许后重新发布已有通知，服务核心逻辑不变。

保留一次提示而不是反复自动请求，是本次处理“系统不再显示弹窗”的策略；后续若希望增加手动再次请求按钮，应另行确认交互，不在本次自动扩展。

实现依据：Android官方说明通知许可不是启动FGS的前提，拒绝时通知抽屉和任务管理器展示可能不同；本实现没有把拒绝等同FGS必然失败。
https://developer.android.com/develop/ui/compose/notifications/notification-permission

## 五、文档和政策版本评估

仅更新《应用权限说明》的通知段。主政策既有“由用户决定是否允许通知”“拒绝后展示受限、不等于其他授权”与新流程无冲突，因此不修改主政策正文。

本轮明确评估后，**保留统一政策版本2026-10-28**，不是未经评估沿用：

- POST_NOTIFICATIONS与应用锁持续运行通知用途已在C1.2准确列出，没有增加权限、SDK、信息接收方、网络上传字段或处理目的。
- 改动是既有能力的按需申请、拒绝处理和设置管理交互；本地一次提示标记属于应用锁本地设置，未加入用户识别信息，不上传，不额外持久化系统授权结果。
- 《应用权限说明》补足实际交互和本地标记说明，未扩大主政策的数据处理范围，故本次不要求已同意当前版本的用户再次同意。

五份政策版本/日期仍为2026-10-28，主政策及其他四份说明的SDK/会员/支付/儿童现状不变。C1.1精确匹配规则继续有效，旧1.0或其他不匹配记录仍需重新同意。最终发布审阅如认定涉及实质性处理范围变化，应同步调整唯一版本和文档并重新告知，不得只替换文本绕过门禁。

Debug/Release内五份legal资产与当前源码逐字节相同；当前统一版本常量及主政策的验收前摘要未改变。

## 六、测试、构建与Manifest

最终执行成功：

```powershell
$env:ANDROID_HOME='D:\AndroidSDK'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:generateReleaseBuildConfig
```

- JVM：**336项通过；失败/错误/跳过均为0**，其中新增通知测试16项；原320项隐私、登录、试用、应用锁和后台PIN回归通过。
- 新增测试覆盖：仅进入/恢复不申请；显式成功开启后先解释；重复开启合并；确认一次请求；允许/拒绝；跳过跨控制器恢复；系统不弹窗时设置回退；API26～32不请求、API33/37请求；已授权不打扰；系统设置允许后刷新；提示期间已被外部授予则不重复申请；存储读写异常；通知总开关/渠道被禁用状态；通知拒绝不参与应用锁保护状态。
- Debug构建成功，Release构建及lintVitalRelease成功；Release为未签名包，未进行生产签名/发布。
- 设备测试源码编译通过，adb devices -l无设备；没有把系统真实弹窗/通知/SDK动态行为计作通过。
- 生成Debug/Release的TRIAL_DEVICE_UPLOAD_ENABLED均false，未改Gradle开关。

最终Release APK uses-permission仍为10项：

1. android.permission.INTERNET
2. android.permission.ACCESS_NETWORK_STATE
3. android.permission.ACCESS_WIFI_STATE
4. android.permission.PACKAGE_USAGE_STATS
5. android.permission.SYSTEM_ALERT_WINDOW
6. android.permission.FOREGROUND_SERVICE
7. android.permission.FOREGROUND_SERVICE_SPECIAL_USE
8. android.permission.POST_NOTIFICATIONS
9. android.permission.WRITE_EXTERNAL_STORAGE（maxSdkVersion=29）
10. com.aurora.calculatorvault.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION（signature内部广播保护）

AD_ID/MSA、无障碍服务均未加入；specialUse与原服务用途保持。权限证据同时来自最终合并Manifest及aapt读取Release APK。

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`（未签名，不直接当作可安装正式包）

APK SHA-256、测试汇总、DEX前缀检查、类检查和本轮文件差异见 `evidence/task-c1.4/verification.json`。

## 七、剩余最小真机验收

以下尚未执行：

1. **Android13+首次主动开启**：先完成有效隐私同意、使用情况访问和悬浮窗授权。单纯进入/反复打开页面不弹通知请求；主动开启并保存成功后才说明用途，确认才出现系统申请。允许后检查持续通知和原有保护正常。
2. **拒绝/跳过/不再显示**：分别跳过用途说明、拒绝系统申请、在受控设备设置user-fixed等状态验证系统不弹窗。检查明确提示、系统设置入口可达；重复进入、冷启动、再次启用其他目标不反复自动弹窗；应用锁不因通知拒绝停用。
3. **系统设置与渠道**：允许/关闭应用通知和应用锁通知渠道，返回后检查提示、通知重新发布；不得把拒绝通知后通知抽屉不显示误判为FGS未运行，需查看任务管理器/服务状态。
4. **生命周期与旧系统**：权限对话框前后旋转/后台恢复，确认无重复请求；Android12及以下没有POST_NOTIFICATIONS弹窗，系统通知关闭时仍能进入设置。
5. **Release诊断与核心回归**：使用现有受控测试签名安装Release（不创建新生产凭据），切换普通/受保护测试应用，检查CV_APPLOCK等本应用标签无包名/目标日志；系统自身日志或SDK日志不能误归成本应用输出。同时检查PIN、临时解锁、锁定匹配、后台保护与媒体/试用保留。
6. **C1.3未完成的动态项目**：SDK初始化、实际事件送达、采集/网络和移除权限的ROM兼容仍需原专项验收，不因此次两个代码缺口修复就自动标记整体隐私验收通过。继续保持自有试用上传关闭，不进入真实支付、账号注销或I3.1。

本次没有连接设备，通知渠道/系统设置异常回退与系统是否弹窗的真实表现尤其需人工确认；JVM状态测试不替代系统UI验收。

## 八、证据与停止点

`evidence/task-c1.4/` 包含：

- build.log：最终完整回归与双变体构建记录。
- verification.json：336项测试、16项通知测试、APK哈希、包内文档、1332类日志引用、Release DEX/Smali及本轮文件差异。
- release-monitor.smali、release-overlay.smali：从最终APK反汇编的关键日志调用路径。
- Release-AndroidManifest.xml、release-apk-permissions.txt：最终权限。
- source-baseline.json：本轮开始前源文件摘要，区分前几轮尚未提交内容。

F-01/F-02代码及离线验证修复完成，停止等待人工确认；未执行下一任务或自动提交。
