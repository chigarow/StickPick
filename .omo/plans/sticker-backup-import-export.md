# Implementation Plan: Sticker Backup Export & Import (.sbspk)

## Overview & Goals
Provide a production-ready, fully robust backup export and import system for StickPick sticker packs on Android using Storage Access Framework (SAF), ZIP container format (`.sbspk`), SHA-256 deduplication, atomic index writes, and intelligent WhatsApp-compliant pack merging/overwriting rules.

---

## 1. Architecture & Container Specification (.sbspk)

### Container Layout
A `.sbspk` archive is a standard ZIP archive containing:
```
manifest.json
packs/
  <url_safe_pack_id>/
    pack.json
    tray.png
    stickers/
      <sha256_hash>.webp
```

### Portable Pack DTO
`pack.json` contains a portable representation where file paths are stored as relative file names (e.g. `imageFileName: "<sha256_hash>.webp"`) rather than absolute device paths. During import, `convertedFilePath` and `rawFilePath` are dynamically rebased to the target device's `context.filesDir/stickers/converted/<packId>/...`.

### Security & Zip-Slip Protection
- Path normalization: `entry.name` MUST be normalized by replacing backslashes with forward slashes (`val normalizedName = entry.name.replace('\\', '/')`).
- All Zip entry paths MUST be validated during extraction using strict canonical path check:
  `val destinationFile = File(destinationDir, normalizedName)`
  `require(destinationFile.canonicalPath.startsWith(destinationDir.canonicalPath + File.separator))`
- Entry paths MUST be relative and MUST NOT contain `..` or leading `/`.
- Enforce byte-counting `InputStream` wrapping during decompression:
  - Max entry size: 1 MB per sticker WebP, 5 MB for JSON/PNG files. Abort with `ZipSecurityException` if exceeded.
  - Max total uncompressed archive size: 100 MB. Abort if exceeded.
- Unmanifested files protection: Staging extraction fails immediately with `SecurityException("Unmanifested entry in archive: $normalizedName")` if any file in the zip is not listed in `manifest.json` (excluding `manifest.json` itself).

### Manifest Schema (`manifest.json`)
`manifest.json` MUST contain SHA-256 checksum entries for **ALL** files inside the archive (including `pack.json`, `tray.png`, and all sticker `.webp` images):
```json
{
  "version": 1,
  "createdAt": 1770912000000,
  "appVersion": "1.0.0",
  "packCount": 2,
  "files": [
    {
      "path": "packs/pack_1/pack.json",
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    },
    {
      "path": "packs/pack_1/tray.png",
      "sha256": "8f4e2...b9"
    },
    {
      "path": "packs/pack_1/stickers/abc12345.webp",
      "sha256": "1a2b3...c4"
    }
  ]
}
```

---

## 2. WhatsApp Constraints & Smart Merge Logic

### Hard WhatsApp Rules
- Sticker count per pack: **Min 3, Max 30**.
- Homogeneity: Static and animated stickers CANNOT be mixed in the same pack.
- Emojis per sticker: Max **3 emoji tags** (Min 1 emoji tag; fallback to `listOf("😀")` if empty or blank).
- Tray image: 96x96 PNG/WebP, max 50KB.
- Sticker image: 512x512 PNG/WebP, max 100KB (static) or 500KB (animated).

### Smart Merge Algorithm (Default Action)
1. **Deduplication by Image Content (SHA-256)**:
   - Calculate `sha256:` hash of sticker WebP bytes.
   - If imported sticker image matches existing sticker image in the target pack:
     - Combine emoji tags: `(existing.emojis + imported.emojis).filter { it.isNotBlank() }.distinct().take(3).ifEmpty { listOf("😀") }`.
     - Keep existing sticker ID/file, do NOT duplicate image storage.
2. **Appending New Stickers**:
   - New unseen stickers are appended to target pack.
3. **Capacity Re-Balancing & Continuation Packs (Overflow Handling)**:
   - If total stickers (existing + new unseen) > 30:
     - Stickers are re-balanced across main and continuation packs to ensure **EVERY pack has between 3 and 30 stickers**.
     - Example: 31 total stickers split into 28 in main pack and 3 in continuation pack; 32 total stickers split into 29 in main pack and 3 in continuation pack. Never leave a continuation pack with fewer than 3 stickers.
     - Dynamic continuation ID generation: inspect existing packs for `<base_id>_c\d+` to find the next available index `_cN`.
     - Continuation pack name: `<Original Name> (continued N)`.
     - Continuation pack tray image: copied from base pack (`tray_<new_id>.png`).
4. **Mixed Static/Animated Splitting & Min 3 Count Scoping**:
   - If an imported pack contains mixed static and animated stickers, split into:
     - `<Name>` (static)
     - `<Name> (Animated)`
   - Tray image for split animated pack copied from base pack (`tray_<new_id>.png`).
   - Min 3 Count Validation: `StickerMergeEngine.analyzeImport(...)` flags **ANY** pack (whether standalone imported or split subgroup) with fewer than 3 stickers as an invalid pack warning in `ImportPreviewState`.

### Overwrite Mode (Explicit User Choice)
- Completely replaces existing pack metadata and stickers with imported pack contents.
- Performed atomically via Copy-On-Write (staging directory).

---

## 3. Storage Layer Hardening & Copy-on-Write Staging

### Mutex Serialization
- Single global `Mutex` inside `PackStorage` for write operations.

### Transactional Copy-On-Write Staging & Directory Commit Mechanics
- All import extractions MUST write to a temporary staging folder (`cacheDir/import_staging/<session_id>/`).
- Files are fully extracted, validated, SHA-256 verified, and new index prepared in staging.
- **Directory Commit Mechanics (Avoiding DirectoryNotEmptyException)**:
  - For **Overwrite Mode**: Delete existing pack directory (`files/stickers/converted/<packId>`) recursively first, then move staged pack directory using `Files.move(stagedPackDir, targetPackDir, StandardCopyOption.REPLACE_EXISTING)`.
  - For **Merge Mode**: Copy/move individual new sticker `.webp` files into existing `files/stickers/converted/<packId>/stickers/` folder and update `tray.png` if updated, rather than attempting top-level directory `Files.move`.
- On success: staged assets are safely merged/moved into target directories and atomic index swap executed.
- On failure: staging directory is completely deleted, leaving existing storage untouched.

### Atomic Index Swap & WhatsApp Notification
- Write index data to `packs_index.json.tmp`.
- Flush bytes to disk using `FileChannel.force(true)` (fsync).
- Perform `Files.move(tmpPath, indexPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)` (fallback `REPLACE_EXISTING`).
- Notify WhatsApp ContentResolver immediately post-import:
  `context.contentResolver.notifyChange(Uri.parse("content://${StickerContentProvider.AUTHORITY}/metadata"), null)`

---

## 4. UI & ViewModel Architecture (`MainViewModel.kt` & `SettingsScreen.kt`)

### Two-Stage Import Workflow
1. **Stage 1 (Pre-Import Inspection)**:
   - User picks `.sbspk` file via SAF `OpenDocument`.
   - `MainViewModel.inspectBackup(uri)` reads archive manifest/metadata via `InputStream` in background coroutine.
   - `StickerMergeEngine.analyzeImport(...)` produces an `ImportPreviewState`:
     - List of imported packs and titles.
     - Per-pack merge vs overwrite options.
     - Calculated warnings (overflow continuation pack creation, subgroup split alerts, < 3 sticker pack warnings).
2. **Stage 2 (User Batch Review & Confirmation)**:
   - Displays `Import Review Dialog` with calculated summary and options ("Merge All", "Overwrite All", per-pack toggles).
3. **Stage 3 (Atomic Execution & Feedback)**:
   - User confirms -> `MainViewModel.confirmImport(...)` runs transactional staging import.
   - Modal progress indicator shown.
   - On completion: trigger `ContentResolver` notification and display summary Snackbar.

---

## 5. Verification & Test Plan (TDD)

### Automated Unit & Integration Tests (Robolectric)
- `PackStorageTest`: Mutex concurrency, atomic index swap, corrupted index recovery, WhatsApp notification trigger, Overwrite/Merge directory move safety (`DirectoryNotEmptyException` prevention).
- `StickerArchiveManagerTest`: Zip export/import roundtrip using SAF `InputStream`/`OutputStream`, Zip-Slip attack prevention with path normalization (`\\` to `/`) and `File.separator`, byte-counting decompression limit checks (Zip Bomb protection), SHA-256 manifest verification for all files, unmanifested entry rejection, portable path rebasing.
- `StickerMergeEngineTest`:
  - Deduplication of identical SHA-256 stickers and emoji union (`filterNot { it.isBlank() }` + `ifEmpty { listOf("😀") }`).
  - 30-sticker capacity cap re-balancing (e.g. 31 split into 28 + 3, ensuring min 3 per pack).
  - Continuation pack ID resolution (`_c2`, `_c3`) and tray image copying.
  - Mixed static/animated splitting logic and <3 subgroup/standalone warning generation in `ImportPreviewState`.
- `MainViewModelTest`: Pre-import inspection `ImportPreviewState`, batch review confirmation, progress states, export launcher triggers.

### Gradle & Build Config Updates
- `app/build.gradle.kts`:
  - Add `testOptions { unitTests.isIncludeAndroidResources = true }`.
- `gradle/libs.versions.toml`:
  - Add `robolectric = "4.14.1"` and `kotlinx-coroutines-test = "1.9.0"`.

---

## Proposed File Modifications & Additions

### [NEW] `app/src/main/java/com/avishkar/stickpick/data/backup/StickerArchiveManager.kt`
Zip container SAF stream reader/writer, manifest JSON validator, strict Zip-Slip safety guard with path normalization and `File.separator`, byte-counting streams, unmanifested entry safety check, portable DTO converter, SHA-256 calculator.

### [NEW] `app/src/main/java/com/avishkar/stickpick/data/backup/StickerMergeEngine.kt`
Pre-import inspection analyzer (`analyzeImport`), deduplication, capacity re-balancing (min 3 / max 30), continuation pack ID resolver & tray generator, mixed static/animated separator, < 3 sticker pack warning generator.

### [MODIFY] `app/src/main/java/com/avishkar/stickpick/data/local/PackStorage.kt`
Atomic file swap, mutex locking, copy-on-write staging directory operations with target directory purge/merge logic (avoiding `DirectoryNotEmptyException`), and WhatsApp `ContentResolver.notifyChange` trigger.

### [MODIFY] `app/src/main/java/com/avishkar/stickpick/viewmodel/MainViewModel.kt`
Add backup/import state flows (`ImportPreviewState`), SAF Uri handlers (`exportBackup`, `inspectBackup`, `confirmImport`), and coroutine dispatch.

### [MODIFY] `app/src/main/java/com/avishkar/stickpick/ui/screens/SettingsScreen.kt`
Add Backup & Restore card, SAF launchers, two-stage import preview review modal dialog, and progress dialogs.

### [MODIFY] `app/build.gradle.kts` & `gradle/libs.versions.toml`
Add Robolectric 4.14.1, kotlinx-coroutines-test, and set `isIncludeAndroidResources = true`.

---

## Verification Plan

### Automated Tests
- `sh run_tests.sh` -> `./gradlew test` (Target 100% pass, 10 consecutive clean passes)

### Production Build
- `sh build_apk.sh` -> `./gradlew assembleDebug`
- `sh calc_checksum.sh` -> `sha256sum app/build/outputs/apk/debug/app-debug.apk`
