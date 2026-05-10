package com.phonebackup.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Payload class containing the encrypted stream and initialization vector.
 * Note: Data is kept as InputStream instead of ByteArray to prevent OOM on large files.
 */
data class EncryptedPayload(val data: InputStream, val iv: ByteArray)

/**
 * Manages End-to-End Encryption using the Android Keystore.
 */
class EncryptionManager {

    private val keyAlias = "backup_encryption_key"
    private val androidKeyStore = "AndroidKeyStore"
    private val cipherTransformation = "AES/GCM/NoPadding"

    init {
        generateKeyIfNecessary()
    }

    private fun generateKeyIfNecessary() {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    /**
     * Encrypts an input stream.
     * Note: Calling .encoded on a Keystore key returns null, ensuring non-exportability.
     */
    fun encrypt(inputStream: InputStream): EncryptedPayload {
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        val cipherInputStream = CipherInputStream(inputStream, cipher)
        
        return EncryptedPayload(cipherInputStream, iv)
    }

    /**
     * Decrypts an encrypted input stream using the provided IV.
     */
    fun decrypt(inputStream: InputStream, iv: ByteArray): InputStream {
        val cipher = Cipher.getInstance(cipherTransformation)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        return CipherInputStream(inputStream, cipher)
    }
}