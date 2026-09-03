package com.vibe.app.feature.agent.service

import android.content.Context
import android.util.Log
import com.vibe.app.data.database.entity.ChatRoomV2
import com.vibe.app.data.database.entity.MessageV2
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.ChatRepository
import com.vibe.app.data.repository.ProjectRepository
import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentPlan
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentStepItem
import com.vibe.app.feature.agent.AgentStepType
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentToolStatus
import com.vibe.app.feature.agent.ToolCallInfo
import com.vibe.app.feature.diagnostic.DiagnosticContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SessionMessageState(
    val userMessages: List<MessageV2>,
    val assistantMessages: List<List<MessageV2>>,
    val agentSteps: List<List<AgentStepItem>> = emptyList(),
)

@Singleton
class AgentSessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentLoopCoordinator: AgentLoopCoordinator,
    private val agentToolRegistry: AgentToolRegistry,
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val notificationHelper: AgentNotificationHelper,
) {

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private val _sessions =
        MutableStateFlow<Map<Int, AgentSession>>(emptyMap())

    val sessions: StateFlow<Map<Int, AgentSession>> =
        _sessions.asStateFlow()

    private val messageStates =
        ConcurrentHashMap<Int, MutableStateFlow<SessionMessageState>>()

    private val saveContexts =
        ConcurrentHashMap<Int, SessionSaveContext>()

    private val _hasActiveSessions =
        MutableStateFlow(false)

    val hasActiveSessions: StateFlow<Boolean> =
        _hasActiveSessions.asStateFlow()

    init {
        scope.launch {
            _sessions.collect { map ->
                _hasActiveSessions.value =
                    map.isNotEmpty()
            }
        }
    }

    fun startSession(
        chatId: Int,
        projectId: String?,
        platform: PlatformV2,
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        systemPrompt: String?,
        diagnosticContext: DiagnosticContext?,
        chatRoom: ChatRoomV2,
        chatPlatformModels: Map<String, String>,
    ) {
        stopSession(chatId)

        val stateFlow =
            MutableStateFlow(
                SessionMessageState(
                    userMessages =
                        userMessages,
                    assistantMessages =
                        assistantMessages,
                    agentSteps =
                        List(userMessages.size) { turnIndex ->

                            val msg =
                                assistantMessages
                                    .getOrNull(turnIndex)
                                    ?.firstOrNull()

                            if (
                                msg != null &&
                                msg.thoughts.isNotBlank()
                            ) {
                                parseThoughtsToSteps(
                                    msg.thoughts
                                )
                            } else {
                                emptyList()
                            }
                        },
                )
            )

        messageStates[chatId] =
            stateFlow

        saveContexts[chatId] =
            SessionSaveContext(
                chatRoom =
                    chatRoom,
                chatPlatformModels =
                    chatPlatformModels,
            )

        val statusFlow =
            MutableStateFlow(
                AgentSessionStatus.RUNNING
            )

        val job =
            scope.launch {

                try {

                    val request =
                        AgentLoopRequest(
                            chatId =
                                chatId,
                            projectId =
                                projectId,
                            diagnosticContext =
                                diagnosticContext,
                            platform =
                                platform,
                            userMessages =
                                userMessages,
                            assistantMessages =
                                assistantMessages,
                            systemPrompt =
                                systemPrompt,
                            tools =
                                agentToolRegistry
                                    .listDefinitions(),
                        )

                    agentLoopCoordinator
                        .run(request)
                        .collect { event ->
                            applyEvent(
                                chatId,
                                event
                            )
                        }

                    saveToRoom(chatId)

                    statusFlow.value =
                        AgentSessionStatus.COMPLETED

                    onSessionFinished(
                        chatId,
                        projectId,
                        success = true,
                    )

                } catch (
                    e: kotlinx.coroutines.CancellationException
                ) {

                    statusFlow.value =
                        AgentSessionStatus.CANCELLED

                    try {
                        saveToRoom(chatId)
                    } catch (_: Exception) {
                    }

                    throw e

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Agent session failed for chatId=$chatId",
                        e,
                    )

                    applyEvent(
                        chatId,
                        AgentLoopEvent.LoopFailed(
                            message =
                                e.message
                                    ?: "Unknown error"
                        )
                    )

                    saveToRoom(chatId)

                    statusFlow.value =
                        AgentSessionStatus.FAILED

                    onSessionFinished(
                        chatId,
                        projectId,
                        success = false,
                    )

                } finally {
                    removeSession(chatId)
                }
            }

        val session =
            AgentSession(
                chatId =
                    chatId,
                projectId =
                    projectId,
                platformName =
                    platform.name,
                job =
                    job,
                status =
                    statusFlow,
            )

        _sessions.update {
            it + (chatId to session)
        }

        AgentForegroundService.start(
            appContext
        )
    }

    fun stopSession(
        chatId: Int
    ) {
        val session =
            _sessions.value[chatId]
                ?: return

        session.job.cancel()

        removeSession(chatId)
    }

    fun stopAllSessions() {

        _sessions.value
            .forEach { (_, session) ->
                session.job.cancel()
            }

        _sessions.update {
            emptyMap()
        }

        messageStates.clear()
        saveContexts.clear()
    }

    fun clearMessageState(
        chatId: Int
    ) {
        messageStates.remove(chatId)
        saveContexts.remove(chatId)
    }

    fun getMessageState(
        chatId: Int
    ): StateFlow<SessionMessageState>? {
        return messageStates[chatId]
            ?.asStateFlow()
    }

    fun getSessionStatus(
        chatId: Int
    ): StateFlow<AgentSessionStatus>? {
        return _sessions
            .value[chatId]
            ?.status
    }

    fun getActiveSessionPlatformName(
        chatId: Int
    ): String? {
        return _sessions
            .value[chatId]
            ?.platformName
    }

    fun isSessionRunning(
        chatId: Int
    ): Boolean {
        return _sessions
            .value[chatId]
            ?.status
            ?.value ==
            AgentSessionStatus.RUNNING
    }

    private fun applyEvent(
        chatId: Int,
        event: AgentLoopEvent,
    ) {
        val stateFlow =
            messageStates[chatId]
                ?: return

        when (event) {

            is AgentLoopEvent.ThinkingDelta -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant { msg ->
                            msg.copy(
                                thoughts =
                                    msg.thoughts +
                                        event.delta
                            )
                        }

                    updated.updateSingletonStep(
                        AgentStepType.THINKING
                    ) { existing ->

                        existing.copy(
                            content =
                                existing.content +
                                    event.delta
                        )
                    }
                }
            }

            is AgentLoopEvent.OutputDelta -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant { msg ->
                            msg.copy(
                                content =
                                    msg.content +
                                        event.delta
                            )
                        }

                    updated.appendOrUpdateLastStep(
                        AgentStepType.OUTPUT
                    ) { existing ->

                        existing.copy(
                            content =
                                existing.content +
                                    event.delta
                        )
                    }
                }
            }

            is AgentLoopEvent.ToolExecutionStarted -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant { msg ->
                            msg.copy(
                                thoughts =
                                    msg.thoughts +
                                        "\n[Tool] " +
                                        "${event.call.name}\n"
                            )
                        }

                    updated.updateSingletonStep(
                        AgentStepType.TOOL_CALL
                    ) { existing ->

                        existing.copy(
                            toolName =
                                event.call.name,
                            toolStatus =
                                AgentToolStatus.CALLING,
                            toolCalls =
                                existing.toolCalls +
                                    ToolCallInfo(
                                        toolName =
                                            event.call.name,
                                        toolStatus =
                                            AgentToolStatus.CALLING,
                                    ),
                        )
                    }
                }
            }

            is AgentLoopEvent.ToolExecutionFinished -> {

                stateFlow.update { state ->

                    val status =
                        if (
                            event.result.isError
                        ) {
                            AgentToolStatus.ERROR
                        } else {
                            AgentToolStatus.OK
                        }

                    val updated =
                        state.updateLastAssistant { msg ->
                            msg.copy(
                                thoughts =
                                    msg.thoughts +
                                        "\n[Tool Result] " +
                                        "${event.result.toolName}: " +
                                        if (
                                            event.result.isError
                                        ) {
                                            "error\n"
                                        } else {
                                            "ok\n"
                                        }
                            )
                        }

                    updated.updateSingletonToolCallStatus(
                        event.result.toolName,
                        status,
                    )
                }
            }

            is AgentLoopEvent.LoopCompleted -> {

                stateFlow.update { state ->

                    state.updateLastAssistant { msg ->

                        val fallbackText =
                            event.finalText.ifBlank {
                                "لم يتم استخدام أدوات إنشاء المشروع، لم يتم إنشاء أي ملفات"
                            }

                        msg.copy(
                            content =
                                msg.content.ifBlank {
                                    fallbackText
                                },
                            createdAt =
                                System.currentTimeMillis() /
                                    1000,
                        )
                    }
                }
            }

            is AgentLoopEvent.LoopFailed -> {

                stateFlow.update { state ->

                    val friendlyMessage =
                        AgentErrorMessageFormatter.format(
                            event.message
                        )

                    state.updateLastAssistant { msg ->

                        msg.copy(
                            content =
                                if (
                                    msg.content.isBlank()
                                ) {
                                    friendlyMessage
                                } else {
                                    msg.content
                                },

                            thoughts =
                                msg.thoughts +
                                    "\n[Agent Error] " +
                                    friendlyMessage,

                            createdAt =
                                System.currentTimeMillis() /
                                    1000,
                        )
                    }
                }
            }

            is AgentLoopEvent.PlanCreated -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant { msg ->
                            msg.copy(
                                thoughts =
                                    msg.thoughts +
                                        "\n[Plan] Created: " +
                                        "${event.plan.summary}\n"
                            )
                        }

                    updated.addStep(
                        AgentStepItem(
                            type =
                                AgentStepType.PLAN,
                            content =
                                event.plan.summary,
                            plan =
                                event.plan,
                        )
                    )
                }
            }

            is AgentLoopEvent.PlanUpdated -> {

                stateFlow.update { state ->

                    state.updateLastPlanStep(
                        event.plan
                    )
                }
            }

            else -> Unit
        }
    }

    private fun SessionMessageState.updateSingletonStep(
        type: AgentStepType,
        update: (AgentStepItem) -> AgentStepItem,
    ): SessionMessageState {

        val steps =
            agentSteps.toMutableList()

        if (steps.isEmpty()) {
            steps.add(emptyList())
        }

        val lastTurnSteps =
            steps.last().toMutableList()

        val idx =
            lastTurnSteps.indexOfFirst {
                it.type == type
            }

        if (idx >= 0) {

            lastTurnSteps[idx] =
                update(
                    lastTurnSteps[idx]
                )

        } else {

            lastTurnSteps.add(
                update(
                    AgentStepItem(
                        type = type
                    )
                )
            )
        }

        steps[steps.lastIndex] =
            lastTurnSteps

        return copy(
            agentSteps = steps
        )
    }

    private fun SessionMessageState.appendOrUpdateLastStep(
        type: AgentStepType,
        update: (AgentStepItem) -> AgentStepItem,
    ): SessionMessageState {

        val steps =
            agentSteps.toMutableList()

        if (steps.isEmpty()) {
            steps.add(emptyList())
        }

        val lastTurnSteps =
            steps.last().toMutableList()

        val lastStep =
            lastTurnSteps.lastOrNull()

        if (
            lastStep != null &&
            lastStep.type == type
        ) {

            lastTurnSteps[
                lastTurnSteps.lastIndex
            ] =
                update(
                    lastStep
                )

        } else {

            lastTurnSteps.add(
                update(
                    AgentStepItem(
                        type = type
                    )
                )
            )
        }

        steps[steps.lastIndex] =
            lastTurnSteps

        return copy(
            agentSteps = steps
        )
    }

    private fun SessionMessageState.addStep(
        step: AgentStepItem
    ): SessionMessageState {

        val steps =
            agentSteps.toMutableList()

        if (steps.isEmpty()) {
            steps.add(emptyList())
        }

        val lastTurnSteps =
            steps.last().toMutableList()

        lastTurnSteps.add(step)

        steps[steps.lastIndex] =
            lastTurnSteps

        return copy(
            agentSteps = steps
        )
    }

    private fun SessionMessageState.updateSingletonToolCallStatus(
        toolName: String,
        status: AgentToolStatus,
    ): SessionMessageState {

        val steps =
            agentSteps.toMutableList()

        if (steps.isEmpty()) {
            return this
        }

        val lastTurnSteps =
            steps.last().toMutableList()

        val idx =
            lastTurnSteps.indexOfFirst {
                it.type ==
                    AgentStepType.TOOL_CALL
            }

        if (idx >= 0) {

            val step =
                lastTurnSteps[idx]

            val updatedCalls =
                step.toolCalls.toMutableList()

            val callIdx =
                updatedCalls.indexOfLast {
                    it.toolName == toolName &&
                        it.toolStatus ==
                        AgentToolStatus.CALLING
                }

            if (callIdx >= 0) {

                updatedCalls[callIdx] =
                    updatedCalls[callIdx]
                        .copy(
                            toolStatus =
                                status
                        )
            }

            lastTurnSteps[idx] =
                step.copy(
                    toolName =
                        toolName,
                    toolStatus =
                        status,
                    toolCalls =
                        updatedCalls,
                )

            steps[steps.lastIndex] =
                lastTurnSteps
        }

        return copy(
            agentSteps = steps
        )
    }

    private fun SessionMessageState.updateLastPlanStep(
        plan: AgentPlan,
    ): SessionMessageState {

        val steps =
            agentSteps.toMutableList()

        if (steps.isEmpty()) {
            return this
        }

        val lastTurnSteps =
            steps.last().toMutableList()

        val idx =
            lastTurnSteps.indexOfLast {
                it.type ==
                    AgentStepType.PLAN
            }

        if (idx >= 0) {

            lastTurnSteps[idx] =
                lastTurnSteps[idx]
                    .copy(
                        plan = plan
                    )

            steps[steps.lastIndex] =
                lastTurnSteps
        }

        return copy(
            agentSteps = steps
        )
    }

    private inline fun SessionMessageState.updateLastAssistant(
        transform: (MessageV2) -> MessageV2,
    ): SessionMessageState {

        if (
            assistantMessages.isEmpty()
        ) {
            return this
        }

        val lastTurn =
            assistantMessages
                .last()
                .toMutableList()

        if (lastTurn.isEmpty()) {
            return this
        }

        lastTurn[0] =
            transform(
                lastTurn[0]
            )

        val updated =
            assistantMessages.toMutableList()

        updated[updated.lastIndex] =
            lastTurn

        return copy(
            assistantMessages =
                updated
        )
    }

    private suspend fun saveToRoom(
        chatId: Int
    ) {

        val state =
            messageStates[chatId]
                ?.value
                ?: return

        val saveContext =
            saveContexts[chatId]
                ?: return

        val messages =
            (
                state.userMessages +
                    state.assistantMessages
                        .flatten()
                )
                .filter {
                    it.content.isNotBlank()
                }
                .sortedBy {
                    it.createdAt
                }

        try {

            val savedChatRoom =
                chatRepository.saveChat(
                    chatRoom =
                        saveContext.chatRoom,
                    messages =
                        messages,
                    chatPlatformModels =
                        saveContext
                            .chatPlatformModels,
                )

            saveContexts[chatId] =
                saveContext.copy(
                    chatRoom =
                        savedChatRoom
                )

            Log.d(
                TAG,
                "Saved session state to Room " +
                    "for chatId=$chatId " +
                    "(savedId=${savedChatRoom.id})"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to save session state to Room " +
                    "for chatId=$chatId",
                e,
            )
        }
    }

    fun getSavedChatRoom(
        chatId: Int
    ): ChatRoomV2? {

        val ctx =
            saveContexts[chatId]
                ?: return null

        return ctx.chatRoom.takeIf {
            it.id > 0
        }
    }

    private fun removeSession(
        chatId: Int
    ) {

        _sessions.update {
            it - chatId
        }

        /*
         * Keep messageStates around so reconnecting UI
         * can still read final state.
         */
        saveContexts.remove(chatId)
    }

    private suspend fun onSessionFinished(
        chatId: Int,
        projectId: String?,
        success: Boolean,
    ) {

        val projectName =
            projectId?.let {

                try {

                    projectRepository
                        .fetchProjectByChatId(
                            chatId
                        )
                        ?.name

                } catch (
                    e: Exception
                ) {
                    null
                }
            }

        notificationHelper
            .showCompletionNotification(
                chatId,
                projectName,
                success,
            )
    }

    private data class SessionSaveContext(
        val chatRoom: ChatRoomV2,
        val chatPlatformModels: Map<String, String>,
    )

    companion object {

        private const val TAG =
            "AgentSessionManager"

        private val TOOL_LINE_REGEX =
            Regex(
                """\[Tool]\s+(\S+)"""
            )

        private val TOOL_RESULT_REGEX =
            Regex(
                """\[Tool Result]\s+(\S+):\s*(ok|error|fail)"""
            )

        private val PLAN_LINE_REGEX =
            Regex(
                """\[Plan]\s+Created:\s+(.+)"""
            )

        fun parseThoughtsToSteps(
            thoughts: String
        ): List<AgentStepItem> {

            val steps =
                mutableListOf<AgentStepItem>()

            val thinkingBuffer =
                StringBuilder()

            val toolCalls =
                mutableListOf<ToolCallInfo>()

            var lastToolName:
                String? = null

            var lastToolStatus:
                AgentToolStatus? = null

            for (
                line in thoughts.lines()
            ) {

                val trimmed =
                    line.trim()

                val toolMatch =
                    TOOL_LINE_REGEX
                        .matchEntire(
                            trimmed
                        )

                val resultMatch =
                    TOOL_RESULT_REGEX
                        .matchEntire(
                            trimmed
                        )

                when {

                    toolMatch != null -> {

                        val name =
                            toolMatch
                                .groupValues[1]

                        toolCalls.add(
                            ToolCallInfo(
                                toolName =
                                    name,
                                toolStatus =
                                    AgentToolStatus.CALLING,
                            )
                        )

                        lastToolName =
                            name

                        lastToolStatus =
                            AgentToolStatus.CALLING
                    }

                    resultMatch != null -> {

                        val name =
                            resultMatch
                                .groupValues[1]

                        val status =
                            if (
                                resultMatch
                                    .groupValues[2] ==
                                "ok"
                            ) {
                                AgentToolStatus.OK
                            } else {
                                AgentToolStatus.ERROR
                            }

                        val idx =
                            toolCalls
                                .indexOfLast {
                                    it.toolName == name &&
                                        it.toolStatus ==
                                        AgentToolStatus.CALLING
                                }

                        if (idx >= 0) {

                            toolCalls[idx] =
                                toolCalls[idx]
                                    .copy(
                                        toolStatus =
                                            status
                                    )
                        }

                        lastToolName =
                            name

                        lastToolStatus =
                            status
                    }

                    PLAN_LINE_REGEX
                        .matchEntire(
                            trimmed
                        ) != null -> {

                        val planMatch =
                            PLAN_LINE_REGEX
                                .matchEntire(
                                    trimmed
                                )!!

                        steps.add(
                            AgentStepItem(
                                type =
                                    AgentStepType.PLAN,
                                content =
                                    planMatch
                                        .groupValues[1],
                            )
                        )
                    }

                    trimmed.isNotEmpty() -> {

                        thinkingBuffer
                            .appendLine(
                                line
                            )
                    }
                }
            }

            if (
                thinkingBuffer.isNotBlank()
            ) {

                steps.add(
                    0,
                    AgentStepItem(
                        type =
                            AgentStepType.THINKING,
                        content =
                            thinkingBuffer
                                .toString()
                                .trim(),
                    )
                )
            }

            if (
                toolCalls.isNotEmpty()
            ) {

                val insertIdx =
                    if (
                        thinkingBuffer
                            .isNotBlank()
                    ) {
                        1
                    } else {
                        0
                    }

                steps.add(
                    insertIdx,
                    AgentStepItem(
                        type =
                            AgentStepType.TOOL_CALL,
                        toolName =
                            lastToolName,
                        toolStatus =
                            lastToolStatus,
                        toolCalls =
                            toolCalls,
                    )
                )
            }

            return steps
        }
    }
}
