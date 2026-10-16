package dev.chungjungsoo.gptmobile.data.skill

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningLoopTest {
    @Test
    fun review_shortChat_skipsTheModel() = runTest {
        val reviewer = FakeSkillReviewer("""{"skills":[],"memories":[]}""")
        val loop = loop(reviewer)

        val result = loop.review(
            LearningRequest(
                chatId = 1,
                turns = listOf(LearningTurn("user", "hi"), LearningTurn("assistant", "hello")),
                explicit = false
            )
        )

        assertTrue(result is LearningResult.Skipped)
        assertEquals(0, reviewer.calls)
    }

    @Test
    fun review_explicit_filesAPendingProposalAndDoesNotSaveTheSkill() = runTest {
        val store = MemorySkillStore()
        val memory = RecordingMemoryWriter()
        val reviewer = FakeSkillReviewer(
            """{"skills":[{"action":"create","name":"deploy-staging","description":"Deploy staging","instructions":"Check /health.","tools":[],"examples":[],"rationale":"multi-step"}],"memories":[{"text":"Host is staging.example"}]}"""
        )
        val loop = loop(reviewer, store, memory)

        val result = loop.review(longRequest(explicit = true))

        val proposed = result as LearningResult.Proposed
        assertEquals(1, proposed.count)
        assertEquals(0, proposed.autoAccepted)
        assertTrue(store.listSkills().isEmpty())
        assertEquals(1, store.listProposals(SkillProposalStatus.PENDING).size)
        assertTrue(memory.facts.isEmpty())
        val pending = store.listProposals(SkillProposalStatus.PENDING).single()
        assertEquals(listOf("Host is staging.example"), SkillCodec.memories(pending))
    }

    @Test
    fun review_autoAccept_savesTheSkillAndWritesMemory() = runTest {
        val store = MemorySkillStore()
        store.saveSettings(SkillSettings(autoAccept = true))
        val memory = RecordingMemoryWriter()
        val reviewer = FakeSkillReviewer(
            """{"skills":[{"action":"create","name":"deploy-staging","description":"Deploy staging","instructions":"Check /health.","tools":["shell"],"examples":["ship"],"rationale":"worked"}],"memories":[{"text":"Host is staging.example"}]}"""
        )
        val loop = loop(reviewer, store, memory)

        val result = loop.review(longRequest(explicit = false)) as LearningResult.Proposed

        assertEquals(1, result.autoAccepted)
        val skill = store.listSkills().single()
        assertEquals("deploy-staging", skill.stableName)
        assertEquals(SkillOrigin.LEARNED, skill.origin)
        assertEquals(listOf("Host is staging.example"), memory.facts.map { it.text })
        assertEquals(SkillProposalStatus.ACCEPTED, store.listProposals(null).single().status)
    }

    @Test
    fun review_autoAcceptPinnedRetire_staysInTheInbox() = runTest {
        val store = MemorySkillStore()
        store.saveSettings(SkillSettings(autoAccept = true))
        val id = store.insertSkill(
            Skill(
                stableName = "deploy-staging",
                title = "Deploy",
                description = "Deploy staging",
                instructions = "Check /health.",
                pinned = true,
                createdAt = 1,
                updatedAt = 1
            )
        )
        val reviewer = FakeSkillReviewer(
            """{"skills":[{"action":"retire","name":"deploy-staging","description":"Deploy staging","instructions":"Check /health.","rationale":"stale"}],"memories":[]}"""
        )
        val loop = loop(reviewer, store)

        loop.review(longRequest(explicit = true))

        assertEquals(id, store.getByName("deploy-staging")?.id)
        assertEquals(SkillProposalStatus.PENDING, store.listProposals(null).single().status)
    }

    @Test
    fun review_garbageModelOutputOnABackgroundPass_savesNothing() = runTest {
        val store = MemorySkillStore()
        val reviewer = FakeSkillReviewer("I learned a lot but forgot the format")
        val loop = loop(reviewer, store)

        val result = loop.review(longRequest(explicit = false))

        assertTrue(result is LearningResult.Skipped)
        assertTrue(store.listProposals(null).isEmpty())
    }

    private fun loop(
        reviewer: SkillReviewer,
        store: SkillStore = MemorySkillStore(),
        memory: MemoryWriter = RecordingMemoryWriter()
    ): LearningLoop {
        val clock = FixedSkillClock()
        return LearningLoop(store, reviewer, SkillApplier(store, memory, clock), clock)
    }

    private fun longRequest(explicit: Boolean) = LearningRequest(
        chatId = 9,
        turns = listOf(
            LearningTurn("user", "Walk me through the staging deploy and tell me the host you used for the health check."),
            LearningTurn("assistant", "I built the artifact, deployed it, and curled /health on staging.example."),
            LearningTurn("user", "Remember that host and the order of those steps for next time.")
        ),
        explicit = explicit
    )
}
