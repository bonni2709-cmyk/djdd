plugins {
    // KMP + Compose Multiplatform versions are kept aligned for reproducible CI builds.
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.compose") version "1.8.0" apply false
    id("com.android.application") version "8.9.1" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
