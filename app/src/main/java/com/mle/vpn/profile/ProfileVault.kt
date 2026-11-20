package com.mle.vpn.profile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted-at-rest profile vault. The original share URI contains credentials
 * (UUIDs/passwords/private keys), so it is encrypted with an Android Keystore
 * AES-256-GCM key. Plaintext profiles are never written to logs.
 */
class ProfileVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mle_profiles_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun save(profile: ImportedProfile) {
        val list = loadAll().filterNot { it.id == profile.id || it.sourceUri == profile.sourceUri }.toMutableList()
        list += profile
        val json = JSONArray()
        list.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id)
                put("protocol", p.protocol.name)
                put("name", p.displayName)
                put("host", p.host)
                put("port", p.port)
                put("uri", p.sourceUri)
            })
        }
        prefs.edit().putString(DATA_KEY, encrypt(json.toString())).apply()
    }

    @Synchronized
    fun loadAll(): List<ImportedProfile> {
        val encrypted = prefs.getString(DATA_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(decrypt(encrypted))
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ImportedProfile(
                            id = item.getString("id"),
                            protocol = ProfileProtocol.valueOf(item.getString("protocol")),
                            displayName = item.getString("name"),
                            host = item.getString("host"),
                            port = item.getInt("port"),
                            sourceUri = item.getString("uri")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun delete(id: String) {
        val remaining = loadAll().filterNot { it.id == id }
        if (remaining.isEmpty()) {
            prefs.edit().remove(DATA_KEY).apply()
            return
        }
        val json = JSONArray()
        remaining.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id); put("protocol", p.protocol.name)
                put("name", p.displayName); put("host", p.host)
                put("port", p.port); put("uri", p.sourceUri)
            })
        }
        prefs.edit().putString(DATA_KEY, encrypt(json.toString())).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        require(all.size > IV_LENGTH) { "Invalid encrypted profile data." }
        val iv = all.copyOfRange(0, IV_LENGTH)
        val ciphertext = all.copyOfRange(IV_LENGTH, all.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val DATA_KEY = "encrypted_profiles"
        const val KEY_ALIAS = "mle_vpn_profile_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
