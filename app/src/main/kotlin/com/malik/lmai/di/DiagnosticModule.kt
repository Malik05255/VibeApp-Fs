package com.malik.lmai.di

import com.malik.lmai.feature.diagnostic.ChatDiagnosticLogger
import com.malik.lmai.feature.diagnostic.ChatDiagnosticLoggerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticModule {

    @Binds
    @Singleton
    abstract fun bindChatDiagnosticLogger(
        impl: ChatDiagnosticLoggerImpl,
    ): ChatDiagnosticLogger
}
