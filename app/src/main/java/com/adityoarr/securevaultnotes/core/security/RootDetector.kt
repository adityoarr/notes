package com.adityoarr.securevaultnotes.core.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootBeer by lazy { RootBeer(context) }

    /**
     * Deteksi apakah device di-root.
     * Return true jika rooted, false jika aman.
     */
    fun isDeviceRooted(): Boolean {
        return rootBeer.isRooted
    }

    /**
     * Detail deteksi untuk logging/debugging (tidak untuk release).
     */
    fun getRootDetectionDetails(): Map<String, Boolean> {
        return mapOf(
            "rootManagementApps" to rootBeer.detectRootManagementApps(),
            "potentiallyDangerousApps" to rootBeer.detectPotentiallyDangerousApps(),
            "testKeys" to rootBeer.detectTestKeys(),
            "busyBoxBinary" to rootBeer.checkForBusyBoxBinary(),
            "suBinary" to rootBeer.checkForSuBinary(),
            "dangerousProps" to rootBeer.checkForDangerousProps(),
            "rwPaths" to rootBeer.checkForRWPaths(),
            "suExists" to rootBeer.checkSuExists(),
            "rootNative" to rootBeer.checkForRootNative(),
            "magiskBinary" to rootBeer.checkForMagiskBinary()
        )
    }
}