package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.crash.LocalCrashLog
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CrashLogViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val log = LocalCrashLog(File(context.filesDir, LocalCrashLog.DIRECTORY_NAME))

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _text.value = withContext(Dispatchers.IO) { log.readText() }
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { log.clear() }
            _text.value = ""
        }
    }

    suspend fun exportFile(): File = withContext(Dispatchers.IO) {
        val destination = File(context.cacheDir, "crash/${LocalCrashLog.FILE_NAME}")
        log.copyTo(destination)
        destination
    }
}
