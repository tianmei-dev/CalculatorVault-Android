# CalculatorVault · Task C1.2 隐私文档及 Manifest 一致性修复交付

## 一、结果和范围

已完成五份文档、Manifest 和政策版本一致性修复。Debug 和 Release 构建成功，320 项 JVM 回归全部通过；设备测试源码编译通过，尚未连接真机执行。

不改变计算器密码、私密相册、UsageStats 应用锁实现或 72 小时试用规则，不新增无障碍、不启用 Android ID 上传、不接入会员查询联调/支付/注销，不修改 AuroraAppServer，不自动继续 C1.3。C1.1 的未提交改动以及用户此前的文档修改在其基础上保留；本次未自动提交或推送。

## 二、本次修改文件

1. `app/src/main/assets/legal/privacy_policy.txt`：纠正应用锁、权限、会员现状、儿童机制和 SDK 标识边界，实填微信版本。
2. `app/src/main/assets/legal/personal_information_list.txt`：补充实际 UsageStats 读取范围，区分服务端会员数据与 Android 未启用查询、支付未上线。
3. `app/src/main/assets/legal/third_party_sdk_list.txt`：微信 Open SDK Android 6.8.40，SDK 权限移除及仍可能处理标识的边界。
4. `app/src/main/assets/legal/permission_description.txt`：按最终权限和真实服务用途整理，移除现有无障碍服务指引，准确列出 specialUse/通知/使用情况访问/悬浮窗。
5. `app/src/main/assets/legal/children_privacy_policy.txt`：不将普通同意写成监护人同意，明确当前缺少年龄分流和有效监护人同意机制，将相关要求标记为后续落实。
6. `app/src/main/AndroidManifest.xml`：以精确权限名和 tools:node="remove" 排除两项友盟合入权限。
7. `app/src/main/java/com/aurora/calculatorvault/core/privacy/PrivacyPolicy.kt`：当前政策版本同步为 `2026-10-28`，继续使用 C1.1 精确匹配及重新同意规则。无其他业务代码改动。
8. 本交付说明及 `evidence/task-c1.2/` 证据文件。

本次五份政策更新/生效日期统一为 2026-10-28，新增明确“政策版本：2026-10-28”。因文档内容改变，同步唯一版本常量，避免用同一版本对应两套内容；已同意 2026-10-27 或 1.0 的用户须经 C1.1 流程重新选择，不重置本地数据或试用起点。

## 三、最终 Release 权限清单

已同时核对重新生成的合并 Manifest 和最终 Release APK 的 aapt dump permissions；两项已移除权限不在产物中。

| 最终 uses-permission | 当前用途/边界 |
|---|---|
| android.permission.INTERNET | 微信登录、会话维护、符合门禁的试用及统计/异常通信；真实会员查询未在 Android 正式启用 |
| android.permission.ACCESS_NETWORK_STATE | 网络状态/恢复观察；不绕过同意或上传开关 |
| android.permission.ACCESS_WIFI_STATE | Wi-Fi 联网环境及 SDK 可能需要的连接状态，不为读取 Wi-Fi 密码 |
| android.permission.PACKAGE_USAGE_STATS | 用户主动开启特殊使用情况访问后查询近期应用使用状态，再本地筛选保护目标 |
| android.permission.SYSTEM_ALERT_WINDOW | 应用锁验证悬浮窗，需特殊授权 |
| android.permission.FOREGROUND_SERVICE | 应用锁前台监测服务运行声明 |
| android.permission.FOREGROUND_SERVICE_SPECIAL_USE | AppLockMonitorService 的 specialUse 类型；用途 app_lock_foreground_monitor |
| android.permission.POST_NOTIFICATIONS | Android 13+ 应用锁持续状态通知；拒绝不等同于停止所有前台服务 |
| android.permission.WRITE_EXTERNAL_STORAGE | maxSdkVersion=29，仅旧系统必要媒体恢复/保存场景 |
| com.aurora.calculatorvault.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX 自定义 signature 内部广播保护；不是设备信息读取权限 |

此外，Manifest 声明上述 signature 权限本身；它不属于新增危险权限。queries 保留 MAIN/LAUNCHER 与 com.tencent.mm 包可见性；不申请 QUERY_ALL_PACKAGES。Release allowBackup=false、usesCleartextTraffic=false，未加入无障碍服务或其绑定权限。

## 四、AD_ID 与 MSA：均移除

移除项：

- `com.google.android.gms.permission.AD_ID`
- `freemme.permission.msa`

核对依据与取舍：

- 真实合并来源均为 `com.umeng.umsdk:common:9.9.8`，非应用主 Manifest 主动业务需求。
- 业务代码仅初始化基础统计并上报无业务参数事件 ID，没有广告投放、广告归因、Google 广告 ID 或厂商 MSA 标识业务调用；微信会话和设备试用匹配不依赖这两项权限。自有试用使用 SSAID，且上传仍默认关闭。
- 核查 Common 9.9.8 本地 AAR/DeviceConfig：存在标识字段开关、空/缓存返回和部分异常处理路径，未将标识可用性作为本应用业务必须条件。此类静态检查不等于所有设备和 SDK 线程路径的无崩溃证明。
- 因当前业务无需这两项访问能力，采用 Manifest 合并规则精确移除，不为保留权限虚构广告用途，不额外引入 SDK 或权限。SDK 版本和初始化配置不变，C1.1 同意门禁继续有效。
- 可能影响 SDK 在部分系统上的标识可用性或设备统计精度，需真机验证基础统计初始化及事件兼容性。当前没有连接设备，不承诺已完成 SDK 动态验收。

合并来源报告中友盟对应条目显示 REJECTED；最终 APK 权限表无这两项。没有因为权限曾存在就认定已实际采集，也没有因为移除就宣称所有 Android 版本/厂商的 SDK 无法处理任何标识。文档保留 Android ID、OAID、OpenUDID、GUID 的条件性披露；关闭自有试用上传也不等于 SDK 所有标识处理均关闭。

技术参考：

- Android 官方 Manifest 合并与移除规则：https://developer.android.com/build/manage-manifests
- Google 官方 AD_ID 与 target 33+ 访问要求：https://developers.google.com/android/reference/com/google/android/gms/ads/identifier/AdvertisingIdClient.Info

这些资料支持权限机制说明，不构成友盟所有 ROM 兼容性的保证。

## 五、五份文档一致性核对

| 项目 | 核对结果 |
|---|---|
| 名称/主体 | 应用加密计算器；四川极光软件科技有限公司 |
| 联系方式/答复口径 | 594841675@qq.com；完整有效请求 15 个工作日内答复，保留法律另有规定的例外 |
| 政策版本和日期 | 五份统一为 2026-10-28；C1.1 当前版本常量同步；旧版记录不静默放行 |
| 应用锁 | 使用情况访问 + specialUse 前台服务 + 悬浮窗，明确本版本未接入无障碍，无不存在的服务开启/关闭指引 |
| UsageStats 范围 | 近期使用事件、事件类型、包名；必要时最近使用时间回退，系统可能返回未保护应用；本地筛选，不声称系统只返回保护列表 |
| 查询与管理记录 | 不保存完整监测历史/上传识别结果；用户主动启动的本地最近打开时间和次数另作管理记录，避免与前者混淆 |
| SDK | Common 9.9.8、ASMS 1.8.7.2、Bugly 4.1.9.3、微信 Android 6.8.40，主政策与 SDK 目录一致，其余文件引用目录 |
| 标识/权限 | 两项 SDK 权限已移除；不据此断言零标识处理；SSAID 原始上传与服务端 HMAC 摘要分别说明，上传默认关闭 |
| 会员/支付 | 只读确认 Server 已有会员接口；Android 真实查询/恢复尚未完成联调并未正式启用；当前无真实权益缓存，套餐展示不是权益已开通；支付未上线 |
| 儿童保护 | 普通同意不等同有效监护人同意；年龄分流和可核验同意未实现，明确后续产品/运营待办，不新增身份证或人脸收集 |
| 其他规则 | 退出账号不删除私密数据、不重置设备试用；无云同步；邮件注销仍是既定人工渠道，不开发新入口 |

实际应用锁保留约 250ms 轮询、约最近 5 秒事件窗口；UsageStats 回退结果范围受系统实现影响。文档没有把这个查询窗口包装成只能看见受保护应用或只能获取这 5 秒所有统计结果的保证。没有读取 UI 节点、聊天或其他应用密码输入的新增能力。

## 六、编译、回归及包内核验

执行通过：

```powershell
$env:ANDROID_HOME='D:\AndroidSDK'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:dependencies --configuration releaseRuntimeClasspath
```

最后完成文案校对后重新执行 assembleDebug/assembleRelease，确保产物含最终文本；随后逐字节比对两种 APK 中的五份 legal assets 与当前源码，全部一致。

- JVM：**320 项，通过 320；失败、错误、跳过均为 0**。隐私版本迁移/撤回、登录、试用和应用锁现有回归继续通过。
- SDK 实际解析：微信6.8.40、Common9.9.8、ASMS1.8.7.2、Bugly4.1.9.3。
- Debug 构建成功；Release 构建及 lintVitalRelease 成功，Release 为未签名包，未进行生产签名/发布。
- 设备测试仅编译通过，adb 无连接设备；未安装、未进行真实 SDK 动态采集或 ROM 兼容检测。
- Debug/Release 生成的 TRIAL_DEVICE_UPLOAD_ENABLED 均为 false。
- 公司、联系方式、版本/日期、SDK 实填版本、无旧微信占位文字已做静态一致性核验。
- AuroraAppServer 工作区无修改；未发起会员联调或生产业务请求。

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`（未签名，不直接当作可安装正式包）

证据：`evidence/task-c1.2/` 下的 build-and-dependencies.log、final-package.log、Release-AndroidManifest.xml、manifest-merger-release-report.txt、release-apk-permissions.txt、verification.json。verification.json 含权限、320 项测试汇总和 APK SHA-256。baseline-hashes.json 保留本次开始前文档/Manifest/版本文件摘要。

## 七、仍需人工确认及验收步骤

1. 连接专用测试设备：覆盖安装旧政策版本，确认先重新告知、完整文档可读；拒绝不初始化受保护 SDK，同意后基本统计与异常监控正常。检查不清除原密码/媒体/应用锁/试用记录。
2. 在不同 Android 版本及厂商 ROM 上验证移除 AD_ID/MSA 后友盟初始化、基础事件与拒绝/撤回流程；必要时结合官方支持确认，不能用 JVM 测试代替厂商标识访问路径验证。
3. 使用受控测试数据动态核验标识、SDK 请求和日志，特别是 Android 13 以下及厂商 OAID 路径；本次移除权限并非 SDK 全标识禁用配置，不宣称绝对零采集。不要保存真实 Token、微信 code 或完整设备标识到检测报告。
4. 运行 connectedDebugAndroidTest，并人工检查应用锁的使用情况访问、悬浮窗、持续通知及通知拒绝场景；不启用无障碍或自有试用上传。
5. 儿童产品策略、有效监护人同意/权利请求流程仍由产品和合规负责人决定并落实；当前文本如实标记尚未完成，不代表儿童合规问题已解决。本次不开展下一任务。
6. 正式上线前核实生产部署地点、服务器日志中的原始 SSAID、数据保留/删除期限、邮件权利及注销请求的运营可兑现性。仅改文档不能证明后台运营安排已经落实。
7. Android 真实会员查询/恢复联调及支付上线后的数据处理另行验收；本次只区分现状，不承诺已具备这些 Android 能力。
8. 审阅本次政策日期和生效安排；当前版本常量与包内五份文档均已同步。如最终发布文本再变化，应按 C1.1 统一版本规则处理，不只替换资产而绕过重新告知。

C1.2 完成，停止等待人工验收，不自动开始 C1.3，不自动提交推送。
