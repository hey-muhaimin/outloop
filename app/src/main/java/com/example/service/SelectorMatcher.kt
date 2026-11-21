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
            val matches = root.findAccessibilityNodeInfosByViewId(viewId)
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

    fun countVisibleViewIdMatches(
        root: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity
    ): Int {
        return rule.viewIds.count { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }
    }

    fun isTabSelected(root: AccessibilityNodeInfo, label: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByText(label)
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
        if (candidate.equals(label, ignoreCase = true)) return true
        val prefix = candidate.split(',', '.', '\n', '\u00B7').firstOrNull()?.trim()
        return prefix.equals(label, ignoreCase = true)
    }

    fun findHomeTabNode(root: AccessibilityNodeInfo, homeTabLabel: String): AccessibilityNodeInfo? {
        val label = homeTabLabel.ifBlank { "Home" }
        val candidates = root.findAccessibilityNodeInfosByText(label)
        for (candidate in candidates) {
            if (!candidate.isVisibleToUser) continue
            if (matchesTabLabel(candidate, label)) {
                val clickable = findClickableAncestorOrSelf(candidate)
                if (clickable != null) return clickable
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && matchesTabLabel(node, label)) {
                val clickable = findClickableAncestorOrSelf(node)
                if (clickable != null) return clickable
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
}
