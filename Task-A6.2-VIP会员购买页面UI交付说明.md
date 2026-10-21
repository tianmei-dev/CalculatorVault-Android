# Task A6.2 VIP会员购买页面 UI

本次完成全屏页面、套餐/支付方式选择、协议勾选、设置入口、来源参数和购买占位。没有接入 SDK、Server、订单、游客、试用计时或真实会员状态。

## 修改和新增文件

| 文件 | 变更 |
| --- | --- |
| `app/src/main/java/com/aurora/calculatorvault/core/navigation/AppRoute.kt` | 新增会员页路由及 source 参数 |
| `app/src/main/java/com/aurora/calculatorvault/feature/settings/presentation/SettingsScreen.kt` | 账号分组新增 VIP会员入口 |
| `app/src/main/java/com/aurora/calculatorvault/feature/vault/presentation/VaultMainScreen.kt` | 注册会员页目的地，连接设置入口与现有隐私政策目的地 |
| `app/src/main/java/com/aurora/calculatorvault/feature/membership/domain/VipPurchaseModels.kt` | 静态套餐目录、支付方式、来源、页面状态、不可变购买选择快照 |
| `app/src/main/java/com/aurora/calculatorvault/feature/membership/presentation/VipPurchaseViewModel.kt` | 选择状态、协议校验、重复点击限制、占位反馈 |
| `app/src/main/java/com/aurora/calculatorvault/feature/membership/presentation/VipPurchaseScreen.kt` | 全屏会员页面及局部展示组件 |
| `app/src/main/res/values/strings_membership.xml` | 全部会员页面文案 |
| `app/src/main/res/drawable/ic_payment_wechat.xml` | 本地微信支付示意矢量图标 |
| `app/src/main/res/drawable/ic_payment_alipay.xml` | 本地支付宝示意矢量图标 |
| `app/src/test/java/com/aurora/calculatorvault/feature/membership/VipPurchaseViewModelTest.kt` | 9 项状态、价格、防重复点击与来源测试 |
| `Task-A6.2-VIP会员购买页面UI交付说明.md` | 本说明 |

## 页面结构及复用

页面采用 Scaffold，顺序为顶部返回导航、VIP介绍、三项权益、三张横向套餐卡、支付方式、协议与说明。购买按钮位于 Scaffold.bottomBar，主体滚动时始终可见。

复用 VaultSecondaryTopBar、VaultCard、VaultSectionTitle、VaultIcons、AppMessageController 以及 AppColors、AppShapes、AppSpacing、AppDimensions、AppTextStyles。购买按钮采用与现有主按钮相同的紫色/圆角规范，使用最小高度而非固定高度，支持放大字体。未修改共享按钮、主题或添加 UI 依赖。

页面主体纵向滚动，套餐卡保持单行、等宽、等高；在可用宽度不足或字体放大时支持横向滚动，并将当前选择滚入可视范围，避免缩小字体或裁切价格。权益文案可换行，不设置固定内容高度。系统安全区域沿用外层保险箱 Scaffold 已提供并消费的 safeDrawing，内部不重复扣减。

会员恢复文案明确限定会员资格，不同步照片、视频、应用锁配置或本地密码。三种套餐均标明非自动续费，无虚构优惠标签。

## 导航和来源

- 入口：设置 → 账号 → VIP会员。
- 路由模式：`vault/vip?source={source}`。
- 调用方式：`VaultTabRoute.VipMembership.createRoute(VipPurchaseSource.Settings)`。
- 预留来源：Settings、AppLock、PrivateAlbum、TrialExpired。缺失或无法识别的值回落至 Settings。
- 当前只接通 Settings，未在其他模块增加会员拦截或跳转。
- 返回调用 popBackStack，保留原设置目的地及其原有滚动状态，不重建或清空设置导航栈。
- 隐私政策继续使用现有 `VaultTabRoute.LegalDocument` 和 `LegalDocumentScreen`，返回仍为同一会员页面实例。

## 状态管理

VipPurchaseViewModel 绑定会员页的导航条目，以 StateFlow 提供：selectedPlan、selectedPaymentMethod、agreementAccepted、purchaseSource、purchaseActionState。

首次进入默认为年卡、微信支付、协议未勾选。切换套餐不重置支付方式或勾选，切换支付方式不重置套餐。查看隐私政策返回时 ViewModel 保留，配置变更沿用该 ViewModel；退出后重新进入会创建新条目，恢复默认选择和未勾选协议。状态不使用静态全局变量，也不写入会员状态或本地会员持久化。

静态目录：月卡 590分/30天、年卡 1990分/365天、永久会员 2990分/永久有效。金额以整数分保存，以 BigDecimal 转换为 5.9、19.9、29.9，页面价格和底部按钮共同读取同一选择。

requestPurchase() 未勾选时只返回协议提示。勾选后只显示“支付功能即将开放”；采用单调时钟限制 1000ms 内重复操作，快速重复点击返回 null，不再次触发占位反馈。没有订单、登录成功或会员授予分支。

## 协议状态

项目当前没有会员服务协议。点击《会员服务协议》仅显示“会员服务协议待接入，当前仅提供页面预览”，页面也持续显示该说明；没有链接到用户协议或其他错误页面，没有编造正式法律文本。《隐私政策》打开已有页面。

## 后续 Server / SDK 接入点

1. 将 VipPlan 的本地静态目录替换为 Server 商品模型及服务端定价；目前 enum 名称不代表真实服务端商品编号。
2. `purchaseSelection()` 生成包含套餐、支付方式和来源的不可变 VipPurchaseSelection。后续购买协调逻辑可以在打开登录前保存该快照。
3. `requestPurchase()` 的明确标注分支是购买接入边界。后续应检查真实微信登录态；未登录调用 A6.1 的 LoginSource.MembershipPurchase，同一组件完成真实登录后继续原选择。本 Task 不触发或模拟这个流程。
4. 真实下单、SDK调用、支付结果校验、服务端订单查询、权益同步与恢复均待后续 Task。当前支付图标只作本地 UI 展示，不包含 SDK 或商户参数。
5. 会员服务协议正式文本和页面待后续提供；上线购买前需接入正式协议及对应确认流程。

## 构建和测试

执行：`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --console=plain`。

结果：BUILD SUCCESSFUL。45 个测试套件、225 项测试全部通过，0 失败、0 错误、0 跳过，包含新增 9 项会员页面测试及 A6.1 登录测试。构建只有既有弃用/参数命名等警告，会员模块无新增编译警告。

新增测试覆盖默认状态、准确价格和时长、两类单选互不重置、协议校验、同意后即时重试、快速点击和冷却边界、重新取消同意、重新进入恢复默认、购买快照和四种来源路由。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

本次未执行真机 UI 或设备导航自动化。原项目 MainActivity 在 Manifest 中固定 portrait，本 Task 未修改方向策略；页面按可用宽高自适应，横屏尺寸的实际视觉效果仍需在支持调整窗口尺寸的测试环境验收。

## 人工验收步骤

1. 正常通过现有密码验证进入保险箱，在设置的账号分组点击 VIP会员；确认是独立全屏页、顶部仅返回和标题、底部不显示保险箱 Tab 栏。
2. 检查深色紫色主题、介绍与三项权益，确认恢复说明不暗示同步任何本地私密数据。
3. 确认默认年卡 ¥19.9、365天、微信支付、协议未勾选。依次点击月卡、永久会员，确认只有一张选中，价格分别为 ¥5.9、¥29.9，按钮实时一致。
4. 在微信支付和支付宝之间切换，确认只有一项选中，套餐不改变；切换套餐后支付方式不改变。
5. 不勾选直接点击购买，确认提示先同意协议，无支付占位或订单。勾选后点击只显示“支付功能即将开放”。连续快速点击，确认不会重复触发占位操作，也不会出现已登录或 VIP 状态。
6. 点击会员服务协议，确认只提示待接入，既不进入其他协议也无虚构协议正文。
7. 选择永久会员、支付宝并勾选协议，打开隐私政策再返回，确认原选择、协议勾选及页面滚动状态仍保留。
8. 点击顶部返回以及系统返回，确认回到原设置页；重新打开会员页，确认恢复年卡、微信支付及协议未勾选。
9. 滚动主体至底部，确认购买按钮始终固定可见；在小屏、大屏、长屏和最大字体下检查内容换行、套餐横向滚动、价格可见性、底部按钮及系统导航栏安全间距。用可调整尺寸的环境检查宽屏短高度布局。
10. 进入会员页或阅读协议时切后台再返回，确认原有保险箱锁定、密码校验不变；回归 A6.1 登录入口、应用锁和私密相册，确认无 VIP 拦截或其他新增业务限制。

原有未提交改动、报告删除及 app/release 内容未作清理或覆盖。
