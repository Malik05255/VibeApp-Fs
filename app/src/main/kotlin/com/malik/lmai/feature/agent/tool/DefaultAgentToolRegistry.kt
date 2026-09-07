package com.malik.lmai.feature.agent.tool

import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.AgentToolDefinition
import com.malik.lmai.feature.agent.AgentToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AgentTool>,
) : AgentToolRegistry {

    private val toolList = tools.toList()

    override fun listDefinitions(): List<AgentToolDefinition> = toolList.map { it.definition }

    override fun findTool(name: String): AgentTool? = toolList.firstOrNull { it.definition.name == name }
}
