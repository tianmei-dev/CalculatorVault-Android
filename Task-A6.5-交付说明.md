# Task A6.5 微信开放平台正式配置交付说明

日期：2026-10-29。

## 一、交付结论

正式 AppID `wxa4c721130ed106c9` 已通过现有 Gradle 配置方式写入项目，并核对新 Debug 的生成源码和编译后 BuildConfig。Debug 构建成功，完整 JVM 回归345项通过、失败/错误/跳过均为0。

Android 实际包名为 `com.aurora.calculatorvault`。未提供开放平台后台登记包名和签名MD5，无法宣称平台匹配已通过。已发起待核对信息请求；当前不更换任何签名证书、不安装覆盖已有应用、不修改开放平台设置。真实微信授权和服务端换取会话尚未执行。

## 二、修改文件及配置方式

本任务新增/修改：

- `gradle.properties`：新增 `WECHAT_APP_ID=wxa4c721130ed106c9`；AppID是公开标识，不是AppSecret。
- `Task-A6.5-交付说明.md`：本说明。
- `evidence/task-a6.5/`：构建/签名报告、APK证书及包名、相关测试XML、配置及验证摘要。

沿用现有 `app/build.gradle.kts` 的 Gradle属性 → 环境变量 → 空值读取逻辑及合法性校验。现在项目属性提供正式值；需要有意覆盖时使用 `-PWECHAT_APP_ID=...`，不能再期待低优先级环境变量覆盖项目属性。显式空值仍可用于缺失AppID场景的测试包，不作为正式配置。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:signingReport
```

未引入 Android AppSecret 配置、私钥或密码。AppSecret及微信code换取凭证只应由Server处理；Android仍仅将一次性code交给现有账号接口。

I3.1现有未提交改动原样保留，本任务未修改其API、Repository、测试或上传规则；本次Debug生成字段 `TRIAL_DEVICE_UPLOAD_ENABLED=false`。会员、支付、applicationId、微信SDK版本和签名方式均未修改。

## 三、包名和签名核查

用 Build Tools 37.0.0 的 `apksigner verify --print-certs` 核验APK证书，用 `aapt2 dump badging` 核验APK包名；与Gradle signingReport交叉比对。

| 对象 | 包名 | 证书MD5（小写、无冒号） | 状态 |
|---|---|---|---|
| 本次新Debug APK | com.aurora.calculatorvault | `6621896083ef55ce8dc3fe5950da8c0f` | APK签名验证通过，与本次signingReport一致 |
| 现存 `app/release/app-release.apk` | com.aurora.calculatorvault | `d74060446c4b077172f355285d43cebb` | APK签名验证通过；历史签名包，仅用于取证书 |
| 当前Release自动构建配置 | com.aurora.calculatorvault | 无 | signingReport为Config:null，未配置Release signingConfig |
| 微信开放平台登记值 | 待提供 | 待提供 | 无法判定匹配或不匹配 |

Debug证书DN为Android Debug；历史Release证书DN为 `C=86, ST=86, L=chengdu, O=ztmay, OU=ztmay, CN=ztmay`。两份APK证书MD5明确不同，不能用Debug通过构建代替正式签名授权验收。

若平台登记的是 `d74060446c4b077172f355285d43cebb`，则现存Release匹配，而本次Debug的 `6621896083ef55ce8dc3fe5950da8c0f` 不匹配；若平台登记的是其他值，则需分别比对。没有平台原值时，以上只是条件判断，不能认定已发生平台签名不匹配。

旧A6.4文档中Debug MD5为 `586af2f39e07e79740a97dc5ffca42fb`，与本次实际APK不同，不能沿用旧值验收；原因未在本任务中推定，也没有重建或替换调试密钥。

本次只构建Debug；历史Release APK没有重签或重打包，不代表已经包含本次AppID配置。后续新版Release必须使用已获授权且与平台登记一致的既有正式签名流程，不能以生成新证书来解决差异。当前无正式签名自动化配置，亦未读取私钥及密码。

## 四、A6.4接入核查

微信 OpenSDK 仍为6.8.40。

- 回调类为 `com.aurora.calculatorvault.wxapi.WXEntryActivity`，位置与包名对应。最终Debug merged Manifest核验：exported=true、singleTask、taskAffinity=com.aurora.calculatorvault、excludeFromRecents=true；使用透明主题；保留微信包可见性声明。
- Application持有SDK bridge及授权coordinator，SDK只在有效隐私门禁下按需创建并注册正式AppID。`createWXAPI(..., true)`保留微信客户端签名检查；它不能证明本应用签名已匹配平台登记值。
- 用户主动发起时发送 `snsapi_userinfo` 和32字节随机nonce；五分钟有效期，重复点击合并。
- onCreate/onNewIntent均经过handleIntent；仅处理SendAuth响应和正确type；域层校验有效同意、pending、时效、state及非空code。取消、失败、无效回调不会登录。
- state和时间可短暂持久化；必须先同步commit清除pending，再交给Server。code仅内存交接，不进入SharedPreferences、SavedState或日志。
- Server入口实际连接 `AccountSessionRepository`，不是Pending占位实现；应用scope异步执行，不依赖回调Activity存活。收到code本身不标记已登录，仅Server成功才更新账号会话。
- 重复/并发回调、进程恢复后重放、清除pending失败均有现成测试；HTTP层没有业务重试或重定向，超时测试确认只收到一次请求。网络失败后必须重新发起授权获取新code，不重复消费旧code。
- SDK日志使用SilentWeChatLog；code请求对象toString脱敏；回调完成清理Intent extras并finish。未增加包含code/Token的诊断输出。
- 回调不直接解锁保险箱，也不改变PIN校验和后台锁定；撤回同意后禁止SDK授权和回调交接，迟到账号结果受原有会话generation保护。

上述属于源码/合并Manifest及自动化核查，不代表已在真实微信客户端完成回调。

## 五、测试与产物

实际命令：`:app:testDebugUnitTest :app:assembleDebug :app:signingReport`，BUILD SUCCESSFUL。

- 完整JVM：345通过、0失败、0错误、0跳过。
- 其中WeChatAuthorizationTest 22项、AccountSessionTest 16项、LoginPromptTest 6项均通过（已包含在345中，不重复累计）。
- 覆盖缺失AppID、不可用微信、scope/state、有效/空code、取消拒绝、重复与并发回调、消费失败、恢复重放、超时、隐私门禁、登录及退出竞态、Fake HTTP请求格式和不重试。
- 生成及编译后的Debug BuildConfig确认正式AppID且试用上传关闭；最终merged Manifest确认包名及回调注册。
- 新Debug APK的签名验证通过，证书与本次signingReport一致；`git diff --check`通过。
- 未执行真机安装、微信授权、真实code交换、Server登录E2E；未在本次重建或签署Release。

Debug产物：`app/build/outputs/apk/debug/app-debug.apk`。

证据：`evidence/task-a6.5/verification.json`、`debug-build-and-tests.log`、`debug-apk-certificate.txt`、`existing-release-apk-certificate.txt`、`debug-compiled-config.txt`及相关XML。验证摘要包含APK SHA-256，避免把不同构建混作同一包验收。

## 六、仍需人工确认的开放平台与环境设置

1. 提供并核对AppID `wxa4c721130ed106c9` 对应的Android登记包名，应与实际APK `com.aurora.calculatorvault`一致；核对后台登记的32位签名MD5。
2. 明确用于真机验收的APK签名。若登记的是正式证书，本次默认Debug不能直接作为匹配包验收；通过既有授权签名流程准备匹配包。不要为测试擅改正式证书或平台登记签名。
3. 确认该已审核移动应用的微信登录能力可用。仅配置AppID不代表端到端授权必然成功。
4. 由Server维护者核对calculator_vault对应微信AppID及服务端AppSecret配置；不把AppSecret发给Android、不提交仓库。本任务未修改Server。
5. 为匹配签名的验收包配置可达测试API地址。Debug默认模拟器10.0.2.2:8080，真机需显式开发地址或USB端口转发；Release仍必须配置HTTPS API URL，不能使用默认空地址完成真实登录。

## 七、最小真机验收步骤

1. 选择专用测试设备，先确认安装包签名、平台登记MD5和包名一致。已有安装若签名不同，不直接卸载或覆盖真实私密数据；换用干净的专用测试设备，或沿用原有匹配证书构建。
2. 使用当前正式AppID构建，保持试用上传关闭。安装有效微信客户端，测试API可达；不使用生产账号私密数据，不抓取或记录明文code/Token。
3. 未同意当前隐私政策时不能发起微信授权；同意后进入登录弹窗，未勾选登录协议不能继续，勾选后点击微信登录可拉起微信。
4. 验证微信确认、取消、拒绝、未安装微信等路径。确认返回后若后台锁定触发，仍先输入计算器密码；回调不能绕过PIN。
5. 正常授权后通过仅含状态/计数的服务端测试观察，确认每次授权仅提交一次微信登录请求；返回Server成功结果后才展示已登录。不要输出请求体或Token来验收。
6. 测试重复点击/重复回调、授权中返回或取消、切后台、回调Activity结束；应无重复提交和假登录。
7. 登录请求超时/Server不可用后重试必须重新授权；撤回同意或主动退出后，迟到结果不能恢复登录；原有媒体、PIN、应用锁配置及试用起点保留。
8. 待匹配签名新版Release准备好，再复核实际Release APK的包名、MD5、AppID和HTTPS地址，执行同一授权流程。正式版设备试用上传仍保持关闭。

完成配置与离线核查后停止，等待登记信息比对及人工真机验收。
