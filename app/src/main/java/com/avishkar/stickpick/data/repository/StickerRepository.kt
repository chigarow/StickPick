package com.avishkar.stickpick.data.repository

import android.content.Context
import com.avishkar.stickpick.data.local.PackStorage
import com.avishkar.stickpick.data.local.PreferencesManager
import com.avishkar.stickpick.data.model.*
import com.avishkar.stickpick.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StickerRepository(context: Context) {
    private val api = RetrofitClient.telegramApi
    val prefs = PreferencesManager(context)
    val storage = PackStorage(context)

    suspend fun fetchStickerSet(packNameOrUrl: String): Result<TelegramStickerSet> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.botToken.first()
            if (token.isBlank()) return@withContext Result.failure(Exception("Bot token not configured"))

            val name = extractPackName(packNameOrUrl)
            val url = "https://api.telegram.org/bot$token/getStickerSet"
            val response = api.getStickerSet(url, name)
            if (response.ok && response.result != null) {
                Result.success(response.result)
            } else {
                Result.failure(Exception(response.description ?: "Failed to fetch sticker set"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadSticker(
        sticker: TelegramSticker,
        packId: String,
        index: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.botToken.first()
            val url = "https://api.telegram.org/bot$token/getFile"
            val fileResponse = api.getFile(url, sticker.fileId)
            if (!fileResponse.ok || fileResponse.result?.filePath == null) {
                return@withContext Result.failure(Exception("Failed to get file path"))
            }

            val filePath = fileResponse.result.filePath!!
            val ext = filePath.substringAfterLast('.', "webp")
            val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"

            val body = api.downloadFile(downloadUrl)
            val dir = storage.getRawDir(packId)
            val file = File(dir, "sticker_${index}.$ext")

            FileOutputStream(file).use { fos ->
                body.byteStream().use { it.copyTo(fos) }
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractPackName(input: String): String {
        val trimmed = input.trim()
        // Handle t.me/addstickers/PackName
        val addStickersRegex = Regex("(?:https?://)?t\\.me/addstickers/([\\w]+)")
        addStickersRegex.find(trimmed)?.let { return it.groupValues[1] }
        // Handle direct pack name
        return trimmed.substringAfterLast("/")
    }

    fun splitIntoPacks(
        stickers: List<Sticker>,
        baseName: String,
        publisher: String,
        packId: String,
        trayFileName: String,
        maxPerPack: Int = 30
    ): List<StickerPack> {
        return stickers.chunked(maxPerPack).mapIndexed { index, chunk ->
            val suffix = if (stickers.size > maxPerPack) "_${index + 1}" else ""
            StickerPack(
                identifier = "${packId}${suffix}",
                name = "${baseName}${suffix}",
                publisher = publisher,
                trayImageFile = trayFileName,
                stickers = chunk,
                animatedStickerPack = chunk.any { it.rawFilePath.endsWith(".webm") || it.rawFilePath.endsWith(".mp4") }
            )
        }
    }
}
