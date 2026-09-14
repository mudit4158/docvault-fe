package com.docvault.app.ui.screens.scan

/**
 * Pure list-transform logic for a scan session's pages.
 *
 * Deliberately extracted from [ScanViewModel] — reorder/delete indexing is
 * plain list arithmetic with no Android framework dependency, so it's
 * unit-testable on the JVM without Robolectric or a device, unlike the rest
 * of the ViewModel (which touches `ContentResolver`/`File` I/O).
 */
object ScanPageOps {

    fun move(pages: List<ScanPage>, fromIndex: Int, toIndex: Int): List<ScanPage> {
        if (fromIndex !in pages.indices || toIndex !in pages.indices) return pages
        return pages.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun remove(pages: List<ScanPage>, pageId: String): List<ScanPage> =
        pages.filterNot { it.id == pageId }

    /** Keeps a page index inside bounds after the list's size changes. */
    fun clampIndex(index: Int, pageCount: Int): Int = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    /** The format picker only makes sense for a single-page session (engineering handoff: multi-page is always PDF). */
    fun needsFormatChoice(pageCount: Int, saveFormat: SaveFormat?): Boolean = pageCount == 1 && saveFormat == null
}
