package com.avishkar.stickpick.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avishkar.stickpick.conversion.ConversionEngine
import com.avishkar.stickpick.data.model.*
import com.avishkar.stickpick.data.repository.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StickerRepository(app)
    private val converter = ConversionEngine(app)

    val botToken = repo.prefs.botToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val authorName = repo.prefs.authorName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val packNamingPattern = repo.prefs.packNamingPattern.stateIn(viewModelScope, SharingStarted.Eagerly, "{name}_by_{author}")
    val onboardingComplete = repo.prefs.onboardingComplete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    val autoSplit = repo.prefs.autoSplit.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val losslessConversion = repo.prefs.losslessConversion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val packLimit = repo.prefs.packLimit.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    val themeMode = repo.prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val whatsappBusiness = repo.prefs.whatsappBusiness.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stickerSet = MutableStateFlow<TelegramStickerSet?>(null)
    val stickerSet: StateFlow<TelegramStickerSet?> = _stickerSet

    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFiles: StateFlow<List<File>> = _downloadedFiles

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    private val _conversionProgress = MutableStateFlow(ConversionProgress())
    val conversionProgress: StateFlow<ConversionProgress> = _conversionProgress

    private val _savedPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val savedPacks: StateFlow<List<StickerPack>> = _savedPacks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentPackId = MutableStateFlow("")
    private var isDownloading = false
    private var isConverting = false

    init {
        loadSavedPacks()
        viewModelScope.launch {
            repo.prefs.onboardingComplete.first()
            _isReady.value = true
        }
    }

    fun completeOnboarding(token: String, author: String, pattern: String) {
        viewModelScope.launch {
            repo.prefs.saveBotToken(token)
            repo.prefs.saveAuthorName(author)
            repo.prefs.savePackNamingPattern(pattern)
            repo.prefs.setOnboardingComplete()
        }
    }

    fun updateSettings(token: String, author: String, pattern: String, autoSplit: Boolean, lossless: Boolean) {
        viewModelScope.launch {
            repo.prefs.saveBotToken(token)
            repo.prefs.saveAuthorName(author)
            repo.prefs.savePackNamingPattern(pattern)
            repo.prefs.saveAutoSplit(autoSplit)
            repo.prefs.saveLosslessConversion(lossless)
        }
    }

    fun fetchStickerSet(input: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repo.fetchStickerSet(input).fold(
                onSuccess = { set ->
                    _stickerSet.value = set
                    _currentPackId.value = set.name
                    _isLoading.value = false
                },
                onFailure = { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
            )
        }
    }

    fun downloadAllStickers() {
        if (isDownloading) return
        val set = _stickerSet.value ?: return
        isDownloading = true

        viewModelScope.launch {
            _downloadProgress.value = DownloadProgress(
                packName = set.title,
                totalStickers = set.stickers.size
            )

            val results = arrayOfNulls<File>(set.stickers.size)
            var completed = 0

            set.stickers.forEachIndexed { index, sticker ->
                repo.downloadSticker(sticker, set.name, index).onSuccess { results[index] = it }
                completed++
                _downloadProgress.value = _downloadProgress.value.copy(downloadedStickers = completed)
            }

            _downloadedFiles.value = results.filterNotNull()
            _downloadProgress.value = _downloadProgress.value.copy(isComplete = true)
            isDownloading = false
        }
    }

    fun removeSticker(index: Int) {
        val current = _downloadedFiles.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _downloadedFiles.value = current
        }
    }

    fun addExternalFile(file: File) {
        _downloadedFiles.value = _downloadedFiles.value + file
    }

    fun moveSticker(from: Int, to: Int) {
        val current = _downloadedFiles.value.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            _downloadedFiles.value = current
        }
    }

    fun convertAllStickers() {
        if (isConverting) return
        val files = _downloadedFiles.value
        val packId = _currentPackId.value
        if (files.isEmpty() || packId.isBlank()) return
        isConverting = true

        viewModelScope.launch {
            _conversionProgress.value = ConversionProgress(totalStickers = files.size)
            val results = mutableListOf<Pair<Int, Boolean>>()
            var done = 0

            files.forEachIndexed { index, file ->
                val result = converter.convertSticker(file, packId, "sticker_$index")
                done++
                result.onSuccess { results.add(index to it.isAnimated) }
                _conversionProgress.value = _conversionProgress.value.copy(convertedStickers = done)
            }

            val firstSuccess = results.firstOrNull()?.first
            if (firstSuccess != null) {
                converter.createTrayImage(files[firstSuccess], packId)
            }

            buildAndSavePacks(results)
            _conversionProgress.value = _conversionProgress.value.copy(isComplete = true)
            isConverting = false
        }
    }

    private data class StickerWithMeta(val sticker: Sticker, val isAnimated: Boolean)

    private suspend fun buildAndSavePacks(results: List<Pair<Int, Boolean>>) {
        val set = _stickerSet.value ?: return
        val packId = _currentPackId.value
        val author = authorName.value.ifBlank { "StickPick User" }
        val convertedDir = repo.storage.getConvertedDir(packId)
        val limit = packLimit.value

        val allStickers = results.map { (index, isAnimated) ->
            val file = _downloadedFiles.value[index]
            val emoji = set.stickers.getOrNull(index)?.emoji
            StickerWithMeta(
                Sticker(
                    imageFileName = "sticker_$index.webp",
                    emojis = listOfNotNull(emoji ?: "\uD83D\uDE00"),
                    rawFilePath = file.absolutePath,
                    convertedFilePath = File(convertedDir, "sticker_$index.webp").absolutePath
                ),
                isAnimated
            )
        }

        if (allStickers.size < 3) return

        val validStickers = allStickers.filter { sm ->
            val file = File(sm.sticker.convertedFilePath)
            file.exists() && file.length() > 200
        }

        val animatedStickers = validStickers.filter { it.isAnimated }
        val staticStickers = validStickers.filter { !it.isAnimated }

        var packIndex = 0
        val animLimit = minOf(limit, 30)
        val staticLimit = limit

        if (animatedStickers.size >= 3) {
            val animChunks = animatedStickers.chunked(animLimit)
            animChunks.forEach { chunk ->
                packIndex++
                val suffix = if (animChunks.size + (if (staticStickers.size >= 3) 1 else 0) > 1) "_$packIndex" else ""
                saveSinglePack(chunk, suffix, packId, set.title, author, convertedDir, true)
            }
        }

        if (staticStickers.size >= 3) {
            val staticChunks = staticStickers.chunked(staticLimit)
            staticChunks.forEach { chunk ->
                packIndex++
                val suffix = "_s$packIndex"
                saveSinglePack(chunk, suffix, packId, set.title + " (Static)", author, convertedDir, false)
            }
        }

        if (animatedStickers.size < 3 && staticStickers.size < 3 && validStickers.size >= 3) {
            val chunks = validStickers.chunked(animLimit)
            chunks.forEachIndexed { idx, chunk ->
                val suffix = if (chunks.size > 1) "_${idx + 1}" else ""
                saveSinglePack(chunk, suffix, packId, set.title, author, convertedDir, false)
            }
        }

        loadSavedPacks()
    }

    private suspend fun saveSinglePack(
        chunk: List<StickerWithMeta>,
        suffix: String,
        packId: String,
        name: String,
        author: String,
        convertedDir: File,
        animated: Boolean
    ) {
        val packIdentifier = "${packId}${suffix}"
        val trayFileName = "tray_${packIdentifier}.png"

        val firstRawPath = chunk.first().sticker.rawFilePath
        if (firstRawPath.isNotEmpty()) {
            converter.createTrayImage(File(firstRawPath), packId)
            val genericTray = File(convertedDir, "tray_${packId}.png")
            val specificTray = File(convertedDir, trayFileName)
            if (genericTray.exists() && genericTray.absolutePath != specificTray.absolutePath) {
                genericTray.copyTo(specificTray, overwrite = true)
            }
        }

        val trayFile = File(convertedDir, trayFileName)
        if (!trayFile.exists()) {
            val genericTray = File(convertedDir, "tray_${packId}.png")
            if (genericTray.exists()) genericTray.copyTo(trayFile, overwrite = true)
        }

        if (chunk.size < 3) return

        val pack = StickerPack(
            identifier = packIdentifier,
            name = name,
            publisher = author,
            trayImageFile = trayFileName,
            stickers = chunk.map { it.sticker },
            animatedStickerPack = animated
        )
        repo.storage.savePack(pack)
    }

    fun loadSavedPacks() { _savedPacks.value = repo.storage.loadPacks() }
    fun deletePack(id: String) { repo.storage.deletePack(id); loadSavedPacks() }

    fun updatePackName(id: String, newName: String) {
        val packs = repo.storage.loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == id }
        if (idx >= 0) { packs[idx] = packs[idx].copy(name = newName); repo.storage.savePacks(packs); loadSavedPacks() }
    }

    fun clearHistory() { repo.storage.clearAll(); loadSavedPacks() }
    fun clearError() { _error.value = null }
    fun updatePackLimit(limit: Int) { viewModelScope.launch { repo.prefs.savePackLimit(limit) } }
    fun updateThemeMode(mode: String) { viewModelScope.launch { repo.prefs.saveThemeMode(mode) } }
    fun updateWhatsAppBusiness(enabled: Boolean) { viewModelScope.launch { repo.prefs.saveWhatsAppBusiness(enabled) } }

    // Backup & Import Workflows
    private val archiveManager by lazy { com.avishkar.stickpick.data.backup.StickerArchiveManager(getApplication()) }
    private val mergeEngine by lazy { com.avishkar.stickpick.data.backup.StickerMergeEngine() }

    private val _importPreviewState = MutableStateFlow<com.avishkar.stickpick.data.backup.ImportPreviewState?>(null)
    val importPreviewState: StateFlow<com.avishkar.stickpick.data.backup.ImportPreviewState?> = _importPreviewState

    private val _isBackupProcessing = MutableStateFlow(false)
    val isBackupProcessing: StateFlow<Boolean> = _isBackupProcessing

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackupProcessing.value = true
            try {
                val packs = repo.storage.loadPacks()
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openOutputStream(uri)?.use { os ->
                    archiveManager.exportArchive(packs, os)
                }
                _backupMessage.value = "Backup exported successfully (${packs.size} pack(s))."
            } catch (e: Exception) {
                _backupMessage.value = "Export failed: ${e.message}"
            } finally {
                _isBackupProcessing.value = false
            }
        }
    }

    fun inspectBackup(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackupProcessing.value = true
            try {
                // Delete previous staging directory if active
                _importPreviewState.value?.stagingDir?.deleteRecursively()

                val contentResolver = getApplication<Application>().contentResolver
                val stagingResult = contentResolver.openInputStream(uri)?.use { isStream ->
                    archiveManager.extractArchiveToStaging(isStream)
                }

                if (stagingResult != null) {
                    val existing = repo.storage.loadPacks()
                    val preview = mergeEngine.analyzeImport(stagingResult.packs, existing, stagingResult.stagingDir)
                    _importPreviewState.value = preview
                } else {
                    _backupMessage.value = "Failed to open archive stream."
                }
            } catch (e: Exception) {
                _backupMessage.value = "Failed to parse backup: ${e.message}"
            } finally {
                _isBackupProcessing.value = false
            }
        }
    }

    fun confirmImport(mode: com.avishkar.stickpick.data.backup.ImportMode) {
        val preview = _importPreviewState.value ?: return
        val stagingDir = preview.stagingDir

        viewModelScope.launch(Dispatchers.IO) {
            _isBackupProcessing.value = true
            try {
                val appCtx = getApplication<Application>()
                val existingPacks = repo.storage.loadPacks().toMutableList()

                val hashProvider: (File) -> String = { f ->
                    if (f.exists()) {
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        digest.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
                    } else "missing_${f.canonicalPath}"
                }

                var finalPacksToSave = mutableListOf<StickerPack>()

                if (mode == com.avishkar.stickpick.data.backup.ImportMode.OVERWRITE) {
                    repo.storage.clearAll()
                    for (imported in preview.importedPacks) {
                        val rebalanced = mergeEngine.rebalanceCapacity(imported, hashProvider)
                        finalPacksToSave.addAll(rebalanced)
                    }
                } else {
                    // MERGE Mode
                    var currentPacks = existingPacks.toList()
                    for (imported in preview.importedPacks) {
                        val targetIdx = currentPacks.indexOfFirst { it.identifier == imported.identifier }
                        if (targetIdx >= 0) {
                            val mergeRes = mergeEngine.mergePacks(currentPacks[targetIdx], imported, hashProvider)
                            val mutableCurrent = currentPacks.toMutableList()
                            mutableCurrent.removeAt(targetIdx)
                            mutableCurrent.addAll(targetIdx, mergeRes.resultPacks)
                            currentPacks = mutableCurrent
                        } else {
                            val rebalanced = mergeEngine.rebalanceCapacity(imported, hashProvider)
                            currentPacks = currentPacks + rebalanced
                        }
                    }
                    finalPacksToSave = currentPacks.toMutableList()
                }

                // Copy physical files from stagingDir into permanent storage
                if (stagingDir != null && stagingDir.exists()) {
                    val packsDir = File(stagingDir, "packs")
                    for (pack in finalPacksToSave) {
                        // Sanitize identifier
                        val safeIdentifier = File(pack.identifier).name
                        val targetPackDir = File(appCtx.filesDir, "stickers/converted/$safeIdentifier").apply { mkdirs() }

                        // Resolve base pack directory if identifier contains underscores
                        val lastUnderscore = safeIdentifier.lastIndexOf('_')
                        val basePackId = if (lastUnderscore > 0) safeIdentifier.substring(0, lastUnderscore) else safeIdentifier
                        val basePackDir = File(appCtx.filesDir, "stickers/converted/$basePackId").apply { mkdirs() }

                        // Resolve original pack directory in staging
                        val stagedPackFolder = File(packsDir, safeIdentifier).takeIf { it.exists() }
                            ?: File(packsDir, basePackId).takeIf { it.exists() }
                            ?: packsDir.listFiles { _, name -> safeIdentifier.startsWith(name) }?.firstOrNull()
                            ?: File(packsDir, safeIdentifier)

                        val stagedStickersFolder = File(stagedPackFolder, "stickers")

                        // Copy tray image with fallback resolution to BOTH targetPackDir and basePackDir
                        val rawTrayName = File(pack.trayImageFile.ifEmpty { "tray.png" }).name
                        val stagedTray = File(stagedPackFolder, rawTrayName)
                        val targetTray = File(targetPackDir, rawTrayName)
                        val baseTray = File(basePackDir, rawTrayName)

                        val fallbackTray = stagedPackFolder.listFiles { _, name -> name.startsWith("tray") }?.firstOrNull()
                        val traySource = if (stagedTray.exists()) stagedTray else fallbackTray

                        if (traySource?.exists() == true) {
                            if (targetTray.canonicalPath.startsWith(targetPackDir.canonicalPath + File.separator)) {
                                traySource.copyTo(targetTray, overwrite = true)
                            }
                            if (baseTray.canonicalPath.startsWith(basePackDir.canonicalPath + File.separator)) {
                                traySource.copyTo(baseTray, overwrite = true)
                            }
                        }

                        // Copy sticker WebP files with path traversal security checks
                        val updatedStickers = pack.stickers.map { sticker ->
                            val safeImageName = File(sticker.imageFileName).name
                            val stagedStickerFile = File(stagedStickersFolder, safeImageName)
                            val targetStickerFile = File(targetPackDir, safeImageName)

                            if (targetStickerFile.canonicalPath.startsWith(targetPackDir.canonicalPath + File.separator)) {
                                if (stagedStickerFile.exists()) {
                                    stagedStickerFile.copyTo(targetStickerFile, overwrite = true)
                                }
                            }
                            sticker.copy(
                                imageFileName = safeImageName,
                                convertedFilePath = targetStickerFile.absolutePath,
                                rawFilePath = targetStickerFile.absolutePath
                            )
                        }

                        val finalIndex = finalPacksToSave.indexOfFirst { it.identifier == pack.identifier }
                        if (finalIndex != -1) {
                            finalPacksToSave[finalIndex] = pack.copy(
                                identifier = safeIdentifier,
                                trayImageFile = rawTrayName,
                                stickers = updatedStickers
                            )
                        }
                    }
                }

                // Filter out invalid packs with fewer than 3 stickers
                val validPacksToSave = finalPacksToSave.filter { it.stickers.size >= 3 }

                repo.storage.savePacksAtomic(validPacksToSave)
                withContext(Dispatchers.Main) { loadSavedPacks() }
                _backupMessage.value = "Backup imported successfully (${preview.importedPacks.size} pack(s))."

            } catch (e: Exception) {
                _backupMessage.value = "Import failed: ${e.message}"
            } finally {
                stagingDir?.deleteRecursively()
                _importPreviewState.value = null
                _isBackupProcessing.value = false
            }
        }
    }

    fun dismissImportPreview() {
        _importPreviewState.value?.stagingDir?.deleteRecursively()
        _importPreviewState.value = null
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _importPreviewState.value?.stagingDir?.deleteRecursively()
    }

    fun resetWorkflow() {
        _stickerSet.value = null
        _downloadedFiles.value = emptyList()
        _downloadProgress.value = DownloadProgress()
        _conversionProgress.value = ConversionProgress()
        _currentPackId.value = ""
        isDownloading = false
        isConverting = false
    }
}
