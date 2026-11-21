package com.example.service

import android.content.Context
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        if (rootNode == null) {
            return CapturedTreeSnapshot("null", emptyList(), emptyList())
        }
        val rootPkg = runCatching { rootNode.packageName?.toString() }.getOrNull() ?: "null"
        val signals = runCatching { extractStructuralSignals(rootNode) }
            .getOrDefault(StructuralSignals(emptyList(), emptyList()))
        return CapturedTreeSnapshot(
            rootPackage = rootPkg,
            visibleViewIds = signals.visibleViewIds,
            selectedTabs = signals.selectedTabs
        )
    }

    suspend fun logEvent(
        packageName: String,
        eventType: Int,
        eventClassName: String?,
        rootNode: AccessibilityNodeInfo?,
        matchedSelector: String?,
        outcome: String
    ) {
        val snapshot = captureSnapshot(rootNode)
        logCapturedSnapshot(
            packageName = packageName,
            eventType = eventType,
            eventClassName = eventClassName,
            snapshot = snapshot,
            matchedSelector = matchedSelector,
            outcome = outcome
        )
    }

    suspend fun logCapturedSnapshot(
        packageName: String,
        eventType: Int,
        eventClassName: String?,
        snapshot: CapturedTreeSnapshot,
        matchedSelector: String?,
        outcome: String
    ) = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val eventLabel = eventTypeToString(eventType)
        val cls = eventClassName?.substringAfterLast('.')?.ifBlank { "Unknown" } ?: "Unknown"

        val entry = buildString {
            append('[').append(timestamp).append("] ")
            append("pkg=").append(packageName)
            append(" event=").append(eventLabel)
            append(" class=").append(cls)
            append(" rootPkg=").append(snapshot.rootPackage)
            append(" match=").append(matchedSelector ?: "NONE")
            append(" outcome=").append(outcome)
            if (snapshot.selectedTabs.isNotEmpty()) {
                append("\n  selectedTabs=").append(snapshot.selectedTabs.joinToString(" | "))
            }
            if (snapshot.visibleViewIds.isNotEmpty()) {
                append("\n  visibleIds=").append(snapshot.visibleViewIds.joinToString(", "))
            }
            append('\n')
        }

        fileMutex.withLock {
            runCatching {
                rotateIfOversizedLocked()
                logFile.appendText(entry)
                _logVersion.value = System.currentTimeMillis()
            }
        }
    }

    suspend fun readTail(maxLines: Int = 120): String = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!logFile.exists()) return@withLock ""
            runCatching {
                val lines = logFile.readLines()
                if (lines.size <= maxLines) {
                    lines.joinToString("\n")
                } else {
                    lines.takeLast(maxLines).joinToString("\n")
                }
            }.getOrDefault("")
        }
    }

    suspend fun getFileSizeBytes(): Long = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (logFile.exists()) logFile.length() else 0L
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                if (logFile.exists()) {
                    logFile.writeText("")
                }
                _logVersion.value = System.currentTimeMillis()
            }
        }
    }

    fun getFile(): File = logFile

    fun getShareUri(): Uri? {
        val file = logFile
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun rotateIfOversizedLocked() {
        if (!logFile.exists()) return
        if (logFile.length() < MAX_LOG_BYTES) return
        val lines = logFile.readLines()
        val kept = lines.takeLast((lines.size / 2).coerceAtLeast(50))
        logFile.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private data class StructuralSignals(
        val visibleViewIds: List<String>,
        val selectedTabs: List<String>
    )

    private fun extractStructuralSignals(root: AccessibilityNodeInfo): StructuralSignals {
        val viewIds = LinkedHashSet<String>()
        val selectedTabs = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 450 && (viewIds.size < 16 || selectedTabs.size < 6)) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val id = node.viewIdResourceName
                if (!id.isNullOrBlank() && viewIds.size < 16) {
                    viewIds.add(id.substringAfter(":id/", id))
                }
                if (node.isSelected && selectedTabs.size < 6) {
                    val desc = node.contentDescription?.toString()?.take(32)?.trim()
                    val text = node.text?.toString()?.take(32)?.trim()
                    val label = listOfNotNull(
                        desc?.takeIf { it.isNotBlank() }?.let { "desc=\"$it\"" },
                        text?.takeIf { it.isNotBlank() }?.let { "text=\"$it\"" }
                    ).joinToString(" ")
                    if (label.isNotBlank()) {
                        selectedTabs.add(label)
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return StructuralSignals(viewIds.toList(), selectedTabs.toList())
    }

    private fun eventTypeToString(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
        else -> "TYPE_$eventType"
    }

    companion object {
        const val LOG_FILE_NAME = "accessibility_events_log.txt"
        private const val MAX_LOG_BYTES = 512L * 1024L
    }
}
