package com.adityoarr.securevaultnotes.data.local.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SupportFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val REAL_DB_NAME = "real_vault.db"
        const val DECOY_DB_NAME = "decoy_vault.db"

        // Kunci decoy SENGAJA statis & tidak rahasia:
        // isi decoy hanya data dummy; tujuannya plausible deniability,
        // bukan melindungi rahasia.
        private val DECOY_KEY =
            "SecureVault-Decoy-Plausible-Deniability-0x01".toByteArray(Charsets.US_ASCII)
    }

    @Volatile
    private var currentDb: AppDatabase? = null

    /** True jika sesi ini membuka vault pura-pura (password salah). */
    @Volatile
    var isDecoySession: Boolean = false
        private set

    fun requireDb(): AppDatabase = currentDb
        ?: throw IllegalStateException("Vault belum dibuka. Panggil openVault()/createRealVault() dulu.")

    fun vaultExists(): Boolean = context.getDatabasePath(REAL_DB_NAME).exists()

    /**
     * Mode UNLOCK: coba buka vault asli dengan key turunan password.
     * Jika key salah, SQLCipher melempar exception saat query pertama →
     * kita jatuh SENYAP ke decoy (tanpa error ke UI).
     */
    suspend fun openVault(masterKey: ByteArray): AppDatabase = withContext(Dispatchers.IO) {
        closeCurrent()

        tryOpen(REAL_DB_NAME, masterKey)?.let { real ->
            isDecoySession = false
            currentDb = real
            return@withContext real
        }

        val decoy = tryOpen(DECOY_DB_NAME, DECOY_KEY) ?: createDecoy()
        isDecoySession = true
        currentDb = decoy
        decoy
    }

    /**
     * Mode SETUP (first launch): buat vault asli dengan key dari password user.
     */
    suspend fun createRealVault(masterKey: ByteArray): AppDatabase = withContext(Dispatchers.IO) {
        closeCurrent()
        val db = build(REAL_DB_NAME, SupportFactory(masterKey), destructiveMigration = false)
        db.openHelper.writableDatabase // trigger onCreate
        isDecoySession = false
        currentDb = db
        db
    }

    private suspend fun tryOpen(name: String, key: ByteArray): AppDatabase? = try {
        if (!context.getDatabasePath(name).exists()) return@try null
            val db = build(name, SupportFactory(key), destructiveMigration = name == DECOY_DB_NAME)
            db.noteDao().ping() // Query pancingan: salah key => exception di sini
            db
        } catch (e: Exception) {
        null
    }

    private fun createDecoy(): AppDatabase {
        val db = build(DECOY_DB_NAME, SupportFactory(DECOY_KEY), destructiveMigration = true)
        db.openHelper.writableDatabase
        return db
    }

    private fun build(
        name: String,
        factory: SupportFactory,
        destructiveMigration: Boolean
    ): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(factory)
        if (destructiveMigration) builder.fallbackToDestructiveMigration()
        // Vault ASLI tidak pernah destructive: skema berubah = migrasi eksplisit,
        // karena fallback destructive berarti musnahnya data user.
        return builder.build()
    }

    private fun closeCurrent() {
        currentDb?.close()
        currentDb = null
    }
}