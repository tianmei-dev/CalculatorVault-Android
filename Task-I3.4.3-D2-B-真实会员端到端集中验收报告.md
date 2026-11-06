# Task I3.4.3-D2-B 真实会员端到端集中验收报告

日期：2026-11-06。纯验收与缺陷定位：**未改生产代码**、未改会员协议、未开发支付、未进入 I3.5。

## 结论

真实 Server（8081 / s32it / `aurora_app_test`）→ Android（真实 Bearer + `/membership/me`）→ EntitlementRepository → 应用锁 / 私密相册 的主链路**已打通，绝大多数用例 PASS(R)**。

发现 **2 个真实缺陷（同一根因）**，均已定位、未修复（属跨会话/权益模块改动，按要求先报告）：

| 编号 | 缺陷 | 严重度 |
|---|---|---|
| **D-1** | 断网状态下 App 回到前台，已验证的 24h Sticky VIP 被丢弃（会话变 `TemporarilyUnavailable`），付费能力被拒 | 高 |
| **D-2** | 联网正常回前台时，`revalidate()` 期间会话短暂处于 `Validating`，VIP 消失约 0.1–0.7s，**应用锁监控服务被停止再重启** | 中 |

**建议：暂不关闭整个 I3.4.3**；修复 D-1/D-2 并重跑 `t03`、`t06` 后即可关闭（见 §12）。

结果标识：PASS(R)=真实 Server+Android 端到端；PASS(J)=JVM；PASS(S)=Server 集成；PASS(E)=模拟器但非真实会员链路；FAIL；BLOCKED。

---

## 1. 环境与数据库隔离确认

| 项 | 结果 |
|---|---|
| 模拟器 | `CalculatorVault_Test` / `emulator-5554`（测试内断言 `ro.kernel.qemu=1` + AVD 名） |
| Server | `:8081`，日志 `profile is active: "s32it"`，JDBC `127.0.0.1:3307/aurora_app_test` |
| 夹具 | `i343d1-fixture.bat seed`，SafetyGate：`profile=s32it catalog=aurora_app_test` |
| Android 构建 | `-PISOLATED_MEMBERSHIP_TEST=true` → `API_BASE_URL=http://10.0.2.2:8081`（测试内断言） |
| 开发库 8080 | 未访问；`aurora_app.membership_grants = 0`（与 D1 基线一致） |
| Fake 隔离 | 每个用例断言 `EntitlementInstrumentationBridge` 未装、`stateOverride`/`actionDecisionOverride` 为 null、`membershipProvider === serverMembershipProvider` |
| 禁止项 | 未用 connectedDebugAndroidTest、未卸载、未清数据、未用物理机、未用真实微信/支付 |
| 安装 | 仅 `adb install -r`（包名 `com.aurora.calculatorvault` / `.test` 已确认，签名兼容） |
| Token | 未写入日志/报告/Git；设备 `/data/local/tmp/cv_i343d1_sessions.local.json` **已删除**；证据目录已扫描无 Token |

环境启动说明：任务开始时 8081、`aurora-mysql-it`、模拟器均未运行，本次已**启动**（未改配置）。当前仍在运行。

**设备试用的影响：** 开始时设备试用为 `PENDING_SYNC`、剩余约 25 分钟，期间所有「VIP 失效」场景都回落到试用（试用接管）。试用到期后（自然到期，未做任何改动）重跑，拿到了「失效后停止保护 / 禁止导入」的真实结果。两组结果在下文分别标注。

---

## 2. 会员各套餐和状态测试结果

用例 `t01_allMembershipStates_realMe`（试用有效时 run3、试用到期后 run5 均通过）。

| # | 场景 | 结果 | 证据（run5，试用已到期） |
|---|---|---|---|
| 1 | NONE 无 VIP | **PASS(R)** | `vip=false provider=NotEntitled visible=TrialExpired` |
| 2 | MONTHLY 月卡 + 到期日 | **PASS(R)** | `plan=MONTHLY expiresAt=2026-12-06T12:25:47Z ui=VipActive:Monthly` |
| 3 | YEARLY 年卡 + 到期日 | **PASS(R)** | `plan=YEARLY expiresAt=2027-11-06… ui=VipActive:Annual` |
| 4 | LIFETIME 永久 | **PASS(R)** | `permanent=true expiresAt=null ui=VipPermanent` |
| 5 | STACK 叠加 | **PASS(R)** | `contributing=[MONTHLY, YEARLY] expiresAt=2027-12-06… ui=VipActive:null`（显示「VIP会员」，没有误标为月卡） |
| 6 | EXPIRED 无 VIP | **PASS(R)** | `vip=false provider=NotEntitled` |
| 7 | REVOKED 无 VIP | **PASS(R)** | `vip=false provider=NotEntitled` |
| 8 | 账号间权益隔离 | **PASS(R)** | 每个 VIP 的 `accountId == 夹具 userId`；generation 0→6 递增；跨 App 账号 `other-app-none` 为 NONE（Host HTTP） |
| 9 | 有效期与 Server 一致 | **PASS(R)** | 见 §3 对照表，毫秒级完全一致 |
| 10 | 登录成功本身不授 VIP | **PASS(R)** + PASS(J) | NONE/EXPIRED/REVOKED 均为 Authenticated 但无 VIP |

---

## 3. 真实 HTTP 与权益状态证据

Host 端脱敏 `/me`（`evidence/task-i3.4.3-d2-b/server-me-redacted*.json`，不含 Token）：

| label | HTTP | code | status | planType | 到期 |
|---|---|---|---|---|---|
| none | 200 | 0 | NONE | – | – |
| monthly | 200 | 0 | ACTIVE | MONTHLY | 2026-12-06 |
| yearly | 200 | 0 | ACTIVE | YEARLY | 2027-11-06 |
| lifetime | 200 | 0 | ACTIVE | LIFETIME | （永久） |
| stack | 200 | 0 | ACTIVE | null，contributing=[MONTHLY,YEARLY] | 2027-12-06 |
| expired | 200 | 0 | EXPIRED | MONTHLY | 2020-02-14 |
| revoked | 200 | 0 | REVOKED | – | – |
| session-expired | **401** | **40101** | – | – | – |
| session-revoked | **401** | **40102** | – | – | – |

Server 与 Android 有效期对照（同一次 seed，run4）：

| 套餐 | Server `membershipExpiresAt` | Android `EntitlementSource.Vip.expiresAt` |
|---|---|---|
| monthly | 2026-12-06T12:20:57.997Z | 2026-12-06T12:20:57.997Z |
| yearly | 2027-11-06T12:20:57.997Z | 2027-11-06T12:20:57.997Z |
| stack | 2027-12-06T12:20:57.997Z | 2027-12-06T12:20:57.997Z |
| lifetime | null | null（permanent） |

Android 侧证据：logcat tag `CV_D2B_EVIDENCE`（`logcat-e2e-run*.txt`）。

---

## 4. 会员 UI 展示结果

用例 `t02`：UiAutomator 在真实 App 内输入计算器 PIN → 进入保险库 → 设置页，读取界面上的真实渲染文本。

| 账号 | 标题 | 有效期行 | 登录状态单独展示 | 结果 |
|---|---|---|---|---|
| monthly | 月卡会员 | 有效期至 … | 已登录 | **PASS(R)** |
| yearly | 年卡会员 | 有效期至 … | 已登录 | **PASS(R)** |
| lifetime | 永久会员 | 永久有效 | 已登录 | **PASS(R)** |
| stack | VIP会员 | 有效期至 … | 已登录 | **PASS(R)** |
| revoked | 不出现任何 VIP 标题 | – | – | **PASS(R)** |
| 设置页「刷新会员状态」按钮 | 点击后 revision 13→14，同步状态回到 Idle | | | **PASS(R)** |
| 断网刷新提示 | `会员状态暂时无法同步` | | | **PASS(R)**（映射层，t06） |

---

## 5. 登录、登出、切换账号和 401 测试

| 场景 | 结果 | 说明 |
|---|---|---|
| 登录后自动查询会员 | **PASS(R)** | 安装测试 Session → Authenticated → 自动 `/me` → VIP（不需要手动操作）。注：使用隔离测试 Session，**不代表**真实微信 OpenSDK 登录已验收 |
| 当前账号 401 → SessionExpired | **PASS(R)** | 会员接口真实返回 401/40102 → 协调器 → `SessionExpired`，VIP 清除；鉴权层 40101/40102 同样进入 `SessionExpired` |
| 旧账号迟到的 401 不影响新账号 | **PASS(R)** | 切换到 yearly 后，用旧账号 scope 收到真实 401 → 新账号仍 Authenticated + VIP |
| 登出立即撤销 VIP | **PASS(R)** | `logout()` 后同步变为 Guest，VIP 消失 |
| 切换账号立即撤销旧 VIP | **PASS(R)** | 授权路径（`actionPolicy` 实时重算）在切换瞬间已无旧 VIP；见观察项 O-2 |
| 设备试用独立保留 | **PASS(R)** | 试用有效时 `PENDING_SYNC/902114ms → 902069ms`（仅自然消耗）；试用到期后 `EXPIRED → EXPIRED` |

---

## 6. 前后台与冷启动测试

| 场景 | 结果 | 证据 |
|---|---|---|
| 前后台切换触发刷新 | **PASS(R)** | run5：`rev0=16 → 第 1 次回前台=17` |
| 5 分钟节流 | **PASS(R)**（5 分钟内）/ PASS(J)（超过 5 分钟的边界） | 第 2 次回前台 `=17`（没有新请求） |
| 手动刷新绕过节流 | **PASS(R)** | `→18` |
| 进程死亡后重启并恢复 | **PASS(R)** | 分两次独立执行 `am instrument`，中间由 Host 执行 `am force-stop`（不在运行中的 Instrumentation 内杀进程）。pid 6072 → 6143；Session 从 Keystore 恢复为 Authenticated；重新查询 `/me` 得到 VIP；provider revision 从头开始（内存 Sticky 未被带过来） |
| 回前台时的 `onStart` / `revalidate` 时序 | **FAIL → D-2** | 5ms 采样：`validating=true vipGapSamples=23/43 serviceStoppedDuringFg=true` |

---

## 7. Sticky 离线测试

| 场景 | 结果 | 说明 |
|---|---|---|
| 真实断网（飞行模式）后短时间仍有 VIP | **PASS(R)** | 手动刷新失败 → `sync=Anomaly`、VIP 保留、显示「会员状态暂时无法同步」 |
| **断网状态下回前台** | **FAIL → D-1** | `session=TemporarilyUnavailable vip=false providerSticky=VerifiedActive`；无试用时导入被拒 199 次 |
| 恢复网络后 VIP 恢复 | **PASS(R)** | `offline_recovered vip=true` |
| 网络失败不延长缓存期限 | PASS(J) | 设备上无法观测内部 `confirmedAt` |
| 限时会员不超过自身到期 / 23h59m / 24h 边界 | PASS(J) | 可控时钟（B/C 回归） |
| 永久会员同样受 24h 限制 | PASS(J) | |
| 登出 / 换号 / 明确撤销立即失效 | **PASS(R)** | 见 §5、§2 |
| 设备墙钟变化不能延长 VIP | PASS(J)；设备上**未执行** | 没有临近到期的会员夹具，设备上无可观测差异；改墙钟还会干扰试用时钟可信度 |

---

## 8. 应用锁 Service / Overlay / Room 结果

用例 `t07`，合成目标为 `com.android.chrome`；测试前 Room 锁定基线为 0，结束后已恢复。

| # | 场景 | 试用到期后（run4） | 试用有效时（run3） |
|---|---|---|---|
| 1 | ACTIVE VIP 可以启用保护 | **PASS(R)** `service=true` | PASS(R) |
| 2 | 打开被锁应用出现保护界面 | **PASS(R)** `overlay=true` | PASS(R) |
| 3 | REVOKED 后停止保护 | **PASS(R)** `service=false` | 由试用接管（预期行为） |
| 4 | 失效后 Room 锁配置保留 | **PASS(R)** `room=true` | PASS(R) |
| 5 | VIP 重新生效后恢复保护 | **PASS(R)** `overlay restored=true` | PASS(R) |
| 6 | 失效后旧 Overlay 不残留 | **PASS(R)** `noOverlayOnLaunch=true`（控制器状态 + dumpsys 双重检查） | – |
| 7 | 旧异步触发不能重新启动保护 | **PASS(R)** `staleTriggerStayedStopped=true`；PIN 竞态仍为 PASS(E)（I3.3.3 Fake 用例） | – |
| 8 | 有效设备试用正确接管 | – | **PASS(R)** `source=DeviceTrial allowed=true` |
| – | 回前台期间服务被停止 | **FAIL → D-2** | 被试用掩盖 |

EXPIRED 账号与 REVOKED 账号在 Android 端走同一条 `NotEntitled` 路径；应用锁实际用 REVOKED 账号验证，EXPIRED 账号在相册用例中验证。

---

## 9. 私密相册 JPEG / MP4 导入和恢复结果

用例 `t08`、`t09`，全部使用合成媒体（`CV_I334B_` 前缀，测试后已清理）。

| # | 场景 | 试用到期后（run4） | 试用有效时 |
|---|---|---|---|
| 1 | ACTIVE VIP 导入 JPEG + MP4 | **PASS(R)** `success=2` | PASS(R) |
| 2 | 失效（EXPIRED 账号）后禁止新导入 | **PASS(R)** `Denied skipped=1` | 由试用接管，允许导入 |
| 3 | 已导入内容仍可查看 | **PASS(R)** `viewed=2` | |
| 4 | 已导入内容仍可恢复到系统 | **PASS(R)** `restored=1` | |
| 5 | 已导入内容仍可删除和管理 | **PASS(R)**（删除）；重命名/移动为 PASS(E)（I3.3.4-B） | |
| 6 | 批量导入中途失效，后续停止 | **PASS(R)** `success=2 skipped=14` | 试用接管，16/16 全部成功 |
| 7 | 已完成的导入不回滚 | **PASS(R)** `kept=2` | |
| 8 | 无临时文件残留 | **PASS(R)** `tmp=0` | |
| 9 | 切换账号不删除本地私密文件 | **PASS(R)** | |

---

## 10. JVM、Instrumentation、Server 回归

| 套件 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **482 个测试，0 失败**（4 个隔离 HTTP 用例需显式开启，跳过） |
| Server `membership.*`（ApiIT 11 + ServiceIT 21 + Calculator 10） | **42 个测试，0 失败**（`aurora_app_test@3307`） |
| D2-A `IsolatedTestSessionInstrumentationTest` | **OK (7 tests)** |
| D2-B `RealMembershipE2EInstrumentationTest` | run3（试用有效）9/10；run4（试用中途到期）8/10；run5（无试用，t01–t03、t06）3/4；**仅 t06 是真实缺陷**，其余失败均为测试设计问题且已修正后通过 |
| D2-B `RealMembershipColdStartTest` | phase1 OK / phase2 OK |
| 未重跑 | I3.3 的 Fake 版应用锁/相册 Instrumentation（本任务不以 Fake 结果作结论） |

本任务新增 / 修改文件（仅测试与证据）：

- `androidTest/.../membership/RealMembershipE2EInstrumentationTest.kt`（新增）
- `androidTest/.../membership/RealMembershipColdStartTest.kt`（新增）
- `evidence/task-i3.4.3-d2-b/`（`me-redacted.py`、脱敏 `/me` JSON、instrument 输出、logcat、UI dump）

---

## 11. 缺陷及未覆盖项目

### D-1 断网回前台丢弃 Sticky VIP（高）

- **现象：** 已验证 VIP，断网后切到后台再回前台 → 会话变为 `TemporarilyUnavailable` → 无 VIP。此时 `ServerMembershipProvider.membershipFor()` 仍返回 `VerifiedActive`。没有试用时，应用锁停止、相册导入被拒，直到恢复网络。
- **影响：** 「24 小时离线保留」实际上只在 App 一直留在前台时成立；断网期间任何一次回前台、以及断网冷启动都会失去 VIP。
- **根因：**
  - `AccountSessionRepository.revalidate()` 在网络请求前先切到 `Validating`，网络失败后切到 `TemporarilyUnavailable`；这两个状态都不携带账号。
  - `EntitlementRepository.resolveVip()` 只认 `Authenticated`：

```155:155:app/src/main/java/com/aurora/calculatorvault/feature/entitlement/domain/EntitlementRepository.kt
        if (session !is AccountSession.Authenticated || accountId == null) return null
```

### D-2 联网回前台的 revalidate 空窗（中）

- **现象：** 每次 `ProcessLifecycle.onStart` 都会调用 `revalidate()`，在 `/auth/me` 返回之前（实测约 0.1–0.7s）VIP 为空，`AppLockProtectionCoordinator` 随即停止监控服务，Authenticated 后再重新启动。
- **影响：** 每次回前台都会出现一个短暂的保护空窗，并造成前台服务反复启停。
- **根因：** 与 D-1 相同。

### 修复方向（需确认后另开任务，本轮未实施）

- `revalidate()` 期间保持 `Authenticated`（后台校验），或让 `Validating` / `TemporarilyUnavailable` 携带上一个已认证账号，并由 `EntitlementRepository` 在这两个状态下继续使用该账号 + generation 去查询 Provider 的 Sticky。
- 必须保证：明确的 401 / `SessionExpired` 仍然立即清除 VIP；换号、登出不受影响。
- 修复后重跑 `t03` 与 `t06`。

### 观察项（低，不阻断）

- **O-1：** 协调器处理 SessionInvalid 时调用 `clearAccount()`，会取消产生这个 401 的同一个进行中请求，调用方因此拿到 `CancellationException`。最终状态正确（`SessionExpired`）。
- **O-2：** 切换账号后，`EntitlementRepository.state` 这个 StateFlow 要等异步收集器跑一次才更新，期间观察者可能看到旧账号 VIP；授权路径（`actionPolicy` 实时重算）不受影响。UI 最多晚一帧。
- **O-3（仅测试路径）：** 通过测试注入从 A 账号直接切换到 B 账号时，协调器把这次刷新归类为 Foreground（会占用 B 的节流窗口）。正式微信登录会经过 Guest / Authenticating，不受影响。

### 未覆盖

- 真实微信 OpenSDK 登录（本轮使用隔离测试 Session）。
- 设备上修改墙钟；真实等待 24 小时；设备上超过 5 分钟的节流边界（均为 PASS(J)）。
- 应用锁未单独使用 EXPIRED 账号（与 REVOKED 同路径）。
- 真实会员失效下的相册重命名/移动（仅 PASS(E)）。
- localIntegration / Release 对真实 Server（未执行）。

---

## 12. 是否建议关闭整个 I3.4.3

**暂不建议关闭整包。**

- 协议、接入、刷新、展示、401、换号、冷启动、应用锁、相册主链路均已 PASS(R)。
- **D-1 / D-2** 直接违反「已验证 VIP 断网 24 小时内保留」，并在每次回前台时造成应用锁空窗。

建议另开一个小任务（例如 I3.4.3-D2-C），仅修改 `AccountSessionRepository` 和 `EntitlementRepository` 的会话校验期语义，修复后重跑 `t03`、`t06` 和本套件。全部通过后即可关闭 I3.4.3。

---

**本任务停止，等待审核。不进入 I3.5 支付开发。**
