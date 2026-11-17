package com.muzik.desktop.util

import java.util.Properties

/** Version injected at build time from gradle.properties via processResources. */
object BuildConfigVersion {
    val VERSION: String by lazy {
        val props = Properties()
        BuildConfigVersion::class.java.classLoader
            .getResourceAsStream("version.properties")
            ?.use { props.load(it) }
        props.getProperty("version") ?: "7.8.4"
    }

    val VERSION_CODE: Int by lazy {
        val props = Properties()
        BuildConfigVersion::class.java.classLoader
            .getResourceAsStream("version.properties")
            ?.use { props.load(it) }
        props.getProperty("versionCode")?.toIntOrNull() ?: 70804
    }
}
