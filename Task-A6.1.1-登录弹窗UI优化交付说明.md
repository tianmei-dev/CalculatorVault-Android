# Task A6.1.1 登录弹窗 UI 优化

## 本次文件列表

1. `app/src/main/java/com/aurora/calculatorvault/feature/login/presentation/WeChatLoginDialog.kt`
2. `app/src/main/res/values/strings_login.xml`
3. `app/src/main/res/drawable/ic_wechat.xml`
4. `Task-A6.1.1-登录弹窗UI优化交付说明.md`

## UI 调整及组件复用

- 在现有 `WeChatLoginDialog` 内调整布局，仍使用 Material3 AlertDialog。依次展示 App 图标、欢迎标题、副标题、微信登录主按钮、暂不登录次按钮和协议区域。
- 顶部 56dp 图标直接读取现有 `R.mipmap.ic_launcher`，将当前系统适配的 launcher drawable 渲染为位图，兼容项目中的 adaptive icon；未生成或替换 App 图标。
- 主标题“欢迎使用隐藏计算器”复用 SemiBold 的 `AppTextStyles.SectionTitle`；副标题改为“登录后可购买会员，更换设备也能恢复会员权益”。图标及标题、副标题居中。
- 主按钮复用 AppColors.AccentPrimary 紫色与现有主按钮颜色/圆角规范。微信图标改用深色气泡配紫色眼点，与深色按钮文字一致，提升紫色按钮上的对比度。
- 新增同宽的“暂不登录”按钮，采用深色填充、紫色描边及紫色文字，直接调用原有 `onDismiss`。移除原右上角 X。系统返回与点击外部的关闭处理不变。
- 两按钮均使用 `AppDimensions.Button` 的 48dp 最小高度、`AppShapes.Medium` 圆角。没有直接使用固定高度的 VaultPrimaryButton/VaultSecondaryButton，以保留微信图标插槽并允许大字体时自然增高。未更改这些共享按钮组件。
- 间距、圆角、字号、文字色均复用现有 Design System。协议区左对齐，使用段落换行策略及紫色协议链接；保留 Material Checkbox 的触控区和无障碍描述。
- 全部内容位于纵向滚动区域内，不设置固定弹窗高度；继续使用标准平台 Dialog 窗口边界和 SecureOn 窗口保护。

当前聊天未提供参考截图，本实现依据任务中明确的布局顺序及视觉要求完成，未进行截图逐像素比对。

## 逻辑保留

本次没有修改 LoginPromptViewModel、LoginPromptPreferences、VaultMainScreen、协议页面或导航、首次启动及保险箱会话逻辑，也没有添加登录/会员/支付业务。

暂不登录无需协议勾选；重新打开默认未勾选，查看协议返回恢复当前勾选状态，未勾选点击登录提示原有错误，勾选后仅显示原有 SDK 占位提示。首次展示记录、设置页入口及预留来源继续沿用 A6.1。

## 构建和测试

验证命令：`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --console=plain`。

验证结果：BUILD SUCCESSFUL。Debug APK 已生成，44 个测试套件、216 项单元测试全部通过，0 失败、0 错误、0 跳过，其中 A6.1 的 4 项登录测试通过。构建保留了项目原有的弃用及参数命名警告，本次弹窗文件无新增编译警告。

最初受限环境无法创建 Gradle 缓存锁文件；在允许的构建执行权限下完成上述离线构建与测试，未修改 Gradle 或项目依赖配置。

本次未新增重复验证 UI 实现细节的单元测试，沿用 A6.1 登录状态与持久化测试及现有回归测试；未执行真机 UI 验收。

## 人工验收

1. 在全新安装的测试环境完成隐私协议和密码设置，输入密码进入应用管理首页。确认弹窗自动展示一次，深色卡片内按上述顺序展示内容，App 图标与桌面图标一致。
2. 确认主标题及副标题内容准确且居中，双按钮同宽、正常字体时均为 48dp 高，微信图标与文字清晰，右上角不再显示 X。
3. 不勾选协议点击微信快捷登录，确认原有协议提示出现；再点击暂不登录，确认直接关闭并可继续使用。
4. 在设置页重新打开，确认默认未勾选；分别进入用户协议、隐私政策后返回，确认恢复弹窗及本次勾选状态。
5. 勾选后点击登录，确认仅显示暂未开放提示，没有真实登录状态；分别验证暂不登录、系统返回、点击外部均可关闭。
6. 切换 Tab、退出保险箱再进入、结束进程后冷启动，确认不会再次自动弹出，设置页仍可主动打开。
7. 在小屏幕及系统最大字体下检查换行与滚动，确保图标、文本、双按钮、协议及提示都可访问，没有内容裁切或系统导航栏遮挡。
8. 弹窗或协议页面切后台后返回，确认原有后台锁定、密码验证仍正常；检查现有应用锁和私密相册入口可正常使用。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
