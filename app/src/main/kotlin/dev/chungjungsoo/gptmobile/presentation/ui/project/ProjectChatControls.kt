package dev.chungjungsoo.gptmobile.presentation.ui.project

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.project.ProjectFilter

@Composable
fun ProjectFilterBar(
    projects: List<Project>,
    filter: ProjectFilter,
    onFilter: (ProjectFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filter == ProjectFilter.All,
            onClick = { onFilter(ProjectFilter.All) },
            label = { Text(stringResource(R.string.project_filter_all)) }
        )
        FilterChip(
            selected = filter == ProjectFilter.Ungrouped,
            onClick = { onFilter(ProjectFilter.Ungrouped) },
            label = { Text(stringResource(R.string.project_filter_ungrouped)) }
        )
        projects.forEach { project ->
            FilterChip(
                selected = filter is ProjectFilter.Project && filter.id == project.id,
                onClick = { onFilter(ProjectFilter.Project(project.id)) },
                label = { Text(project.name) }
            )
        }
    }
}

@Composable
fun MoveToProjectDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_project)) },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) {
                    Text(stringResource(R.string.project_filter_ungrouped))
                }
                projects.forEach { project ->
                    TextButton(onClick = { onSelect(project.id) }) {
                        Text(project.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
