# File Manager (`Luma Files`)

**File Manager** (branded in-app as **Luma Files**) is a high-performance, security-first Android file manager, lossless multi-format studio, AES-256-GCM PIN-protected vault, and multi-threaded download manager built with Kotlin, Jetpack Compose (Material 3), and Room.

---

## Key Capabilities

### 1. Maximum Non-Root Android Storage Access
- **Android 11+ All Files Access (`MANAGE_EXTERNAL_STORAGE`)**: Direct one-tap system settings integration to unlock full shared storage read/write access on non-rooted devices.
- **Storage Access Framework (`OpenDocumentTree`)**: Persisted URI permissions for mounting external directories, SD cards, and scoped folders.
- **Pre-Seeded Luma Studio Workspace**: Ready-to-use multi-format workspace initialized on first launch with real `.md`, `.txt`, `.kt`, `.json`, `.docx`, `.pdf`, `.zip`, `.wav`, `.png`, and hidden dotfile samples.

### 2. Open-With Permission & Routing Prompt
- Every file tap triggers an explicit **Open File Permission & Routing Modal**, letting the user choose between:
  - **Luma Files Built-In Lossless Studio** (100% original byte quality preserved)
  - **External Installed Android App** via `FileProvider` and `Intent.createChooser`
  - **Raw Hex & Checksum Studio** (`MD5` / `SHA-256` + byte patching)
  - **One-Tap AES-256 Secure Vault Encryption**

### 3. Built-In Lossless Multi-Format Studio (View & Edit)
- **Text, Markdown & Code (`txt`, `md`, `kt`, `java`, `py`, `json`, `xml`, `html`, `csv`, `log`)**: Line-numbered code editor with live Markdown rendering preview, Find & Replace, encoding support (`UTF-8` / `ISO-8859-1`), 512 KB chunked streaming window, and atomic temporary-file swap on save.
- **Word OOXML (`docx`) Studio**: Parses and edits `<w:p>` paragraphs directly inside `word/document.xml` and repackages the `.docx` archive without stripping styles, relationships, or embedded media.
- **Native High-DPI PDF Studio (`pdf`)**: Renders pages natively via Android's `PdfRenderer` with page navigation and companion annotation notes.
- **ZIP Archive Manager (`zip`, `jar`, `apk`)**: Streams archive entries with uncompressed/compressed byte sizes, compression ratios, and `CRC-32` checksums; supports full extraction (with Zip-Slip traversal protection) and packing new entries into existing archives.
- **Lossless Audio & Video Studio (`wav`, `mp3`, `flac`, `mp4`, `mkv`)**: Native hardware playback with RIFF/WAVE PCM header telemetry and bitrate/resolution inspection.
- **High-Res Image Studio (`png`, `jpg`, `webp`)**: 32-bit `ARGB_8888` lossless decoding with resolution, megapixel, and EXIF attribute inspection.
- **Paginated Raw Hex & ASCII Binary Studio (`*/*`)**: 2 KB sliding byte window via `RandomAccessFile` with live `MD5` & `SHA-256` hashes and single-byte hex patching (`0x00`–`0xFF`).

### 4. AES-256-GCM PIN-Authenticated Secure Vault
- Configurable **4-digit** or **6-digit numeric PIN** with a security question recovery flow.
- Derives a 256-bit key using `PBKDF2WithHmacSHA256` (12,000 iterations, 128-bit random salt) and encrypts files via `AES/GCM/NoPadding` (96-bit random IV, 128-bit authentication tag).
- Verifies bit-exact restoration using `SHA-256` checksum comparison upon decryption.

### 5. Super-Fast Multi-Threaded Download Manager
- Configurable **1x, 2x, or 4x parallel HTTP `Range` segment workers** (`OkHttp`) with live transfer speed (`KB/s` and `MB/s`), Pause/Resume support, automatic `SHA-256` verification, and an offline-capable **8 MB Synthetic Multi-Stream Benchmark**.

### 6. Dual-Posture UI: User-Friendly Default & Power-User Advanced Mode
- **Default Mode**: Clean adaptive Dark (`#0B0F17` Obsidian / `#00E5FF` Electric Cyan) and Light (`#F8FAFC` Alabaster / `#0284C7` Deep Cobalt) themes with storage telemetry cards and format studio shortcuts.
- **Advanced Mode**: Unlocks hidden dotfiles (`.luma_hidden_config`, `.nomedia`), inline POSIX `rwx` permission flags, Regex file filtering, **Batch Regex Rename**, **SHA-256 Duplicate & Large File Storage Analyzer**, and a **Real-Time Diagnostic & Operation Audit Trail** that reports exact technical root causes for any I/O, permission, network, or cryptographic error.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity & LumaFilesApp                         │
│     (Edge-to-Edge Scaffold, Adaptive Nav, Open-With Chooser Launcher)      │
└──────────────────┬──────────────────┬──────────────────┬───────────────────┘
                   │                  │                  │
         ┌─────────▼────────┐ ┌───────▼────────┐ ┌───────▼──────────┐
         │ Explorer &       │ │ Secure Vault & │ │ Multi-Threaded   │
         │ Studio UI        │ │ PIN Auth UI    │ │ Download UI      │
         │ (Categories,     │ │ (4/6-Digit Pad,│ │ (URL Queue,      │
         │  Breadcrumbs,    │ │  Vault List,   │ │  Thread Gauges,  │
         │  Format Editors, │ │  Decrypt/Open) │ │  Pause/Resume)   │
         │  Advanced Tools) │ └───────┬────────┘ └───────┬──────────┘
         └─────────┬────────┘         │                  │
                   └──────────────────┼──────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          LumaFilesViewModel (MVVM)                         │
│   StateFlows: ExplorerState | StudioState | VaultState | DownloadState     │
│               AdvancedModeState | DiagnosticErrorState                     │
└─────────┬───────────────────────────┬──────────────────────────┬───────────┘
          │                           │                          │
┌─────────▼─────────────┐   ┌─────────▼─────────────┐  ┌─────────▼───────────┐
│ FileSystem & Format   │   │ CryptoVaultEngine     │  │ FastDownloadEngine  │
│ Engine                │   │ (PBKDF2 Key Derive,   │  │ (OkHttp Range       │
│ (Direct File + SAF,   │   │  AES-256-GCM Stream   │  │  Chunk Workers,     │
│  TXT/MD/Code/DOCX/    │   │  Encrypt/Decrypt,     │  │  Speed Telemetry,   │
│  PDF/ZIP/Media/Hex,   │   │  SHA-256/MD5 Hasher)  │  │  Exact Error Map)   │
│  Regex Batch & Dupes) │   └─────────┬─────────────┘  └─────────┬───────────┘
└─────────┬─────────────┘             │                          │
          └───────────────────────────┼──────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   Room Database (LumaDatabase) & FileProvider              │
│   Entities: VaultItemEntity | DownloadTaskEntity | BookmarkEntity          │
│             OperationLogEntity (Exact Error Diagnostics & Audit Trail)     │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3, Edge-to-Edge, Adaptive Dark/Light Themes)
- **Typography**: Bundled Google Fonts (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`)
- **Architecture**: MVVM (`AndroidViewModel`, `StateFlow`, Repository pattern)
- **Local Persistence**: Room Database with KSP (`VaultItemEntity`, `DownloadTaskEntity`, `BookmarkEntity`, `OperationLogEntity`)
- **Cryptography**: `javax.crypto.Cipher` (`AES/GCM/NoPadding`), `PBKDF2WithHmacSHA256`, `MessageDigest` (`SHA-256`, `MD5`)
- **Networking**: `OkHttp3` with parallel `Range` byte-stream workers
- **Testing**: JUnit 4 + Robolectric JVM tests

---

## Building & Testing Locally

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 36)

### Build Debug APK
```bash
./gradlew :app:assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Tests
```bash
./gradlew :app:testDebugUnitTest
```

---

## GitHub Actions CI/CD Workflow

This repository includes an automated GitHub Actions workflow at `.github/workflows/android-ci.yml` that:
1. Sets up JDK 21 and the Android Gradle cache.
2. Runs unit & Robolectric verification tests (`:app:testDebugUnitTest`).
3. Builds the debug APK (`:app:assembleDebug`).
4. Uploads the resulting `LumaFiles-FileManager-debug-apk` artifact on every push, pull request, or manual dispatch.
