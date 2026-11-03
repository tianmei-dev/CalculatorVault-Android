# Task I3.2-B 卸载重装验收补充

日期：2026-11-02。用户已明确确认仅卸载emulator-5554上的com.aurora.calculatorvault并重装。

## 操作范围

操作前重新确认：emulator-5554、CalculatorVault_Test、ro.kernel.qemu=1。只操作该serial及指定包；未删除AVD或其他应用，未操作物理手机。卸载和安装均返回Success。

使用此前同一调试证书签名启用副本，安装前SHA-256与前次验收一致；未重签、换证书或更换标识。App试用地址仍8081、账号地址仍8080。未访问8080开发数据库。

卸载删除该模拟器此包本地数据及密钥，符合已授权范围；未清理或直接修改Server记录。重新启动展示隐私告知页，重新同意由测试操作完成；未设置/记录PIN，未导入媒体。

## 逐项结果

| 项目 | 结果 |
|---|---|
| 正确模拟器与指定包卸载重装 | PASS |
| 同一已签名启用APK、未重置系统/设备标识 | PASS |
| 重装后重新展示隐私告知 | PASS |
| 新进程同意前无标识交接/HTTP事件 | PASS（日志路径及既有门禁核对；非系统级API hook） |
| 同意后8081请求 | PASS：HTTP200、业务码0 |
| 试用状态恢复 | PASS：ACTIVE |
| Server同一条记录、起点不变 | PASS |
| Server到期时间未延长 | PASS：前后均2026-11-05 12:57:51.211（数据库原始时间） |
| 未额外领取新的试用记录 | PASS：隔离库max(id)/总数未变，基线后新增0条 |

这次验证的是已有ACTIVE试用资格恢复和不会重新领取完整72小时；没有把记录改为过期，因此不将“该真实模拟器已过期后重装不可复活”写成执行通过。该语义已有I3.2-A合成测试，真实到期模拟器用例仍属后续明确安排范围。本轮不新增重置或短期试用入口。

## 证据

- evidence/task-i3.2-b/emulator/before-uninstall-db.json：卸载前同一记录与隔离库计数。
- reinstall-before-consent-events.json：新进程同意前CV_TRIAL_DIAG。
- reinstall-after-consent-events.json：新进程HTTP200、业务0、ACTIVE。
- after-reinstall-db.json：只读记录及新增计数。
- reinstall-verification.json：全部布尔断言为true。
- verification.json：更新后的总体结果及APK哈希/证书元数据。

仅采集CV_TRIAL_DIAG日志，落盘stage/value整数；没有读取、输出真实Android ID、Token、Secret、Authorization、请求体或其他应用日志。数据库仅对隔离库做只读元数据核对，不输出设备哈希。

已完成用户确认的操作并停止，不进入I3.3，不再卸载或清数据。
