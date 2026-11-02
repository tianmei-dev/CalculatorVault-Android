# Task I3.1 最终交付说明

日期：2026-10-29。范围：Android 设备试用 S3.2 接入；不修改 Server、不接入会员或支付、不部署生产、不修改五份隐私文件。

## 1. 修改文件

- `app/build.gradle.kts`：上传开关移入 buildTypes；Debug 显式参数启用，Release 常量 false。
- `app/src/main/java/com/aurora/calculatorvault/feature/login/session/HttpAuthApi.kt`：请求增加必传 cap，校验 0～259200；沿用匿名 HTTP、超时和错误处理。
- `app/src/main/java/com/aurora/calculatorvault/feature/trial/DeviceTrialRepository.kt`：逐次计算保守 cap；支持零长度试用；响应与已消耗本地期限取更严格值，持久化最早 Server 到期；网络失败计入消耗；取消协程不吞异常。
- `app/src/test/java/com/aurora/calculatorvault/feature/trial/DeviceTrialTest.kt`：更新 API 签名及请求字段断言；已损坏且有历史标记的本地记录不能重获期限。
- `app/src/test/java/com/aurora/calculatorvault/feature/trial/DeviceTrialCapTest.kt`：新增 S3.2、离线、重装、重试、隐私竞态及 HTTP 契约回归。
- 本说明与 `evidence/task-i3.1/final/`：本次验证证据。

旧《Task-I3.1-交付说明.md》及旧 evidence 保留为 S3.1 阻塞历史，不能作为此次 S3.2 测试结果；原失败探针未纳入常规测试。S3.2 新回归替代其重装场景。

## 2. 实际请求与响应契约

只读核对 Server `DeviceTrialActivateRequest`、Controller、Service、Repository 原子 SQL、`ApiErrorCode` 及 `docs/device-trial.md`。

```http
POST /api/v1/trial/device/activate
X-App-Code: calculator_vault
Content-Type: application/json; charset=utf-8

{"deviceKey":"i31-synthetic-device","remainingSecondsCap":86400}
```

以上为合成标识示例，不是 Android ID。请求仅含这两个字段，无 Authorization、PIN、媒体、应用锁列表或微信 code。

成功为 HTTP 200、`code=0`，`data` 含 `status`（ACTIVE/EXPIRED）、ISO Instant `trialStartedAt`、`trialExpiresAt`、`serverTime`、整数 `remainingSeconds`。允许 expires == started，EXPIRED 必须 remaining=0 且 serverTime>=expires。

Server 首次使用 Server 时间和 cap 原子写入；后续 LEAST 收紧，旧客户端缺省 cap 保持兼容。非法 cap 为 HTTP400/code40010/TRIAL_CAP_INVALID；Android 发送前先校验范围，服务端40010按请求失败处理，不更新确认记录。429单独退避15分钟；其他异常沿用安全降级，不清空数据。

## 3. 期限合并与离线启动

1. 首次打开即建立本地连续72小时记录；隐私尚未同意也只做原有本地计时。
2. 每次实际请求前重新读取有效同意、刷新本地消耗，`cap=floor(clamp(remainingMillis,0,259200000)/1000)`；已到期传0，不会向上取整。重试重新计算，不缓存旧cap。
3. 历史记录已丢失但仍有 seen 标记、存储失败或时间不可确认时，采用0而不是重新授予72小时。这是保守降级，可能提前结束试用。
4. 回包再次校验有效同意与开关，刷新当前本地消耗，取 `min(本地剩余, max(0,min(Server剩余,本次cap毫秒)-请求单调耗时))`。Server EXPIRED 或本地已到期均保持到期。
5. Server started 发生异常变化或时间明显倒退仍按原有策略要求验证。Server到期上限取历史最早值，与剩余时间、过期位、时间锚一起写入原有独立 Keystore 加密 noBackup 存储，冷启动沿用，不修改PIN等数据。
6. 单应用scope串行，合并重复同步；成功15分钟内不重复同步，失败指数退避。网络失败也消耗已过去的本地时间。异常响应不持久化为已确认；协程取消向上传播。
7. 同次启动用单调时间抗墙钟回拨；跨启动保留原有 boot/时间不确定降级。不增加权限，不信任客户端绝对首次启动时间。

离线48小时首次联网 cap约86400秒，Server存24小时上限；本地仍取更严格期限。第73小时空本地存储、同合成标识联网，Server返回已过期，不能复活。账号登录/退出没有设备试用状态修改路径。

已接受边界：首次联网前卸载且本地记录全丢，Server无法追溯；标识或签名变化、设备重置等不保证识别同一物理设备。重装后离线无法读取Server已存资格，必须联网恢复；不宣称离线重装绝对防重领。相对cap与本地记录不是可信硬件证明。

首次cap传输延迟期间，Server以收到请求时刻加cap，可能比严格的本地截止稍晚；Android保留本地更严期限且扣除整段请求耗时，不延长本次安装试用。重装只能恢复已被Server确认的期限，存在网络延迟量级偏差，不能宣称精确到毫秒复原离线起点。

## 4. 隐私与构建开关

保留当前政策版本门禁、Android ID provider 的 runIfAllowed 保护、请求前二次校验与响应后校验。撤回后 gate 在本进程保持关闭，旧回包不会重新放行；已发送请求无法远程撤回，但其结果不恢复功能。不新增日志或数据种类。

默认 Debug=false、Release=false。仅专用开发测试构建使用：

```powershell
.\gradlew.bat :app:assembleDebug -PTRIAL_DEVICE_UPLOAD_ENABLED=true -PDEBUG_API_BASE_URL=http://10.0.2.2:8080
```

模拟器10.0.2.2；USB专用测试机可 `adb reverse tcp:8080 tcp:8080` 后使用127.0.0.1；局域网专用测试机配置开发机地址。必须先确认测试服务及测试设备，普通手机不读取/上传真实标识。本次自动化只用合成标识，未安装、清数据或读取已连接手机的Android ID。

Release 在 buildTypes 固定 false，不受同名参数影响。Release地址及 HTTPS 规则、微信AppID、applicationId不变。

## 5. 自动化及构建

以下均基于最终代码，2026-10-29执行：

| 检查 | 结果 |
|---|---|
| 完整 Debug JVM 回归 | 345项通过，0失败、0错误、0跳过（原336项+新增9项） |
| Debug assemble | 通过 |
| Release assemble + lintVitalRelease | 通过（未签名APK） |
| 显式 opt-in 参数下的双构建及回归 | 通过；Debug=true、Release=false |
| Release 编译后的 BuildConfig 常量 javap 检查 | false |
| 移除 opt-in 参数后再构建与回归 | 通过；Debug=false、Release=false，最终留存默认关闭APK |
| git diff --check | 通过 |
| 真实AuroraAppServer联调、真机安装/重装、抓包 | 未执行，不标记通过 |

新增测试覆盖48h离线cap、同步过期资格重装恢复、73h首次联网cap0/零长度期限/冷启动、秒向下取整、超时重算、Fake Server只收紧、迟到回包和本地回拨、撤回拒收回包、丢失历史保守处理、异常响应、回环HTTP零cap及40010失败。已有用例继续覆盖首次离线72h、重复请求、退避、账号切换、隐私未同意、SDK门禁及C1.4通知流程。

相同cap/更小cap的Server LEAST行为在本次使用Fake模型验证，真实Server并发/事务行为未在Android仓库复测。

证据位于 `evidence/task-i3.1/final/`：两次最终构建日志、测试统计与试用模块XML、两种开关矩阵、Release编译常量检查和默认APK哈希。

默认产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release-unsigned.apk`。

## 6. Server 联调结果

未验证：本机127.0.0.1:8080没有监听服务，未获得其他已确认非生产Server地址。本次不启动未知数据库、不读取Server密钥、不修改Server，不向生产端点发送请求。

已执行的是 Fake Clock + 持久化 Fake Server 规则模型及回环 Fake HTTP Server 测试；它们验证 Android 请求、解析与状态处理，不等同于真实 AuroraAppServer 联调。Server所述116项测试属于上游交付，本次没有重新执行或计入 Android 结果。

## 7. 剩余最小验收清单

1. 提供并确认非生产S3.2服务地址、calculator_vault配置及隔离测试库；先用合成标识运行首次cap86400、重复、缩短、cap0、较大cap不可复活及非法cap40010请求，核对返回和持久化值。
2. 专用测试模拟器/真机安装 Debug opt-in 包；未同意/旧版同意/撤回时抓包确认无设备试用请求；同意后验证当前版本门禁、正常同步、断网超时和恢复。
3. 离线首次启动后消耗时间再联网，核对cap向下取整及Server上限；不要改变生产时钟或生产数据。
4. 同一签名、用户和设备身份下，先同步短期或过期Server记录；仅在专用测试设备卸载重装，联网确认过期不复活。清数据/卸载会删除测试本地私密数据，仅在空白测试安装执行。
5. 核查后台锁定、PIN、测试私密媒体、应用锁配置及账号切换不受同步影响；网络失败不清空这些数据。
6. 在专用机验证默认Debug和Release均无上传；opt-in参数不得放行Release。Release为未签名构建，安装验收需既有签名流程。

结论：不宣称真实Server联调或真机重装验收已通过。完成本任务范围后等待人工验收，不进入会员、支付或生产部署。
