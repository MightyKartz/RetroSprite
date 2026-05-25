package com.retrosprite.app.data.gkp

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeAliasDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedGkpPack(
    val game: GameDomain,
    val knowledge: List<KnowledgeChunkDomain>,
)

class GkpV0Parser(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    fun knowledgePaths(manifestText: String): List<String> {
        val manifest = parseObject(manifestText)
        require(manifest.string("schema_version") == SCHEMA_VERSION) {
            "Unsupported GKP schema_version: ${manifest.string("schema_version")}"
        }
        return manifest.obj("contents").array("knowledge").map { it.jsonPrimitive.content }
    }

    fun aliasPath(manifestText: String): String? {
        val manifest = parseObject(manifestText)
        require(manifest.string("schema_version") == SCHEMA_VERSION) {
            "Unsupported GKP schema_version: ${manifest.string("schema_version")}"
        }
        return manifest.obj("contents").stringOrNull("aliases")
    }

    fun parse(
        manifestText: String,
        knowledgeFiles: Map<String, String>,
        aliasFiles: Map<String, String> = emptyMap(),
        provenance: GkpPackProvenance = GkpPackProvenance.Unknown,
        signature: GkpSignatureMetadata = signatureMetadata(manifestText),
    ): ParsedGkpPack {
        val manifest = parseObject(manifestText)
        val schemaVersion = manifest.string("schema_version")
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported GKP schema_version: $schemaVersion"
        }

        val gameObject = manifest.obj("game")
        val gameId = gameObject.string("game_id")
        val romIdentity = gameObject.objOrNull("rom_identity")
        val game = GameDomain(
            gameId = gameId,
            packId = manifest.string("pack_id"),
            title = gameObject.string("title"),
            platform = gameObject.string("platform"),
            region = gameObject.stringOrNull("region"),
            languages = gameObject.array("languages").map { it.jsonPrimitive.content },
            romCrc32 = romIdentity?.stringOrNull("crc32"),
            romSha1 = romIdentity?.stringOrNull("sha1"),
            retroarchSystemIds = gameObject.arrayOrEmpty("retroarch_system_ids").map { it.jsonPrimitive.content },
            retroarchLabels = gameObject.arrayOrEmpty("retroarch_labels").map { it.jsonPrimitive.content },
            coverageTier = manifest.stringOrNull("coverage_tier"),
            packVersion = manifest.string("pack_version"),
            schemaVersion = schemaVersion,
            trustLevel = manifest.string("trust_level"),
            provenance = provenance.id,
            signatureStatus = signature.status.id,
            signatureKeyId = signature.keyId,
            contentDigest = signature.contentDigest,
            installedAt = nowMillis(),
        )

        val expectedPaths = knowledgePaths(manifestText).toSet()
        require(knowledgeFiles.keys.containsAll(expectedPaths)) {
            "Missing GKP knowledge files: ${expectedPaths - knowledgeFiles.keys}"
        }

        val aliasesByEntity = aliasMetadataByEntity(manifest, aliasFiles)
        val knowledge = expectedPaths.flatMap { path ->
            parseJsonl(knowledgeFiles.getValue(path)).map { row ->
                row.toKnowledgeChunk(gameId)
            }
        }.map { chunk ->
            chunk.withMergedAliases(aliasesByEntity[chunk.entityId].orEmpty())
        }
        require(knowledge.isNotEmpty()) { "GKP pack must include at least one knowledge row" }

        return ParsedGkpPack(game = game, knowledge = knowledge)
    }

    fun signatureMetadata(
        manifestText: String,
        contentDigest: String? = null,
    ): GkpSignatureMetadata {
        val manifest = parseObject(manifestText)
        val signatureObject = manifest.objOrNull("signature")
            ?: manifest.objOrNull("distribution")?.objOrNull("signature")
        return if (signatureObject == null) {
            GkpSignatureMetadata(
                status = GkpSignatureStatus.Unsigned,
                contentDigest = contentDigest,
            )
        } else {
            GkpSignatureMetadata(
                status = GkpSignatureStatus.Declared,
                keyId = signatureObject.stringOrNull("key_id"),
                contentDigest = contentDigest ?: signatureObject.stringOrNull("digest_sha256"),
            )
        }
    }

    private fun JsonObject.toKnowledgeChunk(gameId: String): KnowledgeChunkDomain =
        KnowledgeChunkDomain(
            id = 0L,
            gameId = gameId,
            entityId = string("entity_id"),
            entityType = string("entity_type"),
            canonicalName = string("canonical_name"),
            aliases = array("aliases").map { it.jsonPrimitive.content },
            aliasMetadata = inlineAliasMetadata(),
            descriptionShort = string("description_short"),
            descriptionLong = stringOrNull("description_long"),
            progressGate = stringOrNull("progress_gate"),
            spoilerLevel = string("spoiler_level"),
            sourceRefs = array("source_refs").map { it.jsonPrimitive.content },
            confidence = string("confidence"),
            answerTemplates = arrayOrEmpty("answer_templates").map(JsonElement::toString),
        )

    private fun parseObject(text: String): JsonObject =
        JSON.parseToJsonElement(text).jsonObject

    private fun parseJsonl(text: String): List<JsonObject> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { JSON.parseToJsonElement(it).jsonObject }
            .toList()

    private fun aliasMetadataByEntity(
        manifest: JsonObject,
        aliasFiles: Map<String, String>,
    ): Map<String, List<KnowledgeAliasDomain>> {
        val aliasPath = manifest.obj("contents").stringOrNull("aliases") ?: return emptyMap()
        val aliasText = aliasFiles[aliasPath] ?: return emptyMap()
        return parseObject(aliasText)
            .arrayOrEmpty("aliases")
            .mapNotNull { alias ->
                val obj = alias as? JsonObject ?: return@mapNotNull null
                val entityId = obj.stringOrNull("entity_id")?.trim().orEmpty()
                val term = obj.stringOrNull("term")?.trim().orEmpty()
                if (entityId.isEmpty() || term.isEmpty()) {
                    null
                } else {
                    entityId to KnowledgeAliasDomain(
                        term = term,
                        entityId = entityId,
                        kind = obj.stringOrNull("kind")?.trim()?.takeIf { it.isNotEmpty() } ?: "display_alias",
                        source = obj.stringOrNull("source")?.trim()?.takeIf { it.isNotEmpty() },
                        weight = obj.doubleOrNull("weight"),
                        canonicalTerm = obj.stringOrNull("canonical_term")?.trim()?.takeIf { it.isNotEmpty() },
                        notes = obj.stringOrNull("notes")?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            }
            .groupBy(
                keySelector = { (entityId, _) -> entityId },
                valueTransform = { (_, aliasMetadata) -> aliasMetadata },
            )
    }

    private fun JsonObject.inlineAliasMetadata(): List<KnowledgeAliasDomain> {
        val entityId = string("entity_id")
        return array("aliases")
            .mapNotNull { alias ->
                val term = alias.jsonPrimitive.content.trim()
                term.takeIf { it.isNotEmpty() }?.let {
                    KnowledgeAliasDomain(
                        term = it,
                        entityId = entityId,
                        kind = "display_alias",
                    )
                }
            }
    }

    private fun KnowledgeChunkDomain.withMergedAliases(
        extraAliases: List<KnowledgeAliasDomain>,
    ): KnowledgeChunkDomain {
        if (extraAliases.isEmpty()) return this
        val merged = (aliases + extraAliases.map { it.term })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val metadata = (aliasMetadata + extraAliases)
            .filter { it.entityId == entityId && it.term.isNotBlank() }
            .distinctBy { "${it.term}\u0000${it.kind}\u0000${it.canonicalTerm.orEmpty()}" }
        return copy(aliases = merged, aliasMetadata = metadata)
    }

    private fun JsonObject.obj(name: String): JsonObject =
        this[name]?.jsonObject ?: error("Missing object field '$name'")

    private fun JsonObject.objOrNull(name: String): JsonObject? =
        this[name]?.takeUnless { it is JsonNull }?.jsonObject

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field '$name'")

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        this[name]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: error("Missing string field '$name'")

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.doubleOrNull(name: String): Double? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    }

    private companion object {
        const val SCHEMA_VERSION = "gkp.v0"

        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
    }
}
