# Task 4.0.2 真机证据索引

设备：Samsung SM-G973U  
系统：Android 12（API 31）  
ROM：One UI 4.0  
Launcher：Samsung One UI Home（`com.sec.android.app.launcher`）

## 已验证

- `02_saved_page.xml`：新建配置完成页，包含“添加到桌面”入口。
- `03_system_confirmation.xml`：Samsung Launcher 的系统确认页，包含 “Add to Home screen?”、Cancel、Add。
- `05_desktop_entry.png`：桌面生成的 `App Market` 入口，使用 CalculatorVault 内置浏览器图标。
- `10_details_status.xml`：Launcher 成功回调后，详情页状态为“已添加，请前往桌面查看”。
- `11_repeat_request_confirmation.xml`：同一配置再次请求前的二次确认。
- `12_system_repeat_confirmation.xml`：二次请求进入 Samsung Launcher 系统确认页。
- `13_after_system_cancel.xml`：系统取消后 App 不崩溃，仅提示“请求已提交，不代表系统一定已经添加”。
- `17_shortcut_fixed.png`：点击桌面入口后进入普通计算器，不进入 Vault、不启动目标 App。
- `18_shortcut_after_process_kill_fixed.png`：普通后台进程终止后，桌面入口仍可安全启动普通计算器。

## 截图限制

Vault 内页面启用了 `FLAG_SECURE`。ADB 对新建完成页、详情页和重复确认弹窗的 PNG 截图返回空文件，
因此这些页面保留真实 UIAutomator XML 作为证据；桌面和普通计算器页面提供真实 PNG。

## 未执行

- 没有可用的第二台品牌设备或本地 AVD，因此未执行第二种 Launcher 真机验证。
- 未经用户单独确认，没有重启实体手机，因此未执行手机重启后的入口验证。
- 当前 Samsung Launcher 支持固定快捷方式，无法在该设备上产生“不支持”页面的真实截图；
 该分支由单元测试覆盖。
