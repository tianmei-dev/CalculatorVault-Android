# Task I3.2-B 专用模拟器非破坏性验收

日期：2026-11-02。仅操作用户确认的专用模拟器；未改业务代码、Server配置、8080数据库或正式试用记录。

## 目标与安装

- 唯一运行模拟器：emulator-5554；AVD=CalculatorVault_Test；ro.kernel.qemu=1；API35。
- 另有1台物理手机，仅计数，不执行设备命令。安装前目标包不存在。
- 从此前启用包实际DEX核验：上传true、trial API及HTTP例外均为192.168.0.107:8081，账号API为192.168.0.107:8080，build type=localIntegration。
- 原包SHA-256与上轮启用构建证据一致（1e8d88205a6055a1b8b3cbefe715986811fdb20e88530677494eb5cc8e92c366）。原包未签名，给副本使用本机标准Android调试证书签名后仅安装模拟器，未读取正式JKS。签名不改变代码或上述配置。
- 本轮真实设备标识是该模拟器/该调试证书对应Android ID，不是正式证书或物理手机的标识；后续重装必须保持此副本和证书不变。未直接读取、输出或收集完整Android ID或HMAC。

## 逐项结果

| 项目 | 结果 | 证据/边界 |
|---|---|---|
| 模拟器serial、AVD、qemu及目标唯一性 | PASS | 每次操作前断言名称及qemu，命令固定-s emulator-5554 |
| 安装前启用开关/8081试用与8080账号配置 | PASS | 实际APK DEX静态字段解析，不仅检查源码 |
| 同意前无标识交接、无HTTP上传 | PASS（路径验证） | 首次隐私页CV_TRIAL_DIAG只有本地状态/同步触发，无2/3/4/5/6/7；同时核对provider及Repository的有效同意门禁。未进行底层API hook调用计数，不把它冒充系统级标识读取追踪 |
| 同意后真实标识链路与上传 | PASS | 生产AndroidDeviceIdentityProvider注入，stage2/3/4后HTTP200、业务0；没有替换为Fake provider；隔离库基线后唯一新增1条记录 |
| localIntegration HTTP例外真机生效 | PASS（模拟器API35） | 8081真实POST收到HTTP200、业务0；未改正式Release策略 |
| 首次联网试用ACTIVE | PASS | stage9 value1；隔离Server首次起点与到期不是额外发完整72h，仍体现本地此前消耗 |
| 断网 | PASS（请求失败路径） | 仅该模拟器关闭Wi-Fi及移动数据，保留数据冷启动后stage8 value2=ConnectException；没有Server成功响应；未因异常清数据 |
| 恢复联网 | PASS | 恢复Wi-Fi/数据并保留数据冷启动，第二次HTTP200、业务0、ACTIVE；这是冷启动恢复同步，不是跳过同进程15分钟成功冷却 |
| 同一Server记录、期限不延长 | PASS | 新增唯一记录id/started相同，expires从2026-11-05 12:57:52.957收紧为12:57:51.211（数据库原始时间，未转换时区） |
| 离线剩余分钟数的独立UI测量 | 未单独验证 | 当前仍在新安装创建密码页，未设置或记录PIN；不将状态日志当作剩余秒数日志。期限及冷启动恢复已验证 |
| 卸载重装/Server资格恢复 | 待确认，未执行 | 按用户要求停在破坏性操作前 |

## 初始网络失败及恢复

刚安装同意后曾出现ConnectException、TEMPORARILY_UNAVAILABLE；宿主8081健康检查HTTP200，而模拟器TCP探测为unreachable。关闭/恢复网络和重启App仍失败。仅对专用模拟器做保留数据reboot后，8081 TCP与App请求恢复成功。未改地址、宿主防火墙、Server或试用规则。此记录不能归因为App业务缺陷，也未证明具体宿主网络根因；本次后续断网恢复无需再重启模拟器即成功。

## 证据与隐私

evidence/task-i3.2-b/emulator/：

- verification.json：目标、签名、APK哈希及结果。
- before-consent-events.json、after-consent-events.json：首次门禁及最初失败阶段。
- after-reboot-events.json、online-before-offline-events.json：真实HTTP200/业务0/ACTIVE。
- validated-offline-events.json、validated-recovery-events.json：断网失败与恢复成功。
- db-baseline.json、db-before-offline.json、db-after-recovery.json：隔离库唯一新增测试记录的行号及时间元数据，未读标识或哈希。

Logcat只采集CV_TRIAL_DIAG，落盘仅stage/value整数；未抓取code、Token、Secret、Authorization、完整请求/响应体或其他Logcat标签。另有目标身份、界面和网络只读检查，不含真实手机私密数据。数据库仅只读查询aurora-mysql-it；新增/收紧测试记录由模拟器App按原接口完成。未访问8080数据库。

## 待确认的卸载重装操作

拟操作唯一目标：**emulator-5554 / CalculatorVault_Test / ro.kernel.qemu=1**。

包名：**com.aurora.calculatorvault**。

卸载会删除该模拟器此包的应用私有数据：本地加密试用记录、seen标记、Keystore中该包密钥、隐私同意记录、配置、Room/SharedPreferences/DataStore，以及该包可能存放的PIN、私密媒体和应用锁配置等沙盒内容。本轮未设置PIN或导入媒体，但不因此省略删除范围告知。

不会删除AVD、模拟器系统数据、其他应用、物理手机数据或隔离Server试用记录。不会重置Android ID；同模拟器、同用户、同调试证书重装后检验是否恢复同一Server记录，不能预先宣称必然稳定。

收到明确确认后，先再次核验目标身份，仅卸载该包，再安装同一已签名启用副本，重新明确同意隐私，验证8081返回已有记录、起点不变及expires不增加。本轮只验证现有ACTIVE资格恢复；不擅自把它改为过期或缩短以做破坏性到期测试。

当前已暂停在此确认点，不执行卸载、不进入I3.3。


后续状态更新（2026-11-02）：用户明确确认后已完成限定模拟器的卸载重装，恢复原ACTIVE试用记录、起点及期限不变，无新增试用记录。详见《Task-I3.2-B-卸载重装验收补充.md》。上文“等待确认”保留为操作前历史记录。
