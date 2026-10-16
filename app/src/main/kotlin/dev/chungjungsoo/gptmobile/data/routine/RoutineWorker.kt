package dev.chungjungsoo.gptmobile.data.routine

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RoutineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: RoutineRunner,
    private val scheduler: RoutineScheduler
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val routineUid = inputData.getString(KEY_ROUTINE_UID) ?: return Result.failure()
        runner.run(routineUid)
        scheduler.scheduleNext(routineUid)
        return Result.success()
    }

    companion object {
        const val KEY_ROUTINE_UID = "routine_uid"
    }
}
