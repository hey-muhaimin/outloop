package com.example.service

import android.content.Context
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AccessibilityEventLogger(private val context: Context) {

    private val fileMutex = Mutex()
    private val logFile: File
        get() = File(context.filesDir, LOG_FILE_NAME)

    private val _logVersion = MutableStateFlow(0L)
    val logVersion: StateFlow<Long> = _logVersion.asStateFlow()

    data class CapturedTreeSnapshot(
        val rootPackage: String,
        val visibleViewIds: List<String>,
        val selectedTabs: List<String>
    )

    fun captureSnapshot(rootNode: AccessibilityNodeInfo?): CapturedTreeSnapshot {
        // Strict zero-data policy: never traverse or capture screen nodes or view IDs.
        return CapturedTreeSnapshot("redacted", emptyList(), emptyList())
    }

    suspend fun logEvent(
        packageName: String,
        eventType: Int,
        eventClassName: String?,
        rootNode: AccessibilityNodeInfo?,
        matchedSelector: String?,
        outcome: String
    ) {
        // Strict zero-data policy: accessibility events are never logged to disk.
    }

    suspend fun logCapturedSnapshot(
        packageName: String,
        eventType: Int,
        eventClassName: String?,
        snapshot: CapturedTreeSnapshot,
        matchedSelector: String?,
        outcome: String
    ) {
        // Strict zero-data policy: no-op.
    }

    suspend fun readTail(maxLines: Int = 120): String = ""

    suspend fun getFileSizeBytes(): Long = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                if (logFile.exists()) {
                    logFile.delete()
                }
            }
            0L
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                if (logFile.exists()) {
                    logFile.delete()
                }
                _logVersion.value = System.currentTimeMillis()
            }
        }
    }

    fun getFile(): File = logFile

    fun getShareUri(): Uri? = null

    companion object {
        const val LOG_FILE_NAME = "accessibility_events_log.txt"
    }
}
