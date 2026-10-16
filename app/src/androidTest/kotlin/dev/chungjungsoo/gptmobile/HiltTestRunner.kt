package dev.chungjungsoo.gptmobile

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Boots instrumented tests with Hilt so smoke tests can inject DAOs and launch
 * [dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity].
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
