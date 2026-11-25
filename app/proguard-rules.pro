# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve stack trace metadata
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <methods>; }
-keep class com.example.data.entity.** { *; }
-dontwarn androidx.room.paging.**

# Data/model classes used across reflection/serialization boundaries
-keep class com.example.data.remote.SupabaseService$AppUpdatePolicy { *; }
-keep class com.example.data.remote.SupabaseService$DeepgramConfig { *; }
-keep class com.example.data.remote.SupabaseService$AccessPolicyConfig { *; }
-keep class com.example.data.remote.SupabaseService$DeviceRegistration { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Microsoft ONNX Runtime (Silero VAD JNI)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ZXing QR code generator
-keep class com.google.zxing.** { *; }

# Firebase (Firestore, Auth, AppCheck)
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Coil image loading
-dontwarn coil.**
