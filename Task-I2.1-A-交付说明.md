# Task I2.1-A：Android 微信账号会话集成

## 范围

仅 Android 与既有 AuroraAppServer S2.1 会话接口集成。未修改 Server、Docker、数据库、applicationId、微信 AppID 配置、计算器密码、媒体加密、设备试用或应用锁数据。正式 APK 没有测试登录入口。微信 AppID 尚未通过，真实授权及真实签发 E2E 属于 I2.1-B。

## 文件清单与网络结构

- `app/build.gradle.kts`：Debug/Release API URL、按变体控制明文、JVM JSON 测试依赖。
- `app/src/main/AndroidManifest.xml`：变体明文策略；原有 allowBackup=false 保持。
- `feature/login/session/HttpAuthApi.kt`：AuthApi、DTO、平台 HttpURLConnection，IO dispatcher、10 秒连接/15 秒读取超时、64K 响应上限、禁用重定向，无日志、无业务自动重试。
- `feature/login/session/KeystoreTokenStore.kt`：TokenStore、Android Keystore AES-GCM 与 noBackupFilesDir 原子密文文件。
- `feature/login/session/AccountSessionRepository.kt`：Application 范围唯一会话来源、恢复/校验/退出、操作版本与到期计时。
- `app/CalculatorVaultApp.kt`：手动依赖注入、隐私同意后恢复、前台重新校验、撤回隐私时清除账号会话。
- `feature/login/domain/WeChatServerLoginEntry.kt`：保留一次性 code 交接接口，移除生产 Pending 空实现。
- `feature/login/presentation/LoginPromptViewModel.kt`：取消登录联动；首次弹窗、跳过和协议校验沿用。
- `feature/login/presentation/WeChatLoginDialog.kt`：显示服务端登录、失败、存储异常等状态；正在登录时禁用重复点击。
- `feature/settings/presentation/SettingsScreen.kt`：待验证说明、重新校验与本地退出。
- `feature/vault/presentation/VaultMainScreen.kt`：会话映射账号 UI、真实退出、登录成功关闭弹窗、撤销失败提示。
- `res/values/strings_wechat_auth.xml`：移除“服务端待接入”文案。
- `app/src/test/.../session/AccountSessionTest.kt`：会话替身与本机 Fake HTTP Server 测试。
- 原 `WeChatFakes.kt` / `LoginPromptTest.kt`：Pending 空实现仅留在测试源码，既有授权回归仍可运行。
- `app/src/androidTest/.../session/KeystoreTokenStoreTest.kt`：设备 Keystore 冷读、密文检查、篡改拒绝、退出清理。
- `.gitignore`：忽略本机临时构建工具目录。

上述 feature 路径位于 `app/src/main/java/com/aurora/calculatorvault/`。

## Server 契约核对（只读真实源码）

核对 `auth/web/AuthController.java`、WechatLoginRequest/LoginResponse/MeResponse、`common/api/ApiResponse.java`、ApiErrorCode、BearerTokenAuthenticationFilter、AuthSessionService、AuroraAuthProperties 及 application.yml。

共同请求头 `X-App-Code: calculator_vault`。响应信封 `{code: int, message: string, data: object|null}`，仅 HTTP 2xx 且 code=0 视为成功。

| 接口 | 请求 | 成功 data |
|---|---|---|
| POST /api/v1/auth/wechat/login | JSON `{code: string}` | accessToken:string、tokenType:"Bearer"、expiresAt:ISO Instant、userId:Long、appCode:string |
| GET /api/v1/auth/me | Authorization: Bearer token | userId:Long、appCode:string、status:"ACTIVE" |
| POST /api/v1/auth/logout | Authorization: Bearer token，无 JSON 请求体 | null |

登录及 /me 的 appCode 必须等于 calculator_vault。/me 的 ACTIVE 与真实 userId 才构成已认证身份。没有头像、昵称、会员或 refresh token 字段。

40100/40101/40102 分别为未授权、到期、撤销；HTTP 401 无论信封能否解析都清会话。40300/40301 为禁止/应用上下文不匹配，40001/40002 为应用不存在/禁用，也停止本地会话。40005/40006 为微信 code 无效/微信认证失败；50000/50001 为内部错误/微信配置缺失。请求超时、无网、5xx 或格式异常不把已保存会话当作确认退出，而转待验证。

Bearer Filter 校验原始 Token 的 hash、撤销、到期、Token 所属 appId 与 X-App-Code 所解析 appId、用户 ACTIVE 状态；跨 App 返回 APP_CONTEXT_MISMATCH。Android 的隔离测试验证客户端拒绝该响应及不匹配 appCode，不替代 Server 自身真实隔离测试。

## Token 与会话

密钥：AndroidKeyStore 中 AES 密钥，AES/GCM/NoPadding，每次写入新随机 IV；文件只保存 IV + 认证密文，内容为 expiresAt 与应用 Token。无 SharedPreferences/Room Token，无微信 code 持久化。密文和退出标记均放 noBackupFilesDir，且应用原有 allowBackup=false。退出删除密文及密钥，额外退出标记防止密文删除异常后的恢复；写入成功才解除标记。读写异常不建立登录状态，写失败时尝试撤销已签发 Token。

Guest → Authenticating → 仅 Server 真成功并安全落盘后 Authenticated；失败为 LoginFailed/StorageFailed，用户需重新授权获取新 code。code 不自动重发。

冷启动：Validating → 从安全存储读取 → /me → Authenticated；网络失败为 TemporarilyUnavailable，保留密文但不展示账号权益。重回前台或点击重新校验可重试 /me。到期、撤销、禁用或应用不匹配转 SessionExpired 并清 Token。无刷新能力，必须重新授权。

退出立即 Guest，再尝试 /logout。退出/取消改变 generation，旧登录成功响应只能尝试撤销新签发 Token，不能落盘或恢复身份。Activity 销毁不取消 Application 范围网络请求。A6.4 state 校验与持久消费顺序未改变；重复回调不会再次交接 code。后台锁定仍由原 VaultSessionManager/AppLifecycleObserver 负责，账号成功不会解锁保险箱或导航绕过密码。

账号 UI 是会话的派生显示，未增加第二个持久登录来源。会员始终未订阅，未接入试用、会员或支付接口。退出函数仅操作会话存储，不操作照片、视频、密码、应用锁或设备试用。

## 远端残留会话方案

不持久保留退出 Token，也不做后台无限撤销重试。离线退出、撤销超时、登录请求超时但服务端已签发，或进程在签发后被杀，都可能产生远端残留会话。已获得但被取消的成功响应会尽力调用 /logout；本地身份始终保持退出。

服务端目前默认 TTL 为 7 天，以真实 expiresAt 为准；残留会话最迟随服务端到期失效，也可由服务端运维撤销。重新登录不会自动撤销旧会话。本任务不增加全会话撤销接口。UI 明确提示撤销未确认风险。若未来要求离线退出后恢复网络必须可靠撤销，需单独设计加密撤销队列或服务端全会话撤销策略。

## 自动化验证

最终构建通过：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:processReleaseMainManifest :app:generateReleaseBuildConfig`。

JVM 测试 **277 项，通过 277，失败/错误/跳过均为 0**。其中新增会话测试 16 项，既有 LoginPromptTest 6 项、WeChatAuthorizationTest 22 项、AccountUiStateTest 12 项全部通过。设备测试仅编译通过，未执行。

Release 合并 Manifest：usesCleartextTraffic=false，allowBackup=false；默认 Release API_BASE_URL 为空。传入 `-PRELEASE_API_BASE_URL=http://10.0.2.2:8080` 的负向校验按预期构建失败。Server git 工作区检查无修改。

验证记录位于 `evidence/task-i2.1-a/`（build.log、release-http-rejection.log、verification.json）。APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256：`1fc8d590ed0a26fb99ee80f67a67215f18b7d389e798bcecb050c7029a6d90bd`。

首次构建的既有微信/友盟等依赖产生 D8 stack map 警告及部分原有弃用警告，最终不阻止 APK 生成。未为本任务修改这些 SDK。

新增 JVM 测试覆盖真实 HTTP 请求 code/App-Code/Bearer、ApiResponse 解析、登录保存与失败不保存、/me 恢复及 401 清理、网络失败待验证、退出成功/离线失败、跨 App 错误拒绝、重复登录及迟到响应、冷启动、到期、安全存储读写异常、取消发生在隐私检查期间、服务端暂不可用与格式异常。原 A6.4 state 校验/重复回调与 A6.1～A6.3 既有测试作为回归运行。测试全部使用测试 code/Token、本机 HTTP 服务或 Fake，不访问生产及真实微信。

设备测试尚未执行：当前 adb devices 无连接设备。人工 UI、真实 Keystore 和安装验收不能用 JVM 测试替代。

## Debug 配置及人工验收步骤

使用 JDK 21、Android SDK 37（本机 D:/AndroidSDK）。Gradle 属性可用命令行 -P 或用户级 gradle.properties 配置。

```powershell
# 模拟器：访问宿主机
.\gradlew.bat :app:assembleDebug -PDEBUG_API_BASE_URL=http://10.0.2.2:8080
# USB 真机：先建立反向端口映射
adb reverse tcp:8080 tcp:8080
.\gradlew.bat :app:assembleDebug -PDEBUG_API_BASE_URL=http://127.0.0.1:8080
# 局域网真机：使用开发电脑实际 IP，同网段并允许防火墙入站
.\gradlew.bat :app:assembleDebug -PDEBUG_API_BASE_URL=http://192.168.1.100:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug 默认 10.0.2.2:8080，仅 Debug 允许明文。Release 默认 API URL 为空（账号网络不可用，安全失败），部署时必须显式提供 `-PRELEASE_API_BASE_URL=https://真实公开域名`；构建校验 HTTPS、非 localhost/数字 IP/.local，运行时还拒绝 URL 用户信息，Release 禁止明文。不填写任何正式凭证。微信 WECHAT_APP_ID 配置保持原样。

1. 按 Server 原有已验收方式由开发者启动 Server，不需修改数据库。先确认其 health，再选择上面的设备地址。
2. 安装 Debug。AppID 未配置时登录提示“尚未配置 AppID”；不勾协议提示校验，暂不登录可继续使用；首次进入弹窗只出现一次。
3. 运行 JVM Fake HTTP 测试验证三个端点及状态；无 AppID 时 APK 不提供假登录入口。
4. 连接设备运行 Keystore 测试。人工检查已有本地照片/视频、计算器密码、应用锁与设备试用在退出前后均保留。
5. 冷启动、有网/断网、后台恢复都检查设置页“正在校验/待验证/失效”说明；待验证可以重新校验或退出。真实 Token 的手工冷启动恢复留待 I2.1-B；目前自动化替身覆盖该流程。
6. 发起微信授权后切后台，返回必须仍走原密码锁定；未解锁不得展示保险箱。授权中取消、重复点击及旧请求晚返回由替身自动化验证；真实微信回跳留 I2.1-B。
7. 不进行生产请求、真实支付、会员或试用验收。

## 已知限制与待人工验收

- 微信真实授权、真实 Token 签发与完整 E2E：I2.1-B。
- 当前未连接设备，安装、UI 视觉回归、设备 Keystore、后台回跳密码验证待人工执行。
- 本次不启动或修改 Server；Server 联通性依赖开发者本地服务、端口和防火墙配置。
- 清理失败时当前进程立即未登录，安全存储提供退出标记/密文删除/密钥删除多重保护；若文件系统和 Keystore 均不可写，不能承诺持久清理成功，UI 会提示清理未确认。
- Release 未配置 URL 不会落到开发地址；上线前必须填写并核验正式 HTTPS 域名。
- 当前不取昵称头像，不假造用户或会员权益。
