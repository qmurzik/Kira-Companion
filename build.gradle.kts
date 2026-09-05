// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    // Kotlin must be at least as new as whatever SceneView/Filament (see app/build.gradle.kts)
    // was itself compiled with (2.4.10, per its own gradle/libs.versions.toml) - Kotlin only
    // guarantees a compiler can read metadata from *older* Kotlin versions, not newer ones,
    // and using an older compiler against newer metadata is what caused CI's "source must
    // not be null" FIR compiler crash when this was still on 2.0.21.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
