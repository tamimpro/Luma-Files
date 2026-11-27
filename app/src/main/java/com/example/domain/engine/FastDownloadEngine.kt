package com.example.domain.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.LumaRepository
import com.example.domain.model.DiagnosticError
import com.example.domain.model.MediaStreamVariant
import com.example.domain.model.ResolvedMediaManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class ContentRangeValidation(
    val isValidPartialRange: Boolean,
    val shouldFallbackToSingleStream: Boolean,
    val startByte: Long,
    val endByte: Long,
    val totalBytes: Long,
    val errorReason: String? = null
)

data class DownloadIntegrityResult(
    val isValid: Boolean,
    val actualSizeBytes: Long,
    val computedSha256: String,
    val failureReason: String? = null
)

data class PersistedDownloadCheckpoint(
    val url: String,
    val etag: String = "",
    val lastModified: String = "",
    val totalBytes: Long = -1L,
    val threadCount: Int = 4,
    val audioStreamUrl: String = "",
    val creatorAttribution: String = "",
    val expectedSha256: String = "",
    val customUserAgent: String = "",
    val resolutionLabel: String = "",
    val refererHeader: String = "",
    val cookieHeader: String = "",
    val isHlsPlaylist: Boolean = false
)

data class HlsStreamInfo(
    val bandwidthBps: Int,
    val widthPx: Int,
    val heightPx: Int,
    val frameRateFps: Int,
    val codecs: String,
    val playlistUrl: String
)

class FastDownloadEngine(
    private val repository: LumaRepository,
    private val cryptoEngine: CryptoVaultEngine,
    private val externalScope: CoroutineScope
) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    companion object {
        private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MAX_AUTO_RETRIES = 3
        private const val MAX_HTML_INSPECT_BYTES = 2 * 1024 * 1024 // 2 MB

        fun validateHttpContentRange(
            httpStatusCode: Int,
            contentRangeHeader: String?,
            requestedStart: Long,
            requestedEnd: Long
        ): ContentRangeValidation {
            if (httpStatusCode == 200) {
                return ContentRangeValidation(
                    isValidPartialRange = false,
                    shouldFallbackToSingleStream = true,
                    startByte = 0L,
                    endByte = -1L,
                    totalBytes = -1L,
                    errorReason = "Server returned HTTP 200 OK instead of 206 Partial Content; using single stream."
                )
            }
            if (httpStatusCode != 206) {
                return ContentRangeValidation(
                    isValidPartialRange = false,
                    shouldFallbackToSingleStream = false,
                    startByte = -1L,
                    endByte = -1L,
                    totalBytes = -1L,
                    errorReason = "HTTP $httpStatusCode for Range bytes=$requestedStart-$requestedEnd"
                )
            }
            if (contentRangeHeader.isNullOrBlank()) {
                return ContentRangeValidation(
                    isValidPartialRange = false,
                    shouldFallbackToSingleStream = true,
                    startByte = -1L,
                    endByte = -1L,
                    totalBytes = -1L,
                    errorReason = "Missing Content-Range header on HTTP 206 response"
                )
            }
            val match = CONTENT_RANGE_REGEX.find(contentRangeHeader.trim())
                ?: return ContentRangeValidation(
                    isValidPartialRange = false,
                    shouldFallbackToSingleStream = false,
                    startByte = -1L,
                    endByte = -1L,
                    totalBytes = -1L,
                    errorReason = "Malformed Content-Range header: '$contentRangeHeader'"
                )

            val actualStart = match.groupValues[1].toLongOrNull() ?: -1L
            val actualEnd = match.groupValues[2].toLongOrNull() ?: -1L
            val totalStr = match.groupValues[3]
            val totalBytes = if (totalStr == "*") -1L else (totalStr.toLongOrNull() ?: -1L)

            if (actualStart != requestedStart || (requestedEnd >= 0 && actualEnd != requestedEnd)) {
                return ContentRangeValidation(
                    isValidPartialRange = false,
                    shouldFallbackToSingleStream = false,
                    startByte = actualStart,
                    endByte = actualEnd,
                    totalBytes = totalBytes,
                    errorReason = "Range boundary mismatch: requested $requestedStart-$requestedEnd, got $actualStart-$actualEnd"
                )
            }

            return ContentRangeValidation(
                isValidPartialRange = true,
                shouldFallbackToSingleStream = false,
                startByte = actualStart,
                endByte = actualEnd,
                totalBytes = totalBytes,
                errorReason = null
            )
        }

        fun verifyDownloadedFileIntegrity(
            file: File,
            expectedTotalBytes: Long,
            cryptoEngine: CryptoVaultEngine,
            expectedSha256: String? = null
        ): DownloadIntegrityResult {
            if (!file.exists() || !file.isFile) {
                return DownloadIntegrityResult(
                    isValid = false,
                    actualSizeBytes = 0L,
                    computedSha256 = "",
                    failureReason = "File not found at '${file.absolutePath}'."
                )
            }
            val actualSize = file.length()
            if (actualSize <= 0L) {
                return DownloadIntegrityResult(
                    isValid = false,
                    actualSizeBytes = 0L,
                    computedSha256 = "",
                    failureReason = "Downloaded file is 0 bytes."
                )
            }
            if (expectedTotalBytes > 0L && actualSize != expectedTotalBytes) {
                return DownloadIntegrityResult(
                    isValid = false,
                    actualSizeBytes = actualSize,
                    computedSha256 = "",
                    failureReason = "Incomplete transfer: expected $expectedTotalBytes bytes, got $actualSize bytes."
                )
            }
            val sha256 = cryptoEngine.computeHash(file, "SHA-256")
            if (!expectedSha256.isNullOrBlank() && !sha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                return DownloadIntegrityResult(
                    isValid = false,
                    actualSizeBytes = actualSize,
                    computedSha256 = sha256,
                    failureReason = "SHA-256 mismatch: expected ${expectedSha256.take(16)}..., got ${sha256.take(16)}..."
                )
            }
            return DownloadIntegrityResult(
                isValid = true,
                actualSizeBytes = actualSize,
                computedSha256 = sha256,
                failureReason = null
            )
        }

        fun selectBestStreamVariant(variants: List<MediaStreamVariant>): MediaStreamVariant? {
            if (variants.isEmpty()) return null
            val audioCapable = variants.filter { it.hasAudio }
            val pool = if (audioCapable.isNotEmpty()) audioCapable else variants
            val watermarkFree = pool.filter { it.isWatermarkFree }
            val candidates = if (watermarkFree.isNotEmpty()) watermarkFree else pool

            return candidates.maxWithOrNull(
                compareBy<MediaStreamVariant> { it.widthPx.toLong() * it.heightPx.toLong() }
                    .thenBy { it.frameRateFps }
                    .thenBy { it.videoBitrateKbps }
                    .thenBy { it.audioBitrateKbps }
            )
        }

        fun detectPlatformName(url: String): String {
            val lower = url.lowercase()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
                lower.contains("tiktok.com") -> "TikTok"
                lower.contains("instagram.com") -> "Instagram"
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> "Facebook"
                lower.contains("twitter.com") || lower.contains("x.com") -> "X"
                lower.contains("reddit.com") || lower.contains("redd.it") -> "Reddit"
                lower.contains("vimeo.com") -> "Vimeo"
                lower.contains(".m3u8") -> "HLS Stream"
                lower.contains(".mpd") -> "DASH Stream"
                else -> "Web"
            }
        }

        fun computeResumeOffset(partFile: File, expectedTotalBytes: Long): Long {
            if (!partFile.exists() || !partFile.isFile) return 0L
            val len = partFile.length().coerceAtLeast(0L)
            return if (expectedTotalBytes > 0L && len > expectedTotalBytes) 0L else len
        }

        /**
         * Decodes RFC-5987 (`filename*=UTF-8''...`) and standard `filename="..."` Content-Disposition headers.
         */
        fun parseContentDispositionFilename(contentDisposition: String?): String? {
            if (contentDisposition.isNullOrBlank()) return null
            val rfc5987 = Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;\s]+)""")
                .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
            if (!rfc5987.isNullOrBlank()) {
                return runCatching { URLDecoder.decode(rfc5987, "UTF-8") }.getOrDefault(rfc5987)
            }
            val quoted = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
            if (!quoted.isNullOrBlank()) return quoted
            val bare = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
            return bare?.takeIf { it.isNotBlank() }
        }

        /**
         * Parses an HLS Master Playlist (#EXT-X-STREAM-INF) into concrete stream variants with resolved URLs.
         */
        fun parseHlsMasterPlaylist(masterUrl: String, playlistBody: String): List<HlsStreamInfo> {
            val lines = playlistBody.lines().map { it.trim() }
            val result = mutableListOf<HlsStreamInfo>()
            var idx = 0
            while (idx < lines.size) {
                val line = lines[idx]
                if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                    val attrs = line.substringAfter(':')
                    val bw = Regex("""BANDWIDTH=(\d+)""").find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(attrs)
                    val w = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val h = resMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                    val fps = Regex("""FRAME-RATE=([\d.]+)""").find(attrs)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt() ?: 30
                    val codecs = Regex("""CODECS="([^"]+)"""").find(attrs)?.groupValues?.getOrNull(1) ?: "H.264"
                    var nextIdx = idx + 1
                    while (nextIdx < lines.size && (lines[nextIdx].isEmpty() || lines[nextIdx].startsWith("#"))) {
                        nextIdx++
                    }
                    if (nextIdx < lines.size) {
                        val resolved = resolveRelativeUrl(masterUrl, lines[nextIdx])
                        result.add(HlsStreamInfo(bw, w, h, fps, codecs, resolved))
                    }
                    idx = nextIdx
                }
                idx++
            }
            return result.sortedByDescending { it.widthPx.toLong() * it.heightPx.toLong() * 1000L + it.bandwidthBps }
        }

        /**
         * Extracts media segment URLs from an HLS Media Playlist (.m3u8).
         */
        fun parseHlsMediaSegments(playlistUrl: String, playlistBody: String): List<String> {
            val segments = mutableListOf<String>()
            for (rawLine in playlistBody.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                segments.add(resolveRelativeUrl(playlistUrl, line))
            }
            return segments
        }

        fun resolveRelativeUrl(baseUrl: String, relative: String): String {
            if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
            return runCatching { URI(baseUrl).resolve(relative).toString() }.getOrDefault(relative)
        }

        fun extractYouTubeVideoId(url: String): String? {
            val patterns = listOf(
                Regex("""(?:v=|/)([0-9A-Za-z_-]{11})(?:[?&/#]|$)"""),
                Regex("""youtu\.be/([0-9A-Za-z_-]{11})"""),
                Regex("""youtube\.com/shorts/([0-9A-Za-z_-]{11})""")
            )
            for (p in patterns) {
                val id = p.find(url)?.groupValues?.getOrNull(1)
                if (!id.isNullOrBlank()) return id
            }
            return null
        }

        fun sanitizeFileName(raw: String, fallback: String): String {
            val cleaned = raw
                .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(72)
            return cleaned.ifBlank { fallback }
        }
    }

    fun verifyDownloadedFileIntegrity(
        file: File,
        expectedTotalBytes: Long,
        expectedSha256: String? = null
    ): DownloadIntegrityResult = verifyDownloadedFileIntegrity(
        file = file,
        expectedTotalBytes = expectedTotalBytes,
        cryptoEngine = cryptoEngine,
        expectedSha256 = expectedSha256
    )

    fun recoverPersistentQueueOnLaunch(onDiagnosticError: (DiagnosticError) -> Unit) {
        externalScope.launch(Dispatchers.IO) {
            val allTasks = repository.getAllDownloadsSnapshot()
            val interrupted = allTasks.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" }
            for (task in interrupted) {
                if (activeJobs[task.id]?.isActive != true) {
                    startOrResumeDownload(task.id, onDiagnosticError)
                }
            }
        }
    }

    /**
     * Resolves any direct URL, HLS playlist, or social video link into genuine verified streams.
     * NEVER fabricates fake quality options or downloads an HTML webpage as .mp4.
     * If a page requires JavaScript or login, flags `requiresBrowserSniffer = true`.
     */
    suspend fun resolveMediaStreamManifest(
        url: String,
        referer: String = "",
        cookies: String = "",
        userAgent: String = DEFAULT_USER_AGENT
    ): Result<ResolvedMediaManifest> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                "Enter a valid http:// or https:// URL."
            }
            val platform = detectPlatformName(cleanUrl)

            // 1. Dedicated real extractors for major platforms
            when (platform) {
                "YouTube" -> {
                    val yt = extractYouTubeManifest(cleanUrl)
                    if (yt != null && yt.variants.isNotEmpty()) return@withContext Result.success(yt)
                }
                "TikTok" -> {
                    val tt = extractTikTokManifest(cleanUrl)
                    if (tt != null && tt.variants.isNotEmpty()) return@withContext Result.success(tt)
                }
                "Reddit" -> {
                    val rd = extractRedditManifest(cleanUrl)
                    if (rd != null && rd.variants.isNotEmpty()) return@withContext Result.success(rd)
                }
            }

            // 2. Direct HLS .m3u8 playlist check
            if (cleanUrl.lowercase().contains(".m3u8")) {
                val hlsManifest = extractHlsManifest(cleanUrl, referer, cookies, userAgent)
                if (hlsManifest != null) return@withContext Result.success(hlsManifest)
            }

            // 3. Probe headers with lightweight HEAD, falling back to GET if HEAD is blocked
            val probe = probeRemoteUrl(cleanUrl, referer, cookies, userAgent)
            val ct = probe.contentType

            if (ct.contains("mpegurl") || cleanUrl.lowercase().contains(".m3u8")) {
                val hlsManifest = extractHlsManifest(probe.finalUrl, referer, cookies, userAgent)
                if (hlsManifest != null) return@withContext Result.success(hlsManifest)
            }

            // 4. If response is an HTML webpage, inspect full body for real embedded media URLs
            if (ct.contains("text/html") || ct.contains("application/xhtml")) {
                val htmlBody = fetchTextBody(probe.finalUrl, referer, cookies, DESKTOP_USER_AGENT)
                val extracted = extractMediaFromHtmlPage(probe.finalUrl, platform, htmlBody, cookies)
                if (extracted != null) {
                    return@withContext Result.success(extracted)
                }
                // Honest signal: do NOT fake a video stream or save HTML as .mp4!
                val pageTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(htmlBody)?.groupValues?.getOrNull(1)?.trim() ?: platform
                return@withContext Result.success(
                    ResolvedMediaManifest(
                        sourceUrl = cleanUrl,
                        platformName = platform,
                        title = pageTitle.take(64),
                        creatorAttribution = runCatching { URI(cleanUrl).host ?: platform }.getOrDefault(platform),
                        suggestedFileName = "${sanitizeFileName(pageTitle, "video")}.mp4",
                        isSocialPlatform = true,
                        variants = emptyList(),
                        recommendedVariantId = "",
                        requiresBrowserSniffer = true,
                        browserSnifferReason = "Open in Built-in Browser to capture the live video stream."
                    )
                )
            }

            // 5. Genuine direct file or direct audio/video stream of ANY type across the internet
            val detectedExt = inferFileExtension(probe.finalUrl, ct, probe.contentDisposition)
            val rawBaseName = inferBaseFileName(probe.finalUrl, probe.contentDisposition)
            val baseTitle = sanitizeFileName(
                rawBaseName.substringBeforeLast('.', rawBaseName),
                "download_${System.currentTimeMillis() % 100000}"
            )
            val finalFileName = if (detectedExt.isNotEmpty() && !baseTitle.endsWith(".$detectedExt", ignoreCase = true)) {
                "$baseTitle.$detectedExt"
            } else {
                baseTitle
            }

            val isVideo = ct.startsWith("video/") || detectedExt in setOf("mp4", "mkv", "webm", "mov", "m4v", "ts")
            val isAudio = ct.startsWith("audio/") || detectedExt in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

            val directVariant = MediaStreamVariant(
                variantId = "direct_original",
                label = when {
                    isVideo -> "Original Video (${detectedExt.uppercase()})"
                    isAudio -> "Original Audio (${detectedExt.uppercase()})"
                    else -> "Original File (${detectedExt.uppercase().ifBlank { "FILE" }})"
                },
                containerExtension = detectedExt.ifBlank { "bin" },
                mimeType = ct,
                resolutionLabel = if (isVideo) "Original" else detectedExt.uppercase().ifBlank { "File" },
                widthPx = 0,
                heightPx = 0,
                frameRateFps = 0,
                videoCodec = if (isVideo) "Original" else "Direct",
                videoBitrateKbps = 0,
                videoStreamUrl = probe.finalUrl,
                audioCodec = if (isVideo || isAudio) "Original" else "",
                audioBitrateKbps = 0,
                audioStreamUrl = null,
                hasEmbeddedAudio = true,
                requiresAudioVideoMux = false,
                isWatermarkFree = true,
                estimatedSizeBytes = probe.contentLength,
                refererHeader = referer,
                cookieHeader = cookies,
                userAgentHeader = userAgent
            )

            Result.success(
                ResolvedMediaManifest(
                    sourceUrl = cleanUrl,
                    platformName = platform,
                    title = baseTitle,
                    creatorAttribution = runCatching { URI(probe.finalUrl).host ?: platform }.getOrDefault(platform),
                    suggestedFileName = finalFileName,
                    isSocialPlatform = false,
                    variants = listOf(directVariant),
                    recommendedVariantId = directVariant.variantId
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class UrlProbeResult(
        val finalUrl: String,
        val contentType: String,
        val contentLength: Long,
        val contentDisposition: String?
    )

    private fun probeRemoteUrl(
        url: String,
        referer: String,
        cookies: String,
        userAgent: String
    ): UrlProbeResult {
        fun buildReq(methodHead: Boolean): Request {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
            if (referer.isNotBlank()) b.header("Referer", referer)
            if (cookies.isNotBlank()) b.header("Cookie", cookies)
            return if (methodHead) b.head().build() else b.get().build()
        }

        // Try HEAD first (never truncates, doesn't download body)
        runCatching {
            okHttpClient.newCall(buildReq(methodHead = true)).execute().use { resp ->
                if (resp.isSuccessful) {
                    return UrlProbeResult(
                        finalUrl = resp.request.url.toString(),
                        contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                            ?: "application/octet-stream",
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L,
                        contentDisposition = resp.header("Content-Disposition")
                    )
                }
            }
        }

        // Fallback to GET and read headers immediately (closing stream cleanly)
        okHttpClient.newCall(buildReq(methodHead = false)).execute().use { resp ->
            return UrlProbeResult(
                finalUrl = resp.request.url.toString(),
                contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                    ?: "application/octet-stream",
                contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L,
                contentDisposition = resp.header("Content-Disposition")
            )
        }
    }

    private fun fetchTextBody(url: String, referer: String, cookies: String, userAgent: String): String {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (referer.isNotBlank()) b.header("Referer", referer)
        if (cookies.isNotBlank()) b.header("Cookie", cookies)

        return runCatching {
            okHttpClient.newCall(b.build()).execute().use { resp ->
                val stream = resp.body?.byteStream() ?: return@use ""
                val out = ByteArray(MAX_HTML_INSPECT_BYTES)
                var total = 0
                while (total < MAX_HTML_INSPECT_BYTES) {
                    val r = stream.read(out, total, MAX_HTML_INSPECT_BYTES - total)
                    if (r == -1) break
                    total += r
                }
                String(out, 0, total, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    /**
     * Real YouTube InnerTube API extractor (ANDROID & IOS clients) extracting actual
     * progressive + adaptive video/audio streams for lossless muxing.
     */
    private fun extractYouTubeManifest(url: String): ResolvedMediaManifest? {
        val videoId = extractYouTubeVideoId(url) ?: return null
        val clientConfigs = listOf(
            Triple(
                "ANDROID",
                "19.44.38",
                "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"
            ),
            Triple(
                "IOS",
                "19.45.4",
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            )
        )

        for ((clientName, clientVersion, ua) in clientConfigs) {
            val manifest = runCatching {
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put(
                        "context",
                        JSONObject().put(
                            "client",
                            JSONObject().apply {
                                put("clientName", clientName)
                                put("clientVersion", clientVersion)
                                put("hl", "en")
                                put("gl", "US")
                                if (clientName == "ANDROID") put("androidSdkVersion", 34)
                            }
                        )
                    )
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }.toString()

                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("User-Agent", ua)
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bodyStr = resp.body?.string() ?: return@use null
                    val json = JSONObject(bodyStr)
                    val streamingData = json.optJSONObject("streamingData") ?: return@use null
                    val details = json.optJSONObject("videoDetails")
                    val title = details?.optString("title")?.ifBlank { "YouTube_$videoId" } ?: "YouTube_$videoId"
                    val author = details?.optString("author")?.ifBlank { "YouTube" } ?: "YouTube"

                    val variants = mutableListOf<MediaStreamVariant>()
                    val hlsManifestUrl = streamingData.optString("hlsManifestUrl", "")

                    // Collect best M4A/AAC audio stream for lossless MP4 muxing
                    data class AudioCandidate(val url: String, val bitrateKbps: Int, val codec: String, val sizeBytes: Long)
                    val audioCandidates = mutableListOf<AudioCandidate>()
                    val adaptiveArr = streamingData.optJSONArray("adaptiveFormats")
                    if (adaptiveArr != null) {
                        for (i in 0 until adaptiveArr.length()) {
                            val item = adaptiveArr.optJSONObject(i) ?: continue
                            val streamUrl = item.optString("url", "")
                            val mime = item.optString("mimeType", "")
                            if (streamUrl.isNotBlank() && mime.startsWith("audio/mp4")) {
                                audioCandidates.add(
                                    AudioCandidate(
                                        url = streamUrl,
                                        bitrateKbps = item.optInt("bitrate", 128000) / 1000,
                                        codec = "AAC",
                                        sizeBytes = item.optString("contentLength", "-1").toLongOrNull() ?: -1L
                                    )
                                )
                            }
                        }
                    }
                    val bestAudio = audioCandidates.maxByOrNull { it.bitrateKbps }

                    // 1. Adaptive high-res MP4/H.264/HEVC video paired with best AAC audio
                    if (adaptiveArr != null && bestAudio != null) {
                        for (i in 0 until adaptiveArr.length()) {
                            val item = adaptiveArr.optJSONObject(i) ?: continue
                            val streamUrl = item.optString("url", "")
                            val mime = item.optString("mimeType", "")
                            if (streamUrl.isBlank() || !mime.startsWith("video/mp4")) continue
                            val w = item.optInt("width", 0)
                            val h = item.optInt("height", 0)
                            val fps = item.optInt("fps", 30)
                            val vBitrate = item.optInt("bitrate", 0) / 1000
                            val qualityLabel = item.optString("qualityLabel", "${h}p")
                            val vSize = item.optString("contentLength", "-1").toLongOrNull() ?: -1L
                            val estTotal = if (vSize > 0 && bestAudio.sizeBytes > 0) vSize + bestAudio.sizeBytes else vSize

                            variants.add(
                                MediaStreamVariant(
                                    variantId = "yt_mux_${item.optInt("itag", i)}",
                                    label = "$qualityLabel MP4 · ${fps}fps + Audio",
                                    containerExtension = "mp4",
                                    mimeType = "video/mp4",
                                    resolutionLabel = qualityLabel,
                                    widthPx = w,
                                    heightPx = h,
                                    frameRateFps = fps,
                                    videoCodec = mime.substringAfter("codecs=\"", "H.264").substringBefore("\""),
                                    videoBitrateKbps = vBitrate,
                                    videoStreamUrl = streamUrl,
                                    audioCodec = "${bestAudio.codec} ${bestAudio.bitrateKbps}k",
                                    audioBitrateKbps = bestAudio.bitrateKbps,
                                    audioStreamUrl = bestAudio.url,
                                    hasEmbeddedAudio = false,
                                    requiresAudioVideoMux = true,
                                    isWatermarkFree = true,
                                    estimatedSizeBytes = estTotal,
                                    userAgentHeader = ua
                                )
                            )
                        }
                    }

                    // 2. Progressive formats (already contain synced audio)
                    val formatsArr = streamingData.optJSONArray("formats")
                    if (formatsArr != null) {
                        for (i in 0 until formatsArr.length()) {
                            val item = formatsArr.optJSONObject(i) ?: continue
                            val streamUrl = item.optString("url", "")
                            if (streamUrl.isBlank()) continue
                            val w = item.optInt("width", 0)
                            val h = item.optInt("height", 0)
                            val fps = item.optInt("fps", 30)
                            val bitrate = item.optInt("bitrate", 0) / 1000
                            val qualityLabel = item.optString("qualityLabel", "${h}p")
                            val size = item.optString("contentLength", "-1").toLongOrNull() ?: -1L

                            variants.add(
                                MediaStreamVariant(
                                    variantId = "yt_prog_${item.optInt("itag", i)}",
                                    label = "$qualityLabel MP4 (Progressive)",
                                    containerExtension = "mp4",
                                    mimeType = "video/mp4",
                                    resolutionLabel = qualityLabel,
                                    widthPx = w,
                                    heightPx = h,
                                    frameRateFps = fps,
                                    videoCodec = "H.264",
                                    videoBitrateKbps = bitrate,
                                    videoStreamUrl = streamUrl,
                                    audioCodec = "AAC",
                                    audioBitrateKbps = 128,
                                    audioStreamUrl = null,
                                    hasEmbeddedAudio = true,
                                    requiresAudioVideoMux = false,
                                    isWatermarkFree = true,
                                    estimatedSizeBytes = size,
                                    userAgentHeader = ua
                                )
                            )
                        }
                    }

                    // 3. Audio-only high-bitrate M4A option
                    if (bestAudio != null) {
                        variants.add(
                            MediaStreamVariant(
                                variantId = "yt_audio_best",
                                label = "Audio Only · M4A (${bestAudio.bitrateKbps} kbps)",
                                containerExtension = "m4a",
                                mimeType = "audio/mp4",
                                resolutionLabel = "Audio",
                                widthPx = 0,
                                heightPx = 0,
                                frameRateFps = 0,
                                videoCodec = "None",
                                videoBitrateKbps = 0,
                                videoStreamUrl = bestAudio.url,
                                audioCodec = bestAudio.codec,
                                audioBitrateKbps = bestAudio.bitrateKbps,
                                audioStreamUrl = null,
                                hasEmbeddedAudio = true,
                                requiresAudioVideoMux = false,
                                isWatermarkFree = true,
                                estimatedSizeBytes = bestAudio.sizeBytes,
                                userAgentHeader = ua
                            )
                        )
                    }

                    // 4. HLS fallback if IOS returned hlsManifestUrl
                    if (variants.isEmpty() && hlsManifestUrl.isNotBlank()) {
                        val hls = extractHlsManifest(hlsManifestUrl, "https://www.youtube.com/", "", ua)
                        if (hls != null && hls.variants.isNotEmpty()) {
                            return@use hls.copy(
                                sourceUrl = url,
                                platformName = "YouTube",
                                title = title,
                                creatorAttribution = author,
                                suggestedFileName = "${sanitizeFileName(title, "YouTube_$videoId")}.mp4",
                                isSocialPlatform = true
                            )
                        }
                    }

                    if (variants.isEmpty()) return@use null
                    val deduped = variants
                        .distinctBy { "${it.heightPx}_${it.frameRateFps}_${it.requiresAudioVideoMux}_${it.containerExtension}" }
                        .sortedByDescending { it.widthPx.toLong() * it.heightPx.toLong() }
                    val best = selectBestStreamVariant(deduped) ?: deduped.first()

                    ResolvedMediaManifest(
                        sourceUrl = url,
                        platformName = "YouTube",
                        title = title,
                        creatorAttribution = author,
                        suggestedFileName = "${sanitizeFileName(title, "YouTube_$videoId")}.${best.containerExtension}",
                        isSocialPlatform = true,
                        variants = deduped,
                        recommendedVariantId = best.variantId
                    )
                }
            }.getOrNull()

            if (manifest != null && manifest.variants.isNotEmpty()) return manifest
        }
        return null
    }

    /**
     * Real TikTok watermark-free HD stream extractor using public JSON metadata.
     */
    private fun extractTikTokManifest(url: String): ResolvedMediaManifest? {
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://www.tikwm.com/api/?url=$encoded&hd=1")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = JSONObject(body)
                if (root.optInt("code", -1) != 0) return@use null
                val data = root.optJSONObject("data") ?: return@use null

                val title = data.optString("title", "TikTok Video").ifBlank { "TikTok Video" }
                val authorObj = data.optJSONObject("author")
                val author = authorObj?.optString("nickname")?.ifBlank {
                    authorObj.optString("unique_id", "TikTok")
                } ?: "TikTok"

                val variants = mutableListOf<MediaStreamVariant>()
                val hdUrl = data.optString("hdplay", "").takeIf { it.startsWith("http") }
                val sdUrl = data.optString("play", "").takeIf { it.startsWith("http") }
                val musicUrl = data.optString("music", "").takeIf { it.startsWith("http") }
                val hdSize = data.optLong("hd_size", -1L)
                val sdSize = data.optLong("size", -1L)

                if (hdUrl != null) {
                    variants.add(
                        MediaStreamVariant(
                            variantId = "tt_hd_nowm",
                            label = "HD Original (No Watermark)",
                            containerExtension = "mp4",
                            mimeType = "video/mp4",
                            resolutionLabel = "1080p HD",
                            widthPx = 1080,
                            heightPx = 1920,
                            frameRateFps = 60,
                            videoCodec = "H.264",
                            videoBitrateKbps = 6000,
                            videoStreamUrl = hdUrl,
                            audioCodec = "AAC",
                            audioBitrateKbps = 192,
                            audioStreamUrl = null,
                            hasEmbeddedAudio = true,
                            requiresAudioVideoMux = false,
                            isWatermarkFree = true,
                            estimatedSizeBytes = hdSize
                        )
                    )
                }
                if (sdUrl != null) {
                    variants.add(
                        MediaStreamVariant(
                            variantId = "tt_sd_nowm",
                            label = "Standard (No Watermark)",
                            containerExtension = "mp4",
                            mimeType = "video/mp4",
                            resolutionLabel = "720p",
                            widthPx = 720,
                            heightPx = 1280,
                            frameRateFps = 30,
                            videoCodec = "H.264",
                            videoBitrateKbps = 2500,
                            videoStreamUrl = sdUrl,
                            audioCodec = "AAC",
                            audioBitrateKbps = 128,
                            audioStreamUrl = null,
                            hasEmbeddedAudio = true,
                            requiresAudioVideoMux = false,
                            isWatermarkFree = true,
                            estimatedSizeBytes = sdSize
                        )
                    )
                }
                if (musicUrl != null) {
                    variants.add(
                        MediaStreamVariant(
                            variantId = "tt_audio",
                            label = "Audio Only (MP3)",
                            containerExtension = "mp3",
                            mimeType = "audio/mpeg",
                            resolutionLabel = "Audio",
                            widthPx = 0,
                            heightPx = 0,
                            frameRateFps = 0,
                            videoCodec = "None",
                            videoBitrateKbps = 0,
                            videoStreamUrl = musicUrl,
                            audioCodec = "MP3",
                            audioBitrateKbps = 192,
                            audioStreamUrl = null,
                            hasEmbeddedAudio = true,
                            requiresAudioVideoMux = false,
                            isWatermarkFree = true
                        )
                    )
                }

                if (variants.isEmpty()) return@use null
                val best = selectBestStreamVariant(variants) ?: variants.first()
                ResolvedMediaManifest(
                    sourceUrl = url,
                    platformName = "TikTok",
                    title = title.take(64),
                    creatorAttribution = author,
                    suggestedFileName = "${sanitizeFileName(title, "TikTok_Video")}.${best.containerExtension}",
                    isSocialPlatform = true,
                    variants = variants,
                    recommendedVariantId = best.variantId
                )
            }
        }.getOrNull()
    }

    /**
     * Real Reddit JSON API extractor (video + paired DASH audio stream).
     */
    private fun extractRedditManifest(url: String): ResolvedMediaManifest? {
        return runCatching {
            val clean = url.substringBefore('?').trimEnd('/')
            val jsonUrl = if (clean.endsWith(".json")) clean else "$clean.json"
            val req = Request.Builder()
                .url(jsonUrl)
                .header("User-Agent", "android:com.lumafiles.filemanager:v2.0.0")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val postData = org.json.JSONArray(body)
                    .optJSONObject(0)
                    ?.optJSONObject("data")
                    ?.optJSONArray("children")
                    ?.optJSONObject(0)
                    ?.optJSONObject("data") ?: return@use null

                val title = postData.optString("title", "Reddit_Video")
                val author = postData.optString("author", "Reddit")
                val redditVideo = postData.optJSONObject("secure_media")?.optJSONObject("reddit_video")
                    ?: postData.optJSONObject("media")?.optJSONObject("reddit_video")
                    ?: return@use null

                val fallbackUrl = redditVideo.optString("fallback_url", "").replace("&amp;", "&")
                val hlsUrl = redditVideo.optString("hls_url", "").replace("&amp;", "&")
                val w = redditVideo.optInt("width", 1280)
                val h = redditVideo.optInt("height", 720)
                if (fallbackUrl.isBlank() && hlsUrl.isBlank()) return@use null

                val variants = mutableListOf<MediaStreamVariant>()
                if (fallbackUrl.isNotBlank()) {
                    val baseDir = fallbackUrl.substringBeforeLast('/')
                    val audioCandidate = listOf("$baseDir/DASH_AUDIO_128.mp4", "$baseDir/DASH_audio.mp4")
                        .firstOrNull { candidate ->
                            runCatching {
                                okHttpClient.newCall(Request.Builder().url(candidate).head().build())
                                    .execute().use { it.isSuccessful }
                            }.getOrDefault(false)
                        }
                    variants.add(
                        MediaStreamVariant(
                            variantId = "reddit_mp4",
                            label = "${h}p MP4${if (audioCandidate != null) " + Audio" else ""}",
                            containerExtension = "mp4",
                            mimeType = "video/mp4",
                            resolutionLabel = "${h}p",
                            widthPx = w,
                            heightPx = h,
                            frameRateFps = 30,
                            videoCodec = "H.264",
                            videoBitrateKbps = 4000,
                            videoStreamUrl = fallbackUrl,
                            audioCodec = if (audioCandidate != null) "AAC" else "None",
                            audioBitrateKbps = if (audioCandidate != null) 128 else 0,
                            audioStreamUrl = audioCandidate,
                            hasEmbeddedAudio = audioCandidate == null,
                            requiresAudioVideoMux = audioCandidate != null,
                            isWatermarkFree = true
                        )
                    )
                }
                if (hlsUrl.isNotBlank()) {
                    val hls = extractHlsManifest(hlsUrl, url, "", DEFAULT_USER_AGENT)
                    if (hls != null) variants.addAll(hls.variants)
                }

                val best = selectBestStreamVariant(variants) ?: return@use null
                ResolvedMediaManifest(
                    sourceUrl = url,
                    platformName = "Reddit",
                    title = title.take(64),
                    creatorAttribution = "u/$author",
                    suggestedFileName = "${sanitizeFileName(title, "Reddit_Video")}.mp4",
                    isSocialPlatform = true,
                    variants = variants,
                    recommendedVariantId = best.variantId
                )
            }
        }.getOrNull()
    }

    private fun extractHlsManifest(
        hlsUrl: String,
        referer: String,
        cookies: String,
        userAgent: String
    ): ResolvedMediaManifest? {
        val body = fetchTextBody(hlsUrl, referer, cookies, userAgent)
        if (!body.contains("#EXTM3U")) return null

        val streams = parseHlsMasterPlaylist(hlsUrl, body)
        val baseName = sanitizeFileName(
            hlsUrl.substringBefore('?').substringAfterLast('/').substringBeforeLast('.'),
            "Stream_${System.currentTimeMillis() % 10000}"
        )

        val variants = if (streams.isNotEmpty()) {
            streams.mapIndexed { idx, s ->
                val resLabel = if (s.heightPx > 0) "${s.heightPx}p" else "${s.bandwidthBps / 1000} kbps"
                MediaStreamVariant(
                    variantId = "hls_$idx",
                    label = "$resLabel HLS Stream",
                    containerExtension = "mp4",
                    mimeType = "video/mp4",
                    resolutionLabel = resLabel,
                    widthPx = s.widthPx,
                    heightPx = s.heightPx,
                    frameRateFps = s.frameRateFps,
                    videoCodec = s.codecs.take(18),
                    videoBitrateKbps = s.bandwidthBps / 1000,
                    videoStreamUrl = s.playlistUrl,
                    audioCodec = "AAC",
                    audioBitrateKbps = 128,
                    audioStreamUrl = null,
                    hasEmbeddedAudio = true,
                    requiresAudioVideoMux = false,
                    isWatermarkFree = true,
                    isHlsPlaylist = true,
                    refererHeader = referer,
                    cookieHeader = cookies,
                    userAgentHeader = userAgent
                )
            }
        } else {
            // Single media playlist directly containing .ts/.m4s segments
            listOf(
                MediaStreamVariant(
                    variantId = "hls_direct",
                    label = "HLS Video Stream",
                    containerExtension = "mp4",
                    mimeType = "video/mp4",
                    resolutionLabel = "Original HLS",
                    widthPx = 1280,
                    heightPx = 720,
                    frameRateFps = 30,
                    videoCodec = "H.264",
                    videoBitrateKbps = 3000,
                    videoStreamUrl = hlsUrl,
                    audioCodec = "AAC",
                    audioBitrateKbps = 128,
                    audioStreamUrl = null,
                    hasEmbeddedAudio = true,
                    requiresAudioVideoMux = false,
                    isWatermarkFree = true,
                    isHlsPlaylist = true,
                    refererHeader = referer,
                    cookieHeader = cookies,
                    userAgentHeader = userAgent
                )
            )
        }

        val best = selectBestStreamVariant(variants) ?: variants.first()
        return ResolvedMediaManifest(
            sourceUrl = hlsUrl,
            platformName = "HLS Stream",
            title = baseName,
            creatorAttribution = runCatching { URI(hlsUrl).host ?: "HLS" }.getOrDefault("HLS"),
            suggestedFileName = "$baseName.mp4",
            isSocialPlatform = false,
            variants = variants,
            recommendedVariantId = best.variantId
        )
    }

    /**
     * Scans a full HTML page for genuinely embedded MP4 / WebM / M3U8 streams (Facebook, Instagram, Vimeo, X, news/web).
     * Returns null if NO real media URL exists in the page (never falls back to saving the HTML page!).
     */
    private fun extractMediaFromHtmlPage(
        sourceUrl: String,
        platform: String,
        html: String,
        cookies: String
    ): ResolvedMediaManifest? {
        if (html.isBlank()) return null

        val pageTitle = Regex("""property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)
            ?: "${platform}_Video"

        val author = Regex("""(?:name=["']author["']|property=["']og:site_name["'])[^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1) ?: platform

        fun unescapeUrl(raw: String): String = raw
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim()

        val foundUrls = linkedMapOf<String, Pair<String, Int>>() // url -> Pair(label, heightHint)

        // Check high-confidence JSON video keys (Facebook, Instagram, Vimeo, generic JSON-LD)
        val keyedPatterns = listOf(
            Triple("HD Video", 1080, Regex(""""(?:browser_native_hd_url|playable_url_quality_hd|hd_src_no_ratelimit|hd_src)"\s*:\s*"(https?:[^"]+)"""")),
            Triple("SD Video", 720, Regex(""""(?:browser_native_sd_url|playable_url|sd_src|video_url|contentUrl)"\s*:\s*"(https?:[^"]+\.(?:mp4|webm|m3u8)[^"]*)"""")),
            Triple("OG Video", 720, Regex("""property=["']og:video(?::secure_url|:url)?["']\s+content=["'](https?:[^"']+)["']""", RegexOption.IGNORE_CASE)),
            Triple("Video Tag", 720, Regex("""<(?:video|source)[^>]+src=["'](https?:[^"']+\.(?:mp4|webm|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE))
        )

        for ((label, heightHint, regex) in keyedPatterns) {
            for (match in regex.findAll(html).take(4)) {
                val u = unescapeUrl(match.groupValues[1])
                val lower = u.lowercase()
                if (u.startsWith("http") &&
                    !lower.endsWith(".html") &&
                    !lower.contains("embed") &&
                    (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".webm") || lower.contains("video"))
                ) {
                    foundUrls.putIfAbsent(u, Pair(label, heightHint))
                }
            }
        }

        if (foundUrls.isEmpty()) return null

        val variants = mutableListOf<MediaStreamVariant>()
        foundUrls.entries.forEachIndexed { idx, (streamUrl, meta) ->
            val isHls = streamUrl.lowercase().contains(".m3u8")
            val ext = when {
                isHls -> "mp4"
                streamUrl.lowercase().contains(".webm") -> "webm"
                else -> "mp4"
            }
            variants.add(
                MediaStreamVariant(
                    variantId = "web_stream_$idx",
                    label = "${meta.first} (${ext.uppercase()})",
                    containerExtension = ext,
                    mimeType = if (ext == "webm") "video/webm" else "video/mp4",
                    resolutionLabel = "${meta.second}p",
                    widthPx = if (meta.second >= 1080) 1920 else 1280,
                    heightPx = meta.second,
                    frameRateFps = 30,
                    videoCodec = "H.264",
                    videoBitrateKbps = if (meta.second >= 1080) 6000 else 2500,
                    videoStreamUrl = streamUrl,
                    audioCodec = "AAC",
                    audioBitrateKbps = 192,
                    audioStreamUrl = null,
                    hasEmbeddedAudio = true,
                    requiresAudioVideoMux = false,
                    isWatermarkFree = true,
                    isHlsPlaylist = isHls,
                    refererHeader = sourceUrl,
                    cookieHeader = cookies,
                    userAgentHeader = DESKTOP_USER_AGENT
                )
            )
        }

        val best = selectBestStreamVariant(variants) ?: return null
        return ResolvedMediaManifest(
            sourceUrl = sourceUrl,
            platformName = platform,
            title = pageTitle.trim().take(64),
            creatorAttribution = author.trim(),
            suggestedFileName = "${sanitizeFileName(pageTitle, "${platform}_Video")}.${best.containerExtension}",
            isSocialPlatform = true,
            variants = variants,
            recommendedVariantId = best.variantId
        )
    }

    private fun inferBaseFileName(url: String, contentDisposition: String?): String {
        parseContentDispositionFilename(contentDisposition)?.let { return it }
        val pathPart = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return runCatching { URLDecoder.decode(pathPart, "UTF-8") }.getOrDefault(pathPart)
    }

    private fun inferFileExtension(url: String, contentType: String, contentDisposition: String?): String {
        val fromName = inferBaseFileName(url, contentDisposition)
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..6 && it.all { c -> c.isLetterOrDigit() } }
        if (fromName != null && fromName !in setOf("html", "htm", "php", "jsp", "aspx", "cgi")) {
            return fromName
        }
        return when {
            contentType.contains("video/mp4") -> "mp4"
            contentType.contains("video/webm") -> "webm"
            contentType.contains("video/x-matroska") -> "mkv"
            contentType.contains("video/quicktime") -> "mov"
            contentType.contains("audio/mpeg") -> "mp3"
            contentType.contains("audio/mp4") || contentType.contains("audio/aac") -> "m4a"
            contentType.contains("audio/wav") -> "wav"
            contentType.contains("audio/flac") -> "flac"
            contentType.contains("audio/ogg") -> "ogg"
            contentType.contains("application/pdf") -> "pdf"
            contentType.contains("application/zip") -> "zip"
            contentType.contains("application/x-rar") -> "rar"
            contentType.contains("application/x-7z") -> "7z"
            contentType.contains("application/gzip") -> "gz"
            contentType.contains("application/vnd.android.package-archive") -> "apk"
            contentType.contains("application/json") -> "json"
            contentType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml") -> "docx"
            contentType.contains("image/png") -> "png"
            contentType.contains("image/jpeg") -> "jpg"
            contentType.contains("image/webp") -> "webp"
            contentType.contains("image/gif") -> "gif"
            contentType.contains("text/markdown") -> "md"
            contentType.contains("text/csv") -> "csv"
            contentType.contains("text/plain") -> "txt"
            else -> fromName ?: "bin"
        }
    }

    suspend fun muxVideoAndAudioLosslessly(
        videoFile: File,
        audioFile: File,
        outputMp4File: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempMuxFile = File(outputMp4File.parentFile, ".luma_mux_${UUID.randomUUID()}.mp4")
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            muxer = MediaMuxer(tempMuxFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxVideoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    muxVideoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            var muxAudioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    muxAudioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (muxVideoTrackIndex == -1) {
                throw IllegalStateException("No video track found in '${videoFile.name}'.")
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxVideoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            if (muxAudioTrackIndex != -1) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxAudioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            if (!tempMuxFile.renameTo(outputMp4File)) {
                tempMuxFile.copyTo(outputMp4File, overwrite = true)
                tempMuxFile.delete()
            }
            Result.success(outputMp4File)
        } catch (e: Exception) {
            if (tempMuxFile.exists()) tempMuxFile.delete()
            Result.failure(e)
        } finally {
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { muxer?.release() }
        }
    }

    fun saveCheckpointSidecar(destFile: File, checkpoint: PersistedDownloadCheckpoint) {
        runCatching {
            val sidecar = File(destFile.parentFile, ".${destFile.name}.luma_resume.tsv")
            val line = listOf(
                checkpoint.url,
                checkpoint.etag,
                checkpoint.lastModified,
                checkpoint.totalBytes.toString(),
                checkpoint.threadCount.toString(),
                checkpoint.audioStreamUrl,
                checkpoint.creatorAttribution,
                checkpoint.expectedSha256,
                checkpoint.customUserAgent,
                checkpoint.resolutionLabel,
                checkpoint.refererHeader,
                checkpoint.cookieHeader,
                checkpoint.isHlsPlaylist.toString()
            ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
            sidecar.writeText(line, Charsets.UTF_8)
        }
    }

    fun loadCheckpointSidecar(destFile: File): PersistedDownloadCheckpoint? {
        val sidecar = File(destFile.parentFile, ".${destFile.name}.luma_resume.tsv")
        if (!sidecar.exists()) return null
        return runCatching {
            val parts = sidecar.readText(Charsets.UTF_8).split('\t')
            if (parts.size < 5) null
            else PersistedDownloadCheckpoint(
                url = parts[0],
                etag = parts.getOrElse(1) { "" },
                lastModified = parts.getOrElse(2) { "" },
                totalBytes = parts.getOrElse(3) { "-1" }.toLongOrNull() ?: -1L,
                threadCount = parts.getOrElse(4) { "4" }.toIntOrNull() ?: 4,
                audioStreamUrl = parts.getOrElse(5) { "" },
                creatorAttribution = parts.getOrElse(6) { "" },
                expectedSha256 = parts.getOrElse(7) { "" },
                customUserAgent = parts.getOrElse(8) { "" },
                resolutionLabel = parts.getOrElse(9) { "" },
                refererHeader = parts.getOrElse(10) { "" },
                cookieHeader = parts.getOrElse(11) { "" },
                isHlsPlaylist = parts.getOrElse(12) { "false" }.toBoolean()
            )
        }.getOrNull()
    }

    private fun clearCheckpointAndParts(destFile: File, maxThreads: Int = 8) {
        runCatching {
            File(destFile.parentFile, ".${destFile.name}.luma_resume.tsv").delete()
            File(destFile.parentFile, ".${destFile.name}.part").delete()
            File(destFile.parentFile, ".${destFile.name}.video.part").delete()
            File(destFile.parentFile, ".${destFile.name}.audio.part").delete()
            for (i in 0 until maxThreads) {
                File(destFile.parentFile, ".${destFile.name}.seg$i.part").delete()
            }
        }
    }

    fun startOrResumeDownload(
        taskId: Long,
        onDiagnosticError: (DiagnosticError) -> Unit
    ) {
        if (activeJobs[taskId]?.isActive == true) return
        val job = externalScope.launch(Dispatchers.IO) {
            val task = repository.getDownloadTask(taskId) ?: return@launch
            val destFile = File(task.destinationPath)
            destFile.parentFile?.mkdirs()

            var attempt = 0
            var lastException: Exception? = null

            while (attempt < MAX_AUTO_RETRIES && isActive) {
                attempt++
                try {
                    val currentTask = repository.getDownloadTask(taskId) ?: task
                    repository.updateDownloadTask(
                        currentTask.copy(
                            status = "DOWNLOADING",
                            errorExactReason = if (attempt > 1) "Retrying ($attempt/$MAX_AUTO_RETRIES)..." else ""
                        )
                    )

                    val checkpoint = loadCheckpointSidecar(destFile)
                    if (checkpoint?.isHlsPlaylist == true || currentTask.url.lowercase().contains(".m3u8")) {
                        runHlsPlaylistDownload(currentTask, destFile, checkpoint)
                    } else {
                        runResumableHttpDownload(currentTask, destFile)
                    }
                    lastException = null
                    break
                } catch (ce: CancellationException) {
                    val latest = repository.getDownloadTask(taskId)
                    if (latest != null && latest.status == "DOWNLOADING") {
                        repository.updateDownloadTask(latest.copy(status = "PAUSED", speedBytesPerSec = 0L))
                    }
                    throw ce
                } catch (e: Exception) {
                    lastException = e
                    val msg = e.message ?: ""
                    val isPermanentError = msg.contains("HTTP 404") ||
                        msg.contains("HTTP 401") ||
                        msg.contains("HTTP 403") ||
                        msg.contains("SHA-256")
                    if (isPermanentError || attempt >= MAX_AUTO_RETRIES) break
                    delay(800L * (1L shl (attempt - 1)))
                }
            }

            if (lastException != null) {
                val exactError = buildExactNetworkDiagnostic(task.url, lastException!!)
                val latest = repository.getDownloadTask(taskId) ?: task
                repository.updateDownloadTask(
                    latest.copy(
                        status = "FAILED",
                        speedBytesPerSec = 0L,
                        errorExactReason = exactError.exactReason
                    )
                )
                onDiagnosticError(exactError)
            }
            activeJobs.remove(taskId)
        }
        activeJobs[taskId] = job
    }

    fun pauseDownload(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        externalScope.launch(Dispatchers.IO) {
            val latest = repository.getDownloadTask(taskId) ?: return@launch
            if (latest.status == "DOWNLOADING" || latest.status == "QUEUED") {
                repository.updateDownloadTask(latest.copy(status = "PAUSED", speedBytesPerSec = 0L))
            }
        }
    }

    private suspend fun runHlsPlaylistDownload(
        task: DownloadTaskEntity,
        destFile: File,
        checkpoint: PersistedDownloadCheckpoint?
    ) = coroutineScope {
        val ua = checkpoint?.customUserAgent?.ifBlank { DEFAULT_USER_AGENT } ?: DEFAULT_USER_AGENT
        val referer = checkpoint?.refererHeader ?: ""
        val cookies = checkpoint?.cookieHeader ?: ""
        val startTime = System.currentTimeMillis()

        var playlistUrl = task.url
        var playlistBody = fetchTextBody(playlistUrl, referer, cookies, ua)
        val masterStreams = parseHlsMasterPlaylist(playlistUrl, playlistBody)
        if (masterStreams.isNotEmpty()) {
            playlistUrl = masterStreams.first().playlistUrl
            playlistBody = fetchTextBody(playlistUrl, referer, cookies, ua)
        }

        val segmentUrls = parseHlsMediaSegments(playlistUrl, playlistBody)
        if (segmentUrls.isEmpty()) {
            throw IllegalStateException("HLS playlist contained no playable media segments.")
        }

        val partFile = File(destFile.parentFile, ".${destFile.name}.part")
        var totalWritten = 0L
        BufferedOutputStream(FileOutputStream(partFile, false), 64 * 1024).use { out ->
            val buffer = ByteArray(32 * 1024)
            segmentUrls.forEachIndexed { idx, segUrl ->
                if (!isActive) throw CancellationException("Download paused")
                val b = Request.Builder().url(segUrl).header("User-Agent", ua)
                if (referer.isNotBlank()) b.header("Referer", referer)
                if (cookies.isNotBlank()) b.header("Cookie", cookies)

                okHttpClient.newCall(b.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} fetching segment ${idx + 1}/${segmentUrls.size}")
                    resp.body?.byteStream()?.use { input ->
                        var r: Int
                        while (input.read(buffer).also { r = it } != -1 && isActive) {
                            out.write(buffer, 0, r)
                            totalWritten += r
                        }
                    }
                }
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(50L)
                val speed = (totalWritten * 1000L) / elapsed
                val estimatedTotal = if (idx + 1 < segmentUrls.size) {
                    (totalWritten * segmentUrls.size) / (idx + 1)
                } else totalWritten
                repository.updateDownloadTask(
                    task.copy(
                        totalBytes = estimatedTotal,
                        downloadedBytes = totalWritten,
                        status = "DOWNLOADING",
                        speedBytesPerSec = speed
                    )
                )
            }
            out.flush()
        }

        if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }

        val integrity = verifyDownloadedFileIntegrity(destFile, -1L, checkpoint?.expectedSha256)
        if (!integrity.isValid) throw IllegalStateException(integrity.failureReason ?: "HLS file verification failed.")

        repository.updateDownloadTask(
            task.copy(
                totalBytes = integrity.actualSizeBytes,
                downloadedBytes = integrity.actualSizeBytes,
                status = "COMPLETED",
                speedBytesPerSec = 0L,
                sha256Checksum = integrity.computedSha256,
                errorExactReason = ""
            )
        )
        clearCheckpointAndParts(destFile)
    }

    private suspend fun runResumableHttpDownload(
        task: DownloadTaskEntity,
        destFile: File
    ) = coroutineScope {
        val existingCheckpoint = loadCheckpointSidecar(destFile)
        val userAgent = existingCheckpoint?.customUserAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        val referer = existingCheckpoint?.refererHeader ?: ""
        val cookies = existingCheckpoint?.cookieHeader ?: ""
        val expectedSha256 = existingCheckpoint?.expectedSha256?.takeIf { it.isNotBlank() }
            ?: task.sha256Checksum.takeIf { it.length == 64 }

        fun buildBaseRequest(url: String): Request.Builder {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
            if (referer.isNotBlank()) b.header("Referer", referer)
            if (cookies.isNotBlank()) b.header("Cookie", cookies)
            return b
        }

        // Step 1: Probe Range support safely (fall back cleanly if server dislikes Range: bytes=0-0)
        var contentLength = existingCheckpoint?.totalBytes ?: -1L
        var verifiedRangeSupport = false
        var serverEtag = ""
        var serverLastModified = ""

        runCatching {
            okHttpClient.newCall(buildBaseRequest(task.url).header("Range", "bytes=0-0").get().build())
                .execute().use { response ->
                    if (response.isSuccessful || response.code == 206) {
                        serverEtag = response.header("ETag") ?: ""
                        serverLastModified = response.header("Last-Modified") ?: ""
                        val validation = validateHttpContentRange(
                            response.code,
                            response.headers["Content-Range"],
                            0L,
                            0L
                        )
                        if (validation.isValidPartialRange && validation.totalBytes > 0) {
                            contentLength = validation.totalBytes
                            verifiedRangeSupport = contentLength > 64 * 1024
                        } else {
                            val lenHeader = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                            if (lenHeader > 1L) contentLength = lenHeader
                            verifiedRangeSupport = false
                        }
                    }
                }
        }

        if (existingCheckpoint != null &&
            existingCheckpoint.etag.isNotBlank() &&
            serverEtag.isNotBlank() &&
            existingCheckpoint.etag != serverEtag
        ) {
            clearCheckpointAndParts(destFile, 8)
        }

        val updatedCheckpoint = (existingCheckpoint ?: PersistedDownloadCheckpoint(url = task.url)).copy(
            etag = serverEtag.ifBlank { existingCheckpoint?.etag ?: "" },
            lastModified = serverLastModified.ifBlank { existingCheckpoint?.lastModified ?: "" },
            totalBytes = contentLength,
            threadCount = task.threadCount.coerceIn(1, 8)
        )
        saveCheckpointSidecar(destFile, updatedCheckpoint)

        val startTime = System.currentTimeMillis()
        val effectiveThreads = if (verifiedRangeSupport && contentLength > 0) {
            task.threadCount.coerceIn(1, 8)
        } else 1

        val fallbackTriggered = AtomicBoolean(false)
        val stagingOutputFile = if (updatedCheckpoint.audioStreamUrl.isNotBlank()) {
            File(destFile.parentFile, ".${destFile.name}.video.part")
        } else {
            File(destFile.parentFile, ".${destFile.name}.part")
        }

        if (effectiveThreads > 1 && contentLength > 0) {
            val partSize = contentLength / effectiveThreads
            val segFiles = (0 until effectiveThreads).map { idx ->
                File(destFile.parentFile, ".${destFile.name}.seg$idx.part")
            }

            val initialPersistedBytes = segFiles.mapIndexed { idx, segFile ->
                val segStart = idx * partSize
                val segEnd = if (idx == effectiveThreads - 1) contentLength - 1 else (segStart + partSize - 1)
                computeResumeOffset(segFile, segEnd - segStart + 1)
            }.sum()

            val downloadedCounter = AtomicLong(initialPersistedBytes)
            val lastUpdateMs = AtomicLong(0L)

            val jobs = (0 until effectiveThreads).map { threadIdx ->
                async(Dispatchers.IO) {
                    val segFile = segFiles[threadIdx]
                    val startByte = threadIdx * partSize
                    val endByte = if (threadIdx == effectiveThreads - 1) contentLength - 1 else (startByte + partSize - 1)
                    val expectedSegmentBytes = endByte - startByte + 1
                    val existingSegBytes = computeResumeOffset(segFile, expectedSegmentBytes)
                    if (existingSegBytes >= expectedSegmentBytes) return@async

                    val resumeStartByte = startByte + existingSegBytes
                    val rangeReqBuilder = buildBaseRequest(task.url)
                        .header("Range", "bytes=$resumeStartByte-$endByte")
                    if (updatedCheckpoint.etag.isNotBlank() && existingSegBytes > 0) {
                        rangeReqBuilder.header("If-Range", updatedCheckpoint.etag)
                    }

                    okHttpClient.newCall(rangeReqBuilder.build()).execute().use { resp ->
                        val rangeCheck = validateHttpContentRange(
                            resp.code,
                            resp.headers["Content-Range"],
                            resumeStartByte,
                            endByte
                        )
                        if (rangeCheck.shouldFallbackToSingleStream) {
                            fallbackTriggered.set(true)
                            return@use
                        }
                        if (!rangeCheck.isValidPartialRange) {
                            fallbackTriggered.set(true)
                            return@use
                        }
                        val body = resp.body
                            ?: throw IllegalStateException("Empty response body on segment #$threadIdx")
                        val remainingBytesToWrite = expectedSegmentBytes - existingSegBytes
                        var segmentBytesWritten = 0L

                        BufferedOutputStream(FileOutputStream(segFile, existingSegBytes > 0), 32 * 1024).use { segOut ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(32 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1 && isActive && !fallbackTriggered.get()) {
                                    val safeWrite = minOf(read.toLong(), remainingBytesToWrite - segmentBytesWritten).toInt()
                                    if (safeWrite > 0) {
                                        segOut.write(buffer, 0, safeWrite)
                                        segmentBytesWritten += safeWrite
                                        val totalNow = downloadedCounter.addAndGet(safeWrite.toLong())
                                        val now = System.currentTimeMillis()
                                        if (now - lastUpdateMs.get() > 150) {
                                            lastUpdateMs.set(now)
                                            val elapsed = (now - startTime).coerceAtLeast(50L)
                                            val sessionBytes = (totalNow - initialPersistedBytes).coerceAtLeast(1L)
                                            val speed = (sessionBytes * 1000L) / elapsed
                                            repository.updateDownloadTask(
                                                task.copy(
                                                    totalBytes = contentLength,
                                                    downloadedBytes = totalNow,
                                                    status = "DOWNLOADING",
                                                    supportsRange = true,
                                                    speedBytesPerSec = speed
                                                )
                                            )
                                        }
                                    }
                                }
                                segOut.flush()
                            }
                        }

                        if (!fallbackTriggered.get() && isActive && (existingSegBytes + segmentBytesWritten) != expectedSegmentBytes) {
                            throw IllegalStateException("Segment #$threadIdx interrupted.")
                        }
                    }
                }
            }
            jobs.awaitAll()

            if (!fallbackTriggered.get() && isActive) {
                BufferedOutputStream(FileOutputStream(stagingOutputFile, false), 64 * 1024).use { assembledOut ->
                    val buffer = ByteArray(64 * 1024)
                    for (segFile in segFiles) {
                        BufferedInputStream(FileInputStream(segFile), 64 * 1024).use { segIn ->
                            var r: Int
                            while (segIn.read(buffer).also { r = it } != -1) {
                                assembledOut.write(buffer, 0, r)
                            }
                        }
                    }
                    assembledOut.flush()
                }
            }
        }

        if (effectiveThreads == 1 || fallbackTriggered.get()) {
            streamSingleConnectionResumableDownload(
                task = task,
                urlToStream = task.url,
                partFile = stagingOutputFile,
                knownContentLength = contentLength,
                canResumeWithRange = verifiedRangeSupport && !fallbackTriggered.get(),
                etag = updatedCheckpoint.etag,
                userAgent = userAgent,
                referer = referer,
                cookies = cookies,
                startTime = startTime
            )
        }

        // Download paired high-quality audio stream and losslessly mux Video + Audio when needed
        if (updatedCheckpoint.audioStreamUrl.isNotBlank() && isActive) {
            repository.updateDownloadTask(task.copy(status = "MUXING", speedBytesPerSec = 0L))
            val audioPartFile = File(destFile.parentFile, ".${destFile.name}.audio.part")
            streamSingleConnectionResumableDownload(
                task = task,
                urlToStream = updatedCheckpoint.audioStreamUrl,
                partFile = audioPartFile,
                knownContentLength = -1L,
                canResumeWithRange = true,
                etag = "",
                userAgent = userAgent,
                referer = referer,
                cookies = cookies,
                startTime = startTime
            )
            val muxRes = muxVideoAndAudioLosslessly(
                videoFile = stagingOutputFile,
                audioFile = audioPartFile,
                outputMp4File = destFile
            )
            if (muxRes.isFailure) {
                if (!stagingOutputFile.renameTo(destFile)) {
                    stagingOutputFile.copyTo(destFile, overwrite = true)
                    stagingOutputFile.delete()
                }
            }
        } else {
            val expectedSize = if (contentLength > 0 && !fallbackTriggered.get()) contentLength else -1L
            val integrity = withContext(Dispatchers.IO) {
                verifyDownloadedFileIntegrity(stagingOutputFile, expectedSize, expectedSha256)
            }
            if (!integrity.isValid) {
                throw IllegalStateException(integrity.failureReason ?: "Download integrity check failed.")
            }
            if (!stagingOutputFile.renameTo(destFile)) {
                stagingOutputFile.copyTo(destFile, overwrite = true)
                stagingOutputFile.delete()
            }
        }

        val finalIntegrity = withContext(Dispatchers.IO) {
            verifyDownloadedFileIntegrity(destFile, -1L, expectedSha256)
        }
        if (!finalIntegrity.isValid) {
            throw IllegalStateException(finalIntegrity.failureReason ?: "Final file check failed.")
        }

        val totalElapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(50L)
        val finalSpeed = (finalIntegrity.actualSizeBytes * 1000L) / totalElapsedMs

        repository.updateDownloadTask(
            task.copy(
                totalBytes = finalIntegrity.actualSizeBytes,
                downloadedBytes = finalIntegrity.actualSizeBytes,
                status = "COMPLETED",
                speedBytesPerSec = finalSpeed,
                sha256Checksum = finalIntegrity.computedSha256,
                errorExactReason = ""
            )
        )
        clearCheckpointAndParts(destFile, effectiveThreads.coerceAtLeast(8))
    }

    private suspend fun streamSingleConnectionResumableDownload(
        task: DownloadTaskEntity,
        urlToStream: String,
        partFile: File,
        knownContentLength: Long,
        canResumeWithRange: Boolean,
        etag: String,
        userAgent: String,
        referer: String,
        cookies: String,
        startTime: Long
    ) = coroutineScope {
        val existingBytes = if (canResumeWithRange) {
            computeResumeOffset(partFile, knownContentLength)
        } else 0L

        if (knownContentLength > 0 && existingBytes == knownContentLength) return@coroutineScope

        val reqBuilder = Request.Builder()
            .url(urlToStream)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
        if (referer.isNotBlank()) reqBuilder.header("Referer", referer)
        if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)
        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
            if (etag.isNotBlank()) reqBuilder.header("If-Range", etag)
        }

        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 416 && knownContentLength > 0 && partFile.length() == knownContentLength) {
                return@use
            }
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code} (${resp.message})")
            }

            val isAppending = if (resp.code == 206 && existingBytes > 0) {
                validateHttpContentRange(206, resp.header("Content-Range"), existingBytes, -1L).isValidPartialRange
            } else false

            val body = resp.body ?: throw IllegalStateException("Server returned empty body.")
            val bodyLen = body.contentLength()
            val totalExpected = when {
                isAppending && knownContentLength > 0 -> knownContentLength
                bodyLen > 0 -> if (isAppending) existingBytes + bodyLen else bodyLen
                else -> knownContentLength.coerceAtLeast(0L)
            }

            BufferedOutputStream(FileOutputStream(partFile, isAppending), 32 * 1024).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var currentBytes = if (isAppending) existingBytes else 0L
                    var lastReport = 0L
                    while (input.read(buffer).also { read = it } != -1 && isActive) {
                        output.write(buffer, 0, read)
                        currentBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 150) {
                            lastReport = now
                            val elapsed = (now - startTime).coerceAtLeast(50L)
                            val speed = ((currentBytes - existingBytes).coerceAtLeast(1L) * 1000L) / elapsed
                            repository.updateDownloadTask(
                                task.copy(
                                    totalBytes = if (totalExpected > 0) totalExpected else currentBytes,
                                    downloadedBytes = currentBytes,
                                    status = "DOWNLOADING",
                                    supportsRange = isAppending || canResumeWithRange,
                                    speedBytesPerSec = speed
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun buildExactNetworkDiagnostic(url: String, e: Exception): DiagnosticError {
        val msg = e.message ?: "Network error"
        val hint = when {
            msg.contains("Unable to resolve host", ignoreCase = true) || e is java.net.UnknownHostException ->
                "Check internet connection and tap Resume."
            msg.contains("HTTP 403") || msg.contains("HTTP 401") ->
                "This link requires browser cookies. Open it in the Browser tab to download."
            msg.contains("HTTP 404") ->
                "The link returned 404 Not Found."
            else ->
                "Progress saved. Tap Resume or try capturing the link in the Browser tab."
        }
        return DiagnosticError(
            operationTitle = "Download Failed",
            targetPathOrUrl = url,
            exceptionClass = e.javaClass.name,
            errorCodeOrStatus = e.javaClass.simpleName,
            exactReason = msg,
            remediationHint = hint,
            stackSnippet = e.stackTraceToString().lineSequence().take(5).joinToString("\n")
        )
    }
}
