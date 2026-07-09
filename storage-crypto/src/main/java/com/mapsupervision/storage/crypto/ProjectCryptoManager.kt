package com.mapsupervision.storage.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectCryptoManager @Inject constructor() {
    private val keyStoreProvider = "AndroidKeyStore"

    fun encrypt(alias: String, input: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val payload = cipher.doFinal(input)
        return EncryptedPayload(iv = iv, payload = payload)
    }

    fun decrypt(alias: String, payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), spec)
        return cipher.doFinal(payload.payload)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        runCatching { generator.init(spec) }.getOrElse {
            val fallback = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(fallback)
        }
        return generator.generateKey()
    }

    fun diagnostics(alias: String): KeyDiagnostics {
        val key = getOrCreateKey(alias)
        val keyFactory = KeyFactory.getInstance(key.algorithm, keyStoreProvider)
        val keyInfo = runCatching { keyFactory.getKeySpec(key, KeyInfo::class.java) as KeyInfo }.getOrNull()
        return KeyDiagnostics(
            isHardwareBacked = keyInfo?.isInsideSecureHardware == true,
            isStrongBoxBacked = false
        )
    }
}

data class EncryptedPayload(
    val iv: ByteArray,
    val payload: ByteArray
)

data class KeyDiagnostics(
    val isHardwareBacked: Boolean,
    val isStrongBoxBacked: Boolean
)
