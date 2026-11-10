package com.aurora.calculatorvault.feature.payment

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.IsolatedTestSessionAccess
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.IsolatedTestSessionBridge
import com.aurora.calculatorvault.feature.privatemedia.SyntheticMediaFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I3.5.4-B: Android x 8081 isolated payment acceptance on CalculatorVault_Test.
 *
 * Real UI (UiAutomator), real Bearer session (Debug-only bridge), real HTTP to s32it.
 * The PC side simulates payment through the loopback-only fixture control after this test logs
 * `ORDER_CREATED`. Each method is run individually via `am instrument -e class ...#method` with
 * `-e account <index>`; tokens are never logged.
 */
@RunWith(AndroidJUnit4::class)
class IsolatedPaymentE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context.applicationContext as CalculatorVaultApp
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var catalog: List<IsolatedTestSessionBridge.FixtureAccount>

    @Before
    fun setUp() {
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())
        assertEquals("CalculatorVault_Test", shell("getprop ro.boot.qemu.avd_name").trim())
        assumeTrue(IsolatedTestSessionAccess.isInstallEnabled())
        assertEquals("http://10.0.2.2:8081", BuildConfig.API_BASE_URL)
        assertTrue(BuildConfig.PAYMENT_TEST_KIND_ALLOWED)
        EntitlementInstrumentationBridge.clear(app)
        assertNull(app.entitlementRepository.stateOverride)
        assertNull(app.entitlementRepository.actionDecisionOverride)
        catalog = IsolatedTestSessionBridge.loadCatalog(args.getString("accounts") ?: ACCOUNTS_FILE).values.toList()
        evidence("setup", "api=${BuildConfig.API_BASE_URL} trial=${app.deviceTrial.state.value.status} " +
            "night=${shell("cmd uimode night").trim()}")
    }

    // ---------------- B01 ----------------

    @Test
    fun b01_plansDisplay_darkTheme_failureRetry() {
        installAccount(arg("account", 1))
        openVipPage()
        assertPlansShown("light")
        screenshot("b01_plans_light")

        shell("cmd uimode night yes")
        SystemClock.sleep(2_500)
        openVipPage()
        assertPlansShown("dark")
        screenshot("b01_plans_dark")
        shell("cmd uimode night no")
        SystemClock.sleep(2_000)

        // Failure + retry: open the page while offline, then reload after the network is back.
        device.pressBack()
        setAirplane(true)
        try {
            SystemClock.sleep(2_000)
            openVipPage()
            val failed = device.wait(Until.hasObject(By.text("套餐加载失败，请检查网络后重试")), 20_000)
            val button = purchaseButtonState()
            screenshot("b01_plans_failed")
            evidence("b01_failure", "failedState=$failed button[$button] noPrice=${!device.hasObject(By.textContains("¥"))}")
            assertTrue(failed)
            assertTrue("purchase must be disabled without a server catalog: $button", button == "text=立即开通 enabled=false")
        } finally {
            setAirplane(false)
        }
        assertTrue(waitUntil(60_000) {
            device.findObject(By.text("重新加载"))?.click()
            SystemClock.sleep(3_000)
            device.hasObject(By.text("¥19.90"))
        })
        evidence("b01_retry", "plansAfterRetry=true button=${device.findObject(By.textStartsWith("立即开通"))?.text}")
    }

    // ---------------- B02 / B03 / B04 / B06 ----------------

    @Test
    fun b02_b03_createMonthly_waitForPcPayment_vipAndEntitlements(): Unit = runBlocking {
        val account = installAccount(arg("account", 1))
        val lockBefore = decision(EntitlementCapability.APP_LOCK_PROTECTION)
        val importBefore = decision(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
        evidence("b04_before", "vip=${isVip()} appLock=$lockBefore import=$importBefore")

        openVipPage()
        clickText("月卡")
        assertTrue(device.wait(Until.hasObject(By.text("立即开通 ¥5.90")), 5_000))
        acceptAgreement()
        val buy = device.findObject(By.text("立即开通 ¥5.90"))
        // B06: rapid repeated taps.
        repeat(5) { runCatching { buy.click() } }
        assertTrue("order panel", device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val orderNo = waitPrefsOrderNo()
        val requestId = prefs().getString("client_request_id", null)
        val panel = panelSnapshot()
        screenshot("b02_waiting")
        evidence("b02_panel", panel)
        assertTrue(panel.contains("套餐：月卡") && panel.contains("金额：¥5.90") && panel.contains("待支付"))
        assertTrue(panel.contains("剩余支付时间") && panel.contains("当前为测试支付环境，请在电脑端模拟支付结果"))
        assertFalse("no browser for TEST", device.currentPackageName != context.packageName)
        // B06: repeated manual refreshes while waiting.
        repeat(3) { device.findObject(By.text("刷新支付状态"))?.click(); SystemClock.sleep(400) }
        marker("ORDER_CREATED", "orderNo=$orderNo plan=MONTHLY clientRequestId=$requestId userId=${account.userId}")

        val statuses = watchStatusesUntilSynced(240_000)
        evidence("b03_statuses", statuses.joinToString(" -> "))
        screenshot("b03_fulfilled")
        assertTrue("会员开通成功 shown", device.hasObject(By.text("会员开通成功")))
        val vip = app.entitlementRepository.state.value.source as EntitlementSource.Vip
        assertEquals(account.userId, vip.accountId)
        assertEquals("MONTHLY", vip.planType)
        evidence("b03_vip", "userId=${vip.accountId} plan=${vip.planType} expiresAt=${vip.expiresAtEpochMillis?.let { Instant.ofEpochMilli(it) }}")

        // B04: entitlement linkage through the unchanged EntitlementRepository.
        val lockAfter = decision(EntitlementCapability.APP_LOCK_PROTECTION)
        val importAfter = decision(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
        evidence("b04_after", "appLock=$lockAfter import=$importAfter")
        assertEquals("Allowed", lockAfter)
        assertEquals("Allowed", importAfter)
        verifyAppLockAndAlbum()

        // Membership row in Settings.
        device.findObject(By.text("完成"))?.click()
        SystemClock.sleep(1_000)
        openSettingsTab()
        val row = device.wait(Until.hasObject(By.text("月卡会员")), 8_000)
        evidence("b04_settings", "monthlyRow=$row")
        assertTrue(row)
    }

    /** B04 display check for an account that is already VIP from a real purchase (no new order). */
    @Test
    fun b04_settingsMembershipRow_andVipPageState() {
        val account = catalog[arg("account", 1) - 1]
        IsolatedTestSessionBridge.install(app, account)
        assertTrue(waitUntil(20_000) { isVip() })
        openSettingsTab()
        val row = device.wait(Until.hasObject(By.text("月卡会员")), 10_000)
        val expiry = device.findObject(By.textStartsWith("有效期至"))?.text
        screenshot("b04_settings")
        evidence("b04_settings", "monthlyRow=$row expiryLine=$expiry vip=${(app.entitlementRepository.state.value.source as? EntitlementSource.Vip)?.planType}")
        assertTrue(row)
    }

    // ---------------- B05 ----------------

    /** Keeps same-account /membership/me requests in flight back-to-back while the PC pays. */
    @Test
    fun b05_fulfilledWhileMeInFlight_syncsWithoutManualRetry() {
        val account = installAccount(arg("account", 1))
        openVipPage()
        clickText("月卡")
        acceptAgreement()
        device.findObject(By.text("立即开通 ¥5.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val orderNo = waitPrefsOrderNo()
        val pressure = java.util.concurrent.atomic.AtomicBoolean(true)
        val issued = java.util.concurrent.atomic.AtomicInteger(0)
        val loop = Thread {
            while (pressure.get()) {
                instrumentation.runOnMainSync {
                    if (app.membershipRefreshCoordinator.refreshManual()) issued.incrementAndGet()
                }
                SystemClock.sleep(20)
            }
        }
        loop.start()
        marker("ORDER_CREATED", "orderNo=$orderNo plan=MONTHLY userId=${account.userId} purpose=b05")
        val start = SystemClock.uptimeMillis()
        var sawSyncingHint = false
        val synced = waitUntil(120_000, pollMs = 100) {
            if (device.hasObject(By.textStartsWith("支付成功，会员状态同步中"))) sawSyncingHint = true
            device.hasObject(By.text("会员开通成功"))
        }
        pressure.set(false)
        loop.join(2_000)
        val retryButton = device.hasObject(By.text("重新同步会员状态"))
        val vip = app.entitlementRepository.state.value.source as? EntitlementSource.Vip
        evidence(
            "b05",
            "synced=$synced afterMs=${SystemClock.uptimeMillis() - start} concurrentManualMe=${issued.get()} " +
                "sawSyncingHint=$sawSyncingHint manualRetryShown=$retryButton vipPlan=${vip?.planType} " +
                "expiresAt=${vip?.expiresAtEpochMillis?.let { Instant.ofEpochMilli(it) }}",
        )
        assertTrue(synced)
        assertFalse(retryButton)
    }

    // ---------------- B07 ----------------

    @Test
    fun b07_phase1_yearlyOrder_rotate_background_revalidate() {
        val account = installAccount(arg("account", 2))
        openVipPage()
        clickText("年卡")
        acceptAgreement()
        device.findObject(By.text("立即开通 ¥19.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val orderNo = waitPrefsOrderNo()
        marker("ORDER_CREATED", "orderNo=$orderNo plan=YEARLY userId=${account.userId} phase=1")

        // Rotation -> Activity recreation.
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 1")
        SystemClock.sleep(3_000)
        val afterRotate = device.wait(Until.hasObject(By.text("订单支付")), 10_000)
        val suffix = orderNo.takeLast(10)
        val sameOrder = device.hasObject(By.textContains(suffix))
        screenshot("b07_rotated")
        shell("settings put system user_rotation 0")
        shell("settings put system accelerometer_rotation 1")
        SystemClock.sleep(2_000)
        evidence("b07_rotate", "panelKept=$afterRotate sameOrder=$sameOrder prefsOrder=${prefs().getString("order_no", null) == orderNo}")
        assertTrue(afterRotate && sameOrder)

        // Background -> foreground.
        device.pressHome()
        SystemClock.sleep(3_000)
        bringToFront()
        val back = reachOrderPanel(orderNo)
        evidence("b07_foreground", "panelAfterForeground=$back session=${app.accountSession.state.value::class.simpleName}")
        assertTrue(back)

        // Same-account re-validation keeps the order flow.
        app.accountSession.revalidate()
        SystemClock.sleep(4_000)
        val keptAfterRevalidate = device.hasObject(By.text("订单支付")) && device.hasObject(By.textContains(suffix))
        evidence("b07_revalidate", "kept=$keptAfterRevalidate session=${app.accountSession.state.value}")
        assertTrue(keptAfterRevalidate)
        marker("PHASE1_DONE", "orderNo=$orderNo")
        // Leave the order pending; the PC side force-stops the app before phase 2.
    }

    @Test
    fun b07_phase2_processRestore_thenPcPayment() {
        val account = catalog[arg("account", 2) - 1]
        assertTrue(waitUntil(20_000) { (app.accountSession.state.value as? AccountSession.Authenticated)?.account?.userId == account.userId })
        val stored = prefs().getString("order_no", null)
        assertNotNull("metadata survived process death", stored)
        openVipPage()
        val resumed = device.wait(Until.hasObject(By.text("订单支付")), 20_000) &&
            device.hasObject(By.textContains(stored!!.takeLast(10)))
        screenshot("b07_restored")
        evidence("b07_restore", "pid=${android.os.Process.myPid()} resumed=$resumed orderNo=$stored")
        assertTrue(resumed)
        marker("RESTORED_READY", "orderNo=$stored")
        val statuses = watchStatusesUntilSynced(240_000)
        evidence("b07_statuses", statuses.joinToString(" -> "))
        val vip = app.entitlementRepository.state.value.source as EntitlementSource.Vip
        assertEquals("YEARLY", vip.planType)
        evidence("b07_vip", "plan=${vip.planType} expiresAt=${vip.expiresAtEpochMillis?.let { Instant.ofEpochMilli(it) }}")
    }

    // ---------------- B08 ----------------

    @Test
    fun b08_switchAccountWhileWaiting_thenLogoutWhileWaiting() {
        val first = installAccount(arg("account", 3))
        openVipPage()
        clickText("月卡")
        acceptAgreement()
        device.findObject(By.text("立即开通 ¥5.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val firstOrder = waitPrefsOrderNo()

        val second = catalog[arg("second", 4) - 1]
        IsolatedTestSessionBridge.install(app, second)
        assertTrue(waitUntil(15_000) { (app.accountSession.state.value as? AccountSession.Authenticated)?.account?.userId == second.userId })
        val panelGone = waitUntil(10_000) { !device.hasObject(By.text("订单支付")) }
        evidence("b08_switch", "panelGone=$panelGone newUser=${second.userId}")
        assertTrue(panelGone)
        marker("ORDER_CREATED", "orderNo=$firstOrder plan=MONTHLY userId=${first.userId} purpose=late-for-old-account")
        // PC pays the old account's order now; the new account must stay unaffected.
        assertTrue(waitForMarkerAck(120_000))
        SystemClock.sleep(12_000)
        val leaked = isVip() || device.hasObject(By.text("会员开通成功"))
        evidence("b08_after_old_paid", "newAccountVip=${isVip()} successShown=${device.hasObject(By.text("会员开通成功"))} " +
            "source=${app.entitlementRepository.state.value.source::class.simpleName}")
        assertFalse(leaked)

        // Logout while waiting on the second account's own order.
        clickText("月卡")
        acceptAgreementIfNeeded()
        device.findObject(By.text("立即开通 ¥5.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val secondOrder = waitPrefsOrderNo()
        app.accountSession.logout()
        val stopped = waitUntil(10_000) { !device.hasObject(By.text("订单支付")) }
        evidence("b08_logout", "panelGone=$stopped session=${app.accountSession.state.value} secondOrder=$secondOrder vip=${isVip()}")
        assertTrue(stopped)
        assertFalse(isVip())
    }

    // ---------------- B09 ----------------

    @Test
    fun b09_offlineQuery_thenServerSide401() {
        installAccount(arg("account", 5))
        openVipPage()
        // A pending order from an earlier attempt is resumed instead of creating another.
        if (!device.wait(Until.hasObject(By.text("订单支付")), 8_000)) {
            clickText("月卡")
            acceptAgreement()
            device.findObject(By.text("立即开通 ¥5.90")).click()
            assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        }
        val orderNo = waitPrefsOrderNo()
        setAirplane(true)
        try {
            val offlineAt = SystemClock.uptimeMillis()
            var pendingThroughout = true
            val failedHint = waitUntil(60_000, pollMs = 500) {
                if (!device.hasObject(By.text("待支付"))) pendingThroughout = false
                device.hasObject(By.text(OFFLINE_HINT)) || device.hasObject(By.text(QUERY_FAILED_HINT))
            }
            val offlineHintShown = device.hasObject(By.text(OFFLINE_HINT))
            val hintAfterMs = SystemClock.uptimeMillis() - offlineAt
            val stillPending = device.hasObject(By.text("待支付"))
            screenshot("b09_offline")
            evidence("b09_offline", "queryFailedHint=$failedHint offlineHint=$offlineHintShown afterMs=$hintAfterMs " +
                "statusStillPending=$stillPending " +
                "pendingThroughout=$pendingThroughout paidOrFailedShown=${device.hasObject(By.text("会员已开通"))}")
            assertTrue(failedHint && stillPending && pendingThroughout)
        } finally {
            setAirplane(false)
        }
        val onlineAt = SystemClock.uptimeMillis()
        val recovered = waitUntil(90_000, pollMs = 500) {
            !device.hasObject(By.text(OFFLINE_HINT)) && !device.hasObject(By.text(QUERY_FAILED_HINT)) &&
                device.hasObject(By.text("待支付"))
        }
        evidence("b09_recovered", "recovered=$recovered afterMs=${SystemClock.uptimeMillis() - onlineAt}")
        assertTrue(recovered)

        marker("REVOKE_SESSION", "orderNo=$orderNo")
        assertTrue(waitForMarkerAck(120_000))
        val expired = waitUntil(30_000) { app.accountSession.state.value == AccountSession.SessionExpired }
        val panelGone = waitUntil(5_000) { !device.hasObject(By.text("订单支付")) }
        screenshot("b09_401")
        evidence("b09_401", "session=${app.accountSession.state.value} panelGone=$panelGone vip=${isVip()}")
        assertTrue(expired && panelGone)
    }

    @Test
    fun b09_expiry_phase1_createAndLeave() {
        val account = installAccount(arg("account", 6))
        openVipPage()
        clickText("月卡")
        acceptAgreement()
        device.findObject(By.text("立即开通 ¥5.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        marker("ORDER_CREATED", "orderNo=${waitPrefsOrderNo()} plan=MONTHLY userId=${account.userId} purpose=expiry")
    }

    @Test
    fun b09_expiry_phase2_expiredThenLatePayment() {
        val account = catalog[arg("account", 6) - 1]
        assertTrue(waitUntil(20_000) { (app.accountSession.state.value as? AccountSession.Authenticated)?.account?.userId == account.userId })
        openVipPage()
        val expired = device.wait(Until.hasObject(By.textStartsWith("订单已超时")), 30_000)
        screenshot("b09_expired")
        evidence("b09_expired", "expiredShown=$expired vip=${isVip()} goPayButton=${device.hasObject(By.text("前往支付"))}")
        assertTrue(expired)
        // Automatic polling must stop on EXPIRED: no UI interaction, measure this process's traffic.
        SystemClock.sleep(3_000)
        val uid = android.os.Process.myUid()
        val txBefore = android.net.TrafficStats.getUidTxPackets(uid)
        marker("IDLE_START", "uid=$uid")
        SystemClock.sleep(30_000)
        marker("IDLE_END", "uid=$uid")
        val txDelta = android.net.TrafficStats.getUidTxPackets(uid) - txBefore
        // Per-UID packets include non-order traffic; the PC side attributes 8081 connections.
        evidence("b09_polling_window", "idleWindowMs=30000 uidTxPacketsDelta=$txDelta stillExpired=${device.hasObject(By.textStartsWith("订单已超时"))}")
        marker("EXPIRED_READY", "orderNo=${prefs().getString("order_no", null)}")
        assertTrue(waitForMarkerAck(120_000))
        val statuses = mutableListOf<String>()
        val synced = waitUntil(90_000) {
            device.findObject(By.text("刷新支付状态"))?.click()
            SystemClock.sleep(3_000)
            currentStatus()?.let { if (statuses.lastOrNull() != it) statuses += it }
            device.hasObject(By.text("会员开通成功"))
        }
        evidence("b09_late_payment", "statuses=${statuses.joinToString(" -> ")} synced=$synced vip=${isVip()}")
        assertTrue(synced)
    }

    /**
     * Short-TTL server (approved 8081 restart with order-ttl=2m): the open waiting page must pick
     * up EXPIRED by itself and then stop automatic polling. Query counts come from [PaymentQueryProbe].
     */
    @Test
    fun b09c_shortTtl_pageAutoExpires_thenPollingStops() {
        val account = installAccount(arg("account", 2))
        val probe = PaymentQueryProbe.install(app)
        openVipPage()
        clickText("月卡")
        acceptAgreement()
        device.findObject(By.text("立即开通 ¥5.90")).click()
        assertTrue(device.wait(Until.hasObject(By.text("订单支付")), 20_000))
        val orderNo = waitPrefsOrderNo()
        val created = SystemClock.elapsedRealtime()
        val remainingText = device.findObject(By.textStartsWith("剩余支付时间"))?.text
        marker("ORDER_CREATED", "orderNo=$orderNo plan=MONTHLY userId=${account.userId} purpose=short-ttl")

        SystemClock.sleep(20_000)
        val pendingQueries = probe.queriesBetween(created, SystemClock.elapsedRealtime())
        evidence("b09c_pending", "remainingAtCreate=$remainingText queriesFirst20s=$pendingQueries")

        // No taps: the page must reach EXPIRED through its own polling.
        val autoExpired = waitUntil(240_000, pollMs = 500) { device.hasObject(By.textStartsWith("订单已超时")) }
        val expiredAt = SystemClock.elapsedRealtime()
        evidence("b09c_auto_expired", "autoExpired=$autoExpired afterCreateMs=${expiredAt - created} " +
            "totalQueries=${probe.queryTimes.size} goPay=${device.hasObject(By.text("前往支付"))}")
        assertTrue(autoExpired)

        SystemClock.sleep(2_000)
        val idleFrom = SystemClock.elapsedRealtime()
        SystemClock.sleep(45_000)
        val idleQueries = probe.queriesBetween(idleFrom, SystemClock.elapsedRealtime())
        evidence("b09c_polling_stopped", "idleWindowMs=45000 orderQueriesInWindow=$idleQueries stillExpired=${device.hasObject(By.textStartsWith("订单已超时"))}")
        assertTrue(pendingQueries >= 5)
        assertEquals(0, idleQueries)

        // Manual refresh still works after EXPIRED (one query, then stops again).
        val before = probe.queryTimes.size
        device.findObject(By.text("刷新支付状态"))?.click()
        SystemClock.sleep(15_000)
        evidence("b09c_manual_refresh", "queriesFromOneTapIn15s=${probe.queryTimes.size - before} stillExpired=${device.hasObject(By.textStartsWith("订单已超时"))}")
        assertEquals(1, probe.queryTimes.size - before)
    }

    /** Final cleanup: drop the isolated test session locally (no server call) and leftover metadata. */
    @Test
    fun zz_cleanup_abandonTestSession() {
        app.accountSession.abandonLocalSession()
        assertTrue(waitUntil(5_000) { app.accountSession.state.value == AccountSession.Guest })
        evidence("cleanup", "session=Guest pendingOrderMeta=${prefs().getString("order_no", null) != null} vip=${isVip()}")
    }

    // ---------------- helpers ----------------

    private fun installAccount(index: Int): IsolatedTestSessionBridge.FixtureAccount {
        val account = catalog[index - 1]
        IsolatedTestSessionBridge.install(app, account)
        assertTrue("authenticated", waitUntil(20_000) {
            (app.accountSession.state.value as? AccountSession.Authenticated)?.account?.userId == account.userId
        })
        SystemClock.sleep(1_500)
        evidence("account", "index=$index userId=${account.userId} vip=${isVip()}")
        return account
    }

    private fun openVipPage() {
        bringToFront()
        unlockIfNeeded()
        if (device.hasObject(By.text("选择套餐")) || device.hasObject(By.text("订单支付"))) return
        openSettingsTab()
        val titles = listOf("未开通VIP", "开通VIP", "免费试用中", "月卡会员", "年卡会员", "永久会员", "VIP会员")
        var row: androidx.test.uiautomator.UiObject2? = null
        waitUntil(10_000) {
            row = titles.firstNotNullOfOrNull { device.findObject(By.text(it)) }
            if (row == null && !device.hasObject(By.text("账号"))) {
                device.findObjects(By.text("设置")).lastOrNull()?.click()
                SystemClock.sleep(800)
            }
            row != null
        }
        assertNotNull("membership row; visible=${visibleTexts()}", row)
        row!!.click()
        assertTrue("vip page", device.wait(Until.hasObject(By.text("会员权益")), 10_000))
        SystemClock.sleep(1_000)
    }

    private fun reachOrderPanel(orderNo: String): Boolean {
        unlockIfNeeded()
        if (!device.hasObject(By.text("订单支付"))) openVipPage()
        return device.wait(Until.hasObject(By.text("订单支付")), 20_000) &&
            device.hasObject(By.textContains(orderNo.takeLast(10)))
    }

    private fun openSettingsTab() {
        if (device.currentPackageName != context.packageName) bringToFront()
        unlockIfNeeded()
        val tab = device.findObjects(By.text("设置")).lastOrNull()
        assertNotNull("settings tab", tab)
        tab!!.click()
        SystemClock.sleep(1_000)
    }

    private fun unlockIfNeeded() {
        waitUntil(8_000) { device.hasObject(By.desc("数字 1")) || device.hasObject(By.text("设置")) || device.hasObject(By.text("会员权益")) }
        if (!device.hasObject(By.desc("数字 1"))) return
        for (digit in PIN) {
            device.wait(Until.findObject(By.desc("数字 $digit")), 3_000)!!.click()
            SystemClock.sleep(150)
        }
        device.findObject(By.desc("计算结果")).click()
        assertTrue(waitUntil(8_000) { device.hasObject(By.text("设置")) })
    }

    private fun bringToFront() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
        SystemClock.sleep(800)
    }

    private fun assertPlansShown(theme: String) {
        val ok = device.wait(Until.hasObject(By.text("¥19.90")), 20_000)
        val texts = listOf("月卡", "年卡", "永久会员", "¥5.90", "¥19.90", "¥29.90", "30天", "365天", "永久有效", "立即开通 ¥19.90")
        val missing = texts.filterNot { device.hasObject(By.text(it)) }
        val noChannelPicker = !device.hasObject(By.text("微信支付")) && !device.hasObject(By.text("支付宝"))
        val cashierNote = device.hasObject(By.text("通过悦森统一收银台完成支付，可在收银台中选择付款方式"))
        evidence("b01_$theme", "loaded=$ok missing=$missing noChannelPicker=$noChannelPicker cashierNote=$cashierNote")
        assertTrue(ok && missing.isEmpty() && noChannelPicker && cashierNote)
    }

    private fun acceptAgreement() {
        val box = device.findObject(By.desc("我已阅读并同意《会员服务协议》和《隐私政策》"))
        assertNotNull("agreement checkbox", box)
        if (!box.isChecked) box.click()
        SystemClock.sleep(300)
    }

    private fun acceptAgreementIfNeeded() {
        device.findObject(By.desc("我已阅读并同意《会员服务协议》和《隐私政策》"))?.let { if (!it.isChecked) it.click() }
    }

    private fun clickText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000)
        assertNotNull(text, node)
        node.click()
        SystemClock.sleep(400)
    }

    private fun currentStatus(): String? = STATUS_TEXTS.firstOrNull { device.hasObject(By.textStartsWith(it)) }

    private fun watchStatusesUntilSynced(timeoutMs: Long): List<String> {
        val seen = mutableListOf<String>()
        val ok = waitUntil(timeoutMs, pollMs = 150) {
            currentStatus()?.let { if (seen.lastOrNull() != it) seen += it }
            device.hasObject(By.text("会员开通成功"))
        }
        if (device.hasObject(By.textStartsWith("支付成功，会员状态同步中"))) seen += "支付成功，会员状态同步中"
        assertTrue("synced within timeout, seen=$seen", ok)
        return seen
    }

    private fun panelSnapshot(): String =
        device.findObjects(By.textContains("")).mapNotNull { it.text }
            .filter { t -> PANEL_KEYS.any { t.startsWith(it) } || STATUS_TEXTS.any { t.startsWith(it) } || t.startsWith("当前为测试") }
            .joinToString(" | ")

    private fun waitPrefsOrderNo(): String {
        var orderNo: String? = null
        assertTrue("order metadata", waitUntil(10_000) { prefs().getString("order_no", null).also { orderNo = it } != null })
        return orderNo!!
    }

    private fun visibleTexts(): List<String> =
        device.findObjects(By.textContains("")).mapNotNull { it.text }.filter { it.isNotBlank() }.take(25)

    private fun prefs() = context.getSharedPreferences("payment_pending_purchase", Context.MODE_PRIVATE)

    private fun isVip() = app.entitlementRepository.state.value.source is EntitlementSource.Vip

    private fun decision(capability: EntitlementCapability): String =
        when (app.entitlementRepository.actionPolicy.evaluate(capability)) {
            is ActionDecision.Allowed -> "Allowed"
            is ActionDecision.Denied -> "Denied"
        }

    private suspend fun verifyAppLockAndAlbum() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
        val baseline = app.appLockRepository.observeLockedPackages().first()
        val pkg = "com.android.chrome"
        val lockedNow = app.appLockRepository.setLocked(pkg, "chrome", true) == AppLockSetResult.Success
        try {
            bringToFront()
            app.appLockProtectionCoordinator.onProcessForeground()
            app.appLockProtectionCoordinator.reconcile()
            val running = waitUntil(20_000) {
                if (!AppLockMonitorService.isRunning) runBlocking { app.appLockProtectionCoordinator.reconcile() }
                AppLockMonitorService.isRunning
            }
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            var overlay = false
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launch)
                overlay = waitUntil(20_000) { AppLockMonitorService.isOverlayShowing() }
            }
            evidence("b04_applock", "locked=$lockedNow serviceRunning=$running overlay=$overlay")
            assertTrue(running && overlay)
        } finally {
            if (pkg !in baseline) app.appLockRepository.setLocked(pkg, "chrome", false)
            device.pressHome()
        }
        val uri = SyntheticMediaFactory.createJpegUri(context, 3541)
        try {
            val album = app.vaultMediaRepository.ensureDefaultAlbum()
            val summary = app.vaultMediaRepository.importMedia(album.id, listOf(uri))
            evidence("b04_album", "importSuccess=${summary.successCount} skipped=${summary.skippedDueToEntitlement}")
            assertEquals(1, summary.successCount)
            app.vaultMediaRepository.deletePrivateMedia(summary.importedMediaIds)
        } finally {
            SyntheticMediaFactory.deleteUri(context, uri)
        }
    }

    /** PC side acknowledges by writing this file through `adb shell` after acting on the marker. */
    private fun waitForMarkerAck(timeoutMs: Long): Boolean {
        val ack = File("/data/local/tmp/cv_i354b_ack")
        val ok = waitUntil(timeoutMs, pollMs = 500) { shell("cat ${ack.path} 2>/dev/null").trim() == "ok" }
        shell("rm -f ${ack.path}")
        return ok
    }

    private fun setAirplane(enabled: Boolean) {
        shell("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
    }

    /** FLAG_SECURE blanks pixel screenshots, so evidence is the accessibility hierarchy. */
    private fun screenshot(name: String) {
        val dir = context.getExternalFilesDir("i354b") ?: return
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }

    private fun purchaseButtonState(): String {
        val node = device.findObject(By.textStartsWith("立即开通"))
            ?: return "missing"
        var current: androidx.test.uiautomator.UiObject2? = node
        var enabled = node.isEnabled
        repeat(3) {
            current = current?.parent
            current?.let { if (it.isClickable) enabled = it.isEnabled }
        }
        return "text=${node.text} enabled=$enabled"
    }

    private fun arg(key: String, default: Int): Int = args.getString(key)?.toIntOrNull() ?: default

    private fun waitUntil(timeoutMs: Long, pollMs: Long = 200, predicate: () -> Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            SystemClock.sleep(pollMs)
        }
        return predicate()
    }

    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { it.readText() }
    }

    private fun marker(name: String, detail: String) {
        Log.i(MARKER_TAG, "$name $detail")
    }

    private fun evidence(step: String, detail: String) {
        Log.i(EVIDENCE_TAG, "$step :: $detail")
    }

    private companion object {
        const val ACCOUNTS_FILE = "/data/local/tmp/cv_i354b_pay_accounts.local.json"
        const val MARKER_TAG = "CV_I354B_MARKER"
        const val OFFLINE_HINT = "网络连接不可用，请检查网络后重试"
        const val QUERY_FAILED_HINT = "暂时无法查询订单，稍后自动重试"
        const val EVIDENCE_TAG = "CV_I354B_EVIDENCE"
        val PIN = charArrayOf('1', '3', '5', '7', '9', '0')
        val STATUS_TEXTS = listOf("待支付", "支付已确认，正在开通会员", "会员已开通", "订单已超时", "订单状态确认中")
        val PANEL_KEYS = listOf("套餐：", "金额：", "订单号：", "剩余支付时间")
    }
}
