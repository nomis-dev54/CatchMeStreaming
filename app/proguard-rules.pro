# CatchMeStreaming ProGuard Configuration
# Security-focused obfuscation rules for RTSP streaming application

# ===== SECURITY RULES =====

# Aggressive obfuscation - rename all classes, methods, and fields
-repackageclasses 'obfuscated'
-allowaccessmodification
-mergeinterfacesaggressively

# Remove debug information and stack traces to prevent reverse engineering
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debug-related attributes but keep essential ones for crash reports
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# Rename source files to obfuscate original file names
-renamesourcefileattribute SourceFile

# ===== SECURITY-CRITICAL CLASSES =====

# Protect security classes from being removed or renamed incorrectly
-keep class com.example.catchmestreaming.security.** { *; }

# Keep encryption and keystore related classes
-keep class androidx.security.crypto.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# ===== ANDROID FRAMEWORK =====

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep View constructors for XML inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== JETPACK COMPOSE =====

# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Compose @Stable and @Immutable annotated classes
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# Keep Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keep class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ===== CAMERAX =====

# Keep CameraX classes and interfaces
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# Keep camera provider and lifecycle classes
-keep class androidx.camera.lifecycle.ProcessCameraProvider { *; }
-keep class androidx.camera.core.** { *; }

# ===== KOTLIN COROUTINES =====

# Keep coroutines classes
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep suspend functions
-keepclassmembers class * {
    *** *(..., kotlin.coroutines.Continuation);
}

# ===== VIEWMODEL AND REPOSITORY =====

# Keep ViewModels and Repositories for reflection
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.example.catchmestreaming.viewmodel.** { *; }
-keep class com.example.catchmestreaming.repository.** { *; }

# ===== SERIALIZATION =====

# Keep data classes for potential serialization
-keepclassmembers class com.example.catchmestreaming.** {
    *** get*();
    *** set*(***);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== REFLECTION PROTECTION =====

# Obfuscate reflection calls to make reverse engineering harder
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# ===== NETWORK SECURITY =====

# Keep network security config
-keep class android.security.NetworkSecurityPolicy { *; }

# ===== CRASH REPORTING =====

# Keep minimal information for crash reports while obfuscating
-keepattributes SourceFile,LineNumberTable
-keep class com.example.catchmestreaming.** { *; }

# ===== OPTIMIZATION =====

# Enable all optimizations except those that might break functionality
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# ===== ANTI-TAMPERING =====

# Make reverse engineering more difficult
-dontskipnonpubliclibraryclasses
-forceprocessing

# ===== REMOVE UNNECESSARY CODE =====

# Remove unused classes and methods aggressively
-dontwarn **
-ignorewarnings

# Remove kotlin metadata to prevent easy decompilation
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
    static void throwNpe();
    static void throwNpe(java.lang.String);
    static void throwAssert();
    static void throwAssert(java.lang.String);
    static void throwIllegalArgument();
    static void throwIllegalArgument(java.lang.String);
    static void throwIllegalState();
    static void throwIllegalState(java.lang.String);
}