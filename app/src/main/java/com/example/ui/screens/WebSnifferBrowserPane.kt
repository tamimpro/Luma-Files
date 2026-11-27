package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.engine.SocialMediaExtractors
import com.example.domain.model.SniffedWebMedia
import com.example.ui.LumaFormatUtils
import java.net.URLEncoder

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val MEDIA_URL_PATTERNS = listOf(
    ".mp4", ".webm", ".m3u8", ".mpd", ".mkv", ".mov", ".mp3", ".m4a", ".aac", ".ogg", ".flac",
    ".pdf", ".zip", ".apk", ".rar", ".7z", ".docx", ".xlsx", ".csv",
    "videoplayback", "googlevideo.com", "v.redd.it", "video.twimg.com", "tiktokcdn",
    "video-", "cdninstagram.com/v/", "fbcdn.net/v/", "bytestart="
)

/**
 * Injected into pages to capture Instagram/Facebook/X/TikTok live GraphQL/XHR video_versions,
 * video_url, playable_url_quality_hd, and <video>/<audio> sources even when MediaSource blob: URLs are used.
 */
private val SOCIAL_MEDIA_INTERCEPTOR_JS = """
(function() {
  if (window.__lumaHookInstalled) {
    if (window.__lumaScanDom) window.__lumaScanDom();
    return;
  }
  window.__lumaHookInstalled = true;

  function emit(url, quality, kind) {
    if (!url || typeof url !== 'string' || url.indexOf('http') !== 0) return;
    if (window.LumaSniffer) {
      window.LumaSniffer.onRichMediaFound(url, document.title || 'Media', location.href, quality || '', kind || 'VIDEO');
    }
  }

  function scanTextPayload(text) {
    if (!text || text.length < 40 || text.length > 1200000) return;
    try {
      var hdMatches = text.match(/"(?:browser_native_hd_url|playable_url_quality_hd|hd_src|video_url)"\s*:\s*"(https?:[^"]+)"/g);
      if (hdMatches) {
        hdMatches.slice(0, 6).forEach(function(m) {
          var u = m.replace(/^[^:]+:\s*"/, '').replace(/"$/, '')
            .replace(/\\u0026/g, '&').replace(/\\u002F/g, '/').replace(/\\\//g, '/');
          emit(u, 'HD', 'VIDEO');
        });
      }
      if (text.indexOf('video_versions') !== -1) {
        var verMatches = text.match(/"url"\s*:\s*"(https?:[^"]+\.mp4[^"]*)"/g);
        if (verMatches) {
          verMatches.slice(0, 4).forEach(function(m) {
            var u = m.replace(/^[^:]+:\s*"/, '').replace(/"$/, '')
              .replace(/\\u0026/g, '&').replace(/\\u002F/g, '/').replace(/\\\//g, '/');
            emit(u, 'HD Reel', 'VIDEO');
          });
        }
      }
    } catch (e) {}
  }

  window.__lumaScanDom = function() {
    try {
      document.querySelectorAll('video, audio, source').forEach(function(el) {
        var src = el.currentSrc || el.src || '';
        if (src.indexOf('http') === 0) {
          var kind = el.tagName === 'AUDIO' ? 'AUDIO' : 'VIDEO';
          emit(src, kind === 'AUDIO' ? 'Audio' : 'Original', kind);
        }
      });
    } catch (e) {}
  };

  try {
    var origFetch = window.fetch;
    if (origFetch) {
      window.fetch = function() {
        return origFetch.apply(this, arguments).then(function(resp) {
          try {
            var ct = (resp.headers && resp.headers.get('content-type')) || '';
            if (ct.indexOf('json') !== -1 || ct.indexOf('javascript') !== -1 || ct.indexOf('text') !== -1) {
              resp.clone().text().then(scanTextPayload).catch(function(){});
            }
          } catch (e) {}
          return resp;
        });
      };
    }
  } catch (e) {}

  try {
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function() {
      this.addEventListener('load', function() {
        try {
          if (this.responseType === '' || this.responseType === 'text') {
            scanTextPayload(this.responseText);
          }
        } catch (e) {}
      });
      return origSend.apply(this, arguments);
    };
  } catch (e) {}

  window.__lumaScanDom();
  setInterval(window.__lumaScanDom, 2500);
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSnifferBrowserPane(
    initialUrl: String,
    handoffSequenceId: Long = 0L,
    detectedMedia: List<SniffedWebMedia>,
    onMediaDetected: (SniffedWebMedia) -> Unit,
    onClearDetected: () -> Unit,
    onDownloadSniffedItem: (SniffedWebMedia) -> Unit
) {
    var addressInput by remember { mutableStateOf(initialUrl.ifBlank { "https://www.instagram.com" }) }
    var currentPageTitle by remember { mutableStateOf("") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var desktopMode by remember { mutableStateOf(false) }
    var showDetectedSheet by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Track last seen audio stream per page so Instagram/FB/YouTube DASH video+audio auto-pair for muxing
    var lastPageAudioUrl by remember { mutableStateOf<String?>(null) }

    fun normalizeAndLoad(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val target = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        addressInput = target
        lastPageAudioUrl = null
        onClearDetected()
        webViewRef?.loadUrl(target)
    }

    // Auto-navigate when DownloadManager hands off a social URL (e.g., Instagram Reel)
    LaunchedEffect(initialUrl, handoffSequenceId) {
        if (initialUrl.isNotBlank() && handoffSequenceId > 0L) {
            normalizeAndLoad(initialUrl)
        }
    }

    fun emitCandidate(
        rawUrl: String,
        mimeHint: String = "",
        titleHint: String = "",
        sizeBytes: Long = -1L,
        qualityHint: String = "",
        kindHint: String = ""
    ) {
        if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) return
        // Reject images, thumbnails, avatars, JS/CSS/fonts immediately
        if (SocialMediaExtractors.isLikelyImageOrStaticAssetUrl(rawUrl)) return

        val normalizedUrl = SocialMediaExtractors.normalizeSocialCdnStreamUrl(rawUrl)
        val lower = normalizedUrl.lowercase()
        val isLikelyMedia = kindHint.isNotBlank() ||
            MEDIA_URL_PATTERNS.any { lower.contains(it) } ||
            mimeHint.startsWith("video/") ||
            mimeHint.startsWith("audio/") ||
            mimeHint.contains("mpegurl") ||
            mimeHint == "application/pdf" ||
            mimeHint == "application/zip" ||
            mimeHint == "application/vnd.android.package-archive"

        if (!isLikelyMedia) return

        val isAudioOnly = kindHint == "AUDIO" || SocialMediaExtractors.isLikelyAudioOnlyStreamUrl(rawUrl, mimeHint)
        if (isAudioOnly) {
            lastPageAudioUrl = normalizedUrl
        }

        val pageReferer = addressInput
        val cookie = runCatching {
            val hostCookies = CookieManager.getInstance().getCookie(normalizedUrl).orEmpty()
            val pageCookies = CookieManager.getInstance().getCookie(pageReferer).orEmpty()
            listOf(hostCookies, pageCookies).filter { it.isNotBlank() }.distinct().joinToString("; ")
        }.getOrDefault("")

        val isHls = lower.contains(".m3u8") || mimeHint.contains("mpegurl")
        val ext = when {
            isHls -> "mp4"
            isAudioOnly && lower.contains(".mp3") -> "mp3"
            isAudioOnly -> "m4a"
            lower.contains(".mp4") || mimeHint.contains("mp4") || lower.contains("cdninstagram.com") || lower.contains("fbcdn.net") -> "mp4"
            lower.contains(".webm") || mimeHint.contains("webm") -> "webm"
            lower.contains(".pdf") || mimeHint.contains("pdf") -> "pdf"
            lower.contains(".apk") || mimeHint.contains("android.package") -> "apk"
            lower.contains(".zip") -> "zip"
            else -> Uri.parse(normalizedUrl).lastPathSegment?.substringAfterLast('.', "")?.take(5)?.ifBlank { "mp4" } ?: "mp4"
        }

        val cleanTitle = titleHint.ifBlank { currentPageTitle }.ifBlank {
            Uri.parse(normalizedUrl).lastPathSegment?.substringBefore('?') ?: "Download"
        }.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().take(54).ifBlank { "Media" }

        val isDashVideo = !isAudioOnly && !isHls && (rawUrl.contains("bytestart=", ignoreCase = true) || lower.contains("googlevideo.com"))
        val pairedAudio = if (isDashVideo) lastPageAudioUrl?.takeIf { it != normalizedUrl } else null

        val mediaKind = when {
            isHls -> "HLS"
            isAudioOnly -> "AUDIO"
            pairedAudio != null -> "VIDEO_MUX"
            ext in setOf("mp4", "webm", "mkv", "mov") -> "VIDEO"
            else -> "FILE"
        }

        val qualityLabel = qualityHint.ifBlank {
            when {
                isHls -> "HLS Stream"
                pairedAudio != null -> "HD Video + Audio"
                isAudioOnly -> "Audio"
                lower.contains("2160") || lower.contains("4k") -> "4K"
                lower.contains("1080") -> "1080p HD"
                lower.contains("720") -> "720p"
                else -> ext.uppercase()
            }
        }

        onMediaDetected(
            SniffedWebMedia(
                url = normalizedUrl,
                fileName = if (cleanTitle.contains('.')) cleanTitle else "$cleanTitle.$ext",
                mimeType = mimeHint.ifBlank {
                    when {
                        isHls -> "application/vnd.apple.mpegurl"
                        isAudioOnly -> "audio/mp4"
                        ext == "mp4" -> "video/mp4"
                        else -> "application/octet-stream"
                    }
                },
                contentLength = sizeBytes,
                sourcePageUrl = pageReferer,
                referer = pageReferer,
                cookies = cookie,
                userAgent = if (desktopMode) DESKTOP_UA else MOBILE_UA,
                isHlsOrDash = isHls,
                audioStreamUrl = pairedAudio,
                qualityLabel = qualityLabel,
                mediaKind = mediaKind
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("web_sniffer_browser_pane")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Browser Address & Nav Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = { webViewRef?.takeIf { it.canGoBack() }?.goBack() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { webViewRef?.takeIf { it.canGoForward() }?.goForward() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                        }
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            placeholder = { Text("Enter URL or search") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { normalizeAndLoad(addressInput) }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sniffer_url_input")
                        )
                        IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                desktopMode = !desktopMode
                                webViewRef?.settings?.userAgentString = if (desktopMode) DESKTOP_UA else MOBILE_UA
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                if (desktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                contentDescription = "Toggle Desktop Mode",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Quick Site Shortcuts
                    LazyRow(
                        contentPadding = PaddingValues(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sites = listOf(
                            "Instagram" to "https://www.instagram.com",
                            "YouTube" to "https://m.youtube.com",
                            "TikTok" to "https://www.tiktok.com",
                            "X" to "https://x.com",
                            "Facebook" to "https://m.facebook.com",
                            "Reddit" to "https://www.reddit.com"
                        )
                        items(sites) { (label, siteUrl) ->
                            FilterChip(
                                selected = addressInput.contains(Uri.parse(siteUrl).host ?: "", ignoreCase = true),
                                onClick = { normalizeAndLoad(siteUrl) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Embedded WebView with Live XHR/Fetch + Network Stream Sniffer
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = MOBILE_UA

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onRichMediaFound(mediaUrl: String, title: String, pageUrl: String, quality: String, kind: String) {
                                    post {
                                        emitCandidate(
                                            rawUrl = mediaUrl,
                                            titleHint = title,
                                            qualityHint = quality,
                                            kindHint = kind
                                        )
                                    }
                                }
                            },
                            "LumaSniffer"
                        )

                        setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
                            val name = contentDisposition?.substringAfter("filename=", "")?.trim('"', '\'', ' ')
                                ?.ifBlank { "" } ?: ""
                            emitCandidate(url, mimeHint = mimeType.orEmpty(), titleHint = name, sizeBytes = contentLength, kindHint = "FILE")
                            showDetectedSheet = true
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) currentPageTitle = title
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (!url.isNullOrBlank()) addressInput = url
                                view?.evaluateJavascript(SOCIAL_MEDIA_INTERCEPTOR_JS, null)
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val reqUrl = request?.url?.toString()
                                if (!reqUrl.isNullOrBlank()) {
                                    val accept = request.requestHeaders?.get("Accept").orEmpty()
                                    view?.post { emitCandidate(reqUrl, mimeHint = accept) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (!url.isNullOrBlank()) addressInput = url
                                view?.evaluateJavascript(SOCIAL_MEDIA_INTERCEPTOR_JS, null)
                            }
                        }

                        loadUrl(addressInput)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Floating Sniffer Capture Pill
        if (detectedMedia.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showDetectedSheet = true },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                text = { Text("Captured (${detectedMedia.size})") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("sniffer_detected_fab")
            )
        }
    }

    if (showDetectedSheet) {
        AlertDialog(
            onDismissRequest = { showDetectedSheet = false },
            title = { Text("Captured Media (${detectedMedia.size})") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detectedMedia, key = { it.url }) { media ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDetectedSheet = false
                                    onDownloadSniffedItem(media)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val icon = when (media.mediaKind) {
                                    "AUDIO" -> Icons.Default.AudioFile
                                    "FILE" -> Icons.Default.Description
                                    else -> Icons.Default.VideoFile
                                }
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        media.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeStr = if (media.contentLength > 0) " · ${LumaFormatUtils.formatBytes(media.contentLength)}" else ""
                                    val badge = media.qualityLabel.ifBlank {
                                        if (media.isHlsOrDash) "HLS Stream" else media.mimeType.substringAfter('/').uppercase()
                                    }
                                    Text(
                                        "$badge$sizeStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetectedSheet = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { onClearDetected(); showDetectedSheet = false }) { Text("Clear") }
            }
        )
    }
}
