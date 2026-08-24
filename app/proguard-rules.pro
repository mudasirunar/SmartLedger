# =====================================================================
# ProGuard & R8 Optimization Rules - SmartLedger Release Build
# =====================================================================

# ---------------------------------------------------------------------
# Keep Essential Attributes for Reflection & Serialization
# ---------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ---------------------------------------------------------------------
# AI Models & Groq API Service (DO NOT OBFUSCATE OR REMOVE)
# ---------------------------------------------------------------------
-keep class com.example.smartledger.util.AiHelper** { *; }
-keep class com.example.smartledger.util.GroqRequest { *; }
-keep class com.example.smartledger.util.GroqResponse { *; }
-keep class com.example.smartledger.util.Choice { *; }
-keep class com.example.smartledger.util.AiMessage { *; }
-keep interface com.example.smartledger.util.AiHelper$GroqApiService { *; }

# ---------------------------------------------------------------------
# App Data Models, Room Entities & DAOs
# ---------------------------------------------------------------------
-keep class com.example.smartledger.data.** { *; }
-keepclassmembers class com.example.smartledger.data.** { *; }

-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
}
-dontwarn androidx.room.**

# ---------------------------------------------------------------------
# Inner Data Classes, Adapters & Activity Serializables
# ---------------------------------------------------------------------
-keep class com.example.smartledger.activity.CreateCustomLedgerActivity$FieldPricing { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------------------------------------------------------------------
# Gson Serialization
# ---------------------------------------------------------------------
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }
-dontwarn com.google.gson.**

# ---------------------------------------------------------------------
# Retrofit 2 & OkHttp
# ---------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers @interface * { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------------------------------------------------------------------
# WorkManager & Concurrent Futures / Guava ListenableFuture
# ---------------------------------------------------------------------
-keep class androidx.work.** { *; }
-keepclassmembers class androidx.work.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keepclassmembers class androidx.concurrent.futures.** { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-dontwarn androidx.work.**
-dontwarn androidx.concurrent.futures.**
-dontwarn com.google.common.util.concurrent.**

# ---------------------------------------------------------------------
# Third Party Libraries (Glide, PhotoView, MPAndroidChart)
# ---------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule { *; }
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-dontwarn com.bumptech.glide.**

-keep class com.github.chrisbanes.photoview.** { *; }
-keep class com.github.PhilJay.MPAndroidChart.** { *; }

# ---------------------------------------------------------------------
# BuildConfig
# ---------------------------------------------------------------------
-keep class com.example.smartledger.BuildConfig { *; }