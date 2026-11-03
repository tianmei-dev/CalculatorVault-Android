# Task I3.2-B 准备及最小代码交付

日期：2026-11-02。仅实施联调开关、客户端装配、目标限制与脱敏诊断；未修改试用72h/cap/过期粘性、标识生成、存储/Keystore/noBackup、微信、权益或支付业务规则。

## 修改文件

- app/build.gradle.kts：新增TRIAL_API_BASE_URL与TRIAL_HTTP_BASE_URL；Debug/Release沿用原API地址，例外为空；localIntegration试用固定192.168.0.107:8081，账号仍8080。独立参数LOCAL_TRIAL_UPLOAD_ENABLED仅在localIntegration读取，严格true/false，默认false；Release上传固定false。
- CalculatorVaultApp.kt：仅设备试用HTTP客户端使用独立配置及trial-only约束；账号客户端未修改。
- HttpAuthApi.kt：新增默认空的trialOnlyBaseUrl；启用时只允许trial路径且URI精确匹配，禁止端口/主机/协议替换及账号请求。仍禁止重定向。增加试用专属整数诊断。
- DeviceTrialRepository.kt：只增加固定阶段/状态/异常编号观察点，不改变计时、cap、重试、隐私判断、存储规则或会话逻辑。
- src/debug、src/release、src/localIntegration/.../session/TrialTrace.kt：前两者无日志实现，只有联调变体输出CV_TRIAL_DIAG。
- LocalIntegrationHttpTest.kt：新增试用客户端错误主机/端口/协议及账号请求拒绝测试。
- 本说明与evidence/task-i3.2-b/。

Network Security Config不需新增规则：localIntegration既有XML已仅放行192.168.0.107主机，端口由客户端精确限制。XML不具备端口粒度；账号客户端仍用8080的精确HTTP例外，试用客户端只允许8081。普通Release不合入此XML，无新增明文例外。

## 构建及安全操作

默认：`./gradlew.bat :app:assembleLocalIntegration`，试用上传false。

仅专用可销毁模拟器测试包：`./gradlew.bat :app:assembleLocalIntegration '-PLOCAL_TRIAL_UPLOAD_ENABLED=true'`。原Debug测试参数不能代替此localIntegration专用参数。不要在现有手机安装此启用上传包。

签名仍由既有人工流程执行，不读取JKS及密码；同一模拟器的重装资格验收须同签名、同用户及稳定Android ID。不增加重置试用入口，不通过换签名/标识绕过记录。

启用后只有取得当前版本有效隐私同意才读真实ANDROID_ID和上传；撤回后不再新发包，旧回包再次校验。已发出的请求可能已经在隔离库建立/收紧记录，不能远程撤回。

## 脱敏诊断

CV_TRIAL_DIAG仅stage/value整数：0=sync触发；2=标识取得且准备调用API（不记录标识）；3=HTTP初始化；4=请求体交给输出流（不等于Server已收到）；5=HTTP状态；6=解析的业务码；7=回复进入校验；8=异常类别；9=TrialStatus.ordinal状态。

异常类别1超时、2连接失败、3域名解析、4参数/响应校验、5IO、0其他；不记录message、堆栈、请求体、响应正文、设备标识、HMAC、Token或Secret。日志输出异常被忽略，不能影响业务。HTTP401/429保留原先提前抛出处理路径，只记录HTTP码，不为诊断改变响应处理去补读业务码。

状态ordinal：NOT_INITIALIZED=0、ACTIVE=1、EXPIRED=2、PENDING_SYNC=3、TEMPORARILY_UNAVAILABLE=4、VERIFICATION_REQUIRED=5。诊断不是成功凭据，最终以状态及隔离Server记录验收。

## 模拟器检查及未执行项目

只读AVD列表发现Pixel_3、Pixel_9；没有运行中的模拟器，仅连接1台物理手机。现有AVD是否包含需保留数据、是否允许销毁均未确认。因此没有可确认可用的专用可销毁模拟器，本轮设备验证阻塞；不自行擦除已有AVD、不改用真机。

本轮未安装任何包、未读取真实Android ID、未实际执行真实标识上传/隐私流程/断网恢复/卸载重装，没有改开发库或正式试用记录。已有I3.2-A合成8081验收与Server重启验证由用户确认，本轮不把这些结果计为真实设备标识联调通过。

后续先提供专用可销毁AVD或另行安排新建专用AVD，再明确其serial；安装前必须检查ro.kernel.qemu=1及AVD名，ADB所有操作显式指定该emulator serial，不使用无目标的adb install或卸载命令。

## 专用模拟器待验收步骤

1. 核验专用AVD/serial、启用包常量：账号8080、试用8081、试用上传true，确认隔离Server s32it/测试库就绪。
2. 全新专用安装：未同意隐私时不读标识、不发试用请求；同意后确认8081真实设备试用请求、cap及响应。日志只收集CV_TRIAL_DIAG，不输出标识或凭据。
3. 断网期间倒计时继续，联网恢复考虑成功15分钟冷却和失败退避，确认期限不增加；账号登录退出不改变设备试用资格。
4. 到期与重装只在专用空白安装执行，不能更改产品时长或重置标识。现有生产流程没有短期cap测试入口，必要时由隔离服务维护者仅对专用测试记录安排只收紧的短期fixture，并核对不涉及真实记录；未经确认不直接改库。
5. 同签名卸载重装后联网恢复Server资格；重新同意，过期不可复活。卸载会删除专用安装本地数据，须确认目标专用AVD；不得在现有物理手机执行。
6. 正常Release复核上传false、无本地HTTP例外；结束后普通localIntegration构建恢复默认false。

## 构建/自动化结果

已完成：

- 默认Debug/localIntegration/Release三种APK构建及相关lintVital通过。
- 显式-PLOCAL_TRIAL_UPLOAD_ENABLED=true的localIntegration构建通过；Release生成常量仍false。账号API保持8080，试用API及HTTP精确例外为8081。
- Debug测试353项：349通过、4项显式隔离HTTP测试按预期跳过，0失败/错误。
- localIntegration测试341项：337通过、同样4项隔离HTTP测试跳过，0失败/错误。两变体用例不重复累计为不同测试。
- 新增错误主机、8080端口、HTTPS替换及试用客户端账号请求的拒绝测试通过，无真实联网。
- 编译后TrialTrace.class核对：Debug/Release均不引用android/util/Log，只有localIntegration引用；日志仅固定整数，不接收字符串或Throwable。
- git diff --check通过。

证据：evidence/task-i3.2-b/default-config.json、tests.json、enabled-check.json、default-build.log及enabled-build.log。

显式启用包已单独保存至 `.local-tools/i32b-enabled-emulator-only/app-localIntegration-trial-enabled-unsigned.apk`，仅供后续已确认专用可销毁模拟器，不得用于现有物理手机。没有自动签名或安装。

常规输出已成功恢复默认关闭包，生成常量TRIAL_DEVICE_UPLOAD_ENABLED=false；恢复构建BUILD SUCCESSFUL，证据restored-default-build.log。

默认关闭未签名包：app/build/outputs/apk/localIntegration/app-localIntegration-unsigned.apk。启用包与默认包分目录保存，安装前必须分别核对生成常量/产物哈希。

验收结论：代码与构建、自动化准备完成；专用可销毁模拟器尚未确认，设备端隐私门禁、真实Android ID上传、飞行模式恢复及真卸载重装未执行，不标为通过。等待专用模拟器条件落实后继续，不改用真机。
