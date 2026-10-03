package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 7-step image processing pipeline optimized for thermal receipt printers.
 * Converts any photo into a 1-bit dithered raster suitable for ESC/POS GS v 0 printing.
 *
 * Pipeline: Resize → BT.601 Luma → CLAHE → Unsharp Mask → Gamma → Atkinson Dither → Pack bits
 */
object ThermalImageProcessor {

    /**
     * Process a source bitmap for thermal printing.
     * @param source Original color photo
     * @param targetWidth Print width in dots (384 for 58mm, 576 for 80mm)
     * @return Packed 1-bit raster bytes ready for GS v 0 command
     */
    fun processForThermal(source: Bitmap, targetWidth: Int = 384): ProcessedImage {
        // Step 1: Bicubic resample to target width
        val scaled = resizeToWidth(source, targetWidth)
        return runPipeline(scaled)
    }

    /**
     * Process a source bitmap with 1:1 center-crop for story photos.
     * Crops to square first, then resizes to targetWidth and runs the full pipeline.
     */
    fun processForThermalSquare(source: Bitmap, targetWidth: Int = 384): ProcessedImage {
        return processForThermalAspectRatio(source, targetWidth = targetWidth, aspectNumerator = 7, aspectDenominator = 4)
    }

    /**
     * Process a source bitmap with custom aspect ratio center-crop for story photos.
     * Default: 7:4 aspect ratio (384 dots width by 219 dots height).
     * Fits 100% of the printable width (48 bytes) with zero margins, while keeping receipt height sleek.
     */
    fun processForThermalAspectRatio(
        source: Bitmap,
        targetWidth: Int = 384,
        aspectNumerator: Int = 7,
        aspectDenominator: Int = 4
    ): ProcessedImage {
        val cropped = centerCropAspectRatio(source, aspectNumerator, aspectDenominator)
        val targetHeight = ((targetWidth.toFloat() * aspectDenominator) / aspectNumerator).roundToInt()
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        return runPipeline(scaled)
    }

    /**
     * Center-crop a bitmap to a specific aspect ratio (e.g. 7:4, 5:4, 16:9).
     */
    private fun centerCropAspectRatio(source: Bitmap, aspectNumerator: Int, aspectDenominator: Int): Bitmap {
        val targetRatio = aspectNumerator.toFloat() / aspectDenominator.toFloat()
        val currentRatio = source.width.toFloat() / source.height.toFloat()

        val cropWidth: Int
        val cropHeight: Int

        if (currentRatio > targetRatio) {
            // Source is wider than target ratio -> crop width
            cropHeight = source.height
            cropWidth = ((source.height * aspectNumerator).toFloat() / aspectDenominator).roundToInt()
                .coerceAtMost(source.width)
        } else {
            // Source is taller than target ratio -> crop height
            cropWidth = source.width
            cropHeight = ((source.width * aspectDenominator).toFloat() / aspectNumerator).roundToInt()
                .coerceAtMost(source.height)
        }

        val xOffset = max(0, (source.width - cropWidth) / 2)
        val yOffset = max(0, (source.height - cropHeight) / 2)
        return Bitmap.createBitmap(source, xOffset, yOffset, cropWidth, cropHeight)
    }

    /**
     * Center-crop a bitmap to 1:1 square aspect ratio.
     */
    private fun centerCropSquare(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val xOffset = (source.width - size) / 2
        val yOffset = (source.height - size) / 2
        return Bitmap.createBitmap(source, xOffset, yOffset, size, size)
    }

    /**
     * Run the 7-step thermal processing pipeline on a scaled bitmap.
     */
    private fun runPipeline(scaled: Bitmap): ProcessedImage {
        val width = scaled.width
        val height = scaled.height

        // Step 2: BT.601 perceptual luminance extraction
        val luma = extractLuminance(scaled)

        // Step 3: CLAHE - Contrast Limited Adaptive Histogram Equalization
        clahe(luma, width, height, gridSize = 8, clipLimit = 2.5f)

        // Step 4: Unsharp masking (1.2x edge boost)
        unsharpMask(luma, width, height, amount = 1.2f)

        // Step 5: Gamma correction (γ = 1.3 for thermal dot gain compensation)
        gammaCorrection(luma, gamma = 1.3f)

        // Step 6: Bill Atkinson dithering (75% error diffusion)
        val binaryPixels = atkinsonDither(luma, width, height)

        // Step 7: Pack into 1-bit raster bytes
        val rasterBytes = packBits(binaryPixels, width, height)

        return ProcessedImage(
            data = rasterBytes,
            widthBytes = (width + 7) / 8,
            heightDots = height,
            widthDots = width
        )
    }

    data class ProcessedImage(
        val data: ByteArray,
        val widthBytes: Int,
        val heightDots: Int,
        val widthDots: Int
    )

    // Step 1: Resize maintaining aspect ratio
    private fun resizeToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / source.width
        val targetHeight = (source.height * scale).roundToInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    // Step 2: ITU-R BT.601 luminance extraction
    private fun extractLuminance(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return luma
    }

    // Step 3: CLAHE (simplified but effective implementation)
    private fun clahe(luma: FloatArray, width: Int, height: Int, gridSize: Int, clipLimit: Float) {
        val tileW = width / gridSize
        val tileH = height / gridSize
        if (tileW < 2 || tileH < 2) return

        for (ty in 0 until gridSize) {
            for (tx in 0 until gridSize) {
                val x0 = tx * tileW
                val y0 = ty * tileH
                val x1 = if (tx == gridSize - 1) width else (tx + 1) * tileW
                val y1 = if (ty == gridSize - 1) height else (ty + 1) * tileH
                val tw = x1 - x0
                val th = y1 - y0

                // Build histogram for this tile
                val hist = IntArray(256)
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val idx = y * width + x
                        val bin = luma[idx].roundToInt().coerceIn(0, 255)
                        hist[bin]++
                    }
                }

                // Clip histogram
                val totalPixels = tw * th
                val clipThreshold = (clipLimit * totalPixels / 256).roundToInt()
                var excess = 0
                for (i in 0 until 256) {
                    if (hist[i] > clipThreshold) {
                        excess += hist[i] - clipThreshold
                        hist[i] = clipThreshold
                    }
                }
                val redistPerBin = excess / 256
                for (i in 0 until 256) {
                    hist[i] += redistPerBin
                }

                // Build CDF and map
                val cdf = IntArray(256)
                cdf[0] = hist[0]
                for (i in 1 until 256) {
                    cdf[i] = cdf[i - 1] + hist[i]
                }
                val cdfMin = cdf.first { it > 0 }
                val denom = (totalPixels - cdfMin).coerceAtLeast(1)

                // Apply mapping
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val idx = y * width + x
                        val bin = luma[idx].roundToInt().coerceIn(0, 255)
                        luma[idx] = ((cdf[bin] - cdfMin).toFloat() / denom * 255f).coerceIn(0f, 255f)
                    }
                }
            }
        }
    }

    // Step 4: Unsharp masking with 3x3 blur kernel
    private fun unsharpMask(luma: FloatArray, width: Int, height: Int, amount: Float) {
        val blurred = FloatArray(luma.size)
        // Simple 3x3 box blur
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += luma[(y + dy) * width + (x + dx)]
                    }
                }
                blurred[y * width + x] = sum / 9f
            }
        }
        // Apply sharpening
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val edge = luma[idx] - blurred[idx]
                luma[idx] = (luma[idx] + amount * edge).coerceIn(0f, 255f)
            }
        }
    }

    // Step 5: Gamma correction for thermal dot gain
    private fun gammaCorrection(luma: FloatArray, gamma: Float) {
        val invGamma = 1f / gamma
        for (i in luma.indices) {
            luma[i] = (255f * (luma[i] / 255f).pow(invGamma)).coerceIn(0f, 255f)
        }
    }

    // Step 6: Bill Atkinson dithering (diffuses 6/8 = 75% of error)
    private fun atkinsonDither(luma: FloatArray, width: Int, height: Int): BooleanArray {
        val result = BooleanArray(width * height) // true = black
        val work = luma.copyOf()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = work[idx]
                val newVal = if (oldVal < 128f) 0f else 255f
                result[idx] = newVal == 0f // black = true
                val error = (oldVal - newVal) / 8f // 1/8th of error

                // Atkinson kernel: distribute to 6 neighbors (6/8 = 75%)
                if (x + 1 < width) work[idx + 1] += error
                if (x + 2 < width) work[idx + 2] += error
                if (y + 1 < height) {
                    if (x - 1 >= 0) work[(y + 1) * width + (x - 1)] += error
                    work[(y + 1) * width + x] += error
                    if (x + 1 < width) work[(y + 1) * width + (x + 1)] += error
                }
                if (y + 2 < height) {
                    work[(y + 2) * width + x] += error
                }
            }
        }
        return result
    }

    // Step 7: Pack binary pixels into bytes (MSB = leftmost pixel)
    private fun packBits(pixels: BooleanArray, width: Int, height: Int): ByteArray {
        val bytesPerLine = (width + 7) / 8
        val result = ByteArray(bytesPerLine * height)

        for (y in 0 until height) {
            for (byteIdx in 0 until bytesPerLine) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < width && pixels[y * width + x]) {
                        byte = byte or (1 shl (7 - bit))
                    }
                }
                result[y * bytesPerLine + byteIdx] = byte.toByte()
            }
        }
        return result
    }
}
