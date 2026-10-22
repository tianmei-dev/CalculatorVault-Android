# CalculatorVault · A7.1 设备 72 小时试用交付

## 结果与范围

仅完成 Android 设备试用数据和展示。未开发 I3.1、最终权益系统、会员或支付；未更改应用锁、相册收费判断、PIN、媒体数据或 Server。I2.1-A 现有改动保留，未提交或推送代码。

Debug 构建成功。全部 **304 项 JVM 测试通过，失败/错误/跳过均为 0**，其中 A7.1 新增 27 项；原 I2.1-A 的 16 项会话测试和原有授权、账号 UI 等回归继续通过。设备测试源码编译通过，当前 adb 无连接设备，未安装 APK、未执行真实 Keystore 或视觉验收。

APK：`app/build/outputs/apk/debug/app-debug.apk`
SHA-256：`380a7fa9d9b5b19f0b11c7578f926610d11502840f928213bbc1b2de4911bbd9`
验证记录：`evidence/task-a7.1/build.log`、`verification.json`。

## 修改文件

以下 Kotlin 路径基于 `app/src/main/java/com/aurora/calculatorvault/`：

- `feature/trial/DeviceTrialRepository.kt`：模型、Fake 可替换接口、唯一设备试用状态源、计时、校验、去重和退避。
- `feature/trial/AndroidTrialInfrastructure.kt`：SSAID Provider、Android Clock、独立加密试用存储。
- `feature/login/session/HttpAuthApi.kt`：复用 I2.1-A 网络实现，增加 TrialApi.activate；无第二套 HTTP 框架。
- `feature/login/session/KeystoreTokenStore.kt`：增加独立命名空间参数，默认账号密钥别名及文件名保持兼容。
- `app/CalculatorVaultApp.kt`：主进程尽早本地初始化；前台、网络恢复、隐私状态变化触发同步；30 秒刷新显示。
- `feature/vault/presentation/VaultMainScreen.kt`：真实设备试用状态与剩余时长展示，派生 A6.3 deviceTrial，不影响账号退出。
- `feature/settings/presentation/AccountUiState.kt`：未确认试用时不再默认显示“试用已到期”。
- `app/src/main/res/values/strings_account.xml`：等待验证文案。
- `app/build.gradle.kts`：增加默认关闭的设备标识上传开关。
- `app/src/test/java/.../feature/trial/DeviceTrialTest.kt`：27 项自动化测试。
- `app/src/androidTest/java/.../feature/trial/EncryptedTrialStoreTest.kt`：真实密文、冷读和账号存储隔离测试。
- 本交付说明和 `evidence/task-a7.1/` 验证记录。

## 设备标识与隐私发布门禁

AndroidDeviceIdentityProvider 仅使用 Settings.Secure.ANDROID_ID，校验 16 位十六进制，拒绝空值、全零和已知无效旧值；异常返回 null。不申请新权限，不读取 IMEI/MAC/序列号，不持久保存原始 SSAID，不记录到日志，不用于账号认证。测试使用 FakeDeviceIdentityProvider。

**现有隐私政策未覆盖“自有服务端设备试用防滥用”用途和传输。** 当前文档只在友盟统计 SDK 用途下提及 Android ID，且仍有不提供账号登录等旧描述。因此不宣称已合规，未擅自发布新的政策。

`TRIAL_DEVICE_UPLOAD_ENABLED` 默认为 false，正式和 Debug 均默认不向试用接口上传设备标识。启用开关仍必须同时满足现有隐私同意状态；撤回同意后新请求被阻止，已在途响应也不会建立新的同步结果。完成政策、信息收集清单及同意版本核对后，开发者才应开启该开关。政策上线审阅仍为人工待办。

首次 Application 主进程启动时，本地试用记录先于第三方 SDK 初始化创建，不读取 SSAID、不联网。用户晚些同意不会重算起点。未同意也会连续本地计时。上传关闭期间状态显示待联网验证，不假装已经服务端确认。

## 加密存储

复用平台 Android Keystore AES-GCM 原子文件方案，试用使用 `device-trial-v1` 独立命名空间，与账号 Token、计算器密码、媒体加密密钥隔离。文件位于 noBackupFilesDir，原 allowBackup=false 保持。无明文 SharedPreferences/Room 试用表。

密文保存首次打开时间、剩余时长上限、最近 wall/elapsed/开机计数锚点、永久到期标记、Server 开始/结束/时间锚点、已验证及时间不可信标记。起点不重置；本地截止时间可由首次打开 + 72 小时推导，但同次开机倒计时以单调时钟为准。

额外 `device-trial-v1.seen` 只存首次记录存在标记，不含身份或完整状态；先持久化标记，再写加密授权。密文丢失但标记存在时，不重新发放本地 72 小时。读、解密、写失败均转 VERIFICATION_REQUIRED，可在隐私允许后查询 Server 恢复。账号 logout 只删除账号命名空间，不能删除试用记录。

## 状态机及在线/离线协调

- NOT_INITIALIZED：尚未读取本地记录。
- PENDING_SYNC：本地连续试用已建立，尚未由 Server 确认；保留可用本地剩余时长。
- ACTIVE：Server 已确认，剩余时长仍受本地已消耗上限限制。
- TEMPORARILY_UNAVAILABLE：网络、超时、429 或服务端异常；不等同到期，不发新试用。
- VERIFICATION_REQUIRED：存储异常、缺失记录、无有效设备标识、重启后时间明显回拨或无法建立可信基准。显示受限/待验证，不提供确认有效的试用结果。
- EXPIRED：本地上限耗尽或 Server 返回到期；持久保存，不会被重启、退出账号或后来 ACTIVE 响应逆转。

同次开机以及当前进程内使用 elapsedRealtime 扣除消耗，墙钟前调/回拨不会延长或提前耗尽该段倒计时。冷启动通过 BOOT_COUNT 判断是否仍为同次开机；重启后结合墙钟差计算，超过 2 分钟的明显回拨进入验证，轻微偏差容忍。缺少可靠开机标记的冷启动安全降级；初次打开当前进程仍可用单调时钟计时。

正常首次联网取 `min(本地剩余, Server 剩余)`，扣除请求在途时间；已有 Server 记录的旧起点和截止时间优先，不用 Server 新发放的完整 72 小时覆盖离线已消耗时间。后续持久化的剩余上限只减不增。已有可信服务端锚点时，可由再次校验消除设备重启后的时间不确定性；若从未建立过可信服务端锚点且离线时段已无法核实，不因第一次获得一个新服务端周期就解除不确定状态。

Server 起点发生矛盾或 Server 时间明显倒退时进入验证并退避。同一启动周期并发调用合并，成功后 15 分钟内合并重复触发；网络失败指数退避（60 秒起，最大 15 分钟），HTTP 429 至少 15 分钟退避。退避为进程内状态，频繁强杀重启不构成绝对服务端限流保护，Server 自身仍负责限流。无自动无限重试、无首页网络阻塞。网络恢复、前台恢复和隐私状态变化可触发一次同步。

试用状态只供展示及后续权益判断使用，没有改变收费拦截。ACTIVE 不能解锁 PIN，EXPIRED 不删除照片、视频或应用锁配置。微信登录/退出不参与试用起点或计时。

## Server 契约与调用示例

只读核对真实 DeviceTrialController、DeviceTrialActivateRequest、DeviceTrialResponse、DeviceTrialService、ApiErrorCode 和异常处理。Server 工作区无修改。

`POST /api/v1/trial/device/activate`

```http
X-App-Code: calculator_vault
Content-Type: application/json; charset=utf-8

{"deviceKey":"0123456789abcdef"}
```

示例仅为测试值。生产只发送 Provider 读取的应用范围 SSAID；不发送 Bearer、微信 code、客户端起止时间或私密数据。服务端校验 deviceKey 长度 8～128 和允许字符，并按 App 范围 HMAC 查找记录；客户端不会持有该 HMAC 密钥。

成功信封 code=0，data 为 status、trialStartedAt、trialExpiresAt、serverTime、remainingSeconds。时间解析为 Instant，秒转毫秒采用溢出检查，Repository 校验 ACTIVE/EXPIRED、时间顺序、最长 72 小时和剩余时长一致性。真实限流错误为 42901/HTTP 429；配置缺失为 50002。网络/格式/业务错误不直接解释为 EXPIRED。

API Base URL、HTTPS/Debug 明文策略完全复用 I2.1-A。Release 无默认开发地址，不引入正式凭证。

## 自动化测试

最终执行通过：

```powershell
$env:ANDROID_HOME='D:\AndroidSDK'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

27 项新增测试覆盖首次离线启动、重复/冷启动、游客同步、首次联网取小值、Server 旧记录、上限不增长、永久到期、网络异常、429 退避、异常响应、Server EXPIRED、同次开机墙钟前调/回拨、elapsedRealtime、正常重启、重启回拨、开机标记缺失、本地损坏/模拟 Keystore 失败、密文丢失、写失败、deviceKey 缺失、隐私前零标识读取/零请求、上传门禁、并发合并、PIN 后台锁定、晚同意不重置、Server 恢复、重启前调、真实本机 Fake HTTP 契约和账号登录/退出隔离。部分测试同时覆盖多个边界。

未改变设备系统时间，也未等待真实 72 小时。网络测试只访问本机随机端口；其余 Fake Clock/Fake TrialApi 完成。真实 Keystore 测试仅编译，未执行，不与模拟解密失败混淆。既有 277 项回归保持通过，总计 304。

## Debug 真机人工验收清单

1. 使用专用测试设备/模拟器；设备测试会操作测试账号会话和试用存储，不应在保留重要试用现场的设备上执行。
2. 默认 APK 无需真实微信 AppID。首次打开、拒绝/晚同意隐私、杀进程重启、断网进入设置，确认剩余时间不会重置。
3. 在已完成开发环境披露核对的测试构建中启用 `-PTRIAL_DEVICE_UPLOAD_ENABLED=true`；默认 APK 不会上传设备标识。
4. 地址沿用：模拟器 `-PDEBUG_API_BASE_URL=http://10.0.2.2:8080`；USB 真机先 `adb reverse tcp:8080 tcp:8080`，使用 http://127.0.0.1:8080；局域网使用开发电脑实际 IP。
5. 由开发者按 Server 已验收流程启动服务；不修改 Server/Docker/数据库。安装 `adb install -r app/build/outputs/apk/debug/app-debug.apk`，检查游客 activate 请求无 Authorization、无微信 code，且仅包含 deviceKey。
6. 首次离线后联网、已有旧试用设备再次安装、Server 停止/恢复、限流、重启、隐私撤回时检查状态与请求数量；不在日志记录原始设备标识。
7. 退出和切换微信账号前后，试用起点及剩余上限保持；后台再回来仍需计算器 PIN。检查既有照片、视频及应用锁配置保留。
8. 在专用设备运行 `:app:connectedDebugAndroidTest` 检查真实加密、冷读和账号清理隔离；目前待执行。
9. 视觉检查设置页游客/登录状态下都显示真实设备试用；VIP 购买页面设计、免费功能和收费判断不改变。
10. 无需为人工验收修改真实设备时间或等待 72 小时；这些时间边界已由 Fake Clock 验证。安装和真机 E2E 当前未验收。

## 上线前待办与已知限制

- 隐私政策、个人信息收集清单、用途/传输披露及同意版本由产品/合规负责人确认，再启用设备标识上传。当前上传默认关闭，不代表已完成真实 Server 联通验收。
- SSAID 不可当作不可伪造硬件身份。签名、用户、恢复出厂设置、系统迁移等可影响它；不承诺百分百防重装重新领取。
- 卸载或清除全部应用数据会同时删除密文及本地存在标记，客户端离线时无法可靠区分真正首次安装与重装。此时显示尚未服务端验证的本地周期；联网后同 SSAID 的服务端旧记录可收紧/终止它。本任务不增加最终权益，因此不据此开放收费功能。
- 长期离线、设备重启与修改墙钟结合时，不存在绝对防篡改的客户端时间源。采用保守验证和上限策略，不能宣称绝对防作弊；墙钟大幅前调且发生重启可能导致保守提前到期。
- 从未同步且重启后的离线时间已不可核实时，保守保持待验证，可能影响部分正常用户；需人工判断，不能自行延长服务端有效期。
- 迁移到本版本之前的真实“首次打开时间”无法由旧版本凭空恢复；优先采用已有服务端记录，本地起点为本功能首次执行时间。
- 无真机连接，安装、Keystore 实际执行、真实服务端试用联通、UI 视觉及后台 PIN 人工验收尚未完成。
- 未新增最终 VIP/试用/免费功能优先级执行逻辑，未开发支付、I3.1 或会员系统。

完成 A7.1，等待人工验收。
