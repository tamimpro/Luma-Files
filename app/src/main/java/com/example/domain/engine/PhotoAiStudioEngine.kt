package com.example.domain.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class AiSmartGradeResult(
    val adjustments: PhotoAdjustments,
    val recommendedFilterId: String,
    val summary: String
)

class PhotoAiStudioEngine(
    private val personalApiKeyProvider: () -> String = { "" }
) {

    private val restClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "PhotoAiStudioEngine"
        private const val PRIMARY_GRADE_MODEL = "gemini-3.8-flash"
        private const val FALLBACK_GRADE_MODEL = "gemini-3.5-flash-lite"
        private const val PRIMARY_REMASTER_MODEL = "gemini-nano-banana-2.1"
        private const val FALLBACK_REMASTER_MODEL = "gemini-3.1-flash-lite-image"

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
         * Detects transient overload / high-demand errors suitable for automatic retry & fast-model fallback.
         */
        fun isHighDemandOrTransientError(throwable: Throwable): Boolean {
            val msg = buildErrorChainMessage(throwable).lowercase()
            return msg.contains("503") ||
                msg.contains("high demand") ||
                msg.contains("unavailable") ||
                msg.contains("overloaded") ||
                msg.contains("temporarily") ||
                msg.contains("429") ||
                msg.contains("rate limit") ||
                msg.contains("resource_exhausted") ||
                msg.contains("deadline") ||
                throwable is TimeoutCancellationException
        }

        /**
         * Detects Firebase App Check / sideloaded APK attestation / billing errors where a personal Gemini API key resolves access.
         */
        fun isAppCheckOrAuthError(throwable: Throwable): Boolean {
            val msg = buildErrorChainMessage(throwable).lowercase()
            return msg.contains("app check") ||
                msg.contains("appcheck") ||
                msg.contains("403") ||
                msg.contains("401") ||
                msg.contains("attestation") ||
                msg.contains("firebaseapp") ||
                msg.contains("not initialized") ||
                msg.contains("permission_denied") ||
                msg.contains("unauthenticated") ||
                msg.contains("billing") ||
                msg.contains("blaze") ||
                msg.contains("api key") ||
                msg.contains("quota")
        }

        /**
         * Produces a concise user-facing error message and whether to show the one-tap "Set API Key" action.
         */
        fun formatFriendlyAiError(throwable: Throwable, hasPersonalKey: Boolean): Pair<String, Boolean> {
            val raw = buildErrorChainMessage(throwable)
            val lower = raw.lowercase()
            return when {
                isAppCheckOrAuthError(throwable) && !hasPersonalKey -> {
                    "Firebase AI requires App Check (unavailable in sideloaded APKs). Add a free Gemini API key to enable AI Enhance." to true
                }
                isAppCheckOrAuthError(throwable) && hasPersonalKey -> {
                    "Personal Gemini API key rejected or quota exceeded. Update your key in Tools → AI Photo Engine." to true
                }
                isHighDemandOrTransientError(throwable) -> {
                    "AI servers are busy right now after retries. Wait a moment and tap again." to !hasPersonalKey
                }
                lower.contains("unable to resolve host") || lower.contains("network") -> {
                    "No internet connection for AI Enhance." to false
                }
                else -> {
                    (throwable.localizedMessage?.take(140) ?: "AI Enhance failed. Please try again.") to !hasPersonalKey
                }
            }
        }

        private fun buildErrorChainMessage(t: Throwable): String {
            val sb = StringBuilder()
            var cur: Throwable? = t
            var depth = 0
            while (cur != null && depth < 5) {
                sb.append(cur.javaClass.simpleName).append(": ").append(cur.message.orEmpty()).append(" | ")
                cur = cur.cause
                depth++
            }
            return sb.toString()
        }

        fun buildCombinedColorMatrixArray(
            preset: PhotoFilterPreset,
            filterIntensity: Float,
            adjustments: PhotoAdjustments
        ): FloatArray {
            val t = filterIntensity.coerceIn(0f, 1f)
            val blendedFilter = FloatArray(20) { i ->
                IDENTITY_MATRIX[i] + (preset.matrix[i] - IDENTITY_MATRIX[i]) * t
            }

            val c = adjustments.contrast.coerceIn(0.5f, 1.8f)
            val b = (adjustments.brightness.coerceIn(-0.5f, 0.5f) * 255f)
            val translate = (-0.5f * c + 0.5f) * 255f + b
            val contrastBright = floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )

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

            val w = adjustments.warmth.coerceIn(-0.5f, 0.5f)
            val warmMat = floatArrayOf(
                1f + w * 0.28f, 0f, 0f, 0f, w * 18f,
                0f, 1f + w * 0.04f, 0f, 0f, w * 4f,
                0f, 0f, 1f - w * 0.28f, 0f, -w * 18f,
                0f, 0f, 0f, 1f, 0f
            )

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

    private val gradeSchema by lazy {
        Schema.obj(
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
    }

    private val aiSmartGradePrimaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_GRADE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = gradeSchema
            }
        )
    }

    private val aiSmartGradeFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_GRADE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = gradeSchema
            }
        )
    }

    private val aiRemasterPrimaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_REMASTER_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val aiRemasterFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_REMASTER_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    /**
     * Real Cloud AI Auto-Enhance with automatic high-demand retry + fast-model fallback.
     * Uses Firebase AI Logic (`Firebase.ai`) by default, or the user's optional personal Gemini API key
     * when configured in Settings (for sideloaded APKs outside AI Studio).
     */
    suspend fun analyzeAndAutoEnhance(
        sourceBitmap: Bitmap,
        onProgress: (String) -> Unit = {}
    ): Result<AiSmartGradeResult> = withContext(Dispatchers.IO) {
        val probeBitmap = downscaleForAnalysis(sourceBitmap, maxEdge = 1024)
        val personalKey = personalApiKeyProvider().trim()
        val prompt = "Analyze this photo as a master colorist. Return balanced studio auto-enhancement " +
            "parameters (brightness, contrast, saturation, warmth, clarity, skinSmooth, vignette) " +
            "and the best matching filter preset id to make the photo look vivid, natural, and crisp."

        try {
            val jsonText = if (personalKey.isNotBlank()) {
                executeWithRetryAndFallback(
                    primaryLabel = "Analyzing photo…",
                    fallbackLabel = "High demand — switching to fast model…",
                    onProgress = onProgress,
                    primaryCall = { callRestGradeModel(personalKey, PRIMARY_GRADE_MODEL, probeBitmap, prompt) },
                    fallbackCall = { callRestGradeModel(personalKey, FALLBACK_GRADE_MODEL, probeBitmap, prompt) }
                )
            } else {
                executeWithRetryAndFallback(
                    primaryLabel = "Analyzing photo…",
                    fallbackLabel = "High demand — switching to fast model…",
                    onProgress = onProgress,
                    primaryCall = {
                        val resp = withTimeout(60_000L) {
                            aiSmartGradePrimaryModel.generateContent(content { image(probeBitmap); text(prompt) })
                        }
                        resp.text ?: throw IllegalStateException("AI returned an empty grading response.")
                    },
                    fallbackCall = {
                        val resp = withTimeout(60_000L) {
                            aiSmartGradeFallbackModel.generateContent(content { image(probeBitmap); text(prompt) })
                        }
                        resp.text ?: throw IllegalStateException("AI returned an empty grading response.")
                    }
                )
            }

            Result.success(parseGradeJson(jsonText))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Auto Enhance failed", e)
            Result.failure(e)
        } finally {
            if (probeBitmap !== sourceBitmap && !probeBitmap.isRecycled) {
                probeBitmap.recycle()
            }
        }
    }

    /**
     * Generative AI Photo Remaster with automatic high-demand retry + fast-image-model fallback.
     * Uses Firebase AI Logic (`gemini-nano-banana-2.1` -> `gemini-3.1-flash-lite-image`) by default,
     * or the user's optional personal Gemini API key when configured in Settings.
     */
    suspend fun generateAiRemasteredPhoto(
        sourceBitmap: Bitmap,
        stylePrompt: String = "Enhance lighting, sharpness, dynamic range, and natural color clarity while preserving every detail and facial feature faithfully.",
        onProgress: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val probeBitmap = downscaleForAnalysis(sourceBitmap, maxEdge = 1536)
        val personalKey = personalApiKeyProvider().trim()

        try {
            val resultBitmap = if (personalKey.isNotBlank()) {
                executeWithRetryAndFallback(
                    primaryLabel = "Remastering with AI…",
                    fallbackLabel = "High demand — switching to fast image model…",
                    onProgress = onProgress,
                    primaryCall = { callRestImageRemaster(personalKey, PRIMARY_REMASTER_MODEL, probeBitmap, stylePrompt) },
                    fallbackCall = { callRestImageRemaster(personalKey, FALLBACK_REMASTER_MODEL, probeBitmap, stylePrompt) }
                )
            } else {
                executeWithRetryAndFallback(
                    primaryLabel = "Remastering with AI…",
                    fallbackLabel = "High demand — switching to fast image model…",
                    onProgress = onProgress,
                    primaryCall = {
                        val resp = withTimeout(60_000L) {
                            aiRemasterPrimaryModel.generateContent(content { image(probeBitmap); text(stylePrompt) })
                        }
                        resp.candidates.firstOrNull()?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                            ?: throw IllegalStateException("AI model did not return an enhanced image.")
                    },
                    fallbackCall = {
                        val resp = withTimeout(60_000L) {
                            aiRemasterFallbackModel.generateContent(content { image(probeBitmap); text(stylePrompt) })
                        }
                        resp.candidates.firstOrNull()?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                            ?: throw IllegalStateException("AI model did not return an enhanced image.")
                    }
                )
            }
            Result.success(resultBitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Remaster failed", e)
            Result.failure(e)
        } finally {
            if (probeBitmap !== sourceBitmap && !probeBitmap.isRecycled) {
                probeBitmap.recycle()
            }
        }
    }

    /**
     * Validates an optional user-supplied Gemini API key with a minimal request.
     */
    suspend fun verifyPersonalApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("API key is empty."))
        try {
            val payload = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply OK")))
                )
            )
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$FALLBACK_GRADE_MODEL:generateContent?key=$trimmed")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            withTimeout(25_000L) {
                restClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = extractRestErrorMessage(body, resp.code)
                        throw IllegalStateException("HTTP ${resp.code}: $errMsg")
                    }
                    Result.success("Connected ($FALLBACK_GRADE_MODEL)")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeWithRetryAndFallback(
        primaryLabel: String,
        fallbackLabel: String,
        onProgress: (String) -> Unit,
        primaryCall: suspend () -> T,
        fallbackCall: suspend () -> T
    ): T {
        onProgress(primaryLabel)
        var lastError: Exception? = null

        // Attempt 1 & 2 on primary model (retry once with short backoff on transient 503/high demand)
        for (attempt in 1..2) {
            try {
                return primaryCall()
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
                if (!isHighDemandOrTransientError(e)) {
                    // Non-transient error (e.g., App Check 403 / invalid key / bad request) — don't stall user
                    throw e
                }
                if (attempt == 1) {
                    onProgress("High demand — retrying…")
                    delay(1400L)
                }
            }
        }

        // Attempt 3 & 4: automatic fallback to faster, higher-capacity Gemini 3.x lite model
        onProgress(fallbackLabel)
        for (attempt in 1..2) {
            try {
                return fallbackCall()
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
                if (!isHighDemandOrTransientError(e) || attempt == 2) break
                delay(1800L)
            }
        }

        throw lastError ?: IllegalStateException("AI request failed.")
    }

    private suspend fun callRestGradeModel(
        apiKey: String,
        modelName: String,
        bitmap: Bitmap,
        prompt: String
    ): String = withTimeout(60_000L) {
        val jpegB64 = bitmapToBase64Jpeg(bitmap, quality = 85)
        val filterEnum = JSONArray().apply { FILTER_PRESETS.forEach { put(it.id) } }
        val schemaObj = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("brightness", JSONObject().put("type", "NUMBER"))
                    .put("contrast", JSONObject().put("type", "NUMBER"))
                    .put("saturation", JSONObject().put("type", "NUMBER"))
                    .put("warmth", JSONObject().put("type", "NUMBER"))
                    .put("clarity", JSONObject().put("type", "NUMBER"))
                    .put("skinSmooth", JSONObject().put("type", "NUMBER"))
                    .put("vignette", JSONObject().put("type", "NUMBER"))
                    .put("recommendedFilterId", JSONObject().put("type", "STRING").put("enum", filterEnum))
                    .put("summary", JSONObject().put("type", "STRING"))
            )
            .put(
                "required",
                JSONArray(listOf("brightness", "contrast", "saturation", "warmth", "clarity", "skinSmooth", "vignette", "recommendedFilterId", "summary"))
            )

        val parts = JSONArray()
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegB64)))
            .put(JSONObject().put("text", prompt))

        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schemaObj)
            )

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        restClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${extractRestErrorMessage(body, resp.code)}")
            }
            val root = JSONObject(body)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty grading response.")
        }
    }

    private suspend fun callRestImageRemaster(
        apiKey: String,
        modelName: String,
        bitmap: Bitmap,
        stylePrompt: String
    ): Bitmap = withTimeout(60_000L) {
        val jpegB64 = bitmapToBase64Jpeg(bitmap, quality = 90)
        val parts = JSONArray()
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegB64)))
            .put(JSONObject().put("text", stylePrompt))

        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("IMAGE"))
            )

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        restClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${extractRestErrorMessage(body, resp.code)}")
            }
            val root = JSONObject(body)
            val respParts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            if (respParts != null) {
                for (i in 0 until respParts.length()) {
                    val part = respParts.optJSONObject(i) ?: continue
                    val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    val b64 = inline?.optString("data").orEmpty()
                    if (b64.isNotBlank()) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decoded != null) return@use decoded
                    }
                }
            }
            throw IllegalStateException("AI model did not return an enhanced image.")
        }
    }

    private fun extractRestErrorMessage(body: String, code: Int): String {
        return runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").ifBlank { "Request failed with HTTP $code" }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseGradeJson(jsonText: String): AiSmartGradeResult {
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
        return AiSmartGradeResult(
            adjustments = adjustments,
            recommendedFilterId = filterId,
            summary = summary
        )
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

        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cmArray = buildCombinedColorMatrixArray(preset, filterIntensity, adjustments)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(cmArray))
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)

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
