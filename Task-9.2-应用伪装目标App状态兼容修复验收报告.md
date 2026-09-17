# Task 9.2 应用伪装目标 App 状态兼容修复验收报告

## 结论

已修复 `COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED` 被误判为“目标应用已停用”的问题。现有 FlipDrama 伪装记录无需重新创建，真机已从伪装入口成功打开目标 Activity。

## 根因与修改

原 `AndroidHiddenAppRuntime` 将 `DISABLED`、`DISABLED_USER`、`DISABLED_UNTIL_USED` 都判为 `Disabled`，所以目标应用虽然仍安装且有 Launcher Activity，也会被提前拦截。

本次修改文件：

- `app/src/main/java/com/aurora/calculatorvault/feature/hiddenapp/domain/HiddenAppRuntime.kt`
- `app/src/main/java/com/aurora/calculatorvault/feature/hiddenapp/domain/InstalledAppScanner.kt`
- `app/src/main/java/com/aurora/calculatorvault/app/CalculatorVaultApp.kt`
- `app/src/test/java/com/aurora/calculatorvault/feature/hiddenapp/domain/AppLaunchEligibilityTest.kt`（新增）
- `app/src/androidTest/java/com/aurora/calculatorvault/feature/hiddenapp/domain/AndroidHiddenAppRuntimeTest.kt`

新的 Runtime 判断如下：

| Package / enabled setting / 启动入口 | Runtime 结果 |
| --- | --- |
| Package 缺失 | `NotInstalled` |
| `DISABLED` 或 `DISABLED_USER` | `Disabled`，不尝试启动 |
| `DEFAULT` 或 `ENABLED` 且有入口 | `Available` |
| `DISABLED_UNTIL_USED` 且有入口 | `Available`，正常尝试启动 |
| Package 存在但无入口 | `NoLauncher`，走原有不可启动处理 |

启动入口优先使用 `getLaunchIntentForPackage()`；其在暂时停用状态返回空时，复用现有 `ACTION_MAIN + CATEGORY_LAUNCHER` 查询作为兼容回退，并使用 `MATCH_DISABLED_UNTIL_USED_COMPONENTS` 读取该类 Activity。只有解析到导出且启用的 Launcher Activity 才认为可启动。

应用伪装和应用管理的扫描器启用了上述兼容查询；应用锁继续使用原有扫描来源和规则，未改变其行为。

## 边界确认

- 未调用 `setApplicationEnabledSetting()`，未修改 FlipDrama 或任何目标 App 的状态。
- 没有 FlipDrama 包名、Samsung 厂商或 `auto_disabler` 的业务特判。
- 未修改 Room Schema、DisguiseEntry、Shortcut 数据或创建流程。
- 未新增 Android 权限；未修改 AppLock、私密相册和新手引导。
- Package 存在但实际 `startActivity` 失败时，仍保留原有 `ActivityNotFound`、`SecurityBlocked`、`Failed` 等启动失败分支，不映射成“目标应用已停用”。

## 验证

### 自动化测试与构建

- `:app:testDebugUnitTest`：212 tests，0 failures，0 errors。
- `:app:assembleDebug`：通过。
- `:app:assembleRelease`：通过。
- `:app:assembleDebugAndroidTest`：通过。
- compileSdk 37 / API 37 构建通过。
- 单元测试覆盖 DEFAULT、ENABLED、DISABLED、DISABLED_USER、DISABLED_UNTIL_USED 的状态映射。
- Android 测试覆盖 Package Missing、无 Launcher 的已安装 Package，以及指定真机目标的 Runtime 与应用选择器解析；测试前后断言没有改变目标 enabled setting。

### Samsung 真机：FlipDrama

验证时目标为 `com.nicedramas.freereel`：

- 修复前：`installed=true`、`enabled=4`（`DISABLED_UNTIL_USED`）、可解析 `.ui.CoverActivity`，但伪装入口显示“目标应用已停用”。
- 修复后：在仍为 `enabled=4` 时，Android 测试确认 Runtime 返回 `Available` 且应用伪装选择器可解析该 Package；未改动其 enabled setting。
- 使用现有伪装 Shortcut 的记录进入密码验证后，成功启动 `com.nicedramas.freereel/.ui.CoverActivity`，未出现“目标应用已停用”。
- 正常启动后，系统将该 App 改回 `enabled=0`；这是 Android 系统自己的正常状态变化，应用未调用任何启用/停用 API。

### 其他回归

- 明确 `DISABLED`、`DISABLED_USER`：单元测试确认仍被 Runtime 拦截；当前设备未人为改变其他 App 状态做破坏性真机测试。
- 卸载：Android Runtime 测试确认缺失 Package 返回 `NotInstalled` / `TargetNotInstalled` 原有链路。
- 已有 Shortcut：直接使用既有 FlipDrama Shortcut 数据成功验证，无需删除或重建。
- 应用管理／应用伪装：使用同一个兼容后的扫描器，暂时停用但有入口的 App 不再被筛除或标为停用。
- Pixel：当前未连接 Pixel，未执行该项回归。

## 已知问题

`DISABLED_UNTIL_USED` 的最终启动能否成功仍由系统和目标 App 决定；若普通 Android Intent 启动失败，产品会显示既有的通用启动失败结果，而不会误称为“目标应用已停用”。

后续设备 UI 操作没有继续执行：自动审批在完成核心真机验证后拒绝了新的 ADB 操作，原因是本会话额度限制。该限制不影响已完成的构建、测试和 FlipDrama 真机启动验证。
