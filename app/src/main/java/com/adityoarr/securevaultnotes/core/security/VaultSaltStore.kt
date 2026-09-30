package com.adityoarr.securevaultnotes.core.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class VaultSaltStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("vault_meta", Context.MODE_PRIVATE)
    }

    fun getSalt(): ByteArray? =
        prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun saveSalt(salt: ByteArray) {
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
        }
    }

    private companion object {
        const val KEY_SALT = "vault_salt"
    }
}