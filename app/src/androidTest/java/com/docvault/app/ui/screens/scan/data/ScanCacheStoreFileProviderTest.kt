package com.docvault.app.ui.screens.scan.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirms the FileProvider wiring (manifest `<provider>` +
 * `res/xml/scan_file_paths.xml`) actually works end to end: a Uri built from
 * a cache file must be readable back out through `ContentResolver` — the
 * exact call `ProgressRequestBody.writeTo` makes during the real upload.
 *
 * A misconfigured authority or path fails silently deep inside that call,
 * not at Uri-creation time, so this is worth a dedicated regression test
 * rather than trusting the manifest by inspection.
 */
@RunWith(AndroidJUnit4::class)
class ScanCacheStoreFileProviderTest {

    @Test
    fun exportedFileIsReadableThroughItsFileProviderUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ScanCacheStore(context)
        val sessionId = store.newSession()
        val file = store.newFile(sessionId, ".txt")
        val content = "scan export round-trip"
        file.writeText(content)

        val uri = store.uriFor(file)
        val readBack = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.decodeToString()

        assertEquals(content, readBack)
        store.delete(sessionId)
    }
}
