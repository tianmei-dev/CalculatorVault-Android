import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val umengAppKey = providers.gradleProperty("UMENG_APP_KEY").orElse("")
val umengChannel = providers.gradleProperty("UMENG_CHANNEL").orElse("official")
val buglyAppId = providers.gradleProperty("BUGLY_APP_ID").orElse("")
val buglyChannel = providers.gradleProperty("BUGLY_CHANNEL").orElse(umengChannel)
val wechatAppId = providers.gradleProperty("WECHAT_APP_ID")
    .orElse(providers.environmentVariable("WECHAT_APP_ID")).orElse("").get().trim()
require(wechatAppId.isEmpty() || wechatAppId.matches(Regex("wx[0-9a-fA-F]{16}"))) {
    "WECHAT_APP_ID must be empty or a valid WeChat mobile AppID (wx + 16 hexadecimal characters)"
}

/**
 * I3.4.3-D2-A: dedicated Debug builds for isolated membership Session injection.
 * `-PISOLATED_MEMBERSHIP_TEST=true` defaults API hosts to emulator → host :8081 (s32it),
 * enables [BuildConfig.TEST_SESSION_INSTALL_ENABLED], and does not change Release / 8080 defaults.
 */
val isolatedMembershipTest =
    providers.gradleProperty("ISOLATED_MEMBERSHIP_TEST").orElse("false").get() == "true"
val debugApiUrlDefault = if (isolatedMembershipTest) "http://10.0.2.2:8081" else "http://10.0.2.2:8080"
val debugApiUrl = providers.gradleProperty("DEBUG_API_BASE_URL").orElse(debugApiUrlDefault).get()
val releaseApiUrl = providers.gradleProperty("RELEASE_API_BASE_URL").orElse("").get()
fun quotedUrl(value: String): String {
    require(value.none { it == '"' || it == '\\' || it.isISOControl() })
    return "\"$value\""
}
/** Public HTTPS API host only: no http, IP, userinfo or local / reserved names. */
fun requirePublicHttpsUrl(name: String, value: String) {
    if (value.isEmpty()) return
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "$name must be an https URL" }
    val host = uri.host.lowercase()
    require(host.contains('.') && !host.matches(Regex("[0-9.]+")) && host.none { it == ':' || it == '[' || it == ']' }) {
        "$name must use a DNS host name"
    }
    require(listOf("localhost", "local", "lan", "internal", "test", "invalid").none {
        host == it || host.endsWith(".$it")
    }) { "$name must not use a local or reserved host" }
}
requirePublicHttpsUrl("RELEASE_API_BASE_URL", releaseApiUrl)

/**
 * I3.5.5-B2 paystaging: real-gateway joint-test build. HTTPS API injected by
 * `-PPAYSTAGING_API_BASE_URL=https://...`; when absent the build still assembles but the app
 * refuses all payment / account requests (no fallback to any development address).
 */
val paystagingApiUrl = providers.gradleProperty("PAYSTAGING_API_BASE_URL").orElse("").get().trim()
requirePublicHttpsUrl("PAYSTAGING_API_BASE_URL", paystagingApiUrl)

/**
 * Yusen cashier host allow-list per build type, comma separated, exact lowercase host names.
 * Empty (the default everywhere) refuses every cashier link; real hosts are confirmed manually.
 */
fun cashierHosts(property: String): String {
    val hosts = providers.gradleProperty(property).orElse("").get().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    hosts.forEach { host ->
        val labels = host.split('.')
        require(labels.size >= 2 && labels.all { label.matches(it) } && labels.last().any { it in 'a'..'z' }) {
            "$property: cashier hosts must be lowercase DNS names without scheme, port or path"
        }
    }
    return quotedUrl(hosts.joinToString(","))
}

/**
 * Joint-test signing, supplied only from outside the repository:
 * `PAYSTAGING_STORE_FILE` / `PAYSTAGING_KEY_ALIAS` (Gradle property or environment) and
 * `PAYSTAGING_STORE_PASSWORD` / `PAYSTAGING_KEY_PASSWORD` (environment only). Without them the
 * paystaging APK is left unsigned; partial configuration fails the build.
 */
val paystagingStoreFile = providers.gradleProperty("PAYSTAGING_STORE_FILE")
    .orElse(providers.environmentVariable("PAYSTAGING_STORE_FILE")).orElse("").get().trim()
val paystagingKeyAlias = providers.gradleProperty("PAYSTAGING_KEY_ALIAS")
    .orElse(providers.environmentVariable("PAYSTAGING_KEY_ALIAS")).orElse("").get().trim()
val paystagingStorePassword = providers.environmentVariable("PAYSTAGING_STORE_PASSWORD").orElse("").get()
val paystagingKeyPassword = providers.environmentVariable("PAYSTAGING_KEY_PASSWORD").orElse("").get()
val paystagingSigningParts = listOf(paystagingStoreFile, paystagingKeyAlias, paystagingStorePassword, paystagingKeyPassword)
val paystagingSigned = paystagingSigningParts.all { it.isNotEmpty() }
require(paystagingSigned || paystagingSigningParts.none { it.isNotEmpty() }) {
    "paystaging signing needs PAYSTAGING_STORE_FILE, PAYSTAGING_KEY_ALIAS, PAYSTAGING_STORE_PASSWORD and PAYSTAGING_KEY_PASSWORD"
}
if (paystagingSigned) {
    val store = file(paystagingStoreFile).canonicalFile
    require(store.isFile) { "PAYSTAGING_STORE_FILE does not exist" }
    require(store.name != "debug.keystore") { "paystaging must not use the debug keystore" }
    require(!store.path.startsWith(rootDir.canonicalPath)) { "paystaging keystore must live outside the repository" }
}
android {
    namespace = "com.aurora.calculatorvault"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aurora.calculatorvault"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UMENG_APP_KEY", "\"${umengAppKey.get()}\"")
        buildConfigField("String", "UMENG_CHANNEL", "\"${umengChannel.get()}\"")
        buildConfigField("String", "BUGLY_APP_ID", "\"${buglyAppId.get()}\"")
        buildConfigField("String", "BUGLY_CHANNEL", "\"${buglyChannel.get()}\"")
        buildConfigField("String", "TRIAL_HTTP_BASE_URL", "\"\"")
        buildConfigField("String", "TRIAL_API_BASE_URL", "\"\"")
        buildConfigField("String", "LOCAL_HTTP_BASE_URL", "\"\"")
        buildConfigField("String", "WECHAT_APP_ID", "\"$wechatAppId\"")
        buildConfigField("String", "PAYMENT_CASHIER_HOSTS", "\"\"")
    }

    signingConfigs {
        if (paystagingSigned) {
            create("paystaging") {
                storeFile = file(paystagingStoreFile)
                keyAlias = paystagingKeyAlias
                storePassword = paystagingStorePassword
                keyPassword = paystagingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TRIAL_DEVICE_UPLOAD_ENABLED", providers.gradleProperty("TRIAL_DEVICE_UPLOAD_ENABLED").orElse("false").get().toBoolean().toString())
            buildConfigField("String", "TRIAL_API_BASE_URL", quotedUrl(debugApiUrl))
            buildConfigField("String", "TRIAL_HTTP_BASE_URL", quotedUrl(debugApiUrl))
            buildConfigField("String", "API_BASE_URL", quotedUrl(debugApiUrl))
            // Align cleartext local allow-list with the active Debug API host (8080 or isolated 8081).
            buildConfigField("String", "LOCAL_HTTP_BASE_URL", quotedUrl(debugApiUrl))
            buildConfigField("boolean", "TEST_SESSION_INSTALL_ENABLED", isolatedMembershipTest.toString())
            // pay.kind=TEST (s32it isolated payments) is recognised only in Debug / localIntegration.
            buildConfigField("boolean", "PAYMENT_TEST_KIND_ALLOWED", "true")
            buildConfigField("String", "PAYMENT_CASHIER_HOSTS", cashierHosts("DEBUG_PAYMENT_CASHIER_HOSTS"))
            manifestPlaceholders["accountCleartext"] = "true"
        }
        release {
            buildConfigField("boolean", "TRIAL_DEVICE_UPLOAD_ENABLED", "false")
            buildConfigField("String", "TRIAL_API_BASE_URL", quotedUrl(releaseApiUrl))
            buildConfigField("String", "API_BASE_URL", quotedUrl(releaseApiUrl))
            buildConfigField("boolean", "TEST_SESSION_INSTALL_ENABLED", "false")
            buildConfigField("boolean", "PAYMENT_TEST_KIND_ALLOWED", "false")
            buildConfigField("String", "PAYMENT_CASHIER_HOSTS", cashierHosts("RELEASE_PAYMENT_CASHIER_HOSTS"))
            manifestPlaceholders["accountCleartext"] = "false"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("localIntegration") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            // Deliberately unsigned: use the existing certificate in Android Studio's signing wizard.
            signingConfig = null
            isDebuggable = false
            versionNameSuffix = "-local-integration"
            val trialOptIn = providers.gradleProperty("LOCAL_TRIAL_UPLOAD_ENABLED").orElse("false").get()
            require(trialOptIn in listOf("true", "false"))
            buildConfigField("String", "TRIAL_API_BASE_URL", quotedUrl("http://192.168.0.107:8081"))
            buildConfigField("String", "TRIAL_HTTP_BASE_URL", quotedUrl("http://192.168.0.107:8081"))
            buildConfigField("String", "API_BASE_URL", quotedUrl("http://192.168.0.107:8080"))
            buildConfigField("String", "LOCAL_HTTP_BASE_URL", quotedUrl("http://192.168.0.107:8080"))
            buildConfigField("boolean", "TRIAL_DEVICE_UPLOAD_ENABLED", trialOptIn)
            buildConfigField("boolean", "TEST_SESSION_INSTALL_ENABLED", "false")
            buildConfigField("boolean", "PAYMENT_TEST_KIND_ALLOWED", "true")
            buildConfigField("String", "PAYMENT_CASHIER_HOSTS", cashierHosts("LOCAL_PAYMENT_CASHIER_HOSTS"))
        }
        create("paystaging") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            // Same applicationId as Release: WeChat login is bound to the registered package name.
            signingConfig = if (paystagingSigned) signingConfigs.getByName("paystaging") else null
            isDebuggable = false
            versionNameSuffix = "-paystaging"
            buildConfigField("String", "API_BASE_URL", quotedUrl(paystagingApiUrl))
            buildConfigField("String", "TRIAL_API_BASE_URL", quotedUrl(paystagingApiUrl))
            buildConfigField("String", "TRIAL_HTTP_BASE_URL", "\"\"")
            buildConfigField("String", "LOCAL_HTTP_BASE_URL", "\"\"")
            buildConfigField("boolean", "TRIAL_DEVICE_UPLOAD_ENABLED", "false")
            buildConfigField("boolean", "TEST_SESSION_INSTALL_ENABLED", "false")
            buildConfigField("boolean", "PAYMENT_TEST_KIND_ALLOWED", "false")
            buildConfigField("String", "PAYMENT_CASHIER_HOSTS", cashierHosts("PAYSTAGING_PAYMENT_CASHIER_HOSTS"))
            manifestPlaceholders["accountCleartext"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Legacy Bugly has no 16 KB-aligned x86_64 native crash library.
            // Keep x86_64 emulator support for Java/ANR and retain native crash on ARM.
            excludes += "lib/x86_64/libBugly_Native.so"
        }
    }
}

dependencies {
    implementation(libs.wechat.opensdk)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.umeng.common)
    implementation(libs.umeng.asms)
    implementation(libs.bugly.crashreport)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
