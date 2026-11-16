-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose and debug logs in production release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Optimize R8 - enable aggressive optimizations
-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# Remove unused resources
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.glance.** { *; }

# Preserve model classes used in serialization and reflection
-keepclassmembers class com.eipna.centsation.data.models.** {
    <fields>;
    <init>(...);
}

# Keep Room database classes
-keep class com.eipna.centsation.habit.data.** { *; }

# Keep widget receivers and callbacks
-keep class com.eipna.centsation.habit.widget.** { *; }

# Keep Parcelable and Serializable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum values for settings
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove unused Material3 components
-dontwarn androidx.compose.material3.**

# Optimize Kotlin metadata
-keepclassmembers class * {
    @kotlin.Metadata *;
}

# Preserve annotation processing
-keepattributes *Annotation*
