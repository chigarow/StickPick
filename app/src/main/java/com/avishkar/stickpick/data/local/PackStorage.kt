package com.avishkar.stickpick.data.local

import android.content.Context
import com.avishkar.stickpick.data.model.StickerPack
import com.avishkar.stickpick.data.model.StickerPackIndex
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class PackStorage(private val context: Context) {
    private val gson = Gson()

    private val packsDir get() = File(context.filesDir, "sticker_packs").also { it.mkdirs() }
    private val rawDir get() = File(context.filesDir, "stickers/raw").also { it.mkdirs() }
    private val convertedDir get() = File(context.filesDir, "stickers/converted").also { it.mkdirs() }
    private val indexFile get() = File(packsDir, "packs_index.json")

    fun getRawDir(packId: String): File = File(rawDir, packId).also { it.mkdirs() }
    fun getConvertedDir(packId: String): File = File(convertedDir, packId).also { it.mkdirs() }

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
        indexFile.writeText(gson.toJson(packs))
    }

    fun savePack(pack: StickerPack) {
        val packs = loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == pack.identifier }
        if (idx >= 0) packs[idx] = pack else packs.add(pack)
        savePacks(packs)
    }

    fun deletePack(identifier: String) {
        val packs = loadPacks().filter { it.identifier != identifier }
        savePacks(packs)
        File(rawDir, identifier).deleteRecursively()
        File(convertedDir, identifier).deleteRecursively()
    }

    fun generateContentProviderIndex(packs: List<StickerPack>): StickerPackIndex {
        return StickerPackIndex(stickerPacks = packs)
    }

    fun clearAll() {
        rawDir.deleteRecursively()
        convertedDir.deleteRecursively()
        indexFile.delete()
    }
}
