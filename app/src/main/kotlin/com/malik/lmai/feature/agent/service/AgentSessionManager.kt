package com.malik.lmai.feature.agent.service

import android.content.Context
import android.util.Log
import com.malik.lmai.R
import com.malik.lmai.data.preferences.AppText
import com.malik.lmai.data.database.entity.ChatRoomV2
import com.malik.lmai.data.database.entity.MessageV2
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.repository.ChatRepository
import com.malik.lmai.data.repository.ProjectRepository
import com.malik.lmai.feature.agent.AgentLoopCoordinator
import com.malik.lmai.feature.agent.AgentLoopEvent
import com.malik.lmai.feature.agent.AgentLoopRequest
import com.malik.lmai.feature.agent.AgentPlan
import com.malik.lmai.feature.agent.AgentStepItem
import com.malik.lmai.feature.agent.AgentStepType
import com.malik.lmai.feature.agent.AgentToolRegistry
import com.malik.lmai.feature.agent.AgentToolStatus
import com.malik.lmai.feature.agent.ToolCallInfo
import com.malik.lmai.feature.diagnostic.DiagnosticContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionMessageState(
    val userMessages: List<MessageV2>,
    val assistantMessages: List<List<MessageV2>>,
    val agentSteps: List<List<AgentStepItem>> = emptyList(),
)

@Singleton
class AgentSessionManager @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val agentLoopCoordinator: AgentLoopCoordinator,
    private val agentToolRegistry: AgentToolRegistry,
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val notificationHelper: AgentNotificationHelper,
) {

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    private val _sessions =
        MutableStateFlow<Map<Int, AgentSession>>(
            emptyMap()
        )

    val sessions: StateFlow<Map<Int, AgentSession>> =
        _sessions.asStateFlow()

    private val messageStates =
        ConcurrentHashMap<
            Int,
            MutableStateFlow<SessionMessageState>
        >()

    private val saveContexts =
        ConcurrentHashMap<
            Int,
            SessionSaveContext
        >()

    /*
     * =========================================================
     * SESSION GENERATION
     * =========================================================
     *
     * كل تشغيل جديد لنفس chatId يحصل على رقم مختلف.
     *
     * السبب:
     *
     * إذا ألغينا Session قديمة ثم بدأنا Session جديدة مباشرة،
     * قد تصل finally الخاصة بالقديمة متأخرة وتحذف الجديدة.
     *
     * generation يمنع ذلك.
     */
    private val sessionGenerations =
        ConcurrentHashMap<Int, Long>()

    private val nextSessionGeneration =
        AtomicLong(0L)

    private val _hasActiveSessions =
        MutableStateFlow(false)

    val hasActiveSessions:
        StateFlow<Boolean> =
        _hasActiveSessions.asStateFlow()

    init {

        scope.launch {

            _sessions.collect { map ->

                _hasActiveSessions.value =
                    map.isNotEmpty()
            }
        }
    }

    /*
     * =========================================================
     * START SESSION
     * =========================================================
     */
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

        /*
         * Cancel previous session for this chat.
         *
         * Do NOT delete its persistence context here.
         * Its CancellationException handler may still
         * need to save partial work.
         */
        stopSession(
            chatId
        )

        /*
         * Install a new generation.
         */
        val sessionGeneration =
            nextSessionGeneration
                .incrementAndGet()

        sessionGenerations[
            chatId
        ] =
            sessionGeneration

        /*
         * =====================================================
         * INITIAL MESSAGE STATE
         * =====================================================
         */
        val stateFlow =
            MutableStateFlow(
                SessionMessageState(
                    userMessages =
                        userMessages,

                    assistantMessages =
                        assistantMessages,

                    agentSteps =
                        List(
                            userMessages.size
                        ) { turnIndex ->

                            val message =
                                assistantMessages
                                    .getOrNull(
                                        turnIndex
                                    )
                                    ?.firstOrNull()

                            if (
                                message != null &&
                                message.thoughts
                                    .isNotBlank()
                            ) {

                                parseThoughtsToSteps(
                                    message.thoughts
                                )

                            } else {

                                emptyList()
                            }
                        },
                )
            )

        messageStates[
            chatId
        ] =
            stateFlow

        saveContexts[
            chatId
        ] =
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

        /*
         * =====================================================
         * AGENT JOB
         * =====================================================
         */
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

                    /*
                     * =================================================
                     * CRITICAL FIX
                     * =================================================
                     *
                     * LoopFailed is an EVENT.
                     *
                     * It does not throw an Exception.
                     *
                     * Therefore Flow.collect() can finish normally
                     * after LoopFailed.
                     *
                     * We must explicitly remember the outcome.
                     */
                    var loopCompleted =
                        false

                    var loopFailureMessage:
                        String? =
                        null

                    agentLoopCoordinator
                        .run(
                            request
                        )
                        .collect { event ->

                            /*
                             * Ignore late events belonging
                             * to an older cancelled session.
                             */
                            if (
                                !isCurrentGeneration(
                                    chatId =
                                        chatId,

                                    generation =
                                        sessionGeneration,
                                )
                            ) {

                                return@collect
                            }

                            applyEvent(
                                chatId =
                                    chatId,

                                event =
                                    event,

                                expectedGeneration =
                                    sessionGeneration,
                            )

                            when (event) {

                                is AgentLoopEvent
                                    .LoopCompleted -> {

                                    loopCompleted =
                                        true
                                }

                                is AgentLoopEvent
                                    .LoopFailed -> {

                                    loopFailureMessage =
                                        event.message
                                }

                                else ->
                                    Unit
                            }
                        }

                    /*
                     * If this session was replaced while
                     * collect() was finishing, do nothing.
                     */
                    if (
                        !isCurrentGeneration(
                            chatId =
                                chatId,

                            generation =
                                sessionGeneration,
                        )
                    ) {

                        return@launch
                    }

                    /*
                     * A coordinator Flow should always emit:
                     *
                     * LoopCompleted
                     *
                     * OR
                     *
                     * LoopFailed
                     *
                     * If neither arrives, classify it as failure.
                     */
                    if (
                        !loopCompleted &&
                        loopFailureMessage == null
                    ) {

                        val message =
                            "Agent loop ended without a terminal completion event."

                        loopFailureMessage =
                            message

                        applyEvent(
                            chatId =
                                chatId,

                            event =
                                AgentLoopEvent
                                    .LoopFailed(
                                        message =
                                            message
                                    ),

                            expectedGeneration =
                                sessionGeneration,
                        )
                    }

                    /*
                     * Save final result before cleanup.
                     */
                    saveToRoom(
                        chatId =
                            chatId,

                        expectedGeneration =
                            sessionGeneration,
                    )

                    /*
                     * =================================================
                     * CRITICAL FIX
                     * =================================================
                     *
                     * Failure takes precedence over completion.
                     */
                    if (
                        loopFailureMessage !=
                        null
                    ) {

                        statusFlow.value =
                            AgentSessionStatus.FAILED

                        onSessionFinished(
                            chatId =
                                chatId,

                            projectId =
                                projectId,

                            success =
                                false,
                        )

                    } else {

                        statusFlow.value =
                            AgentSessionStatus.COMPLETED

                        onSessionFinished(
                            chatId =
                                chatId,

                            projectId =
                                projectId,

                            success =
                                true,
                        )
                    }

                } catch (
                    e: CancellationException
                ) {

                    /*
                     * A cancelled session is NOT failure
                     * and NOT successful completion.
                     */
                    statusFlow.value =
                        AgentSessionStatus.CANCELLED

                    /*
                     * Preserve partial work if this is
                     * still the current generation.
                     */
                    try {

                        saveToRoom(
                            chatId =
                                chatId,

                            expectedGeneration =
                                sessionGeneration,
                        )

                    } catch (
                        _: Exception
                    ) {
                        /*
                         * Do not replace cancellation with
                         * a persistence exception.
                         */
                    }

                    throw e

                } catch (
                    e: Exception
                ) {

                    Log.e(
                        TAG,
                        "Agent session failed for chatId=$chatId",
                        e,
                    )

                    /*
                     * An old/replaced session must never
                     * report an error into a newer session.
                     */
                    if (
                        isCurrentGeneration(
                            chatId =
                                chatId,

                            generation =
                                sessionGeneration,
                        )
                    ) {

                        val rawMessage =
                            e.message
                                ?: "Unknown error"

                        applyEvent(
                            chatId =
                                chatId,

                            event =
                                AgentLoopEvent
                                    .LoopFailed(
                                        message =
                                            rawMessage
                                    ),

                            expectedGeneration =
                                sessionGeneration,
                        )

                        saveToRoom(
                            chatId =
                                chatId,

                            expectedGeneration =
                                sessionGeneration,
                        )

                        statusFlow.value =
                            AgentSessionStatus.FAILED

                        onSessionFinished(
                            chatId =
                                chatId,

                            projectId =
                                projectId,

                            success =
                                false,
                        )
                    }

                } finally {

                    /*
                     * Only this generation may remove itself.
                     */
                    removeSession(
                        chatId =
                            chatId,

                        expectedGeneration =
                            sessionGeneration,
                    )
                }
            }

        /*
         * Register active session.
         */
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
            current ->

            current +
                (
                    chatId to
                        session
                )
        }

        /*
         * Keep process alive while Agent is running.
         */
        AgentForegroundService
            .start(
                appContext
            )
    }

    /*
     * =========================================================
     * STOP ONE SESSION
     * =========================================================
     */
    fun stopSession(
        chatId: Int,
    ) {

        val session =
            _sessions.value[
                chatId
            ]
                ?: return

        /*
         * CRITICAL:
         *
         * Do NOT call removeSession() here.
         *
         * Cancellation handler first needs access to:
         *
         * messageStates
         * saveContexts
         *
         * so it can preserve partial progress.
         *
         * finally will perform cleanup.
         */
        session.job.cancel()
    }

    /*
     * =========================================================
     * STOP ALL
     * =========================================================
     */
    fun stopAllSessions() {

        /*
         * Do not immediately clear messageStates/saveContexts.
         *
         * Every cancelled coroutine should first get
         * its chance to persist partial work.
         */
        _sessions.value
            .forEach {
                (_, session) ->

                session.job.cancel()
            }
    }

    /*
     * =========================================================
     * CLEAR CACHED MESSAGE STATE
     * =========================================================
     */
    fun clearMessageState(
        chatId: Int,
    ) {

        messageStates.remove(
            chatId
        )

        saveContexts.remove(
            chatId
        )
    }

    /*
     * =========================================================
     * STATE ACCESS
     * =========================================================
     */
    fun getMessageState(
        chatId: Int,
    ): StateFlow<SessionMessageState>? {

        return messageStates[
            chatId
        ]
            ?.asStateFlow()
    }

    fun getSessionStatus(
        chatId: Int,
    ): StateFlow<AgentSessionStatus>? {

        return _sessions
            .value[
                chatId
            ]
            ?.status
    }

    fun getActiveSessionPlatformName(
        chatId: Int,
    ): String? {

        return _sessions
            .value[
                chatId
            ]
            ?.platformName
    }

    fun isSessionRunning(
        chatId: Int,
    ): Boolean {

        return _sessions
            .value[
                chatId
            ]
            ?.status
            ?.value ==
            AgentSessionStatus.RUNNING
    }

    /*
     * =========================================================
     * GENERATION CHECK
     * =========================================================
     */
    private fun isCurrentGeneration(
        chatId: Int,
        generation: Long,
    ): Boolean {

        return sessionGenerations[
            chatId
        ] ==
            generation
    }

    /*
     * =========================================================
     * APPLY AGENT EVENT
     * =========================================================
     */
    private fun applyEvent(
        chatId: Int,
        event: AgentLoopEvent,
        expectedGeneration: Long? = null,
    ) {

        /*
         * Prevent stale session events from mutating
         * state belonging to a newer session.
         */
        if (
            expectedGeneration != null &&
            sessionGenerations[
                chatId
            ] !=
            expectedGeneration
        ) {

            return
        }

        val stateFlow =
            messageStates[
                chatId
            ]
                ?: return

        when (event) {

            /*
             * =================================================
             * THINKING
             * =================================================
             */
            is AgentLoopEvent
                .ThinkingDelta -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant {
                            message ->

                            message.copy(
                                thoughts =
                                    message.thoughts +
                                        event.delta
                            )
                        }

                    updated.updateSingletonStep(
                        AgentStepType.THINKING
                    ) {
                        existing ->

                        existing.copy(
                            content =
                                existing.content +
                                    event.delta
                        )
                    }
                }
            }

            /*
             * =================================================
             * OUTPUT
             * =================================================
             */
            is AgentLoopEvent
                .OutputDelta -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant {
                            message ->

                            message.copy(
                                content =
                                    message.content +
                                        event.delta
                            )
                        }

                    updated.appendOrUpdateLastStep(
                        AgentStepType.OUTPUT
                    ) {
                        existing ->

                        existing.copy(
                            content =
                                existing.content +
                                    event.delta
                        )
                    }
                }
            }

            /*
             * =================================================
             * TOOL START
             * =================================================
             */
            is AgentLoopEvent
                .ToolExecutionStarted -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant {
                            message ->

                            message.copy(
                                thoughts =
                                    message.thoughts +
                                        "\n[Tool] " +
                                        event.call.name +
                                        "\n"
                            )
                        }

                    updated.updateSingletonStep(
                        AgentStepType.TOOL_CALL
                    ) {
                        existing ->

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

            /*
             * =================================================
             * TOOL RESULT
             * =================================================
             */
            is AgentLoopEvent
                .ToolExecutionFinished -> {

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
                        state.updateLastAssistant {
                            message ->

                            message.copy(
                                thoughts =
                                    message.thoughts +
                                        "\n[Tool Result] " +
                                        event.result.toolName +
                                        ": " +
                                        if (
                                            event.result.isError
                                        ) {
                                            "error\n"
                                        } else {
                                            "ok\n"
                                        }
                            )
                        }

                    updated
                        .updateSingletonToolCallStatus(
                            toolName =
                                event.result.toolName,

                            status =
                                status,
                        )
                }
            }

            /*
             * =================================================
             * LOOP COMPLETED
             * =================================================
             */
            is AgentLoopEvent
                .LoopCompleted -> {

                stateFlow.update { state ->

                    state.updateLastAssistant {
                        message ->

                        val fallbackText =
                            event.finalText
                                .ifBlank {

                                    if (
                                        event.toolResults
                                            .any {
                                                !it.isError
                                            }
                                    ) {

                                        AppText.get(R.string.agent_task_executed)

                                    } else {

                                        AppText.get(R.string.agent_session_completed)
                                    }
                                }

                        message.copy(
                            content =
                                message.content
                                    .ifBlank {
                                        fallbackText
                                    },

                            createdAt =
                                System
                                    .currentTimeMillis() /
                                    1000,
                        )
                    }
                }
            }

            /*
             * =================================================
             * LOOP FAILED
             * =================================================
             */
            is AgentLoopEvent
                .LoopFailed -> {

                stateFlow.update { state ->

                    /*
                     * Keep the real provider/Agent error.
                     *
                     * User UI can still show a clean localized
                     * message, but debugging must not lose:
                     *
                     * HTTP status
                     * provider message
                     * schema error
                     * tool-call error
                     * SSE error
                     */
                    val rawMessage =
                        event.message
                            .trim()
                            .ifBlank {
                                "Unknown agent error"
                            }

                    val friendlyMessage =
                        AgentErrorMessageFormatter
                            .format(
                                rawMessage
                            )

                    state.updateLastAssistant {
                        message ->

                        message.copy(
                            content =
                                if (
                                    message.content
                                        .isBlank()
                                ) {

                                    friendlyMessage

                                } else {

                                    message.content
                                },

                            /*
                             * IMPORTANT:
                             *
                             * Keep RAW error in thoughts.
                             *
                             * Previously the code stored only the
                             * friendly generic error here, which
                             * destroyed the actual diagnostic cause.
                             */
                            thoughts =
                                message.thoughts +
                                    "\n[Agent Error] " +
                                    rawMessage +
                                    "\n",

                            createdAt =
                                System
                                    .currentTimeMillis() /
                                    1000,
                        )
                    }
                }
            }

            /*
             * =================================================
             * PLAN CREATED
             * =================================================
             */
            is AgentLoopEvent
                .PlanCreated -> {

                stateFlow.update { state ->

                    val updated =
                        state.updateLastAssistant {
                            message ->

                            message.copy(
                                thoughts =
                                    message.thoughts +
                                        "\n[Plan] Created: " +
                                        event.plan.summary +
                                        "\n"
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

            /*
             * =================================================
             * PLAN UPDATED
             * =================================================
             */
            is AgentLoopEvent
                .PlanUpdated -> {

                stateFlow.update { state ->

                    state.updateLastPlanStep(
                        event.plan
                    )
                }
            }

            else ->
                Unit
        }
    }

    /*
     * =========================================================
     * SINGLETON STEP
     * =========================================================
     */
    private fun SessionMessageState
        .updateSingletonStep(
            type: AgentStepType,
            update:
                (AgentStepItem) ->
                    AgentStepItem,
        ): SessionMessageState {

        val steps =
            agentSteps
                .toMutableList()

        if (
            steps.isEmpty()
        ) {

            steps.add(
                emptyList()
            )
        }

        val lastTurnSteps =
            steps
                .last()
                .toMutableList()

        val index =
            lastTurnSteps
                .indexOfFirst {
                    it.type ==
                        type
                }

        if (
            index >=
            0
        ) {

            lastTurnSteps[
                index
            ] =
                update(
                    lastTurnSteps[
                        index
                    ]
                )

        } else {

            lastTurnSteps.add(
                update(
                    AgentStepItem(
                        type =
                            type
                    )
                )
            )
        }

        steps[
            steps.lastIndex
        ] =
            lastTurnSteps

        return copy(
            agentSteps =
                steps
        )
    }

    /*
     * =========================================================
     * OUTPUT STEP
     * =========================================================
     */
    private fun SessionMessageState
        .appendOrUpdateLastStep(
            type: AgentStepType,
            update:
                (AgentStepItem) ->
                    AgentStepItem,
        ): SessionMessageState {

        val steps =
            agentSteps
                .toMutableList()

        if (
            steps.isEmpty()
        ) {

            steps.add(
                emptyList()
            )
        }

        val lastTurnSteps =
            steps
                .last()
                .toMutableList()

        val lastStep =
            lastTurnSteps
                .lastOrNull()

        if (
            lastStep != null &&
            lastStep.type ==
            type
        ) {

            lastTurnSteps[
                lastTurnSteps
                    .lastIndex
            ] =
                update(
                    lastStep
                )

        } else {

            lastTurnSteps.add(
                update(
                    AgentStepItem(
                        type =
                            type
                    )
                )
            )
        }

        steps[
            steps.lastIndex
        ] =
            lastTurnSteps

        return copy(
            agentSteps =
                steps
        )
    }

    /*
     * =========================================================
     * ADD STEP
     * =========================================================
     */
    private fun SessionMessageState
        .addStep(
            step: AgentStepItem,
        ): SessionMessageState {

        val steps =
            agentSteps
                .toMutableList()

        if (
            steps.isEmpty()
        ) {

            steps.add(
                emptyList()
            )
        }

        val lastTurnSteps =
            steps
                .last()
                .toMutableList()

        lastTurnSteps.add(
            step
        )

        steps[
            steps.lastIndex
        ] =
            lastTurnSteps

        return copy(
            agentSteps =
                steps
        )
    }

    /*
     * =========================================================
     * UPDATE TOOL CALL STATUS
     * =========================================================
     */
    private fun SessionMessageState
        .updateSingletonToolCallStatus(
            toolName: String,
            status: AgentToolStatus,
        ): SessionMessageState {

        val steps =
            agentSteps
                .toMutableList()

        if (
            steps.isEmpty()
        ) {

            return this
        }

        val lastTurnSteps =
            steps
                .last()
                .toMutableList()

        val stepIndex =
            lastTurnSteps
                .indexOfFirst {
                    it.type ==
                        AgentStepType
                            .TOOL_CALL
                }

        if (
            stepIndex >=
            0
        ) {

            val step =
                lastTurnSteps[
                    stepIndex
                ]

            val updatedCalls =
                step.toolCalls
                    .toMutableList()

            val callIndex =
                updatedCalls
                    .indexOfLast {

                        it.toolName ==
                            toolName &&
                            it.toolStatus ==
                            AgentToolStatus
                                .CALLING
                    }

            if (
                callIndex >=
                0
            ) {

                updatedCalls[
                    callIndex
                ] =
                    updatedCalls[
                        callIndex
                    ]
                        .copy(
                            toolStatus =
                                status
                        )
            }

            lastTurnSteps[
                stepIndex
            ] =
                step.copy(
                    toolName =
                        toolName,

                    toolStatus =
                        status,

                    toolCalls =
                        updatedCalls,
                )

            steps[
                steps.lastIndex
            ] =
                lastTurnSteps
        }

        return copy(
            agentSteps =
                steps
        )
    }

    /*
     * =========================================================
     * UPDATE PLAN
     * =========================================================
     */
    private fun SessionMessageState
        .updateLastPlanStep(
            plan: AgentPlan,
        ): SessionMessageState {

        val steps =
            agentSteps
                .toMutableList()

        if (
            steps.isEmpty()
        ) {

            return this
        }

        val lastTurnSteps =
            steps
                .last()
                .toMutableList()

        val index =
            lastTurnSteps
                .indexOfLast {
                    it.type ==
                        AgentStepType.PLAN
                }

        if (
            index >=
            0
        ) {

            lastTurnSteps[
                index
            ] =
                lastTurnSteps[
                    index
                ]
                    .copy(
                        plan =
                            plan
                    )

            steps[
                steps.lastIndex
            ] =
                lastTurnSteps
        }

        return copy(
            agentSteps =
                steps
        )
    }

    /*
     * =========================================================
     * UPDATE LAST ASSISTANT MESSAGE
     * =========================================================
     */
    private inline fun SessionMessageState
        .updateLastAssistant(
            transform:
                (MessageV2) ->
                    MessageV2,
        ): SessionMessageState {

        if (
            assistantMessages
                .isEmpty()
        ) {

            return this
        }

        val lastTurn =
            assistantMessages
                .last()
                .toMutableList()

        if (
            lastTurn.isEmpty()
        ) {

            return this
        }

        lastTurn[
            0
        ] =
            transform(
                lastTurn[
                    0
                ]
            )

        val updated =
            assistantMessages
                .toMutableList()

        updated[
            updated.lastIndex
        ] =
            lastTurn

        return copy(
            assistantMessages =
                updated
        )
    }

    /*
     * =========================================================
     * SAVE TO ROOM
     * =========================================================
     */
    private suspend fun saveToRoom(
        chatId: Int,
        expectedGeneration: Long? = null,
    ) {

        /*
         * An old cancelled session must never save
         * state belonging to a newer session.
         */
        if (
            expectedGeneration !=
            null &&
            sessionGenerations[
                chatId
            ] !=
            expectedGeneration
        ) {

            return
        }

        val state =
            messageStates[
                chatId
            ]
                ?.value
                ?: return

        val saveContext =
            saveContexts[
                chatId
            ]
                ?: return

        val messages =
            (
                state.userMessages +
                    state
                        .assistantMessages
                        .flatten()
            )
                .filter {
                    it.content
                        .isNotBlank()
                }
                .sortedBy {
                    it.createdAt
                }

        try {

            val savedChatRoom =
                chatRepository
                    .saveChat(
                        chatRoom =
                            saveContext.chatRoom,

                        messages =
                            messages,

                        chatPlatformModels =
                            saveContext
                                .chatPlatformModels,
                    )

            /*
             * Save returned DB ID for subsequent updates.
             */
            saveContexts[
                chatId
            ] =
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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Failed to save session state " +
                    "to Room for chatId=$chatId",
                e,
            )
        }
    }

    /*
     * =========================================================
     * SAVED CHAT ROOM
     * =========================================================
     */
    fun getSavedChatRoom(
        chatId: Int,
    ): ChatRoomV2? {

        val context =
            saveContexts[
                chatId
            ]
                ?: return null

        return context
            .chatRoom
            .takeIf {
                it.id >
                    0
            }
    }

    /*
     * =========================================================
     * REMOVE SESSION
     * =========================================================
     */
    private fun removeSession(
        chatId: Int,
        expectedGeneration: Long,
    ) {

        /*
         * =====================================================
         * CRITICAL RACE-CONDITION FIX
         * =====================================================
         *
         * Old Session A:
         *
         * cancel()
         *      ↓
         * New Session B starts
         *      ↓
         * A finally executes later
         *
         * Without generation protection,
         * A would remove B.
         */
        if (
            sessionGenerations[
                chatId
            ] !=
            expectedGeneration
        ) {

            return
        }

        _sessions.update {
            current ->

            current -
                chatId
        }

        sessionGenerations.remove(
            chatId,
            expectedGeneration,
        )

        /*
         * Keep messageStates so ChatScreen can still
         * read the finished result.
         */
        saveContexts.remove(
            chatId
        )
    }

    /*
     * =========================================================
     * COMPLETION NOTIFICATION
     * =========================================================
     */
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
                    _: Exception
                ) {

                    null
                }
            }

        notificationHelper
            .showCompletionNotification(
                chatId =
                    chatId,

                projectName =
                    projectName,

                success =
                    success,
            )
    }

    private data class SessionSaveContext(
        val chatRoom: ChatRoomV2,
        val chatPlatformModels:
            Map<String, String>,
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

        /*
         * =====================================================
         * PARSE HISTORICAL THOUGHTS
         * =====================================================
         */
        fun parseThoughtsToSteps(
            thoughts: String,
        ): List<AgentStepItem> {

            val steps =
                mutableListOf<
                    AgentStepItem
                >()

            val thinkingBuffer =
                StringBuilder()

            val toolCalls =
                mutableListOf<
                    ToolCallInfo
                >()

            var lastToolName:
                String? =
                null

            var lastToolStatus:
                AgentToolStatus? =
                null

            for (
                line in
                thoughts.lines()
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

                    /*
                     * Tool start.
                     */
                    toolMatch !=
                        null -> {

                        val name =
                            toolMatch
                                .groupValues[
                                    1
                                ]

                        toolCalls.add(
                            ToolCallInfo(
                                toolName =
                                    name,

                                toolStatus =
                                    AgentToolStatus
                                        .CALLING,
                            )
                        )

                        lastToolName =
                            name

                        lastToolStatus =
                            AgentToolStatus
                                .CALLING
                    }

                    /*
                     * Tool result.
                     */
                    resultMatch !=
                        null -> {

                        val name =
                            resultMatch
                                .groupValues[
                                    1
                                ]

                        val status =
                            if (
                                resultMatch
                                    .groupValues[
                                        2
                                    ] ==
                                "ok"
                            ) {

                                AgentToolStatus.OK

                            } else {

                                AgentToolStatus.ERROR
                            }

                        val callIndex =
                            toolCalls
                                .indexOfLast {

                                    it.toolName ==
                                        name &&
                                        it.toolStatus ==
                                        AgentToolStatus
                                            .CALLING
                                }

                        if (
                            callIndex >=
                            0
                        ) {

                            toolCalls[
                                callIndex
                            ] =
                                toolCalls[
                                    callIndex
                                ]
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

                    /*
                     * Historical plan.
                     */
                    PLAN_LINE_REGEX
                        .matchEntire(
                            trimmed
                        ) !=
                        null -> {

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
                                        .groupValues[
                                            1
                                        ],
                            )
                        )
                    }

                    /*
                     * Ordinary reasoning/thinking.
                     */
                    trimmed
                        .isNotEmpty() -> {

                        thinkingBuffer
                            .appendLine(
                                line
                            )
                    }
                }
            }

            /*
             * One consolidated THINKING step.
             */
            if (
                thinkingBuffer
                    .isNotBlank()
            ) {

                steps.add(
                    0,
                    AgentStepItem(
                        type =
                            AgentStepType
                                .THINKING,

                        content =
                            thinkingBuffer
                                .toString()
                                .trim(),
                    )
                )
            }

            /*
             * One consolidated TOOL_CALL step.
             */
            if (
                toolCalls
                    .isNotEmpty()
            ) {

                val insertIndex =
                    if (
                        thinkingBuffer
                            .isNotBlank()
                    ) {

                        1

                    } else {

                        0
                    }

                steps.add(
                    insertIndex,
                    AgentStepItem(
                        type =
                            AgentStepType
                                .TOOL_CALL,

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
