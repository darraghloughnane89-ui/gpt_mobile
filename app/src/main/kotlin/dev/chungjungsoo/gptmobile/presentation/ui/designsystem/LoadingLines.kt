package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import dev.chungjungsoo.gptmobile.R

fun loadingLineIndex(seed: Int, count: Int): Int {
    if (count <= 0) return 0
    val remainder = seed % count
    return if (remainder < 0) remainder + count else remainder
}

@Composable
fun LoadingCaption(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val lines = stringArrayResource(R.array.loading_lines).toList()
    Text(
        text = lines[loadingLineIndex(seed, lines.size)],
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
