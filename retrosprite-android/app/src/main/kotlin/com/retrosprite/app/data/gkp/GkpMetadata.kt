package com.retrosprite.app.data.gkp

import java.security.MessageDigest

enum class GkpPackProvenance(val id: String) {
    Bundled("bundled"),
    External("external"),
    Registry("registry"),
    Unknown("unknown");

    companion object {
        fun fromId(id: String?): GkpPackProvenance =
            values().firstOrNull { it.id == id } ?: Unknown
    }
}

enum class GkpSignatureStatus(val id: String) {
    Unsigned("unsigned"),
    Declared("declared"),
    Verified("verified"),
    Failed("failed"),
    Unknown("unknown");

    companion object {
        fun fromId(id: String?): GkpSignatureStatus =
            values().firstOrNull { it.id == id } ?: Unknown
    }
}

data class GkpSignatureMetadata(
    val status: GkpSignatureStatus = GkpSignatureStatus.Unsigned,
    val keyId: String? = null,
    val contentDigest: String? = null,
)

object GkpContentDigests {
    fun sha256(files: Map<String, String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.toSortedMap().forEach { (path, text) ->
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(text.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
