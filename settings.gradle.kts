pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // Automatic JDK toolchain provisioning so contributors and CI always get Java 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // InnerTubeX and QuackJS publish through JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.MetrolistGroup")
                includeGroup("com.github.MetrolistGroup.innertubex")
                includeGroup("com.github.MetrolistGroup.quackjs")
            }
        }
    }
}

rootProject.name = "MuzikDesktop"
include(":app")
