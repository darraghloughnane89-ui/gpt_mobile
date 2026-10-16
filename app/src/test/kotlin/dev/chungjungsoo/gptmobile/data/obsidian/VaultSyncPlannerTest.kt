package dev.chungjungsoo.gptmobile.data.obsidian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSyncPlannerTest {
    @Test
    fun plan_remoteOnly_pulls() {
        val remote = note(memory("mem-1", "Tea", "Likes tea", 20))

        val plan = VaultSyncPlanner.plan(emptyList(), listOf(remote), emptyMap())

        assertEquals(listOf("mem-1"), plan.pulls.map { it.document.id })
        assertTrue(plan.pushes.isEmpty())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun plan_localOnly_pushes() {
        val local = note(memory("mem-1", "Tea", "Likes tea", 20))

        val plan = VaultSyncPlanner.plan(listOf(local), emptyList(), emptyMap())

        assertEquals(listOf("mem-1"), plan.pushes.map { it.document.id })
        assertTrue(plan.pulls.isEmpty())
    }

    @Test
    fun plan_sameHash_doesNothing() {
        val local = note(memory("mem-1", "Tea", "Likes tea", 20))

        val plan = VaultSyncPlanner.plan(listOf(local), listOf(local), emptyMap())

        assertTrue(plan.pulls.isEmpty())
        assertTrue(plan.pushes.isEmpty())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun plan_remoteChangedSinceBase_pulls() {
        val base = note(memory("mem-1", "Tea", "Likes tea", 20))
        val remote = note(memory("mem-1", "Tea", "Likes coffee", 30))

        val plan = VaultSyncPlanner.plan(
            local = listOf(base),
            remote = listOf(remote),
            index = mapOf(entry(base))
        )

        assertEquals("Likes coffee", plan.pulls.single().document.body)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun plan_localChangedSinceBase_pushes() {
        val base = note(memory("mem-1", "Tea", "Likes tea", 20))
        val local = note(memory("mem-1", "Tea", "Likes coffee", 30))

        val plan = VaultSyncPlanner.plan(
            local = listOf(local),
            remote = listOf(base),
            index = mapOf(entry(base))
        )

        assertEquals("Likes coffee", plan.pushes.single().document.body)
    }

    @Test
    fun plan_bothChanged_memoryConflicts() {
        val base = note(memory("mem-1", "Tea", "Likes tea", 20))
        val local = note(memory("mem-1", "Tea", "Phone edit", 40))
        val remote = note(memory("mem-1", "Tea", "Vault edit", 50))

        val plan = VaultSyncPlanner.plan(
            local = listOf(local),
            remote = listOf(remote),
            index = mapOf(entry(base))
        )

        val conflict = plan.conflicts.single()
        assertEquals("mem-1", conflict.id)
        assertEquals("Phone edit", conflict.local.document.body)
        assertEquals("Vault edit", conflict.remote.document.body)
        assertTrue(plan.pulls.isEmpty())
        assertTrue(plan.pushes.isEmpty())
    }

    @Test
    fun plan_bothAppendedChatMessages_merges() {
        val local = note(chat("chat-1", message("m1", "Hello", 10), message("m2", "Phone", 20)))
        val remote = note(chat("chat-1", message("m1", "Hello", 10), message("m3", "Vault", 30)))

        val plan = VaultSyncPlanner.plan(listOf(local), listOf(remote), emptyMap())

        val pushed = plan.pushes.single().document.messages.map { it.id }
        assertEquals(listOf("m1", "m2", "m3"), pushed)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun plan_editedSameChatMessage_conflicts() {
        val local = note(chat("chat-1", message("m1", "Phone", 10)))
        val remote = note(chat("chat-1", message("m1", "Vault", 10)))

        val plan = VaultSyncPlanner.plan(listOf(local), listOf(remote), emptyMap())

        assertEquals("chat-1", plan.conflicts.single().id)
        assertTrue(plan.conflicts.single().allowKeepBoth)
    }

    @Test
    fun mergeChat_divergedSharedMessage_returnsNull() {
        val merged = VaultSyncPlanner.mergeChat(
            base = listOf(message("m1", "Base", 10)),
            local = listOf(message("m1", "Phone", 10)),
            remote = listOf(message("m1", "Vault", 10))
        )

        assertNull(merged)
    }

    @Test
    fun keepBoth_sameMessageId_keepsBothCopies() {
        val local = chat("chat-1", message("m1", "Phone", 10))
        val remote = chat("chat-1", message("m1", "Vault", 12))

        val merged = VaultSyncPlanner.keepBoth(local, remote, 99)

        assertEquals(listOf("m1", "m1#vault"), merged.messages.map { it.id })
        assertEquals(99, merged.updatedEpochSeconds)
    }

    private fun memory(id: String, title: String, body: String, updated: Long) = VaultDocument(
        id = id,
        type = VaultNoteType.MEMORY,
        updatedEpochSeconds = updated,
        title = title,
        body = body
    )

    private fun chat(id: String, vararg messages: VaultMessage) = VaultDocument(
        id = id,
        type = VaultNoteType.CHAT,
        updatedEpochSeconds = messages.maxOf { it.atEpochSeconds },
        title = "Chat",
        project = "Inbox",
        messages = messages.toList()
    )

    private fun message(id: String, content: String, at: Long) = VaultMessage(
        id = id,
        role = "user",
        content = content,
        atEpochSeconds = at
    )

    private fun note(document: VaultDocument) = VaultMarkdown.track(document, "Robin/Memory/${document.id}.md")

    private fun entry(note: TrackedNote) = note.document.id to SyncIndexEntry(
        id = note.document.id,
        path = note.path,
        type = note.document.type.wire,
        baseHash = note.hash
    )
}
