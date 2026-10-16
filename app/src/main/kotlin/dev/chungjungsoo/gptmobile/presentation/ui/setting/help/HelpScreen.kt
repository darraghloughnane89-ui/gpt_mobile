package dev.chungjungsoo.gptmobile.presentation.ui.setting.help

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigationClick: () -> Unit
) {
    HelpTheme {
        val context = LocalContext.current
        val guide = remember {
            runCatching {
                context.assets.open(SetupGuide.ASSET).bufferedReader().use { it.readText() }
            }.map(SetupGuide::parse).getOrNull()
        }
        val scrollState = rememberScrollState()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                HelpTopBar(
                    scrollBehavior = scrollBehavior,
                    navigationOnClick = onNavigationClick
                )
            }
        ) { innerPadding ->
            if (guide == null || guide.sections.isEmpty()) {
                HelpMissing(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                )
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .verticalScroll(scrollState)
                            .padding(horizontal = HelpSpace.lg)
                            .padding(top = HelpSpace.sm, bottom = HelpSpace.xxl)
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(HelpSpace.lg)
                    ) {
                        if (guide.title.isNotEmpty()) {
                            Text(
                                text = guide.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        guide.intro.forEach { block ->
                            GuideBlockView(block)
                        }
                        guide.sections.forEach { section ->
                            GuideSectionCard(section)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpMissing(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(HelpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HelpSpace.sm)
    ) {
        Text(
            text = stringResource(R.string.help_missing_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.help_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuideSectionCard(section: GuideSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(HelpSpace.lg),
            verticalArrangement = Arrangement.spacedBy(HelpSpace.md)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            section.blocks.forEach { block ->
                GuideBlockView(block)
            }
        }
    }
}

@Composable
private fun GuideBlockView(block: GuideBlock) {
    when (block) {
        is GuideBlock.Paragraph -> GuideText(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        is GuideBlock.Subheading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        is GuideBlock.Steps -> Column(verticalArrangement = Arrangement.spacedBy(HelpSpace.md)) {
            block.items.forEach { step ->
                GuideStepRow(step)
            }
        }
        is GuideBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(HelpSpace.sm)) {
            block.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(HelpSpace.sm)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    GuideText(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        is GuideBlock.Code -> GuideCode(block.text)
    }
}

@Composable
private fun GuideStepRow(step: GuideStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HelpSpace.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = step.number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = HelpSpace.sm, vertical = HelpSpace.xxs)
                .widthIn(min = HelpSpace.xl)
        )
        GuideText(
            text = step.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuideCode(text: String) {
    val scroll = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = HelpCodeStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(HelpSpace.md)
        )
    }
}

@Composable
private fun GuideText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val inlineCode = HelpCodeStyle.fontFamily ?: FontFamily.Monospace
    val annotated = remember(text, color, inlineCode) {
        guideAnnotatedString(text, color, inlineCode)
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        modifier = modifier
    )
}

private fun guideAnnotatedString(
    source: String,
    color: Color,
    codeFamily: FontFamily
): AnnotatedString = buildAnnotatedString {
    parseGuideInline(source).forEach { span ->
        val start = length
        append(span.text)
        if (span.strong) {
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, length)
        }
        if (span.code) {
            addStyle(
                SpanStyle(
                    fontFamily = codeFamily,
                    background = color.copy(alpha = 0.08f)
                ),
                start,
                length
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(HelpSpace.xs),
                text = stringResource(R.string.help),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(HelpSpace.xs),
                onClick = navigationOnClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
