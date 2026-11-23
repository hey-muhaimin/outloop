package com.example.service

import com.example.data.repository.InterventionRepository

/**
 * Tracks active foreground sessions for controlled apps using Accessibility window transitions
 * and produces per-date duration slices without requiring PACKAGE_USAGE_STATS permission.
 */
class AppSessionTimeTracker {

    data class TrackedUsageSlice(
        val packageName: String,
        val dateKey: String,
        val deltaSeconds: Long
    )

    private var activePkg: String? = null
    private var sessionAnchorElapsedMs: Long = 0L

    @Synchronized
    fun activePackage(): String? = activePkg

    @Synchronized
    fun onPackageEntered(
        packageName: String,
        nowElapsedMs: Long,
        nowWallMs: Long
    ): List<TrackedUsageSlice> {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return emptyList()
        val current = activePkg
        if (current == cleanPkg) {
            return emptyList()
        }
        val flushed = if (current != null) {
            flushInternal(nowElapsedMs = nowElapsedMs, nowWallMs = nowWallMs)
        } else {
            emptyList()
        }
        activePkg = cleanPkg
        sessionAnchorElapsedMs = nowElapsedMs
        return flushed
    }

    @Synchronized
    fun onPackageExited(
        expectedPackage: String? = null,
        nowElapsedMs: Long,
        nowWallMs: Long
    ): List<TrackedUsageSlice> {
        val current = activePkg ?: return emptyList()
        if (expectedPackage != null && expectedPackage != current) {
            return emptyList()
        }
        val flushed = flushInternal(nowElapsedMs = nowElapsedMs, nowWallMs = nowWallMs)
        activePkg = null
        sessionAnchorElapsedMs = 0L
        return flushed
    }

    @Synchronized
    fun flushActiveSession(
        nowElapsedMs: Long,
        nowWallMs: Long
    ): List<TrackedUsageSlice> {
        if (activePkg == null) return emptyList()
        return flushInternal(nowElapsedMs = nowElapsedMs, nowWallMs = nowWallMs)
    }

    @Synchronized
    fun getPendingSeconds(
        packageName: String,
        nowElapsedMs: Long
    ): Long {
        if (activePkg != packageName) return 0L
        return ((nowElapsedMs - sessionAnchorElapsedMs).coerceAtLeast(0L)) / 1000L
    }

    @Synchronized
    fun reset() {
        activePkg = null
        sessionAnchorElapsedMs = 0L
    }

    private fun flushInternal(
        nowElapsedMs: Long,
        nowWallMs: Long
    ): List<TrackedUsageSlice> {
        val pkg = activePkg ?: return emptyList()
        val rawElapsedMs = (nowElapsedMs - sessionAnchorElapsedMs).coerceAtLeast(0L)
        val wholeSeconds = rawElapsedMs / 1000L
        if (wholeSeconds <= 0L) return emptyList()

        val committedMs = wholeSeconds * 1000L
        sessionAnchorElapsedMs += committedMs

        val endWallMs = nowWallMs
        val startWallMs = (endWallMs - committedMs).coerceAtLeast(0L)
        val startDateKey = InterventionRepository.dateKeyForMs(startWallMs)
        val endDateKey = InterventionRepository.dateKeyForMs(endWallMs)

        if (startDateKey == endDateKey) {
            return listOf(
                TrackedUsageSlice(
                    packageName = pkg,
                    dateKey = endDateKey,
                    deltaSeconds = wholeSeconds
                )
            )
        }

        val todayMidnightMs = InterventionRepository.startOfTodayMs(endWallMs)
        val postMidnightSeconds = ((endWallMs - todayMidnightMs).coerceAtLeast(0L) / 1000L)
            .coerceIn(0L, wholeSeconds)
        val preMidnightSeconds = (wholeSeconds - postMidnightSeconds).coerceAtLeast(0L)

        val slices = mutableListOf<TrackedUsageSlice>()
        if (preMidnightSeconds > 0L) {
            slices.add(
                TrackedUsageSlice(
                    packageName = pkg,
                    dateKey = startDateKey,
                    deltaSeconds = preMidnightSeconds
                )
            )
        }
        if (postMidnightSeconds > 0L) {
            slices.add(
                TrackedUsageSlice(
                    packageName = pkg,
                    dateKey = endDateKey,
                    deltaSeconds = postMidnightSeconds
                )
            )
        }
        return slices
    }
}
