package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun frostTopAppBarColors(selection: Boolean = false): TopAppBarColors {
    val background = MaterialTheme.colorScheme.background
    val selectionContainer = MaterialTheme.colorScheme.primaryContainer
    return TopAppBarDefaults.topAppBarColors(
        containerColor = if (selection) selectionContainer else background.copy(alpha = 0.78f),
        scrolledContainerColor = if (selection) selectionContainer else background.copy(alpha = 0.94f),
        titleContentColor = if (selection) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onBackground
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigation: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationDescription: String? = null,
    onCommand: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Box(modifier = modifier) {
        TopAppBar(
            title = {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                if (onNavigation != null) {
                    IconButton(onClick = onNavigation) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = navigationDescription
                        )
                    }
                }
            },
            actions = {
                if (onCommand != null) {
                    IconButton(onClick = onCommand) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.command_title)
                        )
                    }
                }
            },
            colors = frostTopAppBarColors(),
            scrollBehavior = scrollBehavior
        )
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.0f))
                    )
                )
        )
    }
}
