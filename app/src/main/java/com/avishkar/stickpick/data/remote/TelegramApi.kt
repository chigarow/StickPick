package com.avishkar.stickpick.data.remote

import com.avishkar.stickpick.data.model.TelegramFile
import com.avishkar.stickpick.data.model.TelegramResponse
import com.avishkar.stickpick.data.model.TelegramStickerSet
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface TelegramApi {
    @GET
    suspend fun getStickerSet(
        @Url url: String,
        @Query("name") name: String
    ): TelegramResponse<TelegramStickerSet>

    @GET
    suspend fun getFile(
        @Url url: String,
        @Query("file_id") fileId: String
    ): TelegramResponse<TelegramFile>

    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody
}
