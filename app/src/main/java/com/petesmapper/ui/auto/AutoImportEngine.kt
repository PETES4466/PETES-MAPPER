package com.petesmapper.ui.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File

data class ImageBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class PerspectiveCorners(
    val topLeftX: Float,
    val topLeftY: Float,
    val topRightX: Float,
    val topRightY: Float,
    val bottomRightX: Float,
    val bottomRightY: Float,
    val bottomLeftX: Float,
    val bottomLeftY: Float,
)

data class NormalizedImage(
    val originalPath: String,
    val workingBitmap: Bitmap,
    val width: Int,
    val height: Int,
    val portrait: Boolean,
)

class AutoImportEngine {

    fun loadImage(imageFile: File, decodeMutable: Boolean = true): NormalizedImage {
        require(imageFile.exists()) { "Image not found: ${imageFile.absolutePath}" }
        val options = BitmapFactory.Options().apply {
            inMutable = decodeMutable
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = BitmapFactory.decodeFile(imageFile.absolutePath, options)
            ?: throw IllegalArgumentException("Unable to decode image: ${imageFile.absolutePath}")

        // Preserve original; always operate on copy.
        val workingCopy = source.copy(Bitmap.Config.ARGB_8888, true)
        val portrait = workingCopy.height >= workingCopy.width
        return NormalizedImage(
            originalPath = imageFile.absolutePath,
            workingBitmap = workingCopy,
            width = workingCopy.width,
            height = workingCopy.height,
            portrait = portrait,
        )
    }

    fun cropImage(image: NormalizedImage, bounds: ImageBounds): NormalizedImage {
        val safeLeft = bounds.left.coerceIn(0, image.width - 1)
        val safeTop = bounds.top.coerceIn(0, image.height - 1)
        val safeWidth = bounds.width.coerceAtLeast(1).coerceAtMost(image.width - safeLeft)
        val safeHeight = bounds.height.coerceAtLeast(1).coerceAtMost(image.height - safeTop)

        val cropped = Bitmap.createBitmap(image.workingBitmap, safeLeft, safeTop, safeWidth, safeHeight)
        return image.copy(workingBitmap = cropped, width = cropped.width, height = cropped.height, portrait = cropped.height >= cropped.width)
    }

    fun rotateImage(image: NormalizedImage, degrees: Float): NormalizedImage {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(image.workingBitmap, 0, 0, image.width, image.height, matrix, true)
        return image.copy(workingBitmap = rotated, width = rotated.width, height = rotated.height, portrait = rotated.height >= rotated.width)
    }

    fun perspectiveCorrect(
        image: NormalizedImage,
        corners: PerspectiveCorners? = null,
        autoDetect: Boolean = true,
    ): NormalizedImage {
        val detected = corners ?: if (autoDetect) autoDetectCorners(image) else defaultCorners(image)
        val corrected = warpPerspective(image.workingBitmap, detected)
        return image.copy(workingBitmap = corrected, width = corrected.width, height = corrected.height, portrait = corrected.height >= corrected.width)
    }

    fun normalizeBrightness(image: NormalizedImage, delta: Float): NormalizedImage {
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val m = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, delta,
                0f, 1f, 0f, 0f, delta,
                0f, 0f, 1f, 0f, delta,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(m) }
        canvas.drawBitmap(image.workingBitmap, 0f, 0f, paint)
        return image.copy(workingBitmap = out)
    }

    fun normalizeContrast(image: NormalizedImage, contrast: Float): NormalizedImage {
        val scale = contrast.coerceIn(0.1f, 3f)
        val translate = (-0.5f * scale + 0.5f) * 255f
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val m = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(m) }
        canvas.drawBitmap(image.workingBitmap, 0f, 0f, paint)
        return image.copy(workingBitmap = out)
    }

    private fun autoDetectCorners(image: NormalizedImage): PerspectiveCorners {
        // Placeholder auto-detection that defaults to full-image corners.
        return defaultCorners(image)
    }

    private fun defaultCorners(image: NormalizedImage): PerspectiveCorners {
        return PerspectiveCorners(
            topLeftX = 0f,
            topLeftY = 0f,
            topRightX = image.width - 1f,
            topRightY = 0f,
            bottomRightX = image.width - 1f,
            bottomRightY = image.height - 1f,
            bottomLeftX = 0f,
            bottomLeftY = image.height - 1f,
        )
    }

    private fun warpPerspective(bitmap: Bitmap, corners: PerspectiveCorners): Bitmap {
        val src = floatArrayOf(
            corners.topLeftX, corners.topLeftY,
            corners.topRightX, corners.topRightY,
            corners.bottomRightX, corners.bottomRightY,
            corners.bottomLeftX, corners.bottomLeftY,
        )
        val dst = floatArrayOf(
            0f, 0f,
            bitmap.width - 1f, 0f,
            bitmap.width - 1f, bitmap.height - 1f,
            0f, bitmap.height - 1f,
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }
}
