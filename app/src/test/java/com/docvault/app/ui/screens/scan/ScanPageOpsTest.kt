package com.docvault.app.ui.screens.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun page(id: String) = ScanPage(id = id, originalFile = File("$id.jpg"), workingFile = File("$id.jpg"))

class ScanPageOpsTest {

    // --- move ---------------------------------------------------------

    @Test
    fun `move shifts a page from one index to another`() {
        val pages = listOf(page("a"), page("b"), page("c"))
        val moved = ScanPageOps.move(pages, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("b", "c", "a"), moved.map { it.id })
    }

    @Test
    fun `move backward shifts the intervening pages forward`() {
        val pages = listOf(page("a"), page("b"), page("c"))
        val moved = ScanPageOps.move(pages, fromIndex = 2, toIndex = 0)
        assertEquals(listOf("c", "a", "b"), moved.map { it.id })
    }

    @Test
    fun `move with an out-of-range index is a no-op`() {
        val pages = listOf(page("a"), page("b"))
        assertEquals(pages, ScanPageOps.move(pages, fromIndex = 0, toIndex = 5))
        assertEquals(pages, ScanPageOps.move(pages, fromIndex = -1, toIndex = 1))
    }

    // --- remove ---------------------------------------------------------

    @Test
    fun `remove drops exactly the matching page`() {
        val pages = listOf(page("a"), page("b"), page("c"))
        val remaining = ScanPageOps.remove(pages, "b")
        assertEquals(listOf("a", "c"), remaining.map { it.id })
    }

    @Test
    fun `remove of an unknown id is a no-op`() {
        val pages = listOf(page("a"), page("b"))
        assertEquals(pages, ScanPageOps.remove(pages, "nonexistent"))
    }

    // --- clampIndex -------------------------------------------------------

    @Test
    fun `clampIndex keeps an in-range index unchanged`() {
        assertEquals(1, ScanPageOps.clampIndex(1, pageCount = 3))
    }

    @Test
    fun `clampIndex pulls an index back inside a shrunken list`() {
        assertEquals(1, ScanPageOps.clampIndex(4, pageCount = 2))
    }

    @Test
    fun `clampIndex floors at zero for an empty list`() {
        assertEquals(0, ScanPageOps.clampIndex(3, pageCount = 0))
    }

    // --- needsFormatChoice --------------------------------------------------

    @Test
    fun `needsFormatChoice is true for exactly one page with no format chosen yet`() {
        assertTrue(ScanPageOps.needsFormatChoice(pageCount = 1, saveFormat = null))
    }

    @Test
    fun `needsFormatChoice is false once a format has been chosen`() {
        assertFalse(ScanPageOps.needsFormatChoice(pageCount = 1, saveFormat = SaveFormat.PDF))
    }

    @Test
    fun `needsFormatChoice is false for more than one page, regardless of format`() {
        assertFalse(ScanPageOps.needsFormatChoice(pageCount = 2, saveFormat = null))
    }

    @Test
    fun `needsFormatChoice is false for zero pages`() {
        assertFalse(ScanPageOps.needsFormatChoice(pageCount = 0, saveFormat = null))
    }
}
