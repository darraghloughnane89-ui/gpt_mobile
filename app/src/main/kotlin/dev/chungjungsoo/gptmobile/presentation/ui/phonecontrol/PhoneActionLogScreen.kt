package dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PhoneActionLogViewModel @Inject constructor(
    repository: PhoneControlRepository
) : ViewModel() {
    val entries = repository.observeLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneActionLogScreen(
    onBack: () -> Unit,
    viewModel: PhoneActionLogViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_control_action_log)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) } }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Text(stringResource(R.string.phone_control_log_empty), modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(entry.summary)
                        Text("${entry.packageName} · ${entry.outcome}")
                    }
                }
            }
        }
    }
}
