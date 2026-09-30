# ==========================================
# BOUNCYCASTLE (Argon2id)
# ==========================================
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.bouncycastle.jcajce.**

# ==========================================
# SQLCIPHER (Native JNI)
# ==========================================
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclasseswithmembers class net.sqlcipher.database.** {
    native <methods>;
}

# ==========================================
# ROOM DATABASE
# ==========================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class androidx.room.** { *; }

# ==========================================
# KOTLINX SERIALIZATION (.securevault manifest)
# ==========================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.securevault.notes.**$$serializer { *; }
-keepclassmembers class com.securevault.notes.** {
    *** Companion;
}
-keepclasseswithmembers class com.securevault.notes.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==========================================
# JETPACK SECURITY (EncryptedFile)
# ==========================================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ==========================================
# GOOGLE CRYPTO (Tink - dependency Jetpack Security)
# ==========================================
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ==========================================
# COROUTINES
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineDispatcherFactory {}
-keepnames class kotlinx.coroutines.test.** {}

# ==========================================
# COMPOSE & UI
# ==========================================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ==========================================
# DOMAIN MODELS (untuk serialisasi)
# ==========================================
-keep class com.securevault.notes.domain.model.** { *; }