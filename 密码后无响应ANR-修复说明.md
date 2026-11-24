# 输入密码后无响应 ANR 修复

日期：2026-11-24。

已按用户确认在当前物理手机复现，读取的ANR原因是MainActivity等待输入事件超过10001ms。仅保存应用代码主线程位置，不记录密码、Token或日志原文。

主线程链：AppLockViewModel新增权益collect → ActionPolicy.evaluate → DeviceTrialRepository.refresh → EncryptedTrialStore.write → KeystoreTokenStore.write → Android Keystore Binder。evaluate同时发布新EntitlementState，订阅再次调用evaluate，构成反馈循环，反复写入Keystore并堵塞主线程。问题由上轮展示修复引入，不能归因于用户密码或数据损坏。

修复文件：

- AppLockViewModel.kt：权益展示订阅只读快照，权限刷新、锁列表观察、监控状态同步中的展示读取同样不再调用有副作用的evaluate。实际toggleLock授权命令继续使用原ActionPolicy，不改变权益授予或试用规则。
- 新增AppLockPresentationEntitlement.kt：Flow<EntitlementState>映射现有hasPaidEntitlement并distinctUntilChanged；不持有Repository/Store/策略，不写上游。
- 新增AppLockPresentationEntitlementTest.kt：验证倒计时快照变化不会重新发相同展示权益，过期/恢复正确传播、读取无额外上游事件；配合既有应用锁/权益测试确保ON/OFF和原配置保留。

87项相关测试全部通过，Debug与Release最终构建成功。证据：evidence/applock-anr-fix/，含脱敏ANR代码栈、回归XML、最终构建日志和未签名Release哈希。

未清数据、卸载、更改密码、删除媒体、改变签名或读取正式JKS。当前真机仍是旧正式签名包，修复尚未覆盖安装，因此不能宣称修复后的真机入口已验收。已有app/release/app-release.apk经检查不含新修复helper，不能重新安装旧文件冒充验证。

修复未签名包：app/build/outputs/apk/release/app-release-unsigned.apk。需用户沿用既有正式JKS在Android Studio签名，并回复实际APK路径；不需要提供密码。随后先校验证书及修复内容，再同签名保留数据覆盖安装，由用户自行输入密码，观察进入保险箱/应用锁页与ANR是否重现。保持原Release环境参数和签名，不使用Debug包覆盖正式签名应用。

本轮只执行相关回归，未重新运行已知存在其他失败的整个JVM套件。手机上的最终复测等待正式签名修复包，不将编译通过等同真机验证通过。
