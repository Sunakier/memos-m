# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Gson / Retrofit reflection -------------------------------------------
# Gson (de)serializes model fields reflectively; generic type adapters need
# Signature, and Retrofit needs annotations at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# REST DTOs and persisted models are (de)serialized by Gson.
-keep class org.example.memosm.model.** { *; }
-keep class org.example.memosm.api.** { *; }

# CachedMemo and CacheListType appear in backup payloads via Gson.
-keep class org.example.memosm.data.cache.** { *; }
# Backup manifest/rows are (de)serialized by Gson.
-keep class org.example.memosm.data.backup.** { *; }
# Per-account offline snapshots are (de)serialized by Gson.
-keep class org.example.memosm.data.offline.** { *; }
# Durable outbox entries are (de)serialized by Gson.
-keep class org.example.memosm.data.sync.PendingOp* { *; }

# --- WorkManager -----------------------------------------------------------
# Workers are instantiated reflectively from the class name stored in the
# WorkSpec table.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- msgpack (backup archives) ---------------------------------------------
# MessageBuffer's static initializer loads MessageBufferU/MessageBufferBE via
# Class.forName when selecting the buffer implementation on Android.
-keep class org.msgpack.** { *; }
# DirectBufferAccess references JDK-internal sun.nio.ch.DirectBuffer, absent
# from the Android API; msgpack probes for it at runtime and falls back.
-dontwarn sun.nio.ch.DirectBuffer
-dontwarn org.msgpack.**
