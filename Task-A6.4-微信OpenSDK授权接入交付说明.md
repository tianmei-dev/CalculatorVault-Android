# CalculatorVault · Android Task A6.4

## 范围

仅实现官方微信 Open SDK 集成、授权发起、回调验证及授权 code 的一次性 Server 入口交接。没有调用 AuroraAppServer，没有 Token、真实账号登录、会员恢复、试用绑定或支付。

## 开发前检查

- Android 单 app 模块，AGP 9.1.1、Kotlin Compose 插件2.2.10、Compose BOM 2025.03.01、Navigation 2.8.9，minSdk 26 / compileSdk、targetSdk 37。
- applicationId 与 namespace 均为 `com.aurora.calculatorvault`，实际产品名仍为“应用加密计算器”。
- 原登录入口由 LoginPromptViewModel + WeChatLoginDialog 统一承载，仅有 UI 占位；没有微信 SDK、WXEntryActivity、真实账号/Token 存储或 Server 登录客户端。
- A6.3 AccountUiState 为展示模型，默认游客；本次没有将微信授权状态写入该模型。
- Debug 使用 Android 调试签名。Release 未配置 signingConfig，本次没有修改证书、签名策略或 applicationId。
- 原隐私会话在进入后台时锁定。微信授权不享有解锁豁免，回到 App 后可能需要正常验证计算器密码，本次没有修改锁定策略。

## 文件清单

修改：

1. `gradle/libs.versions.toml`：固定官方微信 SDK 版本。
2. `app/build.gradle.kts`：SDK 依赖、配置化 WECHAT_APP_ID 与 BuildConfig 字段。
3. `app/src/main/AndroidManifest.xml`：定向查询 com.tencent.mm；注册导出的 WXEntryActivity。
4. `app/src/main/java/com/aurora/calculatorvault/app/CalculatorVaultApp.kt`：App 级授权协调器和延迟初始化 SDK；撤回隐私时取消授权并释放 SDK。
5. `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/LoginPromptViewModel.kt`：保留原协议校验、首次弹窗逻辑，替换授权占位分支，关闭/取消勾选时作废请求。
6. `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/WeChatLoginDialog.kt`：仅添加授权中按钮禁用及结果提示；视觉布局、协议链接及暂不登录入口保留。
7. `app/src/main/java/com/aurora/calculatorvault/feature/vault/presentation/VaultMainScreen.kt`：依赖注入、隐私同意检查、授权状态收集与超时刷新。
8. `app/src/test/java/com/aurora/calculatorvault/feature/login/LoginPromptTest.kt`：更新原占位断言；增加协议阻断 SDK、重复发起和成功交接不关闭登录弹窗等测试。

新增：

1. `app/src/main/java/com/aurora/calculatorvault/feature/login/data/AndroidWeChatSdkBridge.kt`：IWXAPI 的 applicationContext 实例、注册、发送、handleIntent、释放及 SDK 日志静默处理。
2. `app/src/main/java/com/aurora/calculatorvault/feature/login/data/SharedPreferencesWeChatAuthorizationStore.kt`：仅保存短期 state 和创建时间。
3. `app/src/main/java/com/aurora/calculatorvault/feature/login/domain/WeChatAuthorizationCoordinator.kt`：授权状态机、重复请求防护、随机 state、超时和一次性消费。
4. `app/src/main/java/com/aurora/calculatorvault/feature/login/domain/WeChatServerLoginEntry.kt`：S2.1 契约、code 请求对象和无网络占位交接实现。
5. `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/WeChatAuthorizationMessages.kt`：结果提示资源映射。
6. `app/src/main/java/com/aurora/calculatorvault/wxapi/WXEntryActivity.kt`：官方约定路径的回调 Activity。
7. `app/src/main/res/values/strings_wechat_auth.xml`：缺少 AppID、微信不可用、取消、失败、超时和 code 已交接等提示。
8. `app/src/test/java/com/aurora/calculatorvault/feature/login/WeChatFakes.kt`：单元测试专用 SDK/存储替身。
9. `app/src/test/java/com/aurora/calculatorvault/feature/login/WeChatAuthorizationTest.kt`：授权协议、失败、重放、恢复、并发回调等单元测试。

已有 A6.1～A6.3 交付报告的删除在开始任务时已经存在，本次未恢复或改动这些删除。会员页、设置 UI、账号展示模型、密码及保险箱会话实现没有修改。

## SDK 与 AppID 配置

依赖固定为：

```kotlin
implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.40")
```

来源：[官方 Maven 发布元数据](https://central.sonatype.com/artifact/com.tencent.mm.opensdk/wechat-sdk-android)。使用项目已有仓库配置，无动态 `+` 版本、无第三方封装库。

AppID 读取顺序：Gradle 属性 WECHAT_APP_ID → 同名环境变量 → 空字符串。支持在本机 Gradle 用户配置中填写，或通过 `-PWECHAT_APP_ID=实际移动应用AppID` 构建。该配置适用于本次 Debug 和 Release。

未提供真实 AppID，本次默认留空。点击已同意协议的登录按钮会提示“微信登录尚未配置 AppID，请联系开发者”，不生成假 AppID。非空 AppID 必须符合 wx + 16位十六进制字符，格式错误会给出明确构建错误。Android 端只有 AppID 配置，没有 AppSecret 字段。

IWXAPI 由 Application 所持有的桥接层延迟创建，使用 applicationContext；不会持有 Activity。只有用户同意应用隐私和登录协议并主动点击，或已有合法 pending 请求的回调到达且隐私同意仍有效时，才触及 SDK。实例复用，成功注册不重复注册，失败时可释放重试。createWXAPI 开启微信签名检查。

## 授权与回调

1. 保留原 LoginPromptViewModel 的协议校验；未经同意不会初始化或调用 SDK。
2. 检查 AppID、SDK 初始化、微信是否安装且支持 API，注册 AppID；失败按类型显示提示。
3. SecureRandom 生成32字节随机数，编码为64位十六进制 state；先持久化，再发送 `SendAuth.Req(scope="snsapi_userinfo", state=...)`。
4. 请求在5分钟内有效。协调器拒绝重复发起，按钮显示“微信授权中…”并禁用；关闭弹窗始终可用。
5. WXEntryActivity 在 onCreate/onNewIntent 处理 intent，通过 SDK handleIntent 验证，且只接受 SendAuth.Resp。
6. 检查 pending 请求、有效期和 state；使用 MessageDigest.isEqual 比较。state 不匹配不会交接 code，也不会消费仍合法的请求；显式重试会生成新的 state。
7. state 匹配后先同步提交清除 pending，再区分成功、取消、拒绝和其他失败。清除失败时不交接 code。
8. 成功且 code 非空时，仅调用 WeChatServerLoginEntry.acceptAuthorization。占位实现不保留 code、不发网络、不产生会话。提示“已取得微信授权，服务端登录待接入，当前仍未登录”。
9. Activity 清除收到的授权 extras 并结束，不主动解锁保险箱或跳转进入保险箱。

pending 文件为应用私有 `shared_prefs/wechat_pending_authorization.xml`，只有 `state` 和 `created_at_millis` 两个字段；未写入 Room、DataStore 或授权 code。同步 commit 用于确保发起前持久化 nonce、交接前持久化消费记录。没有 code 的重试队列或结果缓存。

Activity 重建或进程被系统回收后，使用原 pending state 在有效期内校验。没有 pending、请求过期或未来时间戳时拒绝。已消费回调再次到达不会再次交接；若在清除 pending 后、Server 入口执行前进程退出，本次 code 会丢弃，需要重新授权，以避免持久化 code 或重复使用。

SDK 自身诊断日志使用静默 ILog；应用代码不打印 intent、BaseResp、code 或异常载荷。请求对象和 pending 对象的 toString 均脱敏。code 只在回调和同步入口参数的内存中传递。

## Server 预留入口

WeChatServerLoginRequest 声明：

```text
POST /api/v1/auth/wechat/login
X-App-Code: calculator_vault
Body: {"code": "授权code"}
```

WeChatServerLoginEntry 是 I2.1 的替换入口。当前 PendingWeChatServerLoginEntry 收到请求后直接返回，不调用 Server，不保存参数，不生成 Token。

I2.1 仍需接入真实网络请求、错误协议、Token 安全保存、账号状态刷新及会话处理。微信 access_token 交换只能在 Server 完成；客户端不得加入 AppSecret 或直接请求微信换 token。

## 签名注意事项

- 微信开放平台登记的移动应用 AppID、Android 包名和签名必须与安装包一致。
- 当前 Debug 使用调试证书，不能假设与原 Release APK 相同；换电脑或重建 debug.keystore 也可能改变调试签名。
- Release 尚未配置自动签名。本次没有生成、替换或读取私钥/密码。使用 Android Studio 或发布流水线签名时，应沿用已在微信开放平台登记的正式证书。
- 最终签名信息以本次 signingReport 和实际签名 APK 的证书为准；本次不是正式 Release APK 打包交付。

本次 signingReport：Debug MD5 为 `586af2f39e07e79740a97dc5ffca42fb`，Release 显示 `Config: null`。若平台登记的是另一份正式证书，不能直接使用当前默认 Debug 包验证微信授权。

## 自动化验证

最终命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileReleaseKotlin :app:signingReport --offline --console=plain
```

最终结果：BUILD SUCCESSFUL，47个测试套件、261项测试，0失败、0错误、0跳过。其中 WeChatAuthorizationTest 22项，LoginPromptTest 6项（保留原有4项并新增2项）。本次共新增24项测试。Debug APK、Release Kotlin 编译和 signingReport 均通过；不是完整 Release APK 构建。

首轮验证出现一次初始化异常分类不一致，修正并重新编译后完整测试通过。SDK AAR 和原有友盟 AAR 在首次 D8 转换时有 stack map table 警告，没有阻断最终构建；真实设备运行仍按下列步骤验收。

测试使用 SDK/存储替身，覆盖配置缺失、微信不可用、初始化/发送异常、正确 scope、随机 state、协议校验、防连点、取消/失败、空 code、state 不匹配、重放、并发回调、模拟进程恢复、超时、存储失败、关闭弹窗和隐私撤回。替身测试不能证明真实微信 SDK 已在设备上正常初始化或拉起。

日志：`artifacts/task-a6-4/build-final.log`。当前没有真实 AppID、签名匹配的微信测试配置及已验证的真机环境；没有宣称真实授权通过，也没有安装覆盖现有应用或绕过锁屏。ADB 本次启动失败，未执行设备端 UI 自动化。

另检查了合并 Manifest 中回调 Activity 的类名、exported、singleTask、taskAffinity 和 com.tencent.mm 查询声明；确认最终 Debug BuildConfig 的 AppID 为空。会员页、设置页及保险箱会话代码与任务开始时相比没有修改。静态检查不等同于设备端 UI 回归已通过。

## 真机人工验收

1. 默认不配置 AppID 构建：未勾选协议时仍显示原协议提示；勾选后显示明确 AppID 未配置提示；关闭/暂不登录正常，账号仍是游客。
2. 使用已审核、已开通微信登录能力的真实移动应用 AppID，并使用平台登记的对应证书签名。确认包名 `com.aurora.calculatorvault`，配置后重新构建安装测试包。
3. 在未安装微信的测试设备点击登录，检查“未检测到可用的微信”提示；不要为测试卸载日常使用设备的微信。
4. 在已安装并登录微信的设备点击登录，确认真实拉起微信授权页、按钮防重复点击。分别选择取消、拒绝，检查提示和可重试；账号仍为游客。
5. 同意微信授权：确认提示 code 已取得但服务端登录待接入。可在 Server 入口设置断点检查确实收到非空 code，不打印、不截图传播或保存该值。
6. 检查请求 scope 为 snsapi_userinfo，使用 Debug 调试器/测试替身验证错误 state、空 state、重复回调均不会重复交接。错误 state 后可显式重试。
7. 授权等待期间允许系统回收 App 进程，再从微信完成授权；5分钟内原请求可恢复校验。过期、清除应用数据、主动关闭弹窗或撤回隐私后应拒绝旧回调。
8. 检查返回后的原后台锁定规则：需要密码时正常验证，不因微信授权绕过保险箱。账号、VIP、试用状态均不得提前变化。
9. 回归首次弹窗一次性规则、协议链接和返回恢复、暂不登录、设置滚动状态及 VIP 购买页的套餐/支付/协议占位行为。
10. 检查正常字体、大字体下提示自然换行，SecureOn 保护仍生效；观察 Logcat 时不得出现完整 code。

## 后续事项

等待真实 AppID/微信能力审核与匹配签名后的人工验收；等待 I2.1 实际 Server 联调。正式上线真实登录前，还需要按最终处理的数据和 SDK 信息同步现有隐私政策、用户协议及第三方 SDK 目录；本任务没有擅自重写正式法律文本。

仅完成 A6.4，不继续开发 A6.5/A6.6、试用、支付或会员业务。
