pluginManagement {
    repositories {
        // Primary: Aliyun mirrors (for China/Iran)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // Secondary: Huawei mirror (alternative)
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }

        // Tertiary: Tencent mirror
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }

        // Standard repositories (VPN required in restricted regions)
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Aliyun mirrors
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }

        // Huawei mirror
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }

        // Tencent mirror
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }

        // Standard
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LifeOs"
include(":app")