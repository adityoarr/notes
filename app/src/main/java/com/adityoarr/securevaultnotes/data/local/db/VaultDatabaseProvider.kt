package com.adityoarr.securevaultnotes.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val REAL_DB_NAME = "real_vault.db"
        const val DECOY_DB_NAME = "decoy_vault.db"

        private val DECOY_KEY =
            "SecureVault-Decoy-Plausible-Deniability-0x01".toByteArray(Charsets.US_ASCII)

        init {
            try {
                System.loadLibrary("sqlcipher")
            } catch (_: UnsatisfiedLinkError) {
                // Library already loaded or not available
            }
        }
    }

    @Volatile
    private var currentDb: AppDatabase? = null

    @Volatile
    var isDecoySession: Boolean = false
        private set

    fun requireDb(): AppDatabase = currentDb
        ?: throw IllegalStateException("Vault belum dibuka. Panggil openVault()/createRealVault() dulu.")

    fun vaultExists(): Boolean = context.getDatabasePath(REAL_DB_NAME).exists()

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

    suspend fun createRealVault(masterKey: ByteArray): AppDatabase = withContext(Dispatchers.IO) {
        closeCurrent()
        val db = build(REAL_DB_NAME, masterKey, destructiveMigration = false)
        db.openHelper.writableDatabase
        isDecoySession = false
        currentDb = db
        db
    }

    private suspend fun tryOpen(name: String, key: ByteArray): AppDatabase? {
        return try {
            if (!context.getDatabasePath(name).exists()) {
                null
            } else {
                val db = build(name, key, destructiveMigration = name == DECOY_DB_NAME)
                db.noteDao().ping() // Query pancingan: key salah => exception di sini
                db
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createDecoy(): AppDatabase {
        val db = build(DECOY_DB_NAME, DECOY_KEY, destructiveMigration = true)
        db.openHelper.writableDatabase
        return db
    }

    private fun build(name: String, key: ByteArray, destructiveMigration: Boolean): AppDatabase {
        val factory: SupportSQLiteOpenHelper.Factory = SupportOpenHelperFactory(key)
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(factory)
        if (destructiveMigration) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }
        return builder.build()
    }

    private fun closeCurrent() {
        currentDb?.close()
        currentDb = null
    }
}