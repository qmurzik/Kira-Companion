# Kira Companion release ProGuard/R8 rules.

# Keep OkHttp/Okio internals that use reflection.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

# Keep our model classes (parsed to/from JSON manually) so field names survive.
-keep class com.kira.companion.model.** { *; }
-keep class com.kira.companion.data.** { *; }
