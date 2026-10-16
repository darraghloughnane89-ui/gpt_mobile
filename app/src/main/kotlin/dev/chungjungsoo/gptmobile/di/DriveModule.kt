package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.backup.ChatSnapshotSource
import dev.chungjungsoo.gptmobile.data.backup.RoomChatSnapshotSource
import dev.chungjungsoo.gptmobile.data.drive.DriveAppDataStore
import dev.chungjungsoo.gptmobile.data.drive.DriveAuthorizer
import dev.chungjungsoo.gptmobile.data.drive.GoogleDriveAppData
import dev.chungjungsoo.gptmobile.data.drive.GoogleDriveAuthorizer

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveModule {
    @Binds
    abstract fun bindChatSnapshotSource(impl: RoomChatSnapshotSource): ChatSnapshotSource

    @Binds
    abstract fun bindDriveAppDataStore(impl: GoogleDriveAppData): DriveAppDataStore

    @Binds
    abstract fun bindDriveAuthorizer(impl: GoogleDriveAuthorizer): DriveAuthorizer
}
