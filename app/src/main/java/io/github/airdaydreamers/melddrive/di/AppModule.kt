package io.github.airdaydreamers.melddrive.di

import android.content.Context
import com.hierynomus.smbj.SMBClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import io.github.airdaydreamers.melddrive.data.security.TinkSecurityManager
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
    fun provideSecurityManager(@ApplicationContext context: Context): SecurityManager = TinkSecurityManager(context)

    @Provides
    @Singleton
    fun provideCredentialStorage(@ApplicationContext context: Context, securityManager: SecurityManager): CredentialStorage =
        CredentialStorage(context, securityManager)

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager = SettingsManager(context)

    @Provides
    fun provideSMBClient(): SMBClient = SMBClient()

    @Provides
    fun provideOkHttpClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideWebDavFileSystemHandlerFactory(): io.github.airdaydreamers.melddrive.data.storage.webdav.WebDavFileSystemHandler.Factory =
        io.github.airdaydreamers.melddrive.data.storage.webdav.WebDavFileSystemHandler.Factory { server ->
            io.github.airdaydreamers.melddrive.data.storage.webdav.WebDavFileSystemHandler(server)
        }
}
