package com.docvault.app.ui.screens.scan.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Regression test for the open risk flagged in [PdfAssembler]'s KDoc: does
 * `Canvas.drawBitmap` onto a `PdfDocument` page preserve JPEG compression, or
 * re-encode as raw pixel data? If this starts failing, PdfDocument is
 * re-encoding — the fix is switching to `pdfbox-android` (inserting JPEG
 * bytes directly as a DCTDecode image XObject), not tuning quality here.
 *
 * Needs a real device/emulator's Skia PDF backend — this is exactly the kind
 * of thing Robolectric's shadow could mask a real answer to, so it stays an
 * instrumented test rather than a JVM one.
 */
@RunWith(AndroidJUnit4::class)
class PdfAssemblerInstrumentedTest {

    @Test
    fun assembledPdfStaysWithinAGenerousMultipleOfItsSourceJpegs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "pdf_assembler_test").apply { mkdirs() }

        // Random-filled rectangles, not a solid fill — a uniform-color bitmap
        // compresses to almost nothing and would make this check meaningless.
        // Resolution/quality match the real working-page spec (ScanSpec).
        val pageFiles = (1..4).map { index ->
            val bitmap = Bitmap.createBitmap(
                ScanSpec.TARGET_LONGEST_SIDE_PX,
                (ScanSpec.TARGET_LONGEST_SIDE_PX * 1.414f).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint()
            val random = Random(index)
            repeat(400) {
                paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                canvas.drawRect(
                    random.nextInt(bitmap.width).toFloat(),
                    random.nextInt(bitmap.height).toFloat(),
                    random.nextInt(bitmap.width).toFloat(),
                    random.nextInt(bitmap.height).toFloat(),
                    paint,
                )
            }
            val file = File(dir, "page_$index.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, ScanSpec.WORKING_JPEG_QUALITY, it) }
            bitmap.recycle()
            file
        }
        val sourceBytes = pageFiles.sumOf { it.length() }

        val destination = File(dir, "export.pdf")
        try {
            PdfAssembler.assemble(pageFiles, destination)

            // A generous multiple, not a tight bound — this catches gross
            // re-encoding (many times larger), not fine differences in
            // compression ratio.
            assertTrue(
                "PDF (${destination.length()} bytes) is far larger than its ${pageFiles.size} " +
                    "source JPEGs ($sourceBytes bytes combined) — PdfDocument may be re-encoding " +
                    "instead of preserving JPEG compression. See PdfAssembler's KDoc.",
                destination.length() < sourceBytes * 10,
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
