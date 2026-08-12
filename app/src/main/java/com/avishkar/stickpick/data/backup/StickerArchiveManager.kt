package com.avishkar.stickpick.data.backup

import android.content.Context
import com.avishkar.stickpick.data.model.Sticker
import com.avishkar.stickpick.data.model.StickerPack
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveManifest(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val packCount: Int,
    val files: List<ManifestFileEntry>
)

data class ManifestFileEntry(
    val path: String,
    val sha256: String
)

data class StagingExtractionResult(
    val packs: List<StickerPack>,
    val stagingDir: File
)

class StickerArchiveManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        const val MAX_STICKER_SIZE_BYTES = 1 * 1024 * 1024L // 1 MB
        const val MAX_JSON_PNG_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        const val MAX_TOTAL_ARCHIVE_BYTES = 100 * 1024 * 1024L // 100 MB
    }

    private fun resolveExistingFile(identifier: String, fileName: String, explicitPath: String = ""): File? {
        if (explicitPath.isNotBlank()) {
            val f = File(explicitPath)
            val filesPath = context.filesDir.canonicalPath
            val cachePath = context.cacheDir.canonicalPath
            if (f.exists() && (f.canonicalPath.startsWith(filesPath + File.separator) || f.canonicalPath.startsWith(cachePath + File.separator))) {
                return f
            }
        }
        val safeId = File(identifier).name
        val safeName = File(fileName).name

        val exact = File(context.filesDir, "stickers/converted/$safeId/$safeName")
        if (exact.exists()) return exact

        val lastUnderscore = safeId.lastIndexOf('_')
        if (lastUnderscore > 0) {
            val baseId = safeId.substring(0, lastUnderscore)
            val baseFile = File(context.filesDir, "stickers/converted/$baseId/$safeName")
            if (baseFile.exists()) return baseFile
        }

        val firstUnderscore = safeId.substringBefore('_')
        val firstBaseFile = File(context.filesDir, "stickers/converted/$firstUnderscore/$safeName")
        if (firstBaseFile.exists()) return firstBaseFile

        return null
    }

    fun exportArchive(packs: List<StickerPack>, outputStream: OutputStream) {
        val zipOut = ZipOutputStream(outputStream)
        val manifestEntries = mutableListOf<ManifestFileEntry>()

        for (pack in packs) {
            val safePackId = File(pack.identifier).name
            val relativePackPath = "packs/$safePackId"

            // 1. Write portable pack.json
            val portablePack = pack.copy(
                identifier = safePackId,
                stickers = pack.stickers.map { sticker ->
                    val pathStr = sticker.convertedFilePath.ifEmpty { sticker.imageFileName }
                    val safeStickerName = File(pathStr).name
                    sticker.copy(
                        imageFileName = safeStickerName,
                        convertedFilePath = "${relativePackPath}/stickers/$safeStickerName",
                        rawFilePath = ""
                    )
                }
            )
            val packJsonBytes = gson.toJson(portablePack).toByteArray(Charsets.UTF_8)
            val packJsonEntryPath = "${relativePackPath}/pack.json"
            writeZipEntry(zipOut, packJsonEntryPath, packJsonBytes)
            manifestEntries.add(ManifestFileEntry(packJsonEntryPath, calculateSha256(packJsonBytes)))

            // 2. Write tray.png
            val safeTrayName = File(pack.trayImageFile.ifEmpty { "tray.png" }).name
            val trayToUse = resolveExistingFile(safePackId, safeTrayName)
            if (trayToUse != null && trayToUse.exists()) {
                val trayBytes = trayToUse.readBytes()
                val trayEntryPath = "${relativePackPath}/${trayToUse.name}"
                writeZipEntry(zipOut, trayEntryPath, trayBytes)
                manifestEntries.add(ManifestFileEntry(trayEntryPath, calculateSha256(trayBytes)))
            }

            // 3. Write sticker WebP files
            for (sticker in pack.stickers) {
                val safeStickerName = File(sticker.imageFileName).name
                val stickerToRead = resolveExistingFile(safePackId, safeStickerName, sticker.convertedFilePath)
                if (stickerToRead != null && stickerToRead.exists()) {
                    val stickerBytes = stickerToRead.readBytes()
                    val stickerEntryPath = "${relativePackPath}/stickers/${stickerToRead.name}"
                    writeZipEntry(zipOut, stickerEntryPath, stickerBytes)
                    manifestEntries.add(ManifestFileEntry(stickerEntryPath, calculateSha256(stickerBytes)))
                }
            }
        }

        // 4. Write manifest.json
        val manifest = ArchiveManifest(
            packCount = packs.size,
            files = manifestEntries
        )
        val manifestBytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        writeZipEntry(zipOut, "manifest.json", manifestBytes)

        zipOut.finish()
    }

    private fun writeZipEntry(zipOut: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName.replace('\\', '/'))
        zipOut.putNextEntry(entry)
        zipOut.write(data)
        zipOut.closeEntry()
    }

    fun extractArchiveToStaging(inputStream: InputStream): StagingExtractionResult {
        val sessionId = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, "import_staging/$sessionId").apply { mkdirs() }

        var totalArchiveBytesRead = 0L

        try {
            val zipIn = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipIn.nextEntry

            while (entry != null) {
                val normalizedName = entry.name.replace('\\', '/')
                val destinationFile = File(stagingDir, normalizedName)

                // Zip-Slip Security Check
                if (!destinationFile.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                    throw SecurityException("Zip-Slip attack detected in entry: ${entry.name}")
                }

                if (!entry.isDirectory) {
                    destinationFile.parentFile?.mkdirs()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var entryBytesRead = 0L
                    val maxAllowedForEntry = if (normalizedName.endsWith(".webp")) MAX_STICKER_SIZE_BYTES else MAX_JSON_PNG_SIZE_BYTES

                    FileOutputStream(destinationFile).use { fos ->
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            entryBytesRead += bytesRead
                            totalArchiveBytesRead += bytesRead

                            if (entryBytesRead > maxAllowedForEntry) {
                                throw ZipSecurityException("Zip bomb detected: entry $normalizedName exceeded max limit of $maxAllowedForEntry bytes")
                            }
                            if (totalArchiveBytesRead > MAX_TOTAL_ARCHIVE_BYTES) {
                                throw ZipSecurityException("Zip bomb detected: total uncompressed archive size exceeded $MAX_TOTAL_ARCHIVE_BYTES bytes")
                            }

                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }

            // Verify manifest.json
            val manifestFile = File(stagingDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw SecurityException("Invalid archive: manifest.json is missing")
            }
            val manifest = gson.fromJson(manifestFile.readText(), ArchiveManifest::class.java)

            // Verify SHA-256 checksum for all files in manifest with path traversal check
            for (manifestEntry in manifest.files) {
                val fileOnDisk = File(stagingDir, manifestEntry.path)
                if (!fileOnDisk.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                    throw SecurityException("Path traversal attack detected in manifest entry: ${manifestEntry.path}")
                }
                if (!fileOnDisk.exists()) {
                    throw SecurityException("Archive verification failed: missing file ${manifestEntry.path}")
                }
                val actualHash = calculateSha256(fileOnDisk.readBytes())
                if (actualHash != manifestEntry.sha256) {
                    throw ZipSecurityException("Checksum verification failed for ${manifestEntry.path}")
                }
            }

            // Reconstruct packs pointing to STAGING file paths initially
            val packs = mutableListOf<StickerPack>()
            val packsDir = File(stagingDir, "packs")
            val packFolders = packsDir.listFiles { f -> f.isDirectory } ?: emptyArray()

            for (packFolder in packFolders) {
                val packJsonFile = File(packFolder, "pack.json")
                if (packJsonFile.exists()) {
                    val pack = gson.fromJson(packJsonFile.readText(), StickerPack::class.java)
                    val safePackId = File(pack.identifier).name
                    val stagingPackDir = File(packFolder, "stickers")

                    val rebasedStickers = pack.stickers.map { sticker ->
                        val safeImageName = File(sticker.imageFileName).name
                        val stickerFile = File(stagingPackDir, safeImageName)
                        sticker.copy(
                            imageFileName = safeImageName,
                            convertedFilePath = stickerFile.absolutePath,
                            rawFilePath = stickerFile.absolutePath
                        )
                    }

                    val rebasedPack = pack.copy(
                        identifier = safePackId,
                        trayImageFile = File(pack.trayImageFile.ifEmpty { "tray.png" }).name,
                        stickers = rebasedStickers
                    )
                    packs.add(rebasedPack)
                }
            }

            return StagingExtractionResult(packs, stagingDir)

        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw e
        }
    }

    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
