package com.retrosprite.app.data.gkp

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

enum class GkpPreflightSeverity { Info, Warning, Error }

data class GkpPreflightIssue(
    val severity: GkpPreflightSeverity,
    val code: String,
    val path: String?,
    val message: String,
)

data class GkpPreflightInput(
    val displayName: String,
    val files: Map<String, String>,
    val allPaths: Set<String> = files.keys,
)

data class GkpPreflightReport(
    val displayName: String,
    val ok: Boolean,
    val packId: String?,
    val gameId: String?,
    val gameTitle: String?,
    val packVersion: String?,
    val coverageTier: String?,
    val schemaVersion: String?,
    val knowledgeRows: Int,
    val sourceCount: Int,
    val goldenRows: Int,
    val licenseStatus: String,
    val signatureStatus: String,
    val signatureKeyId: String?,
    val contentDigest: String?,
    val issues: List<GkpPreflightIssue>,
) {
    val errorCount: Int get() = issues.count { it.severity == GkpPreflightSeverity.Error }
    val warningCount: Int get() = issues.count { it.severity == GkpPreflightSeverity.Warning }
}

class GkpV0PreflightValidator {

    fun validate(input: GkpPreflightInput): GkpPreflightReport {
        val issues = mutableListOf<GkpPreflightIssue>()
        val files = input.files.mapKeys { (path, _) -> normalizePath(path) }
        val allPaths = input.allPaths.map(::normalizePath).toSet()

        fun issue(
            severity: GkpPreflightSeverity,
            code: String,
            path: String?,
            message: String,
        ) {
            issues += GkpPreflightIssue(
                severity = severity,
                code = code,
                path = path,
                message = message,
            )
        }

        allPaths.forEach { path ->
            if (path.isBlank()) return@forEach
            if (path.hasBlockedExtension()) {
                issue(
                    severity = GkpPreflightSeverity.Error,
                    code = "blocked_file_type",
                    path = path,
                    message = "GKP 不能包含 ROM、可执行文件、脚本或二进制包。",
                )
            }
        }
        files.forEach { (path, text) ->
            if (text.contains(SCAFFOLD_PLACEHOLDER)) {
                issue(
                    severity = GkpPreflightSeverity.Error,
                    code = "scaffold_placeholder",
                    path = path,
                    message = "GKP Lite scaffold placeholder 必须替换为审核后的来源化内容。",
                )
            }
        }

        val manifest = readObject(files, MANIFEST_PATH, issues) ?: return report(
            input = input,
            issues = issues,
            licenseStatus = "未检查",
            contentDigest = digestOrNull(files),
        )

        val schemaVersion = manifest.stringOrNull("schema_version")
        val packId = manifest.stringOrNull("pack_id")
        val packVersion = manifest.stringOrNull("pack_version")
        val coverageTier = manifest.stringOrNull("coverage_tier")
        val contentDigest = digestOrNull(files)
        val signature = validateSignature(manifest, issues)
        if (schemaVersion != SCHEMA_VERSION) {
            issue(
                severity = GkpPreflightSeverity.Error,
                code = "unsupported_schema",
                path = MANIFEST_PATH,
                message = "schema_version 必须是 $SCHEMA_VERSION。",
            )
        }
        packId?.let { requireIdentifier(it, "pack_id", MANIFEST_PATH, issues) }
            ?: issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "manifest 缺少 pack_id。")
        requireNonBlank(packVersion, "pack_version", MANIFEST_PATH, issues)
        requireIn(manifest.stringOrNull("trust_level"), TRUST_LEVELS, "trust_level", MANIFEST_PATH, issues)
        coverageTier?.let { tier ->
            if (tier !in COVERAGE_TIERS) {
                issue(
                    severity = GkpPreflightSeverity.Error,
                    code = "invalid_coverage_tier",
                    path = MANIFEST_PATH,
                    message = "coverage_tier 必须是 ${COVERAGE_TIERS.joinToString()}。",
                )
            }
        } ?: issue(
            severity = GkpPreflightSeverity.Warning,
            code = "missing_coverage_tier",
            path = MANIFEST_PATH,
            message = "manifest 建议声明 coverage_tier，便于按 Lite/expanded/deep profile 预检。",
        )

        val game = manifest.objOrNull("game")
        val gameId = game?.stringOrNull("game_id")
        val gameTitle = game?.stringOrNull("title")
        if (game == null) {
            issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "manifest 缺少 game。")
        } else {
            gameId?.let { requireIdentifier(it, "game.game_id", MANIFEST_PATH, issues) }
                ?: issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "game 缺少 game_id。")
            requireNonBlank(gameTitle, "game.title", MANIFEST_PATH, issues)
            requireNonBlank(game.stringOrNull("platform"), "game.platform", MANIFEST_PATH, issues)
            if (game.arrayOrNull("languages").orEmpty().isEmpty()) {
                issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "game.languages 必须至少包含一个语言。")
            }
        }

        val contents = manifest.objOrNull("contents")
        if (contents == null) {
            issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "manifest 缺少 contents。")
            return report(
                input = input,
                issues = issues,
                packId = packId,
                gameId = gameId,
                gameTitle = gameTitle,
                packVersion = packVersion,
                coverageTier = coverageTier,
                schemaVersion = schemaVersion,
                licenseStatus = "未检查",
                signatureStatus = signature.status.id,
                signatureKeyId = signature.keyId,
                contentDigest = contentDigest,
            )
        }

        val knowledgePaths = contents.arrayOrNull("knowledge")
            .orEmpty()
            .mapNotNull { it.primitiveStringOrNull() }
        if (knowledgePaths.isEmpty()) {
            issue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "contents.knowledge 必须至少声明一个 JSONL 文件。")
        }

        val citationsPath = contents.stringOrNull("citations")
        val aliasesPath = contents.stringOrNull("aliases")
        val spoilerGraphPath = contents.stringOrNull("spoiler_graph")
        val goldensPath = contents.stringOrNull("qa_goldens")
        val requiredPaths = buildList {
            addAll(knowledgePaths)
            addPath(citationsPath, "contents.citations", issues)
            addPath(aliasesPath, "contents.aliases", issues)
            addPath(spoilerGraphPath, "contents.spoiler_graph", issues)
            addPath(goldensPath, "contents.qa_goldens", issues)
            add(LICENSE_PATH)
        }

        requiredPaths.forEach { path ->
            validateDeclaredPath(path, files, issues)
        }
        warnUnknownFiles(allPaths, requiredPaths.toSet(), issues)

        val citations = citationsPath?.let { readJsonl(files, it, issues) }.orEmpty()
        val sourceIds = citations.mapNotNull { it.stringOrNull("source_id") }
        requireUnique(sourceIds, "source_id", citationsPath, issues)
        citations.forEach { source ->
            val path = citationsPath ?: return@forEach
            source.stringOrNull("source_id")?.let { requireIdentifier(it, "source_id", path, issues) }
                ?: issue(GkpPreflightSeverity.Error, "missing_field", path, "citation 缺少 source_id。")
            requireNonBlank(source.stringOrNull("title"), "source.title", path, issues)
            requireNonBlank(source.stringOrNull("license"), "source.license", path, issues)
            requireIn(source.stringOrNull("kind"), SOURCE_KINDS, "source.kind", path, issues)
            requireIn(source.stringOrNull("reliability"), RELIABILITY_LEVELS, "source.reliability", path, issues)
        }
        val sourceIdSet = sourceIds.toSet()

        val licenseStatus = validateLicense(files, issues)
        val spoilerGraph = spoilerGraphPath?.let { readObject(files, it, issues) }
        val gateIds = spoilerGraph?.arrayOrNull("gates")
            .orEmpty()
            .mapNotNull { it.jsonObjectOrNull()?.stringOrNull("gate_id") }
            .toSet()
        if (spoilerGraph != null) {
            if (gateIds.isEmpty()) {
                issue(GkpPreflightSeverity.Error, "missing_field", spoilerGraphPath, "spoiler_graph.gates 必须至少包含一个 gate。")
            }
            val defaultGate = spoilerGraph.stringOrNull("default_gate")
            if (defaultGate == null || defaultGate !in gateIds) {
                issue(GkpPreflightSeverity.Error, "unknown_gate", spoilerGraphPath, "spoiler_graph.default_gate 必须存在于 gates。")
            }
            spoilerGraph.arrayOrNull("edges").orEmpty().forEach { edge ->
                val obj = edge.jsonObjectOrNull()
                val from = obj?.stringOrNull("from")
                val to = obj?.stringOrNull("to")
                if (from !in gateIds || to !in gateIds) {
                    issue(GkpPreflightSeverity.Error, "unknown_gate", spoilerGraphPath, "spoiler_graph.edges 引用了不存在的 gate。")
                }
            }
        }

        val knowledge = knowledgePaths.flatMap { path -> readJsonl(files, path, issues) }
        val entityIds = knowledge.mapNotNull { it.stringOrNull("entity_id") }
        requireUnique(entityIds, "entity_id", "knowledge/*.jsonl", issues)
        knowledge.forEach { row ->
            val rowPath = knowledgePaths.firstOrNull { files[it]?.contains(row.toString()) == true } ?: "knowledge/*.jsonl"
            val entityId = row.stringOrNull("entity_id")
            entityId?.let { requireIdentifier(it, "entity_id", rowPath, issues) }
                ?: issue(GkpPreflightSeverity.Error, "missing_field", rowPath, "knowledge row 缺少 entity_id。")
            requireIn(row.stringOrNull("entity_type"), ENTITY_TYPES, "entity_type", rowPath, issues)
            requireNonBlank(row.stringOrNull("canonical_name"), "canonical_name", rowPath, issues)
            if (row.arrayOrNull("aliases").orEmpty().isEmpty()) {
                issue(GkpPreflightSeverity.Error, "missing_field", rowPath, "aliases 必须至少包含一项。")
            }
            requireNonBlank(row.stringOrNull("description_short"), "description_short", rowPath, issues)
            row.stringOrNull("description_short")?.let {
                if (it.length > 240) {
                    issue(GkpPreflightSeverity.Warning, "long_text", rowPath, "description_short 建议不超过 240 字符。")
                }
            }
            row.stringOrNull("description_long")?.let {
                if (it.length > 1_200) {
                    issue(GkpPreflightSeverity.Warning, "long_text", rowPath, "description_long 建议不超过 1200 字符。")
                }
            }
            requireIn(row.stringOrNull("spoiler_level"), SPOILER_LEVELS, "spoiler_level", rowPath, issues)
            requireIn(row.stringOrNull("confidence"), CONFIDENCE_LEVELS, "confidence", rowPath, issues)
            row.stringOrNull("progress_gate")?.let { gate ->
                if (gate !in gateIds) {
                    issue(GkpPreflightSeverity.Error, "unknown_gate", rowPath, "progress_gate '$gate' 不存在于 spoiler_graph。")
                }
            }
            row.arrayOrNull("source_refs").orEmpty().forEach { source ->
                val sourceId = source.primitiveStringOrNull()
                if (sourceId == null || sourceId !in sourceIdSet) {
                    issue(GkpPreflightSeverity.Error, "unknown_source_ref", rowPath, "knowledge source_refs 引用了未知来源。")
                }
            }
            row.arrayOrNull("answer_templates").orEmpty().forEach { template ->
                val tmpl = template.jsonObjectOrNull()
                if (tmpl == null) {
                    issue(GkpPreflightSeverity.Error, "invalid_template", rowPath, "answer_templates 必须是对象数组。")
                } else {
                    tmpl.stringOrNull("template_id")?.let { requireIdentifier(it, "template_id", rowPath, issues) }
                        ?: issue(GkpPreflightSeverity.Error, "missing_field", rowPath, "answer_template 缺少 template_id。")
                    tmpl.stringOrNull("intent")?.let {
                        requireIn(it, ANSWER_INTENTS, "template.intent", rowPath, issues)
                    }
                    tmpl.stringOrNull("spoiler_level")?.let {
                        requireIn(it, SPOILER_LEVELS, "template.spoiler_level", rowPath, issues)
                    }
                    listOf("spoiler_light", "spoiler_clear", "spoiler_direct").forEach { field ->
                        tmpl.stringOrNull(field)?.let { level ->
                            requireIn(level, SPOILER_LEVELS, "template.$field", rowPath, issues)
                        }
                    }
                    val hasFlatAnswer = !tmpl.stringOrNull("answer").isNullOrBlank()
                    val hasTieredAnswer = listOf("answer_light", "answer_clear", "answer_direct")
                        .any { !tmpl.stringOrNull(it).isNullOrBlank() }
                    if (!hasFlatAnswer && !hasTieredAnswer) {
                        issue(GkpPreflightSeverity.Error, "missing_field", rowPath, "answer_template 缺少 answer 或 answer_* 分层答案。")
                    }
                    tmpl.arrayOrNull("source_refs").orEmpty().forEach { source ->
                        val sourceId = source.primitiveStringOrNull()
                        if (sourceId == null || sourceId !in sourceIdSet) {
                            issue(GkpPreflightSeverity.Error, "unknown_source_ref", rowPath, "answer_template source_refs 引用了未知来源。")
                        }
                    }
                }
            }
        }
        if (knowledge.isEmpty()) {
            issue(GkpPreflightSeverity.Error, "empty_knowledge", "knowledge/*.jsonl", "知识文件必须至少包含一条知识。")
        }
        val entityIdSet = entityIds.toSet()

        aliasesPath?.let { path ->
            val aliases = readObject(files, path, issues)?.arrayOrNull("aliases").orEmpty()
            if (aliases.isEmpty()) {
                issue(GkpPreflightSeverity.Error, "missing_field", path, "aliases 必须至少包含一项。")
            }
            aliases.forEach { alias ->
                val obj = alias.jsonObjectOrNull()
                if (obj == null) {
                    issue(GkpPreflightSeverity.Error, "invalid_alias", path, "aliases 必须是对象数组。")
                } else {
                    val term = obj.stringOrNull("term")?.trim()
                    requireNonBlank(term, "alias.term", path, issues)
                    val entityId = obj.stringOrNull("entity_id")
                    if (entityId == null || entityId !in entityIdSet) {
                        issue(GkpPreflightSeverity.Error, "unknown_entity", path, "alias.entity_id 必须指向已存在的 knowledge entity。")
                    }
                    val kind = obj.stringOrNull("kind")
                    val source = obj.stringOrNull("source")
                    obj.stringOrNull("kind")?.let { requireIn(it, ALIAS_KINDS, "alias.kind", path, issues) }
                    obj.stringOrNull("source")?.let { requireIn(it, ALIAS_SOURCES, "alias.source", path, issues) }
                    if (kind in ASR_ALIAS_KINDS || source == "observed_asr") {
                        val canonicalTerm = obj.stringOrNull("canonical_term")?.trim()
                        requireNonBlank(canonicalTerm, "alias.canonical_term", path, issues)
                        if (!term.isNullOrBlank() && !canonicalTerm.isNullOrBlank() && term == canonicalTerm) {
                            issue(
                                GkpPreflightSeverity.Error,
                                "invalid_value",
                                path,
                                "alias.canonical_term 不能和 alias.term 相同。",
                            )
                        }
                    }
                }
            }
        }

        val goldens = goldensPath?.let { readJsonl(files, it, issues) }.orEmpty()
        val qaIds = goldens.mapNotNull { it.stringOrNull("qa_id") }
        requireUnique(qaIds, "qa_id", goldensPath, issues)
        goldens.forEach { qa ->
            val path = goldensPath ?: return@forEach
            qa.stringOrNull("qa_id")?.let { requireIdentifier(it, "qa_id", path, issues) }
                ?: issue(GkpPreflightSeverity.Error, "missing_field", path, "qa 缺少 qa_id。")
            if (gameId != null && qa.stringOrNull("game_id") != gameId) {
                issue(GkpPreflightSeverity.Error, "game_mismatch", path, "qa.game_id 必须等于 manifest game_id。")
            }
            requireIn(qa.stringOrNull("spoiler_level"), SPOILER_LEVELS, "qa.spoiler_level", path, issues)
            qa.stringOrNull("progress_gate")?.let { gate ->
                if (gate !in gateIds) {
                    issue(GkpPreflightSeverity.Error, "unknown_gate", path, "qa.progress_gate '$gate' 不存在于 spoiler_graph。")
                }
            }
            qa.arrayOrNull("expected_entity_ids").orEmpty().forEach { entity ->
                val entityId = entity.primitiveStringOrNull()
                if (entityId == null || entityId !in entityIdSet) {
                    issue(GkpPreflightSeverity.Error, "unknown_entity", path, "qa.expected_entity_ids 引用了未知 entity。")
                }
            }
            qa.arrayOrNull("source_refs").orEmpty().forEach { source ->
                val sourceId = source.primitiveStringOrNull()
                if (sourceId == null || sourceId !in sourceIdSet) {
                    issue(GkpPreflightSeverity.Error, "unknown_source_ref", path, "qa.source_refs 引用了未知来源。")
                }
            }
        }

        return report(
            input = input,
            issues = issues,
            packId = packId,
            gameId = gameId,
            gameTitle = gameTitle,
            packVersion = packVersion,
            coverageTier = coverageTier,
            schemaVersion = schemaVersion,
            knowledgeRows = knowledge.size,
            sourceCount = sourceIdSet.size,
            goldenRows = goldens.size,
            licenseStatus = licenseStatus,
            signatureStatus = signature.status.id,
            signatureKeyId = signature.keyId,
            contentDigest = contentDigest,
        )
    }

    private fun validateSignature(
        manifest: JsonObject,
        issues: MutableList<GkpPreflightIssue>,
    ): GkpSignatureMetadata {
        val signature = manifest.objOrNull("signature")
            ?: manifest.objOrNull("distribution")?.objOrNull("signature")
        if (signature == null) {
            issues += GkpPreflightIssue(
                GkpPreflightSeverity.Info,
                "unsigned_pack",
                MANIFEST_PATH,
                "manifest 未声明签名；本地安装允许，registry 分发前需要签名。",
            )
            return GkpSignatureMetadata(status = GkpSignatureStatus.Unsigned)
        }

        val algorithm = signature.stringOrNull("algorithm")
        if (algorithm !in SIGNATURE_ALGORITHMS) {
            issues += GkpPreflightIssue(
                GkpPreflightSeverity.Error,
                "unsupported_signature_algorithm",
                MANIFEST_PATH,
                "signature.algorithm 必须是 ${SIGNATURE_ALGORITHMS.joinToString()}。",
            )
        }
        val keyId = signature.stringOrNull("key_id")
        requireNonBlank(keyId, "signature.key_id", MANIFEST_PATH, issues)
        val digest = signature.stringOrNull("digest_sha256")
        if (digest == null || !SHA256_HEX_PATTERN.matches(digest)) {
            issues += GkpPreflightIssue(
                GkpPreflightSeverity.Error,
                "invalid_signature_digest",
                MANIFEST_PATH,
                "signature.digest_sha256 必须是 64 位十六进制 SHA-256。",
            )
        }
        requireNonBlank(signature.stringOrNull("signature"), "signature.signature", MANIFEST_PATH, issues)

        return GkpSignatureMetadata(
            status = GkpSignatureStatus.Declared,
            keyId = keyId,
            contentDigest = digest,
        )
    }

    private fun readObject(
        files: Map<String, String>,
        path: String,
        issues: MutableList<GkpPreflightIssue>,
    ): JsonObject? {
        val text = files[normalizePath(path)]
        if (text == null) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_file", path, "缺少文件：$path")
            return null
        }
        return runCatching { JSON.parseToJsonElement(text).jsonObject }
            .onFailure {
                issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "invalid_json", path, "JSON 解析失败：${it.message}")
            }
            .getOrNull()
    }

    private fun readJsonl(
        files: Map<String, String>,
        path: String,
        issues: MutableList<GkpPreflightIssue>,
    ): List<JsonObject> {
        val normalized = normalizePath(path)
        val text = files[normalized]
        if (text == null) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_file", path, "缺少文件：$path")
            return emptyList()
        }
        val rows = mutableListOf<JsonObject>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEachIndexed
            runCatching { JSON.parseToJsonElement(line).jsonObject }
                .onSuccess { rows += it }
                .onFailure {
                    issues += GkpPreflightIssue(
                        GkpPreflightSeverity.Error,
                        "invalid_jsonl",
                        "$path:${index + 1}",
                        "JSONL 第 ${index + 1} 行解析失败：${it.message}",
                    )
                }
        }
        if (rows.isEmpty()) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "empty_jsonl", path, "$path 必须至少包含一个 JSON 对象。")
        }
        return rows
    }

    private fun validateDeclaredPath(
        path: String,
        files: Map<String, String>,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        if (path.startsWith("/") || path.split("/").any { it == ".." }) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "unsafe_path", path, "manifest.contents 只能引用包内相对路径。")
            return
        }
        if (normalizePath(path) !in files) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_file", path, "声明的文件不存在：$path")
        }
    }

    private fun warnUnknownFiles(
        allPaths: Set<String>,
        declaredPaths: Set<String>,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        val allowed = declaredPaths + setOf(MANIFEST_PATH, "changelog.md")
        allPaths
            .filter { it.isNotBlank() && it !in allowed }
            .filterNot { it.startsWith("knowledge/") && it.endsWith(".jsonl") && it in declaredPaths }
            .forEach { path ->
                if (!path.hasBlockedExtension()) {
                    issues += GkpPreflightIssue(
                        GkpPreflightSeverity.Warning,
                        "undeclared_file",
                        path,
                        "该文件不会被 v0 导入器使用。",
                    )
                }
            }
    }

    private fun validateLicense(
        files: Map<String, String>,
        issues: MutableList<GkpPreflightIssue>,
    ): String {
        val license = files[LICENSE_PATH]
        return when {
            license == null -> {
                issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_license", LICENSE_PATH, "外部 GKP 必须包含 sources/licenses.md。")
                "缺失"
            }
            license.isBlank() -> {
                issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "empty_license", LICENSE_PATH, "sources/licenses.md 不能为空。")
                "为空"
            }
            else -> "已声明"
        }
    }

    private fun report(
        input: GkpPreflightInput,
        issues: List<GkpPreflightIssue>,
        packId: String? = null,
        gameId: String? = null,
        gameTitle: String? = null,
        packVersion: String? = null,
        coverageTier: String? = null,
        schemaVersion: String? = null,
        knowledgeRows: Int = 0,
        sourceCount: Int = 0,
        goldenRows: Int = 0,
        licenseStatus: String,
        signatureStatus: String = GkpSignatureStatus.Unsigned.id,
        signatureKeyId: String? = null,
        contentDigest: String? = null,
    ): GkpPreflightReport = GkpPreflightReport(
        displayName = input.displayName,
        ok = issues.none { it.severity == GkpPreflightSeverity.Error },
        packId = packId,
        gameId = gameId,
        gameTitle = gameTitle,
        packVersion = packVersion,
        coverageTier = coverageTier,
        schemaVersion = schemaVersion,
        knowledgeRows = knowledgeRows,
        sourceCount = sourceCount,
        goldenRows = goldenRows,
        licenseStatus = licenseStatus,
        signatureStatus = signatureStatus,
        signatureKeyId = signatureKeyId,
        contentDigest = contentDigest,
        issues = issues,
    )

    private fun digestOrNull(files: Map<String, String>): String? =
        runCatching { GkpContentDigests.sha256(files) }.getOrNull()

    private fun MutableList<String>.addPath(
        path: String?,
        field: String,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        if (path == null) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_field", MANIFEST_PATH, "manifest 缺少 $field。")
        } else {
            add(path)
        }
    }

    private fun requireNonBlank(
        value: String?,
        field: String,
        path: String?,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        if (value.isNullOrBlank()) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "missing_field", path, "$field 不能为空。")
        }
    }

    private fun requireIdentifier(
        value: String,
        field: String,
        path: String?,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        if (!ID_PATTERN.matches(value)) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "invalid_id", path, "$field 不是合法 id：$value")
        }
    }

    private fun requireIn(
        value: String?,
        allowed: Set<String>,
        field: String,
        path: String?,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        if (value !in allowed) {
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "invalid_value", path, "$field 必须是 ${allowed.joinToString()}。")
        }
    }

    private fun requireUnique(
        values: List<String>,
        field: String,
        path: String?,
        issues: MutableList<GkpPreflightIssue>,
    ) {
        val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        duplicates.forEach { duplicate ->
            issues += GkpPreflightIssue(GkpPreflightSeverity.Error, "duplicate_id", path, "$field 重复：$duplicate")
        }
    }

    private fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').removePrefix("./").trim('/')

    private fun String.hasBlockedExtension(): Boolean =
        substringAfterLast('.', missingDelimiterValue = "")
            .lowercase() in BLOCKED_EXTENSIONS

    private fun JsonObject.objOrNull(name: String): JsonObject? =
        this[name]?.takeUnless { it is JsonNull }?.jsonObjectOrNull()

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        this[name]?.takeUnless { it is JsonNull }?.jsonArrayOrNull()

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.primitiveStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val SCHEMA_VERSION = "gkp.v0"
        const val MANIFEST_PATH = "manifest.json"
        const val LICENSE_PATH = "sources/licenses.md"
        const val SCAFFOLD_PLACEHOLDER = "__REPLACE_WITH_REVIEWED_GKP_DATA__"

        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")
        val SHA256_HEX_PATTERN = Regex("[a-fA-F0-9]{64}")
        val SIGNATURE_ALGORITHMS = setOf("ed25519")
        val TRUST_LEVELS = setOf("official", "community", "personal", "sample")
        val ALIAS_KINDS = setOf("display_alias", "asr_variant", "observed_asr")
        val ASR_ALIAS_KINDS = setOf("asr_variant", "observed_asr")
        val ALIAS_SOURCES = setOf("official", "community", "generated_phonetic", "observed_asr", "manual_review")
        val COVERAGE_TIERS = setOf("lite", "expanded", "deep")
        val SOURCE_KINDS = setOf(
            "manual",
            "official_site",
            "project_note",
            "community_note",
            "wiki",
            "transcript",
            "other",
        )
        val RELIABILITY_LEVELS = setOf("verified", "community", "uncertain")
        val ENTITY_TYPES = setOf(
            "mechanic",
            "item",
            "enemy",
            "boss",
            "location",
            "quest",
            "npc",
            "dialogue",
            "strategy",
            "faq",
            "note",
        )
        val SPOILER_LEVELS = setOf("none", "light", "medium", "heavy")
        val CONFIDENCE_LEVELS = setOf("verified", "community", "uncertain")
        val ANSWER_INTENTS = setOf(
            "game_overview",
            "beginner_guide",
            "team_build",
            "leveling",
            "name_mapping",
            "location",
            "usage",
            "mechanic",
            "route_hint",
            "strategy",
            "production",
            "no_evidence",
            "unknown_or_out_of_scope",
        )
        val BLOCKED_EXTENSIONS = setOf(
            "apk",
            "apks",
            "aab",
            "exe",
            "dll",
            "dylib",
            "so",
            "jar",
            "class",
            "dex",
            "sh",
            "bash",
            "zsh",
            "fish",
            "bat",
            "cmd",
            "ps1",
            "py",
            "js",
            "mjs",
            "cjs",
            "rom",
            "iso",
            "bin",
            "cue",
            "sfc",
            "smc",
            "nes",
            "fds",
            "gb",
            "gbc",
            "gba",
            "nds",
            "z64",
            "n64",
            "v64",
            "zip",
            "7z",
            "rar",
        )
    }
}
