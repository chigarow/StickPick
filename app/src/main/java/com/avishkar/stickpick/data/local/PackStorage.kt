package com.avishkar.stickpick.data.local

import android.content.Context
import android.net.Uri
import com.avishkar.stickpick.data.model.StickerPack
import com.avishkar.stickpick.data.model.StickerPackIndex
import com.avishkar.stickpick.whatsapp.StickerContentProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class PackStorage(private val context: Context) {
    private val gson = Gson()
    private val storageMutex = Mutex()

    private val packsDir get() = File(context.filesDir, "sticker_packs").also { it.mkdirs() }
    private val rawDir get() = File(context.filesDir, "stickers/raw").also { it.mkdirs() }
    private val convertedDir get() = File(context.filesDir, "stickers/converted").also { it.mkdirs() }
    private val indexFile get() = File(packsDir, "packs_index.json")

    fun getRawDir(packId: String): File = File(rawDir, File(packId).name).also { it.mkdirs() }
    fun getConvertedDir(packId: String): File = File(convertedDir, File(packId).name).also { it.mkdirs() }

    fun loadPacks(): List<StickerPack> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = indexFile.readText()
            val type = object : TypeToken<List<StickerPack>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePacks(packs: List<StickerPack>) {
        savePacksAtomic(packs)
    }

    fun savePacksAtomic(packs: List<StickerPack>) {
        runBlocking {
            storageMutex.withLock {
                performAtomicWrite(packs)
            }
        }
    }

    suspend fun savePacksAtomicThreadSafe(packs: List<StickerPack>) {
        storageMutex.withLock {
            performAtomicWrite(packs)
        }
    }

    private fun performAtomicWrite(packs: List<StickerPack>) {
        val tmpFile = File(packsDir, "packs_index.json.tmp")
        val jsonBytes = gson.toJson(packs).toByteArray(Charsets.UTF_8)

        FileOutputStream(tmpFile).use { fos ->
            fos.write(jsonBytes)
            fos.flush()
            fos.fd.sync()
        }

        try {
            Files.move(
                tmpFile.toPath(),
                indexFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            Files.move(
                tmpFile.toPath(),
                indexFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        notifyWhatsAppContentProvider()
    }

    fun savePack(pack: StickerPack) {
        val safePack = pack.copy(identifier = File(pack.identifier).name)
        val packs = loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == safePack.identifier }
        if (idx >= 0) packs[idx] = safePack else packs.add(safePack)
        savePacksAtomic(packs)
    }

    fun purgePackDirectory(targetDir: File, baseDir: File = convertedDir) {
        if (targetDir.exists() && targetDir.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
            targetDir.deleteRecursively()
        }
    }

    fun deletePack(identifier: String) {
        val safeId = File(identifier).name
        val packs = loadPacks().filter { it.identifier != safeId }
        savePacksAtomic(packs)
        purgePackDirectory(File(rawDir, safeId), rawDir)
        purgePackDirectory(File(convertedDir, safeId), convertedDir)
    }

    fun notifyWhatsAppContentProvider() {
        try {
            val uri = Uri.parse("content://${StickerContentProvider.AUTHORITY}/metadata")
            context.contentResolver.notifyChange(uri, null)
        } catch (e: Exception) {
            // Ignore in unit tests if ContentResolver is unmocked
        }
    }

    fun generateContentProviderIndex(packs: List<StickerPack>): StickerPackIndex {
        return StickerPackIndex(stickerPacks = packs)
    }

    fun clearAll() {
        rawDir.deleteRecursively()
        convertedDir.deleteRecursively()
        indexFile.delete()
        notifyWhatsAppContentProvider()
    }
}
