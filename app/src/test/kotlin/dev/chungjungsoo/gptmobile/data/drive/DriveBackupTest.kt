package dev.chungjungsoo.gptmobile.data.drive

import dev.chungjungsoo.gptmobile.data.backup.ArchiveChat
import dev.chungjungsoo.gptmobile.data.backup.ArchiveImportCounts
import dev.chungjungsoo.gptmobile.data.backup.ArchiveMemory
import dev.chungjungsoo.gptmobile.data.backup.ArchiveMessage
import dev.chungjungsoo.gptmobile.data.backup.ArchivePlatform
import dev.chungjungsoo.gptmobile.data.backup.BackupCipher
import dev.chungjungsoo.gptmobile.data.backup.BackupCipherException
import dev.chungjungsoo.gptmobile.data.backup.ChatArchive
import dev.chungjungsoo.gptmobile.data.backup.ChatArchiveCodec
import dev.chungjungsoo.gptmobile.data.backup.ChatSnapshotSource
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.security.SoftwareAesGcmVault
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupTest {
    @Test
    fun cipher_roundTrip_restoresPlaintext() {
        val key = ByteArray(32) { index -> index.toByte() }
        val encrypted = BackupCipher.encrypt(key, "archive".encodeToByteArray())
        assertEquals(BackupCipher.MAGIC, encrypted.copyOfRange(0, 4).decodeToString())
        assertEquals("archive", BackupCipher.decrypt(key, encrypted).decodeToString())
    }

    @Test
    fun cipher_wrongKey_failsClosed() {
        val encrypted = BackupCipher.encrypt(ByteArray(32) { 1 }, "archive".encodeToByteArray())
        val error = runCatching { BackupCipher.decrypt(ByteArray(32) { 2 }, encrypted) }.exceptionOrNull()
        assertTrue(error is BackupCipherException)
    }

    @Test
    fun archive_omitsTokensAndSecretRefs() {
        val platform = PlatformV2(
            uid = "uid-1",
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com/v1/",
            token = "sk-live-secret",
            secretRef = "vault-ref-9",
            model = "gpt-4o"
        )
        val json = ChatArchiveCodec.encode(
            ChatArchive(
                exportedAtEpochSeconds = 1,
                platforms = listOf(ChatArchiveCodec.platform(platform)),
                memories = listOf(ArchiveMemory("I like tea", "explicit", createdAt = 1, updatedAt = 1))
            )
        )
        assertFalse(json.contains("sk-live-secret"))
        assertFalse(json.contains("vault-ref-9"))
        val decoded = ChatArchiveCodec.decode(json)
        assertEquals("uid-1", decoded.platforms.single().uid)
        assertEquals("I like tea", decoded.memories.single().text)
    }

    @Test
    fun platformsToInsert_skipsExistingUidsAndDropsSecrets() {
        val incoming = listOf(
            ArchivePlatform(uid = "keep", name = "A", compatibleType = "OPENAI", apiUrl = "https://a", model = "m"),
            ArchivePlatform(uid = "new", name = "B", compatibleType = "OPENAI", apiUrl = "https://b", model = "m"),
            ArchivePlatform(uid = "bad", name = "C", compatibleType = "NOPE", apiUrl = "https://c", model = "m")
        )
        val inserted = ChatArchiveCodec.platformsToInsert(setOf("keep"), incoming)
        assertEquals(listOf("new"), inserted.map { it.uid })
        assertNull(inserted.single().token)
        assertNull(inserted.single().secretRef)
    }

    @Test
    fun backup_notConfigured_doesNotUpload() = runBlocking {
        val transport = FakeDrive()
        val snapshots = FakeSnapshots()
        val result = coordinator(transport, snapshots, unlocked = true)
            .backup(DriveAuthorization.NotConfigured("missing client"))
        assertTrue(result is DriveBackupResult.NotConfigured)
        assertEquals(0, transport.uploads)
        assertEquals(0, snapshots.snapshots)
    }

    @Test
    fun backup_lockedVault_doesNotEncryptOrUpload() = runBlocking {
        val transport = FakeDrive()
        val snapshots = FakeSnapshots()
        val result = coordinator(transport, snapshots, unlocked = false)
            .backup(DriveAuthorization.Ready("access-token"))
        assertTrue(result is DriveBackupResult.Locked)
        assertEquals(0, transport.uploads)
        assertEquals(0, snapshots.snapshots)
        assertNull(transport.bytes)
    }

    @Test
    fun backupAndRestore_roundTrip_importsArchive() = runBlocking {
        val transport = FakeDrive()
        val snapshots = FakeSnapshots()
        val backup = coordinator(transport, snapshots, unlocked = true)
        val backedUp = backup.backup(DriveAuthorization.Ready("access-token"))
        assertTrue(backedUp is DriveBackupResult.BackedUp)
        assertEquals(1, transport.uploads)
        assertTrue(transport.bytes!!.copyOfRange(0, 4).decodeToString() == BackupCipher.MAGIC)
        val restored = backup.restore(DriveAuthorization.Ready("access-token"))
        assertTrue(restored is DriveBackupResult.Restored)
        assertEquals(snapshots.exported, snapshots.imported)
    }

    private fun coordinator(
        transport: FakeDrive,
        snapshots: FakeSnapshots,
        unlocked: Boolean
    ): DriveBackupCoordinator {
        val directory = Files.createTempDirectory("drive-vault").toFile()
        val session = VaultSession().apply {
            useClock { if (unlocked) 1_000L else 0L }
            if (unlocked) unlock()
        }
        return DriveBackupCoordinator(
            snapshots = snapshots,
            syncDao = FakeSyncDao(),
            transport = transport,
            vault = SoftwareAesGcmVault(directory),
            session = session
        )
    }
}

private class FakeSnapshots : ChatSnapshotSource {
    var snapshots = 0
    var exported: ChatArchive? = null
    var imported: ChatArchive? = null
    val archive = ChatArchive(
        exportedAtEpochSeconds = 10,
        platforms = listOf(
            ArchivePlatform(
                uid = "p1",
                name = "OpenAI",
                compatibleType = "OPENAI",
                apiUrl = "https://api.openai.com/v1/",
                model = "gpt-4o"
            )
        ),
        chats = listOf(
            ArchiveChat(
                title = "Hello",
                createdAt = 1,
                updatedAt = 2,
                messages = listOf(ArchiveMessage(id = 7, content = "Hi", createdAt = 3))
            )
        ),
        memories = listOf(ArchiveMemory(text = "tea", source = "explicit", createdAt = 1, updatedAt = 1))
    )

    override suspend fun snapshot(nowEpochSeconds: Long): ChatArchive {
        snapshots++
        val copy = archive.copy(exportedAtEpochSeconds = nowEpochSeconds)
        exported = copy
        return copy
    }

    override suspend fun importAdditive(archive: ChatArchive): ArchiveImportCounts {
        imported = archive
        return ArchiveImportCounts(0, archive.chats.size, archive.memories.size)
    }
}

private class FakeDrive : DriveAppDataStore {
    var uploads = 0
    var fileId: String? = null
    var bytes: ByteArray? = null

    override suspend fun findBackup(accessToken: String): String? = fileId

    override suspend fun upload(accessToken: String, existingFileId: String?, bytes: ByteArray): String {
        uploads++
        this.bytes = bytes.copyOf()
        fileId = existingFileId ?: "drive-file-1"
        return fileId!!
    }

    override suspend fun download(accessToken: String, fileId: String): ByteArray = bytes ?: error("no backup")
}

private class FakeSyncDao : DriveSyncDao {
    var state: DriveSyncState? = null

    override suspend fun get(): DriveSyncState? = state

    override suspend fun upsert(state: DriveSyncState) {
        this.state = state
    }
}
