# Task A6.3 · 账号与会员状态展示 UI

## 修改文件

新增：

- `app/src/main/java/com/aurora/calculatorvault/feature/settings/presentation/AccountUiState.kt`：账号、会员、设备试用快照及展示文案映射。
- `app/src/main/java/com/aurora/calculatorvault/feature/settings/presentation/SettingsAccountContent.kt`：账号/会员条目、退出入口及确认弹窗。
- `app/src/main/res/values/strings_account.xml`：本任务全部文案。
- `app/src/debug/java/com/aurora/calculatorvault/feature/settings/presentation/AccountPreviewScenes.kt`：六种 Debug 专用数据。
- `app/src/debug/java/com/aurora/calculatorvault/feature/settings/presentation/SettingsAccountPreview.kt`：普通字号和1.6倍字号 Preview。
- `app/src/testDebug/java/com/aurora/calculatorvault/feature/settings/AccountUiStateTest.kt`：12项 Debug 单元测试，与 Debug 数据共用场景。

修改：

- `app/src/main/java/com/aurora/calculatorvault/feature/settings/presentation/SettingsScreen.kt`：在原账号分组中使用新展示组件，接收账号数据和退出回调。
- `app/src/main/java/com/aurora/calculatorvault/feature/vault/presentation/VaultMainScreen.kt`：传入退出确认后的占位提示回调，原登录与会员导航保持不变。
- `app/src/main/java/com/aurora/calculatorvault/ui/component/VaultContent.kt`：VaultSettingItem 增加默认关闭的 adaptiveHeight 参数，仅新账号/会员条目启用，以便多行和大字体撑高；其他调用保留原64dp固定高度。

## 状态模型与交互

AccountStatus 独立描述 Guest / WeChatLoggedIn，昵称为空、空白或未授权时回退到“微信用户”。

MembershipStatus 描述 NotSubscribed / TrialActive / TrialExpired / VipActive / VipPermanent。相关状态包含 planType、expiresAt（LocalDate）和 trialRemaining（Duration）；无关字段为空。月卡、年卡复用现有 VipPlan；永久会员不生成虚假到期日。日期显示 ISO YYYY-MM-DD，试用快照按完整天数和剩余小时展示，不读取系统时间。

AccountUiState 分别持有 account、accountMembership 和 deviceTrial。visibleMembership 在游客状态只读取 deviceTrial，避免显示此前账号的会员快照。设备试用不得填入付费会员。改变账号展示状态不会清除设备试用数据。这是展示隔离，不是会员鉴权或真实登出逻辑。

正式导航没有加载模拟状态，默认显示未登录、未开通VIP；不声称用户试用已过期，也不生成假试用剩余时长。额外的 NotSubscribed 状态用于明确表达尚无正式账号/试用数据。

未登录条目点击仍调用原 onLogin → LoginSource.Settings → A6.1 WeChatLoginDialog。会员条目始终调用原 onVipMembership → A6.2，来源仍为 Settings。没有新增 Tab 或账号独立页。

已登录时显示退出按钮，复用 VaultDialog。取消、点击外部及返回仅关闭确认；确定后关闭确认并调用 onLogoutConfirmed。正式导航当前只提示“退出登录功能待接入”，不修改账号、会员或任何本地私密数据。

设置页保留原 rememberScrollState 及导航返回栈；没有重置滚动状态的状态键或副作用。账号数据由 SettingsScreen 的 accountState 参数注入，不引入不必要的全局状态或模拟存储。

## Debug / Preview 使用

Android Studio 选择 Debug 构建变体，打开 SettingsAccountPreview.kt，刷新 Preview。AccountSceneProvider 为每个 Preview 提供六种场景：

| 场景 | 展示 |
| --- | --- |
| 游客试用中 | 微信登录；免费试用中；剩余2天5小时 |
| 游客试用到期 | 微信登录；未开通VIP；开通会员，继续使用高级保护功能 |
| 微信非VIP | 微信用户；已登录；开通VIP；退出登录 |
| 微信月卡VIP | 月卡用户；VIP会员；有效期至 2026-11-20 |
| 微信年卡VIP | 年卡用户；VIP会员；有效期至 2027-10-21 |
| 微信永久VIP | 永久用户；永久VIP；永久有效 |

同时提供360×740dp正常字体与360×640dp、1.6倍字体预览。可在已登录场景开启 Interactive Preview，检查退出确认的取消和确定。Preview 导航和登出回调为空，不会登录或写入会员状态；真实导航通过正式 Debug 包验收。

模拟数据和 Preview 均在 src/debug，没有用户可操作的切换开关、调试入口、额外 Activity 或 Manifest 变更。相关测试放在 src/testDebug，Release 单元测试不引用 Debug 类。

## 验证结果

执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileReleaseKotlin --offline --console=plain
```

BUILD SUCCESSFUL，耗时3分10秒。46个测试套件，237项测试，0失败、0错误；包含新增12项账号状态测试。覆盖六种状态、默认不生成试用/VIP、昵称回退、日期、试用天/小时拆分、游客隔离账号VIP、设备试用保留及非法状态约束。

检查 Release Kotlin 类目录，未发现 AccountPreviewScenes / SettingsAccountPreview 类。没有构建完整 Release APK。本次未运行设备端 UI 自动化，未进行真机或模拟器视觉验收。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 人工验收

1. 安装 Debug 包，正常完成现有密码验证，打开设置；账号区显示“微信登录 / 登录后可恢复会员权益”和“未开通VIP / 开通会员，继续使用高级保护功能”。
2. 点击微信登录，确认打开原登录弹窗；默认未同意协议，原协议校验、链接跳转及返回恢复正常，关闭后不出现真实登录状态。
3. 点击会员入口，确认进入现有购买页，套餐/支付选择、协议校验及支付占位正常。返回设置，检查滚动位置保持；也回归设置其他子页的返回位置。
4. 在 Debug Preview 中逐一查看六种状态，核对上表及昵称回退、会员有效期和永久有效。已登录场景点击“退出登录”，检查指定的标题、完整正文及“取消 / 确定退出”；两种操作均不得模拟真实登出或修改会员。
5. 查看1.6倍字体 Preview；在小屏、横屏及系统大字体的 Debug 包中检查设置可滚动、长文案自然撑高、触控区域可访问。
6. 回归密码修改、权限查看、隐私文档与撤回、首次登录弹窗及后台锁定，确认现有行为不变。

本次仅完成 A6.3。未接入登录/登出 SDK、账号/会员服务端接口、真实订单、设备标识、正式试用计时或会员权限拦截。
