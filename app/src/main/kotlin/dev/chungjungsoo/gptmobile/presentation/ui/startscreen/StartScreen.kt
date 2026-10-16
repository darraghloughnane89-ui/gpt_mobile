package dev.chungjungsoo.gptmobile.presentation.ui.startscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Brand
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Mascot
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.VoiceOrb
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.VoiceOrbState

@Composable
fun StartScreen(onStartClick: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StartScreenLogo()
            Spacer(modifier = Modifier.weight(1f))
            WelcomeText()
            PrimaryLongButton(
                modifier = Modifier.padding(20.dp),
                onClick = onStartClick,
                text = stringResource(R.string.get_started)
            )
        }
    }
}

@Preview
@Composable
fun StartScreenLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 36.dp)
            .size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        VoiceOrb(
            amplitude = 0.15f,
            state = VoiceOrbState.Idle,
            modifier = Modifier.fillMaxSize(),
            contentDescription = stringResource(R.string.gpt_mobile_introduction_logo, Brand.name())
        )
        Mascot(mood = MascotMood.Wave, modifier = Modifier.size(120.dp))
    }
}

@Preview
@Composable
fun WelcomeText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = Brand.name(),
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.welcome_title, Brand.name()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
