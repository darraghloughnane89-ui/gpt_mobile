package dev.chungjungsoo.gptmobile.data.skill

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRepositoryTest {
    @Test
    fun save_duplicate_import_andFeedback_roundTrip() = runTest {
        val repository = repository()
        val created = repository.save(
            null,
            SkillDraft(
                name = "Deploy Staging",
                title = "Deploy staging",
                description = "Deploy the staging server",
                instructions = "Check /health.",
                tools = listOf("shell"),
                examples = listOf("ship staging")
            )
        ) as SkillWriteResult.Ok

        val copy = repository.duplicate(created.skill.id) as SkillWriteResult.Ok
        assertEquals("deploy-staging-copy", copy.skill.stableName)
        assertEquals(0, copy.skill.useCount)

        val imported = repository.importMarkdown(SkillMarkdown.export(created.skill.copy(stableName = "other-skill", title = "Other")))
        assertTrue(imported is SkillWriteResult.Ok)

        repository.recordUse(listOf(created.skill.id, created.skill.id))
        repository.recordFeedback(listOf(created.skill.id), up = true)
        repository.recordFeedback(listOf(created.skill.id), up = false)
        val updated = repository.get(created.skill.id)!!
        assertEquals(1, updated.useCount)
        assertEquals(1, updated.thumbsUp)
        assertEquals(1, updated.thumbsDown)

        repository.save(created.skill.id, created.skill.toDraft().copy(pinned = true))
        val blocked = repository.delete(created.skill.id)
        assertTrue(blocked is SkillWriteResult.Invalid)
        assertEquals(3, repository.listSkills().size)
    }

    @Test
    fun botLinks_replaceAndResolveAssignedSkills() = runTest {
        val repository = repository()
        val first = (repository.save(null, draft("alpha")) as SkillWriteResult.Ok).skill
        val second = (repository.save(null, draft("beta")) as SkillWriteResult.Ok).skill

        repository.replaceBotSkills("bot-1", listOf(first.id, second.id, 99))
        assertEquals(listOf(first.id, second.id), repository.skillsForBot("bot-1").map { it.id })

        repository.unlinkSkill("bot-1", first.id)
        assertEquals(listOf(second.id), repository.skillsForBot("bot-1").map { it.id })
    }

    @Test
    fun accept_pendingProposal_writesTheSkillAndMemory() = runTest {
        val store = MemorySkillStore()
        val memory = RecordingMemoryWriter()
        val clock = FixedSkillClock()
        val repository = SkillRepository(store, SkillApplier(store, memory, clock), clock)
        val proposalId = store.insertProposal(
            SkillProposal(
                action = SkillProposalAction.CREATE,
                proposedName = "deploy-staging",
                proposedDescription = "Deploy staging",
                proposedInstructions = "Check /health.",
                memoryFactsJson = SkillCodec.encodeList(listOf("Host is staging.example")),
                source = SkillProposalSource.SAVE_AS_SKILL,
                createdAt = clock.nowSeconds()
            )
        )

        val result = repository.accept(proposalId, null)

        assertTrue(result is ApplyResult.Saved)
        assertEquals("deploy-staging", store.listSkills().single().stableName)
        assertEquals("Host is staging.example", memory.facts.single().text)
    }

    private fun repository(): SkillRepository {
        val store = MemorySkillStore()
        val clock = FixedSkillClock()
        return SkillRepository(store, SkillApplier(store, RecordingMemoryWriter(), clock), clock)
    }

    private fun draft(name: String) = SkillDraft(
        name = name,
        title = name,
        description = "Use $name",
        instructions = "Do $name."
    )
}
