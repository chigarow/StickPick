package com.avishkar.stickpick.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.avishkar.stickpick.data.local.PackStorage
import com.avishkar.stickpick.data.model.Sticker
import com.avishkar.stickpick.data.model.StickerPack
import com.avishkar.stickpick.whatsapp.StickerContentProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class StickerBackupIntegrationTest {

    private lateinit var context: Context
    private lateinit var packStorage: PackStorage
    private lateinit var archiveManager: StickerArchiveManager
    private lateinit var provider: StickerContentProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packStorage = PackStorage(context)
        archiveManager = StickerArchiveManager(context)
        provider = Robolectric.setupContentProvider(StickerContentProvider::class.java, StickerContentProvider.AUTHORITY)
    }

    @Test
    fun testExportSplitPackWithBaseDirFilesIncludesAllImagesInZip() {
        // Simulate StickPick structure for split pack: identifier = "my_pack_s1", files in base dir "my_pack"
        val baseId = "my_pack"
        val splitPackId = "my_pack_s1"
        val baseConvertedDir = packStorage.getConvertedDir(baseId)

        val trayFile = File(baseConvertedDir, "tray_my_pack_s1.png").apply { writeBytes(ByteArray(500) { 1 }) }
        val sticker1File = File(baseConvertedDir, "sticker_0.webp").apply { writeBytes(ByteArray(1000) { 2 }) }
        val sticker2File = File(baseConvertedDir, "sticker_1.webp").apply { writeBytes(ByteArray(1000) { 3 }) }
        val sticker3File = File(baseConvertedDir, "sticker_2.webp").apply { writeBytes(ByteArray(1000) { 4 }) }

        val splitPack = StickerPack(
            identifier = splitPackId,
            name = "My Pack (Static)",
            publisher = "Author",
            trayImageFile = trayFile.name,
            stickers = listOf(
                Sticker("sticker_0.webp", listOf("😀"), convertedFilePath = sticker1File.absolutePath),
                Sticker("sticker_1.webp", listOf("🚀"), convertedFilePath = sticker2File.absolutePath),
                Sticker("sticker_2.webp", listOf("🔥"), convertedFilePath = sticker3File.absolutePath)
            )
        )
        packStorage.savePacks(listOf(splitPack))

        // Export
        val exportStream = ByteArrayOutputStream()
        archiveManager.exportArchive(listOf(splitPack), exportStream)
        val zipBytes = exportStream.toByteArray()

        // Inspect Zip entries to verify tray.png and all .webp files are present
        val zipEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("Zip must contain pack.json", zipEntries.any { it.contains("pack.json") })
        assertTrue("Zip must contain tray image", zipEntries.any { it.endsWith(".png") })
        assertTrue("Zip must contain sticker_0.webp", zipEntries.any { it.endsWith("sticker_0.webp") })
        assertTrue("Zip must contain sticker_1.webp", zipEntries.any { it.endsWith("sticker_1.webp") })
        assertTrue("Zip must contain sticker_2.webp", zipEntries.any { it.endsWith("sticker_2.webp") })
    }
}
