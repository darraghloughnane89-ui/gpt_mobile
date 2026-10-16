package dev.chungjungsoo.gptmobile.presentation.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectContextLimits
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons

@Composable
fun ProjectEditorForm(
    name: String,
    instructions: String,
    icon: String,
    color: Int,
    onNameChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.project_name)) },
            singleLine = true,
            supportingText = { Text("${name.length}/${ProjectContextLimits.MAX_NAME_CHARS}") }
        )
        Text(text = stringResource(R.string.project_icon), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProjectIcons.all.forEach { key ->
                IconButton(onClick = { onIconChange(key) }) {
                    Icon(
                        imageVector = ProjectIcons.image(key),
                        contentDescription = key,
                        tint = if (key == icon) ProjectColors.color(color) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(text = stringResource(R.string.project_color), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectColors.palette.forEach { swatch ->
                val selected = swatch == color
                val description = stringResource(
                    R.string.project_color_swatch,
                    "#%06X".format(swatch and 0xFFFFFF)
                )
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ProjectColors.color(swatch))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable(role = Role.RadioButton) { onColorChange(swatch) }
                        .semantics {
                            contentDescription = description
                            role = Role.RadioButton
                            if (selected) this.selected = true
                        }
                )
            }
        }
        OutlinedTextField(
            value = instructions,
            onValueChange = onInstructionsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.project_instructions)) },
            minLines = 5,
            supportingText = {
                Text(
                    stringResource(
                        R.string.project_instructions_limit,
                        instructions.length,
                        ProjectContextLimits.MAX_INSTRUCTION_CHARS
                    )
                )
            }
        )
    }
}
