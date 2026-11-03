# Task I3.2-A 8081隔离联调交付说明

日期：2026-11-02。本报告替代此前“隔离端点未提供”的阻塞结论；保留先前报告作为历史。

## 结果

使用用户确认的s32it隔离实例http://192.168.0.107:8081。真实HTTP实际执行4项并全部通过；其余348项Fake/本地自动化通过，总352项，失败/错误/跳过均0。instrumentation harness编译成功，未安装或执行手机端测试。本轮没有使用真实Android ID、修改现有手机数据、修改正式记录或请求8080。

## 文件清单

- 修改 `app/src/androidTest/java/com/aurora/calculatorvault/feature/trial/IsolatedDeviceTrialHttpTest.kt`：只接受精确http://192.168.0.107:8081，不能用其他端口/主机/8080。保留合成UUID、Fake Clock、内存TrialStore和显式环境确认。
- 新增 `app/src/test/java/com/aurora/calculatorvault/feature/trial/IsolatedDeviceTrialJvmHttpTest.kt`：开发机上可直接执行的真实HTTP版本，复用原DeviceTrialRepository及HttpAuthApi；额外覆盖更短Server上限、真实回复迟到后的同意撤回。
- 新增 `evidence/task-i3.2-a/enable-isolated-http.init.gradle`：仅显式使用此测试init脚本才为测试JVM设置隔离确认参数；不改正式构建配置。
- 新增本报告与 `evidence/task-i3.2-a/real-8081/` 验证证据。

本轮没有更改生产源码、普通Release、localIntegration正常上传开关、HTTP放行资源、微信及其他业务。此前Fake60秒测试等准备成果继续复用。

## 执行方式与网络隔离

实际执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --init-script evidence/task-i3.2-a/enable-isolated-http.init.gradle
```

BUILD SUCCESSFUL，完整测试352项通过，instrumentation打包通过。真实测试默认Assume跳过；普通测试命令不传init脚本不会自动访问8081。固定地址只存在测试源码，未写入正常App配置。

真实HTTP运行于开发机JVM，调用同一Android Kotlin Repository和HttpAuthApi生产实现，使用真实网络和S3.2响应；不等同于手机端Network Security Config运行时验收。测试客户端显式允许固定HTTP地址，不改变App网络安全策略。正常App仍不上传试用标识，localIntegration仍固定false。

## 场景分层结果

| 场景 | Fake/本地回归 | 本轮真实8081 |
|---|---|---|
| 初次初始化连续72小时 | 通过 | 通过，Repository断言 |
| 无同意不读取标识/不上传 | 通过 | 通过，注入provider/API计数0 |
| Fake推进48h首次cap86400 | 通过 | 通过，记录实际交给HttpAuthApi的cap |
| Server更短上限优先 | 通过 | 通过，预先合成fixture cap120，Repository采用更短剩余 |
| 并发合并及15分钟冷却 | 通过 | 通过，重复触发只调用一次；推进Fake时间再同步 |
| 离线与恢复不延长 | 通过 | 通过，用传输包装层注入IOException模拟离线，恢复后真实HTTP并比较期限 |
| Fake推进73h cap0/EXPIRED | 通过 | 通过，Server started==expires |
| 到期后提交更大cap不复活 | 通过 | 通过，真实HTTP再发259200仍EXPIRED |
| 60秒到期 | Fake Clock通过 | 通过，Server cap60后自然等待61秒，期限保持且EXPIRED |
| 同合成标识模拟重装 | 通过 | 通过，重建内存Store后HTTP恢复仍EXPIRED |
| 撤回后不上传/迟到不更新 | 通过 | 通过，真实HTTP返回后在测试包装层暂存响应、撤回测试同意再释放；verified/serverExpires不写回，后续请求计数不增 |
| 登录退出与设备记录独立 | 既有Fake账号回归通过 | 未请求真实微信登录/退出，不宣称此组合真实HTTP已验收 |

“真实8081通过”不是每行单独一个@Test；4个真实测试方法组合覆盖上述场景。离线为测试传输故障注入，不代表真实切换手机飞行模式；迟到为真实回复后测试包装层延迟交付，不代表Server网络故障模拟。

Fake时钟只驱动Android本地消耗/冷却，Server继续真实时间；断言使用Server返回的started/expires、相对cap和只收紧比较，不拿Fake墙钟当Server绝对时间。73h在首次联网前推进可确定cap0；60秒用例实际等Server时间流逝，无改时钟、改产品72h规则或重置记录。

## 数据库持久化核对

按用户已确认的aurora-mysql-it / aurora_app_test隔离环境，执行docker容器内只读聚合SQL；只返回计数，不读取原始设备标识、哈希或账号行，也不输出数据库凭据。

最终观察：device_trials共324行，其中318行到期，4行started==expires。运行中曾观察到321/316/3；该观察不是测试开始前的稳定快照，不据此宣称本轮准确增加3行或4行，也不排除其他测试活动。

真实HTTP测试对同一随机合成标识重复读取，确认已有期限保持/收紧及EXPIRED不复活，提供接口层持久化行为证据；Server实际SQL/事务实现及上游冒烟已由用户确认。

**未完成的独立持久化验证**：逐fixture哈希/行级SQL关联、并发数据库无其他测试的计数差核对，以及Server进程重启后读回。聚合计数不单独证明某行属于本次fixture；因此数据库层属于部分核验，不宣称上述三项通过。没有擅自重启服务或清理记录。

## 安全与遗留项

- 合成key是i32a-前缀随机UUID，各场景独立fixture；同一场景重试/重装保持同key。没有读、更换或重置真实设备标识。
- 测试仓库只持有内存Store和注入Clock，不接触现有手机Keystore、seen标记、媒体、PIN或应用锁配置。
- 未安装测试APK，未签名、卸载、清数据或改系统时间；正常App运行配置不变。
- 未输出code、Token、Authorization、AppSecret、HMAC Secret、完整请求体或真实用户信息。
- 剩余：专用模拟器instrumentation运行、手机网络策略验证；逐fixture数据库读回/重启验证；真实账号操作与试用组合观察。均不能从本轮开发机HTTP结果自动推定通过。
- 最小建议：服务端维护者对本轮独立合成fixture补充作用域明确的只读行级断言及隔离实例重启读回；后续如需手机运行，先指定专用模拟器，不用当前含数据手机。无需为通过这些测试修改生产业务规则。

证据：`evidence/task-i3.2-a/real-8081/verification.json`、`build-and-tests.log`及真实测试JUnit XML。完成后停止，不进入I3.2-B、I3.3或统一权益模块。
