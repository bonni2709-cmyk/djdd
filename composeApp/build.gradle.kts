import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    jvm("desktop") {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("com.goterl:lazysodium-android:5.2.0")
            implementation("net.java.dev.jna:jna:5.17.0@aar")
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.journeyapps:zxing-android-embedded:4.3.0")
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.goterl:lazysodium-java:5.2.0")
                implementation("net.java.dev.jna:jna:5.17.0")
                implementation("com.google.zxing:core:3.5.3")
            }
        }
    }
}

android {
    namespace = "app.serein"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.serein"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.serein.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "serein"
            // macOS installers require a major version >= 1; the app itself remains a 0.4 prototype.
            packageVersion = "1.0.0"
            description = "Serein local-first prototype"
            vendor = "Serein"
        }
    }
}
