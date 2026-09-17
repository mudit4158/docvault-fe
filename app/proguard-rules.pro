# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/android-sdk/tools/proguard/proguard-android.txt

# --- Retrofit / OkHttp ---
# Retrofit builds its API implementation via reflection over interface
# methods and their generic return types; R8 must not touch signatures or
# strip the interface methods themselves.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**

# --- kotlinx.serialization ---
# @Serializable classes are (de)serialized by a generated `$serializer`
# companion that reflection/the plugin wires up at compile time; if R8
# renames or strips the annotated class or its serializer, decoding a
# server response throws at runtime instead of failing to compile.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.docvault.app.**$$serializer { *; }
-keepclassmembers class com.docvault.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.docvault.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# The DTOs themselves — every wire model in data/net/Dtos.kt.
-keep,allowshrinking,allowoptimization @kotlinx.serialization.Serializable class com.docvault.app.data.net.** { *; }

# --- Firebase Auth / Play Services ---
# Firebase's callback classes and Play Services' Task machinery are invoked
# from Google's own compiled code via reflection; R8 has no call-site to see.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
