package com.retrosprite.app.security

interface SecretCipher {
    fun encryptToString(plainText: String): String
    fun decryptFromString(payload: String): String
}
