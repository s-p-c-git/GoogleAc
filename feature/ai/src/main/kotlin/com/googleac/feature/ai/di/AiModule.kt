package com.googleac.feature.ai.di

import com.googleac.feature.ai.AiSummarizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiSummarizer(): AiSummarizer = AiSummarizer()
}
