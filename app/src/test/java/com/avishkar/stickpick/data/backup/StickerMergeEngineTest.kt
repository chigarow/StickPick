package com.avishkar.stickpick.data.backup

import com.avishkar.stickpick.data.model.Sticker
import com.avishkar.stickpick.data.model.StickerPack
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class StickerMergeEngineTest {

    private lateinit var mergeEngine: StickerMergeEngine

    @Before
    fun setUp() {
        mergeEngine = StickerMergeEngine()
    }

    @Test
    fun testSmartMergeDeduplicationAndEmojiUnion() {
        val existingSticker = Sticker(
            imageFileName = "s1.webp",
            emojis = listOf("😀"),
            convertedFilePath = "/path/s1.webp"
        )
        val existingPack = StickerPack(
            identifier = "pack_1",
            name = "My Pack",
            publisher = "Test",
            trayImageFile = "tray.png",
            publisherEmail = "",
            publisherWebsite = "",
            privacyPolicyWebsite = "",
            licenseAgreementWebsite = "",
            imageDataVersion = "1",
            avoidCache = false,
            animatedStickerPack = false,
            stickers = listOf(
                existingSticker,
                Sticker(imageFileName = "s2.webp", emojis = listOf("😀"), convertedFilePath = "/path/s2.webp"),
                Sticker(imageFileName = "s3.webp", emojis = listOf("😀"), convertedFilePath = "/path/s3.webp")
            )
        )

        val importedStickerSameHash = Sticker(
            imageFileName = "imp_s1.webp",
            emojis = listOf("🚀", "🔥", "🎉"),
            convertedFilePath = "/path/imp_s1.webp"
        )
        val importedPack = existingPack.copy(
            stickers = listOf(importedStickerSameHash)
        )

        // Mock hash calculator returning same hash for s1 and imp_s1
        val hashProvider: (File) -> String = { file ->
            if (file.name.contains("s1")) "hash_shared_s1" else "hash_${file.name}"
        }

        val mergeResult = mergeEngine.mergePacks(
            targetPack = existingPack,
            importedPack = importedPack,
            hashProvider = hashProvider
        )

        val mergedPack = mergeResult.resultPacks[0]
        assertEquals(3, mergedPack.stickers.size)
        // Emojis should be merged and capped at 3
        assertEquals(listOf("😀", "🚀", "🔥"), mergedPack.stickers[0].emojis)
    }

    @Test
    fun testCapacityOverflowRebalancingMin3Max30() {
        val stickers31 = (1..31).map { i ->
            Sticker(
                imageFileName = "s_$i.webp",
                emojis = listOf("😀"),
                convertedFilePath = "/path/s_$i.webp"
            )
        }
        val largePack = StickerPack(
            identifier = "pack_large",
            name = "Large Pack",
            publisher = "Test",
            trayImageFile = "tray.png",
            publisherEmail = "",
            publisherWebsite = "",
            privacyPolicyWebsite = "",
            licenseAgreementWebsite = "",
            imageDataVersion = "1",
            avoidCache = false,
            animatedStickerPack = false,
            stickers = stickers31
        )

        val hashProvider: (File) -> String = { file -> "hash_${file.name}" }

        val resultPacks = mergeEngine.rebalanceCapacity(largePack, hashProvider)

        assertEquals(2, resultPacks.size)
        assertEquals(28, resultPacks[0].stickers.size)
        assertEquals(3, resultPacks[1].stickers.size)
        assertEquals("Large Pack (continued 2)", resultPacks[1].name)
        assertTrue(resultPacks[1].identifier.endsWith("_c2"))
    }
}
