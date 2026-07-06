# ---- PolyAlerts release (R8) keep rules ----

# Keep line numbers for readable crash stack traces, hide source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ----
# R8 full mode strips generated serializers unless we keep them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
# Keep the runtime + generated serializer companions.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep every @Serializable class and its synthetic Companion/serializer.
-keep,includedescriptorclasses class com.polyalerts.**$$serializer { *; }
-keepclassmembers class com.polyalerts.** {
    *** Companion;
}
-keepclasseswithmembers class com.polyalerts.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Our serializable data models (API DTOs + backup format).
-keep @kotlinx.serialization.Serializable class com.polyalerts.** { *; }

# ---- Retrofit / OkHttp ----
-keepattributes Signature, Exceptions
# Retrofit does reflection on generic service interfaces and method return types.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep interface com.polyalerts.data.api.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class com.polyalerts.** { *; }
-dontwarn androidx.room.paging.**

# ---- Coil ----
-dontwarn coil.**

# ---- Kotlin metadata / coroutines ----
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
