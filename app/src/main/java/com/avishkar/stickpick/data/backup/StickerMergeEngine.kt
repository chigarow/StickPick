package com.avishkar.stickpick.data.backup

import com.avishkar.stickpick.data.model.Sticker
import com.avishkar.stickpick.data.model.StickerPack
import java.io.File

data class MergeResult(
    val resultPacks: List<StickerPack>,
    val warnings: List<String>
)

data class ImportPreviewState(
    val importedPacks: List<StickerPack>,
    val warnings: List<String>,
    val stagingDir: File? = null,
    val canProceed: Boolean = true
)

enum class ImportMode {
    MERGE,
    OVERWRITE
}

class StickerMergeEngine {

    fun mergePacks(
        targetPack: StickerPack,
        importedPack: StickerPack,
        hashProvider: (File) -> String
    ): MergeResult {
        val warnings = mutableListOf<String>()
        val existingHashes = targetPack.stickers.associateBy { sticker ->
            val file = File(sticker.convertedFilePath.ifEmpty { sticker.imageFileName })
            hashProvider(file)
        }

        val finalStickers = targetPack.stickers.toMutableList()

        for (importedSticker in importedPack.stickers) {
            val file = File(importedSticker.convertedFilePath.ifEmpty { importedSticker.imageFileName })
            val hash = hashProvider(file)
            val existing = existingHashes[hash]

            if (existing != null && !hash.startsWith("missing")) {
                // Duplicate image found: merge emojis
                val combinedEmojis = (existing.emojis + importedSticker.emojis)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(3)
                    .ifEmpty { listOf("😀") }

                val index = finalStickers.indexOfFirst { it.imageFileName == existing.imageFileName }
                if (index != -1) {
                    finalStickers[index] = existing.copy(emojis = combinedEmojis)
                }
            } else {
                // New unseen sticker
                finalStickers.add(importedSticker)
            }
        }

        val mergedPack = targetPack.copy(stickers = finalStickers)
        val rebalancedPacks = rebalanceCapacity(mergedPack, hashProvider)

        if (rebalancedPacks.size > 1) {
            warnings.add("Pack '${targetPack.name}' overflowed 30 stickers. Created ${rebalancedPacks.size - 1} continuation pack(s).")
        }

        return MergeResult(rebalancedPacks, warnings)
    }

    fun rebalanceCapacity(
        pack: StickerPack,
        hashProvider: (File) -> String
    ): List<StickerPack> {
        val stickers = pack.stickers
        if (stickers.size <= 30) {
            return listOf(pack)
        }

        val resultPacks = mutableListOf<StickerPack>()
        var remainingStickers = stickers.toList()
        var packCounter = 1

        while (remainingStickers.isNotEmpty()) {
            val totalRemaining = remainingStickers.size
            val takeCount = when {
                totalRemaining <= 30 -> totalRemaining
                totalRemaining - 30 < 3 -> 30 - (3 - (totalRemaining - 30)) // Ensure remainder is at least 3
                else -> 30
            }

            val currentChunk = remainingStickers.take(takeCount)
            remainingStickers = remainingStickers.drop(takeCount)

            if (packCounter == 1) {
                resultPacks.add(pack.copy(stickers = currentChunk))
            } else {
                val continuationId = "${pack.identifier}_c$packCounter"
                val continuationName = "${pack.name} (continued $packCounter)"
                val continuationPack = pack.copy(
                    identifier = continuationId,
                    name = continuationName,
                    trayImageFile = "tray_$continuationId.png",
                    stickers = currentChunk
                )
                resultPacks.add(continuationPack)
            }
            packCounter++
        }

        return resultPacks
    }

    fun analyzeImport(
        importedPacks: List<StickerPack>,
        existingPacks: List<StickerPack>,
        stagingDir: File? = null
    ): ImportPreviewState {
        val warnings = mutableListOf<String>()

        for (imported in importedPacks) {
            if (imported.stickers.size < 3) {
                warnings.add("Pack '${imported.name}' has only ${imported.stickers.size} stickers (WhatsApp minimum is 3).")
            }
            val existing = existingPacks.find { it.identifier == imported.identifier }
            if (existing != null) {
                val totalCombined = existing.stickers.size + imported.stickers.size
                if (totalCombined > 30) {
                    warnings.add("Merging pack '${imported.name}' will exceed 30 stickers ($totalCombined total). Continuation pack will be created.")
                }
            }
        }

        return ImportPreviewState(
            importedPacks = importedPacks,
            warnings = warnings,
            stagingDir = stagingDir
        )
    }
}
