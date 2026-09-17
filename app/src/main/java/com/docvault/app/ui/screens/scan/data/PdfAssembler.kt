package com.docvault.app.ui.screens.scan.data

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Assembles an ordered list of page JPEGs into a single PDF.
 *
 * Uses Android's built-in [PdfDocument] rather than a third-party library —
 * the backend only ever reads the result via `pypdf` for page count and
 * type-sniffing (docvault-be's `document_lifecycle.md`), no advanced PDF
 * features are needed. One page's bitmap is held at a time, matching the
 * low-end-device memory discipline the rest of the scan flow follows.
 *
 * Known open risk (see the feature's implementation plan, "PDF assembly"):
 * it is unverified whether `Canvas.drawBitmap` onto a `PdfDocument` page
 * preserves JPEG compression or re-encodes as raw pixel data. If a real
 * multi-page export lands far above the sum of its source JPEGs, re-encoding
 * is happening — fall back to `pdfbox-android` (inserts JPEG bytes directly
 * as a DCTDecode image XObject, no re-encoding) rather than tuning quality
 * settings here.
 */
object PdfAssembler {

    fun assemble(pageFiles: List<File>, destination: File) {
        require(pageFiles.isNotEmpty()) { "Nothing to assemble" }
        val document = PdfDocument()
        try {
            pageFiles.forEachIndexed { index, file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("Couldn't read page ${index + 1}")
                try {
                    val pageInfo = PdfDocument.PageInfo
                        .Builder(bitmap.width, bitmap.height, index)
                        .create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(destination).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }
    }
}
