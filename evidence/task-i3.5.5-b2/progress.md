# I3.5.5-B2 progress (checkpoints)

Pre-check: `git-status-before.txt` — 16 uncommitted I3.5.4-C entries kept; FakeMembershipProvider only in `app/src/test`.

## CP1 — code done
- B2-01 `PayLaunchPolicy(allowTestKind, cashierHosts)`; BuildConfig `PAYMENT_CASHIER_HOSTS` per build type (all empty by default).
- B2-02 `paystaging` build type + `src/paystaging` (no-cleartext NSC, silent traces); external signing; `EndpointNotConfigured`.
- B2-03 openCashier debounce 2s; logout → `discardPending(userId)`; `installServerIssuedSession` gated by `serverIssuedInstallAllowed`.

## CP2 — targeted JVM 119/119 → `jvm-targeted-1.log`
## CP3 — full Debug JVM 575 / 0 fail / 4 skipped; paystaging BuildConfig 3/3; Release BuildConfig 2/2 → `jvm-full-summary.txt`
## CP4 — build-script validation 7 invalid configs refused, valid accepted → `build-config-validation.txt`;
##        builds all exit 0 → `build-summary.txt`
## CP5 — APK scan (7 APKs) → `apk-scan.txt`; BuildConfig/manifest/signing → `buildconfig-manifest.txt`;
##        compiled network security → `network-security.txt`. Release + paystaging: 0 hits in every group.

| Case | Precondition | Status | Result | Evidence |
|---|---|---|---|---|
| B2-T01 URL parsing / allowed https hosts | hosts=cashier.example,pay2.cashier.example | PASS(J) | OpenCashier for 6 valid forms | PaymentPoliciesTest.httpsUrl_*, urlKindParsing_* |
| B2-T02 non-allowlisted / subdomain / prefix hosts | same | PASS(J) | HostNotAllowed | PaymentPoliciesTest.nonAllowlistedHosts_rejected |
| B2-T03 case / trailing dot / port / userinfo bypass | same | PASS(J) | Rejected | caseTrailingDotPortAndUserinfo_cannotBypass |
| B2-T04 http / malicious / malformed | same | PASS(J) | Rejected (20 forms) | maliciousOrMalformedUrls_rejected, httpRejected_* |
| B2-T05 empty allow-list | hosts=[] / invalid only | PASS(J) | CashierHostsNotConfigured | emptyAllowList_refusesEveryLink |
| B2-T06 TEST kind (release policy) | allowTestKind=false | PASS(J) | TestKindNotAllowed | release_rejectsTest_* |
| B2-T07 go-pay rapid taps | PENDING + URL | PASS(J) | 1 browser launch per 2s window | VipPurchaseControllerTest.b2_goPay_* |
| B2-T08 browser return → query | paused 45s | PASS(J) | +1 query on resume | b2_returnFromBrowser_* |
| B2-T09 off-list / unconfigured link in controller | 4 cases | PASS(J) | no OpenCashier, PayLinkInvalid | b2_cashierLink_* |
| B2-T10 endpoint not configured | plans EndpointNotConfigured | PASS(J) | no create sent | b2_endpointNotConfigured_*, HttpPaymentApiTest.releaseRules_* |
| B2-T11 account switch isolation | A PENDING → B | PASS(J) | B Idle, A resumes | b2_accountSwitch_* |
| B2-T12 logout clears own record (real session repo) | A PENDING | PASS(J) | record null, re-login Idle | PurchaseMembershipRaceTest.b2_logout_* |
| B2-T13 FULFILLED w/o server VIP | /me not entitled | PASS(J) | no VIP, sync Failed; VIP only after /me active | b2_fulfilledWithoutServerMembership_* |
| B2-T14 install refused unless allowed; logout callback | — | PASS(J) | IllegalState; callback once | InstallServerIssuedSessionTest.b2_* |
| Process restore (existing) | stored record | PASS(J) | resumes, no create | processRestore_* |
| FULFILLED → VIP via /me (existing B05 + F4) | real chain | PASS(J) | VIP | PurchaseMembershipRaceTest |
