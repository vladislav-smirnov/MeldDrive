package io.github.airdaydreamers.melddrive.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.getDatabase(context)

    @Provides
    fun provideRemoteServerDao(appDatabase: AppDatabase): RemoteServerDao = appDatabase.remoteServerDao()

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context): SecurityManager = SecurityManager(context)

    @Provides
    @Singleton
    fun provideCredentialStorage(@ApplicationContext context: Context, securityManager: SecurityManager): CredentialStorage =
        CredentialStorage(context, securityManager)

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager = SettingsManager(context)

    @Provides
    @Singleton
    fun provideFileRepository(remoteServerDao: RemoteServerDao, credentialStorage: CredentialStorage): FileRepository =
        FileRepository(remoteServerDao, credentialStorage)

    @Provides
    @Singleton
    fun provideServerRepository(remoteServerDao: RemoteServerDao, credentialStorage: CredentialStorage): ServerRepository =
        ServerRepository(remoteServerDao, credentialStorage)
}
