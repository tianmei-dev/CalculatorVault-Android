# Task I3.4.3-D2-C 会员校验期权益稳定性修复报告

日期：2026-11-06。只修复「会话校验」与「权益判断」之间的状态衔接。未改支付、会员协议、Provider 的 24h 缓存算法、应用锁/私密相册业务规则、文件加密格式或 Room Schema。

## 结论

**D-1、D-2 均已修复，并在真实环境复测通过。**

- 真实 Server + 模拟器上，完整 E2E 套件 **OK (10 tests)**；测试时设备试用已到期，结果没有被试用掩盖。
- 正常回前台：VIP 空窗 **0**，导入被拒 **0**，应用锁服务**没有停止/重启**。
- 断网回前台：Sticky VIP 保留，服务持续运行，可正常导入；网络恢复后重新确认会员。
- 401、登出、换号、到期等安全路径没有回归。

**建议关闭 I3.4.3**（见 §11）。

---

## 1. D-1 / D-2 根因及修复方式

### 根因（两个缺陷相同）

- `AccountSessionRepository.revalidate()` 每次回前台先把会话切到 `Validating`，网络失败再切到 `TemporarilyUnavailable`。这两个状态原来都是单例对象，**不携带账号**。
- `EntitlementRepository.resolveVip()` 只认 `Authenticated`。
- 结果是：只要会话在重新校验，VIP 就被当作不存在。Provider 里有效的 Sticky 从未被读取。

### 修复

1. **会话状态携带可信账号。**`Validating` 和 `TemporarilyUnavailable` 改为数据类，带可选字段 `trustedAccount`。只有在「重新校验本进程已认证过的会话」时才填入；首次登录、冷启动恢复、新安装凭据一律为 null。
2. **统一身份读取。**新增 `AccountSession.entitlementAccount`，返回 `Authenticated.account`，或上述两个状态里的 `trustedAccount`，其他状态返回 null。
3. **`EntitlementRepository` 仅在可信时读取 VIP。**
   - `entitledAccountId()`：只有 `trustedAccount == lastAuthenticatedAccountId` 时，才用该账号和**当前 generation** 去查询 Provider。
   - 无可信身份的 `Validating` / `TemporarilyUnavailable`（新凭据、冷启动）视为会话边界：generation 加 1、清空上次认证账号，旧账号的 claim 不会被继承。
   - VIP 仍然只来自 Provider 对同账号、同 generation 返回的 `VerifiedActive`，并保留原有的墙钟到期检查。
4. **`MembershipRefreshCoordinator` 处理中间状态。**
   - 已知身份被未知身份替换时（无可信账号的 `Validating`）：`clearAll()` 清掉旧 Sticky，并作废进行中的请求。
   - 从 `TemporarilyUnavailable` 恢复到 `Authenticated` 时：立即重新确认会员，不被 5 分钟前台节流吞掉。
   - SessionInvalid 的身份匹配改为基于 `entitlementAccount`，重新校验期间收到的 401 同样有效。
   - 仍只在 `Authenticated` 时发起 `/me`，校验期间不产生重复请求。
5. **`notifyRemoteSessionInvalid` 身份范围扩大。**重新校验中的同一账号收到 401 也会过期；其他账号的 401 依然被忽略。
6. **展示层与授权保持一致。**
   - 可信账号在重新校验时仍显示为已登录，避免界面隐藏实际有效的 VIP。
   - 断网文案在 Sticky 有效时改为「会员权益按离线缓存暂时保留」。

**没有**延长缓存期限，**没有**加延迟停止 Service 的补丁，**没有**使用 Fake 或 Override。

---

## 2. 修改文件清单

| 文件 | 修改内容 |
|---|---|
| `login/session/AccountSessionRepository.kt` | `Validating` / `TemporarilyUnavailable` 增加 `trustedAccount`；新增 `entitlementAccount` 扩展属性；`restore` / `revalidate` / `validate` / `installServerIssuedSession` / `notifyRemoteSessionInvalid` 相应调整 |
| `entitlement/domain/EntitlementRepository.kt` | generation 跟踪区分可信与未知身份；新增 `entitledAccountId()`；`resolveVip()` 不再依赖会话类型 |
| `membership/data/MembershipRefreshCoordinator.kt` | 会话边界清理、断网恢复后立即重新确认、SessionInvalid 身份匹配 |
| `entitlement/domain/EntitlementDisplayMapper.kt` | 登录态与同步提示改为基于 `entitlementAccount` |
| `vault/presentation/VaultMainScreen.kt` | `is` 判断；断网文案区分 Sticky 有效 |
| `login/presentation/WeChatLoginDialog.kt` | `is` 判断 |
| `test/.../AccountSessionTest.kt` | 断言改为新的状态值（可信 / 不可信） |
| `test/.../SessionRevalidationEntitlementTest.kt` | **新增** 16 个回归测试 |
| `androidTest/.../RealMembershipE2EInstrumentationTest.kt` | `t03` / `t06` 增加「零空窗、服务不停、导入不拒、真实恢复」断言 |

未修改：`ServerMembershipProvider`、应用锁、相册、Room、Server、`build.gradle.kts`。

---

## 3. 新旧会话状态转换对比

| 场景 | 修复前 | 修复后 | VIP 是否可读取 Sticky |
|---|---|---|---|
| 冷启动恢复 | `Validating` | `Validating(null)` | 否 |
| 冷启动 + 断网 | `TemporarilyUnavailable` | `TemporarilyUnavailable(null)` | 否 |
| 已认证回前台 | `Authenticated(A)` → `Validating` | `Authenticated(A)` → `Validating(A)` | **是（同账号同 generation）** |
| 已认证回前台 + 断网 | → `TemporarilyUnavailable` | → `TemporarilyUnavailable(A)` | **是（直到 24h 或会员到期）** |
| 断网状态下再次 revalidate | `Validating` | `Validating(A)` | 是 |
| 校验返回 401 | `SessionExpired` | `SessionExpired` | 否，立即清除 |
| 安装新凭据 / 换号 | `Validating` | `Validating(null)` + generation 加 1 + `clearAll()` | 否 |
| 登出 | `Guest` | `Guest` | 否 |
| 首次微信登录 | `Authenticating` → `Authenticated` | 不变 | 只认服务端确认结果 |

---

## 4. Sticky 安全边界说明

- **只延续，不新授。**校验期间只读取已存在、且同账号同 generation 的 `VerifiedActive`。从未被服务端确认过的会员不会被授予（JVM 测试 8）。
- **24h 与会员自身到期仍由 Provider 判定，算法未改。**
  - 24h 窗口按 `elapsedRealtime` 计算，到边界立即失效（JVM 测试 5，`24h - 1ms` 有效 / `24h` 失效）。
  - 有限期会员早于 24h 到期时提前失效（测试 6）。
  - 断网失败不会重置起点：20h 后失败 + 断网 revalidate，再过 4h 即失效（测试 14）。
- **会话边界不继承。**冷启动、新凭据、同一 userId 重新登录都会产生新的 generation（测试 7、12、18）。进程重启后 Provider 是全新的，不会恢复内存中的 Sticky（真机冷启动 PASS）。
- **明确失效立即撤销。**以下情况均立即清除 VIP（测试 9、10、11、13）：
  - 鉴权层 401 / 40101 / 40102；
  - 会员层 SessionInvalid（包括重新校验期间收到的）；
  - 登出、换号；
  - 旧账号迟到的 401 被忽略，不影响新账号。
- **时钟异常。**Provider 遇到 `elapsedRealtime` 回退会清除 Sticky（B 阶段测试，本次未改动）；墙钟只会让 VIP 提前失效，不能延长。
- **设备试用保持独立。**Sticky 失效后按 VIP > 试用 > 免费回落（测试 15）。

---

## 5. JVM 回归结果

`:app:testDebugUnitTest`：**498 个测试，0 失败**（4 个隔离 HTTP 用例需显式开启，跳过）。

新增 `SessionRevalidationEntitlementTest`（16 个测试）与任务要求的对应关系：

| 要求 | 用例 |
|---|---|
| 1 / 3 / 16 / 17 | `authenticated_validating_authenticated_keepsVip_andLockServiceStable`（start=1，stop=0，revoke 未增加） |
| 2 / 4 / 16 / 17 | `authenticated_validating_temporarilyUnavailable_keepsStickyVip` |
| 5 / 15 | `sticky24hBoundary_duringUnavailable_fallsBackToTrial`、`sticky24hBoundary_noTrial_stopsProtection` |
| 6 | `limitedMembershipExpiresEarly_duringUnavailable` |
| 7 / 11 | `firstValidating_newCredential_doesNotInheritVip` |
| 7 / 18 | `coldStartRestore_offline_noVip_noStickyCarryOver` |
| 8 | `unconfirmedMembership_notGrantedDuringUnavailable` |
| 9 | `auth401_duringRevalidation_revokesImmediately`、`membershipSessionInvalid_duringUnavailable_revokes` |
| 10 | `logout_duringUnavailable_revokes` |
| 12 | `sameUserNewGeneration_doesNotInherit` |
| 13 | `late401OldAccount_ignoredWhileNewAccountRevalidating` |
| 14 | `offlineFailures_doNotRefreshStickyStart` |
| 无重复 `/me` | `noDuplicateMe_andRecoveryReconfirmsOnce` |
| O-2 检查 | `switchAccount_livePolicyNeverAuthorizesOldVip` |

---

## 6. t03 / t06 真实端到端复测结果

环境：`CalculatorVault_Test` / `emulator-5554`，8081 s32it，`aurora_app_test@3307`，`-PISOLATED_MEMBERSHIP_TEST=true`；仅使用 `adb install -r` + `am instrument`。设备试用状态为 `EXPIRED`。

| 用例 | 结果 | 关键证据 |
|---|---|---|
| 完整套件 `RealMembershipE2EInstrumentationTest` | **OK (10 tests)** | `evidence/task-i3.4.3-d2-c/instrument-e2e-full.txt` |
| `t03` 前后台 / 节流 / 校验期稳定性 | **PASS(R)** | `rev 29→30（第 1 次回前台）→30（5 分钟内）→31（手动）`；两次回前台均 `validating=true vipGapSamples=0 liveImportDenied=0 serviceStoppedDuringFg=false` |
| `t06` 断网 Sticky | **PASS(R)** | 断网回前台：`TemporarilyUnavailable(trustedAccount=596) vip=true service=true importAllowed=true`，UI 显示已登录 + VIP；采样 `vipGapSamples=0 liveImportDenied=0 serviceStoppedDuringFg=false` |
| `t06` 网络恢复 | **PASS(R)** | `recovered=true session=Authenticated`，`rev 4→5`（重新确认，未被节流） |
| 冷启动两阶段（中间由 Host 执行 force-stop） | **PASS(R)** | pid 9492 → 9551，恢复为 Authenticated，重新查询 `/me` 得到 VIP，revision 从头开始 |
| D2-A 测试 Session 套件 | **OK (7 tests)** | 中途一次失败是因为同一批夹具中的 lifetime Token 已被 `t10` 真实登出撤销；重新 seed 后通过 |

修复前（D2-B run5，同样无试用）对比：`vipGapSamples=23/43`，`serviceStoppedDuringFg=true`；断网回前台后 `vip=false`，导入被拒 199 次。

---

## 7. 应用锁 Service 是否存在停止 / 重启

**不存在。**

| 检查项 | 结果 |
|---|---|
| 正常回前台期间服务停止 | 否（真机 `serviceStoppedDuringFg=false` ×2；JVM `stop=0`） |
| 校验成功后重复启动 | 否（JVM `startCount=1`） |
| 断网回前台、Sticky 有效时服务运行 | 是（真机 `service=true`） |
| 401 / Sticky 到期后正常停止 | 是（JVM 测试 9、5） |
| 撤销后停止、无 Overlay 残留、旧触发不能重启、Room 配置保留、VIP 恢复后保护恢复 | `t07` **PASS(R)**（`service=false noOverlayOnLaunch=true staleTriggerStayedStopped=true room=true`，`overlay restored=true`） |
| 试用有效时接管 | D2-B run3 已 PASS(R)；本次 JVM 测试 15 PASS（设备试用已到期，无法在真机复现） |

---

## 8. 相册是否仍出现错误 Denied

**不再出现。**

| 检查项 | 结果 |
|---|---|
| 联网重新校验期间被拒 | 0 次（`t03`） |
| 断网回前台、Sticky 有效时被拒 | 0 次，`importAllowed=true`（`t06`） |
| 明确撤销 / 到期后禁止新导入 | 仍生效：`t08` 中 EXPIRED 账号 `Denied skipped=1` |
| 已有文件查看 / 恢复 / 删除 | 正常：`viewed=2 restored=1 deleted=2` |
| 批量导入实时检查 | 正常：`t09` 中 `success=2 skipped=14`，已完成的保留，无 `.tmp` 残留 |

---

## 9. 401、登出、换号、到期安全回归

| 场景 | 真机 | JVM |
|---|---|---|
| 会员层当前账号 401 → SessionExpired | PASS(R)（`t04`） | PASS（测试 9） |
| 鉴权层 40101 / 40102 | PASS(R) | PASS |
| 旧账号迟到的 401 | PASS(R)（`t05`：新账号仍 Authenticated + VIP） | PASS（测试 13，含新账号处于重新校验中） |
| 登出 | PASS(R)（`t10`） | PASS（测试 10，含断网期间登出） |
| 换号 | PASS(R)（`t01` 中 7 次切换，均没有旧 VIP 被授权） | PASS（测试 11、O-2） |
| EXPIRED / REVOKED | PASS(R)（`t01` / `t07` / `t08`） | – |
| 同一 userId 重新登录、新 generation | – | PASS（测试 12） |

观察项：

- **O-1 无恶化。**SessionInvalid 处理器会取消产生该 401 的同一个进行中请求（`t04` 记录为 `CancelledBySessionInvalidHandler`），最终状态正确。
- **O-2 无恶化。**live policy 在 StateFlow 收集器运行之前就已拒绝旧 VIP（新增 JVM 用例）。另外，换号时现在会立刻 `clearAll()`，旧 Sticky 与进行中的请求都会被作废，比之前更安全。

---

## 10. Debug / localIntegration / Release 构建结果

默认构建（不带隔离开关）：`assembleDebug`、`assembleLocalIntegration`、`assembleRelease` 均**成功**。

| 变体 | `API_BASE_URL` | `TEST_SESSION_INSTALL_ENABLED` | `TRIAL_DEVICE_UPLOAD_ENABLED` |
|---|---|---|---|
| Debug | `http://10.0.2.2:8080` | false | false |
| localIntegration | `http://192.168.0.107:8080` | false | false |
| Release | `""`（未改） | false | false |

- Release DEX 不包含 `IsolatedTestSessionAccess`。
- 隔离构建 `-PISOLATED_MEMBERSHIP_TEST=true` 照常编译。

安全检查：

- 设备 Token 文件已删除；证据目录逐个比对，未发现 Token。
- 飞行模式已关闭。
- 开发库 `aurora_app.membership_grants = 0`。
- 未使用 connectedDebugAndroidTest、未卸载、未清数据、未用物理机或真实微信，未新增生产注入入口。

---

## 11. 是否建议关闭 I3.4.3

**建议关闭。**

- D2-B 阻断项 D-1、D-2 已修复，并通过真实环境 PASS(R) 复测。
- 会员状态、UI、刷新、401、换号、登出、冷启动、应用锁、相册全链路已在 D2-B / D2-C 中 PASS(R)。
- JVM 498 / 0，Server 42 / 0（Server 代码本次未改动）。

遗留（不阻断，可在后续阶段处理）：

- 真实微信 OpenSDK 登录与本链路的联合验收。
- 设备上真实等待 24 小时、修改墙钟（目前为 PASS(J)）。
- O-1 / O-3 低优先级观察项。

---

**本任务停止，等待审核。不进入 I3.5。**
