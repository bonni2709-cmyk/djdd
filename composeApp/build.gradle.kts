import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
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
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
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
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            // Both JSpecify and Bouncy Castle ship this OSGi metadata; it is not needed at runtime.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.serein.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Deb)
            packageName = "serein"
            packageVersion = "0.1.0"
            description = "Serein identity prototype"
            vendor = "Serein"
        }
    }
}
