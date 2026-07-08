package io.github.airdaydreamers.melddrive.data.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class SecurityManager(context: Context) {
    private val aead: Aead

    init {
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

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(data: String): String {
        val ciphertext = aead.encrypt(data.toByteArray(), null)
        return Base64.encodeToString(ciphertext, Base64.DEFAULT)
    }

    fun decrypt(encryptedData: String): String {
        val ciphertext = Base64.decode(encryptedData, Base64.DEFAULT)
        val decrypted = aead.decrypt(ciphertext, null)
        return String(decrypted)
    }
}
