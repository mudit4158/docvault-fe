package com.docvault.app.ui.screens.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/**
 * Page edit parameters (PRD §4.5), applied together in one pass so a page is
 * never re-encoded once per slider tick.
 *
 * Boundary detection and crop are Google's ML Kit scanner's job, not this
 * file's — see [DocumentScannerLauncher]. Rotation is baked into the exported
 * pixels (handoff doc: no orientation flag survives to the server);
 * brightness/contrast/black-and-white are destructive at export time, but
 * [apply] always renders from the page's untouched original, so adjusting a
 * slider again is a fresh render rather than a compounding transform on an
 * already-edited image.
 */
data class PageEdit(
    val rotationDegrees: Int = 0,
    /** -100f..100f, 0f = unchanged. */
    val brightness: Float = 0f,
    /** 0.5f..2f, 1f = unchanged. */
    val contrast: Float = 1f,
    val isBlackAndWhite: Boolean = false,
) {
    val isIdentity: Boolean
        get() = rotationDegrees == 0 && brightness == 0f && contrast == 1f && !isBlackAndWhite
}

/**
 * Bitmap-touching page transforms.
 *
 * The pixel-math helpers ([normalizeRotation], [clampBrightness],
 * [clampContrast]) are deliberately pure functions on plain numbers, split
 * out from [apply] so the arithmetic is unit-testable without a device or
 * Robolectric.
 */
object PageTransforms {

    /** Keeps a rotation step inside Android's 0/90/180/270 convention. */
    fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

    fun clampBrightness(brightness: Float): Float = brightness.coerceIn(-100f, 100f)

    fun clampContrast(contrast: Float): Float = contrast.coerceIn(0.5f, 2f)

    /**
     * Renders [source] with [edit] applied, writing a JPEG to [destination].
     *
     * Processes exactly one bitmap at a time and recycles every intermediate
     * — the OOM concern the handoff doc calls out for low-end devices holding
     * a multi-page session.
     */
    fun apply(source: Bitmap, edit: PageEdit, destination: File, quality: Int = 82) {
        val rotationDegrees = normalizeRotation(edit.rotationDegrees)
        val rotated = if (rotationDegrees == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }

        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            rotated,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrixFor(edit)) },
        )

        FileOutputStream(destination).use { out -> result.compress(Bitmap.CompressFormat.JPEG, quality, out) }

        if (rotated !== source) rotated.recycle()
        result.recycle()
    }

    /** Decodes [file] downsampled to roughly [targetLongestSide], never at native resolution. */
    fun decodeDownsampled(file: File, targetLongestSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longestSide / (sample * 2) >= targetLongestSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Couldn't read page image")
    }

    private fun colorMatrixFor(edit: PageEdit): ColorMatrix {
        val contrast = clampContrast(edit.contrast)
        val brightness = clampBrightness(edit.brightness)
        // Contrast scales around mid-grey (128); brightness is a flat additive shift.
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val matrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        if (edit.isBlackAndWhite) {
            matrix.postConcat(ColorMatrix().apply { setSaturation(0f) })
        }
        return matrix
    }
}
