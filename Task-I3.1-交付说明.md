# CalculatorVault · Task I3.1 交付说明（契约核对与阻塞方案，待确认）

日期：2026-10-28（Asia/Shanghai）

## 一、当前状态

**I3.1 未完成，按用户要求暂停相关实现，等待契约方案确认。**

已核对当前 Android 和 AuroraAppServer 的真实实现，并用当前 Android Repository 做了离线、非生产 Fake 探针。确认现有 S3.1 接口不能同时可靠保持“首次离线打开起72小时”与“卸载重装恢复同一资格”这两个要求。此处不是 Server 重复激活幂等失败，而是 Server 尚无本地已消耗上限的持久关系。

未修改 Android 产品源码/Gradle/五份隐私文件，未修改 AuroraAppServer 或数据库，未启动服务/执行真实 activate，也未读取或发送真实 Android ID。当前有已连接 Android 设备，但未确认其为专用非生产测试设备，没有安装、清数据或读取该设备标识。

## 二、实际接口契约核对

核对来源：Server DeviceTrialController、DeviceTrialActivateRequest、DeviceTrialResponse、DeviceTrialService、DeviceTrialEntity、DeviceTrialRepository、DeviceKeyHasher、ApiErrorCode、安全配置及既有 DeviceTrialIT；Android HttpAuthApi、DeviceTrialRepository、AndroidTrialInfrastructure、Application 和 Gradle 配置。Server 只读检查，git 工作区无修改。

```http
POST /api/v1/trial/device/activate
X-App-Code: calculator_vault
Content-Type: application/json

{"deviceKey":"应用范围设备标识"}
```

- 游客端点，不需要 Bearer；Android activate 路径不传 Token、微信 code、PIN、媒体或应用锁列表。
- 请求 DTO **只有 deviceKey**，没有本地首次打开时间、已消耗时长、剩余上限或服务器上限确认字段。
- Server 校验去空格后长度8～128和字符范围 `[A-Za-z0-9._\-:]+`。
- Server 按 App Code 校验活动应用；HMAC-SHA256输入为 appCode、零字节分隔及 deviceKey，以 appId+deviceKeyHash 查询。未读取或复制 HMAC Secret。
- 已有记录直接返回；无记录时以服务端 `clock.instant()` 同时设置 firstSeenAt 和 trialStartedAt，trialExpiresAt=此时+72小时。并发创建后恢复已有记录。
- Android ID 是试用标识，不是账号认证凭据或不可伪造硬件证明。

响应信封：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ACTIVE",
    "trialStartedAt": "2026-01-01T00:00:00Z",
    "trialExpiresAt": "2026-01-04T00:00:00Z",
    "serverTime": "2026-01-01T00:00:00Z",
    "remainingSeconds": 259200
  }
}
```

status 为 ACTIVE/EXPIRED；时间为 Instant；remainingSeconds 为非负秒。Server明确到期返回EXPIRED及0。40007为设备标识无效，42901/HTTP429为限流，50002为试用HMAC配置缺失。当前Android还核对HTTP/信封、解析时间和范围，网络错误不直接解释为到期。

## 三、已复现的阻塞问题

### B-01：本地保守上限未进入Server，重装可复活已到期试用

以首次离线打开为T0：

| 时间 | 行为 | 本地/Server结果 |
|---|---|---|
| T0 | 第一次离线打开 | 本地截止T0+72h，Server尚无记录 |
| T0+48h | 首次联网激活 | Server截止T0+120h（首次联网+72h）；本地取min，仅剩24h，当前安装正确 |
| T0+73h | 原安装刷新 | 已超过本地截止，状态EXPIRED |
| T0+73h | 卸载重装后，用稳定的同一设备标识联网 | 本地记录及存在标记均已删除，新本地周期剩72h；Server返回同一条尚余47h的记录；合并后变为ACTIVE、剩47h |

当前生产Repository第108行取本地与Server剩余较小值，只能在原本地上限仍存在时保护它。重装后上限不在，Server并不知道原本地到期时间。Server幂等正常也无法补齐这条信息。

这违反“已过期设备不能因重装重新计时恢复”，不能把它标成成功的卸载重装资格恢复。若用户在首次联网前就卸载，连Server观察记录都不存在，更无法由当前接口追溯离线首用。

### B-02：现有测试上传参数会同时影响Release

`app/build.gradle.kts:50` 在defaultConfig定义TRIAL_DEVICE_UPLOAD_ENABLED，读取同名Gradle属性。虽然当前Debug和Release实际均false，但使用 `-PTRIAL_DEVICE_UPLOAD_ENABLED=true` 会配置两个变体，未满足“Debug联调配置不进入Release”。Application也直接使用该字段，没有额外Debug限制。

后续实现前应拆成明确的Debug专用参数，Release强制常量false，并测试在Debug参数为true时Release仍false。此次没有通过该参数开启任何变体，也没有修改配置来掩盖B-01。

## 四、建议兼容方案与安全边界（需确认，尚未实现）

### 方案A：先评审Server兼容扩展，持久化只减不增的有效上限（建议）

保持设备+应用一次72小时和首次打开即本地计时，不把客户端墙钟当作可信历史。建议由Server负责人评审：

1. 在试用激活/校验契约中，兼容接收本地保守剩余上限或已耗尽信号。建议使用相对剩余约束而非直接相信客户端提交的绝对首次打开时间；字段名和格式尚待确定，**不是当前已有字段，当前不会发送**。
2. Server使用自己的时间基准，将该信息作为“只能减少资格”的约束，与已有截止时间取更早的有效上限并持久化；任何后续请求、旧客户端、重复调用、并发及乱序都不能延长已记录的截止上限。
3. 首次激活与上限持久化必须定义原子性。不能先生成完整72小时，再另发一个可被中断的补写请求，否则进程退出/卸载仍可能留下完整周期。
4. 本地已到期的0秒必须能在Server形成可恢复的到期结果。须同时评审响应中的起点/截止/状态语义和Android校验：当前Android要求expires>started，不能简单把新记录expires设成started再声称已兼容。
5. 需定义首次未同步、老客户端/已有Server记录的迁移、服务端时间锚点、网络时延下的保守处理，以及真实上限落库的确认行为。不能把一个未经确认的本地cap当成服务端已保存。
6. 当前表已有时间字段，但是否能仅利用现有结构、是否需要额外状态及如何兼容，应由Server设计评审决定；本任务没有自动更改接口、表结构或强行重释已有字段。

安全边界：

- 本地剩余上限是客户端约束，不是可信时间证明或设备证明。Server只允许缩短、不允许据此延长，不能宣称可防止恶意修改客户端或伪造标识。
- 在有效上限已经由Server确认并保存后，同标识重装应恢复该上限，而不是原来晚激活的完整周期。
- **首次联网前卸载且本地状态全失时，Server无法知道此前离线首用。** 没有可信外部记录，无法同时保证完全离线可用、完全丢失本地数据后仍精确追溯首用。本边界必须人工确认，不以信任手机时间、备份设备标识或新增敏感硬件标识掩盖。
- 对超长离线/重启/回拨无法建立可靠基准的情况继续待验证/受限，不任意发新72小时。

此方案需先确定协议与迁移细节，不代表本次已获授权修改Server；确认后也不能绕过“只使用测试环境、无生产标识/Secret”的要求。

### 方案B：只继续现有接口的受控连通验证，保留资格恢复为未验收

可先在明确的Debug测试配置和合成设备标识下验证POST、幂等、错误、隐私和超时，保留当前安装内min策略；但必须明确B-01尚未解决，不能将I3.1整体验收为通过，不能把既定72小时规则改成首次联网起72小时。

该方案只是缩小验证范围，不是满足全部产品规则的替代实现。若选择它，需要用户明确接受本轮仅做接口连通及分阶段验收。

不建议为了绕过问题改成“首次必须联网才有试用”或重新信任客户端首次打开时间，这会改变既有离线规则或引入不实可信假设。

## 五、当前本地/Server合并行为核对

- 首次打开尽早创建本地记录，未同意前不为上传读取标识。
- 同次开机/当前进程主要用elapsedRealtime扣减；重启结合本地锚点，明显回拨/无法建立基准进入验证。
- 当前安装内取本地剩余与Server剩余的较小值，并保守扣除请求在途时间；已持久化的本地到期不被ACTIVE响应重新激活。
- Server明确EXPIRED强制到期；网络异常保留待验证/暂不可用，不清媒体、PIN或应用锁数据。
- 微信账号不参与DeviceTrialRepository设备身份和试用起点，账号切换不重置该记录。
- 隐私当前版本门禁、上传前身份读取保护、响应后重新检查仍存在；上传false时在身份读取前短路。
- 缺口在卸载导致上述本地上限丢失后，Server并无同一有效上限可恢复。单靠Android再取一次min无法修复。

## 六、本次修改文件与测试结果

**Android产品修改文件：无。** Gradle、上传开关、API实现、试用合并、五份政策及Server均未修改。只新增本说明与 `evidence/task-i3.1/`。

为验证问题，采用外部Gradle init注入临时测试源，不写入app/src，不打包到APK，不改变正式测试入口。测试使用Fake Clock、Fake DeviceIdentityProvider、Fake TrialApi，并调用当前真实DeviceTrialRepository，不读取Android系统标识或访问真实Server。

定向契约探针2项：

- 离线48小时后首次联网，保持24小时上限：通过。
- 原始首用72小时后已到期，稳定身份重装不得复活：**失败**，实际ACTIVE且剩169200000ms，即47小时。

该失败是对既定产品规则的真实断言，不是测试工具故障，也没有改成“允许47小时”来让它通过。此探针不证明真实Server/设备E2E，但结合真实契约和生产Repository，足以证实当前信息关系无法满足该组合场景。

移除临时注入后重新运行原有完整基线：**336项通过，失败/错误/跳过均0**。专项2项与普通基线分别记录，不宣称338项全部通过。早期临时源接入时未找到测试的工具尝试不计入最终两项结果，最终探针已实际执行并保存XML。

普通基线命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:generateDebugBuildConfig :app:generateReleaseBuildConfig
```

探针复现命令（预期有1项失败，用于展示阻塞，不是产品默认测试）：

```powershell
.\gradlew.bat -I evidence/task-i3.1/contract-probe.init.gradle :app:testDebugUnitTest --tests com.aurora.calculatorvault.feature.trial.I31OfflineReinstallContractProbe
```

本轮没有为未完成的实现构建/安装新联调APK；没有进行真实首次激活、重复激活、超时或重装Server E2E。当前实际生成Debug/Release上传字段均仍false。

## 七、开发环境与剩余联调步骤

真实Server联调：**未执行，因B-01契约需先确认**。未启动或修改Server/Docker/数据库，未使用生产标识、用户数据、Token或HMAC Secret。

连接设备不等于已确认专用测试环境；当前没有读取其Android ID。确认方案后，还需：

1. 修复Debug/Release开关隔离，明确测试URL；Release强制false，验证Debug opt-in为true时Release仍false。沿用既有API Base URL，不另建HTTP框架。
2. 首先用明确的合成测试标识对本地/测试Server验证，避免误用当前连接设备的生产身份。测试Secret由测试Server安全环境自行提供，不进Android或提交记录。
3. 覆盖首次/重复激活、晚首次联网、原本地已到期、Server到期、超时/异常、回拨、隐私拒绝/撤回和迟到响应、账号切换。
4. 对Server持久化上限、幂等、并发/乱序及0秒状态追加双方契约测试；不只验证返回HTTP200。

## 八、卸载重装真机验收步骤（待方案确认后执行）

1. 使用明确的专用非生产测试设备、同一Android用户及同一签名，先记录试用资格/有效截止；不要用真实私密数据。稳定标识只是条件，不宣称所有系统迁移/恢复出厂/签名变化都稳定。
2. 完成当前版本明确同意，在受控Debug配置向测试Server同步，并确认有效上限已被Server保存。
3. 记录服务端与客户端有效截止后卸载；卸载会删除应用私有文件和密钥，先确认无需保留真实媒体/PIN数据。重装同签名，重新同意，联网读取同标识记录。
4. 比较重装前后有效截止：不得向后移动，已经到期仍为EXPIRED；随后切换测试微信账号，资格不变。真实微信条件不具备时先用既有Fake会话测试，不添加正式假登录入口。
5. 离线首次使用再晚联网的关键边界优先用Fake Clock/测试环境可控时钟，不能通过修改真实设备时间或等待数天冒充可靠验证。
6. 首次联网前已卸载、标识变化、本地记录损坏的不可追溯场景单独记录，不列为“已精确恢复首用”。不因此删除私密数据或自动再发完整本地周期。

以上均为待执行清单，不是已通过结果。

## 九、等待确认的决策

建议选择 **方案A：先评审只减不增上限的兼容契约，并明确首次联网前卸载的不可追溯边界**。如暂不调整Server契约，只能按方案B进行局部受控连通验证，I3.1资格恢复仍保持未完成。

按本任务“遇到无法兼容先输出问题和方案、暂停相关实现”的要求，到此停止等待确认；不修改Server、不启用正式上传、不进入会员/支付阶段。
