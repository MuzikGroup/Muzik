@file:Suppress("UnstableApiUsage")

val useLocalInnerTubeX = providers.gradleProperty("useLocalInnerTubeX").isPresent
val useMavenLocalInnerTubeX = providers.gradleProperty("useMavenLocalInnerTubeX").isPresent
if (useLocalInnerTubeX && useMavenLocalInnerTubeX) {
    error("-PuseLocalInnerTubeX and -PuseMavenLocalInnerTubeX are mutually exclusive")
}

pluginManagement {
    repositories {
        maven { setUrl("https://repo.huaweicloud.com/repository/maven") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.6.1"
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        publishing.onlyIf { System.getenv("CI") != null }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        if (useMavenLocalInnerTubeX) mavenLocal()
        // JitPack builds its artifacts on demand; a mirror that cached a POM without
        // the binary would pin resolution to itself and fail the build. Keep
        // com.github.* on JitPack.
        maven {
            setUrl("https://repo.huaweicloud.com/repository/maven")
            content { excludeGroupByRegex("com\\.github\\..*") }
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/public")
            content { excludeGroupByRegex("com\\.github\\..*") }
        }
        google()
        mavenCentral()
        maven {
            setUrl("https://jitpack.io")
            content {
                includeGroup("com.github.nyxiereal.glyphmind")
                includeGroup("com.github.MuzikGroup.innertubex")
                includeGroup("com.github.MuzikGroup.quackjs")
            }
        }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven("https://jogamp.org/deployment/maven")
        maven {
            name = "FDD Maven"
            url = uri("https://maven.firstdark.dev/releases")
        }
    }
}

rootProject.name = "Muzik"
if (useLocalInnerTubeX) {
    includeBuild("../innertubex") {
        dependencySubstitution {
            substitute(module("com.github.MuzikGroup.innertubex:innertubex")).using(project(":"))
        }
    }
}
include(":shared")
include(":listenTogetherProto")
include(":desktopApp")
