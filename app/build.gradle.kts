@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.kotlin.dsl.aboutLibraries
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.auto.license)
    kotlin(libs.plugins.kotlin.serialization.get().pluginId).version(libs.versions.kotlin)
}

// Last Play release is 0.9.1 (versionCode 25). Git history advances the code and a
// shallow checkout cannot move it backwards. APP_VERSION_CODE / app.versionCode win.
val publishedVersionCode = 25
val publishedVersionName = "0.9.1"

val sideloadAbis = providers.gradleProperty("abi.filters")
    .orElse(providers.environmentVariable("ABI_FILTERS"))
    .orElse("arm64-v8a")

val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE")
    .orElse(providers.gradleProperty("RELEASE_STORE_FILE"))
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD")
    .orElse(providers.gradleProperty("RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS")
    .orElse(providers.gradleProperty("RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD")
    .orElse(providers.gradleProperty("RELEASE_KEY_PASSWORD"))
    .orElse(releaseStorePassword)

val gitVersionCode = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    workingDir(rootProject.rootDir)
    isIgnoreExitValue = true
}.standardOutput.asText.map { raw ->
    val count = raw.trim().toIntOrNull() ?: 0
    if (count >= publishedVersionCode) count else publishedVersionCode
}

val resolvedVersionCode = providers.gradleProperty("app.versionCode")
    .orElse(providers.environmentVariable("APP_VERSION_CODE"))
    .map { it.trim().toInt() }
    .orElse(gitVersionCode)

val resolvedVersionName = providers.gradleProperty("app.versionName")
    .orElse(providers.environmentVariable("APP_VERSION_NAME"))
    .orElse(publishedVersionName)

extensions.configure<ApplicationExtension> {
    namespace = "dev.chungjungsoo.gptmobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.chungjungsoo.gptmobile"
        minSdk = 31
        targetSdk = 37
        versionCode = resolvedVersionCode.get()
        versionName = resolvedVersionName.get()

        // Pixel 9 Pro is arm64-v8a. One ABI keeps the sideload APK from packing
        // x86, x86_64, and armeabi-v7a copies of the LiteRT native runtime.
        ndk {
            abiFilters.addAll(
                sideloadAbis.get()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf("arm64-v8a") }
            )
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Hugging Face OAuth. Replace these after registering an HF OAuth app;
        // the gallery credentials cannot be reused.
        manifestPlaceholders["appAuthRedirectScheme"] =
            "REPLACE_WITH_YOUR_REDIRECT_SCHEME_IN_HUGGINGFACE_APP"
        buildConfigField(
            "String",
            "HF_OAUTH_CLIENT_ID",
            "\"REPLACE_WITH_YOUR_CLIENT_ID_IN_HUGGINGFACE_APP\""
        )
        buildConfigField(
            "String",
            "HF_OAUTH_REDIRECT_URI",
            "\"REPLACE_WITH_YOUR_REDIRECT_URI_IN_HUGGINGFACE_APP\""
        )
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        disable += "MissingTranslation"
    }

    val storePath = releaseStoreFile.orNull
    val storePasswordValue = releaseStorePassword.orNull
    val keyAliasValue = releaseKeyAlias.orNull
    val keyPasswordValue = releaseKeyPassword.orNull
    val releaseKeystore = storePath?.takeIf { it.isNotBlank() }?.let { project.file(it) }
    val releaseKeystoreReady = releaseKeystore != null &&
        releaseKeystore.isFile &&
        !storePasswordValue.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

    signingConfigs {
        if (releaseKeystoreReady && releaseKeystore != null && storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isProfileable = true
            if (releaseKeystoreReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "NONE"
            }
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
            isDebuggable = false
            isProfileable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // SplashScreen
    implementation(libs.splashscreen)

    // DataStore
    implementation(libs.androidx.datastore)

    // Dependency Injection
    implementation(libs.hilt)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // Ktor
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serialization)
    implementation(libs.mcp.kotlin.sdk.client) {
        // The SDK bytecode targets Kotlin 2.1, but its 2.4 stdlib confuses Hilt's 2.3 metadata reader.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // OAuth browser flow
    implementation(libs.androidx.browser)
    implementation(libs.openid.appauth)

    // On-device LiteRT-LM serving
    implementation(libs.litertlm)

    // License page UI
    implementation(libs.auto.license.core)
    implementation(libs.auto.license.ui)

    // Markdown
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)

    // Navigation
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.navigation)

    // Room
    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Serialization
    implementation(libs.kotlin.serialization)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

aboutLibraries {
    // Remove the "generated" timestamp to allow for reproducible builds
    export {
        excludeFields.add("generated")
    }
}
