pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The Aliyun Central mirror can expose Bugly metadata before its AAR is
        // mirrored. Resolve Tencent Bugly artifacts from their source of truth.
        exclusiveContent {
            forRepository { mavenCentral() }
            filter {
                includeGroup("com.tencent.bugly")
            }
        }
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "CalculatorVault"
include(":app")
