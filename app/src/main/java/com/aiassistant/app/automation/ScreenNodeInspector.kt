package com.aiassistant.app.automation

import android.view.accessibility.AccessibilityNodeInfo

/** Read-only inspection of the live accessibility tree. Prefers text/description matches over coordinates. */
object ScreenNodeInspector {
    sealed interface Match {
        data class Found(val node: AccessibilityNodeInfo, val label: String) : Match
        data class Ambiguous(val labels: List<String>) : Match
        data object NotFound : Match
    }

    private fun norm(s: CharSequence?): String =
        s?.toString()?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")?.trim()?.replace(Regex("\\s+"), " ") ?: ""

    private fun labelsOf(n: AccessibilityNodeInfo): List<String> =
        listOf(n.text, n.contentDescription, n.hintText).map { norm(it) }.filter { it.isNotEmpty() }

    private fun displayLabel(n: AccessibilityNodeInfo): String =
        (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.take(40) ?: ""

    private fun score(label: String, q: String): Int = when {
        label == q -> 100
        label.startsWith(q) -> 80
        label.contains(q) -> 60
        label.length >= 3 && q.contains(label) -> 40
        else -> 0
    }

    private fun traverse(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || count++ > 3000) return
            visit(n)
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, depth + 1)
            }
        }
        walk(root, 0)
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo = n
        repeat(6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent ?: return null
        }
        return null
    }

    /** Finds ONE confidently-identified clickable element, or reports not-found / ambiguous. Never guesses. */
    fun findClickable(root: AccessibilityNodeInfo, query: String): Match {
        val q = norm(query)
        if (q.isEmpty()) return Match.NotFound
        val hits = ArrayList<Triple<Int, AccessibilityNodeInfo, String>>()
        traverse(root) { n ->
            if (!n.isVisibleToUser) return@traverse
            val best = labelsOf(n).maxOfOrNull { score(it, q) } ?: 0
            if (best > 0) clickableAncestor(n)?.let { hits += Triple(best, it, displayLabel(n)) }
        }
        if (hits.isEmpty()) return Match.NotFound
        val top = hits.maxOf { it.first }
        val tops = hits.filter { it.first == top }.distinctBy { it.second }
        return if (tops.size == 1) Match.Found(tops[0].second, tops[0].third)
        else Match.Ambiguous(tops.map { it.third }.take(5))
    }

    /** Returns the intended text field only if it is unambiguous (focused, label match, or the only one). */
    fun findEditable(root: AccessibilityNodeInfo, label: String?): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        val q = label?.let { norm(it) }?.takeIf { it.isNotEmpty() }
        if (q == null && focused != null) return focused

        val editables = ArrayList<AccessibilityNodeInfo>()
        traverse(root) { n -> if (n.isEditable && n.isVisibleToUser) editables += n }
        if (q != null) {
            editables.firstOrNull { n ->
                (labelsOf(n) + norm(n.viewIdResourceName?.substringAfterLast('/')))
                    .any { it.contains(q) }
            }?.let { return it }
            if (focused != null) return focused
        }
        return editables.singleOrNull()
    }

    fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val r = android.graphics.Rect()
        traverse(root) { n ->
            if (n.isScrollable && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area > bestArea) { bestArea = area; best = n }
            }
        }
        return best
    }
}
