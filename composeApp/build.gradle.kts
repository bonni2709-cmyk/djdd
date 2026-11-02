import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("org.bouncycastle:bcprov-jdk18on:1.80")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.bouncycastle:bcprov-jdk18on:1.80")
            }
        }
    }
}

android {
    namespace = "app.veil.messenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.veil.messenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Bouncy Castle is a multi-release JAR; these desktop-only metadata files
    // must not be merged into the Android APK resources.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.veil.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Veil"
            packageVersion = "0.1.0"
            description = "Private conversations, designed for you"
            vendor = "Veil"
            windows {
                menuGroup = "Veil"
                upgradeUuid = "5be2e9a4-ade9-4fa4-9d29-2b8ed014d47c"
                perUserInstall = true
            }
        }
    }
}
