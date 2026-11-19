@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        maven {
            setUrl("https://repo.huaweicloud.com/repository/maven/")
            // JitPack builds its artifacts on demand; a mirror that has cached a module's POM
            // without the binary would pin resolution to itself and fail the build
            // (observed with com.github.yalantis:ucrop on CI). Keep com.github.* on JitPack.
            content { excludeGroupByRegex("com\\.github\\..*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        google()
        maven { setUrl("https://repo.huaweicloud.com/repository/maven/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven {
            setUrl("https://maven.aliyun.com/repository/public")
            content { excludeGroupByRegex("com\\.github\\..*") }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "Muzik"
include(":app")
include(":innertube")
include(":kugou")
include(":lrclib")

include(":lastfm")
include(":betterlyrics")
include(":shazamkit")
include(":paxsenix")

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that Muzik and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
