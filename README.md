# File Manager (`Luma Files` v1.1.0)

[![Android CI - File Manager (Luma Files v1.1.0)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/android-ci.yml)

**File Manager** (internally branded as **Luma Files**) is a lossless Android file workstation, multi-format studio editor, isolated **AES-256-GCM Safe Folder Vault**, and verified multi-threaded HTTP Range download engine built with **Kotlin** and **Jetpack Compose (Material 3)**.

---

## How to Download the Built `.apk` from GitHub

Every push to `main` or `master` automatically builds and publishes the installable Android `.apk` in **two places**:

### Option 1: Direct APK Download from **GitHub Releases** (Easiest on Phone)
1. Open your GitHub repository page.
2. Tap **Releases** (on the right sidebar on desktop, or under the repository info header on mobile).
3. Under the latest **`v1.1.x`** release, tap **`LumaFiles-FileManager-v1.1.0.apk`** under **Assets** to download and install the `.apk` directly on your Android device.

### Option 2: Download from **GitHub Actions** Workflow Summary
1. Open the **Actions** tab in your GitHub repository.
2. Tap the latest green workflow run (**Build APK, Run Engine Tests & Publish Release**).
3. Scroll to the **Artifacts** section at the bottom and download **`LumaFiles-FileManager-v1.1.0-APK`**.

> **Troubleshooting GitHub Release Permissions**: If the `Publish Direct APK to GitHub Releases` step ever reports a `403` permission error on a new repository, go to your GitHub repo **Settings -> Actions -> General -> Workflow permissions**, select **Read and write permissions**, and save.

---

## What's New in v1.1.0

### 1. Default Internal Storage Root & Remembered Permission Onboarding
- **Internal Shared Storage Default**: Opens directly into **Internal Shared Storage (`/storage/emulated/0`)** by default whenever storage access is granted, with instant volume chips for **Internal Storage**, **Removable SD Cards**, **SAF Mounts**, and the **Luma Sample Studio**.
- **One-Time Permission Setup**: On first launch, a clean **Storage & Permission Setup** modal asks once for All Files Access or SAF mounting and remembers your decision so access control banners don't clutter the interface on every launch.

### 2. Cross-Volume Copy, Move & Batch Operations Engine
- **Floating Multi-Select & Clipboard Action Dock**: Select multiple files and directories across **Internal Shared Storage**, **Removable SD Cards**, and **Storage Access Framework (`content://` SAF)** trees.
- **Batch Actions**:
  - **Copy & Move (Cut)** with recursive directory support and cross-volume stream fallback when moving across physical storage boundaries.
  - **Conflict Resolution Policies**: Choose **Keep Both (Auto-Rename)**, **Overwrite**, or **Skip** directly from the floating paste dock.
  - **Batch Compress to `.zip`** and **Batch Lock to AES-256 Safe Vault**.

### 3. Hardened AES-256-GCM Safe Folder Vault (Zero External Visibility)
- **210,000-Iteration PBKDF2-HMAC-SHA256**: Supports **4-Digit PIN**, **6-Digit High-Entropy PIN**, or **Alphanumeric Master Password** derived with **210,000 iterations** and a **256-bit (32-byte) cryptographic salt**.
- **Complete Isolation from External File Managers**:
  - Encrypted files are stored inside `context.noBackupFilesDir/.luma_vault_v11/` with a `.nomedia` sentinel and **opaque randomized UUID filenames (`<uuid>.lvault`)** so no filename or content is ever visible to external file managers or media scanners.
  - Original source files are **zero-overwritten (`shredAndDeleteFile`)** and flushed via `FileDescriptor.sync()` before unlinking.
  - Explicit cloud and device-transfer exclusions are enforced in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.

### 4. W3C DOM `.docx` Editing & Power-User Built-In Lossless Studios
- **W3C DOM Word `.docx` Editor**: Parses `word/document.xml` into a full W3C DOM tree and mutates `<w:t>` text runs in-place while preserving `<w:pPr>`, `<w:pStyle>`, `<w:rPr>` formatting runs, `word/styles.xml`, tables, and relationships.
- **SQLite Database Browser (`.db`, `.sqlite`, `.sqlite3`)**: Inspect tables, browse rows in a scrollable data grid, and execute custom SQL queries (`SELECT`, `INSERT`, `UPDATE`).
- **CSV & TSV Interactive Spreadsheet Grid Editor**: Edit cells and column headers inline, add rows, and save RFC-4180 compliant spreadsheets losslessly.
- **APK Package Inspector (`.apk`)**: Inspect package name, version code/name, Min/Target SDK levels, declared `<uses-permission>` entries, activities, `.dex` bytecode count, and `SHA-256` signature digest.
- **JSON / XML / YAML Validator & Pretty-Formatter**: One-tap syntax validation and 2-space pretty indentation inside the Code Studio.
- **PDF Renderer, ZIP Archive Manager, Lossless Media Player & Raw Hex Byte Patcher**.

### 5. Verified Multi-Threaded HTTP Range Download Engine
- **Strict `Content-Range` Validation**: Validates HTTP `206 Partial Content` and `Content-Range: bytes <start>-<end>/<total>` headers before writing to `RandomAccessFile` offsets, automatically falling back to a single-stream download if a server returns `200 OK`.
- **Post-Download Integrity Verification**: Verifies exact byte length and computes the full file **MD5** and **SHA-256** checksums upon completion.

---

## Running Tests & Building Locally

```bash
# Run automated unit tests for FileSystemAndFormatEngine, CryptoVaultEngine & FastDownloadEngine
gradle :app:testDebugUnitTest

# Build the Debug APK
gradle :app:assembleDebug
```
