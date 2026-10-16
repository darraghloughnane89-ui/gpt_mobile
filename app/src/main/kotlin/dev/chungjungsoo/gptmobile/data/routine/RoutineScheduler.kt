package dev.chungjungsoo.gptmobile.data.routine

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RoutineClock {
    fun nowMillis(): Long
    fun zone(): ZoneId
}

class SystemRoutineClock @Inject constructor() : RoutineClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

interface RoutineWorkQueue {
    fun enqueue(routineUid: String, delayMillis: Long)
    fun cancel(routineUid: String)
}

@Singleton
class WorkManagerRoutineQueue @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RoutineWorkQueue {
    override fun enqueue(routineUid: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<RoutineWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RoutineWorker.KEY_ROUTINE_UID to routineUid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(routineUid), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(routineUid: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(routineUid))
    }

    companion object {
        const val WORK_TAG = "routines"
        fun workName(routineUid: String) = "routine-$routineUid"
    }
}

fun RoutineEntity.toScheduleSpec(): RoutineScheduleSpec = RoutineScheduleSpec(
    kind = ScheduleKind.valueOf(scheduleKind),
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    cron = cronExpression
)

@Singleton
class RoutineScheduler @Inject constructor(
    private val routines: PluginRoutineDao,
    private val queue: RoutineWorkQueue,
    private val clock: RoutineClock
) {
    suspend fun restore() {
        routines.enabledRoutines().forEach { schedule(it) }
    }

    fun schedule(routine: RoutineEntity) {
        if (!routine.enabled) {
            queue.cancel(routine.routineUid)
            return
        }
        val delay = RoutineSchedule.delayUntilNext(routine.toScheduleSpec(), clock.nowMillis(), clock.zone())
        queue.enqueue(routine.routineUid, delay)
    }

    suspend fun scheduleNext(routineUid: String) {
        val routine = routines.getRoutine(routineUid) ?: return
        schedule(routine)
    }

    fun cancel(routineUid: String) {
        queue.cancel(routineUid)
    }
}
