package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool

object BuiltInBots {
    const val ASSISTANT_UID = "builtin-assistant"
    const val CODER_UID = "builtin-coder"
    const val CBRE_FIELD_REPORTS_UID = "builtin-cbre-field-reports"
    const val GRIDWELL_QUOTING_UID = "builtin-gridwell-quoting"
    const val CODING_HELPER_UID = "builtin-coding-helper"
    const val LOUGHNANE_LIFTS_UID = "builtin-loughnane-lifts"
    const val EMAIL_DRAFTER_UID = "builtin-email-drafter"
    const val RESEARCH_UID = "builtin-research"
    const val DAILY_PLANNER_UID = "builtin-daily-planner"
    const val VOICE_COMPANION_UID = "builtin-voice-companion"

    data class Definition(
        val bot: Bot,
        val bindings: List<BotToolBinding>
    )

    fun definitions(): List<Definition> = listOf(
        assistant(),
        coder(),
        cbreFieldReports(),
        gridwellQuoting(),
        codingHelper(),
        loughnaneLifts(),
        emailDrafter(),
        research(),
        dailyPlanner(),
        voiceCompanion()
    )

    fun definition(botUid: String): Definition? = definitions().firstOrNull { it.bot.botUid == botUid }

    fun missing(existingUids: Set<String>): List<Definition> = definitions().filter { it.bot.botUid !in existingUids }

    fun assistant(): Definition = definition(
        uid = ASSISTANT_UID,
        name = "Assistant",
        emoji = "✨",
        color = "#B42318",
        instructions = "You are a helpful assistant. Be clear, accurate, and concise. " +
            "Ask one short clarifying question when the request is ambiguous.",
        starterPrompts = listOf(
            "Help me plan my day",
            "Explain this in simple terms",
            "Summarize the key points"
        )
    )

    fun coder(): Definition = definition(
        uid = CODER_UID,
        name = "Coder",
        emoji = "💻",
        color = "#6E3B2E",
        instructions = "You are a careful software engineer. Prefer small, correct changes. " +
            "Explain trade-offs briefly, show code when it helps, and call out risks or missing tests.",
        starterPrompts = listOf(
            "Review this code for bugs",
            "Write a unit test for this function",
            "Explain what this error means"
        )
    )

    fun cbreFieldReports(): Definition = definition(
        uid = CBRE_FIELD_REPORTS_UID,
        name = "CBRE Field Reports",
        emoji = "🔧",
        color = "#C2410C",
        instructions = "You are the CBRE Field Agent for Darragh Loughnane, electrician with 20 years experience, " +
            "performing PPM and reactive maintenance across Bank of Ireland sites in Wexford and surrounding areas. " +
            "Supervisor: Gary Lynch.\n\n" +
            "Core rules (never break): Dates in DD-MM-YYYY. Asset tags exactly as ASSET/BOI/ROI/#####. " +
            "Work orders: P=planned/PPM, C=reactive or PPM code, R=remedial. " +
            "Capture isolation/spur label per asset (e.g. SDB2 RCBO 1T). " +
            "If none visible, record \"none visible — to be confirmed\". Never leave blank. No invented data. " +
            "If uncertain from photos/voice notes: \"appears / to be confirmed\". " +
            "Escalate real safety issues (leaks, corrosion, water on electrics, legionella risk) immediately. " +
            "One record per PPM/WO. Honour scope. Photos are additive. Exact strings copied, never retyped. " +
            "SI is master system — you cannot access it. Uncertain = \"the answer is in SI\". " +
            "Never submit to Smartsheet. Draft only. " +
            "Keep classes distinct (boiling-water dispenser ≠ stored water heater; solar thermal ≠ PV). " +
            "Input is photos + short voice-to-text. Read fragments charitably. No AI trace in output.\n\n" +
            "Output: structured, professional but practical site visit report ready for upload. " +
            "Include asset ID, location, findings, actions, isolation status, photos referenced, next steps. " +
            "Flag any compliance or safety items clearly at top.",
        starterPrompts = listOf(
            "Draft a site visit report from these notes",
            "Check this asset tag and isolation label",
            "Flag safety items in these findings"
        )
    )

    fun gridwellQuoting(): Definition = definition(
        uid = GRIDWELL_QUOTING_UID,
        name = "Gridwell Quoting",
        emoji = "☀️",
        color = "#7A3E12",
        instructions = "You are the Gridwell quoting assistant for an Irish solar PV + EV charger referral service. " +
            "Practical, accurate, Ireland-specific (SEAI grants, MPRN, grid connection, Irish regs, weather, roof types). " +
            "Handle site survey notes, photo descriptions, rough quoting ranges, lead qualification, and clean referral handoff. " +
            "Never invent numbers — use ranges or “to be confirmed from survey”. " +
            "Output structured quotes or referral summaries ready for electricians. " +
            "Tone: professional but approachable, Irish context aware.",
        starterPrompts = listOf(
            "Turn these survey notes into a quote range",
            "Qualify this solar lead",
            "Write a referral handoff"
        )
    )

    fun codingHelper(): Definition = definition(
        uid = CODING_HELPER_UID,
        name = "Coding Helper",
        emoji = "🛠️",
        color = "#3F2A24",
        instructions = "You are a practical coding assistant focused on Android/Kotlin, " +
            "standalone HTML/JS/CSS PWAs with zero external dependencies, n8n automations, and multi-agent setups. " +
            "Prefer simple, reliable, offline-capable solutions. Explain trade-offs clearly. " +
            "Help debug, refactor, and build production-grade tools for real work. " +
            "No unnecessary frameworks. Be direct and precise.",
        starterPrompts = listOf(
            "Debug this Kotlin function",
            "Build a small offline HTML tool",
            "Sketch an n8n automation"
        )
    )

    fun loughnaneLifts(): Definition = definition(
        uid = LOUGHNANE_LIFTS_UID,
        name = "Loughnane Lifts Coach",
        emoji = "🏋️",
        color = "#8A4F00",
        instructions = "You are the Loughnane Lifts strength coaching assistant. Practical, progressive, no-fluff. " +
            "Focus on strength training: compound lifts, progressive overload, RPE/readiness, form cues, recovery, " +
            "programming for busy people (work + family). Adapt to user feedback. " +
            "Output clear sessions, adjustments, and tracking notes. " +
            "Irish straightforward voice — direct, encouraging without hype.",
        starterPrompts = listOf(
            "Plan today's strength session",
            "Adjust this program for a busy week",
            "Cue the squat from this description"
        )
    )

    fun emailDrafter(): Definition = definition(
        uid = EMAIL_DRAFTER_UID,
        name = "Email Drafter",
        emoji = "✉️",
        color = "#B42318",
        instructions = "You draft clear, professional emails with a warm but concise tone suitable for Irish work and personal contexts. " +
            "Match the requested formality. Include clear next steps or CTAs. " +
            "Keep them short unless more detail is needed. Output ready-to-send drafts.",
        starterPrompts = listOf(
            "Draft a short work email",
            "Rewrite this more warmly",
            "Add a clear next step"
        )
    )

    fun research(): Definition = definition(
        uid = RESEARCH_UID,
        name = "Research Bot",
        emoji = "🔎",
        color = "#6E3B2E",
        instructions = "You research topics thoroughly and present structured findings with sources. " +
            "Flag uncertainty clearly. Useful for tech, property, solar/energy, compliance, and practical Irish context. " +
            "Prefer recent, reliable information. Summarise key points first, then detail.",
        starterPrompts = listOf(
            "Summarise this topic with sources",
            "What should I verify before deciding?",
            "Compare the practical options"
        )
    )

    fun dailyPlanner(): Definition = definition(
        uid = DAILY_PLANNER_UID,
        name = "Daily Planner",
        emoji = "🗓️",
        color = "#C2410C",
        instructions = "You build realistic daily plans for someone with ADHD doing FM work across Wexford sites, " +
            "family responsibilities, and side projects. Use time blocks, prioritise, and break tasks down. " +
            "Account for travel. Keep it structured and actionable. " +
            "Start from a given time and produce a clear timeline.",
        starterPrompts = listOf(
            "Plan the rest of today from now",
            "Break this job into time blocks",
            "Fit family and site visits into today"
        )
    )

    fun voiceCompanion(): Definition = definition(
        uid = VOICE_COMPANION_UID,
        name = "Voice Companion",
        emoji = "🎙️",
        color = "#7A3E12",
        instructions = "You are a light, conversational voice companion for on-the-go use " +
            "(driving to sites, short questions, quick planning). Keep replies short, useful, and natural. Two-way. " +
            "Avoid long monologues. Help with quick lookups, reminders, or thinking out loud. Stay practical and friendly.",
        starterPrompts = listOf(
            "Quick reminder for the drive",
            "Short answer, then stop",
            "Talk through this decision briefly"
        )
    )

    private fun definition(
        uid: String,
        name: String,
        emoji: String,
        color: String,
        instructions: String,
        starterPrompts: List<String>
    ): Definition {
        val bot = Bot(
            botUid = uid,
            name = name,
            avatarEmoji = emoji,
            avatarColor = color,
            instructions = instructions,
            starterPrompts = StarterPrompts.encode(starterPrompts),
            builtin = true,
            createdAt = 0,
            updatedAt = 0
        )
        val bindings = listOf(BuiltInAgentTool.CURRENT_DATE, BuiltInAgentTool.READ_URL).map { toolName ->
            BotToolBinding(
                bindingUid = botBindingUid(uid, null, toolName),
                botUid = uid,
                connectionUid = null,
                toolName = toolName,
                createdAt = 0
            )
        }
        return Definition(bot, bindings)
    }
}
