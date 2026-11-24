package com.example.service

import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InspectedNode
import com.example.data.model.LiveTreeSnapshot

object SelectorMatcher {

    fun matchesTargetPackage(
        node: AccessibilityNodeInfo?,
        targetPackage: String
    ): Boolean {
        return node?.packageName?.toString() == targetPackage
    }

    fun selectTargetWindowRoot(
        targetPackage: String,
        activeRoot: AccessibilityNodeInfo?,
        windowRoots: List<AccessibilityNodeInfo?>
    ): AccessibilityNodeInfo? {
        if (matchesTargetPackage(activeRoot, targetPackage)) {
            return activeRoot
        }
        return windowRoots.firstOrNull { matchesTargetPackage(it, targetPackage) }
    }

    fun findMatchingSelector(
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity
    ): String? {
        for (viewId in rule.viewIds) {
            val matches = safeFindByViewId(root, viewId)
            if (matches.any { it.isVisibleToUser }) {
                return viewId
            }
        }
        if (rule.tabFallbackLabel.isNotBlank()) {
            if (isTabSelected(root, rule.tabFallbackLabel)) {
                return "tab:\"${rule.tabFallbackLabel}\""
            }
        }
        return null
    }

    /**
     * Evaluates a TYPE_VIEW_SCROLLED source subtree first (for immediate swipe/scroll interception
     * inside short-form feeds), then falls back to the target window root selectors.
     */
    fun findMatchingScrollSelector(
        sourceNode: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity
    ): String? {
        if (sourceNode != null && matchesTargetPackage(sourceNode, rule.packageName)) {
            val targetIds = rule.viewIds.toSet()
            if (targetIds.isNotEmpty()) {
                var current: AccessibilityNodeInfo? = sourceNode
                var hops = 0
                while (current != null && hops <= 4) {
                    val viewId = current.viewIdResourceName
                    if (viewId != null && viewId in targetIds && current.isVisibleToUser) {
                        return viewId
                    }
                    current = current.parent
                    hops++
                }
                for (viewId in rule.viewIds) {
                    val matches = safeFindByViewId(sourceNode, viewId)
                    if (matches.any { it.isVisibleToUser }) {
                        return viewId
                    }
                }
            }
        }
        return findMatchingSelector(root, rule)
    }

    /**
     * Core short-form feed detector evaluating explicit layout IDs, active tab fallback,
     * and vertical-scrolling short-form container patterns strictly within [rule.packageName]
     * without reading or logging any user content.
     */
    fun detectShortFormFeedPattern(
        eventType: Int,
        sourceNode: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity
    ): String? {
        val explicitMatch = if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            findMatchingScrollSelector(sourceNode, root, rule)
        } else {
            findMatchingSelector(root, rule)
        }
        if (explicitMatch != null) return explicitMatch

        val rootBounds = android.graphics.Rect().also { root.getBoundsInScreen(it) }

        if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            sourceNode != null &&
            matchesTargetPackage(sourceNode, rule.packageName) &&
            isShortFormVerticalFeedContainer(sourceNode, rootBounds)
        ) {
            return PATTERN_VERTICAL_FEED_CONTAINER
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 300) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser &&
                matchesTargetPackage(node, rule.packageName) &&
                isShortFormVerticalFeedContainer(node, rootBounds)
            ) {
                return PATTERN_VERTICAL_FEED_CONTAINER
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun isShortFormVerticalFeedContainer(
        node: AccessibilityNodeInfo,
        rootBounds: android.graphics.Rect
    ): Boolean {
        if (!node.isVisibleToUser) return false
        val simpleClass = node.className?.toString()?.substringAfterLast('.').orEmpty()
        val isPagerOrScrollable = node.isScrollable || simpleClass in VERTICAL_FEED_CONTAINER_CLASSES
        if (!isPagerOrScrollable) return false

        val idToken = node.viewIdResourceName
            ?.substringAfter(":id/", "")
            ?.lowercase(java.util.Locale.US)
            .orEmpty()

        if (idToken.isNotEmpty() && NON_FEED_CONTAINER_HINTS.any { it in idToken }) {
            return false
        }

        val hasShortFormLayoutHint = idToken.isNotEmpty() &&
            SHORT_FORM_LAYOUT_HINTS.any { it in idToken }

        val nodeBounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        val hasMeasuredBounds = nodeBounds.width() > 0 && nodeBounds.height() > 0 &&
            rootBounds.width() > 0 && rootBounds.height() > 0

        if (hasMeasuredBounds) {
            val fillsFeedHeight = nodeBounds.height() >= (rootBounds.height() * 0.55f)
            val fillsFeedWidth = nodeBounds.width() >= (rootBounds.width() * 0.70f)
            if (!fillsFeedHeight || !fillsFeedWidth) {
                return false
            }
            val isFullBleedPager = simpleClass in PAGER_CONTAINER_CLASSES &&
                nodeBounds.height() >= (rootBounds.height() * 0.72f) &&
                nodeBounds.width() >= (rootBounds.width() * 0.85f) &&
                node.isScrollable
            return hasShortFormLayoutHint || isFullBleedPager
        }

        return hasShortFormLayoutHint
    }

    fun countVisibleViewIdMatches(
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity
    ): Int {
        return rule.viewIds.count { id ->
            safeFindByViewId(root, id).any { it.isVisibleToUser }
        }
    }

    fun isTabSelected(root: AccessibilityNodeInfo, label: String): Boolean {
        val matches = safeFindByText(root, label)
        if (matches.any { node ->
                node.isVisibleToUser &&
                    isNodeOrParentSelected(node) &&
                    matchesTabLabel(node, label)
            }
        ) {
            return true
        }
        // Bounded BFS fallback for tabs that expose contentDescription without text
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser &&
                isNodeOrParentSelected(node) &&
                matchesTabLabel(node, label)
            ) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun isNodeOrParentSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        var current = node.parent
        var hops = 0
        while (current != null && hops < 2) {
            if (current.isSelected) return true
            current = current.parent
            hops++
        }
        return false
    }

    private fun matchesTabLabel(node: AccessibilityNodeInfo, label: String): Boolean {
        val desc = node.contentDescription?.toString()?.trim()
        val text = node.text?.toString()?.trim()
        return matchesLabelString(desc, label) || matchesLabelString(text, label)
    }

    private fun matchesLabelString(candidate: String?, label: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        val trimmed = candidate.trim()
        if (trimmed.equals(label, ignoreCase = true)) return true
        val prefix = trimmed.split(',', '.', '\n', '\u00B7', ':', '(').firstOrNull()?.trim()
        if (prefix.equals(label, ignoreCase = true)) return true
        val lower = trimmed.lowercase()
        val target = label.lowercase()
        return lower == "$target tab" ||
            lower == "go to $target" ||
            lower == "navigate to $target" ||
            lower.startsWith("$target tab,") ||
            lower.startsWith("$target,")
    }

    private fun homeTabAliases(packageName: String?, homeTabLabel: String): List<String> {
        val primary = homeTabLabel.ifBlank { "Home" }
        val aliases = linkedSetOf(primary)
        when (packageName) {
            "com.facebook.katana" -> {
                aliases.add("Home")
                aliases.add("News Feed")
                aliases.add("Feed")
            }
            "com.google.android.youtube",
            "com.instagram.android",
            "com.reddit.frontpage" -> aliases.add("Home")
        }
        return aliases.toList()
    }

    private fun knownHomeTabViewIds(packageName: String?): List<String> = when (packageName) {
        "com.instagram.android" -> listOf("com.instagram.android:id/feed_tab")
        else -> emptyList()
    }

    fun findHomeTabNode(
        root: AccessibilityNodeInfo,
        homeTabLabel: String,
        packageName: String? = null,
        additionalRoots: List<AccessibilityNodeInfo?> = emptyList()
    ): AccessibilityNodeInfo? {
        val searchRoots = buildList {
            if (packageName == null || matchesTargetPackage(root, packageName)) {
                add(root)
            }
            additionalRoots.forEach { extra ->
                if (extra != null &&
                    extra !== root &&
                    (packageName == null || matchesTargetPackage(extra, packageName))
                ) {
                    add(extra)
                }
            }
        }
        if (searchRoots.isEmpty()) return null

        for (viewId in knownHomeTabViewIds(packageName)) {
            for (searchRoot in searchRoots) {
                val idMatches = safeFindByViewId(searchRoot, viewId)
                for (candidate in idMatches) {
                    if (!candidate.isVisibleToUser) continue
                    val clickable = findClickableAncestorOrSelf(candidate)
                    if (clickable != null) return clickable
                }
            }
        }

        val labels = homeTabAliases(packageName, homeTabLabel)
        val primaryLabel = labels.first()
        var bestClickable: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun considerCandidate(candidate: AccessibilityNodeInfo, matchedLabel: String) {
            if (!candidate.isVisibleToUser) return
            if (!matchesTabLabel(candidate, matchedLabel)) return
            val clickable = findClickableAncestorOrSelf(candidate) ?: return
            if (packageName != null && !matchesTargetPackage(clickable, packageName)) return

            val score = scoreHomeTabCandidate(
                node = candidate,
                clickable = clickable,
                isPrimaryLabel = matchedLabel.equals(primaryLabel, ignoreCase = true)
            )
            if (score > bestScore) {
                bestScore = score
                bestClickable = clickable
            }
        }

        for (searchRoot in searchRoots) {
            for (label in labels) {
                val textMatches = safeFindByText(searchRoot, label)
                for (candidate in textMatches) {
                    considerCandidate(candidate, label)
                }
            }

            // Full bounded traversal up to 4000 nodes so deep Shorts/Reels trees still reach bottom/top nav bars
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(searchRoot)
            var visited = 0
            while (queue.isNotEmpty() && visited < 4000) {
                val node = queue.removeFirst()
                visited++
                if (node.isVisibleToUser) {
                    for (label in labels) {
                        if (matchesTabLabel(node, label)) {
                            considerCandidate(node, label)
                            break
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        return bestClickable
    }

    private fun scoreHomeTabCandidate(
        node: AccessibilityNodeInfo,
        clickable: AccessibilityNodeInfo,
        isPrimaryLabel: Boolean
    ): Int {
        var score = if (isPrimaryLabel) 50 else 20
        val descLen = node.contentDescription?.length ?: 0
        val textLen = node.text?.length ?: 0
        if (maxOf(descLen, textLen) > 28) score -= 30

        val ownId = clickable.viewIdResourceName.orEmpty().lowercase()
        val nodeId = node.viewIdResourceName.orEmpty().lowercase()
        val combinedId = "$ownId $nodeId"
        if (combinedId.contains("pivot") ||
            combinedId.contains("tab") ||
            combinedId.contains("nav") ||
            combinedId.contains("bottom") ||
            combinedId.contains("feed_tab") ||
            combinedId.contains("home")
        ) {
            score += 40
        }

        var ancestor: AccessibilityNodeInfo? = clickable.parent
        var hops = 0
        var hasSelectedSibling = false
        var insideNavContainer = false
        while (ancestor != null && hops < 4) {
            val cls = ancestor.className?.toString().orEmpty().lowercase()
            val aid = ancestor.viewIdResourceName.orEmpty().lowercase()
            if (cls.contains("tabwidget") ||
                cls.contains("tablayout") ||
                cls.contains("bottomnavigation") ||
                cls.contains("navigationbar") ||
                cls.contains("horizontalscrollview") ||
                cls.contains("radiogroup") ||
                aid.contains("pivot") ||
                aid.contains("tab") ||
                aid.contains("nav") ||
                aid.contains("bottom_bar")
            ) {
                insideNavContainer = true
            }
            if (ancestor.childCount in 2..8) {
                for (i in 0 until ancestor.childCount) {
                    val sibling = ancestor.getChild(i) ?: continue
                    if (sibling !== clickable && sibling.isSelected) {
                        hasSelectedSibling = true
                        break
                    }
                }
            }
            ancestor = ancestor.parent
            hops++
        }
        if (insideNavContainer) score += 35
        if (hasSelectedSibling) score += 30
        if (clickable.isSelected) score -= 5
        return score
    }

    /**
     * Finds an explicit in-app Back / Navigate-up / Close button inside a full-screen Reels/Shorts viewer
     * so we stay inside the target app rather than risking popping back to Outloop or the launcher.
     */
    fun findInAppBackNode(
        root: AccessibilityNodeInfo,
        packageName: String? = null
    ): AccessibilityNodeInfo? {
        val backLabels = listOf("Navigate up", "Back", "Close")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser &&
                (packageName == null || matchesTargetPackage(node, packageName))
            ) {
                val desc = node.contentDescription?.toString()?.trim()
                val text = node.text?.toString()?.trim()
                val matched = backLabels.any { lbl ->
                    desc.equals(lbl, ignoreCase = true) || text.equals(lbl, ignoreCase = true)
                }
                if (matched) {
                    val clickable = findClickableAncestorOrSelf(node)
                    if (clickable != null && (packageName == null || matchesTargetPackage(clickable, packageName))) {
                        return clickable
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 4) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return if (node.isClickable) node else null
    }

    fun captureSanitizedTree(
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity,
        matchedSelector: String?
    ): LiveTreeSnapshot {
        val collected = mutableListOf<InspectedNode>()
        val targetViewIds = rule.viewIds.toSet()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (collected.size >= 45 || depth > 6) return
            val fullClass = node.className?.toString().orEmpty()
            val simpleClass = fullClass.substringAfterLast('.').ifBlank { "View" }
            val viewId = node.viewIdResourceName
            val desc = node.contentDescription?.toString()?.takeIf {
                it.equals(rule.tabFallbackLabel, ignoreCase = true) ||
                    it.equals(rule.homeTabLabel, ignoreCase = true) ||
                    node.isSelected
            }
            val isMatched = (viewId != null && viewId in targetViewIds) ||
                (node.isSelected && desc != null && desc.equals(rule.tabFallbackLabel, ignoreCase = true))

            val shouldInclude = depth <= 2 || viewId != null || desc != null || node.isSelected || isMatched
            if (shouldInclude) {
                collected.add(
                    InspectedNode(
                        depth = depth,
                        className = simpleClass,
                        viewId = viewId,
                        contentDescription = desc,
                        isSelected = node.isSelected,
                        isMatched = isMatched
                    )
                )
            }
            for (i in 0 until node.childCount) {
                if (collected.size >= 45) break
                node.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }

        traverse(root, 0)
        return LiveTreeSnapshot(
            packageName = rule.packageName,
            appDisplayName = rule.displayName,
            capturedAtMs = System.currentTimeMillis(),
            isDiagnosticSample = false,
            nodes = collected
        )
    }

    /**
     * Pure-Kotlin matcher for evaluating selector rules against an abstracted [LiveTreeSnapshot]
     * in unit tests.
     */
    fun matchSelectorInSnapshot(
        snapshot: LiveTreeSnapshot,
        rule: TargetAppRuleEntity
    ): String? {
        if (!rule.enabled || rule.isCurrentlySnoozed()) return null
        for (viewId in rule.viewIds) {
            if (snapshot.nodes.any { it.viewId == viewId }) {
                return viewId
            }
        }
        if (rule.tabFallbackLabel.isNotBlank()) {
            val matchedTab = snapshot.nodes.any {
                it.isSelected && it.contentDescription.equals(rule.tabFallbackLabel, ignoreCase = true)
            }
            if (matchedTab) return "tab:\"${rule.tabFallbackLabel}\""
        }
        return null
    }

    private fun safeFindByViewId(
        node: AccessibilityNodeInfo,
        viewId: String
    ): List<AccessibilityNodeInfo> {
        return runCatching {
            node.findAccessibilityNodeInfosByViewId(viewId)
        }.getOrDefault(emptyList())
    }

    private fun safeFindByText(
        node: AccessibilityNodeInfo,
        text: String
    ): List<AccessibilityNodeInfo> {
        return runCatching {
            node.findAccessibilityNodeInfosByText(text)
        }.getOrDefault(emptyList())
    }

    const val PATTERN_VERTICAL_FEED_CONTAINER = "pattern:vertical_feed_container"

    private val PAGER_CONTAINER_CLASSES = setOf(
        "ViewPager2",
        "ViewPager"
    )

    private val VERTICAL_FEED_CONTAINER_CLASSES = setOf(
        "ViewPager2",
        "ViewPager",
        "RecyclerView",
        "ListView",
        "NestedScrollView",
        "ScrollView"
    )

    private val SHORT_FORM_LAYOUT_HINTS = setOf(
        "short",
        "shorts",
        "reel",
        "reels",
        "clips",
        "spotlight",
        "opera",
        "watch_feed",
        "video_pager",
        "foryou",
        "fyp",
        "vertical_pager",
        "feed_pager"
    )

    private val NON_FEED_CONTAINER_HINTS = setOf(
        "inbox",
        "direct",
        "thread",
        "chat",
        "message",
        "search",
        "comment",
        "profile",
        "settings",
        "notification",
        "story_tray",
        "tray"
    )
}
