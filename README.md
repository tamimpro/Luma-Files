# File Manager (`Luma Files` V2)

[![Android CI - File Manager (Luma Files v2.0.0)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml)

**File Manager (Luma Files)** (`com.lumafiles.filemanager`) is a modern Android file manager, full-screen media & document suite, AES-256 encrypted Secure Vault, and multi-platform download manager built with **Kotlin**, **Jetpack Compose**, and **Dynamic Material You**.

---

## Download the APK from GitHub

Every push to `main` or `master` runs automated unit tests, builds the APK, and publishes it to **GitHub Releases** and **GitHub Actions**:

1. **GitHub Releases (Fastest on Phone)** — Open **Releases** on the repository page and download **`LumaFiles-v2.0.0.apk`** from the latest `v2.0.x` release.
2. **GitHub Actions Artifacts** — Open the **Actions** tab, select the latest successful run, and download **`LumaFiles-v2.0.0-APK`**.

---

## What's New in V2 (`v2.0.0`)

### 1. Dynamic Material You & Clean Explorer
- Adapts automatically to your Android 12+ system wallpaper colors with crisp typography and minimal UI text.
- Clean single-column file list with color-coded badges, compact storage bar, volume pills (`Internal`, `SD`, `SAF`, `Trash`), quick category filters, floating multi-select bar, Trash/Shred prompt, and one-tap Undo.

### 2. Full-Screen Photo Studio & AI Image Enhance
- Edge-to-edge zoomable, pannable photo viewer with sibling gallery navigation, Rename, Share, Delete, Lock to Vault, Rotate, and EXIF info.
- **AI Auto Enhance & AI Remaster**: Powered by Firebase AI Logic to automatically balance exposure, dynamic range, contrast, and skin tones or apply studio portrait and cinema remasters.
- **18 Cinematic & Beauty Filters**: Live previews with intensity slider, manual adjustments (Brightness, Contrast, Saturation, Warmth, Clarity, Skin Smooth, Vignette), Flip H/V, Aspect Crop, Hold-to-Compare, and Save Copy / Overwrite.

### 3. Full-Screen Video Theater
- True edge-to-edge full-screen playback hiding system bars with vertical drag Brightness (left) and Volume (right) HUDs, double-tap `±10s` seek, Screen Lock, Landscape/Portrait toggle, Aspect Ratio (`Fit`, `Fill`, `16:9`), Playback Speed (`0.5×–2.0×`), Loop, Mute, and Frame Capture.

### 4. Full-Screen Audio Player with 18 Live Visualizers
- Opens full-screen with a randomly chosen procedural 60fps audio VFX visualizer on every track (18 distinct styles including Neon Bars, Sunburst, Aurora, Mirror Wave, Star Warp, Cyber Grid, Plasma Rings, DNA Helix, Particle Fountain, Hex Matrix, Ember EQ, Mercury Orb, Laser Tunnel, Kaleidoscope, Silk Ribbon, Sonar Pulse, Pulse Rain, and Nebula), plus Auto-Cycle mode.
- Includes a 5-band Equalizer with presets and Bass Boost, Sleep Timer, Playback Speed, Shuffle/Repeat, and sibling track navigation.

### 5. Modern Viewers for Every File Format
- **PDF**: Continuous vertical multi-page reader with pinch-to-zoom, `Day / Sepia / Night` themes, page scrubber, and companion notes.
- **DOCX**: Paper Reader mode (`Day / Sepia / Night`, font sizing, search, reading time) + lossless W3C DOM editor.
- **Code / Text / Markdown**: Syntax highlighting, rendered Markdown view, line numbers, word-wrap, Find & Replace, and JSON/XML formatting.
- **CSV / TSV**: Frozen sticky header row, column sorting, live row filter, numeric column stats (`Count / Sum / Avg / Min / Max`), and inline editing.
- **ZIP**: Interactive archive browser, in-memory single-file preview (text/code/images without extracting), single-entry extract, Extract All, and Zip-Slip / Zip-Bomb protection.
- **SQLite, APK & Hex**: Interactive SQLite table/query runner, APK inspector with app icon, risk-highlighted permissions and one-tap Install, and Hex byte viewer/patcher.

### 6. Multi-Platform Download Manager + Built-In Browser Sniffer
- Direct multi-threaded resumable downloads for any internet file with RFC-5987 filename decoding and persistent `.part` checkpoints.
- Native stream extractors for YouTube, TikTok (watermark-free HD), Reddit, HLS `.m3u8` streams, and direct media pages with lossless `MediaExtractor` + `MediaMuxer` audio/video merging.
- Built-in **Browser** tab with live network & DOM media sniffing and automatic Cookie/Referer handoff for one-tap capture from social sites and web players.

### 7. Seamless Zero-Trace In-Vault Viewing
- AES-256-GCM encrypted Secure Vault with a wrapped 256-bit Master DEK (210,000-iteration PBKDF2-HMAC-SHA256) so recovery never loses encrypted files.
- Once unlocked with PIN or Password, tap any file in the Vault to view or edit it directly inside the full-screen V2 viewers—temporary decrypted previews are isolated in `no_backup` storage and automatically zero-overwritten when closed, locked, or backgrounded.

---

## Build & Test Locally

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
