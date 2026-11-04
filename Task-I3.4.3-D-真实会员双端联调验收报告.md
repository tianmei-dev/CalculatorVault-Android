# Task I3.4.3-D 真实会员双端联调验收报告

日期：2026-11-04。验证任务；**未**增加订单/支付/生产 grant 能力；**未**进入支付开发。

## 总裁决（分层，不可混用）

| 层级 | 结论 |
|---|---|
| JVM 单元测试（Android Provider/Coordinator/Sticky/展示） | **通过**（本轮重跑 membership.* + EntitlementRepository） |
| 隔离 Server 集成测试（`aurora_app_test` + MembershipService 内部 grant） | **通过**（MembershipApiIT 11 + MembershipServiceIT 21 + Calculator 10） |
| 真实 Server ↔ Android 模拟器端到端（Bearer + `/me` → VIP → 应用锁/相册） | **BLOCKED**（环境缺口，见 §1 / §11） |
| 是否建议关闭 I3.4.3 | **不建议整包关闭**。建议：**关闭 I3.4.3-A/B/C（客户端接入）为完成**；**I3.4.3-D 真实双端保持开放**，直至隔离常驻实例具备可审计的 Session 签发 + 内部 grant 夹具。 |

**不得用 JVM / Server IT 结果代替「真实端到端通过」。**

---

## 1. 环境与数据库隔离确认

证据：`evidence/task-i3.4.3-d/environment.json`

| 项 | 确认结果 |
|---|---|
| 专用模拟器 | **是**：AVD `CalculatorVault_Test`，`emulator-5554`，`ro.kernel.qemu=1` |
| 物理机 | **未使用** |
| `:8080` | **可达**；对应本机 `bootRun` → **`aurora_app` @ MySQL `3308`（`aurora-mysql-dev`）** |
| `:8081`（s32it） | **不可达（DOWN）**；设计指向 `aurora_app_test` @ `3307` |
| `aurora-mysql-it` | 任务开始时为 **Exited**；为跑 Membership\* IT **仅重启**该既有容器（端口 3307 / 库 `aurora_app_test`）。**未**改 compose 配置、**未**动 3308 |
| 测试数据隔离 | Server IT 写入仅发生在 **`aurora_app_test`**。**未**对 `aurora_app`(3308) 执行会员 grant/revoke/SQL 变更 |
| Android Debug `API_BASE_URL` | `http://10.0.2.2:8080`（登录与会员同一主机）→ 指向 **3308 开发库**，**不能**在未批准情况下写入 ACTIVE 会员 |
| localIntegration | 登录 `192.168.0.107:8080`；试用历史 `8081` — **不能假定 8081 已具备完整会员测试条件**（且本轮 8081 未启动） |
| 公开 grant/revoke HTTP | **无**。`MembershipApiIT`：认证后 POST grant/revoke → **40400**。Host 对 8080 无 Token/假 Token → **401** |
| 仅测试环境 grant | **有**：`MembershipService.grant/revoke`（内部）。`GrantSourceType.TEST` 仅允许 profile `test|local|dev`；**`s32it` 不在允许列表** |
| 测试 Session | Server IT 使用 `TestWechatAuthClient`（仅 `@SpringBootTest`）。模拟器 **无**真实微信授权；**无**批准的「注入已安装 App Session 且不清数据」通道 |

### 环境缺口（因此停止的真实双端项）

1. 模拟器 Debug 默认打到 **8080/`aurora_app`**，禁止为造 ACTIVE/REVOKED 改该库。  
2. 隔离常驻 **8081/s32it 未运行**；即便启动：微信为 placeholder，无法真实登录签发 Session。  
3. **无公开 grant HTTP**；缺少「隔离常驻进程 + 内部 grant + 可交付测试 Session」的审批过夹具。  
4. 禁止 uninstall / clear-data / `connectedDebugAndroidTest`；不得用 FakeMembershipProvider / Bridge 宣称真实会员联调成功。

→ **凡依赖「模拟器持真实 Bearer 查询出 ACTIVE/EXPIRED/REVOKED 并驱动锁/相册」的用例一律 BLOCKED**，未擅自扩 scope。

---

## 2. Android / Server 版本及构建信息

| 组件 | 信息 |
|---|---|
| Android 仓库 HEAD | `4fbec30`（message: 权限等实现） |
| App 包名 | `com.aurora.calculatorvault` |
| 模拟器已装版本 | `versionName=1.0.0` `versionCode=1`（lastUpdateTime 观察值 2026-11-04 11:15:00；本轮**未**重装/清数据） |
| AuroraAppServer HEAD | `9ac4fa7` |
| Server IT DB | `aurora_app_test@127.0.0.1:3307` |
| 本轮未执行 | `connectedDebugAndroidTest`；支付 SDK；生产 Profile 开放 grant |

---

## 3. 测试用例及 PASS / FAIL / BLOCKED

图例：**R** = 真实 Server↔Android 模拟器；**S** = 隔离 Server IT；**J** = Android JVM；**E** = 模拟器（非真实会员源）。

### 3.1 会员查询与展示

| ID | 用例 | 结果 | 层级 |
|---|---|---|---|
| Q1 | HTTP 200 ACTIVE → Android VIP | **BLOCKED** | R |
| Q2 | NONE/EXPIRED/REVOKED → 不授 VIP | **PASS(S)** / **PASS(J)** / **BLOCKED(R)** | S/J/R |
| Q3 | MONTHLY 显示月卡 | **PASS(J)** / **BLOCKED(R)** | J/R |
| Q4 | YEARLY 显示年卡 | **PASS(S+J)** / **BLOCKED(R)** | S/J/R |
| Q5 | LIFETIME 显示永久 | **PASS(S+J)** / **BLOCKED(R)** | S/J/R |
| Q6 | 叠加套餐有效期展示 | **PASS(S+J)** / **BLOCKED(R)** | S/J/R |
| Q7 | 有效期与 Server 一致 | **PASS(S)** / **BLOCKED(R)** | S/R |
| Q8 | 手动刷新更新展示 | **PASS(J)** / **BLOCKED(R)** | J/R |
| Q9 | 查询失败不伪造 VIP | **PASS(J)** / **BLOCKED(R)** | J/R |
| Q10 | 登录成功本身不授 VIP | **PASS(J)**；Server NONE+/me **PASS(S)** / **BLOCKED(R)** | |

### 3.2 会话生命周期

| ID | 用例 | 结果 | 层级 |
|---|---|---|---|
| L1–L5 | 登录/冷启/前台/节流/手动刷新 | **PASS(J)** / **BLOCKED(R)** | |
| L6 | 401 → SessionExpired | **PASS(J)**；Server expired token **PASS(S)** / **BLOCKED(R)** | |
| L7 | 旧账号迟到 401 不影响新账号 | **PASS(J)** / **BLOCKED(R)** | |
| L8–L10 | 登出清 VIP、generation、试用保留 | **PASS(J)** / **BLOCKED(R)** | |
| L-timing | ProcessLifecycle.onStart vs revalidate | **PASS(J 设计覆盖)** / **尚未(R 真机时序实测)** | |

### 3.3 24h Sticky

| ID | 用例 | 结果 | 层级 |
|---|---|---|---|
| T1–T10 | Sticky 窗口/边界/撤销/登出/杀进程/墙钟 | **PASS(J 可控时钟)** | **非**真实设备 24h 等待 |
| T-device | 真实设备断网 Sticky | **尚未执行** | |

### 3.4 应用锁 / 私密相册

| ID | 用例 | 结果 | 说明 |
|---|---|---|---|
| AL\* / PA\* | VIP ACTIVE/失效下的 Service、Overlay、导入/恢复 | **BLOCKED** | 依赖真实 VIP 联调；禁止用 Fake 充真实结论。既有 I3.3 Fake Instrumentation **不计入**本任务真实通过 |

---

## 4. 真实 HTTP 请求/响应证据

| 来源 | 内容 | 分类 |
|---|---|---|
| `evidence/task-i3.4.3-d/http-probes-redacted.txt` | Host→8080 `/me`、`/grant` 无/假 Token → **401** | 只读探活（**非**会员状态联调） |
| `evidence/task-i3.4.3-d/server-membership-it-summary.json` | MembershipApiIT / ServiceIT 全绿摘要；含 YEARLY ACTIVE 期望字段（无 Token） | **隔离 Server 集成** |
| 模拟器 shell `curl` | 系统无 curl | 未采集到模拟器侧原始 HTTP |

**未采集**：模拟器 App 持有效 Bearer 的 `/me` 200 ACTIVE/REVOKED 脱敏抓包（环境缺口）。

---

## 5. 月卡 / 年卡 / 永久 / 叠加 / 撤销

| 状态 | 隔离 Server IT | Android JVM | 模拟器真实 HTTP |
|---|---|---|---|
| NONE | PASS (`meReturnsNone` / `queryUserWithNoMembership`) | PASS | BLOCKED |
| MONTHLY ACTIVE | PASS (`firstMonthlyGrant`) | PASS | BLOCKED |
| YEARLY ACTIVE | PASS (`meReturnsActiveMembership` / `firstYearlyGrant`) | PASS | BLOCKED |
| LIFETIME ACTIVE | PASS | PASS | BLOCKED |
| 月+年叠加 | PASS (`monthlyAndYearlyStack`) | PASS（展示 null→VIP会员） | BLOCKED |
| EXPIRED | PASS（可控时钟） | PASS | BLOCKED |
| REVOKED | PASS（revoke\*） | PASS | BLOCKED |

---

## 6. SessionInvalid 与账号切换

| 项 | 结果 |
|---|---|
| Server：过期/无效 Token → 401 | **PASS(S)** |
| Android：SessionInvalid → 清 VIP + SessionExpired；旧 401 不打新号 | **PASS(J)** |
| 模拟器真实 401 联动 UI | **BLOCKED** |

---

## 7. 24h Sticky 边界

| 项 | 结果 |
|---|---|
| JVM 可控 `elapsedRealtime`（含 23h59 / 24h / 永久仍受 24h / 早于 24h 的套餐到期 / 断网不刷新 confirmedAt） | **PASS(J)**（I3.4.3-B/C 回归） |
| 真实设备飞行模式 + 墙钟篡改 | **尚未执行** |

---

## 8. 应用锁 Service / Overlay 实测

**BLOCKED**（无真实 VIP 源注入模拟器）。未清除既有锁定配置；未宣称 Fake 路径为本任务通过。

---

## 9. 私密相册导入 / 恢复

**BLOCKED**（同上）。未访问用户真实私密文件；未用合成媒体做「真实 VIP」导入验收。

---

## 10. 回归测试与构建结果

| 命令 | 结果 |
|---|---|
| Server：`MembershipApiIT` + `MembershipServiceIT` + `MembershipEntitlementCalculatorTest` | **BUILD SUCCESSFUL**（42 tests, 0 fail） |
| Android：`feature.membership.data.*` + `EntitlementRepositoryTest` | **BUILD SUCCESSFUL** |
| I3.4.3-C 已记录三变体 assemble | Debug / localIntegration / Release **SUCCESS**（本轮 D 未强制重打三包） |
| `connectedDebugAndroidTest` | **未执行**（禁止） |

---

## 11. 未覆盖项目及风险

1. **真实双端主路径未通**：模拟器无法安全获得「隔离库上的 ACTIVE Bearer」。  
2. **8080 与会员测试库不一致**：Debug 指 3308；IT 指 3307 — 易误把开发库当联调目标。  
3. **8081 会员能力未验证**：历史试用隔离 ≠ 会员 grant/Session 完备。  
4. **应用锁/相册真实会员门禁**仍依赖 D 解阻后的模拟器 Instrumentation（手动 `am instrument`）。  
5. **风险**：若有人在 8080 手工 grant「图省事」，会污染 `aurora_app` — 本任务明确禁止。

### 解阻建议（需审批后另开任务，本轮不实施）

- 启动 **s32it@:8081 + aurora-mysql-it** 常驻；提供 **仅 test/s32it** 的内部夹具（或一次性 IT 工具）签发 Session + `MembershipService.grant(ADMIN)`；  
- Debug/localIntegration **会员 Base URL** 与登录对齐到该隔离实例；  
- 文档化「测试 Session 注入」且 **不 clear 生产数据**；  
- 再跑模拟器锁/相册清单。

---

## 12. 是否建议关闭 I3.4.3

| 子项 | 建议 |
|---|---|
| I3.4.3-A/B/C（协议客户端、Provider、生产 DI、刷新、展示） | **可关闭为完成** |
| I3.4.3-D（真实双端 + 锁/相册验收） | **保持开放 / 不关闭** |
| 整包 I3.4.3 | **不建议关闭** |
| 支付 / I3.4.4+ | **不进入** |

---

**本任务停止，等待审核。不进入支付开发。**

证据目录：`evidence/task-i3.4.3-d/`。
