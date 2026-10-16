package dev.chungjungsoo.gptmobile.presentation.ui.skill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.brand.AppBrand
import dev.chungjungsoo.gptmobile.data.skill.SkillCodec
import dev.chungjungsoo.gptmobile.data.skill.SkillDraft
import dev.chungjungsoo.gptmobile.data.skill.SkillProposal
import dev.chungjungsoo.gptmobile.data.skill.SkillProposalAction

@Composable
fun SkillInboxRoute(
    viewModel: SkillInboxViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SkillReviewInboxScreen(
        proposals = state.proposals,
        autoAccept = state.autoAccept,
        message = state.message,
        onAutoAcceptChange = viewModel::setAutoAccept,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onBack = onBack,
        onConsumeMessage = viewModel::consumeMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillReviewInboxScreen(
    proposals: List<SkillProposal>,
    autoAccept: Boolean,
    message: String?,
    onAutoAcceptChange: (Boolean) -> Unit,
    onApprove: (SkillProposal, SkillDraft) -> Unit,
    onReject: (Long) -> Unit,
    onBack: () -> Unit,
    onConsumeMessage: () -> Unit
) {
    val scroll = rememberScrollState()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.skill_inbox)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.skill_inbox_description, AppBrand.NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.skill_auto_accept))
                Switch(checked = autoAccept, onCheckedChange = onAutoAcceptChange)
            }
            message?.let { text ->
                Text(text = text, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onConsumeMessage) { Text(stringResource(R.string.confirm)) }
            }
            if (proposals.isEmpty()) {
                Text(stringResource(R.string.skill_inbox_empty))
            }
            proposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onApprove = { draft -> onApprove(proposal, draft) },
                    onReject = { onReject(proposal.id) }
                )
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: SkillProposal,
    onApprove: (SkillDraft) -> Unit,
    onReject: () -> Unit
) {
    var description by remember(proposal.id) { mutableStateOf(proposal.proposedDescription) }
    var instructions by remember(proposal.id) { mutableStateOf(proposal.proposedInstructions) }
    val memories = SkillCodec.memories(proposal)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = proposalTitle(proposal),
            style = MaterialTheme.typography.titleMedium
        )
        if (proposal.rationale.isNotBlank()) {
            Text(text = proposal.rationale, style = MaterialTheme.typography.bodyMedium)
        }
        if (proposal.action != SkillProposalAction.MEMORY && proposal.action != SkillProposalAction.RETIRE) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_when_to_use)) }
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_procedure)) },
                minLines = 4
            )
        }
        if (memories.isNotEmpty()) {
            Text(text = stringResource(R.string.skill_memory_facts), style = MaterialTheme.typography.labelLarge)
            memories.forEach { fact -> Text(text = fact, style = MaterialTheme.typography.bodyMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onApprove(
                        SkillDraft(
                            name = proposal.proposedName,
                            title = proposal.proposedName,
                            description = description,
                            instructions = instructions,
                            tools = SkillCodec.tools(proposal),
                            examples = SkillCodec.examples(proposal)
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.skill_approve))
            }
            TextButton(onClick = onReject) { Text(stringResource(R.string.skill_reject)) }
        }
    }
}

@Composable
private fun proposalTitle(proposal: SkillProposal): String {
    val name = proposal.proposedName.ifBlank { stringResource(R.string.skill_memory_facts) }
    return stringResource(R.string.skill_proposal_title, proposal.action, name)
}
