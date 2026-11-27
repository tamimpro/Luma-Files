package com.example

import com.example.domain.engine.AiKeyVaultStore
import com.example.domain.engine.PhotoAiStudioEngine
import com.example.domain.engine.SocialMediaExtractors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class SocialAndAiEngineRobolectricTest {

    @Test
    fun `verify Instagram Embed DASH Facebook X extraction CDN normalization audio detection and thumbnail filtering`() {
        // 1. Instagram shortcode extraction across /reel/, /reels/, /p/, /tv/, and username paths
        assertEquals(
            "CxYz123AbC_",
            SocialMediaExtractors.extractInstagramShortcode("https://www.instagram.com/reel/CxYz123AbC_/?igsh=abc123")
        )
        assertEquals(
            "DQ99kLmN01",
            SocialMediaExtractors.extractInstagramShortcode("https://www.instagram.com/creator_name/p/DQ99kLmN01/")
        )
        assertEquals(
            "TvCode8899",
            SocialMediaExtractors.extractInstagramShortcode("https://instagr.am/tv/TvCode8899?utm_source=ig")
        )
        assertEquals(
            "1890123456789012345",
            SocialMediaExtractors.extractXStatusId("https://x.com/androiddev/status/1890123456789012345?s=20")
        )

        // 2. Social CDN URL byte-range stripping & image thumbnail rejection
        val chunkedIgUrl = "https://scontent.cdninstagram.com/v/t50.2886-16/video_1080.mp4?efg=abc&bytestart=0&byteend=524287&_nc_ht=scontent.cdninstagram.com"
        val normalizedIgUrl = SocialMediaExtractors.normalizeSocialCdnStreamUrl(chunkedIgUrl)
        assertFalse("bytestart must be stripped", normalizedIgUrl.contains("bytestart="))
        assertFalse("byteend must be stripped", normalizedIgUrl.contains("byteend="))
        assertTrue("essential CDN params must be kept", normalizedIgUrl.contains("_nc_ht=scontent.cdninstagram.com"))

        assertTrue(
            "Instagram jpg thumbnail must be rejected",
            SocialMediaExtractors.isLikelyImageOrStaticAssetUrl(
                "https://scontent.cdninstagram.com/v/t51.2885-15/45678_n.jpg?stp=dst-jpg_e35"
            )
        )
        assertFalse(
            "Instagram mp4 video stream must be allowed",
            SocialMediaExtractors.isLikelyImageOrStaticAssetUrl(normalizedIgUrl)
        )

        // 3. Base64 `efg=` audio-only stream detection
        val audioEfg = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"vencode_tag":"dash_baseline_audio_v1"}""".toByteArray(Charsets.UTF_8))
        assertTrue(
            SocialMediaExtractors.isLikelyAudioOnlyStreamUrl(
                "https://scontent.cdninstagram.com/v/t50.2886-16/track.mp4?efg=$audioEfg"
            )
        )

        // 4. MPEG-DASH XML parser picks highest-bandwidth video + highest-bandwidth audio
        val sampleDashXml = """
            <MPD>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="1" bandwidth="1200000"><BaseURL>https://scontent.cdninstagram.com/v/low_480.mp4?bytestart=0&amp;byteend=100</BaseURL></Representation>
                  <Representation id="2" bandwidth="4800000"><BaseURL>https://scontent.cdninstagram.com/v/hd_1080.mp4?bytestart=0&amp;byteend=200</BaseURL></Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="3" bandwidth="128000"><BaseURL>https://scontent.cdninstagram.com/v/audio_aac.m4a</BaseURL></Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val (dashVideo, dashAudio) = SocialMediaExtractors.parseDashManifestVideoAndAudio(sampleDashXml)
        assertEquals("https://scontent.cdninstagram.com/v/hd_1080.mp4", dashVideo)
        assertEquals("https://scontent.cdninstagram.com/v/audio_aac.m4a", dashAudio)

        // 5. Instagram Embed HTML parsing (video_url + escaped CDN params stripped)
        val igEmbedHtml = """<html><script>window.__data={"username":"reelstar","video_url":"https:\/\/scontent.cdninstagram.com\/v\/embed_reel.mp4?oe=123\u0026bytestart=0\u0026byteend=65536"};</script></html>"""
        val embedManifest = SocialMediaExtractors.parseInstagramEmbedHtml(
            html = igEmbedHtml,
            sourceUrl = "https://www.instagram.com/reel/Embed123/"
        )
        assertNotNull(embedManifest)
        assertEquals("Instagram", embedManifest!!.platformName)
        assertEquals("https://scontent.cdninstagram.com/v/embed_reel.mp4?oe=123", embedManifest.variants.first().videoStreamUrl)

        // 6. Facebook HTML HD/SD stream parsing
        val fbHtml = """<html><title>Fb Demo Reel</title><script>{"browser_native_hd_url":"https:\/\/video.xx.fbcdn.net\/v\/fb_1080.mp4?bytestart=0\u0026oe=777"}</script></html>"""
        val fbManifest = SocialMediaExtractors.parseFacebookHtmlToManifest(fbHtml, "https://www.facebook.com/reel/12345")
        assertNotNull(fbManifest)
        assertEquals("Facebook", fbManifest!!.platformName)
        assertEquals("https://video.xx.fbcdn.net/v/fb_1080.mp4?oe=777", fbManifest.variants.first().videoStreamUrl)
    }

    @Test
    fun `verify PhotoAiStudioEngine high-demand and App Check error classifiers plus AiKeyVaultStore encryption`() {
        val highDemandEx = IllegalStateException("HTTP 503: This model is currently experiencing high demand. Spikes in demand are usually temporary.")
        assertTrue(PhotoAiStudioEngine.isHighDemandOrTransientError(highDemandEx))
        assertFalse(PhotoAiStudioEngine.isAppCheckOrAuthError(highDemandEx))

        val appCheckEx = IllegalStateException("HTTP 403: Firebase App Check token is invalid.")
        assertTrue(PhotoAiStudioEngine.isAppCheckOrAuthError(appCheckEx))
        val (friendlyMsg, showKeyShortcut) = PhotoAiStudioEngine.formatFriendlyAiError(appCheckEx, hasPersonalKey = false)
        assertTrue(showKeyShortcut)
        assertTrue(friendlyMsg.contains("API key", ignoreCase = true))

        val (personalKeyMsg, showUpdateKey) = PhotoAiStudioEngine.formatFriendlyAiError(appCheckEx, hasPersonalKey = true)
        assertTrue(showUpdateKey)
        assertTrue(personalKeyMsg.contains("Tools", ignoreCase = true))

        val tempKeyDir = Files.createTempDirectory("luma_ai_key_test").toFile()
        try {
            val keyStore = AiKeyVaultStore(context = null, customStoreDir = tempKeyDir)
            assertFalse(keyStore.hasApiKey())
            val sampleKey = "AIzaSyTestExampleKey9876543210"
            keyStore.saveApiKey(sampleKey)
            assertTrue(keyStore.hasApiKey())
            assertEquals(sampleKey, keyStore.getApiKey())
            assertEquals("AIza••••••••3210", keyStore.getMaskedPreview())

            val rawEncrypted = File(tempKeyDir, ".luma_ai_key.enc").readText(Charsets.UTF_8)
            assertFalse("Stored file must be encrypted, never plaintext", rawEncrypted.contains(sampleKey))

            keyStore.clearApiKey()
            assertFalse(keyStore.hasApiKey())
            assertEquals("", keyStore.getApiKey())
        } finally {
            tempKeyDir.deleteRecursively()
        }
    }
}
