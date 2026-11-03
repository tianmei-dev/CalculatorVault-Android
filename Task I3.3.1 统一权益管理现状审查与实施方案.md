# Task I3.3.1 统一权益管理现状审查与实施方案

日期：2026-11-02（本轮复核）。仅审查与设计，未修改业务代码，未运行构建/测试/应用，未操作设备、数据库或网络试用接口。文件路径均相对 `D:/Android_Work/CalculatorVault-Android`；下述“事实”为本轮静态核对，“建议”为待批准设计。历史微信/I3.2验收由用户确认，本轮不重复标记为新运行结果。

## 1. 结论

**事实：目前是独立账号会话、独立设备试用和展示型会员模型，没有统一权益管理。** 应用锁真正监控/Overlay与相册 Repository 没有接入 VIP/试用授权判断。试用到期只更新状态/文案，并不能让已经运行的应用锁停止保护，也不能阻止相册新增导入。

没有 AccessibilityService 实现或 Manifest 注册。当前应用锁是 UsageStats + `specialUse` 前台服务 + Overlay；不能为满足本阶段而新增无障碍服务。

Android 没有真实 VIP 权益查询来源。微信登录成功只得到 `userId` 和应用会话，不能当成 VIP；VIP 模型、套餐价目及购买页面不构成会员证明。

DI 为 `CalculatorVaultApp` 手工 `lazy`，无 Hilt/Koin。`VipPurchaseSource.Settings` 是生产唯一 VIP 导航；`AppLock` / `PrivateAlbum` / `TrialExpired` 仅枚举预留。

## 2. 代码结构与事实

| 模块 | 关键文件 | 已验证事实与缺口 |
|---|---|---|
| 设备试用 | feature/trial/DeviceTrialRepository.kt、AndroidTrialInfrastructure.kt | StateFlow；主进程首开72h；Keystore/noBackup；单调计时、跨boot保守处理；cap向下取整、取更严期限、过期粘性；不依赖账号。无统一能力输出 |
| 启动/DI | app/CalculatorVaultApp.kt | 手工lazy DI、Application scope(SupervisorJob/Main.immediate)；启动初始化、30秒ticker；同意、DataStore变化、网络onAvailable、前台恢复触发sync；受开关/隐私/冷却/退避控制。不是所有触发都发请求 |
| 账号 | feature/login/session/AccountSessionRepository.kt、HttpAuthApi.kt、KeystoreTokenStore.kt | Guest/Authenticating/Validating/Authenticated/Unavailable/Expired等；Bearer恢复、/me校验、退出本地先清身份、generation拒收旧响应、token到期watchdog。无VIP字段或会员查询接口 |
| 授权 | feature/login/domain/WeChatAuthorizationCoordinator.kt、wxapi/WXEntryActivity.kt | state及一次性交接；CodeReceived不是已登录。微信授权结果不授予会员 |
| UI订阅 | feature/vault/presentation/VaultMainScreen.kt | 直接collect trial与account StateFlow，页面进入sync；按remainingMillis映射TrialActive，按Authenticated映射账号 |
| 账号会员展示 | feature/settings/presentation/AccountUiState.kt、SettingsAccountContent.kt | MembershipStatus有TrialActive/Expired/VipActive/Permanent等展示型字段。accountMembership默认NotSubscribed，visibleMembership对游客取trial，对登录用户仅取accountMembership；不符合“无VIP但有效trial仍可用”的统一优先级 |
| 套餐/购买占位 | feature/membership/domain/VipPurchaseModels.kt、presentation/VipPurchaseViewModel.kt | Monthly/Annual/Lifetime本地展示价目；requestPurchase只返回PaymentUnavailable，不写会员。不要读取plan/价格/点击购买结果来推导VIP |
| 免费应用管理入口 | feature/vault/presentation/VaultMainScreen.kt、feature/hiddenapp、core/navigation/AppRoute.kt | 应用管理及应用锁、相册导航分离，现有未见统一权益限制；应用管理应继续免费。保险箱PIN/隐私守卫仍保留，“免费”不等于绕过安全认证 |
| 应用锁配置 | feature/applock/data/AppLockEntryEntity.kt、AppLockDao.kt、AppLockRepositoryImpl.kt | Room enabled与包名配置持久化；observeLockedPackages根据enabled及包策略过滤。setLocked只校验包策略/保存，没有权益条件 |
| 应用锁入口 | feature/applock/presentation/AppLockViewModel.kt、AppLockScreen.kt | toggle检查使用情况/悬浮窗权限，保存后启动监控；syncMonitorState按锁列表及权限启停，无权益订阅。拒绝通知权限不等同于不能运行 |
| 实际保护 | feature/applock/service/AppLockMonitorService.kt、domain/ForegroundAppDetector.kt、AppLockSessionManager.kt | UsageStats轮询250ms，最近5s窗口，匹配锁列表、临时解锁和验证状态；onStartCommand仅校验隐私，START_STICKY；轮询及show只检查privacyWithdrawn，无权益条件 |
| 悬浮验证 | feature/applock/overlay/AppLockOverlayController.kt | WindowManager.addView，异步验PIN后markUnlocked；hide会取消verifyJob、清密码缓冲和view。权益变化尚不触发hide |
| 备用验证页 | feature/applock/presentation/AppLockVerificationActivity.kt、AppLockVerificationViewModel.kt | 现有内部Activity及密码验证路径，当前监控主链使用Overlay；备用页只有隐私/密码/session判断，无权益订阅，不能遗留旁路 |
| 私密相册 | feature/privatemedia/presentation/PrivateMediaViewModel.kt、PrivateMediaScreen.kt | picker请求、回调import、文件夹新建/重命名/移动、预览、选择、单/批删除、单/批恢复均存在；无权益条件 |
| 数据/文件操作 | feature/privatemedia/data/VaultMediaRepository.kt、domain/VaultMediaImporter.kt、SystemMediaRestoreManager.kt、SystemMediaRemovalManager.kt、storage/VaultMediaStorage.kt | 实际导入在Repository逐项写私有文件和DB；DB插入失败有文件清理。恢复通过系统媒体管理器，成功不删当前私密副本；删除是显式独立操作。不能把权限失败等同文件丢失 |

补充事实：PrivateMediaViewModel初始化会ensureDefaultAlbum和cleanupMissingMediaRecords；后者按实际文件缺失清DB记录。接入权益不能让文件访问因权益被拒绝后呈现“缺失”，否则可能误清用户记录。deleteAlbum当前禁止删默认相册、非空相册返回NotEmpty；不能借权益到期改变为自动删除。

Manifest仅有AppLockMonitorService和快捷方式receiver等，无BOOT_COMPLETED恢复接入；未检出Application集中恢复应用锁监控。目前主动恢复主要依赖打开AppLock页面的ViewModel，或已有Service sticky重建。不得承诺权益恢复后在任意后台状态必然自动启动服务。

## 3. 已实现与缺口界线

已实现：本地保守试用和联网校准；账号 Token 安全存储和退出竞态保护；应用锁配置/监控/Overlay 及通知授权流程；相册已有数据管理和系统恢复；StateFlow/协程/DAO/手工 DI 和可注入 Fake Clock 可复用。

缺口：统一有效权益源、真实 VIP 来源、能力级操作策略、账号/试用组合优先级、后台到期主动停保护、恢复时恢复既有意图、跨异步权限/登录/文件操作的权益竞态、独立于页面的权益更新与测试。现有 VIP 使用集中在展示/购买 UI，不存在可复用的真实 VIP 判断；当前风险主要是缺少判断而非已有多个正确判断互相冲突。

### 3.1 重点分析问答（事实 vs 建议）

| 问题 | 代码事实 | 建议方案 |
|---|---|---|
| 应用锁停止保护应在哪一层，才能保证不是只隐藏 UI？ | 今日仅 `AppLockViewModel.syncMonitorState` / 隐私撤回路径停 Service；监控链无权益判断；锁配置在 Room `enabled` | **Application 级 `AppLockProtectionCoordinator` + `AppLockMonitorService` 提交边界**双层控制。失权只停运行门禁，**禁止**改 DAO/`enabled`。仅禁用 Switch 不够 |
| 权益到期后，已运行的 AccessibilityService 和 Overlay 如何停止拦截？ | **无 AccessibilityService**。实际为 `AppLockMonitorService` + `AppLockOverlayController` | 失权：提升 generation → 取消 monitor/show/verify → Main 线程 `hide()` → `clearAll` 会话 → `stop` Service。Overlay 须主动 `hide`，不能只停轮询 |
| 恢复权益后如何恢复保护，且不重复授权、不丢失配置？ | `stop` 会 `clearAll`；无软暂停；无 BOOT 自启；主要靠进应用锁页 `syncMonitorState` 或 sticky 重建 | 重读原 Room 配置；权限仍有效则静默 `start`；**不重弹**已授予权限/通知引导；不保留旧临时解锁；用户关掉的项保持关。FGS 后台启动失败时降级为“前台再恢复” |
| 私密相册如何区分查看/恢复与需权益的新增？ | 全部开放，零门禁 | **保留**打开/枚举/预览/选择/恢复；**门控**系统导入（含建夹后立即导入）。命令边界落在 `importMedia`（及开 picker 前）。删除/重命名/移动/新建夹矩阵见 §9 待确认 |
| Trial 各状态如何安全映射为功能权益？ | 今日只驱动设置文案；`remaining>0` 粗映射 `TrialActive`，不区分 PENDING/TEMP | 见 §4 表：`ACTIVE`/`PENDING_SYNC`/`TEMPORARILY_UNAVAILABLE`（本地锚仍有效）→ 可授试用能力；`VERIFICATION_REQUIRED`/`EXPIRED`/`NOT_INITIALIZED` → 不授。有真实 VIP 时 VIP 优先 |
| 是否存在真实可用的 VIP 来源？如何预留？ | `ServerAccount(userId)` 无会员字段；`accountMembership` 生产恒默认；购买恒 `PaymentUnavailable` | `MembershipProvider` 默认 `NotEntitled/Unavailable`；仅测试 Fake 注入。禁止用登录成功/套餐选择/展示模型当 VIP。未来真实接口另任务核对契约 |
| 如何避免多页面重复写 VIP/试用判断？ | 今日几乎无判断；`VaultMainScreen` 自行拼 trial 文案 | 单一 `EntitlementRepository` + 同步 `ActionPolicy`；UI 只订阅展示；Service/Repository 命令边界再验 |
| 接入时最易回归的功能？ | — | ① 后台到期 Overlay 仍弹出 ② 失权清空锁配置 ③ 相册 `cleanupMissingMediaRecords` 误清 ④ 退出登录误清 trial ⑤ 登录用户有 trial 但 `visibleMembership` 只看账号导致 UI 与能力不一致 ⑥ FGS 后台无法启动却当成失败擦配置 ⑦ 批导入中途到期删已成功项 ⑧ 备用 `AppLockVerificationActivity` 旁路 |

## 4. 推荐统一权益架构（建议，未实现）

在core/entitlement或独立feature/entitlement建立Application级单一EntitlementRepository；combine设备Trial StateFlow、账号StateFlow、未来MembershipProvider和当前政策有效同意/时间源；暴露不可变StateFlow<EntitlementState>及同步ActionPolicy。不把UI MembershipStatus作为输入，不让页面自己拼布尔表达式。

推荐分开三个概念：

- 来源与验证：VIP、DeviceTrial、None；Confirmed/LocallyValid/Pending/Unavailable等证据及account-generation，不能把Unavailable显示成Server明确拒绝。
- 能力：应用管理访问、应用锁实际运行、相册读取/恢复/新增导入等；原有PIN和隐私授权独立叠加。
- 生效条件：锁列表非空、使用情况访问/悬浮窗权限、前台服务启动资格等。这些条件失败不改变用户保存的“想锁哪些应用”。

优先级：有当前账号、已服务端确认且未到期的VIP → 有效设备试用 → 免费。登录/退出只改变VIP候选，设备试用持续；VIP到期立即回落trial，无trial才免费。不要先发Free中间状态再给Trial而产生Overlay闪停，可统一快照原子计算。

设备试用能力映射建议：

| Trial状态 | 无VIP时允许试用能力？ | 条件 |
|---|---|---|
| ACTIVE | 是 | 保守剩余>0且没有expired/uncertain证据 |
| PENDING_SYNC | 是 | 有效本地记录且保守剩余>0，保持首开即离线72h既定规则；不得要求首次必须联网 |
| TEMPORARILY_UNAVAILABLE | 是 | 仅沿用仍有效且可验证时间锚的本地剩余，不赠送时长；网络失败不直接等于过期 |
| VERIFICATION_REQUIRED | 否 | 不确定记录不发放新资格；有真实VIP仍可走VIP分支 |
| EXPIRED | 否 | 粘性过期；VIP可独立授权 |
| NOT_INITIALIZED或剩余<=0 | 否 | 启动恢复前不放行，完成初始化再重算 |

当前DeviceTrialState只有status/remaining，uncertain会呈现0；新统一层应通过明确的只读证据快照/单调deadline保证判断完整，不私自解密Store或绕过原计时规则。政策未有效同意时不启动需要同意的功能/SDK；不因此擦除trial。既有本地数据可查看/恢复仍须满足已有PIN、安全与必要系统权限。

到期时机：当前30秒ticker只适合展示，不能让实际保护额外延迟到下一次UI刷新。统一层基于同一保守单调deadline调度最近到期点；每次功能操作/监控拦截提交前再判断当前时间。屏幕关、CPU挂起后在恢复/下一次监控处理先重算；不为此新增精确闹钟权限。任何定时任务延迟不得让旧快照继续授予新操作。

### VIP来源预留

建议MembershipProvider返回Unknown/Unavailable/NotEntitled/VerifiedActive(accountScope, expiresAt或真实永久证明,验证时间与时钟锚)。接口只描述归一化领域结果，不猜JSON字段或新增Server接口。当前生产默认实现为“未接入/不授予VIP”；仅测试注入Fake，不把登录成功、旧昵称、套餐选择或本地日期转成会员。

未来接已有Server会员接口必须另行核对真实契约并获批准；本任务不接入。缺少真实来源时不能验收“真实会员恢复”，只能用Fake验证设计。

账号Guest/Authenticating/Validating或会话失效不能借旧账号缓存授予VIP；默认暂停VIP候选并回落有效trial。退出立即提升account generation清旧VIP；旧会员请求晚回也不得恢复。Token过期与会员到期是两回事，不发明refresh能力。VIP网络故障可否有已确认离线宽限期尚需确认，当前建议不新增宽限。

## 5. 应用锁具体接入点（建议）

有效保护 = 配置启用 ∩ 当前权益允许 ∩ 有效隐私同意 ∩ 系统权限及运行条件。配置和运行时资格分离。

1. AppLockViewModel.toggleLock在“开启”意图处用统一ActionPolicy返回允许/引导VIP，允许关闭和浏览/编辑已有配置；配置列表不因权益失效变false或删行。setLocked或独立UseCase增加同源命令边界，防止绕开UI直接启用；不把observeLockedPackages改为空集合来伪装用户配置已清空。
2. 新Application级AppLockProtectionCoordinator订阅权益、保存配置及权限/前后台变化。页面不再各自决定实际服务是否应运行，AppLockViewModel.syncMonitorState与之统一，避免两个启动器打架。
3. Service.onStartCommand、START_STICKY重建、轮询匹配到目标、Main线程真正show之前均读取同一有效判定。Application之外Service自身也订阅变更，不能只靠页面collect。即使系统重启Service也不能短暂先保护后再检查。
4. 到期/退出导致失权：先同步关闭运行门禁并提升保护generation，再取消monitorJob、待执行show、PIN验证/临时解锁任务，Main线程hide/removeView、清AppLockSessionManager运行态，stopForeground及stopSelf/既有stop；保持DAO、enabled、通知申请历史、PIN原样。
5. Overlay.show在addView提交前再核验；异步PIN验证完成前核验generation/能力，防止取消不及时的旧任务markUnlocked/重开Overlay。当前hide已有取消verifyJob及清缓冲能力可复用；Main/Default切换与WeakReference.stop需用幂等、线程安全关闭路径，避免show与hide交错。
6. AppLockVerificationActivity若已显示，订阅失权后finish，ViewModel拒绝旧确认结果并清运行态。不能仅停Service，留下已开的验证页阻挡用户。
7. 恢复权益：重新读取原配置；仍有锁列表、系统授权仍有效、隐私有效时恢复检测，不重复弹已授予权限，不触发每次通知授权。恢复时不要保留旧临时解锁以跳过应有验证。用户原来主动关闭的项保持关闭，不能自动全选。

当前没有AccessibilityService，所以无“停止已运行无障碍服务”的实现工作。若未来独立任务引入，应在其事件处理/提交边界共用同一门禁，但不是本阶段开发范围。

前台服务后台启动存在平台限制，不能把“有悬浮窗权限”当作任意后台都能启动的保证。恢复时不符合启动资格应保留配置并显示“待回到前台恢复保护”，在前台/用户操作重试，不循环强启或新增绕过权限。[Android官方限制说明](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

## 6. 私密相册接入点与数据安全（建议）

| 操作 | 无有效权益时建议策略 |
|---|---|
| 打开相册、枚举已有文件夹、缩略图、图片/视频查看、预览切换、选择已有条目 | 保留；不让权益影响文件可见性，不阻止PIN |
| 单/批恢复至系统相册 | 保留，沿用权限、失败处理及私密副本保留规则 |
| 单/批删除私密条目、删除空文件夹 | 建议保留用户主动清理权，继续原有确认及非空限制；须产品确认 |
| 重命名、移动已有媒体到已有文件夹 | 建议作为整理已有数据保留，须确认 |
| 新建文件夹 | 产品待确认；默认相册初始化不可被权益阻断以致已有数据不可达 |
| 从系统新增导入（含新建文件夹后立即导入） | 拦截并引导VIP；不能阻止用户查看/恢复原数据 |
| 导入成功后的删除系统原文件授权回调 | 完成已有合法操作的收尾，不因后来失权拒绝或额外删除；保持用户确认语义 |

PrivateMediaViewModel.requestImportToAlbum/CurrentAlbum在打开picker前检查；picker返回importMedia、createAlbum(importAfterCreate=true)后发OpenMediaPicker前再次检查；最终VaultMediaRepository.importMedia或新ImportUseCase作为唯一命令边界复核。不能只禁用加号、不能只在页面onClick检查。

建议把导入授权、落盘及DB提交定义每项操作的线性边界：已获准启动的当前单项允许安全完成或按原有失败回滚，失权后不再开始下一项；不得为“到期立即生效”删除已完成媒体、留下半文件或清整批数据。全批授权一次还是逐项检查需产品确认；建议逐项，报告清楚成功/受限/失败，不把受限当损坏。

查看、恢复、删除不经过“需VIP才能访问Storage”的总开关；底层storage.privateFile不得因无权益返回null。cleanupMissingMediaRecords继续只处理真实缺失，禁止在权益变化时调用清理。恢复/删除/原文件移除的权限IntentSender回调可能跨Activity生命周期，必须保留操作身份与原有结果处理，不与会员拒绝混淆。

## 7. 状态恢复、并发与DI（建议）

- 初始化先恢复本地trial，再组合账号与未接入会员源；状态未知阶段不给新付费操作，但不锁死旧媒体读/恢复。
- 权益Repository由Application唯一持有，Service和ViewModel通过现有Factory注入；可先继续手工DI，不引入新框架。
- 单独提供可同步查询的ActionPolicy/快照与单调截止点。UI订阅用于展示和引导，业务边界和Service再次验证；UI生命周期间不承担后台执行职责。
- 账号切换、logout、隐私撤回先同步撤销相关来源/提升generation，再等待异步撤销/存储；迟到网络结果不恢复VIP或保护。设备trial不清除。
- 前台/网络恢复复用既有事件，但不无条件刷新试用code/计时或绕过冷却；会员查询未来需要自己的节流与scope，不绑定设备资格到userId。
- Service sticky/进程重建从源重新恢复，不能以Service.isRunning的旧静态值当资格；跨进程privacy_restart仅负责原重启流程，不成为第二份权益真相。
- 新设备登录只可恢复Server账号会员（未来接口），不迁移trial存储、PIN、媒体或锁列表。此架构不引入任何数据云同步。

## 8. 建议开发拆分与验收顺序

| 任务 | 最小范围 | 完成门槛 |
|---|---|---|
| I3.3.2 统一领域权益核心 | EntitlementState、能力矩阵、纯策略/时钟、Application单一Repository/Factory、默认不授予VIP的Provider、账号页订阅统一输出 | 真值表、离线trial、VIP优先/回落、退出及旧响应、精确边界Fake测试；应用管理免费；不接支付/真实会员HTTP |
| I3.3.3 应用锁实际保护控制 | Coordinator、Service/Overlay/备用Activity多层提交门禁、停保护与配置分离、恢复及FGS资格处理 | 背景到期实际无Overlay，配置完整；恢复可保护；无权限重弹/服务互相重启；并发旧验证不能复活 |
| I3.3.4 相册操作级接入 | picker/回调/命令边界、批处理授权策略、已有数据查看恢复通路、统一VIP引导 | 到期仍看/恢复所有原数据，新导入被拒；跨权限回调与中途到期无丢失/重复/半文件 |
| I3.3.5 集中回归验收 | 全量JVM/数据库及专用设备测试，冷启动/后台/进程重建/网络/退出/到期/恢复组合 | 无数据丢失、无假VIP、退出不重置trial、不改隐私上传开关与Release；真实会员来源缺失项单独列未验证 |

真实会员接口接入若另有任务，应独立确认实际契约及范围，不能以I3.3.2里的接口预留名义自动开发。每阶段先验收再进入下一阶段，不顺带重设计会员套餐或购买UI。

## 9. 需要产品确认的事项

1. 离线有效本地PENDING_SYNC/TEMPORARILY_UNAVAILABLE继续授予trial能力：建议按既定72h规则允许，网络故障不直接失权；VERIFICATION_REQUIRED保守不给trial。
2. 会员网络暂不可用：建议无新增离线VIP宽限，依靠有效trial兜底。若要宽限须定义Server确认记录、上限、撤销/退出优先级和时间信任边界。
3. 到期瞬间批量导入：当前单项完成/下一项停止，还是整批租约完成？建议前者，保留所有已导入内容。
4. 无权益时删除、重命名、移动、新建文件夹的具体矩阵需确认，尤其默认相册初始化和移动现有媒体不能导致既有数据不可访问。
5. 后台恢复因系统FGS限制未能立即启动，是否接受“配置保留、前台恢复保护”的可解释降级；不能承诺无条件自动恢复。
6. 当前VIP尚无真实来源，本阶段真实VIP购买/恢复不能验收通过；是否先用默认不授予＋测试Fake完成框架。

既定规则不待重新确认：应用管理免费；到期应用锁停止实际保护且配置保留；旧相册数据可查看恢复；退出立即撤销账号VIP但不影响有效trial；无本地数据/锁配置云同步。

## 10. 自动化与专用设备验收建议

可复用现有DeviceTrialTest/DeviceTrialCapTest、IsolatedDeviceTrialJvmHttpTest、AccountSessionTest、AppLockRepositoryImplTest、AppLockSessionManagerTest、AppLockNotificationCoordinatorTest、VipPurchaseViewModelTest及testDebug/AccountUiStateTest。已有androidTest覆盖Keystore、Room迁移及媒体来源URI等；不足以替代新增统一权益和Service/Overlay测试。

新增纯Fake矩阵：账号所有状态×trial六状态×VIP验证/期限×隐私；剩余0/1ms、回拨、重启、异步撤销、VIP到期转trial、同账号重新登录与不同账号迟到响应。明确VIP有效需真实来源，不能用Token有效代替。不要在测试触发真实Android ID或生产HTTP。

Service/Overlay：生命周期重建、配置为空/非空、权限丢失、通知拒绝、到期时show已排队/PIN正在校验/临时解锁中、Main与Default并发、重复stop/start幂等、后台启动失败和前台重试。用假的WindowManager/控制器验证调用顺序，再在专用模拟器验证真实Overlay消失与目标应用可用。

相册：picker打开后到期、批量单项提交前后到期、重复回调、权限拒绝、磁盘失败、DB失败、恢复成功保留副本、删除确认、真实缺失清理与权益拒绝严格区分、文件夹改名/移动/非空删除。维护操作前后文件哈希/行数快照，任何失权不应自动减少原数据。

专用设备最小清单：有trial时锁测试应用；在后台到期后确认Overlay与监控停止而配置不变；用Fake已确认VIP（仅测试注入）或合法恢复trial验证保护恢复；无权限时不重复弹/强启。相册事先放合成图片视频，到期仍预览/恢复，导入被引导；前后台/PIN、离线重连、进程重建、账号退出/重登切换时核验trial起点及媒体不变。真实会员HTTP未接入时必须把会员恢复E2E标为未验证，不能凭Fake通过推定。

本轮没有执行任何新测试、构建、安装卸载、数据库操作或设备标识读取。仅生成本报告，等待确认I3.3.2范围后再实施。
