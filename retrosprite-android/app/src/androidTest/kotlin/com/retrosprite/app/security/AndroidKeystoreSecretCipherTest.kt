package com.retrosprite.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretCipherTest {

    @Test
    fun encryptsAndDecryptsWithoutPersistingPlaintextInPayload() {
        val alias = "retrosprite_test_llm_key_${System.nanoTime()}"
        try {
            val cipher = AndroidKeystoreSecretCipher(alias = alias)
            val payload = cipher.encryptToString("secret-api-key")

            assertFalse(payload.contains("secret-api-key"))
            assertEquals("secret-api-key", cipher.decryptFromString(payload))
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }
}
