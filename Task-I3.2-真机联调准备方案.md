# Task I3.2 设备试用真机联调准备方案

日期：2026-11-02。本轮只读代码/契约检查及方案输出；不改代码、不启用上传、不读取Android ID、不访问或修改真实试用记录、不安装/卸载/清数据。以下方案待确认后实施。

## 已实现功能

- Application首次主进程启动initialize，立即开始本地连续72小时；30秒ticker刷新。记录由独立device-trial-v1 Keystore加密、noBackup存储，seen标记先于发放写入。
- 同启动/同boot优先单调时间；跨boot使用墙钟差，异常回拨/未知boot进入保守验证状态；过期位粘性保留。账号退出不删除试用存储。
- 同步触发：启动、有效隐私同意、网络onAvailable、前台恢复、DataStore变化、进入保险箱。sync先合并并发请求；成功15分钟内抑制重复同步，失败指数退避60秒起、上限15分钟，429退避15分钟。触发事件不是必然发包。
- 上传开关及当前政策有效同意先于标识读取；provider另用runIfAllowed保护ANDROID_ID；发包前再次校验、回包后再校验。同意撤回后不接受回包；已发出的请求无法保证远端撤销。
- API匿名POST /api/v1/trial/device/activate，X-App-Code=calculator_vault，JSON仅deviceKey、remainingSecondsCap；无Bearer、微信code、PIN、媒体、应用锁列表。
- cap=floor(clamp(本地剩余毫秒,0,259200000)/1000)。已到期、未知/损坏记录或uncertain传0，每次重试重新计算。cap0可能永久收紧该标识的Server记录，不能在含正式记录的库上随意试验。
- 成功合并取本地已消耗剩余与Server剩余的较小值，并限制本次cap、扣除请求单调耗时；Server到期、本地到期、零剩余均不能复活。Server最早到期及锚点持久化。异常不清空用户私密数据。

## 当前阻塞与权益边界

1. app/build.gradle.kts中localIntegration明确固定TRIAL_DEVICE_UPLOAD_ENABLED=false。此前微信登录联调刻意禁止设备标识上传，防止登录验收意外建立/收紧试用记录；普通Debug参数不能覆盖该值。
2. CalculatorVaultApp的deviceTrial使用HttpAuthApi(API_BASE_URL, DEBUG)，没有账号客户端的localHttpBaseUrl参数。localIntegration DEBUG=false，虽然专用Network Security Config放行192.168.0.107，客户端运行时仍拒绝HTTP。只改上传开关不足以联调。
3. 现有手机可能已自然消耗72小时；不能为测试重新生成完整期限，开启后可能直接传0。当前本地时间和记录未读取，不宣称该机一定过期。
4. 需确认192.168.0.107:8080指向隔离测试库、calculator_vault启用、HMAC Secret有效且整个测试窗口稳定。微信登录成功不证明试用HMAC配置有效。不得更换Secret借此绕开旧资格。
5. 试用状态当前主要被VaultMainScreen消费作状态说明和AccountUiState展示；未检出统一的功能权益授权层使用该状态。AccountUiState明确是展示模型，visibleMembership在游客时取设备试用、登录时取账号会员。不能宣称已经落实“有效VIP > 有效设备试用 > 免费功能”的统一业务门禁。此为范围缺口，先记录，不在本轮接入会员或修改功能拦截。
6. TEMPORARILY_UNAVAILABLE仍可显示保守剩余，本地离线试用不会因网络异常直接作废；VERIFICATION_REQUIRED剩余展示0。登录前后会员卡内容可能变化，不代表设备试用记录变化。应同时核对独立试用状态说明。

## S3.2契约核对

只读核对Server DTO、DeviceTrialService和Repository SQL：cap为可选Long、0～259200；首次按Server now+cap写入期限，后续原子LEAST，保留原started；0立即过期，expires允许等于started；旧客户端缺省cap不延长已有记录。Android本次始终传cap，响应解析code=0/data/status/三个Instant/remainingSeconds，校验72小时上限及EXPIRED语义，支持零长度区间，兼容。

已知错误：40007设备key、40010 cap非法、42901限流、50002 HMAC配置缺失；另有App不存在/禁用/缺少上下文错误。当前HTTP客户端对非成功一般只抛AuthFailure，试用Repository统一降级，缺少真实联调时的业务码观察；建议测试进程记录状态/业务码与异常类型白名单，不记录标识、请求/响应正文或Token。

信任边界：相对cap仍来自不可信客户端，Server不证明离线历史；Server收到首次请求时刻加cap会引入传输延迟量级偏差，当前安装保留更严本地期限。首次联网前卸载导致历史丢失不可追溯；重装后离线也无法恢复Server已知资格，必须联网。相同标识恢复不保证跨用户、签名变化或设备重置。

## 最小改动建议（未实施）

A. 优先采用无真实数据风险的合成联调：新增仅测试代码的instrumentation harness，使用现有DeviceTrialRepository和HttpAuthApi，注入Fake Clock、内存TrialStore及合成deviceKey，连接隔离S3.2实例。应用正常启动上传仍false；测试Repository自行显式启用，不读取真实Android ID、不触碰正式trial存储，也不依赖微信。不会新增正式APK假试用入口。

B. 确认需要验证真实Android ID链路后，再让localIntegration支持独立显式参数（例如LOCAL_TRIAL_UPLOAD_ENABLED，默认false），并只给其设备试用客户端传现有LOCAL_HTTP_BASE_URL。Release保持固定false、空HTTP例外，原配置与微信流程不改；Debug既有规则不改。对专用空白测试设备及隔离库使用，不对当前含真实数据手机直接开启自动上传。需要验证现有手机时，必须先明确其标识上传和测试记录写入授权；本轮未获得即不执行。

两项必要生产源码改动仅为联调开关及试用客户端网络装配；不改cap/72小时规则，不改存储命名或标识，不添加清除/重置资格功能。构建验证应覆盖默认关闭、显式开启仅localIntegration、Release仍false、精确HTTP地址及APK隔离。

## 具体测试步骤与预期

第一阶段：当前手机仅进行不接触原记录的合成测试，不卸载、不清数据、不改手机/Server系统时间。

1. 确认隔离S3.2实例/数据库，启用calculator_vault和独立测试HMAC配置；不复用正式库。准备多个清晰标注的合成fixture标识，各代表不同测试案例，并非重置当前设备标识。测试数据仅可在隔离库管理。
2. 测试Repository空内存Store、Fake Clock起点T0、上传先关；initialize应剩72h，未同意时provider调用0、网络请求0。
3. Fake Clock前进48h再启用测试Repository上传及有效同意，首次cap约86400秒；检查Server持久化期限、响应和Android更严格剩余。重复触发验证并发合并，成功15分钟冷却需Fake Clock推进或新测试进程，不能误判为漏发。
4. 断网/停隔离服务，推进测试时钟，验证本地计时继续、临时不可用及退避；网络恢复后仅使用当前cap，同步不延长期限。超时等采用Fake HTTP/可控代理测试，避免生产网络变更。
5. 单独fixture初始化并前进73h，首次联网应cap0、EXPIRED、零长度Server区间；再发更大cap仍不复活。该零剩余合成测试无需真实等待72小时。
6. 需要观察真机秒级倒计时结束时，可在隔离库对应合成fixture通过既有activate发送cap60（仅收紧），随后让测试Repository同步该fixture；保持72小时产品配置不变。等待超过60秒并触发refresh，确认EXPIRED。已有15分钟成功冷却需在测试harness用时钟推进/新实例安排同步，不在业务代码强行跳过。
7. Fake Store丢弃重建、同一合成标识，模拟重装资格恢复；Server已到期不得复活。该测试验证协议/Repository恢复，不冒称真实卸载验收。
8. 撤回测试同意时验证新上传停止、迟到回复不写回；通过纯测试会话登录/退出观察trial Store不变，不修改已验收微信流程。失败不删除媒体、PIN或应用锁配置。

第二阶段：仅专用可销毁测试设备/模拟器，确认后使用B方案。

9. 人工既有正式签名localIntegration，复核变体、AppID、192.168.0.107:8080、显式开关；首次启动和隐私拒绝/同意抓取仅状态计数，确认真实ID读取时机及试用POST。不要输出完整ID或HMAC。
10. 离线/重新联网、冷启动、后台恢复、真实登录与退出，核对trial起点及Server到期不增加；当前含私密数据手机不做卸载测试。
11. 真卸载重装只能在明确允许销毁的专用测试安装上执行：同签名/设备用户/标识稳定，先同步隔离Server短期或过期记录，卸载后重装再联网确认恢复。此步骤删除专用安装数据，必须单独确认；在“不清除现有真机数据”约束下，现有手机的真实卸载重装应标未验证。
12. 普通Release仅做静态构建检查：固定上传false、不含联调HTTP规则，不能通过测试参数开启。不发起任何正式记录请求。

## 仍需联调及当前验收状态

待验证：真实S3.2请求/持久化一致性、HMAC配置、真机HTTP例外、隐私门禁运行时行为、网络退避、Server上限校准、合成到期及重装恢复、专用机真实标识及卸载恢复。账号切换与试用独立性要核对记录而非仅会员卡。

现有I3.1证据为历史Fake自动化通过，未把它当作当前真实联调通过。本轮未运行构建或自动化，未读/上传任何真实设备标识，未修改Android或Server代码、数据结构及试用数据。

建议先批准A方案（合成身份+隔离库+注入时钟），再单独确认B方案及专用设备。统一功能权益拦截不在本轮自动开发范围内。等待确认后再实施。
