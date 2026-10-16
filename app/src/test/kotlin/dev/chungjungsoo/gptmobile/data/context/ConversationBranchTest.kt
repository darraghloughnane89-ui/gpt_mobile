package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveThoughts
import dev.chungjungsoo.gptmobile.data.database.entity.selectRevision
import dev.chungjungsoo.gptmobile.data.database.entity.snapshotLatestAssistantRevision
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.chat.persistableMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBranchTest {
    @Test
    fun `existing linear chats stay on one path`() {
        val messages = listOf(
            user(1, "Hello"),
            assistant(2, 1, "Hi"),
            user(3, "Next"),
            assistant(4, 3, "Sure")
        )

        val transcript = activeTranscript(messages)

        assertEquals(listOf("Hello", "Next"), transcript.activeUsers.map { it.content })
        assertEquals(listOf("Hi", "Sure"), transcript.activeAssistants.map { it.single().content })
        assertTrue(transcript.archived.isEmpty())
        assertNull(userBranchSwitch(messages[0], messages))
        assertNull(userBranchSwitch(messages[2], messages))
    }

    @Test
    fun `editing a past user message keeps the old path as a branch`() {
        val messages = listOf(
            user(1, "Hello"),
            assistant(2, 1, "Hi"),
            user(3, "Next"),
            assistant(4, 3, "Sure"),
            user(5, "Later"),
            assistant(6, 5, "Done")
        )

        val branched = applyUserEditBranch(
            messages = messages,
            editedUserId = 3,
            content = "Changed",
            attachments = emptyList(),
            timestamp = 50L
        )!!
        val active = activeTranscript(branched.messages)

        assertEquals(listOf("Hello", "Changed"), active.activeUsers.map { it.content })
        assertEquals("Hi", active.activeAssistants.first().single().content)
        assertTrue(active.activeAssistants.last().isEmpty())
        assertEquals(
            setOf("Next", "Sure", "Later", "Done"),
            active.archived.map { it.content }.toSet()
        )
        assertEquals("< 2/2 >", branchSwitcherLabel(userBranchSwitch(active.activeUsers.last(), branched.messages)!!.position, 2))

        val restored = activeTranscript(
            selectActiveSibling(branched.messages, parentMessageId = 1, siblingIndex = 0)
        )
        assertEquals(listOf("Hello", "Next", "Later"), restored.activeUsers.map { it.content })
        assertTrue(restored.archived.any { it.content == "Changed" })
    }

    @Test
    fun `active path is the only context after a branch`() {
        val platform = PlatformV2(
            uid = "profile",
            name = "Provider",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://provider.example/v1",
            model = "model"
        )
        val branched = applyUserEditBranch(
            messages = listOf(
                user(1, "Remember the color blue"),
                assistant(2, 1, "Blue", platform.uid),
                user(3, "What color?"),
                assistant(4, 3, "Blue", platform.uid)
            ),
            editedUserId = 3,
            content = "What animal?",
            attachments = emptyList(),
            timestamp = 9L
        )!!
        val active = activeTranscript(branched.messages)
        val turns = ContextBuilder().build(
            userMessages = active.activeUsers,
            assistantMessages = active.activeAssistants,
            platform = platform
        )

        assertEquals(listOf("Remember the color blue", "What animal?"), turns.map { it.userMessage.content })
        assertTrue(turns.none { it.userMessage.content == "What color?" })
        assertTrue(active.archived.any { it.content == "What color?" })
    }

    @Test
    fun `regenerating keeps the previous answer as a selectable branch`() {
        val original = assistant(8, 7, "First answer").copy(thoughts = "First thoughts")
        val saved = original.snapshotLatestAssistantRevision(20L)!!
        val regenerated = original.copy(
            content = "Second answer",
            thoughts = "",
            revisions = listOf(saved) + original.revisions,
            activeRevisionIndex = ACTIVE_REVISION_LATEST
        )

        val latest = assistantRevisionSwitch(regenerated)!!
        assertEquals(2, latest.position)
        assertEquals(2, latest.total)
        assertEquals("< 2/2 >", branchSwitcherLabel(latest.position, latest.total))
        assertEquals("Second answer", regenerated.effectiveContent())

        val previous = regenerated.selectRevision(0)
        val older = assistantRevisionSwitch(previous)!!
        assertEquals("< 1/2 >", branchSwitcherLabel(older.position, older.total))
        assertEquals("First answer", previous.effectiveContent())
        assertEquals("First thoughts", previous.effectiveThoughts())
    }

    @Test
    fun `three revisions use the 2 of 3 switcher for the middle branch`() {
        val message = assistant(1, 1, "Newest").copy(
            revisions = listOf(
                AssistantRevision(content = "Middle", createdAt = 2L),
                AssistantRevision(content = "Oldest", createdAt = 1L)
            )
        )

        val middle = assistantRevisionSwitch(message.selectRevision(0))!!
        assertEquals("< 2/3 >", branchSwitcherLabel(middle.position, middle.total))
        assertEquals("Middle", message.selectRevision(0).effectiveContent())
        assertNull(assistantRevisionSwitch(assistant(1, 1, "Only")))
    }

    @Test
    fun `branch into new chat keeps the active path through the selected turn`() {
        val users = listOf(user(1, "One"), user(3, "Two"), user(5, "Three"))
        val assistants = listOf(
            listOf(assistant(2, 1, "A")),
            listOf(assistant(4, 3, "B")),
            listOf(assistant(6, 5, "C"))
        )

        assertEquals(setOf(1, 2, 3, 4), activePathMessageIds(users, assistants, turnIndex = 1, includeAssistantTurn = true).toSet())
        assertEquals(setOf(1, 2, 3), activePathMessageIds(users, assistants, turnIndex = 1, includeAssistantTurn = false).toSet())
        assertEquals("Trip branch", branchedChatTitle("Trip"))
    }

    @Test
    fun `unlinked assistant stays on the prefix when a later message branches`() {
        val messages = listOf(
            user(1, "Hello"),
            assistant(2, 0, "Hi"),
            user(3, "Next"),
            assistant(4, 3, "Sure")
        )

        val branched = applyUserEditBranch(
            messages = messages,
            editedUserId = 3,
            content = "Changed",
            attachments = emptyList(),
            timestamp = 11L
        )!!
        val active = activeTranscript(branched.messages)

        assertEquals("Hi", active.activeAssistants.first().single().content)
        assertEquals(1, active.activeAssistants.first().single().linkedMessageId)
    }

    @Test
    fun `persisted snapshots keep archived branch messages`() {
        val archived = user(3, "Old path")
        val grouped = ChatViewModel.GroupedMessages(
            userMessages = listOf(user(1, "Hello"), user(0, "Changed")),
            assistantMessages = listOf(
                listOf(assistant(2, 1, "Hi")),
                listOf(assistant(0, 0, ""))
            ),
            archivedMessages = listOf(archived, assistant(4, 3, "Old answer"))
        )

        val stored = persistableMessages(grouped).map { it.content }

        assertTrue(stored.contains("Old path"))
        assertTrue(stored.contains("Old answer"))
        assertTrue(stored.contains("Hello"))
    }

    private fun user(id: Int, content: String) = MessageV2(
        id = id,
        chatId = 7,
        content = content,
        platformType = null
    )

    private fun assistant(id: Int, linkedUserId: Int, content: String, platformUid: String = "profile") = MessageV2(
        id = id,
        chatId = 7,
        content = content,
        linkedMessageId = linkedUserId,
        platformType = platformUid
    )
}
