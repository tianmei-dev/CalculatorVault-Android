# Task I3.3.3-B 应用锁模拟器验收报告（阻塞说明）

日期：2026-11-03。本轮**未**把 JVM 单测当作真机验收；**未**操作物理手机；**未**卸载/清数据；**未**改 8080/8081；**未**进入 I3.3.4；**未**实现生产后门。

---

## 1. 模拟器身份（PASS）

| 项 | 值 |
|---|---|
| ADB serial | `emulator-5554` |
| AVD 名 | `CalculatorVault_Test` |
| `ro.kernel.qemu` | `1` |
| model | `sdk_gphone64_x86_64` |
| API | 35 |
| 物理手机 | **无**（`adb devices` 仅 emulator-5554） |

每次敏感操作前均重新断言 AVD 名 + qemu=1。

---

## 2. 安装与构建常量（PASS / 部分）

| 检查项 | 结果 | 说明 |
|---|---|---|
| 包名 | PASS | `com.aurora.calculatorvault` |
| 覆盖安装 | PASS | `adb -s emulator-5554 install -r -t app-debug.apk` → Success |
| 签名兼容 | PASS | 已装包与 Debug 证书一致（Android Debug，SHA-256 `02e98a6a…4a6dad`）；**未卸载、未清数据** |
| 安装前版本 | — | `1.0.0-local-integration`（2026-11-02） |
| 安装后版本 | PASS | `1.0.0` debug（含 I3.3.3 代码，`lastUpdateTime=2026-11-03 11:54:52`） |
| `TRIAL_DEVICE_UPLOAD_ENABLED` | PASS | Debug BuildConfig = **false**（未加 `-P` 启用） |
| firstInstallTime | 保留 | 仍为 2026-11-02（数据未因覆盖安装重置） |

---

## 3. 逐项验收结果

| # | 用例 | 结果 | 说明 |
|---|---|---|---|
| 1 | 模拟器身份 / serial / qemu / 禁物理机 | **PASS** | 见 §1 |
| 2 | 安装测试 APK；包名/签名/试用上传开关；覆盖安装不清数据 | **PASS** | 见 §2 |
| 3 | 有效试用下开启测试应用锁定 | **BLOCKED** | 见 §4：无法在不破坏规则的前提下确认/强制「有效试用」并完成 UI 联调闭环（本轮停在可切换性阻塞） |
| 4 | 打开被锁应用时 Overlay 出现 | **BLOCKED** | 依赖 #3 |
| 5 | 无权益：Service 停、Overlay 关、可进应用、Room 配置不变 | **BLOCKED** | 见 §4：无安全的「失权」切换手段 |
| 6 | 恢复权益后原配置再生效 | **BLOCKED** | 依赖 #5 |
| 7 | 用户主动关闭项不自动重开 | **BLOCKED** | 依赖 #3–#6 的可控开关 |
| 8 | 后台 FGS 限制不循环、不删配置 | **BLOCKED** | 需可控失权/恢复；JVM 已覆盖决策，**不能**代替模拟器 |

**结论：本轮模拟器验收在「权益有效 ↔ 失效」安全切换能力上阻塞，不能宣称 I3.3.3 真机 Overlay/Service 行为验收通过。**

代码侧 I3.3.3 JVM 单测（Decision/Coordinator）仍有效，但按任务要求**不得**当作本交付的模拟器 PASS。

---

## 4. 阻塞原因（架构事实）

当前可安装的 Debug/生产 Application 路径：

1. `MembershipProvider` **固定** `DefaultMembershipProvider` → **永不授予 VIP**。  
2. `FakeMembershipProvider` **仅单元测试**注入，**未**接到 `CalculatorVaultApp`。  
3. 设备试用依赖真实 `EncryptedTrialStore` + 72h 规则；任务禁止：改试用规则、改试用库、生产重置入口、直接改试用 DB。  
4. 无 Debug Fake Clock / 无已批准的模拟器 Harness，无法在秒级制造「到期失权 / 恢复」。

因此在模拟器上无法合法完成「有权益保护 → 失权停保护 → 再恢复」的对照实验，否则只能：

- 等待真实试用走完（最长约 72h，且不可控），或  
- 擅自改 Keystore 试用数据 / 加生产后门（**明确禁止**）。

---

## 5. 建议的最小测试隔离设计（待你确认后再实现）

**目标：** 仅 Debug（或 `localIntegration` + debuggable）可切换权益；Release 零入口。

### 方案 A（推荐）：Instrumentation Test Application

- `androidTest` 使用自定义 `TestCalculatorVaultApp` / 或 `ApplicationProvider` 注入：
  - `FakeMembershipProvider`
  - 可选 `Fake`/`Manual` trial 状态源（**不写**生产 EncryptedTrialStore）
- 用例用 UiAutomator / Espresso：开锁 → 断言 Overlay → Fake 失权 → 断言无 Overlay/Service 停 → 查 Room `enabled` 未变 → Fake 恢复 → 再保护。
- **不**装到普通用户路径；**不**改 Release。

### 方案 B：Debug-only Harness Activity（需显式批准）

- `BuildConfig.DEBUG == true` 时才注册的隐藏 Debug 页或 `adb shell am` 可调的 Broadcast：
  - `grantFakeVip` / `clearFakeVip` / `forceTrialExpiredForUiOnly`（仅覆盖 Entitlement 输入，**禁止**写试用加密库）
- Release / 非 debuggable 包：**无组件、无字符串入口**。
- 文档与包名渠道限定「仅 CalculatorVault_Test」。

### 方案 C：仅等待真实试用到期

- 不推荐作为 I3.3.3-B 主路径（耗时长、不可重复、难测恢复）。

**请确认采用 A / B / 其他后，再继续模拟器 Overlay 联调；在此之前不实现任何生产后门。**

---

## 6. Room 配置快照（安装后基线）

- 覆盖安装后 `firstInstallTime` 仍为 2026-11-02，证明**未清数据**。
- 本轮因权益切换阻塞，**未执行开锁**，故无「失权前后 `app_lock_entries` diff」验收结论。
- 后续在批准的 Harness 步骤中，于开锁前/失权后用 `run-as` + 设备侧 `sqlite3` 或 Instrumentation 直接读 DAO，对比 `package_name/enabled`。

---

## 7. Service / Overlay 实际表现

| 项 | 本轮观察 |
|---|---|
| `AppLockMonitorService` | 安装后未主动开锁；`dumpsys activity services` 未见运行中的 AppLock 监控（符合「尚未开锁」） |
| Overlay | **未执行**开锁→进应用路径（阻塞于权益切换） |

---

## 8. 风险与未覆盖项

1. 真机/模拟器 Overlay 与 generation 竞态仍缺专用设备目视证明。  
2. 后台 FGS 拒绝后的前台恢复，缺模拟器步骤证明。  
3. 覆盖安装 Debug 后 API/试用上传开关与旧 localIntegration 不同（上传已关），试用**本地**状态应仍在；未验证联网校准副作用。  
4. 若未来批准 Debug Harness，必须防止误打进 Release 与物理机。

---

## 9. 本轮明确未做

- 未卸载、未 `pm clear`、未删 AVD。  
- 未改 8080/8081、未改试用加密存储。  
- 未实现 Debug Harness / 生产后门。  
- 未进入 I3.3.4。

**等待你确认 §5 测试隔离方案后，再继续 I3.3.3-B 剩余模拟器步骤。**
