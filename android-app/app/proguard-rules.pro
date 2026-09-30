# PC Master ProGuard Rules

# Keep all public classes and their members
-keep public class * {
    public *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable creators
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep annotation processor generated classes
-keep class **.$$ViewBinder { *; }
-keep class **.$$ViewInjector { *; }

# Keep Retrofit & Gson
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }

# Keep Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Keep Room
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* *;
}

# Keep Glide
-dontwarn com.bumptech.glide.**
-keep class com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep DataStore
-dontwarn androidx.datastore.**
-keep class androidx.datastore.** { *; }

# Keep WorkManager
-dontwarn androidx.work.**
-keep class androidx.work.** { *; }

# Keep Navigation
-dontwarn androidx.navigation.**
-keep class androidx.navigation.** { *; }

# Keep Lifecycle
-dontwarn androidx.lifecycle.**
-keep class androidx.lifecycle.** { *; }

# Keep Material
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

# Keep ViewBinding
-dontwarn ***.databinding.**
-keep class ***.databinding.** { *; }

# Keep our model classes
-keep class com.pcmaster.mobile.** { *; }

# Keep resource classes
-keep class com.pcmaster.mobile.R$* { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}