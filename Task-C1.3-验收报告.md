# CalculatorVault · Task C1.3 隐私行为专项验收报告

验收日期：2026-10-28（Asia/Shanghai）
基线：当前工作区 C1.1 + C1.2 交付，政策版本 2026-10-28。

## 一、验收结论

**结论：离线逻辑、构建及大部分静态一致性检查通过；专项验收暂不作整体通过。**

- 强制重新执行构建与全部 JVM 测试：320 项通过，失败/错误/跳过均为 0。
- Debug、未签名 Release、Release lintVital 构建通过；设备测试源码编译通过。
- 最终 APK 的权限、上传开关及五份文档完成核验。
- 发现两项静态缺口：Release 包名日志；通知运行时请求流程与告知对应关系未闭环。仅记录，未修复。
- adb devices -l 无设备。SDK 实际自启动/采集/网络、统计事件送达、厂商兼容和应用锁交互均尚未验证，不得计作通过。
- **暂不建议直接进入 I3.1 真实联调**：先处理或明确接受这两项缺口，再完成下列最小真机验收，取得人工确认。不自动启动 I3.1。

本次没有修改业务代码、测试源码、政策文本、Manifest 或配置，未开启上传、未改变试用、未修改 Server。对 app/src、app/build.gradle.kts、版本目录的验收前后文件摘要进行了比对，无变化。仅新增本报告和 evidence/task-c1.3 验证材料，没有提交或推送。

## 二、已通过项目（限定证据层级）

| 项目 | 结果及证据边界 |
|---|---|
| 新安装/旧记录有效性 | PrivacyConsentTest、StartupDestinationResolverTest 通过。未同意、旧 1.0、缺失/未知版本、无有效时间均不放行；当前版本精确匹配。属于 JVM 规则验证，不等于已看过真机首次页。 |
| 首次/重新明确同意 | 保存当前版本、布尔状态与时间，写失败不授予许可；旧版拒绝后冷读仍无效。原入口保留完整政策和拒绝按钮。 |
| 重新同意保留本地状态 | Fake 测试验证只改隐私字段、不修复旧版密码材料；原 DataStore 接受/撤回实现仅修改 privacy_* 字段，没有相册、应用锁或试用删除调用。真实覆盖安装后的四类数据保留仍待设备验收。 |
| 冷启动/撤回逻辑 | 当前版本记录有效，旧版重新告知；撤回进程门禁不可由旧读结果恢复，控制器进入重启流程；真实跨进程重启尚未执行。 |
| SDK 显式初始化门禁 | Application、ThirdPartySdkInitializer、友盟/Bugly 初始化使用统一门禁；微信准备/发送/回调受保护；事件上报必须已初始化且门禁有效。通过源码及 Fake 门禁测试，不代表实际 SDK 全部内部行为已验证。 |
| 撤回竞态 | 迟到的同意读取、微信回调、登录成功和试用响应不能重新放行。源码中的 SDK 初始化与撤回串行控制、事件保护区已检查。 |
| 自有试用上传关闭 | 实际生成 Debug/Release BuildConfig 的 TRIAL_DEVICE_UPLOAD_ENABLED 均为 false，设备试用 sync 在读取身份前短路。按当前自有试用业务路径不会主动调用 activate 上传 SSAID；不是抓包结果，不覆盖第三方 SDK 自身的标识处理。 |
| 未来上传门禁 | 测试中启用 Fake 开关：旧版同意零设备标识读取、零 API 请求；有效同意后按既有试用规则处理；撤回后迟到响应不确认状态。正式构建开关未开启。 |
| 连续试用规则 | DeviceTrialTest 27 项通过；首次/重复启动、冷启动、重启、elapsedRealtime、前后调钟、网络失败、到期、上限取小值及账号隔离回归通过。重新同意不重置起点已测；拒绝/撤回无重置调用的静态路径确认。真实设备存储保留尚未验证。 |
| 无障碍依赖 | 主源码未发现 AccessibilityService、事件/窗口读取配置或绑定权限；最终 Manifest 无无障碍服务。应用锁仍为 UsageStats + specialUse 前台服务 + 悬浮窗，未改变实现。 |
| Manifest 与依赖 | 最终 Release APK 10 项 uses-permission，与清单逐项核对；AD_ID/MSA 不存在；SDK 实际解析版本准确。 |
| 文档包内一致性 | Debug、Release 中五份 legal 资产与源码逐字节一致；五份日期/政策版本均为 2026-10-28，主体、邮箱、答复口径一致。会员/支付/儿童机制现状披露与当前源码一致，但通知描述仍有下述缺口。 |
| SDK 显式自启动组件 | 最终 Manifest 的 provider 只有 androidx.startup.InitializationProvider，未出现友盟/Bugly/微信的专属自动初始化 provider。此为 Manifest 结构通过，不能据此断言不存在其他 SDK 静态加载/线程/标识读取行为。 |

现有普通用户同意不代表有效监护人同意。C1.2 已如实披露儿童机制尚未实现，这一“披露一致”通过不表示儿童产品保护已完成。

## 三、未通过项目/静态缺口

### F-01 [P2] Release 仍输出前台及受保护应用包名

证据：

- `app/src/main/java/com/aurora/calculatorvault/feature/applock/service/AppLockMonitorService.kt:172`：foreground changed 的旧/新包名，发生在保护列表筛选之前，涉及观测到的其他前台应用。
- 同文件第 180、183 行输出匹配的受保护包和开始验证目标。
- `app/src/main/java/com/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController.kt:64`、194 行等输出悬浮窗/临时解锁目标包名。
- 这些 Log.d 没有 BuildConfig.DEBUG 门禁；Release isMinifyEnabled=false。最终 Release APK 的 classes*.dex 仍含 foreground changed: old=、locked target matched=、overlay show requested= 字符串，见 verification.json。

影响：可在诊断日志形成一段被观察到的应用切换和保护目标序列，超出必要状态/计数日志。不能把这说成记录了所有时刻的完整历史，也未证实 SDK 已上传这些日志；普通第三方应用不能被一概假定可读取任意 Logcat。这里确认的是 Release 日志最小化缺口。

原因：应用锁调试日志未从正式路径隔离。

建议：后续经授权修复时移除包名/目标信息或在 Debug 下提供最小、脱敏诊断；重新检查 Release DEX 和真机日志，不以补写宽泛政策作为首选解决方案。本次不改代码。

Android 官方日志风险说明：https://developer.android.com/privacy-and-security/risks/log-info-disclosure 。

### F-02 [P2] 通知授权流程与文案尚未闭环

证据：

- Manifest 声明 POST_NOTIFICATIONS，targetSdk=37。
- `app/src/main/assets/legal/permission_description.txt:55` 描述 Android 13 及以上“由系统运行时提示决定”。
- 全量主源码检索 POST_NOTIFICATIONS、RequestPermission、通知设置跳转等，未找到应用内针对 POST_NOTIFICATIONS 的请求/结果处理入口。现有 RequestPermission 调用是旧系统媒体写权限。
- AppLockMonitorService 创建通知渠道和调用 startForeground，但这不等价于为 target 33+ 主动请求通知许可。

影响：目前无法沿应用自身操作闭环“弹出通知请求 → 允许/拒绝”。文案可被理解为当前会提供运行时提示，而实现没有对应入口。可在系统设置或受控权限操作下测试允许/拒绝，但不能据此判定应用内授权流程已经存在。

这是静态告知/流程缺口，**未据此判定应用锁或前台服务在真机必然失败**。建议产品明确采用系统设置管理还是应用内按需请求；如仅系统设置管理，准确调整说明；如后续增加请求入口，再验证允许、拒绝及永久拒绝，不增加新权限。此次只记录。

Android 官方通知权限说明：https://developer.android.com/develop/ui/compose/notifications/notification-permission 。其中 target 33+ 的提示时机由应用请求流程控制；拒绝通知不意味着所有 FGS 都不能运行。

## 四、尚未验证项目（不能当作通过或自动判失败）

| 项目 | 尚未验证的原因/内容 |
|---|---|
| 首次/升级/拒绝/重启真实 UI | 无设备；尚未点击实际页面、检查覆盖安装后的路由、文字可读性和跨进程返回。 |
| 私密数据保留 | 无设备；仅规则、Fake 和静态写集合通过，未对真实 PIN、媒体文件、应用锁配置、试用密文做升级前后快照核验。 |
| SDK 真实自启动/标识访问 | 未抓取实际进程加载、SDK 初始化、标识 API 或网络行为；Manifest/显式调用检查不覆盖所有 SDK 内部路径。 |
| 同意后友盟初始化/事件送达 | 未运行真实 SDK、未检查友盟端实际接收。Fake 门禁计数不代表统计服务器收到事件。 |
| 撤回后 SDK 无重启/补发 | 业务门禁、迟到结果逻辑通过；SDK 自动线程、缓存及网络实际行为未测。需区分新产生/排队事件与撤回前已经在途的请求。 |
| 移除 AD_ID/MSA 后 SDK 兼容 | 构建、门禁回归通过，不等同厂商 ROM 和真实 SDK 运行无异常；需在目标系统/厂商验证。 |
| 自有试用实际网络无上传 | 当前开关和业务路径验证为关闭，但尚未进行按应用归属的抓包；不能把其他 SDK 请求误归因于自有试用接口。 |
| Usage Access/悬浮窗/前台服务 | 只检查了代码、Manifest 和逻辑测试；特殊授权开启/撤回、覆盖层、后台系统限制未实机执行。 |
| Android 13+ 通知允许/拒绝 | 无设备，另有 F-02；需在受控系统状态下验证通知抽屉、任务管理器及 FGS 行为，不能将拒绝通知等同全部服务停止。 |
| 真实微信授权 | 本次生成的 Debug/Release 均未配置微信 AppID，无真实微信授权；门禁/Fake 回调已测，真实 SDK E2E 未通过验收。 |
| 儿童及后台运营措施 | 年龄分流/有效监护人机制尚未实现，生产数据留存、删除、部署和邮件权利执行属于产品/运营待确认；本任务不新增机制或认证其合规。 |

撤回保存失败会明确显示失败：当前进程门禁关闭并停用 SDK，但磁盘写入未完成不能当作持久撤回成功；强制重启仍可能读到先前有效记录。该既定失败语义与成功撤回后重入流程分别验收。

## 五、自动化测试及构建结果

本次使用 --rerun-tasks 强制重新执行，非复用旧的测试成功标记：

```powershell
$env:ANDROID_HOME='D:\AndroidSDK'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:generateReleaseBuildConfig :app:dependencies --configuration releaseRuntimeClasspath --rerun-tasks --console=plain
```

构建成功，104 项 Gradle 任务执行。JVM **320 项通过，失败/错误/跳过均为 0**，包括：

| 测试类 | 数量 | 失败 |
|---|---:|---:|
| PrivacyConsentTest | 16 | 0 |
| PrivacyWithdrawalControllerTest | 3 | 0 |
| StartupDestinationResolverTest | 10 | 0 |
| OnboardingRepositoryTest | 6 | 0 |
| OnboardingViewModelTest | 8 | 0 |
| DeviceTrialTest | 27 | 0 |
| AccountSessionTest | 16 | 0 |
| VaultSessionManagerTest | 11 | 0 |
| AppLockSessionManagerTest | 6 | 0 |
| AppLockRepositoryImplTest | 3 | 0 |

其余既有回归一并计入320项。此处为 JVM/逻辑测试，不是320项设备/UI/真实SDK测试。设备测试仅编译，未执行 connectedDebugAndroidTest，以免把“无设备”误记为功能失败或通过。

Debug APK 和未签名 Release APK 均重建成功；Release lintVital 通过。未进行生产签名/发布，未安装未签名包，未调用 AuroraAppServer、会员或生产支付接口。Fake HTTP 测试只使用本机随机端口。

## 六、最终 Manifest、SDK 与文档检查

最终 Release APK 实际 uses-permission（aapt dump permissions，10项）：

1. android.permission.INTERNET
2. android.permission.ACCESS_NETWORK_STATE
3. android.permission.ACCESS_WIFI_STATE
4. android.permission.PACKAGE_USAGE_STATS
5. android.permission.SYSTEM_ALERT_WINDOW
6. android.permission.FOREGROUND_SERVICE
7. android.permission.FOREGROUND_SERVICE_SPECIAL_USE
8. android.permission.POST_NOTIFICATIONS
9. android.permission.WRITE_EXTERNAL_STORAGE（maxSdkVersion=29）
10. com.aurora.calculatorvault.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION（signature 内部广播保护）

另有该 signature 权限自身的声明，不是第11项访问权限。AD_ID、freemme.permission.msa 和无障碍服务均不存在；queries 为 MAIN/LAUNCHER 与微信可见性。AppLockMonitorService 为非导出、specialUse、app_lock_foreground_monitor。Release allowBackup=false、usesCleartextTraffic=false。

releaseRuntimeClasspath：友盟 Common9.9.8、ASMS1.8.7.2、Bugly4.1.9.3、微信 Android6.8.40。SDK目录与主政策实填一致，无微信占位版本。SDK版本相符不表示已认证其所有动态行为。

五份文件的日期/政策版本均为2026-10-28、公司/邮箱/15个工作日口径一致，两种APK内对应资产均与源码逐字节一致。UsageStats 说明未声称只读取受保护应用；服务端会员已实现、Android真实会员查询/恢复未正式启用、支付未上线、儿童同意机制未实现均如实区分。文档整体准确性仍保留 F-02，不给出无条件全部一致结论。

## 七、必须人工执行的最小真机清单

以下均未执行，建议至少一台 Android 13+ 专用测试设备，并补一台目标厂商 ROM 检查 SDK 兼容；使用无真实私密数据的测试环境。

1. **全新安装/拒绝**：首次不操作、仅看完整政策、点击拒绝再重开；检查正确告知、无旧同意放行，记录SDK初始化/标识访问/按应用归属的网络行为。不要将构建工具下载或系统请求算作App请求。
2. **旧版本升级/保留数据**：使用同签名受控旧包建立1.0或2026-10-27同意、测试PIN/照片/视频/应用锁配置及已消耗试用；覆盖安装新包。先拒绝并重开，再明确同意；核对新版本/时间和四类数据快照，试用起点不重置，PIN仍需验证。冷启动后有效同意应被识别。
3. **SDK正向与权限移除兼容**：有效同意后验证友盟真实初始化及一条允许事件送达、Bugly基础初始化无明显异常；检查去除AD_ID/MSA后的目标ROM日志和网络。当前无微信AppID，只能验收未配置提示及不越过同意门禁，真实微信另行安排。
4. **撤回/重入**：同意后撤回，检查SDK停用、不接受撤回后的新事件或旧异步结果、进程替换后重新告知；重新同意不清空本地数据或试用。区分撤回前已在途网络与撤回后新产生的请求。保持自有试用上传关闭。
5. **应用锁与通知矩阵**：分别关闭/开启使用情况访问和悬浮窗，打开一个受保护测试应用及普通应用；验证覆盖层、PIN、后台锁定、specialUse服务。Android13+在系统设置或受控权限状态下分别允许/拒绝通知，记录通知抽屉与任务管理器表现；同时确认 F-02 的请求入口处理方案，不用手工授权结果冒充应用内提示存在。
6. **日志及设备测试**：复现 F-01 后，在后续授权整改完成时确认Release不再输出包名序列。运行 connectedDebugAndroidTest 执行真实DataStore/Keystore和持久化测试。检测记录脱敏，不留真实Token/code/完整SSAID；禁止为此打开自有上传开关。

## 八、进入 I3.1 的建议

**当前建议：先不要进入真实 I3.1 联调。** 不是因为320项测试失败，而是两项静态缺口未闭环、核心真实SDK/权限/升级行为未执行。建议先由人工确认 F-01/F-02 的整改范围，再完成上述最小真机检查和明确儿童/运营待办的上线限制，之后重新作进入联调的决定。

真实微信 AppID 暂缺造成的E2E限制应单列，不通过假登录绕过；自有试用上传继续保持false，不把进入下一阶段作为自动开启上传的授权。此报告不宣称整个应用已完成法律合规认证。

## 九、证据位置

`evidence/task-c1.3/`：

- build-and-dependencies.log：本轮强制重跑构建/测试及解析依赖。
- verification.json：320项测试与各测试类、包内文档比对、APK摘要、Release日志字符串、最终权限及源码无变化结果。
- build-flags.json：Debug/Release上传关闭、微信AppID未配置的布尔结果，不输出标识值。
- Release-AndroidManifest.xml、release-apk-permissions.txt：合并Manifest和APK实际权限。
- source-baseline.json：验收前文件摘要，验收后已逐项比对无变更。

最终产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release-unsigned.apk`。摘要见verification.json；未签名Release不作为可直接安装正式包。

C1.3验收记录完成，停止等待人工确认；没有自动修复缺口、修改业务、提交代码或进入I3.1。
