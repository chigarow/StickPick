package com.avishkar.stickpick.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.avishkar.stickpick.data.model.Sticker
import com.avishkar.stickpick.data.model.StickerPack
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class StickerArchiveManagerTest {

    private lateinit var context: Context
    private lateinit var archiveManager: StickerArchiveManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        archiveManager = StickerArchiveManager(context)
    }

    @Test
    fun testExportAndImportArchiveRoundTrip() {
        val packDir = File(context.filesDir, "stickers/converted/pack_test").apply { mkdirs() }
        val trayFile = File(packDir, "tray.png").apply { writeBytes(ByteArray(100) { 1 }) }
        val stickerFile = File(packDir, "sticker_1.webp").apply { writeBytes(ByteArray(200) { 2 }) }

        val pack = StickerPack(
            identifier = "pack_test",
            name = "Test Pack",
            publisher = "Test Publisher",
            trayImageFile = trayFile.name,
            publisherEmail = "test@example.com",
            publisherWebsite = "https://example.com",
            privacyPolicyWebsite = "https://example.com/privacy",
            licenseAgreementWebsite = "https://example.com/license",
            imageDataVersion = "1",
            avoidCache = false,
            animatedStickerPack = false,
            stickers = listOf(
                Sticker(
                    imageFileName = stickerFile.name,
                    emojis = listOf("😀", "🚀"),
                    convertedFilePath = stickerFile.absolutePath
                )
            )
        )

        val outputStream = ByteArrayOutputStream()
        archiveManager.exportArchive(listOf(pack), outputStream)

        val archiveBytes = outputStream.toByteArray()
        assertTrue(archiveBytes.isNotEmpty())

        val inputStream = ByteArrayInputStream(archiveBytes)
        val result = archiveManager.extractArchiveToStaging(inputStream)
        val extractedPacks = result.packs

        assertEquals(1, extractedPacks.size)
        val importedPack = extractedPacks[0]
        assertEquals("pack_test", importedPack.identifier)
        assertEquals("Test Pack", importedPack.name)
        assertEquals(1, importedPack.stickers.size)
        assertEquals(listOf("😀", "🚀"), importedPack.stickers[0].emojis)
    }

    @Test(expected = SecurityException::class)
    fun testZipSlipAttackPreventionWithBackslashes() {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("..\\..\\malicious.txt"))
            zos.write("evil content".toByteArray())
            zos.closeEntry()
        }

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        archiveManager.extractArchiveToStaging(inputStream)
    }

    @Test(expected = ZipSecurityException::class)
    fun testDecompressionStreamByteLimitsZipBomb() {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("packs/pack_bomb/oversized.webp"))
            // Write 2 MB (exceeds 1 MB WebP limit)
            zos.write(ByteArray(2 * 1024 * 1024))
            zos.closeEntry()
        }

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        archiveManager.extractArchiveToStaging(inputStream)
    }
}
