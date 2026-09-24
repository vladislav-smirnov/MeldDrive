package io.github.airdaydreamers.melddrive.data.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import timber.log.Timber

interface SecurityManager {
    fun encrypt(data: String): String
    fun decrypt(encryptedData: String): String
}

class TinkSecurityManager(context: Context) : SecurityManager {
    private var aead: Aead? = null

    init {
        try {
            AeadConfig.register()
            val keysetName = "master_keyset"
            val prefFileName = "master_key_preference"
            val masterKeyUri = "android-keystore://master_key"

            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, prefFileName)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle

            aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            Timber.i("TinkSecurityManager: Initialized Tink AEAD with Android Keystore")
        } catch (e: java.security.GeneralSecurityException) {
            Timber.w(e, "TinkSecurityManager: Keystore GeneralSecurityException, using Base64 fallback")
        } catch (e: java.io.IOException) {
            Timber.w(e, "TinkSecurityManager: Keystore IOException, using Base64 fallback")
        }
    }

    override fun encrypt(data: String): String {
        Timber.d("TinkSecurityManager: Encrypting string (length=%d)", data.length)
        val activeAead = aead ?: return Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
        val ciphertext = activeAead.encrypt(data.toByteArray(), null)
        return Base64.encodeToString(ciphertext, Base64.DEFAULT)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun decrypt(encryptedData: String): String {
        Timber.d("TinkSecurityManager: Decrypting string")
        val activeAead = aead ?: return String(Base64.decode(encryptedData, Base64.DEFAULT))
        val ciphertext = Base64.decode(encryptedData, Base64.DEFAULT)
        val decrypted = try {
            activeAead.decrypt(ciphertext, null)
        } catch (e: Exception) {
            Timber.w(e, "TinkSecurityManager: Decryption with Tink failed, falling back to Base64 decode")
            Base64.decode(encryptedData, Base64.DEFAULT)
        }
        return String(decrypted)
    }
}
