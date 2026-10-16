package dev.chungjungsoo.gptmobile.data.routine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import dev.chungjungsoo.gptmobile.data.plugin.AgentReplySource
import dev.chungjungsoo.gptmobile.data.plugin.PluginToolFilter
import dev.chungjungsoo.gptmobile.data.plugin.UnattendedToolCall
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object RoutineRunStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

@Singleton
class RoutineNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun show(title: String, body: String, chatId: Int?, enabledPlatforms: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.routine_notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (chatId != null && chatId > 0) {
                putExtra(RoutineChatLaunch.EXTRA_CHAT_ID, chatId)
                putExtra(RoutineChatLaunch.EXTRA_ENABLED_PLATFORMS, enabledPlatforms)
            }
        }
        val launch = PendingIntent.getActivity(
            context,
            chatId ?: title.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setContentTitle(title)
            .setContentText(body.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "routines"
    }
}

@Singleton
class RoutineRunner @Inject constructor(
    private val routines: PluginRoutineDao,
    private val platforms: PlatformV2Dao,
    private val chatRooms: ChatRoomV2Dao,
    private val messages: MessageV2Dao,
    private val replies: AgentReplySource,
    private val notifier: RoutineNotifier,
    private val weather: WeatherClient,
    private val templateSettings: RoutineTemplateSettings
) {
    suspend fun run(routineUid: String) {
        val routine = routines.getRoutine(routineUid) ?: return
        val started = System.currentTimeMillis() / 1000
        val runUid = UUID.randomUUID().toString()
        var chatId: Int? = routine.chatId
        try {
            val platform = platforms.getPlatforms().firstOrNull { it.uid == routine.platformUid }
                ?: throw IllegalStateException("The routine's platform is no longer available.")
            val openedChat = ensureChat(routine)
            chatId = openedChat
            val task = assembleRoutineTask(routine.prompt, weatherFor(routine))
            val userId = messages.insertMessage(
                MessageV2(chatId = openedChat, content = task, platformType = null)
            ).toInt()
            val pluginIds = routine.pluginUids.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val reply = withContext(UnattendedToolCall() + PluginToolFilter(pluginIds)) {
                replies.reply(platform.uid, task)
            }
            messages.insertMessage(
                MessageV2(
                    chatId = openedChat,
                    content = reply,
                    platformType = platform.uid,
                    linkedMessageId = userId
                )
            )
            routines.insertRun(
                RoutineRunEntity(
                    runUid = runUid,
                    routineUid = routine.routineUid,
                    status = RoutineRunStatus.SUCCESS,
                    resultText = reply,
                    errorText = null,
                    chatId = openedChat,
                    startedAt = started,
                    finishedAt = System.currentTimeMillis() / 1000
                )
            )
            notifier.show(
                title = routine.title,
                body = reply.ifBlank { "The routine finished with an empty reply." },
                chatId = openedChat,
                enabledPlatforms = routine.platformUid
            )
        } catch (error: Exception) {
            routines.insertRun(
                RoutineRunEntity(
                    runUid = runUid,
                    routineUid = routine.routineUid,
                    status = RoutineRunStatus.FAILED,
                    resultText = null,
                    errorText = error.message,
                    chatId = chatId,
                    startedAt = started,
                    finishedAt = System.currentTimeMillis() / 1000
                )
            )
            notifier.show(
                title = routine.title,
                body = error.message ?: "The routine failed.",
                chatId = chatId,
                enabledPlatforms = routine.platformUid
            )
        }
    }

    private suspend fun weatherFor(routine: RoutineEntity): String? {
        if (RoutineTemplates.find(routine.routineUid)?.weather != true) return null
        val saved = templateSettings.observe().first()[routine.routineUid]?.location?.trim()?.takeIf { it.isNotEmpty() }
        val place = saved ?: RoutineTemplates.DEFAULT_WEATHER_PLACE
        return runCatching { weather.forecast(place) }
            .getOrElse { error -> "Weather lookup failed for $place. ${error.message ?: "Try again later."}" }
    }

    private suspend fun ensureChat(routine: RoutineEntity): Int {
        routine.chatId?.let { return it }
        val id = chatRooms.addChatRoom(
            ChatRoomV2(
                title = "Routine: ${routine.title}",
                enabledPlatform = listOf(routine.platformUid)
            )
        ).toInt()
        routines.updateRoutineChat(routine.routineUid, id, System.currentTimeMillis() / 1000)
        return id
    }
}
