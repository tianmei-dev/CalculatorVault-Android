# 微信登录本地联调 APK 配置交付说明

日期：2026-10-29。仅用于用户确认的 AuroraAppServer local 开发环境。

## 1. 方案及修改文件

沿用现有 `BuildConfig.API_BASE_URL` 和 HttpURLConnection 客户端，新增独立构建类型 `localIntegration`，从release继承基础配置，不增加产品flavor或新的网络框架。

| 配置 | localIntegration | 正常release |
|---|---|---|
| applicationId | com.aurora.calculatorvault | com.aurora.calculatorvault |
| 微信AppID | wxa4c721130ed106c9 | 沿用同一正式配置 |
| API_BASE_URL | http://192.168.0.107:8080（固定，不受Debug/Release URL参数覆盖） | RELEASE_API_BASE_URL，默认空；非空必须符合既有HTTPS/域名校验 |
| LOCAL_HTTP_BASE_URL | http://192.168.0.107:8080 | 空字符串 |
| TRIAL_DEVICE_UPLOAD_ENABLED | 固定false | 固定false |
| debuggable | false | false |
| 签名 | 不自动签名，由人工选择现有正式JKS | 沿用既有人工签名流程 |
| versionName | 1.0.0-local-integration | 1.0.0 |
| 明文网络 | 网络安全配置仅放行192.168.0.107；账号客户端还限制完整Base URL及8080端口 | 原策略不变，禁止HTTP |

本任务修改/新增：

- `app/build.gradle.kts`：LOCAL_HTTP_BASE_URL默认空；新增localIntegration，固定本地地址、关闭试用上传、非debuggable、未签名、版本后缀。
- `app/src/localIntegration/AndroidManifest.xml`：仅联调变体引用网络安全XML。
- `app/src/localIntegration/res/xml/local_integration_network_security.xml`：默认禁止明文，只放行指定IP，不包含子域；显式禁止localhost，避免Android17隐式localhost HTTP策略。此资源不合入普通Release。
- `app/src/main/java/com/aurora/calculatorvault/feature/login/session/HttpAuthApi.kt`：增加默认关闭的精确URL例外参数；普通Release无该例外。保留无重定向、不自动重试和无敏感日志。
- `app/src/main/java/com/aurora/calculatorvault/app/CalculatorVaultApp.kt`：只给账号客户端传入该例外；微信授权/回调不变，试用客户端构造及试用逻辑不变。
- `app/src/test/java/com/aurora/calculatorvault/feature/login/session/LocalIntegrationHttpTest.kt`：新增2项测试，覆盖非Debug精确HTTP例外、正常Release拒绝、其他主机/端口/路径等拒绝及真实客户端请求格式。
- 本说明、`evidence/wechat-local-integration/`：交付和验证证据。

本次开始前工作区已有I3.1/A6.5及人工签名Release产物改动；均保留，不将其算成本次新改动。未读取、复制、输出JKS私钥或密码，未更换证书，未签署APK。没有新增日志、权限、会员、支付或设备试用业务改动。

## 2. 网络规则及接口核对

普通Release的usesCleartextTraffic=false保持不变。联调变体也保留这个Manifest值，但在minSdk26的系统上由其专用Network Security Config实施按主机的例外；并不是全局开HTTP。XML策略按主机限制，端口8080及精确URL由账号客户端再次检查；请求禁止重定向。

只读核对Server AuthController与WechatLoginRequest，Android实际请求为：

```http
POST http://192.168.0.107:8080/api/v1/auth/wechat/login
X-App-Code: calculator_vault
Content-Type: application/json; charset=utf-8

{"code":"<仅内存中的一次性微信授权code>"}
```

请求体仅有code，不带设备标识、PIN、媒体或应用锁列表；登录不带Bearer。`/me`和`/logout`继续使用同一账号API地址及已有Bearer会话逻辑。不给Android配置AppSecret。

已校验现有微信授权state消费及code一次性交接，未修改。网络超时后应重新发起微信授权，不无条件重放code。日志、代理和Server访问日志验收只观察方法、路径、状态或计数，不打印请求体、Authorization或响应Token。

## 3. Android Studio 人工生成正式JKS签名联调包

1. Sync Gradle后打开Build Variants，选择app的 **localIntegration**。不要选择debug或release来生成本次联调包。
2. 打开 **Build → Generate Signed App Bundle or APK**，选择 **APK**，module为app。
3. 选择现有正式JKS及既有alias，由你在IDE本机输入密码；不创建新证书，不把密码写入Gradle、命令行、文档或聊天。
4. 在签名向导的构建类型选择 **localIntegration**，选择独立输出目录（建议本机忽略目录下），完成生成。输出路径以IDE通知为准；不要把签名文件或密码提交仓库。[Android官方签名流程](https://developer.android.com/studio/publish/app-signing)
5. 用apksigner核对最终签名包（只读APK公开证书，不读取JKS）：

```powershell
& D:\AndroidSDK\build-tools\37.0.0\apksigner.bat verify --print-certs '<人工签名APK的绝对路径>'
```

证书MD5必须为 `d74060446c4b077172f355285d43cebb`（比较时去冒号、忽略大小写）。若不同，停止安装并核对选中的既有JKS/alias；不替换正式证书。不要使用Android Debug证书测试微信登录。

命令行只验证未签名构建，可运行：

```powershell
.\gradlew.bat :app:assembleLocalIntegration
```

输出位于 `app/build/outputs/apk/localIntegration/`，未签名APK不能直接作为可安装签名包。签名由上述IDE流程完成，不在Gradle配置JKS路径及密码。

## 4. 验证实际Base URL

- 检查生成文件 `app/build/generated/source/buildConfig/localIntegration/com/aurora/calculatorvault/BuildConfig.java` 中API_BASE_URL与LOCAL_HTTP_BASE_URL均为 `http://192.168.0.107:8080`，TRIAL_DEVICE_UPLOAD_ENABLED=false，DEBUG=false，BUILD_TYPE=localIntegration。
- 核对输出目录及版本后缀 `-local-integration`；包名不增加后缀。
- 在Android Studio **Analyze APK** 打开实际人工签名APK，查看Manifest的包名、versionName、networkSecurityConfig，并查看专用XML只允许指定IP。检查DEX中BuildConfig的API_BASE_URL常量，避免分析了旧包。具体界面名称可能随IDE版本变化。
- 最终以手机发出的请求及Server只含路径/状态的访问记录确认实际连接 `/api/v1/auth/wechat/login`。无需为验收新增输出code或Token的日志。

网络安全配置原理见[Android官方文档](https://developer.android.com/privacy-and-security/security-config)。HTTP仅限这个受控本地测试包；该包不作为正式环境分发包。

## 5. 恢复正常Release

不需要撤销代码或删除文件，切换Build Variants及签名向导构建类型为 **release** 即可。localIntegration源集的Manifest/XML不会合入Release，LOCAL_HTTP_BASE_URL为空；Release仍不允许HTTP。正常发布时按原流程显式配置公开HTTPS地址：

```powershell
.\gradlew.bat :app:assembleRelease '-PRELEASE_API_BASE_URL=https://<实际正式域名>'
```

上面域名是占位符，必须替换；不提供参数时仍默认空地址，账号网络安全失败，不能用于真实服务端登录。不要将本地IP赋给RELEASE_API_BASE_URL；Gradle会拒绝HTTP及原有不允许的域名/IP。设备试用上传保持false。

## 6. 真机登录最小步骤

1. 开发机启动已配置正式微信AppID和服务端AppSecret的local Server，监听192.168.0.107:8080，防火墙允许测试手机访问。手机处于可访问该IP的局域网，排除访客Wi-Fi/AP隔离及VPN影响；无需修改Server接口或结构。
2. 人工生成并核对正式证书签名的localIntegration包；同包名、同正式签名可按既有覆盖安装流程更新。不要通过卸载真实数据来解决签名差异，不清除媒体、PIN或试用记录。
3. 确认版本为local-integration、已同意当前隐私政策，微信已安装。进入微信登录弹窗并勾选协议，发起授权。
4. 微信确认返回后按原有后台锁定规则输入计算器密码；等待Server成功后才显示登录账号。通过脱敏Server访问记录确认X-App-Code及方法/路径，单次授权只有一次登录请求。
5. 验证取消/拒绝授权、不勾协议、重复点击、Server断开/超时。失败后重新微信授权，不复用旧code，不应假登录。
6. 成功后验证冷启动/me、主动退出/logout、登录期间退出、迟到响应不恢复账号；媒体、PIN、应用锁、试用起点保持原样，试用接口不上传。
7. 测完切回正常Release，重新确认最终HTTPS地址、MD5及无联调XML。当前尚未人工签名/安装此新变体，不能把此前已通过的微信授权回调测试当作本次Server登录E2E通过。

## 7. 验证结果

执行命令（PowerShell中带点的-P属性需保持引号）：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testLocalIntegrationUnitTest :app:assembleLocalIntegration :app:assembleRelease '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false'
```

AGP9默认只创建受测Debug类型的单元测试；上面参数仅在本次验证命令启用其他变体测试，不写入项目设置。该命令BUILD SUCCESSFUL，两个变体的lintVital也通过。

| 验证项目 | 结果 |
|---|---|
| Debug完整JVM回归 | 347通过，0失败/错误/跳过 |
| localIntegration JVM回归 | 335通过，0失败/错误/跳过 |
| localIntegration构建 | 通过，输出未签名APK |
| 正常Release构建 | 通过，输出未签名APK |
| 新增非Debug HTTP例外及请求契约测试 | 两变体各2项通过，已包含在上方统计 |
| 传入-PTRIAL_DEVICE_UPLOAD_ENABLED=true | localIntegration/release生成常量仍为false |
| Release明文地址负向检查 | 传入HTTP的RELEASE_API_BASE_URL在配置阶段按预期拒绝 |
| 联调APK | 包名、AppID、两个URL、非debuggable、上传false及编译XML符合预期；DEX含指定联调URL |
| 正常Release APK | URL字段保持默认空，LOCAL_HTTP_BASE_URL为空，未带联调XML，DEX不含联调URL |
| 开发机到Server连通性 | 不带凭据GET /api/v1/auth/me返回HTTP401；未记录正文、未发送code/Token |
| git diff --check | 通过 |
| 人工正式JKS签名、手机网络策略及真实登录E2E | 本次未执行，待人工验收 |

Debug比联调多12项，是已有 `src/testDebug/.../AccountUiStateTest` 的12项Debug专属测试；不是跳过或丢失通用测试。两个统计不重复累计为不同用例数。新增2项之外未调整已有业务测试。

证据位于 `evidence/wechat-local-integration/`：verification.json（常量、Manifest、APK SHA-256及测试统计）、build-and-tests.log、两份新增测试XML、apk-network-security-config.txt、forced-trial-flag-check.log、release-http-rejection.log、host-connectivity.json。

AAPT打包优化将XML文件名缩短，已通过APK资源表定位并反查实际编译XML，未仅检查源文件。普通Release资源表中没有该联调XML，DEX中没有该开发地址。当前生成常量与APK均按各变体验证；未对已存在的人工签名Release产物进行修改或重签。

未签名联调APK：`app/build/outputs/apk/localIntegration/app-localIntegration-unsigned.apk`。普通Release仍为 `app/build/outputs/apk/release/app-release-unsigned.apk`。

开发机返回401仅说明该机可访问服务，不代表手机网络、微信code交换或会话恢复已验收。运行时NetworkSecurityPolicy及手机端完整登录需要在人工签名包上执行上述步骤。
