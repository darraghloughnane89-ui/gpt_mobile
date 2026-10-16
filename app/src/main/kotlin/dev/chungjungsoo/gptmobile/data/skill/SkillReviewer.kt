package dev.chungjungsoo.gptmobile.data.skill

fun interface SkillReviewer {
    suspend fun review(system: String, user: String): String
}
