package com.example.domain.engine

import android.util.Base64
import com.example.domain.model.MediaStreamVariant
import com.example.domain.model.ResolvedMediaManifest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Multi-strategy social media stream extractors & CDN URL normalizers for
 * Instagram, X/Twitter, Facebook, and MPEG-DASH manifests.
 */
object SocialMediaExtractors {

    private const val IG_APP_ID = "936619743392459"
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val MOBILE_UA = FastDownloadEngine.DEFAULT_USER_AGENT

    private val IG_GRAPHQL_DOC_IDS = listOf(
        "8845758582119845",
        "10015901848480474",
        "24368985919464652"
    )

    // ==================== PURE TESTABLE PARSERS & NORMALIZERS ====================

    fun extractInstagramShortcode(url: String): String? {
        val clean = url.trim().substringBefore('#')
        val patterns = listOf(
            Regex("""instagram\.com/(?:[^/]+/)?(?:reel|reels|p|tv)/([A-Za-z0-9_-]{5,40})""", RegexOption.IGNORE_CASE),
            Regex("""instagr\.am/(?:reel|reels|p|tv)/([A-Za-z0-9_-]{5,40})""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val code = p.find(clean)?.groupValues?.getOrNull(1)
            if (!code.isNullOrBlank()) return code
        }
        return null
    }

    fun extractXStatusId(url: String): String? {
        return Regex("""(?:x\.com|twitter\.com|fxtwitter\.com|vxtwitter\.com)/[^/]+/status/(\d+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * Strips partial byte-range chunking query parameters (`bytestart`, `byteend`, `range`, `rn`, `rbuf`)
     * from Instagram, Facebook, and YouTube CDN URLs so downloading grabs the full stream instead of a 64KB slice.
     */
    fun normalizeSocialCdnStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val qIdx = trimmed.indexOf('?')
        if (qIdx == -1) return trimmed
        val base = trimmed.substring(0, qIdx)
        val query = trimmed.substring(qIdx + 1)
        val lower = base.lowercase()
        val isSocialCdn = lower.contains("cdninstagram.com") ||
            lower.contains("fbcdn.net") ||
            lower.contains("googlevideo.com") ||
            lower.contains("tiktokcdn")
        if (!isSocialCdn) return trimmed

        val filteredParams = query.split('&').filter { param ->
            val key = param.substringBefore('=').lowercase()
            key !in setOf("bytestart", "byteend", "range", "rn", "rbuf")
        }
        return if (filteredParams.isEmpty()) base else "$base?${filteredParams.joinToString("&")}"
    }

    /**
     * Strictly rejects image thumbnails, avatars, scripts, styles, and fonts so the Browser Sniffer
     * never mistakes Instagram/Facebook `.jpg`/`.webp` CDN assets for playable videos.
     */
    fun isLikelyImageOrStaticAssetUrl(url: String): Boolean {
        val lower = url.lowercase()
        val pathOnly = lower.substringBefore('?').substringBefore('#')
        val staticExts = listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".ico", ".bmp", ".heic", ".avif",
            ".js", ".mjs", ".css", ".woff", ".woff2", ".ttf", ".otf", ".map", ".json"
        )
        if (staticExts.any { pathOnly.endsWith(it) }) return true

        // Instagram / Facebook image CDN path & query signatures
        if (lower.contains("cdninstagram.com") || lower.contains("fbcdn.net")) {
            if (lower.contains("stp=dst-jpg") ||
                lower.contains("stp=dst-webp") ||
                lower.contains("stp=c0.") ||
                lower.contains("/t51.2885-15/") ||
                lower.contains("/t51.2885-19/") ||
                lower.contains("/t51.29350-15/") ||
                lower.contains("ig_cache_key=")
            ) {
                // Only reject if it's NOT clearly a video stream (.mp4)
                if (!pathOnly.endsWith(".mp4") && !lower.contains("video")) return true
            }
        }
        return false
    }

    /**
     * Identifies audio-only DASH streams on Instagram, Facebook, and YouTube (including base64 `efg` tags).
     */
    fun isLikelyAudioOnlyStreamUrl(url: String, mimeHint: String = ""): Boolean {
        val lower = url.lowercase()
        if (mimeHint.startsWith("audio/", ignoreCase = true)) return true
        if (lower.contains("mime=audio") ||
            lower.contains("mime%3daudio") ||
            lower.contains("dash_audio") ||
            lower.contains("audio_0") ||
            lower.contains("_audio.") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".mp3") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".opus")
        ) {
            return true
        }
        // Inspect Meta/Instagram base64 `efg=` query param (often contains `"vencode_tag":"...audio..."`)
        val efgParam = Regex("""[?&]efg=([^&]+)""").find(url)?.groupValues?.getOrNull(1)
        if (!efgParam.isNullOrBlank()) {
            val decoded = runCatching {
                val unescaped = URLDecoder.decode(efgParam, "UTF-8")
                val bytes = runCatching { Base64.decode(unescaped, Base64.URL_SAFE or Base64.NO_WRAP) }
                    .getOrElse { java.util.Base64.getUrlDecoder().decode(unescaped) }
                String(bytes, Charsets.UTF_8).lowercase()
            }.getOrDefault("")
            if (decoded.contains("audio") && !decoded.contains("video")) return true
        }
        return false
    }

    /**
     * Parses MPEG-DASH XML (used by Instagram Reels, Facebook Video, Reddit) to extract
     * the highest-bandwidth video `<BaseURL>` and highest-bandwidth audio `<BaseURL>`.
     */
    fun parseDashManifestVideoAndAudio(dashXml: String): Pair<String?, String?> {
        if (dashXml.isBlank() || !dashXml.contains("BaseURL", ignoreCase = true)) return null to null
        val cleaned = dashXml
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")

        val adaptationRegex = Regex(
            """<AdaptationSet\b([^>]*)>([\s\S]*?)</AdaptationSet>""",
            RegexOption.IGNORE_CASE
        )
        val repRegex = Regex(
            """<Representation\b([^>]*)>([\s\S]*?)</Representation>""",
            RegexOption.IGNORE_CASE
        )
        val baseUrlRegex = Regex("""<BaseURL[^>]*>(https?://[^<]+)</BaseURL>""", RegexOption.IGNORE_CASE)
        val bwRegex = Regex("""bandwidth=["'](\d+)["']""", RegexOption.IGNORE_CASE)

        var bestVideoUrl: String? = null
        var bestVideoBw = -1L
        var bestAudioUrl: String? = null
        var bestAudioBw = -1L

        for (adapt in adaptationRegex.findAll(cleaned)) {
            val attrs = adapt.groupValues[1].lowercase()
            val body = adapt.groupValues[2]
            val isVideo = attrs.contains("video") || body.lowercase().contains("mimeType=\"video/".lowercase())
            val isAudio = !isVideo && (attrs.contains("audio") || body.lowercase().contains("mimetype=\"audio/"))

            for (rep in repRegex.findAll(body)) {
                val repAttrs = rep.groupValues[1]
                val repBody = rep.groupValues[2]
                val url = baseUrlRegex.find(repBody)?.groupValues?.getOrNull(1)?.trim()
                    ?.replace("&amp;", "&") ?: continue
                val bw = bwRegex.find(repAttrs)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val repIsAudio = isAudio || repAttrs.lowercase().contains("audio/") || isLikelyAudioOnlyStreamUrl(url)
                if (repIsAudio) {
                    if (bw >= bestAudioBw) {
                        bestAudioBw = bw
                        bestAudioUrl = normalizeSocialCdnStreamUrl(url)
                    }
                } else {
                    if (bw >= bestVideoBw) {
                        bestVideoBw = bw
                        bestVideoUrl = normalizeSocialCdnStreamUrl(url)
                    }
                }
            }
        }
        return bestVideoUrl to bestAudioUrl
    }

    /**
     * Parses Instagram GraphQL (`xdt_shortcode_media`), legacy `graphql.shortcode_media`,
     * or mobile `items[]` JSON into genuine HD playable video/carousel variants.
     */
    fun parseInstagramJsonToVariants(
        jsonBody: String,
        sourceUrl: String,
        cookies: String = ""
    ): ResolvedMediaManifest? {
        if (jsonBody.isBlank()) return null
        return runCatching {
            val root = JSONObject(jsonBody)
            val data = root.optJSONObject("data")
            val media = data?.optJSONObject("xdt_shortcode_media")
                ?: root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
                ?: root.optJSONArray("items")?.optJSONObject(0)
                ?: root.optJSONObject("shortcode_media")
                ?: return@runCatching null

            val ownerObj = media.optJSONObject("owner") ?: media.optJSONObject("user")
            val username = ownerObj?.optString("username")?.ifBlank { "Instagram" } ?: "Instagram"
            val caption = media.optJSONObject("edge_media_to_caption")
                ?.optJSONArray("edges")?.optJSONObject(0)
                ?.optJSONObject("node")?.optString("text")
                ?: media.optJSONObject("caption")?.optString("text")
                ?: "Instagram_$username"

            val variants = mutableListOf<MediaStreamVariant>()
            val seenUrls = mutableSetOf<String>()

            fun addVideoCandidate(
                rawUrl: String,
                label: String,
                width: Int,
                height: Int,
                audioUrl: String? = null
            ) {
                val u = normalizeSocialCdnStreamUrl(unescapeJsonUrl(rawUrl))
                if (u.startsWith("http") && seenUrls.add(u)) {
                    val resLabel = when {
                        height >= 1080 || width >= 1080 -> "1080p HD"
                        height >= 720 || width >= 720 -> "720p HD"
                        height > 0 -> "${height}p"
                        else -> "Original HD"
                    }
                    variants.add(
                        MediaStreamVariant(
                            variantId = "ig_${variants.size}",
                            label = "$label · $resLabel",
                            containerExtension = "mp4",
                            mimeType = "video/mp4",
                            resolutionLabel = resLabel,
                            widthPx = width.coerceAtLeast(720),
                            heightPx = height.coerceAtLeast(1280),
                            frameRateFps = 30,
                            videoCodec = "H.264",
                            videoBitrateKbps = if (height >= 1080) 5500 else 2800,
                            videoStreamUrl = u,
                            audioCodec = "AAC",
                            audioBitrateKbps = 128,
                            audioStreamUrl = audioUrl,
                            hasEmbeddedAudio = audioUrl == null,
                            requiresAudioVideoMux = audioUrl != null,
                            isWatermarkFree = true,
                            refererHeader = "https://www.instagram.com/",
                            cookieHeader = cookies,
                            userAgentHeader = DESKTOP_UA
                        )
                    )
                }
            }

            // 1. Direct video_url
            val directVideoUrl = media.optString("video_url", "")
            val dims = media.optJSONObject("dimensions")
            val mediaW = dims?.optInt("width", 0) ?: media.optInt("original_width", 0)
            val mediaH = dims?.optInt("height", 0) ?: media.optInt("original_height", 0)
            if (directVideoUrl.startsWith("http")) {
                addVideoCandidate(directVideoUrl, "Instagram Reel/Video", mediaW, mediaH)
            }

            // 2. video_versions array (mobile/web API — sorted highest quality first)
            val videoVersions = media.optJSONArray("video_versions")
            if (videoVersions != null) {
                val sorted = (0 until videoVersions.length()).mapNotNull { videoVersions.optJSONObject(it) }
                    .sortedByDescending { it.optInt("width", 0).toLong() * it.optInt("height", 0).toLong() }
                sorted.forEachIndexed { idx, v ->
                    val vUrl = v.optString("url", "")
                    if (vUrl.startsWith("http")) {
                        val tag = if (idx == 0) "Original HD" else "Stream ${idx + 1}"
                        addVideoCandidate(vUrl, tag, v.optInt("width", 0), v.optInt("height", 0))
                    }
                }
            }

            // 3. DASH manifest XML (video + paired audio)
            val dashXml = media.optString("video_dash_manifest", "")
                .ifBlank { media.optJSONObject("dash_info")?.optString("video_dash_manifest", "").orEmpty() }
            if (dashXml.isNotBlank()) {
                val (dashVideo, dashAudio) = parseDashManifestVideoAndAudio(dashXml)
                if (!dashVideo.isNullOrBlank()) {
                    addVideoCandidate(dashVideo, "DASH HD + Audio", mediaW.coerceAtLeast(1080), mediaH.coerceAtLeast(1920), dashAudio)
                }
            }

            // 4. Carousel / sidecar children (GraphQL `edge_sidecar_to_children` or mobile `carousel_media`)
            val sidecarEdges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (sidecarEdges != null) {
                for (i in 0 until sidecarEdges.length()) {
                    val node = sidecarEdges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val vUrl = node.optString("video_url", "")
                    if (vUrl.startsWith("http")) {
                        val nDims = node.optJSONObject("dimensions")
                        addVideoCandidate(vUrl, "Slide ${i + 1} Video", nDims?.optInt("width", 0) ?: 0, nDims?.optInt("height", 0) ?: 0)
                    }
                }
            }
            val carouselArr = media.optJSONArray("carousel_media")
            if (carouselArr != null) {
                for (i in 0 until carouselArr.length()) {
                    val item = carouselArr.optJSONObject(i) ?: continue
                    val vv = item.optJSONArray("video_versions")?.optJSONObject(0)
                    val vUrl = vv?.optString("url", "").orEmpty()
                    if (vUrl.startsWith("http")) {
                        addVideoCandidate(vUrl, "Slide ${i + 1} Video", vv?.optInt("width", 0) ?: 0, vv?.optInt("height", 0) ?: 0)
                    }
                }
            }

            // 5. High-res photo fallback if it's an Instagram photo post (no video)
            if (variants.isEmpty()) {
                val imgUrl = media.optString("display_url", "")
                    .ifBlank {
                        media.optJSONObject("image_versions2")?.optJSONArray("candidates")
                            ?.optJSONObject(0)?.optString("url", "").orEmpty()
                    }
                if (imgUrl.startsWith("http")) {
                    variants.add(
                        MediaStreamVariant(
                            variantId = "ig_photo_0",
                            label = "Original Photo (JPG)",
                            containerExtension = "jpg",
                            mimeType = "image/jpeg",
                            resolutionLabel = if (mediaW > 0) "${mediaW}×${mediaH}" else "Original",
                            widthPx = mediaW,
                            heightPx = mediaH,
                            frameRateFps = 0,
                            videoCodec = "JPEG",
                            videoBitrateKbps = 0,
                            videoStreamUrl = unescapeJsonUrl(imgUrl),
                            audioCodec = "",
                            audioBitrateKbps = 0,
                            audioStreamUrl = null,
                            hasEmbeddedAudio = true,
                            requiresAudioVideoMux = false,
                            isWatermarkFree = true,
                            refererHeader = "https://www.instagram.com/",
                            cookieHeader = cookies,
                            userAgentHeader = DESKTOP_UA
                        )
                    )
                }
            }

            if (variants.isEmpty()) return@runCatching null
            val best = FastDownloadEngine.selectBestStreamVariant(variants) ?: variants.first()
            val cleanTitle = caption.lineSequence().firstOrNull()?.trim()?.take(60)
                ?.ifBlank { "Instagram_$username" } ?: "Instagram_$username"

            ResolvedMediaManifest(
                sourceUrl = sourceUrl,
                platformName = "Instagram",
                title = cleanTitle,
                creatorAttribution = "@$username",
                suggestedFileName = "${FastDownloadEngine.sanitizeFileName(cleanTitle, "Instagram_Video")}.${best.containerExtension}",
                isSocialPlatform = true,
                variants = variants,
                recommendedVariantId = best.variantId
            )
        }.getOrNull()
    }

    /**
     * Parses Instagram `/embed/captioned/` HTML payloads (which frequently expose `gql_data`,
     * `video_url`, `video_versions`, or `video_dash_manifest` even for unauthenticated requests).
     */
    fun parseInstagramEmbedHtml(
        html: String,
        sourceUrl: String,
        cookies: String = ""
    ): ResolvedMediaManifest? {
        if (html.isBlank()) return null

        // First try extracting embedded `gql_data` JSON block if present
        val gqlIdx = html.indexOf("\"gql_data\"")
        if (gqlIdx != -1) {
            val startBrace = html.indexOf('{', gqlIdx)
            if (startBrace != -1) {
                val extractedObj = extractBalancedJsonObject(html, startBrace)
                if (extractedObj != null) {
                    parseInstagramJsonToVariants(extractedObj, sourceUrl, cookies)?.let { return it }
                }
            }
        }

        // Also scan escaped JSON `"video_url":"https://..."` and `"video_dash_manifest":"..."`
        val variants = mutableListOf<MediaStreamVariant>()
        val seen = mutableSetOf<String>()

        val videoUrlRegex = Regex(""""video_url"\s*:\s*"(https?:[^"]+)"""")
        for (match in videoUrlRegex.findAll(html).take(6)) {
            val u = normalizeSocialCdnStreamUrl(unescapeJsonUrl(match.groupValues[1]))
            if (u.startsWith("http") && !isLikelyImageOrStaticAssetUrl(u) && seen.add(u)) {
                variants.add(
                    MediaStreamVariant(
                        variantId = "ig_embed_${variants.size}",
                        label = "Instagram Video (HD)",
                        containerExtension = "mp4",
                        mimeType = "video/mp4",
                        resolutionLabel = "HD",
                        widthPx = 1080,
                        heightPx = 1920,
                        frameRateFps = 30,
                        videoCodec = "H.264",
                        videoBitrateKbps = 4500,
                        videoStreamUrl = u,
                        audioCodec = "AAC",
                        audioBitrateKbps = 128,
                        audioStreamUrl = null,
                        hasEmbeddedAudio = true,
                        requiresAudioVideoMux = false,
                        isWatermarkFree = true,
                        refererHeader = "https://www.instagram.com/",
                        cookieHeader = cookies,
                        userAgentHeader = DESKTOP_UA
                    )
                )
            }
        }

        val dashRegex = Regex(""""video_dash_manifest"\s*:\s*"((?:[^"\\]|\\.)+)"""")
        dashRegex.find(html)?.groupValues?.getOrNull(1)?.let { rawDash ->
            val (dashV, dashA) = parseDashManifestVideoAndAudio(rawDash)
            if (!dashV.isNullOrBlank() && seen.add(dashV)) {
                variants.add(
                    MediaStreamVariant(
                        variantId = "ig_embed_dash_${variants.size}",
                        label = if (dashA != null) "Instagram DASH HD + Audio" else "Instagram DASH HD",
                        containerExtension = "mp4",
                        mimeType = "video/mp4",
                        resolutionLabel = "1080p HD",
                        widthPx = 1080,
                        heightPx = 1920,
                        frameRateFps = 30,
                        videoCodec = "H.264",
                        videoBitrateKbps = 5500,
                        videoStreamUrl = dashV,
                        audioCodec = "AAC",
                        audioBitrateKbps = 128,
                        audioStreamUrl = dashA,
                        hasEmbeddedAudio = dashA == null,
                        requiresAudioVideoMux = dashA != null,
                        isWatermarkFree = true,
                        refererHeader = "https://www.instagram.com/",
                        cookieHeader = cookies,
                        userAgentHeader = DESKTOP_UA
                    )
                )
            }
        }

        if (variants.isEmpty()) return null
        val username = Regex(""""username"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.getOrNull(1) ?: "Instagram"
        val best = FastDownloadEngine.selectBestStreamVariant(variants) ?: variants.first()
        return ResolvedMediaManifest(
            sourceUrl = sourceUrl,
            platformName = "Instagram",
            title = "Instagram @$username",
            creatorAttribution = "@$username",
            suggestedFileName = "${FastDownloadEngine.sanitizeFileName("Instagram_$username", "Instagram_Video")}.mp4",
            isSocialPlatform = true,
            variants = variants,
            recommendedVariantId = best.variantId
        )
    }

    /**
     * Parses `api.vxtwitter.com` or `cdn.syndication.twimg.com` JSON into real HD MP4 streams.
     */
    fun parseVxTwitterJson(jsonBody: String, sourceUrl: String): ResolvedMediaManifest? {
        if (jsonBody.isBlank()) return null
        return runCatching {
            val root = JSONObject(jsonBody)
            val author = root.optString("user_screen_name").ifBlank {
                root.optJSONObject("user")?.optString("screen_name").orEmpty()
            }.ifBlank { "X" }
            val text = root.optString("text", "X_Video").lineSequence().firstOrNull()?.trim()?.take(60)
                ?: "X_Video"

            val variants = mutableListOf<MediaStreamVariant>()
            val seen = mutableSetOf<String>()

            // vxtwitter format: media_extended[]
            val mediaExt = root.optJSONArray("media_extended")
            if (mediaExt != null) {
                for (i in 0 until mediaExt.length()) {
                    val m = mediaExt.optJSONObject(i) ?: continue
                    val type = m.optString("type", "")
                    val mUrl = m.optString("url", "")
                    if ((type == "video" || type == "gif" || mUrl.contains(".mp4")) && mUrl.startsWith("http") && seen.add(mUrl)) {
                        val sizeObj = m.optJSONObject("size")
                        val w = sizeObj?.optInt("width", 1280) ?: 1280
                        val h = sizeObj?.optInt("height", 720) ?: 720
                        variants.add(
                            MediaStreamVariant(
                                variantId = "x_vid_$i",
                                label = "X Video (${h}p MP4)",
                                containerExtension = "mp4",
                                mimeType = "video/mp4",
                                resolutionLabel = "${h}p",
                                widthPx = w,
                                heightPx = h,
                                frameRateFps = 30,
                                videoCodec = "H.264",
                                videoBitrateKbps = 4000,
                                videoStreamUrl = mUrl,
                                audioCodec = "AAC",
                                audioBitrateKbps = 128,
                                audioStreamUrl = null,
                                hasEmbeddedAudio = true,
                                requiresAudioVideoMux = false,
                                isWatermarkFree = true
                            )
                        )
                    }
                }
            }

            // Syndication format: video.variants[] or mediaDetails[].video_info.variants[]
            fun collectSyndicationVariants(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val ct = v.optString("type", v.optString("content_type", ""))
                    val u = v.optString("src", v.optString("url", ""))
                    if (ct.contains("mp4") && u.startsWith("http") && seen.add(u)) {
                        val bitrate = v.optInt("bitrate", 0) / 1000
                        val resMatch = Regex("""/(\d{3,4})x(\d{3,4})/""").find(u)
                        val w = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1280
                        val h = resMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 720
                        variants.add(
                            MediaStreamVariant(
                                variantId = "x_syn_${variants.size}",
                                label = "${h}p MP4${if (bitrate > 0) " (${bitrate}k)" else ""}",
                                containerExtension = "mp4",
                                mimeType = "video/mp4",
                                resolutionLabel = "${h}p",
                                widthPx = w,
                                heightPx = h,
                                frameRateFps = 30,
                                videoCodec = "H.264",
                                videoBitrateKbps = bitrate.coerceAtLeast(1500),
                                videoStreamUrl = u,
                                audioCodec = "AAC",
                                audioBitrateKbps = 128,
                                audioStreamUrl = null,
                                hasEmbeddedAudio = true,
                                requiresAudioVideoMux = false,
                                isWatermarkFree = true
                            )
                        )
                    }
                }
            }

            collectSyndicationVariants(root.optJSONObject("video")?.optJSONArray("variants"))
            val mediaDetails = root.optJSONArray("mediaDetails")
            if (mediaDetails != null) {
                for (i in 0 until mediaDetails.length()) {
                    collectSyndicationVariants(
                        mediaDetails.optJSONObject(i)?.optJSONObject("video_info")?.optJSONArray("variants")
                    )
                }
            }

            if (variants.isEmpty()) return@runCatching null
            val sorted = variants.sortedByDescending { it.widthPx.toLong() * it.heightPx.toLong() + it.videoBitrateKbps }
            val best = FastDownloadEngine.selectBestStreamVariant(sorted) ?: sorted.first()
            ResolvedMediaManifest(
                sourceUrl = sourceUrl,
                platformName = "X",
                title = text.ifBlank { "X @$author" },
                creatorAttribution = "@$author",
                suggestedFileName = "${FastDownloadEngine.sanitizeFileName("X_${author}_${text.take(30)}", "X_Video")}.mp4",
                isSocialPlatform = true,
                variants = sorted,
                recommendedVariantId = best.variantId
            )
        }.getOrNull()
    }

    /**
     * Parses Facebook page HTML for HD/SD native MP4 streams and embedded DASH XML manifests.
     */
    fun parseFacebookHtmlToManifest(
        html: String,
        sourceUrl: String,
        cookies: String = ""
    ): ResolvedMediaManifest? {
        if (html.isBlank()) return null
        val variants = mutableListOf<MediaStreamVariant>()
        val seen = mutableSetOf<String>()

        val patterns = listOf(
            Triple("HD Video", 1080, Regex(""""(?:browser_native_hd_url|playable_url_quality_hd|hd_src_no_ratelimit|hd_src)"\s*:\s*"(https?:[^"]+)"""")),
            Triple("SD Video", 720, Regex(""""(?:browser_native_sd_url|playable_url|sd_src_no_ratelimit|sd_src)"\s*:\s*"(https?:[^"]+)""""))
        )
        for ((label, height, rx) in patterns) {
            for (m in rx.findAll(html).take(3)) {
                val u = normalizeSocialCdnStreamUrl(unescapeJsonUrl(m.groupValues[1]))
                if (u.startsWith("http") && !isLikelyImageOrStaticAssetUrl(u) && seen.add(u)) {
                    variants.add(
                        MediaStreamVariant(
                            variantId = "fb_${variants.size}",
                            label = "Facebook $label (MP4)",
                            containerExtension = "mp4",
                            mimeType = "video/mp4",
                            resolutionLabel = "${height}p",
                            widthPx = if (height >= 1080) 1920 else 1280,
                            heightPx = height,
                            frameRateFps = 30,
                            videoCodec = "H.264",
                            videoBitrateKbps = if (height >= 1080) 5000 else 2200,
                            videoStreamUrl = u,
                            audioCodec = "AAC",
                            audioBitrateKbps = 128,
                            audioStreamUrl = null,
                            hasEmbeddedAudio = true,
                            requiresAudioVideoMux = false,
                            isWatermarkFree = true,
                            refererHeader = "https://www.facebook.com/",
                            cookieHeader = cookies,
                            userAgentHeader = DESKTOP_UA
                        )
                    )
                }
            }
        }

        // Also check DASH XML inside Facebook HTML
        val dashRegex = Regex(""""dash_manifest"\s*:\s*"((?:[^"\\]|\\.)+)"""")
        dashRegex.find(html)?.groupValues?.getOrNull(1)?.let { rawDash ->
            val (dashV, dashA) = parseDashManifestVideoAndAudio(rawDash)
            if (!dashV.isNullOrBlank() && seen.add(dashV)) {
                variants.add(
                    MediaStreamVariant(
                        variantId = "fb_dash_${variants.size}",
                        label = if (dashA != null) "Facebook DASH HD + Audio" else "Facebook DASH HD",
                        containerExtension = "mp4",
                        mimeType = "video/mp4",
                        resolutionLabel = "1080p HD",
                        widthPx = 1920,
                        heightPx = 1080,
                        frameRateFps = 30,
                        videoCodec = "H.264",
                        videoBitrateKbps = 5500,
                        videoStreamUrl = dashV,
                        audioCodec = "AAC",
                        audioBitrateKbps = 128,
                        audioStreamUrl = dashA,
                        hasEmbeddedAudio = dashA == null,
                        requiresAudioVideoMux = dashA != null,
                        isWatermarkFree = true,
                        refererHeader = "https://www.facebook.com/",
                        cookieHeader = cookies,
                        userAgentHeader = DESKTOP_UA
                    )
                )
            }
        }

        if (variants.isEmpty()) return null
        val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()?.take(60)?.ifBlank { "Facebook_Video" }
            ?: "Facebook_Video"
        val best = FastDownloadEngine.selectBestStreamVariant(variants) ?: variants.first()
        return ResolvedMediaManifest(
            sourceUrl = sourceUrl,
            platformName = "Facebook",
            title = title,
            creatorAttribution = "Facebook",
            suggestedFileName = "${FastDownloadEngine.sanitizeFileName(title, "Facebook_Video")}.mp4",
            isSocialPlatform = true,
            variants = variants,
            recommendedVariantId = best.variantId
        )
    }

    // ==================== LIVE NETWORK EXTRACTORS ====================

    fun extractInstagramManifest(
        client: OkHttpClient,
        rawUrl: String,
        cookies: String
    ): ResolvedMediaManifest? {
        // Step 0: Resolve /share/reel/... or instagr.am redirects to canonical shortcode URL
        val canonicalUrl = if (rawUrl.contains("/share/", ignoreCase = true) || rawUrl.contains("instagr.am", ignoreCase = true)) {
            resolveRedirectUrl(client, rawUrl, cookies, DESKTOP_UA)
        } else rawUrl

        val shortcode = extractInstagramShortcode(canonicalUrl) ?: extractInstagramShortcode(rawUrl)

        // Strategy 1: Instagram Public GraphQL Document API (works for public Reels & Posts without login!)
        if (!shortcode.isNullOrBlank()) {
            val csrfToken = extractCookieValue(cookies, "csrftoken") ?: "missing"
            for (docId in IG_GRAPHQL_DOC_IDS) {
                val gqlResult = runCatching {
                    val variables = JSONObject().put("shortcode", shortcode).toString()
                    val formBody = FormBody.Builder()
                        .add("av", "0")
                        .add("fb_api_caller_class", "RelayModern")
                        .add("fb_api_req_friendly_name", "PolarisPostActionLoadPostQueryQuery")
                        .add("variables", variables)
                        .add("server_timestamps", "true")
                        .add("doc_id", docId)
                        .build()

                    val reqBuilder = Request.Builder()
                        .url("https://www.instagram.com/graphql/query")
                        .header("User-Agent", DESKTOP_UA)
                        .header("Accept", "*/*")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("X-IG-App-ID", IG_APP_ID)
                        .header("X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery")
                        .header("X-ASBD-ID", "129477")
                        .header("X-CSRFToken", csrfToken)
                        .header("Origin", "https://www.instagram.com")
                        .header("Referer", "https://www.instagram.com/reel/$shortcode/")
                        .post(formBody)
                    if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)

                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string().orEmpty()
                        parseInstagramJsonToVariants(body, canonicalUrl, cookies)
                    }
                }.getOrNull()
                if (gqlResult != null && gqlResult.variants.isNotEmpty()) return gqlResult
            }

            // Strategy 2: Instagram Captioned Embed endpoints (publicly accessible for most Reels/Posts)
            val embedUrls = listOf(
                "https://www.instagram.com/p/$shortcode/embed/captioned/",
                "https://www.instagram.com/reel/$shortcode/embed/captioned/"
            )
            for (embedUrl in embedUrls) {
                val embedManifest = runCatching {
                    val reqBuilder = Request.Builder()
                        .url(embedUrl)
                        .header("User-Agent", DESKTOP_UA)
                        .header("Accept", "text/html,application/xhtml+xml,*/*")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Sec-Fetch-Dest", "iframe")
                        .header("Sec-Fetch-Mode", "navigate")
                    if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)

                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val html = resp.body?.string().orEmpty()
                        parseInstagramEmbedHtml(html, canonicalUrl, cookies)
                    }
                }.getOrNull()
                if (embedManifest != null && embedManifest.variants.isNotEmpty()) return embedManifest
            }

            // Strategy 3: Authenticated Web JSON (?__a=1&__d=dis) using WebView session cookies
            if (cookies.isNotBlank()) {
                val authJsonManifest = runCatching {
                    val req = Request.Builder()
                        .url("https://www.instagram.com/p/$shortcode/?__a=1&__d=dis")
                        .header("User-Agent", MOBILE_UA)
                        .header("Accept", "application/json")
                        .header("X-IG-App-ID", IG_APP_ID)
                        .header("Cookie", cookies)
                        .header("Referer", "https://www.instagram.com/")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        parseInstagramJsonToVariants(resp.body?.string().orEmpty(), canonicalUrl, cookies)
                    }
                }.getOrNull()
                if (authJsonManifest != null && authJsonManifest.variants.isNotEmpty()) return authJsonManifest
            }
        }

        return null
    }

    fun extractXTwitterManifest(client: OkHttpClient, url: String): ResolvedMediaManifest? {
        val statusId = extractXStatusId(url) ?: return null

        // Strategy 1: vxtwitter public JSON API
        val vxResult = runCatching {
            val req = Request.Builder()
                .url("https://api.vxtwitter.com/i/status/$statusId")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseVxTwitterJson(resp.body?.string().orEmpty(), url)
            }
        }.getOrNull()
        if (vxResult != null && vxResult.variants.isNotEmpty()) return vxResult

        // Strategy 2: Twitter Syndication public endpoint
        return runCatching {
            val req = Request.Builder()
                .url("https://cdn.syndication.twimg.com/tweet-result?id=$statusId&lang=en&token=a")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseVxTwitterJson(resp.body?.string().orEmpty(), url)
            }
        }.getOrNull()
    }

    fun extractFacebookManifest(
        client: OkHttpClient,
        rawUrl: String,
        cookies: String
    ): ResolvedMediaManifest? {
        val resolved = if (rawUrl.contains("fb.watch", ignoreCase = true) || rawUrl.contains("/share/", ignoreCase = true)) {
            resolveRedirectUrl(client, rawUrl, cookies, DESKTOP_UA)
        } else rawUrl

        val urlsToTry = listOf(
            resolved,
            resolved.replace("m.facebook.com", "www.facebook.com")
        ).distinct()

        for (target in urlsToTry) {
            val manifest = runCatching {
                val reqBuilder = Request.Builder()
                    .url(target)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    parseFacebookHtmlToManifest(html, resolved, cookies)
                }
            }.getOrNull()
            if (manifest != null && manifest.variants.isNotEmpty()) return manifest
        }
        return null
    }

    fun resolveRedirectUrl(
        client: OkHttpClient,
        url: String,
        cookies: String = "",
        userAgent: String = DESKTOP_UA
    ): String {
        return runCatching {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,*/*")
            if (cookies.isNotBlank()) b.header("Cookie", cookies)
            client.newCall(b.build()).execute().use { resp ->
                resp.request.url.toString()
            }
        }.getOrDefault(url)
    }

    private fun extractCookieValue(cookieHeader: String, name: String): String? {
        if (cookieHeader.isBlank()) return null
        return cookieHeader.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun unescapeJsonUrl(raw: String): String = raw
        .replace("\\u0026", "&")
        .replace("\\u002F", "/")
        .replace("\\u003D", "=")
        .replace("\\/", "/")
        .replace("&amp;", "&")
        .trim()

    private fun extractBalancedJsonObject(source: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        val limit = minOf(source.length, startIndex + 400_000)
        for (i in startIndex until limit) {
            val c = source[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\' && inString) {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return source.substring(startIndex, i + 1)
                }
            }
        }
        return null
    }
}
