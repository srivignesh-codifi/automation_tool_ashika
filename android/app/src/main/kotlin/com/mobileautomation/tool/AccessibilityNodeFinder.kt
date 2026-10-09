package com.mobileautomation.tool

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Locates nodes in the target application's accessibility tree.
 *
 * Locator priority, highest first:
 *
 *  1. Accessibility view id (`viewIdResourceName`)
 *  2. Content description
 *  3. Flutter `Semantics(identifier:)`
 *  4. Visible text
 *  5. Node class name / hierarchy
 *  6. Screen coordinates — last resort only, and always derived from the
 *     resolved node's own bounds (see [AutomationEngine.tapNode]). This tool
 *     never uses hard coded screen coordinates.
 *
 * On a Flutter surface (1) and (3) are the *same* field: the Flutter engine
 * publishes `Semantics(identifier:)` through
 * `AccessibilityNodeInfo.setViewIdResourceName`, unprefixed. [byIdentifier]
 * therefore covers both, and also accepts the `package:id/name` form that real
 * native views use, so the same locator works if a screen is ever rewritten in
 * native Android.
 *
 * Nothing in this file reads a node's text for reporting purposes. Node text on
 * a login screen can contain the credential the tool just typed, so only
 * [textLength] (a length, never a value) and [collectIdentifiers] (static
 * identifier strings) are exposed for diagnostics.
 */
object AccessibilityNodeFinder {

    /** Walks the tree breadth first, applying [predicate]. Returns the first hit. */
    private fun search(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        var scanned = 0
        // Each entry is (node, depth).
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (++scanned > AutomationConfig.MAX_NODES_PER_SCAN) return null
            if (predicate(node)) return node
            if (depth >= AutomationConfig.MAX_TREE_DEPTH) continue
            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                }
                if (child != null) queue.addLast(child to depth + 1)
            }
        }
        return null
    }

    private fun matchesIdentifier(node: AccessibilityNodeInfo, identifier: String): Boolean {
        val actual = node.viewIdResourceName ?: return false
        if (actual.isEmpty()) return false
        return actual == identifier ||
            actual.endsWith(":id/$identifier") ||
            actual.endsWith("/$identifier")
    }

    /** Priority 1 + 3: accessibility view id / Flutter semantics identifier. */
    fun byIdentifier(root: AccessibilityNodeInfo?, identifier: String): AccessibilityNodeInfo? {
        // Fast path. Flutter's AccessibilityBridge does not implement
        // findAccessibilityNodeInfosByViewId for its virtual node tree, so this
        // returns nothing on a Flutter surface — hence the manual walk below.
        try {
            val direct = root?.findAccessibilityNodeInfosByViewId(identifier)
            if (direct != null && direct.isNotEmpty()) return direct[0]
        } catch (_: Throwable) {
            // Not supported by this provider; fall through.
        }
        return search(root) { matchesIdentifier(it, identifier) }
    }

    /** Priority 2: content description, exact then case insensitive. */
    fun byContentDescription(
        root: AccessibilityNodeInfo?,
        description: String,
    ): AccessibilityNodeInfo? {
        return search(root) { it.contentDescription?.toString() == description }
            ?: search(root) { it.contentDescription?.toString().equals(description, true) }
    }

    /** Priority 4: visible text, exact match first, then case insensitive equality. */
    fun byText(root: AccessibilityNodeInfo?, candidates: List<String>): AccessibilityNodeInfo? {
        for (candidate in candidates) {
            val exact = search(root) { node ->
                node.text?.toString() == candidate || node.contentDescription?.toString() == candidate
            }
            if (exact != null) return exact
        }
        for (candidate in candidates) {
            val loose = search(root) { node ->
                node.text?.toString().equals(candidate, true) ||
                    node.contentDescription?.toString().equals(candidate, true)
            }
            if (loose != null) return loose
        }
        return null
    }

    /** Priority 5: node class name. */
    fun byClassName(root: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? =
        search(root) { it.className?.toString() == className }

    // ── Action resolution ────────────────────────────────────────────────────
    //
    // A `Semantics(identifier: ...)` wrapper is its own node in the Flutter
    // semantics tree; the node that actually carries the tap or the text entry
    // action is usually a descendant (an InkWell, an EditableText) and can
    // occasionally be an ancestor if Flutter merged the annotation upwards. So
    // an identifier hit is treated as an anchor, and the actionable node is
    // resolved from it: self, then descendants, then a few ancestors.

    private fun supportsAction(node: AccessibilityNodeInfo, action: Int): Boolean =
        node.actionList.any { it.id == action }

    private fun isEditableLike(node: AccessibilityNodeInfo): Boolean =
        supportsAction(node, AccessibilityNodeInfo.ACTION_SET_TEXT) ||
            node.isEditable ||
            node.className?.toString()?.contains("EditText") == true

    private fun isClickableLike(node: AccessibilityNodeInfo): Boolean =
        node.isClickable && supportsAction(node, AccessibilityNodeInfo.ACTION_CLICK)

    private fun resolve(
        anchor: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(anchor)) return anchor
        val descendant = search(anchor, predicate)
        if (descendant != null) return descendant
        var parent = try {
            anchor.parent
        } catch (_: Throwable) {
            null
        }
        var hops = 0
        while (parent != null && hops < 4) {
            if (predicate(parent)) return parent
            parent = try {
                parent.parent
            } catch (_: Throwable) {
                null
            }
            hops++
        }
        return null
    }

    /** The node that can accept ACTION_SET_TEXT for this anchor, if any. */
    fun resolveEditable(anchor: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Prefer a node that genuinely advertises SET_TEXT over one that merely
        // looks like a text field: pin_code_fields hides its real TextField
        // behind an AbsorbPointer, which strips the action from that node.
        return resolve(anchor) { supportsAction(it, AccessibilityNodeInfo.ACTION_SET_TEXT) }
            ?: resolve(anchor) { isEditableLike(it) }
    }

    /** The node that can accept ACTION_CLICK for this anchor, if any. */
    fun resolveClickable(anchor: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        resolve(anchor) { isClickableLike(it) }

    // ── Diagnostics ─────────────────────────────────────────────────────────

    /**
     * The accessibility identifiers currently present in the tree. Used to make
     * a timeout message actionable. Identifiers are static strings authored in
     * the target app's source, so this is safe to put in a report; node *text*
     * is never collected.
     */
    fun collectIdentifiers(root: AccessibilityNodeInfo?, limit: Int = 25): List<String> {
        if (root == null) return emptyList()
        val found = LinkedHashSet<String>()
        var scanned = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty() && found.size < limit) {
            val (node, depth) = queue.removeFirst()
            if (++scanned > AutomationConfig.MAX_NODES_PER_SCAN) break
            node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let { found.add(it) }
            if (depth >= AutomationConfig.MAX_TREE_DEPTH) continue
            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                }
                if (child != null) queue.addLast(child to depth + 1)
            }
        }
        return found.toList()
    }

    /**
     * Length of the text currently held by a node, or null when the node
     * exposes no text. Only ever the length — the value is deliberately not
     * returned so a credential cannot leak into a log or a report.
     */
    fun textLength(node: AccessibilityNodeInfo?): Int? = node?.text?.length
}
