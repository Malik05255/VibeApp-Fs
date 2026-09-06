package com.malik.lmai.di

import com.malik.lmai.feature.agent.AgentLoopCoordinator
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentToolRegistry
import com.malik.lmai.feature.agent.loop.DefaultAgentLoopCoordinator
import com.malik.lmai.feature.agent.loop.ProviderAgentGatewayRouter
import com.malik.lmai.feature.agent.tool.DefaultAgentToolRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAgentModelGateway(
        router: ProviderAgentGatewayRouter
    ): AgentModelGateway

    @Binds
    @Singleton
    abstract fun bindAgentToolRegistry(
        registry: DefaultAgentToolRegistry
    ): AgentToolRegistry

    @Binds
    @Singleton
    abstract fun bindAgentLoopCoordinator(
        coordinator: DefaultAgentLoopCoordinator
    ): AgentLoopCoordinator
}
