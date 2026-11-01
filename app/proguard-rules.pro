-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose and debug logs in production release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve model classes used in serialization and reflection
-keepclassmembers class com.eipna.centsation.data.models.** {
    <fields>;
    <init>(...);
}
