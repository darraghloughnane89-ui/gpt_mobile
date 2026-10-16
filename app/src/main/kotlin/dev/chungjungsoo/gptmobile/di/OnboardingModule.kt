package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.onboarding.DataStoreOnboardingStore
import dev.chungjungsoo.gptmobile.data.onboarding.GatewayOnboardingArchiveImport
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingArchiveImport
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingStore(store: DataStoreOnboardingStore): OnboardingStore

    @Binds
    @Singleton
    abstract fun bindOnboardingArchiveImport(importer: GatewayOnboardingArchiveImport): OnboardingArchiveImport
}
