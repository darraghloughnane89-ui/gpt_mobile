@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.chungjungsoo.gptmobile.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}

// Generate a profile on a connected Pixel 9 Pro:
// ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
// Macrobenchmarks (startup, chat scroll, streaming):
// ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.benchmark.StartupBenchmark
// Emulators need -Pabi.filters=x86_64 because the sideload APK is arm64-only.
baselineProfile {
    useConnectedDevices = true
}
