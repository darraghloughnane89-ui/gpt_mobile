package dev.chungjungsoo.gptmobile.data.portable

import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a preview the person already confirmed.
 * New ids are minted here. Built-in flags, platform ids, connection ids,
 * tokens, and memory are not accepted from the document.
 */
@Singleton
class PortableImporter @Inject constructor(
    private val botRepository: BotRepository,
    private val library: PortableLibraryStore
) {
    suspend fun commit(preview: PortablePreview, now: Long = System.currentTimeMillis() / 1000): String {
        val id = UUID.randomUUID().toString()
        when (preview) {
            is PortablePreview.Bot -> {
                val (bot, bindings) = preview.toEntities(id, now)
                botRepository.save(bot, bindings)
                library.rememberImportedBot(id)
            }
            is PortablePreview.Skill -> library.upsertSkill(preview.toStored(id, now))
            is PortablePreview.Prompt -> library.upsertPrompt(preview.toStored(id, now))
        }
        return id
    }
}
