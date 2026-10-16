package dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlRepository
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.FinanceDenylist
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LaunchableApp(val packageName: String, val label: String)

@HiltViewModel
class PhoneAllowlistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: PhoneControlRepository
) : ViewModel() {
    val apps = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        PackageManager.MATCH_DEFAULT_ONLY
    ).map { resolve ->
        LaunchableApp(
            packageName = resolve.activityInfo.packageName,
            label = resolve.loadLabel(context.packageManager).toString()
        )
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

    val allowed = repository.observeAllowlist().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(app: LaunchableApp, allowedNow: Boolean) {
        viewModelScope.launch { repository.setAllowed(app.packageName, app.label, allowedNow) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAllowlistScreen(
    onBack: () -> Unit,
    viewModel: PhoneAllowlistViewModel = hiltViewModel()
) {
    val allowed by viewModel.allowed.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_control_allowlist)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(viewModel.apps, key = { it.packageName }) { app ->
                val checked = allowed.any { it.packageName == app.packageName && it.allowed }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(app.label)
                    Text(app.packageName)
                    if (FinanceDenylist.isFinance(app.packageName, app.label)) {
                        Text(stringResource(R.string.phone_control_finance_note))
                    }
                    Switch(checked = checked, onCheckedChange = { viewModel.toggle(app, it) })
                }
            }
        }
    }
}
