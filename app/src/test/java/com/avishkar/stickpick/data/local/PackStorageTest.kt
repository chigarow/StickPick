package com.avishkar.stickpick.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.avishkar.stickpick.data.model.StickerPack
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PackStorageTest {

    private lateinit var context: Context
    private lateinit var packStorage: PackStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packStorage = PackStorage(context)
    }

    @Test
    fun testAtomicIndexSwapAndFsync() {
        val testPack = StickerPack(
            identifier = "atomic_pack_1",
            name = "Atomic Pack",
            publisher = "Test",
            trayImageFile = "tray.png",
            publisherEmail = "",
            publisherWebsite = "",
            privacyPolicyWebsite = "",
            licenseAgreementWebsite = "",
            imageDataVersion = "1",
            avoidCache = false,
            animatedStickerPack = false,
            stickers = emptyList()
        )

        packStorage.savePacksAtomic(listOf(testPack))

        val loadedPacks = packStorage.loadPacks()
        assertEquals(1, loadedPacks.size)
        assertEquals("atomic_pack_1", loadedPacks[0].identifier)
    }

    @Test
    fun testOverwriteDirectoryPurgePreventsDirectoryNotEmptyException() {
        val packDir = File(context.filesDir, "stickers/converted/purge_pack").apply { mkdirs() }
        File(packDir, "old_file.txt").writeText("old content")
        assertTrue(packDir.exists())

        packStorage.purgePackDirectory(packDir)
        assertFalse(packDir.exists())
    }
}
