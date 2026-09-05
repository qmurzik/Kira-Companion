# Kira Companion release ProGuard/R8 rules.

# Keep OkHttp/Okio internals that use reflection.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

# Keep our model classes (parsed to/from JSON manually) so field names survive.
-keep class com.kira.companion.model.** { *; }
-keep class com.kira.companion.data.** { *; }
-keep class com.kira.companion.vrm.** { *; }

# Filament/gltfio/SceneView bridge native code via JNI; keep everything so R8 doesn't
# strip classes/methods only referenced from native code.
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.android.filament.**
-dontwarn io.github.sceneview.**
