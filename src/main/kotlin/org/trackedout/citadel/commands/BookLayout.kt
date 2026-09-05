package org.trackedout.citadel.commands

import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * Models the layout constraints of a Minecraft written book page so we can
 * validate that rendered pages don't overflow (which silently clips text in
 * the client).
 *
 * The vanilla book page is 114px wide and fits 14 lines of text. Text wraps on
 * whole words when a word would exceed the page width; a word longer than the
 * page width is hard-wrapped. Explicit newlines start a new line.
 *
 * Character widths are the vanilla default-font advance widths (glyph width +
 * 1px spacing). Bold text adds 1px per character.
 */
object BookLayout {
    const val PAGE_WIDTH_PX = 114
    const val PAGE_LINES = 14

    // Default-font advance widths (glyph + 1px spacing) for the characters that
    // are narrower than the common 6px. Everything else defaults to 6px.
    private val NARROW_WIDTHS: Map<Char, Int> = buildMap {
        put(' ', 4)
        put('!', 2); put('\'', 3); put(',', 2); put('.', 2); put(':', 2); put(';', 2)
        put('i', 2); put('l', 3); put('t', 4); put('f', 5); put('k', 5)
        put('I', 4); put('(', 5); put(')', 5); put('[', 4); put(']', 4)
        put('{', 5); put('}', 5); put('<', 5); put('>', 5); put('|', 2)
        put('`', 3); put('"', 5); put('*', 5)
    }

    private fun charWidth(c: Char, bold: Boolean): Int {
        val base = NARROW_WIDTHS[c] ?: 6
        return if (bold) base + 1 else base
    }

    /**
     * Strip MiniMessage formatting and return the plain visible text, tracking
     * which spans are bold so we can size them correctly. Returns a list of
     * (text, bold) runs preserving newlines inside the text of each run.
     */
    private fun toRuns(miniMessage: String): List<Pair<String, Boolean>> {
        // Deserialize then re-serialize as plain text is lossy for bold, so we
        // do a lightweight tag scan instead: track <bold>/<b> nesting.
        val runs = mutableListOf<Pair<String, Boolean>>()
        val sb = StringBuilder()
        var boldDepth = 0
        var i = 0
        fun flush() {
            if (sb.isNotEmpty()) {
                runs.add(sb.toString() to (boldDepth > 0))
                sb.clear()
            }
        }
        while (i < miniMessage.length) {
            val c = miniMessage[i]
            if (c == '<') {
                val end = miniMessage.indexOf('>', i)
                if (end == -1) { sb.append(c); i++; continue }
                val tag = miniMessage.substring(i + 1, end).trim().lowercase()
                val name = tag.removePrefix("/").substringBefore(':')
                when {
                    name == "bold" || name == "b" -> {
                        flush()
                        if (tag.startsWith("/")) { if (boldDepth > 0) boldDepth-- } else boldDepth++
                    }
                    // Any other tag (colour, click, italic, hex, etc.) is invisible.
                    else -> { /* skip tag */ }
                }
                i = end + 1
            } else if (c == '\\' && i + 1 < miniMessage.length && miniMessage[i + 1] == '<') {
                sb.append('<'); i += 2
            } else {
                sb.append(c); i++
            }
        }
        flush()
        return runs
    }

    /**
     * Compute how many display lines a MiniMessage page occupies once wrapped to
     * the page width, mirroring the vanilla word-wrap behaviour.
     */
    fun lineCount(miniMessagePage: String): Int {
        val runs = toRuns(miniMessagePage)

        var lines = 1
        var lineWidth = 0

        fun newline() { lines++; lineWidth = 0 }

        // Rebuild a flat char stream with bold info, honouring explicit newlines.
        // Wrap on spaces: accumulate a word, and when adding it would overflow,
        // wrap first.
        val word = StringBuilder()
        var wordWidth = 0
        var wordBold = false

        fun commitWord() {
            if (word.isEmpty()) return
            // If the word alone is wider than a page, hard-wrap it.
            if (wordWidth > PAGE_WIDTH_PX) {
                for (ch in word) {
                    val w = charWidth(ch, wordBold)
                    if (lineWidth + w > PAGE_WIDTH_PX) newline()
                    lineWidth += w
                }
            } else {
                if (lineWidth > 0 && lineWidth + wordWidth > PAGE_WIDTH_PX) newline()
                lineWidth += wordWidth
            }
            word.clear(); wordWidth = 0
        }

        for ((text, bold) in runs) {
            for (ch in text) {
                when (ch) {
                    '\n' -> { commitWord(); newline() }
                    ' ' -> {
                        commitWord()
                        val spaceW = charWidth(' ', bold)
                        if (lineWidth > 0 && lineWidth + spaceW <= PAGE_WIDTH_PX) lineWidth += spaceW
                    }
                    else -> {
                        if (word.isEmpty()) wordBold = bold
                        word.append(ch)
                        wordWidth += charWidth(ch, bold)
                    }
                }
            }
        }
        commitWord()
        return lines
    }

    /** True if the page fits within the vanilla page height. */
    fun fits(miniMessagePage: String): Boolean = lineCount(miniMessagePage) <= PAGE_LINES

    /**
     * Validate a full set of pages, returning a list of human-readable problems
     * (one per overflowing page). Empty list means all pages fit.
     *
     * Also verifies each page deserializes as valid MiniMessage.
     */
    fun validate(pages: List<String>): List<String> {
        val problems = mutableListOf<String>()
        pages.forEachIndexed { index, page ->
            try {
                MiniMessage.miniMessage().deserialize(page)
            } catch (e: Exception) {
                problems.add("Page ${index + 1}: invalid MiniMessage: ${e.message}")
            }
            val lines = lineCount(page)
            if (lines > PAGE_LINES) {
                problems.add("Page ${index + 1}: overflows page height ($lines lines > $PAGE_LINES)")
            }
        }
        return problems
    }
}
