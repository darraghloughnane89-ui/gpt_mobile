package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton

fun interface SkillClock {
    fun nowSeconds(): Long
}

@Singleton
class SystemSkillClock @Inject constructor() : SkillClock {
    override fun nowSeconds(): Long = System.currentTimeMillis() / 1000
}
