package com.example.data.repository

import android.content.Context
import com.example.data.local.AppPreferences
import com.example.data.local.FeedSurgeonDao
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.model.RemoteRulesetPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RulesetSyncResult {
    data class Success(
        val version: String,
        val updatedApps: Int,
        val fromLocalCache: Boolean,
        val networkErrorReason: String? = null,
        val etagUnchanged: Boolean = false,
        val revertedToHardcoded: Boolean = false
    ) : RulesetSyncResult

    data class Error(val message: String) : RulesetSyncResult
}

class RulesetRepository(
    private val context: Context,
    private val dao: FeedSurgeonDao,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient? = null,
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
) {
    private val adapter = moshi.adapter(RemoteRulesetPayload::class.java)
    private val syncMutex = Mutex()
    private val resolvedOkHttpClient: OkHttpClient by lazy {
        okHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val localCacheFile: File
        get() = File(context.filesDir, "ruleset_cache.json")

    suspend fun ensureLocalCacheSeeded() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val bundledJson = loadBundledAssetOrFallbackJson()
            val bundledParsed = parseAndValidate(bundledJson) ?: return@withLock

            val existingCacheParsed = if (localCacheFile.exists()) {
                parseAndValidate(runCatching { localCacheFile.readText() }.getOrDefault(""))
            } else {
                null
            }

            if (existingCacheParsed == null || compareVersions(bundledParsed.version, existingCacheParsed.version) > 0) {
                writeLocalCacheFile(bundledJson)
                applyPayloadToDatabase(bundledParsed)
                settingsDataStore.saveFetchedRuleset(
                    version = bundledParsed.version,
                    rawJson = bundledJson,
                    checkedAtMs = 0L,
                    etag = ""
                )
            }
        }
    }

    suspend fun autoSyncIfDue(force: Boolean = false): RulesetSyncResult =
        withContext(Dispatchers.IO) {
            val prefs = settingsDataStore.preferencesFlow.first()
            if (!force && !prefs.autoUpdateRules) {
                return@withContext RulesetSyncResult.Success(
                    version = prefs.rulesetVersion,
                    updatedApps = dao.getAllRulesSnapshot().size,
                    fromLocalCache = true
                )
            }
            val now = System.currentTimeMillis()
            val isStale = prefs.lastRulesetCheckMs == 0L ||
                (now - prefs.lastRulesetCheckMs) >= AppPreferences.AUTO_SYNC_INTERVAL_MS
            if (!force && !isStale) {
                return@withContext RulesetSyncResult.Success(
                    version = prefs.rulesetVersion,
                    updatedApps = dao.getAllRulesSnapshot().size,
                    fromLocalCache = true
                )
            }
            syncFromRemoteOrFallback()
        }

    suspend fun syncFromRemoteOrFallback(): RulesetSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val prefs = settingsDataStore.preferencesFlow.first()
            val targetUrl = prefs.rulesetEndpointUrl.trim().ifEmpty { AppPreferences.DEFAULT_GIST_URL }
            if (!isTrustedHttpsUrl(targetUrl)) {
                return@withLock applyLocalCacheFallback(
                    prefs = prefs,
                    reason = "Ruleset endpoint must use HTTPS; kept local ${prefs.rulesetVersion}",
                    isTransientNetworkIssue = false
                )
            }
            val savedEtag = prefs.rulesetEtag.trim()
            val cachedPair = readValidCachedPayload(prefs)

            try {
                // Step 1: Verify the remote Gist ETag via HEAD before downloading the full JSON body
                if (savedEtag.isNotEmpty() && cachedPair != null) {
                    val headRequest = Request.Builder()
                        .url(targetUrl)
                        .head()
                        .header("If-None-Match", savedEtag)
                        .header("Accept", "application/json")
                        .build()

                    resolvedOkHttpClient.newCall(headRequest).execute().use { headResponse ->
                        val remoteEtag = headResponse.header("ETag")?.trim().orEmpty()
                        val etagMatched = headResponse.code == 304 ||
                            (headResponse.isSuccessful &&
                                remoteEtag.isNotEmpty() &&
                                normalizeEtag(remoteEtag) == normalizeEtag(savedEtag))

                        if (etagMatched) {
                            applyPayloadToDatabase(cachedPair.first)
                            settingsDataStore.updateLastCheckedOnly(
                                checkedAtMs = System.currentTimeMillis(),
                                etag = remoteEtag.ifEmpty { savedEtag }
                            )
                            return@withLock RulesetSyncResult.Success(
                                version = cachedPair.first.version,
                                updatedApps = cachedPair.first.apps.size,
                                fromLocalCache = true,
                                etagUnchanged = true
                            )
                        }
                        if (headResponse.code >= 500 || headResponse.code == 429) {
                            return@withLock applyLocalCacheFallback(
                                prefs = prefs,
                                reason = "Using cached ruleset ${prefs.rulesetVersion} (HEAD HTTP ${headResponse.code})",
                                isTransientNetworkIssue = true
                            )
                        }
                    }
                }

                // Step 2: ETag changed or not yet cached — fetch full ruleset with conditional If-None-Match
                val getBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/json")
                if (savedEtag.isNotEmpty() && cachedPair != null) {
                    getBuilder.header("If-None-Match", savedEtag)
                }

                resolvedOkHttpClient.newCall(getBuilder.build()).execute().use { response ->
                    if (response.code == 304 && cachedPair != null) {
                        val remoteEtag = response.header("ETag")?.trim().orEmpty().ifEmpty { savedEtag }
                        applyPayloadToDatabase(cachedPair.first)
                        settingsDataStore.updateLastCheckedOnly(
                            checkedAtMs = System.currentTimeMillis(),
                            etag = remoteEtag
                        )
                        return@withLock RulesetSyncResult.Success(
                            version = cachedPair.first.version,
                            updatedApps = cachedPair.first.apps.size,
                            fromLocalCache = true,
                            etagUnchanged = true
                        )
                    }

                    if (!response.isSuccessful) {
                        return@withLock applyLocalCacheFallback(
                            prefs = prefs,
                            reason = "Using local ruleset ${prefs.rulesetVersion} (HTTP ${response.code})",
                            isTransientNetworkIssue = response.code >= 500 || response.code == 429
                        )
                    }

                    val body = response.body?.string().orEmpty()
                    val remoteParsed = parseAndValidate(body)
                        ?: return@withLock applyLocalCacheFallback(
                            prefs = prefs,
                            reason = "Remote ruleset format invalid; kept local ${prefs.rulesetVersion}",
                            isTransientNetworkIssue = false
                        )

                    val bundledJson = loadBundledAssetOrFallbackJson()
                    val bundledParsed = parseAndValidate(bundledJson)
                    val currentLocalVersion = bundledParsed?.version ?: prefs.rulesetVersion

                    // Guard against downgrading newer bundled rules if the remote Gist holds an older revision
                    if (compareVersions(remoteParsed.version, currentLocalVersion) < 0) {
                        return@withLock applyLocalCacheFallback(
                            prefs = prefs,
                            reason = "Local ruleset $currentLocalVersion is newer than remote ${remoteParsed.version}",
                            isTransientNetworkIssue = false
                        )
                    }

                    val responseEtag = response.header("ETag")?.trim().orEmpty()
                    writeLocalCacheFile(body)
                    applyPayloadToDatabase(remoteParsed)
                    settingsDataStore.saveFetchedRuleset(
                        version = remoteParsed.version,
                        rawJson = body,
                        checkedAtMs = System.currentTimeMillis(),
                        etag = responseEtag
                    )
                    RulesetSyncResult.Success(
                        version = remoteParsed.version,
                        updatedApps = remoteParsed.apps.size,
                        fromLocalCache = false
                    )
                }
            } catch (e: IOException) {
                applyLocalCacheFallback(
                    prefs = prefs,
                    reason = "Offline (${e.javaClass.simpleName}) \u00B7 using cached ruleset ${prefs.rulesetVersion}",
                    isTransientNetworkIssue = true
                )
            } catch (e: Exception) {
                applyLocalCacheFallback(
                    prefs = prefs,
                    reason = "Ruleset check failed: ${e.message ?: "unknown error"}",
                    isTransientNetworkIssue = true
                )
            }
        }
    }

    suspend fun revertToHardcodedRuleset(reason: String): RulesetSyncResult =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val hardcodedJson = AppPreferences.BUNDLED_RULESET_JSON
                val parsed = parseAndValidate(hardcodedJson)
                    ?: return@withLock RulesetSyncResult.Error("Hardcoded ruleset invalid: $reason")
                writeLocalCacheFile(hardcodedJson)
                applyPayloadToDatabase(parsed)
                settingsDataStore.saveFetchedRuleset(
                    version = parsed.version,
                    rawJson = hardcodedJson,
                    checkedAtMs = System.currentTimeMillis(),
                    etag = ""
                )
                RulesetSyncResult.Success(
                    version = parsed.version,
                    updatedApps = parsed.apps.size,
                    fromLocalCache = true,
                    networkErrorReason = reason,
                    revertedToHardcoded = true
                )
            }
        }

    private fun readValidCachedPayload(prefs: AppPreferences): Pair<RemoteRulesetPayload, String>? {
        val candidateJson = when {
            localCacheFile.exists() -> runCatching { localCacheFile.readText() }.getOrDefault("")
            else -> ""
        }.ifBlank { prefs.cachedRulesetJson }
        val parsed = parseAndValidate(candidateJson) ?: return null
        return parsed to candidateJson
    }

    private fun normalizeEtag(etag: String): String =
        etag.trim().removePrefix("W/").removePrefix("w/").trim('"')

    private suspend fun applyLocalCacheFallback(
        prefs: AppPreferences,
        reason: String,
        isTransientNetworkIssue: Boolean
    ): RulesetSyncResult {
        val cachedJson = when {
            localCacheFile.exists() -> runCatching { localCacheFile.readText() }.getOrDefault("")
            else -> ""
        }.ifBlank {
            prefs.cachedRulesetJson.ifBlank { loadBundledAssetOrFallbackJson() }
        }

        val parsed = parseAndValidate(cachedJson)
        return if (parsed != null) {
            writeLocalCacheFile(cachedJson)
            applyPayloadToDatabase(parsed)
            settingsDataStore.saveFetchedRuleset(
                version = parsed.version,
                rawJson = cachedJson,
                checkedAtMs = System.currentTimeMillis()
            )
            RulesetSyncResult.Success(
                version = parsed.version,
                updatedApps = parsed.apps.size,
                fromLocalCache = true,
                networkErrorReason = if (isTransientNetworkIssue) reason else null
            )
        } else {
            // If cached JSON was corrupt or unreadable, immediately restore the hardcoded fallback
            val hardcodedJson = AppPreferences.BUNDLED_RULESET_JSON
            val hardcodedParsed = parseAndValidate(hardcodedJson)
            if (hardcodedParsed != null) {
                writeLocalCacheFile(hardcodedJson)
                applyPayloadToDatabase(hardcodedParsed)
                settingsDataStore.saveFetchedRuleset(
                    version = hardcodedParsed.version,
                    rawJson = hardcodedJson,
                    checkedAtMs = System.currentTimeMillis(),
                    etag = ""
                )
                RulesetSyncResult.Success(
                    version = hardcodedParsed.version,
                    updatedApps = hardcodedParsed.apps.size,
                    fromLocalCache = true,
                    networkErrorReason = reason,
                    revertedToHardcoded = true
                )
            } else {
                settingsDataStore.updateLastCheckedOnly(System.currentTimeMillis())
                RulesetSyncResult.Error(reason)
            }
        }
    }

    fun loadBundledAssetOrFallbackJson(): String {
        return try {
            context.assets.open("ruleset.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            AppPreferences.BUNDLED_RULESET_JSON
        }
    }

    private fun writeLocalCacheFile(rawJson: String) {
        try {
            localCacheFile.writeText(rawJson)
        } catch (_: Exception) {
        }
    }

    fun parseAndValidate(rawJson: String): RemoteRulesetPayload? {
        return try {
            val payload = adapter.fromJson(rawJson) ?: return null
            if (payload.version.isBlank() || payload.apps.isEmpty()) return null
            if (payload.apps.any { it.packageName.isBlank() || it.viewIds.isEmpty() }) return null
            payload
        } catch (_: Exception) {
            null
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val partsB = b.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return a.compareTo(b)
    }

    private suspend fun applyPayloadToDatabase(payload: RemoteRulesetPayload) {
        val existingByPkg = dao.getAllRulesSnapshot().associateBy { it.packageName }
        val updated = payload.apps.map { remote ->
            val prev = existingByPkg[remote.packageName]
            TargetAppRuleEntity(
                packageName = remote.packageName,
                displayName = remote.displayName.ifBlank { prev?.displayName ?: remote.packageName },
                feedLabel = remote.feedLabel,
                enabled = prev?.enabled ?: true,
                interventionMode = prev?.interventionMode ?: InterventionMode.OVERLAY,
                cooldownMs = prev?.cooldownMs ?: 800,
                homeTabLabel = remote.homeTabLabel.ifBlank { "Home" },
                viewIdsRaw = remote.viewIds.joinToString("|"),
                tabFallbackLabel = remote.tabContentDescription,
                snoozeUntilMs = prev?.snoozeUntilMs ?: 0L,
                allowOnceUntilMs = prev?.allowOnceUntilMs ?: 0L,
                isBuiltIn = prev?.isBuiltIn ?: (remote.packageName in FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES),
                matchedSelectorCount = remote.viewIds.size,
                totalSelectorCount = remote.viewIds.size,
                isOutdated = false
            )
        }
        dao.upsertRules(updated)
    }

    companion object {
        fun isTrustedHttpsUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            val hostPart = trimmed.substringAfter("://").substringBefore('/').trim()
            return hostPart.isNotBlank() && '.' in hostPart
        }
    }
}
