package com.docvault.app.ui.screens.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Pixel-level checks for [PageTransforms.apply]'s color pipeline
 * (`Paint.colorFilter` over `Canvas.drawBitmap`) — moved here from the JVM
 * unit test suite because Robolectric's `Canvas` shadow does not apply
 * `Paint.colorFilter` at all (dimensions come out right; color does not),
 * so these assertions need a real Skia renderer to mean anything.
 */
@RunWith(AndroidJUnit4::class)
class PageTransformsColorInstrumentedTest {

    private fun tempFile(): File = File.createTempFile("page_transform_color_test", ".jpg").apply { deleteOnExit() }

    @Test
    fun blackAndWhiteConvergesTheColorChannelsOfASaturatedPixel() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(200, 50, 50))
        val destination = tempFile()

        PageTransforms.apply(source, PageEdit(isBlackAndWhite = true), destination, quality = 100)

        val pixel = BitmapFactory.decodeFile(destination.absolutePath).getPixel(2, 2)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        assertTrue(
            "expected R/G/B to converge after black-and-white, got ($r,$g,$b)",
            abs(r - g) <= 3 && abs(g - b) <= 3,
        )
    }

    @Test
    fun higherBrightnessProducesALighterPixelThanLowerBrightness() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(120, 120, 120))

        val darkFile = tempFile()
        PageTransforms.apply(source, PageEdit(brightness = -50f), darkFile, quality = 100)
        val brightFile = tempFile()
        PageTransforms.apply(source, PageEdit(brightness = 50f), brightFile, quality = 100)

        val darkPixel = BitmapFactory.decodeFile(darkFile.absolutePath).getPixel(2, 2)
        val brightPixel = BitmapFactory.decodeFile(brightFile.absolutePath).getPixel(2, 2)

        assertTrue(Color.red(brightPixel) > Color.red(darkPixel))
    }

    @Test
    fun higherContrastPushesALightPixelLighter() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(180, 180, 180)) // above mid-grey (128)

        val lowContrastFile = tempFile()
        PageTransforms.apply(source, PageEdit(contrast = 0.8f), lowContrastFile, quality = 100)
        val highContrastFile = tempFile()
        PageTransforms.apply(source, PageEdit(contrast = 1.5f), highContrastFile, quality = 100)

        val lowPixel = BitmapFactory.decodeFile(lowContrastFile.absolutePath).getPixel(2, 2)
        val highPixel = BitmapFactory.decodeFile(highContrastFile.absolutePath).getPixel(2, 2)

        assertTrue(Color.red(highPixel) > Color.red(lowPixel))
    }
}
