package com.docvault.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileRulesTest {

    // --- upload pre-checks ------------------------------------------------

    @Test
    fun `allowed file of normal size passes`() {
        assertNull(FileRules.rejectionReason("Aadhaar.pdf", 1_200_000))
    }

    @Test
    fun `extension check is case insensitive`() {
        assertNull(FileRules.rejectionReason("PHOTO.JPG", 500_000))
    }

    @Test
    fun `unsupported type is rejected`() {
        assertNotNull(FileRules.rejectionReason("setup.exe", 1_000))
        assertNotNull(FileRules.rejectionReason("no-extension", 1_000))
    }

    @Test
    fun `file over 20 MB is rejected`() {
        assertNotNull(FileRules.rejectionReason("big.pdf", FileRules.MAX_UPLOAD_BYTES + 1))
    }

    @Test
    fun `file of exactly 20 MB is allowed`() {
        assertNull(FileRules.rejectionReason("edge.pdf", FileRules.MAX_UPLOAD_BYTES))
    }

    @Test
    fun `empty file is rejected`() {
        assertNotNull(FileRules.rejectionReason("empty.pdf", 0))
    }

    @Test
    fun `unknown size is left for the server to check`() {
        assertNull(FileRules.rejectionReason("stream.pdf", -1))
    }

    // --- formatting -------------------------------------------------------

    @Test
    fun `bytes are formatted for people`() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("744 KB", Format.bytes(744 * 1024))
        assertEquals("1.2 MB", Format.bytes(1_258_291))
    }

    @Test
    fun `dates accept timestamps with or without an offset`() {
        assertEquals("13 Sep 2026", Format.date("2026-09-13T10:15:00.123456"))
        assertEquals("13 Sep 2026", Format.date("2026-09-13T10:15:00+00:00"))
    }

    @Test
    fun `share state reads naturally`() {
        assertEquals("Private", Format.shareState(0))
        assertEquals("1 group", Format.shareState(1))
        assertEquals("3 groups", Format.shareState(3))
    }

    @Test
    fun `document type labels`() {
        assertEquals("Voter ID", DocTypes.label("voter_id"))
        assertEquals("Other", DocTypes.label("unknown"))
    }
}
