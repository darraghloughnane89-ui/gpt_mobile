package dev.chungjungsoo.gptmobile.presentation.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.usage.ModelPrice
import dev.chungjungsoo.gptmobile.data.usage.ModelUsageTotal
import dev.chungjungsoo.gptmobile.data.usage.PeriodUsageTotal
import dev.chungjungsoo.gptmobile.data.usage.UsageCost
import dev.chungjungsoo.gptmobile.data.usage.UsageSummary
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.PrivacyScaffold

@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val priceError by viewModel.priceError.collectAsStateWithLifecycle()
    UsageScreenContent(
        summary = summary,
        prices = prices,
        priceError = priceError != null,
        onBack = onBack,
        onSavePrice = viewModel::savePrice,
        onConsumePriceError = viewModel::consumePriceError
    )
}

@Composable
fun UsageScreenContent(
    summary: UsageSummary,
    prices: List<ModelPrice>,
    priceError: Boolean,
    onBack: () -> Unit,
    onSavePrice: (String, String, String) -> Unit,
    onConsumePriceError: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var modelKey by rememberSaveable { mutableStateOf("") }
    var inputUsd by rememberSaveable { mutableStateOf("") }
    var outputUsd by rememberSaveable { mutableStateOf("") }

    PrivacyScaffold(
        title = stringResource(R.string.usage_title),
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.usage_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            SettingsSection(title = stringResource(R.string.usage_totals)) {
                UsageLine(
                    label = stringResource(R.string.usage_input_tokens),
                    value = summary.totalInputTokens.toString()
                )
                UsageLine(
                    label = stringResource(R.string.usage_output_tokens),
                    value = summary.totalOutputTokens.toString()
                )
                UsageLine(
                    label = stringResource(R.string.usage_cost),
                    value = UsageCost.formatUsd(summary.totalCostMicros)
                )
            }
            UsagePeriodSection(stringResource(R.string.usage_by_model), summary.byModel.map {
                PeriodUsageTotal(it.model, it.inputTokens, it.outputTokens, it.costMicros)
            }, summary.byModel)
            UsagePeriodSection(stringResource(R.string.usage_by_day), summary.byDay, emptyList())
            UsagePeriodSection(stringResource(R.string.usage_by_month), summary.byMonth, emptyList())
            SettingsSection(title = stringResource(R.string.usage_prices)) {
                Text(
                    text = stringResource(R.string.usage_prices_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                prices.forEach { price ->
                    SettingItem(
                        title = price.modelKey,
                        description = stringResource(
                            R.string.usage_price_row,
                            UsageCost.formatUsd(price.inputMicrosPerMillion),
                            UsageCost.formatUsd(price.outputMicrosPerMillion)
                        ),
                        onItemClick = {
                            editing = true
                            modelKey = price.modelKey
                            inputUsd = microsToEdit(price.inputMicrosPerMillion)
                            outputUsd = microsToEdit(price.outputMicrosPerMillion)
                        },
                        showTrailingIcon = true,
                        showLeadingIcon = false
                    )
                }
                SettingItem(
                    title = stringResource(R.string.usage_add_price),
                    onItemClick = {
                        adding = true
                        modelKey = ""
                        inputUsd = ""
                        outputUsd = ""
                    },
                    showTrailingIcon = false,
                    showLeadingIcon = false
                )
            }
        }
    }

    if (adding || editing) {
        AlertDialog(
            onDismissRequest = {
                adding = false
                editing = false
            },
            title = { Text(stringResource(R.string.usage_edit_price)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modelKey,
                        onValueChange = { modelKey = it },
                        enabled = adding,
                        label = { Text(stringResource(R.string.usage_model)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputUsd,
                        onValueChange = { inputUsd = it },
                        label = { Text(stringResource(R.string.usage_input_price)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = outputUsd,
                        onValueChange = { outputUsd = it },
                        label = { Text(stringResource(R.string.usage_output_price)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSavePrice(modelKey, inputUsd, outputUsd)
                    adding = false
                    editing = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    adding = false
                    editing = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (priceError) {
        AlertDialog(
            onDismissRequest = onConsumePriceError,
            title = { Text(stringResource(R.string.usage_price_invalid)) },
            text = { Text(stringResource(R.string.usage_price_invalid_body)) },
            confirmButton = {
                TextButton(onClick = onConsumePriceError) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun UsagePeriodSection(
    title: String,
    periods: List<PeriodUsageTotal>,
    modelDetails: List<ModelUsageTotal>
) {
    SettingsSection(title = title) {
        if (periods.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            periods.forEach { period ->
                val estimated = modelDetails.firstOrNull { it.model == period.label }?.estimatedEvents ?: 0
                val estimateNote = if (estimated > 0) {
                    " · " + stringResource(R.string.usage_estimated_count, estimated)
                } else {
                    ""
                }
                UsageLine(
                    label = period.label,
                    value = "${period.inputTokens} / ${period.outputTokens} · ${UsageCost.formatUsd(period.costMicros)}$estimateNote"
                )
            }
        }
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun microsToEdit(micros: Long): String {
    val text = String.format(java.util.Locale.US, "%.4f", micros / 1_000_000.0)
    return text.trimEnd('0').trimEnd('.')
}
