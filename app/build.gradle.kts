import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val muzikVersion = providers.gradleProperty("muzik.version").orElse("7.8.4").get()
val muzikVersionCode = providers.gradleProperty("muzik.versionCode").orElse("70804").get().toInt()
val muzikJavaFxVersion = (libs.versions.javafx.get())

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.desktop)
}

group = "com.muzikgroup"
version = muzikVersion

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    // JavaFX media engine: native AAC/M4A decode + progressive HTTP playback.
    // Native libraries for every supported platform ride on the classpath so a
    // single build serves Windows, macOS (Intel + Apple Silicon) and Linux (x64 + ARM64).
    listOf("win", "mac", "mac-aarch64", "linux", "linux-aarch64").forEach { platform ->
        listOf("base", "graphics", "media").forEach { module ->
            implementation("org.openjfx:javafx-$module:$muzikJavaFxVersion:$platform")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.muzik.desktop.MainKt"

        val iconsDir = project.file("icons")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Muzik"
            packageVersion = muzikVersion
            vendor = "MuzikGroup"
            description = "Muzik — a premium YouTube Music client for desktop"

            windows {
                iconFile.set(iconsDir.resolve("Muzik.ico"))
                menu = true
                dirChooser = false
                perUserInstall = true
            }
            macOS {
                iconFile.set(iconsDir.resolve("Muzik.icns"))
                bundleID = "com.muzikgroup.desktop"
                dockName = "Muzik"
            }
            linux {
                iconFile.set(iconsDir.resolve("muzik.png"))
                menuGroup = "Multimedia"
                debMaintainer = "motius@users.noreply.github.com"
                rpmLicenseType = "GPL-3.0"
            }
        }
    }
}

tasks.processResources {
    val v = providers.gradleProperty("muzik.version").orElse("7.8.4")
    val vc = providers.gradleProperty("muzik.versionCode").orElse("70804")
    filesMatching("version.properties") {
        expand("version" to v.get(), "versionCode" to vc.get())
    }
}
