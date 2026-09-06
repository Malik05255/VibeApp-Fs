package com.malik.lmai.di

import com.malik.lmai.data.repository.ProjectRepository
import com.malik.lmai.data.repository.ProjectRepositoryImpl
import com.malik.lmai.feature.project.DefaultProjectManager
import com.malik.lmai.feature.project.ProjectManager
import com.malik.lmai.feature.project.memo.DefaultIntentStore
import com.malik.lmai.feature.project.memo.IntentStore
import com.malik.lmai.feature.project.snapshot.Clock
import com.malik.lmai.feature.project.snapshot.DefaultSnapshotManager
import com.malik.lmai.feature.project.snapshot.RandomSnapshotIdGenerator
import com.malik.lmai.feature.project.snapshot.SnapshotIdGenerator
import com.malik.lmai.feature.project.snapshot.SnapshotIndexIo
import com.malik.lmai.feature.project.snapshot.SnapshotManager
import com.malik.lmai.feature.project.snapshot.SnapshotStorage
import com.malik.lmai.feature.project.snapshot.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProjectModule {

    @Provides
    @Singleton
    fun provideProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository = impl

    @Provides
    @Singleton
    fun provideProjectManager(impl: DefaultProjectManager): ProjectManager = impl

    @Provides
    @Singleton
    fun provideIntentStore(impl: DefaultIntentStore): IntentStore = impl

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides
    @Singleton
    fun provideSnapshotIdGenerator(): SnapshotIdGenerator = RandomSnapshotIdGenerator

    @Provides
    @Singleton
    fun provideSnapshotStorage(): SnapshotStorage = SnapshotStorage()

    @Provides
    @Singleton
    fun provideSnapshotIndexIo(): SnapshotIndexIo = SnapshotIndexIo()

    @Provides
    @Singleton
    fun provideSnapshotManager(impl: DefaultSnapshotManager): SnapshotManager = impl
}
