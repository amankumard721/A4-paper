package com.a4sizepaper.typing.texttopdf.pdf

import java.io.Writer

/** Finds matches split across PDFTextStripper writes without retaining a whole page. */
internal class PdfMatchWriter(query: String) : Writer() {
    private val query = query.replace(Regex("[\\s\\u00a0]+"), " ").trim()
    private var previousSpace = false
    var matched = false
        private set
    var hasText = false
        private set
    private var tail = ""

    override fun write(buffer: CharArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        var start = offset
        val end = offset + length
        while (start < end) {
            val count = minOf(4096, end - start)
            val chunk = buildString {
                for (i in start until start + count) {
                    val char = buffer[i]
                    val space = char.isWhitespace() || char == '\u00a0'
                    if (!space || !previousSpace) append(if (space) ' ' else char)
                    previousSpace = space
                }
            }
            hasText = hasText || chunk.any { !it.isWhitespace() }
            if (!matched) {
                val window = tail + chunk
                matched = query.isNotEmpty() && window.contains(query, ignoreCase = true)
                tail = window.takeLast((query.length - 1).coerceAtLeast(0))
            }
            start += count
        }
    }
    override fun flush() = Unit
    override fun close() = Unit
}
