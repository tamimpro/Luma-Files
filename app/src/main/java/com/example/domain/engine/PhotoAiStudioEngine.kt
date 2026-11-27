package com.example.domain.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import com.example.domain.model.PhotoAdjustments
import com.example.domain.model.PhotoFilterCategory
import com.example.domain.model.PhotoFilterPreset
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class AiSmartGradeResult(
    val adjustments: PhotoAdjustments,
    val recommendedFilterId: String,
    val summary: String
)

class PhotoAiStudioEngine {

    companion object {
        private const val TAG = "PhotoAiStudioEngine"

        val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        /**
         * 18 Popular Cinematic & Beauty Photo Filters (+ Original).
         */
        val FILTER_PRESETS: List<PhotoFilterPreset> = listOf(
            PhotoFilterPreset(
                id = "original",
                name = "Original",
                category = PhotoFilterCategory.ALL,
                matrix = IDENTITY_MATRIX
            ),
            // ---- CINEMATIC FILTERS (12) ----
            PhotoFilterPreset(
                id = "teal_orange",
                name = "Teal & Orange",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.18f, 0.06f, -0.08f, 0f, 8f,
                    0.02f, 1.04f, 0.04f, 0f, 0f,
                    -0.10f, 0.12f, 1.16f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.22f
            ),
            PhotoFilterPreset(
                id = "kodak_portra",
                name = "Kodak Portra",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.08f, 0.04f, 0.01f, 0f, 10f,
                    0.02f, 1.04f, 0.02f, 0f, 6f,
                    -0.02f, 0.02f, 0.94f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.14f
            ),
            PhotoFilterPreset(
                id = "fuji_velvia",
                name = "Fuji Velvia",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.24f, -0.06f, -0.04f, 0f, -4f,
                    -0.05f, 1.22f, -0.03f, 0f, -4f,
                    -0.04f, -0.04f, 1.26f, 0f, -4f,
                    0f, 0f, 0f, 1f, 0f
                )
            ),
            PhotoFilterPreset(
                id = "golden_hour",
                name = "Golden Hour",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.22f, 0.08f, 0f, 0f, 14f,
                    0.04f, 1.06f, 0f, 0f, 6f,
                    0f, 0f, 0.80f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.15f,
                vignetteStrength = 0.18f
            ),
            PhotoFilterPreset(
                id = "moody_cinema",
                name = "Moody Cinema",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    0.96f, 0.05f, 0.05f, 0f, -14f,
                    0.02f, 0.94f, 0.06f, 0f, -12f,
                    0.04f, 0.08f, 1.02f, 0f, -4f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.38f
            ),
            PhotoFilterPreset(
                id = "matte_film",
                name = "Matte Film",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    0.90f, 0.04f, 0.02f, 0f, 22f,
                    0.02f, 0.90f, 0.04f, 0f, 20f,
                    0.02f, 0.04f, 0.88f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.16f
            ),
            PhotoFilterPreset(
                id = "vintage_35mm",
                name = "Vintage 35mm",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.05f, 0.18f, 0.06f, 0f, 12f,
                    0.08f, 0.98f, 0.06f, 0f, 8f,
                    0.02f, 0.12f, 0.76f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.28f
            ),
            PhotoFilterPreset(
                id = "noir_bw",
                name = "Noir B&W",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    0.42f, 0.72f, 0.14f, 0f, -24f,
                    0.42f, 0.72f, 0.14f, 0f, -24f,
                    0.42f, 0.72f, 0.14f, 0f, -24f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.35f
            ),
            PhotoFilterPreset(
                id = "cyberpunk",
                name = "Cyberpunk",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.24f, -0.08f, 0.18f, 0f, -6f,
                    -0.06f, 0.96f, 0.14f, 0f, 0f,
                    0.16f, 0.10f, 1.32f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.25f
            ),
            PhotoFilterPreset(
                id = "cold_arctic",
                name = "Cold Arctic",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    0.88f, 0.02f, 0.06f, 0f, -4f,
                    0.02f, 1.02f, 0.08f, 0f, 2f,
                    0.04f, 0.08f, 1.24f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            ),
            PhotoFilterPreset(
                id = "emerald_film",
                name = "Emerald Film",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    0.92f, 0.04f, 0.02f, 0f, -4f,
                    0.02f, 1.14f, 0.06f, 0f, 4f,
                    0.02f, 0.08f, 1.04f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                ),
                vignetteStrength = 0.22f
            ),
            PhotoFilterPreset(
                id = "hdr_vivid",
                name = "HDR Vivid",
                category = PhotoFilterCategory.CINEMATIC,
                matrix = floatArrayOf(
                    1.28f, -0.09f, -0.07f, 0f, -12f,
                    -0.08f, 1.28f, -0.08f, 0f, -12f,
                    -0.07f, -0.09f, 1.28f, 0f, -12f,
                    0f, 0f, 0f, 1f, 0f
                )
            ),
            // ---- BEAUTY & PORTRAIT FILTERS (6) ----
            PhotoFilterPreset(
                id = "soft_beauty",
                name = "Soft Beauty",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    1.07f, 0.03f, 0.02f, 0f, 12f,
                    0.02f, 1.05f, 0.02f, 0f, 10f,
                    0.01f, 0.02f, 1.04f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.45f
            ),
            PhotoFilterPreset(
                id = "porcelain_skin",
                name = "Porcelain Skin",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    1.08f, 0.04f, 0.04f, 0f, 18f,
                    0.03f, 1.06f, 0.04f, 0f, 16f,
                    0.03f, 0.04f, 1.09f, 0f, 18f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.55f
            ),
            PhotoFilterPreset(
                id = "warm_portrait",
                name = "Warm Portrait",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    1.12f, 0.04f, 0f, 0f, 10f,
                    0.02f, 1.04f, 0.01f, 0f, 6f,
                    0f, 0.01f, 0.94f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.35f,
                vignetteStrength = 0.12f
            ),
            PhotoFilterPreset(
                id = "natural_radiance",
                name = "Natural Glow",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    1.10f, 0.02f, 0f, 0f, 8f,
                    0.01f, 1.08f, 0.01f, 0f, 8f,
                    0f, 0.01f, 1.05f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.30f
            ),
            PhotoFilterPreset(
                id = "sunset_rose",
                name = "Sunset Rose",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    1.16f, 0.06f, 0.04f, 0f, 12f,
                    0.02f, 0.98f, 0.04f, 0f, 4f,
                    0.06f, 0.02f, 1.06f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.35f,
                vignetteStrength = 0.14f
            ),
            PhotoFilterPreset(
                id = "pastel_dream",
                name = "Pastel Dream",
                category = PhotoFilterCategory.BEAUTY,
                matrix = floatArrayOf(
                    0.96f, 0.06f, 0.06f, 0f, 22f,
                    0.05f, 0.98f, 0.06f, 0f, 20f,
                    0.06f, 0.06f, 1.02f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f
                ),
                glowStrength = 0.40f
            )
        )

        /**
         * Builds the combined 4x5 color matrix for any filter preset + intensity + manual adjustments.
         * Pure math helper — safe for JVM unit tests.
         */
        fun buildCombinedColorMatrixArray(
            preset: PhotoFilterPreset,
            filterIntensity: Float,
            adjustments: PhotoAdjustments
        ): FloatArray {
            val t = filterIntensity.coerceIn(0f, 1f)
            val blendedFilter = FloatArray(20) { i ->
                IDENTITY_MATRIX[i] + (preset.matrix[i] - IDENTITY_MATRIX[i]) * t
            }

            // Contrast & brightness matrix
            val c = adjustments.contrast.coerceIn(0.5f, 1.8f)
            val b = (adjustments.brightness.coerceIn(-0.5f, 0.5f) * 255f)
            val translate = (-0.5f * c + 0.5f) * 255f + b
            val contrastBright = floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )

            // Saturation matrix (Rec. 709 luma weights)
            val s = adjustments.saturation.coerceIn(0f, 2f)
            val invS = 1f - s
            val rW = 0.2126f * invS
            val gW = 0.7152f * invS
            val bW = 0.0722f * invS
            val satMat = floatArrayOf(
                rW + s, gW, bW, 0f, 0f,
                rW, gW + s, bW, 0f, 0f,
                rW, gW, bW + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )

            // Warmth / temperature shift
            val w = adjustments.warmth.coerceIn(-0.5f, 0.5f)
            val warmMat = floatArrayOf(
                1f + w * 0.28f, 0f, 0f, 0f, w * 18f,
                0f, 1f + w * 0.04f, 0f, 0f, w * 4f,
                0f, 0f, 1f - w * 0.28f, 0f, -w * 18f,
                0f, 0f, 0f, 1f, 0f
            )

            // Clarity boosts micro-contrast slightly
            val cl = adjustments.clarity.coerceIn(0f, 1f)
            val clScale = 1f + cl * 0.16f
            val clShift = (-0.5f * clScale + 0.5f) * 255f
            val clarityMat = floatArrayOf(
                clScale, 0f, 0f, 0f, clShift,
                0f, clScale, 0f, 0f, clShift,
                0f, 0f, clScale, 0f, clShift,
                0f, 0f, 0f, 1f, 0f
            )

            val m1 = multiply4x5(contrastBright, blendedFilter)
            val m2 = multiply4x5(satMat, m1)
            val m3 = multiply4x5(warmMat, m2)
            return multiply4x5(clarityMat, m3)
        }

        private fun multiply4x5(a: FloatArray, b: FloatArray): FloatArray {
            val out = FloatArray(20)
            for (row in 0 until 4) {
                val r = row * 5
                for (col in 0 until 4) {
                    out[r + col] =
                        a[r + 0] * b[0 + col] +
                        a[r + 1] * b[5 + col] +
                        a[r + 2] * b[10 + col] +
                        a[r + 3] * b[15 + col]
                }
                out[r + 4] =
                    a[r + 0] * b[4] +
                    a[r + 1] * b[9] +
                    a[r + 2] * b[14] +
                    a[r + 3] * b[19] +
                    a[r + 4]
            }
            return out
        }
    }

    // Lazy Firebase AI Logic model for instant smart photo auto-grading (no Blaze billing required)
    private val aiSmartGradeModel by lazy {
        val gradeSchema = Schema.obj(
            properties = mapOf(
                "brightness" to Schema.double(description = "Recommended brightness offset between -0.25 and 0.25"),
                "contrast" to Schema.double(description = "Recommended contrast multiplier between 0.85 and 1.35"),
                "saturation" to Schema.double(description = "Recommended saturation multiplier between 0.80 and 1.40"),
                "warmth" to Schema.double(description = "Recommended color temperature warmth offset between -0.25 and 0.25"),
                "clarity" to Schema.double(description = "Recommended clarity crispness between 0.0 and 0.65"),
                "skinSmooth" to Schema.double(description = "Recommended soft portrait beauty smoothing between 0.0 and 0.55 (0 if landscape/non-portrait)"),
                "vignette" to Schema.double(description = "Recommended subtle vignette between 0.0 and 0.30"),
                "recommendedFilterId" to Schema.enumeration(
                    values = FILTER_PRESETS.map { it.id },
                    description = "Best matching cinematic or beauty filter preset id"
                ),
                "summary" to Schema.string(description = "Ultra-short 3-6 word summary of the enhancement applied")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = gradeSchema
            }
        )
    }

    // Lazy Firebase AI Logic model for generative AI photo remastering
    private val aiRemasterModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    /**
     * Real Cloud AI Auto-Enhance using Firebase AI Logic (`gemini-3.8-flash`).
     * Analyzes exposure, dynamic range, white balance, and subject (portrait vs landscape) to return
     * optimal studio grading parameters applied losslessly at full resolution.
     */
    suspend fun analyzeAndAutoEnhance(sourceBitmap: Bitmap): Result<AiSmartGradeResult> = withContext(Dispatchers.IO) {
        try {
            val probeBitmap = downscaleForAnalysis(sourceBitmap, maxEdge = 1024)
            val response = withTimeout(60_000L) {
                aiSmartGradeModel.generateContent(
                    content {
                        image(probeBitmap)
                        text(
                            "Analyze this photo as a master colorist. Return balanced studio auto-enhancement " +
                                "parameters (brightness, contrast, saturation, warmth, clarity, skinSmooth, vignette) " +
                                "and the best matching filter preset id to make the photo look vivid, natural, and crisp."
                        )
                    }
                )
            }
            if (probeBitmap !== sourceBitmap) probeBitmap.recycle()

            val jsonText = response.text ?: throw IllegalStateException("AI returned an empty grading response.")
            val json = JSONObject(jsonText)
            val adjustments = PhotoAdjustments(
                brightness = json.optDouble("brightness", 0.04).toFloat().coerceIn(-0.3f, 0.3f),
                contrast = json.optDouble("contrast", 1.12).toFloat().coerceIn(0.8f, 1.45f),
                saturation = json.optDouble("saturation", 1.12).toFloat().coerceIn(0.75f, 1.5f),
                warmth = json.optDouble("warmth", 0.03).toFloat().coerceIn(-0.3f, 0.3f),
                clarity = json.optDouble("clarity", 0.25).toFloat().coerceIn(0f, 0.8f),
                skinSmooth = json.optDouble("skinSmooth", 0.0).toFloat().coerceIn(0f, 0.65f),
                vignette = json.optDouble("vignette", 0.10).toFloat().coerceIn(0f, 0.4f)
            )
            val filterId = json.optString("recommendedFilterId", "original")
                .takeIf { id -> FILTER_PRESETS.any { it.id == id } } ?: "original"
            val summary = json.optString("summary", "AI Enhanced").ifBlank { "AI Enhanced" }

            Result.success(
                AiSmartGradeResult(
                    adjustments = adjustments,
                    recommendedFilterId = filterId,
                    summary = summary
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Auto Enhance timed out", e)
            Result.failure(Exception("AI enhance timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Auto Enhance failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generative AI Photo Remaster via Firebase AI Logic (`gemini-nano-banana-2.1`).
     * Surfaces real errors transparently without any mock fallback.
     */
    suspend fun generateAiRemasteredPhoto(
        sourceBitmap: Bitmap,
        stylePrompt: String = "Enhance lighting, sharpness, dynamic range, and natural color clarity while preserving every detail and facial feature faithfully."
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val probeBitmap = downscaleForAnalysis(sourceBitmap, maxEdge = 1536)
            val response = withTimeout(60_000L) {
                aiRemasterModel.generateContent(
                    content {
                        image(probeBitmap)
                        text(stylePrompt)
                    }
                )
            }
            if (probeBitmap !== sourceBitmap) probeBitmap.recycle()

            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            val resultBitmap = imagePart?.image
                ?: throw IllegalStateException("AI model did not return an enhanced image.")

            Result.success(resultBitmap)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Remaster timed out", e)
            Result.failure(Exception("AI remaster timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Remaster failed", e)
            Result.failure(e)
        }
    }

    /**
     * Renders a full-resolution or preview Bitmap with transforms (rotate/flip/crop),
     * selected Cinematic/Beauty filter, manual adjustments, soft beauty glow, and radial vignette.
     */
    fun renderEditedBitmap(
        source: Bitmap,
        preset: PhotoFilterPreset,
        filterIntensity: Float,
        adjustments: PhotoAdjustments,
        rotationDegrees: Int = 0,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        cropAspectRatio: Float? = null
    ): Bitmap {
        // 1. Apply rotation & flips
        val matrix = Matrix()
        if (rotationDegrees % 360 != 0) {
            matrix.postRotate((rotationDegrees % 360).toFloat())
        }
        val sx = if (flipHorizontal) -1f else 1f
        val sy = if (flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            matrix.postScale(sx, sy)
        }
        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Apply optional centered aspect crop
        val cropped = if (cropAspectRatio != null && cropAspectRatio > 0f) {
            val w = transformed.width
            val h = transformed.height
            val currentRatio = w.toFloat() / h.toFloat()
            val (cropW, cropH) = if (currentRatio > cropAspectRatio) {
                (h * cropAspectRatio).toInt().coerceIn(1, w) to h
            } else {
                w to (w / cropAspectRatio).toInt().coerceIn(1, h)
            }
            val startX = ((w - cropW) / 2).coerceAtLeast(0)
            val startY = ((h - cropH) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(transformed, startX, startY, cropW, cropH)
        } else {
            transformed
        }

        // 3. Apply color matrix (filter + adjustments)
        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cmArray = buildCombinedColorMatrixArray(preset, filterIntensity, adjustments)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(cmArray))
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        // 4. Apply Soft Beauty Glow / Skin Smoothing blend if active
        val effectiveSmooth = max(adjustments.skinSmooth, preset.glowStrength * filterIntensity)
            .coerceIn(0f, 0.85f)
        if (effectiveSmooth > 0.02f) {
            val downW = max(16, cropped.width / 10)
            val downH = max(16, cropped.height / 10)
            val softLayer = Bitmap.createScaledBitmap(output, downW, downH, true)
            val upSoft = Bitmap.createScaledBitmap(softLayer, output.width, output.height, true)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (effectiveSmooth * 115f).toInt().coerceIn(8, 120)
            }
            canvas.drawBitmap(upSoft, 0f, 0f, glowPaint)
            softLayer.recycle()
            upSoft.recycle()
        }

        // 5. Apply Radial Vignette if active
        val effectiveVignette = max(adjustments.vignette, preset.vignetteStrength * filterIntensity)
            .coerceIn(0f, 0.85f)
        if (effectiveVignette > 0.02f) {
            val cx = output.width / 2f
            val cy = output.height / 2f
            val radius = max(output.width, output.height) * 0.72f
            val edgeAlpha = (effectiveVignette * 210f).toInt().coerceIn(10, 210)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(edgeAlpha, 0, 0, 0)),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), vignettePaint)
        }

        if (cropped !== transformed && cropped !== source) cropped.recycle()
        if (transformed !== source) transformed.recycle()
        return output
    }

    suspend fun saveBitmapAtomically(
        bitmap: Bitmap,
        targetFile: File,
        quality: Int = 95
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, ".luma_img_${System.nanoTime()}.tmp")
        try {
            val format = if (targetFile.extension.equals("png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(format, quality, fos)
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    private fun downscaleForAnalysis(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / maxDim.toFloat()
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
