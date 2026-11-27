package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.domain.model.SniffedWebMedia
import com.example.ui.LumaFormatUtils
import java.net.URLEncoder

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private val MEDIA_URL_PATTERNS = listOf(
    ".mp4", ".webm", ".m3u8", ".mpd", ".mkv", ".mov", ".mp3", ".m4a", ".aac", ".ogg", ".flac",
    ".pdf", ".zip", ".apk", ".rar", ".7z", ".docx", ".xlsx", ".csv",
    "videoplayback", "googlevideo.com", "v.redd.it", "video.twimg.com", "fbcdn.net", "cdninstagram.com", "tiktokcdn"
)

private val DOM_MEDIA_SCANNER_JS = """
(function() {
  try {
    var urls = new Set();
    document.querySelectorAll('video, audio, source').forEach(function(el) {
      if (el.currentSrc && el.currentSrc.indexOf('http') === 0) urls.add(el.currentSrc);
      if (el.src && el.src.indexOf('http') === 0) urls.add(el.src);
    });
    urls.forEach(function(u) {
      if (window.LumaSniffer) {
        window.LumaSniffer.onMediaFound(u, document.title || 'Media', location.href);
      }
    });
  } catch (e) {}
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSnifferBrowserPane(
    initialUrl: String,
    detectedMedia: List<SniffedWebMedia>,
    onMediaDetected: (SniffedWebMedia) -> Unit,
    onClearDetected: () -> Unit,
    onDownloadSniffedItem: (SniffedWebMedia) -> Unit
) {
    var addressInput by remember(initialUrl) {
        mutableStateOf(initialUrl.ifBlank { "https://m.youtube.com" })
    }
    var currentPageTitle by remember { mutableStateOf("") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var desktopMode by remember { mutableStateOf(false) }
    var showDetectedSheet by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun normalizeAndLoad(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val target = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        addressInput = target
        onClearDetected()
        webViewRef?.loadUrl(target)
    }

    fun emitCandidate(url: String, mimeHint: String = "", titleHint: String = "", sizeBytes: Long = -1L) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
        val lower = url.lowercase()
        val isLikelyMedia = MEDIA_URL_PATTERNS.any { lower.contains(it) } ||
            mimeHint.startsWith("video/") ||
            mimeHint.startsWith("audio/") ||
            mimeHint.contains("mpegurl") ||
            mimeHint == "application/pdf" ||
            mimeHint == "application/zip" ||
            mimeHint == "application/vnd.android.package-archive"

        if (!isLikelyMedia) return
        if (lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".png") || lower.endsWith(".svg") || lower.endsWith(".woff2")) return

        val cookie = runCatching { CookieManager.getInstance().getCookie(url).orEmpty() }.getOrDefault("")
        val pageReferer = addressInput
        val isHls = lower.contains(".m3u8") || mimeHint.contains("mpegurl")
        val ext = when {
            isHls -> "mp4"
            lower.contains(".mp4") || mimeHint.contains("mp4") -> "mp4"
            lower.contains(".webm") || mimeHint.contains("webm") -> "webm"
            lower.contains(".mp3") || mimeHint.contains("mpeg") -> "mp3"
            lower.contains(".m4a") -> "m4a"
            lower.contains(".pdf") || mimeHint.contains("pdf") -> "pdf"
            lower.contains(".apk") || mimeHint.contains("android.package") -> "apk"
            lower.contains(".zip") -> "zip"
            else -> Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.take(5)?.ifBlank { "mp4" } ?: "mp4"
        }
        val cleanTitle = titleHint.ifBlank { currentPageTitle }.ifBlank {
            Uri.parse(url).lastPathSegment?.substringBefore('?') ?: "Download"
        }.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().take(54)

        val qualityLabel = when {
            isHls -> "HLS Stream"
            lower.contains("2160") || lower.contains("4k") -> "4K"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            mimeHint.startsWith("audio/") -> "Audio"
            else -> ext.uppercase()
        }

        onMediaDetected(
            SniffedWebMedia(
                url = url,
                fileName = if (cleanTitle.contains('.')) cleanTitle else "$cleanTitle.$ext",
                mimeType = mimeHint.ifBlank { if (isHls) "application/vnd.apple.mpegurl" else "video/mp4" },
                contentLength = sizeBytes,
                sourcePageUrl = pageReferer,
                referer = pageReferer,
                cookies = cookie,
                userAgent = if (desktopMode) DESKTOP_UA else MOBILE_UA,
                isHlsOrDash = isHls
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
                            "YouTube" to "https://m.youtube.com",
                            "Instagram" to "https://www.instagram.com",
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

            // Embedded WebView with Media Interception
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
                                fun onMediaFound(mediaUrl: String, title: String, pageUrl: String) {
                                    post {
                                        emitCandidate(mediaUrl, titleHint = title)
                                    }
                                }
                            },
                            "LumaSniffer"
                        )

                        setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
                            val name = contentDisposition?.substringAfter("filename=", "")?.trim('"', '\'', ' ')
                                ?.ifBlank { "" } ?: ""
                            emitCandidate(url, mimeHint = mimeType.orEmpty(), titleHint = name, sizeBytes = contentLength)
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
                                view?.evaluateJavascript(DOM_MEDIA_SCANNER_JS, null)
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
                text = { Text("Detected (${detectedMedia.size})") },
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
            title = { Text("Detected Media (${detectedMedia.size})") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
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
                                Icon(Icons.Default.VideoFile, null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        media.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeStr = if (media.contentLength > 0) " · ${LumaFormatUtils.formatBytes(media.contentLength)}" else ""
                                    val badge = if (media.isHlsOrDash) "HLS Stream" else media.mimeType.substringAfter('/').uppercase()
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
