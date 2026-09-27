package io.github.airdaydreamers.melddrive.data.discovery

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MulticastLockManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var lockRefCount = 0

    @Synchronized
    fun acquireLock(tag: String = "MeldDriveMulticastLock") {
        if (multicastLock == null) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock(tag)?.apply {
                setReferenceCounted(true)
            }
        }

        try {
            multicastLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                    Timber.d("MulticastLock acquired for tag: %s", tag)
                }
                lockRefCount++
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire MulticastLock")
        }
    }

    @Synchronized
    fun releaseLock() {
        try {
            if (lockRefCount > 0) {
                lockRefCount--
            }
            multicastLock?.let { lock ->
                if (lock.isHeld && lockRefCount == 0) {
                    lock.release()
                    Timber.d("MulticastLock released")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release MulticastLock")
        }
    }
}
