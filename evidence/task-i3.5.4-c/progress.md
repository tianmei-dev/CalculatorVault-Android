# I3.5.4-C progress (checkpoints)

## CP1 — code changes done (2026-11-10)
- F5: `FakeMembershipProvider` moved main → `app/src/test/.../entitlement/domain/FakeMembershipProvider.kt` (same package). main has no Fake class.
- F3: `HttpPaymentApi` order-query-only timeouts 5s/8s; `PaymentRepository.networkAvailable` + `onNetworkLost()`; App default-network callback reports `onLost` + initial no-network;
  `VipPurchaseController` in-flight guard (refresh / network restore / resume reuse running query), offline skip with periodic probe, `OrderQueryIssue` (NetworkUnavailable / Temporary).
- F4: `closeOrderView()` on EXPIRED → `discardOrderRecord(userId, orderNo)`; button "结束查看此订单"; PENDING keeps record.
- Next: JVM tests (F3/F4/F5), builds, Release DEX scan, androidTest text update, report.

## CP2 — targeted JVM (100/100) → `jvm-targeted-1.log`
## CP3 — full JVM 559 / 0 fail / 4 skipped → `jvm-full.log`, `jvm-full-summary.txt`
## CP4 — builds all exit 0 → `build-summary.txt`, `build-*.log`
## CP5 — Release DEX scan 0 hits (17 symbols + 37 secrets), control scan on isolated Debug finds expected hits,
##        Release BuildConfig via dexdump → `release-dex-scan.txt`, `control-debug-isolated-dex-scan.txt`, `release-buildconfig-dexdump.txt`

| Case | Status | Precondition | Result | Evidence |
|---|---|---|---|---|
| C-F3-01 offline immediate hint, no HTTP while offline, status kept, restore re-queries | PASS(J) | PENDING order | NetworkUnavailable at t=0; 0 HTTP in 15s; PAID after restore | VipPurchaseControllerTest.f3_networkLost_* |
| C-F3-02 stale offline signal still probes | PASS(J) | offline never restored | real probe sent within 60s | f3_staleOfflineSignal_* |
| C-F3-03 repeated refresh / resume / restore while in flight | PASS(J) | gated query | HTTP count unchanged, result applied once | f3_repeatedRefresh_* |
| C-F3-04 timeout online → Temporary + backoff, status kept | PASS(J) | Network(timeout) | 2–4 calls/20s, PENDING kept | f3_timeoutWhileOnline_* |
| C-F3-05 fg/bg toggles → single poll loop | PASS(J) | 4 toggles | 4–5 queries/10s | f3_foregroundToggles_* |
| C-F3-06 account switch during in-flight query | PASS(J) | gated FULFILLED | Idle, no membership refresh | f3_accountSwitch_* |
| C-F3-07 only order query uses 5s/8s | PASS(J) | recording transport | plans/create 10/15, query 5/8 | HttpPaymentApiTest.f3_* |
| C-F3-08 network signal lost/restored | PASS(J) | — | restore emits | PaymentRepositoryTest.f3_* |
| C-F4-01 EXPIRED manual refresh, end viewing clears record, reopen shows plans | PASS(J) | EXPIRED | record null, Idle+plans, no resume query | f4_expired_* |
| C-F4-02 PENDING back keeps record and resumes | PASS(J) | PENDING | resumed, no new order | f4_pendingBackToPlans_* |
| C-F4-03 end viewing only clears own order | PASS(J) | other record | CV-OTHER kept | f4_endViewing_onlyClearsOwnOrderRecord, PaymentRepositoryTest.f4_* |
| C-F4-04 late payment after end viewing → VIP via /me | PASS(J) | real chain | VIP; reopen Idle | PurchaseMembershipRaceTest.f4_* |
| C-F5-01 Release DEX has no Fake/test bridge/fixture/8081/tokens | PASS(S) | release APK | 0 hits | release-dex-scan.txt |
| C-F5-02 Release BuildConfig flags | PASS(S) | dexdump | PAYMENT_TEST_KIND_ALLOWED=false, TEST_SESSION_INSTALL_ENABLED=false | release-buildconfig-dexdump.txt |
| Emulator runtime | NOT RUN | — | JVM/static/build prioritized per brief | — |
