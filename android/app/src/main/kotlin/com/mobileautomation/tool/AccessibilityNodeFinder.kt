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

    /**
     * Every label on a screen, for a failure message: a node's text, or its
     * content description as `desc:...`, with `*` after anything tappable. A
     * button that has only an icon shows up as a bare `desc:...*`, or as `?*`
     * when it has no label at all. Editable and password nodes are skipped.
     * For Settings pages, which hold no secret; never call it on a login screen.
     */
    fun describeLabels(root: AccessibilityNodeInfo?, limit: Int = 40): List<String> {
        if (root == null) return emptyList()
        val out = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || out.size >= limit) return
            if (!node.isEditable && !node.isPassword) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val mark = if (node.isClickable) "*" else ""
                when {
                    text.isNotEmpty() && text.length <= 60 -> out.add(text + mark)
                    desc.isNotEmpty() && desc.length <= 60 -> out.add("desc:$desc$mark")
                    node.isClickable && node.childCount == 0 -> out.add("?*")
                }
            }
            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                } ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /** The first node, breadth first, for which [predicate] holds. */
    fun firstWhere(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? = search(root, predicate)

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

    /** Every node carrying [identifier], in accessibility-tree order. */
    fun allByIdentifier(root: AccessibilityNodeInfo?, identifier: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        search(root) { node ->
            if (matchesIdentifier(node, identifier)) found += node
            false
        }
        return found
    }

    /** Priority 2: content description, exact then case insensitive. */
    fun byContentDescription(
        root: AccessibilityNodeInfo?,
        description: String,
    ): AccessibilityNodeInfo? {
        return search(root) { it.contentDescription?.toString() == description }
            ?: search(root) { it.contentDescription?.toString().equals(description, true) }
    }

    private val WHITESPACE = Regex("\\s+")

    /** A node's label with every run of whitespace (a line break inside "Strategy\nFinder") as one space. */
    fun labelOf(node: AccessibilityNodeInfo): String? =
        (node.text ?: node.contentDescription)?.toString()?.replace(WHITESPACE, " ")?.trim()

    /**
     * A control whose label contains [part] (case-insensitive), for screens whose controls carry no identifier. A node
     * that can be clicked is preferred over a plain label; editable nodes are never returned.
     */
    fun byTextContaining(root: AccessibilityNodeInfo?, part: String): AccessibilityNodeInfo? {
        val wanted = part.lowercase().replace(WHITESPACE, " ")
        fun matches(node: AccessibilityNodeInfo) =
            !node.isEditable && labelOf(node)?.lowercase()?.contains(wanted) == true
        return search(root) { matches(it) && resolveClickable(it) != null } ?: search(root) { matches(it) }
    }

    /**
     * The nodes that are *the heading itself*: a label that contains [part] and is not much longer than it. A merged
     * container (a whole page column, a card row) carries a label that contains every heading inside it and covers the
     * screen, which made the heading's position, and the scroll that was driven by it, wrong (the page went up and down
     * looking for a "heading" that was the whole page). When no short label matches, every match is returned.
     */
    fun headingNodes(root: AccessibilityNodeInfo?, part: String, slack: Int = 30): List<AccessibilityNodeInfo> {
        val all = allByTextContaining(root, part)
        val short = all.filter { (labelOf(it)?.length ?: Int.MAX_VALUE) <= part.length + slack }
        return short.ifEmpty { all }
    }

    /** Every non-editable node whose label contains [part], in tree order. */
    fun allByTextContaining(root: AccessibilityNodeInfo?, part: String): List<AccessibilityNodeInfo> {
        val wanted = part.lowercase().replace(WHITESPACE, " ")
        val found = mutableListOf<AccessibilityNodeInfo>()
        search(root) { node ->
            if (!node.isEditable && labelOf(node)?.lowercase()?.contains(wanted) == true) found += node
            false
        }
        return found
    }

    /**
     * The label lines of every node whose vertical centre lies in [top] until [bottom] (screen pixels), in tree order and
     * without repeats. The tree lists a page's headings apart from the cards under them, so what lies *between* two
     * headings has to be found by position, not by order. Editable fields are skipped.
     */
    fun labelsInBand(
        root: AccessibilityNodeInfo?,
        top: Int,
        bottom: Int,
        limit: Int = 120,
        distinct: Boolean = true,
        maxHeight: Int = Int.MAX_VALUE,
    ): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        search(root) { node ->
            if (out.size >= limit) return@search true
            if (!node.isEditable && !node.isPassword) {
                val b = android.graphics.Rect()
                node.getBoundsInScreen(b)
                // A node taller than [maxHeight] is a page container: its lines have no position of their own.
                if (b.centerY() in top until bottom && b.height() <= maxHeight) {
                    (node.text ?: node.contentDescription)?.toString()?.lineSequence()
                        ?.map { it.trim() }?.filter { it.isNotEmpty() && it.length <= 90 }
                        // [distinct] false keeps repeats: three "INR 0.00" amounts are three amounts, three "NSE" are three cards.
                        ?.forEach { if (!distinct || seen.add(it)) out.add(it) }
                }
            }
            false
        }
        return out
    }

    /** An editable field whose hint, label or text contains [part] (case-insensitive). */
    fun editableLabelled(root: AccessibilityNodeInfo?, part: String): AccessibilityNodeInfo? {
        val wanted = part.lowercase()
        return search(root) { node ->
            isEditableLike(node) && listOfNotNull(
                node.text?.toString(), node.contentDescription?.toString(),
                if (android.os.Build.VERSION.SDK_INT >= 26) node.hintText?.toString() else null,
            ).any { it.lowercase().contains(wanted) }
        }
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

    /**
     * The short, non-editable texts on screen, top to bottom, without
     * duplicates. For failure messages on screens that hold no secret (the
     * watchlist edit screen): it is how a snackbar such as "Market watch name
     * already exists" reaches the report. Editable and password nodes are skipped. A label is read from the
     * node's text or, as Flutter publishes it, its content description, one line at a time.
     */
    fun visibleTexts(root: AccessibilityNodeInfo?, limit: Int = 12): List<String> {
        if (root == null) return emptyList()
        val out = LinkedHashSet<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 60 || out.size >= limit) return
            if (!node.isEditable && !node.isPassword) {
                // Flutter publishes a label as the content description (its `text` is usually null), and merges
                // a card's lines into one label separated by newlines: read both and split the lines.
                (node.text ?: node.contentDescription)?.toString()?.lineSequence()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() && it.length <= 90 }
                    ?.forEach { if (out.size < limit) out.add(it) }
            }
            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                } ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return out.toList()
    }

    /** Priority 5: node class name. */
    fun byClassName(root: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? =
        search(root) { it.className?.toString() == className }

    /**
     * Every piece of text under (and including) each node carrying [identifier]:
     * its own text and description first, then its descendants'. A label can sit
     * on the tagged node or, when Flutter did not merge it upwards, on a child;
     * and a tab strip keeps neighbouring tabs built, so several nodes can share
     * one identifier. Only for static display text such as a scrip count.
     */
    fun labelsOf(root: AccessibilityNodeInfo?, identifier: String): List<String> {
        if (root == null) return emptyList()
        val labels = mutableListOf<String>()
        search(root) { node ->
            if (matchesIdentifier(node, identifier)) {
                (node.text ?: node.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
                search(node) { child ->
                    if (child !== node) {
                        (child.text ?: child.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
                    }
                    false
                }
            }
            false
        }
        return labels
    }

    /** The own text of every node carrying [identifier], top to bottom (not their children's). */
    fun ownLabelsOf(root: AccessibilityNodeInfo?, identifier: String): List<String> {
        if (root == null) return emptyList()
        val labels = mutableListOf<String>()
        search(root) { node ->
            if (matchesIdentifier(node, identifier)) {
                (node.text ?: node.contentDescription)?.toString()?.let(labels::add)
            }
            false
        }
        return labels
    }

    /**
     * One combined label per node carrying [identifier]. This is for static list
     * rows: a Semantics boundary can keep the row's text on descendants rather
     * than on the tagged node itself, so [ownLabelsOf] alone is not sufficient.
     */
    fun groupedLabelsOf(root: AccessibilityNodeInfo?, identifier: String): List<String> =
        allByIdentifier(root, identifier).map { node ->
            val parts = LinkedHashSet<String>()
            search(node) { child ->
                (child.text ?: child.contentDescription)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(parts::add)
                false
            }
            parts.joinToString(" ")
        }

    /**
     * The custom accessibility action whose label contains [labelPart], looked
     * for on [anchor], its descendants, and up to eight ancestors. Flutter
     * publishes a widget's `CustomSemanticsAction`s (a reorderable list's "Move
     * down", for one) as such actions, usually on a wrapper above the tagged
     * node. Returns the node carrying it with the action, or null; [available]
     * collects every custom label seen, for a failure message.
     */
    fun findCustomAction(
        anchor: AccessibilityNodeInfo,
        labelPart: String,
        available: MutableList<String>,
    ): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo.AccessibilityAction>? {
        fun check(node: AccessibilityNodeInfo): AccessibilityNodeInfo.AccessibilityAction? {
            val custom = node.actionList.filter { it.label != null }
            custom.forEach { available.add(it.label.toString()) }
            return custom.firstOrNull { it.label.toString().contains(labelPart, ignoreCase = true) }
        }
        check(anchor)?.let { return anchor to it }
        var found: Pair<AccessibilityNodeInfo, AccessibilityNodeInfo.AccessibilityAction>? = null
        search(anchor) { node ->
            check(node)?.let { found = node to it }
            found != null
        }
        found?.let { return it }
        var parent = try { anchor.parent } catch (_: Throwable) { null }
        var hops = 0
        while (parent != null && hops < 8) {
            check(parent)?.let { return parent to it }
            parent = try { parent.parent } catch (_: Throwable) { null }
            hops++
        }
        return null
    }

    /** Number of nodes carrying [identifier]. */
    fun countByIdentifier(root: AccessibilityNodeInfo?, identifier: String): Int {
        if (root == null) return 0
        var count = 0
        search(root) { node ->
            if (matchesIdentifier(node, identifier)) count++
            false
        }
        return count
    }

    /**
     * True only for a node that positively reports vertical scrolling.
     *
     * Flutter does not mark every horizontal scroller as such: a swipeable
     * tab pager (TabBarView's PageView) has implicit scrolling switched off,
     * so it is published as a plain View, and a horizontal list with known
     * children carries only column CollectionInfo. Treating "not labelled
     * horizontal" as vertical made a page scroll swipe to the next tab. So
     * a node counts only if it is a ScrollView, or a list whose CollectionInfo
     * has rows and no columns.
     */
    private fun isVerticalScroller(node: AccessibilityNodeInfo): Boolean {
        if (!node.isScrollable) return false
        if (node.className?.toString() == "android.widget.ScrollView") return true
        val info = try {
            node.collectionInfo
        } catch (_: Throwable) {
            null
        } ?: return false
        return info.rowCount > 0 && info.columnCount <= 1
    }

    /** The vertically scrolling node with the largest on-screen area, if any. */
    fun largestVerticalScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val bounds = android.graphics.Rect()
        search(root) { node ->
            if (isVerticalScroller(node)) {
                node.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (area > bestArea) {
                    bestArea = area
                    best = node
                }
            }
            false
        }
        return best
    }

    /** The nearest ancestor of [node] that can scroll, e.g. a horizontal chip list. */
    fun scrollableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = try {
            node.parent
        } catch (_: Throwable) {
            null
        }
        var hops = 0
        while (current != null && hops < 8) {
            if (current.isScrollable) return current
            current = try {
                current.parent
            } catch (_: Throwable) {
                null
            }
            hops++
        }
        return null
    }

    /** The first node that can scroll, e.g. a Settings page's list. */
    fun firstScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        search(root) { it.isScrollable }

    // ── Action resolution ────────────────────────────────────────────────────
    //
    // A `Semantics(identifier: ...)` wrapper is its own node in the Flutter
    // semantics tree; the node that actually carries the tap or the text entry
    // action is usually a descendant (an InkWell, an EditableText) and can
    // occasionally be an ancestor if Flutter merged the annotation upwards. So
    // an identifier hit is treated as an anchor, and the actionable node is
    // resolved from it: self, then descendants, then a few ancestors.

    fun supportsAction(node: AccessibilityNodeInfo, action: Int): Boolean =
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

    /** Text held by the actual editable node below [anchor], when exposed. */
    fun editableText(anchor: AccessibilityNodeInfo): String? =
        resolve(anchor) { node -> isEditableLike(node) && node.text != null }
            ?.text
            ?.toString()

    /** The node that can accept ACTION_CLICK for this anchor, if any. */
    fun resolveClickable(anchor: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        resolve(anchor) { isClickableLike(it) }

    /** The node that can accept ACTION_LONG_CLICK for this anchor, if any. */
    fun resolveLongClickable(anchor: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        resolve(anchor) { supportsAction(it, AccessibilityNodeInfo.ACTION_LONG_CLICK) }

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
