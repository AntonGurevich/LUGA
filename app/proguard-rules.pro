# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =============================================================================
# ACTEAMITY APP - PROGUARD RULES FOR MINIFICATION
# =============================================================================

# Keep all project classes to prevent issues with reflection and serialization
-keep class silverbackgarden.example.luga.** { *; }

# =============================================================================
# SUPABASE RULES
# =============================================================================
# Keep Supabase classes
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Keep Supabase serialization
-keep @kotlinx.serialization.Serializable class * {
    *;
}

# =============================================================================
# FIREBASE RULES
# =============================================================================
# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }

# Firebase Storage
-keep class com.google.firebase.storage.** { *; }

# =============================================================================
# RETROFIT RULES
# =============================================================================
# Keep Retrofit classes
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Retrofit interfaces
-keep interface retrofit2.** { *; }

# =============================================================================
# KOTLINX SERIALIZATION RULES
# =============================================================================
# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep serializable data classes
-keep @kotlinx.serialization.Serializable class * {
    *;
}

# =============================================================================
# ANDROIDX RULES
# =============================================================================
# Keep AndroidX classes
-keep class androidx.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Lifecycle components
-keep class androidx.lifecycle.** { *; }

# =============================================================================
# DATA CLASSES AND MODELS
# =============================================================================
# Keep all data classes used for API communication
-keep class silverbackgarden.example.luga.UserData { *; }
-keep class silverbackgarden.example.luga.UserExistsResponse { *; }
-keep class silverbackgarden.example.luga.TokenRecord { *; }
-keep class silverbackgarden.example.luga.EmployerCodeValidationResult { *; }

# =============================================================================
# ENUM RULES
# =============================================================================
# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =============================================================================
# REFLECTION RULES
# =============================================================================
# Keep classes that might be accessed via reflection
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# =============================================================================
# DEBUGGING RULES
# =============================================================================
# Preserve line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# =============================================================================
# THIRD-PARTY LIBRARIES
# =============================================================================
# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Keep CircularProgressBar
-keep class com.mikhaellopez.circularprogressbar.** { *; }

# Keep MySQL Connector (if used)
-keep class com.mysql.** { *; }

# =============================================================================
# GENERAL RULES
# =============================================================================
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile