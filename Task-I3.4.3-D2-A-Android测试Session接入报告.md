# Task I3.4.3-D2-A Android 测试 Session 接入报告

日期：2026-11-04。仅解决隔离测试 Session 安全接入与最小 `/me` 连通；**未**做应用锁/相册完整验收；**未**自动进入 D2-B。

**结论：专用 Debug（`-PISOLATED_MEMBERSHIP_TEST=true` → `http://10.0.2.2:8081`）可通过 androidTest 注入 D1 Bearer，复用 `KeystoreTokenStore` + `AccountSessionRepository` 状态机；真实 `/me` 已使 MONTHLY 获得 VIP、NONE 不授 VIP。Release 无注入类型。模拟器 `am instrument` **7/7 OK**。**

---

## 1. 采用的测试 Session 接入方案

复用现有会话栈，不另建登录状态：

1. Host：`i343d1-fixture.bat seed` → `build/i343d1-sessions.local.json`（gitignore，报告不写 Token）
2. `adb push` → 设备 `/data/local/tmp/cv_i343d1_sessions.local.json`
3. androidTest `IsolatedTestSessionBridge` 读文件（`toString` 脱敏）
4. Debug-only `IsolatedTestSessionAccess.install` → `AccountSessionRepository.installServerIssuedSession`
5. 写入 **KeystoreTokenStore** → `AuthApi.me` 校验 → `Authenticated`
6. `MembershipRefreshCoordinator` 观察会话 → 真实 `GET /api/v1/membership/me`（无 Fake VIP）

切换账号：本地清存储且**不** `logout` 远端，避免烧毁其他夹具 Token。  
用例 `logout_clearsVip` 显式调用真实 `logout()`（会 revoke 所用 lifetime Token）。  
`@After` 使用 `abandonLocalSession()`。

未在 Manifest 暴露可外部启动的测试组件。

---

## 2. 新增 / 修改文件

| 文件 | 说明 |
|---|---|
| `AccountSessionRepository.kt` | `installServerIssuedSession`、`abandonLocalSession` |
| `app/build.gradle.kts` | `ISOLATED_MEMBERSHIP_TEST`、`TEST_SESSION_INSTALL_ENABLED`；Debug API/LOCAL/TRIAL 对齐 |
| `debug/.../IsolatedTestSessionAccess.kt` | **仅 debug** 注入门禁 |
| `androidTest/.../IsolatedTestSessionBridge.kt` | 设备夹具加载 + 安装 |
| `androidTest/.../IsolatedTestSessionInstrumentationTest.kt` | 7 项连通性 |
| `test/.../InstallServerIssuedSessionTest.kt` | JVM 安装/切换/abandon |
| `evidence/task-i3.4.3-d2-a/*` | 运行脚本与结果摘要 |
| `.gitignore` | 忽略 sessions 本地文件 |

微信登录 `acceptAuthorization` **未改**。Release `API_BASE_URL` / 试用上传开关默认逻辑保留（Debug 隔离属性不影响 Release）。

---

## 3. Debug / Release 隔离证明

| 项 | Debug + `ISOLATED_MEMBERSHIP_TEST=true` | Release |
|---|---|---|
| `API_BASE_URL` | `http://10.0.2.2:8081` | `""`（未改） |
| `LOCAL_HTTP_BASE_URL` | 同 8081 | （默认空） |
| `TEST_SESSION_INSTALL_ENABLED` | `true` | `false` |
| `IsolatedTestSessionAccess` | 存在于 debug 源码集 | **Release DEX 无此类名** |
| `IsolatedTestSessionBridge` | 仅 androidTest | Release 无 |

普通 Debug（无该属性）默认仍为 `10.0.2.2:8080`，且 `TEST_SESSION_INSTALL_ENABLED=false`。

---

## 4. Token 安全存储与清理

| 项 | 行为 |
|---|---|
| 存储 | 现有 `KeystoreTokenStore`（`noBackupFilesDir` + AES-GCM），非明文 SP |
| 注入 | 仅 Debug + 属性门禁；androidTest 同进程调用 |
| 清理 | `abandonLocalSession`（测后）；`logout`（真实登出+服务端 revoke） |
| 日志/报告 | Token 不打印；失败信息只用 label / userId / 状态名 |

---

## 5. Base URL 隔离证明

- 登录 `HttpAuthApi` 与会员 `HttpMembershipApi` 均读 `BuildConfig.API_BASE_URL`（+ 同值 `LOCAL_HTTP_BASE_URL`）
- 隔离构建生成：`API_BASE_URL=LOCAL_HTTP_BASE_URL=TRIAL_*=http://10.0.2.2:8081`
- **未**改 8080 开发库；**未**把 Session 指 8081 而会员打 8080

---

## 6. 测试结果

### JVM

`InstallServerIssuedSessionTest` + 既有 `AccountSessionTest`：**PASS**

### 模拟器 Instrumentation（手动 `am instrument`）

设备：`CalculatorVault_Test` / `emulator-5554`  
结果：**OK (7 tests)**（见 `evidence/task-i3.4.3-d2-a/instrument-result-summary.txt`）

| # | 用例 | 结果 |
|---|---|---|
| 1 | 注入后 Authenticated + Token 非空 | PASS |
| 2 | 注入触发会员刷新；NONE 无 VIP | PASS |
| 3 | MONTHLY 真实 `/me` → VIP | PASS |
| 4 | 切换账号旧 VIP 失效 | PASS |
| 5 | logout 清 VIP | PASS |
| 6 | 无效 Token → SessionExpired/TEMP | PASS |
| 7 | revalidate 保持 Authenticated（同 restore 校验路径） | PASS |

未执行：`connectedDebugAndroidTest`、卸载、清数据、物理机、8080、真实微信。

---

## 7. 真实 `/me` 最小连通性

- **NONE**：`Authenticated` 且 `source !is Vip`
- **MONTHLY**：`EntitlementSource.Vip`（accountId 与夹具 userId 一致，非 permanent）
- 请求宿主：模拟器 → `10.0.2.2:8081`（s32it / `aurora_app_test`）

---

## 8. D2-B 联调操作说明

```text
1. 确认 8081 s32it 与 aurora-mysql-it(3307) 可用
2. Server: scripts\i343d1-fixture.bat seed
3. Android 仓库:
   powershell -File evidence/task-i3.4.3-d2-a/run-isolated-session-instrument.ps1
   或等价：assembleDebug/AndroidTest -PISOLATED_MEMBERSHIP_TEST=true
           adb push sessions → /data/local/tmp/cv_i343d1_sessions.local.json
           adb install -r app + androidTest
           am instrument … IsolatedTestSessionInstrumentationTest
4. D2-B 可在同一注入桥上扩展 yearly/lifetime/stack/expired/revoked 与锁/相册用例
5. 若用例调用了 logout()，需重新 seed 再跑依赖该 Token 的用例
6. 禁止 clear-data / uninstall；需要重装仅 install -r
```

---

## 9. 未覆盖项与风险

1. **完整进程死亡冷启动**：`am force-stop` 会打崩本 AVD 上的 instrument 进程；以 Keystore + `revalidate`/`restore` 同路径代替，D2-B 可用手工杀进程观察。  
2. yearly / stack / expired / revoked 展示与锁相册：**留给 D2-B**。  
3. `logout()` 会 revoke 服务端 Session — 自动化应用独立 label 或跑后重新 seed。  
4. 设备 tmp 夹具文件含 Token：仅测试机；勿 `adb pull` 进 Git/报告。  
5. Release DEX 仍可能含 `TEST_SESSION_INSTALL_ENABLED` **字段名**（值为 false）；**不含** `IsolatedTestSessionAccess` 类型。

---

**本任务停止，等待审核。不自动进入 D2-B。**
