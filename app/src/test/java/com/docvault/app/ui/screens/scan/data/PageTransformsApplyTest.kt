package com.docvault.app.ui.screens.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Geometry-only checks for [PageTransforms.apply], via Robolectric.
 *
 * Deliberately does NOT check pixel colors here: verified live (see
 * `PageTransformsColorInstrumentedTest` in `androidTest`) that Robolectric's
 * `Canvas.drawBitmap` shadow does not honor `Paint.colorFilter` — a
 * black-and-white/brightness assertion here reports the untouched source
 * pixel and passes for the wrong reason. Dimensions, unlike color filters,
 * come out correct under Robolectric, so those stay here as a fast JVM
 * check.
 */
@RunWith(RobolectricTestRunner::class)
class PageTransformsApplyTest {

    private fun tempFile(): File = File.createTempFile("page_transform_test", ".jpg").apply { deleteOnExit() }

    @Test
    fun `a 90-degree rotation swaps width and height`() {
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val destination = tempFile()

        PageTransforms.apply(source, PageEdit(rotationDegrees = 90), destination)

        val result = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(200, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun `no rotation keeps the original dimensions`() {
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val destination = tempFile()

        PageTransforms.apply(source, PageEdit(), destination)

        val result = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun `a 180-degree rotation keeps the original dimensions`() {
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val destination = tempFile()

        PageTransforms.apply(source, PageEdit(rotationDegrees = 180), destination)

        val result = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }
}
