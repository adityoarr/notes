package com.adityoarr.securevaultnotes.core.di

import android.content.Context
import androidx.room.Room
import com.adityoarr.securevaultnotes.data.local.db.AppDatabase
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val REAL_DB_NAME = "real_vault.db"
    private const val DECOY_DB_NAME = "decoy_vault.db"

    // Fungsi ini akan dipanggil oleh ViewModel Unlock saat user memasukkan password
    // Password akan di-hash menjadi ByteArray menggunakan Argon2id (Dibahas di Tahap 2)
    fun provideDatabaseInstance(
        context: Context,
        derivedKey: ByteArray
    ): AppDatabase {
        val factory = SupportFactory(derivedKey)

        return try {
            // 1. Coba buka Vault Asli
            val realDb = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                REAL_DB_NAME
            )
                .openHelperFactory(factory)
                .build()

            // Pancingan: Jika password salah, SQLCipher akan throw SQLiteException di sini
            // saat pertama kali database diakses. Kita pancing dengan query sederhana.
            runBlocking { realDb.noteDao().checkDbAccess() }

            realDb
        } catch (e: Exception) {
            // 2. Jika Exception tertangkap, berarti Password SALAH.
            // Kita arahkan ke Decoy Vault secara diam-diam.

            // Decoy DB bisa menggunakan password statis atau dummy.
            // (Dalam implementasi nyata, ini bisa di-generate saat first-launch setup)
            val decoyKey = "decoy_dummy_key_12345".toByteArray()
            val decoyFactory = SupportFactory(decoyKey)

            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DECOY_DB_NAME
            )
                .openHelperFactory(decoyFactory)
                .fallbackToDestructiveMigration() // Hancurkan & buat baru jika corrupt
                .build()
        }
    }
}