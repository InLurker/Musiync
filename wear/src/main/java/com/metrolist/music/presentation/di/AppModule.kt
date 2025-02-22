package com.metrolist.music.presentation.di

import android.content.Context
import com.metrolist.music.presentation.wear.DataClientService
import com.metrolist.music.presentation.wear.MessageClientService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    @Provides
    @Singleton
    fun provideDataClientService(@ApplicationContext context: Context): MessageClientService {
        return MessageClientService(context)
    }
}