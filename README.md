# File Manager (`Luma Files` v1.2.0)

[![Android CI - File Manager (Luma Files v1.2.0)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml)

**File Manager** (`com.lumafiles.filemanager`, branded as **Luma Files**) is a professional Android file workstation, View-First multi-format studio editor, **Wrapped 256-Bit Master DEK AES-256-GCM Safe Vault**, Recycle Bin & Undo manager, and persistent multi-threaded stream & file downloader built with **Kotlin** and **Jetpack Compose (Material 3)**.

---

## How to Download the Built `.apk` from GitHub

Every push to `main` or `master` automatically builds and publishes the installable Android `.apk` in **two places**:

### Option 1: Direct APK Download from **GitHub Releases** (Easiest on Phone)
1. Open your GitHub repository page.
2. Tap **Releases** (on the right sidebar on desktop, or under the repository info header on mobile).
3. Under the latest **`v1.2.x`** release, tap **`LumaFiles-FileManager-v1.2.0.apk`** under **Assets** to download and install the `.apk` directly on your Android device.

### Option 2: Download from **GitHub Actions** Workflow Summary
1. Open the **Actions** tab in your GitHub repository.
2. Tap the latest green workflow run (**Build APK, Run Engine Tests & Publish Release**).
3. Scroll to the **Artifacts** section at the bottom and download **`LumaFiles-FileManager-v1.2.0-APK`**.

---

## What's New in v1.2.0

### 1. ZIP Extraction & ZIP Creation Security Hardening
- **Canonical `Zip-Slip` & `Zip-Bomb` Protection**: Rejects any archive entry containing `..`, leading `/` or `\`, or resolving outside `destDir.canonicalPath + File.separator`, and enforces strict entry count (`50,000`), total size (`2 GB`), and compression-ratio (`100:1`) limits.
- **ZIP Creation Self-Inclusion & Cycle Protection**: Automatically excludes the output `.zip` file and its `.tmp` staging file when creating an archive inside the source directory, tracks active canonical directory branches to prevent symlink loops, and commits atomically.

### 2. Recursive Copy/Move Safety, Atomic Overwrite & Unreadable Directory Guarantees
- **Self/Descendant Guard**: Prevents copying or moving any directory into itself or any of its descendants.
- **Verified Move Deletion**: Confirms `!sourceFile.exists()` before reporting a successful move or unlinking source trees.
- **Atomic Temporary File Replacement**: Writes overwrites to a sibling `.luma_atomic_<uuid>.tmp` file with `FileDescriptor.sync()` before atomically replacing the target file.
- **Strict Unreadable Directory Handling**: Never silently treats an unreadable directory (`!dir.canRead()` or `dir.listFiles() == null`) as an empty directory—throws an explicit `IOException` with root-cause diagnostics.

### 3. Wrapped 256-Bit Master DEK Vault & Lossless Password Recovery
- **Envelope Key-Wrapping Architecture**: A cryptographically random **256-bit Master Data Encryption Key (DEK)** derives per-file subkeys and is independently wrapped (AES-256-GCM) by `PrimaryKEK` (210,000-iteration PBKDF2-HMAC-SHA256 from your PIN/Password) and `RecoveryKEK` (210,000-iteration PBKDF2-HMAC-SHA256 from your Recovery Answer).
- **Zero-Data-Loss Password Reset**: Resetting a forgotten PIN or password with your Recovery Answer unwraps the 256-bit Master DEK and re-wraps it under your new credential—unlocking 100% of previously encrypted `.lvault` files without data loss.

### 4. Recycle Bin & One-Tap Undo System
- **Delete Choice Prompt**: Every delete prompts you to choose between **Move to Recycle Bin (Undoable)** or **Permanent Zero-Overwrite Shred**.
- **One-Tap Undo Banner**: Instantly undo Recycle Bin moves, Copy, Move, Rename, and File/Folder creation operations.

### 5. Persistent Resumable Downloader & Lossless Audio+Video Stream Merger
- **Genuine HTTP `Range` + `If-Range` Resume & Persistent Checkpoints**: Persists per-segment `.segN.part` byte offsets and `.luma_resume.tsv` metadata so paused or interrupted downloads resume from the exact byte offset on app launch.
- **Multi-Platform Highest-Quality Stream Resolution**: Resolves direct links and social video streams (YouTube, TikTok, Instagram, Facebook, X/Twitter, Vimeo), automatically selecting the highest original watermark-free resolution and best audio stream, and losslessly muxing separate video+audio tracks via Android's `MediaExtractor` + `MediaMuxer` while preserving creator attribution.
- **Default vs. Advanced Mode**: Default Mode handles format and quality selection automatically in one tap; Advanced Mode unlocks manual stream/resolution/codec/bitrate/audio selection, 1–8 thread tuning, and SHA-256 verification.

### 6. View-First Studio Opening & Clean Production UI
- **View Mode First**: All built-in studios (`Code/Markdown`, `Word .docx`, `CSV/TSV Grid`, `PDF`, `ZIP`, `Hex`) open in safe read-only **View Mode** first with an explicit **Edit** toggle in the top toolbar.
- **Production Application ID**: Updated to `com.lumafiles.filemanager` with zero demo files or overlapping text.

---

## Running Tests & Building Locally

```bash
# Run automated unit tests for FileSystemAndFormatEngine, CryptoVaultEngine & FastDownloadEngine
gradle :app:testDebugUnitTest

# Build the Debug APK
gradle :app:assembleDebug
```
