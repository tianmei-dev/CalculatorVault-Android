plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val umengAppKey = providers.gradleProperty("UMENG_APP_KEY").orElse("")
val umengChannel = providers.gradleProperty("UMENG_CHANNEL").orElse("official")
val buglyAppId = providers.gradleProperty("BUGLY_APP_ID").orElse("")
val buglyChannel = providers.gradleProperty("BUGLY_CHANNEL").orElse(umengChannel)

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
