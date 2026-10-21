# Task A6.1 微信登录弹窗 UI

本任务仅新增登录 UI、展示规则和交互占位；未接入微信 SDK、服务器、支付、游客账号或设备标识。

## 修改文件

| 文件 | 作用 |
| --- | --- |
| `app/src/main/java/com/aurora/calculatorvault/app/CalculatorVaultApp.kt` | 复用现有 DataStore，装配登录提示存储 |
| `app/src/main/java/com/aurora/calculatorvault/feature/vault/presentation/VaultMainScreen.kt` | 应用管理首页首次触发、统一承载弹窗、协议导航 |
| `app/src/main/java/com/aurora/calculatorvault/feature/settings/presentation/SettingsScreen.kt` | 添加账号分组与微信快捷登录入口 |
| `app/src/main/java/com/aurora/calculatorvault/feature/login/data/LoginPromptPreferences.kt` | 首次弹窗持久化及原子领取 |
| `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/LoginPromptViewModel.kt` | 统一弹窗来源、协议勾选、关闭和登录占位状态 |
| `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/WeChatLoginDialog.kt` | 可复用弹窗、可滚动内容、协议链接、关闭按钮 |
| `app/src/main/res/drawable/ic_wechat.xml` | 本地微信双气泡矢量图标 |
| `app/src/main/res/values/strings_login.xml` | 登录弹窗及设置入口文案 |
| `app/src/test/java/com/aurora/calculatorvault/feature/login/LoginPromptTest.kt` | 4 项状态与持久化测试 |
| `Task-A6.1-微信登录弹窗UI交付说明.md` | 本交付说明 |

## 组件与复用

`WeChatLoginDialog` 使用现有 AppColors、AppShapes、AppSpacing、AppTextStyles 和 VaultIconButton，与现有 VaultDialog 一样基于 Material3 AlertDialog。现有 VaultDialog 仅支持纯消息和双按钮，因此保留其现有接口，单独封装登录内容，不修改其他业务弹窗。

统一由 `LoginPromptViewModel` 管理状态，通过 `open(LoginSource.Settings)` 等调用打开。已支持 `FirstHome`、`Settings`、`MembershipPurchase`、`SessionExpired` 来源；本次仅连接前两项，后两项预留复用方式，未开发相关业务。

每次重新打开默认不勾选协议。未勾选点击按钮显示协议提示；勾选后点击仅展示“微信登录暂未开放，敬请期待”。后续 SDK 接入位置为 `requestWeChatLogin()` 中明确标注的占位分支，目前不存在真实登录状态的写入。

协议复用 `VaultTabRoute.LegalDocument` 与现有 `LegalDocumentScreen`。阅读时隐藏弹窗，返回后恢复本次弹窗和勾选状态；关闭弹窗后清空本次勾选状态。系统返回、点击外部和关闭按钮均可关闭。

## 首次展示记录

- 复用现有 `Context.vaultPreferencesDataStore`。
- 文件：应用私有目录 `files/datastore/calculator_vault_settings.preferences_pb`。
- Boolean 键：`login_first_home_prompt_shown`。
- 仅在成功进入保险箱内的应用管理首页后领取首次展示资格；以 DataStore edit 原子写入记录，再展示弹窗。
- 关闭、切换页面、切换 Tab、退出保险箱和冷启动均不重置记录。设置页主动打开不受此记录限制。
- I/O 写入失败时跳过自动提示，保障本地功能仍可使用。
- 未修改首次启动流程、密码校验、保险箱会话或后台锁定实现。弹窗窗口启用 SecureOn，保持私密页面窗口保护。

## 构建与测试

执行：`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --console=plain`。

Debug 构建和单元测试通过：44 个测试套件、216 项测试，0 失败、0 错误、0 跳过。其中新增登录测试 4 项，覆盖首次展示、关闭后不重弹、设置页重新打开时重置勾选、未同意协议阻止占位操作、重复首页事件不重置当前勾选，以及销毁并重建 DataStore/ViewModel 后仍不自动展示。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

本次未执行设备 UI 自动化或真机验收；以下交互和视觉效果需要人工确认。

## 人工验收步骤

1. 使用全新安装的测试环境，完成隐私协议确认和初始密码设置，确认原引导流程正常。
2. 输入正确的计算器密码进入应用管理首页，确认自动显示登录弹窗一次；确认标题、说明、微信图标、按钮、关闭按钮和协议文案。
3. 确认协议默认未勾选；直接点击微信登录应显示先同意协议的提示，弹窗仍保留。
4. 分别点击《用户协议》和《隐私政策》，确认打开对应现有协议页面，返回后恢复登录弹窗；协议阅读不会自动勾选。
5. 勾选协议后点击登录，确认只显示暂未开放提示，不出现已登录状态。
6. 关闭弹窗，切换各 Tab、进入二级页再返回、退出保险箱重新验证密码，确认不再主动弹出。
7. 结束进程后冷启动，再输入密码进入首页，确认不重复自动弹出。
8. 进入设置的“账号 → 微信快捷登录”，确认可重新打开同一弹窗，协议默认未勾选。
9. 分别验证关闭按钮、系统返回及点击弹窗外部均能关闭并继续使用。
10. 弹窗显示或阅读协议时切后台再返回，确认仍按原逻辑锁定并回到计算器；验证错误密码不能进入保险箱。
11. 在小屏幕及系统放大字体下，确认内容可滚动、文案可换行、按钮/协议/关闭入口可用；验证现有隐私文档和撤回入口仍正常。

仓库原有两个验收报告的删除及 `app/release/` 未跟踪内容未作改动。
