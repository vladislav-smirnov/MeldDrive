package io.github.airdaydreamers.melddrive.di

import android.content.Context
import android.util.Base64
import com.hierynomus.smbj.SMBClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import io.github.airdaydreamers.melddrive.data.storage.LocalFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import io.github.airdaydreamers.melddrive.data.storage.SmbFileSystemHandler
import io.github.airdaydreamers.melddrive.repository.TestFileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestAppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.getDatabase(context)

    @Provides
    fun provideRemoteServerDao(appDatabase: AppDatabase): RemoteServerDao = appDatabase.remoteServerDao()

    @Provides
    @Singleton
    fun provideSecurityManager(): SecurityManager = object : SecurityManager {
        override fun encrypt(data: String): String = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
        override fun decrypt(encryptedData: String): String = String(Base64.decode(encryptedData, Base64.DEFAULT))
    }

    @Provides
    @Singleton
    fun provideCredentialStorage(@ApplicationContext context: Context, securityManager: SecurityManager): CredentialStorage =
        CredentialStorage(context, securityManager)

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager = SettingsManager(context)

    @Provides
    @Singleton
    fun provideSMBClient(): SMBClient = SMBClient()

    @Provides
    @Singleton
    fun provideFileRepository(
        remoteServerDao: RemoteServerDao,
        credentialStorage: CredentialStorage,
        localHandler: LocalFileSystemHandler,
        smbHandlerFactory: SmbFileSystemHandler.Factory,
    ): FileRepository = TestFileRepository(remoteServerDao, credentialStorage, localHandler, smbHandlerFactory)
}
