package com.malik.lmai.feature.agent.loop

import android.content.Context
import com.malik.lmai.data.database.entity.MessageV2
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopCoordinator
import com.malik.lmai.feature.agent.AgentLoopEvent
import com.malik.lmai.feature.agent.AgentLoopRequest
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentPlan
import com.malik.lmai.feature.agent.AgentPlanStep
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import com.malik.lmai.feature.agent.AgentToolRegistry
import com.malik.lmai.feature.agent.AgentToolResult
import com.malik.lmai.feature.agent.PlanStepStatus
import com.malik.lmai.feature.agent.loop.compaction.ConversationCompactor
import com.malik.lmai.feature.agent.loop.compaction.ProviderContextBudget
import com.malik.lmai.feature.agent.loop.iteration.AgentMode
import com.malik.lmai.feature.agent.loop.iteration.IterationModeDetector
import com.malik.lmai.feature.agent.loop.iteration.PromptAssembler
import com.malik.lmai.feature.agent.tool.requireString
import com.malik.lmai.feature.diagnostic.ChatDiagnosticLogger
import com.malik.lmai.feature.project.ProjectManager
import com.malik.lmai.feature.project.VibeProjectDirs
import com.malik.lmai.feature.project.memo.MemoLoader
import com.malik.lmai.feature.project.memo.OutlineGenerator
import com.malik.lmai.feature.project.memo.ProjectMemo
import com.malik.lmai.feature.project.snapshot.SnapshotManager
import com.malik.lmai.feature.project.snapshot.SnapshotType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val agentModelGateway: AgentModelGateway,
    private val agentToolRegistry: AgentToolRegistry,
    private val diagnosticLogger: ChatDiagnosticLogger,
    private val projectManager: ProjectManager,
    private val conversationCompactor: ConversationCompactor,
    private val snapshotManager: SnapshotManager,
    private val iterationModeDetector: IterationModeDetector,
    private val memoLoader: MemoLoader,
    private val outlineGenerator: OutlineGenerator,
) : AgentLoopCoordinator {

    override suspend fun run(
        request: AgentLoopRequest,
    ): Flow<AgentLoopEvent> = flow {

        /*
         * =========================================================
         * LOOP START
         * =========================================================
         */
        emit(
            AgentLoopEvent.LoopStarted(
                chatId = request.chatId,
                platformUid = request.platform.uid,
            )
        )

        /*
         * =========================================================
         * PREPARE TURN
         * =========================================================
         */
        val projectId =
            request.projectId

        var turnContext: TurnContext? =
            null

        var mode =
            AgentMode.GREENFIELD

        var memo: ProjectMemo? =
            null

        if (
            !projectId.isNullOrBlank()
        ) {

            /*
             * Snapshot/memo preparation must never
             * prevent the Agent itself from running.
             */
            runCatching {

                val workspace =
                    projectManager
                        .openWorkspace(
                            projectId
                        )

                val vibeDirs =
                    VibeProjectDirs
                        .fromWorkspaceRoot(
                            workspace.rootDir
                        )
                        .also {
                            it.ensureCreated()
                        }

                snapshotManager
                    .recoverPendingRestore(
                        projectId =
                            projectId,
                        workspaceRoot =
                            workspace.rootDir,
                        vibeDirs =
                            vibeDirs,
                    )

                mode =
                    iterationModeDetector
                        .detect(
                            projectId =
                                projectId,
                            vibeDirs =
                                vibeDirs,
                        )

                if (
                    mode ==
                    AgentMode.ITERATE
                ) {

                    memo =
                        memoLoader
                            .load(
                                vibeDirs
                            )
                }

                val priorTurnCount =
                    snapshotManager
                        .list(
                            projectId,
                            vibeDirs,
                        )
                        .count {
                            it.type ==
                                SnapshotType.TURN
                        }

                val nextTurnIndex =
                    priorTurnCount + 1

                val snapshotHandle =
                    snapshotManager
                        .prepare(
                            projectId =
                                projectId,

                            workspaceRoot =
                                workspace.rootDir,

                            vibeDirs =
                                vibeDirs,

                            type =
                                SnapshotType.TURN,

                            label =
                                currentUserText(
                                    request
                                )
                                    .orEmpty()
                                    .take(
                                        40
                                    ),

                            turnIndex =
                                nextTurnIndex,
                        )

                turnContext =
                    TurnContext(
                        projectId =
                            projectId,

                        workspaceRoot =
                            workspace.rootDir,

                        vibeDirs =
                            vibeDirs,

                        mode =
                            mode,

                        snapshotHandle =
                            snapshotHandle,

                        turnIndex =
                            nextTurnIndex,
                    )

            }.onFailure {

                /*
                 * Continue in GREENFIELD mode.
                 *
                 * A snapshot problem should not stop
                 * AI application generation.
                 */
                turnContext =
                    null

                mode =
                    AgentMode.GREENFIELD

                memo =
                    null
            }
        }

        /*
         * Accumulated results are retained for:
         *
         * final UI
         * build status
         * snapshot finalization
         */
        val collectedToolResults =
            mutableListOf<AgentToolResult>()

        try {

            /*
             * Stateful providers can use this.
             *
             * Chat-Completions providers may simply
             * ignore it.
             */
            var previousResponseId:
                String? =
                null

            /*
             * Room history from previous user turns.
             */
            val initialConversation =
                buildInitialConversation(
                    request
                )

            /*
             * Delta:
             *
             * Items newly produced since the previous
             * model turn.
             */
            var conversationDelta:
                List<AgentConversationItem> =
                initialConversation

            /*
             * Full conversation:
             *
             * Required by stateless providers such as
             * OpenRouter / Google AI Studio / Custom
             * Chat Completions.
             */
            val fullConversation =
                initialConversation
                    .toMutableList()

            /*
             * =====================================================
             * CRITICAL RESTORED FEATURE
             * =====================================================
             *
             * Keep the current execution plan across
             * Agent iterations.
             */
            var currentPlan:
                AgentPlan? =
                null

            /*
             * =====================================================
             * AGENT ITERATION LOOP
             * =====================================================
             */
            for (
                iteration in
                1..request.policy.maxIterations
            ) {

                emit(
                    AgentLoopEvent.ModelTurnStarted(
                        iteration
                    )
                )

                val pendingToolResults =
                    mutableListOf<AgentToolResult>()

                val pendingCalls =
                    mutableListOf<
                        com.malik.lmai.feature.agent.AgentToolCall
                    >()

                val outputBuilder =
                    StringBuilder()

                var failureMessage:
                    String? =
                    null

                var turnReasoningContent:
                    String? =
                    null

                /*
                 * =================================================
                 * FIRST ITERATION MUST USE TOOLS
                 * =================================================
                 *
                 * A build/create request must not be
                 * satisfied by a text-only answer.
                 */
                val effectivePolicy =
                    if (
                        iteration == 1 &&
                        request.tools.isNotEmpty()
                    ) {

                        request.policy.copy(
                            toolChoiceMode =
                                AgentToolChoiceMode.REQUIRED
                        )

                    } else {

                        request.policy
                    }

                /*
                 * Compact context before sending it to
                 * the provider.
                 */
                val compactionResult =
                    conversationCompactor
                        .compact(
                            items =
                                fullConversation
                                    .toList(),

                            clientType =
                                request.platform
                                    .compatibleType,

                            platform =
                                request.platform,
                        )

                /*
                 * =================================================
                 * MODEL REQUEST
                 * =================================================
                 */
                agentModelGateway
                    .streamTurn(
                        AgentModelRequest(
                            platform =
                                request.platform,

                            diagnosticContext =
                                request
                                    .diagnosticContext
                                    ?.copy(
                                        platformUid =
                                            request
                                                .platform
                                                .uid
                                    ),

                            conversation =
                                conversationDelta,

                            fullConversation =
                                compactionResult.items,

                            /*
                             * CRITICAL:
                             *
                             * Include the active plan in the
                             * system instructions on every
                             * subsequent iteration.
                             */
                            instructions =
                                buildInstructions(
                                    request =
                                        request,

                                    activePlan =
                                        currentPlan,

                                    mode =
                                        mode,

                                    memo =
                                        memo,
                                ),

                            tools =
                                request.tools,

                            policy =
                                effectivePolicy,

                            previousResponseId =
                                previousResponseId,
                        )
                    )
                    .collect {
                        event ->

                        when (event) {

                            /*
                             * =====================================
                             * THINKING
                             * =====================================
                             */
                            is AgentModelEvent
                                .ThinkingDelta -> {

                                emit(
                                    AgentLoopEvent
                                        .ThinkingDelta(
                                            iteration =
                                                iteration,

                                            delta =
                                                event.delta,
                                        )
                                )
                            }

                            /*
                             * =====================================
                             * TEXT OUTPUT
                             * =====================================
                             */
                            is AgentModelEvent
                                .OutputDelta -> {

                                outputBuilder
                                    .append(
                                        event.delta
                                    )

                                emit(
                                    AgentLoopEvent
                                        .OutputDelta(
                                            iteration =
                                                iteration,

                                            delta =
                                                event.delta,
                                        )
                                )
                            }

                            /*
                             * =====================================
                             * TOOL CALL
                             * =====================================
                             */
                            is AgentModelEvent
                                .ToolCallReady -> {

                                pendingCalls +=
                                    event.call

                                emit(
                                    AgentLoopEvent
                                        .ToolCallDiscovered(
                                            iteration =
                                                iteration,

                                            call =
                                                event.call,
                                        )
                                )
                            }

                            /*
                             * =====================================
                             * MODEL TURN COMPLETED
                             * =====================================
                             */
                            is AgentModelEvent
                                .Completed -> {

                                previousResponseId =
                                    event.responseId
                                        ?: previousResponseId

                                if (
                                    event.reasoningContent !=
                                    null
                                ) {

                                    turnReasoningContent =
                                        event.reasoningContent
                                }
                            }

                            /*
                             * =====================================
                             * PROVIDER FAILURE
                             * =====================================
                             */
                            is AgentModelEvent
                                .Failed -> {

                                failureMessage =
                                    event.message
                            }
                        }
                    }

                /*
                 * =================================================
                 * MODEL FAILURE
                 * =================================================
                 */
                if (
                    failureMessage !=
                    null
                ) {

                    emit(
                        AgentLoopEvent.LoopFailed(
                            message =
                                failureMessage,

                            iteration =
                                iteration,
                        )
                    )

                    return@flow
                }

                /*
                 * =================================================
                 * NATURAL COMPLETION
                 * =================================================
                 *
                 * No tool calls means the model considers
                 * its work complete.
                 */
                if (
                    pendingCalls.isEmpty()
                ) {

                    emit(
                        AgentLoopEvent.LoopCompleted(
                            finalText =
                                outputBuilder
                                    .toString()
                                    .trim(),

                            toolResults =
                                collectedToolResults
                                    .toList(),
                        )
                    )

                    return@flow
                }

                /*
                 * =================================================
                 * APPEND ASSISTANT TOOL-CALL TURN
                 * =================================================
                 *
                 * This is mandatory for OpenAI-compatible
                 * stateless Chat Completions.
                 *
                 * The next request must contain:
                 *
                 * assistant + tool_calls
                 * followed by
                 * tool result messages.
                 */
                fullConversation +=
                    AgentConversationItem(
                        role =
                            AgentMessageRole
                                .ASSISTANT,

                        text =
                            outputBuilder
                                .toString()
                                .trim()
                                .takeIf {
                                    it.isNotEmpty()
                                },

                        toolCalls =
                            pendingCalls
                                .toList(),

                        reasoningContent =
                            turnReasoningContent,
                    )

                /*
                 * =================================================
                 * EXECUTE TOOLS
                 * =================================================
                 */
                pendingCalls
                    .forEach {
                        call ->

                        val tool =
                            agentToolRegistry
                                .findTool(
                                    call.name
                                )

                        /*
                         * =========================================
                         * UNKNOWN TOOL
                         * =========================================
                         */
                        if (
                            tool ==
                            null
                        ) {

                            val result =
                                AgentToolResult(
                                    toolCallId =
                                        call.id,

                                    toolName =
                                        call.name,

                                    output =
                                        buildJsonObject {

                                            put(
                                                "error",
                                                JsonPrimitive(
                                                    "Tool not found: ${call.name}"
                                                )
                                            )
                                        },

                                    isError =
                                        true,
                                )

                            pendingToolResults +=
                                result

                            collectedToolResults +=
                                result

                            emit(
                                AgentLoopEvent
                                    .ToolExecutionFinished(
                                        iteration =
                                            iteration,

                                        result =
                                            result,
                                    )
                            )

                            return@forEach
                        }

                        emit(
                            AgentLoopEvent
                                .ToolExecutionStarted(
                                    iteration =
                                        iteration,

                                    call =
                                        call,
                                )
                        )

                        /*
                         * =========================================
                         * CRITICAL RESTORED FEATURE
                         * =========================================
                         *
                         * Mark the workspace as mutated BEFORE
                         * executing the first write-type tool.
                         *
                         * Without this, FINALIZE never commits
                         * the post-turn snapshot.
                         */
                        turnContext
                            ?.let {
                                context ->

                                if (
                                    call.name in
                                    WRITE_TOOL_NAMES &&
                                    !context
                                        .firstWriteDone
                                ) {

                                    context
                                        .firstWriteDone =
                                        true
                                }
                            }

                        /*
                         * =========================================
                         * EXECUTE ACTUAL TOOL
                         * =========================================
                         */
                        val result =
                            runCatching {

                                tool.execute(
                                    call =
                                        call,

                                    context =
                                        com.malik.lmai
                                            .feature
                                            .agent
                                            .AgentToolContext(
                                                chatId =
                                                    request
                                                        .chatId,

                                                platformUid =
                                                    request
                                                        .platform
                                                        .uid,

                                                iteration =
                                                    iteration,

                                                projectId =
                                                    request
                                                        .projectId
                                                        ?: "",
                                            ),
                                )

                            }.getOrElse {
                                error ->

                                AgentToolResult(
                                    toolCallId =
                                        call.id,

                                    toolName =
                                        call.name,

                                    output =
                                        buildJsonObject {

                                            put(
                                                "error",
                                                JsonPrimitive(
                                                    error.message
                                                        ?: "Tool execution failed"
                                                )
                                            )
                                        },

                                    isError =
                                        true,
                                )
                            }

                        pendingToolResults +=
                            result

                        collectedToolResults +=
                            result

                        /*
                         * =========================================
                         * TRACK FILE MUTATIONS
                         * =========================================
                         */
                        if (
                            !result.isError
                        ) {

                            turnContext
                                ?.let {
                                    context ->

                                    runCatching {

                                        when (
                                            call.name
                                        ) {

                                            "write_project_file",
                                            "edit_project_file" -> {

                                                val path =
                                                    call.arguments
                                                        .requireString(
                                                            "path"
                                                        )

                                                context
                                                    .writtenFiles +=
                                                    path
                                            }

                                            "delete_project_file" -> {

                                                val path =
                                                    call.arguments
                                                        .requireString(
                                                            "path"
                                                        )

                                                context
                                                    .deletedFiles +=
                                                    path
                                            }
                                        }
                                    }
                                }
                        }

                        emit(
                            AgentLoopEvent
                                .ToolExecutionFinished(
                                    iteration =
                                        iteration,

                                    result =
                                        result,
                                )
                        )

                        /*
                         * =========================================
                         * CRITICAL RESTORED PLAN HANDLING
                         * =========================================
                         */
                        when (
                            call.name
                        ) {

                            /*
                             * Create and retain the plan.
                             */
                            "create_plan" -> {

                                parsePlanFromToolResult(
                                    result =
                                        result,

                                    iteration =
                                        iteration,
                                )
                                    ?.let {
                                        plan ->

                                        currentPlan =
                                            plan

                                        emit(
                                            AgentLoopEvent
                                                .PlanCreated(
                                                    iteration =
                                                        iteration,

                                                    plan =
                                                        plan,
                                                )
                                        )
                                    }
                            }

                            /*
                             * Update current plan state.
                             */
                            "update_plan_step" -> {

                                currentPlan
                                    ?.let {
                                        plan ->

                                        updatePlanFromToolResult(
                                            plan =
                                                plan,

                                            result =
                                                result,
                                        )
                                            ?.let {
                                                updatedPlan ->

                                                currentPlan =
                                                    updatedPlan

                                                emit(
                                                    AgentLoopEvent
                                                        .PlanUpdated(
                                                            iteration =
                                                                iteration,

                                                            plan =
                                                                updatedPlan,
                                                        )
                                                )
                                            }
                                    }
                            }
                        }
                    }

                /*
                 * =================================================
                 * DO NOT EARLY-ABORT ON BUILD TOOL ERROR
                 * =================================================
                 *
                 * Important difference from the broken fork:
                 *
                 * run_build_pipeline failures are fed back
                 * into the model.
                 *
                 * The model must get a chance to:
                 *
                 * inspect compiler errors
                 * modify files
                 * rebuild
                 *
                 * This is the core repair loop.
                 */

                /*
                 * Convert tool results into conversation
                 * items.
                 */
                val toolResultItems =
                    pendingToolResults
                        .map {
                            result ->

                            AgentConversationItem(
                                role =
                                    AgentMessageRole
                                        .TOOL,

                                toolCallId =
                                    result.toolCallId,

                                toolName =
                                    result.toolName,

                                payload =
                                    result.output,
                            )
                        }

                /*
                 * Stateless providers need complete
                 * assistant/tool history.
                 */
                fullConversation +=
                    toolResultItems

                /*
                 * Stateful providers only need the
                 * newly produced tool results.
                 */
                conversationDelta =
                    toolResultItems
            }

            /*
             * =====================================================
             * MAX ITERATIONS EXHAUSTED
             * =====================================================
             *
             * Give the model one final TEXT-ONLY turn
             * to summarize current work instead of
             * ending abruptly.
             */
            val windDownMessage =
                AgentConversationItem(
                    role =
                        AgentMessageRole.USER,

                    text =
                        "[System] You have used all available iterations. " +
                            "Do NOT call any more tools. " +
                            "Summarize what you accomplished, " +
                            "including any unresolved build or runtime problems.",
                )

            fullConversation +=
                windDownMessage

            conversationDelta =
                listOf(
                    windDownMessage
                )

            val finalOutput =
                StringBuilder()

            var finalFailureMessage:
                String? =
                null

            emit(
                AgentLoopEvent.ModelTurnStarted(
                    request.policy
                        .maxIterations +
                        1
                )
            )

            val windDownCompaction =
                conversationCompactor
                    .compact(
                        items =
                            fullConversation
                                .toList(),

                        clientType =
                            request.platform
                                .compatibleType,

                        platform =
                            request.platform,
                    )

            agentModelGateway
                .streamTurn(
                    AgentModelRequest(
                        platform =
                            request.platform,

                        diagnosticContext =
                            request
                                .diagnosticContext
                                ?.copy(
                                    platformUid =
                                        request
                                            .platform
                                            .uid
                                ),

                        conversation =
                            conversationDelta,

                        fullConversation =
                            windDownCompaction
                                .items,

                        instructions =
                            buildInstructions(
                                request =
                                    request,

                                activePlan =
                                    currentPlan,

                                mode =
                                    mode,

                                memo =
                                    memo,
                            ),

                        /*
                         * Text-only final response.
                         */
                        tools =
                            emptyList(),

                        policy =
                            request.policy
                                .copy(
                                    toolChoiceMode =
                                        AgentToolChoiceMode
                                            .NONE
                                ),

                        previousResponseId =
                            previousResponseId,
                    )
                )
                .collect {
                    event ->

                    when (event) {

                        is AgentModelEvent
                            .OutputDelta -> {

                            finalOutput
                                .append(
                                    event.delta
                                )

                            emit(
                                AgentLoopEvent
                                    .OutputDelta(
                                        iteration =
                                            request
                                                .policy
                                                .maxIterations +
                                                1,

                                        delta =
                                            event.delta,
                                    )
                            )
                        }

                        is AgentModelEvent
                            .ThinkingDelta -> {

                            emit(
                                AgentLoopEvent
                                    .ThinkingDelta(
                                        iteration =
                                            request
                                                .policy
                                                .maxIterations +
                                                1,

                                        delta =
                                            event.delta,
                                    )
                            )
                        }

                        is AgentModelEvent
                            .Completed -> {

                            previousResponseId =
                                event.responseId
                                    ?: previousResponseId
                        }

                        is AgentModelEvent
                            .Failed -> {

                            finalFailureMessage =
                                event.message
                        }

                        else ->
                            Unit
                    }
                }

            if (
                finalFailureMessage !=
                null
            ) {

                emit(
                    AgentLoopEvent.LoopFailed(
                        message =
                            finalFailureMessage,

                        iteration =
                            request
                                .policy
                                .maxIterations +
                                1,
                    )
                )

                return@flow
            }

            val summary =
                finalOutput
                    .toString()
                    .trim()

            if (
                summary.isNotEmpty()
            ) {

                emit(
                    AgentLoopEvent.LoopCompleted(
                        finalText =
                            summary,

                        toolResults =
                            collectedToolResults
                                .toList(),
                    )
                )

            } else {

                emit(
                    AgentLoopEvent.LoopFailed(
                        message =
                            "Agent loop exceeded max iterations: " +
                                request
                                    .policy
                                    .maxIterations,

                        iteration =
                            request
                                .policy
                                .maxIterations,
                    )
                )
            }

        } finally {

            /*
             * =====================================================
             * FINALIZE TURN
             * =====================================================
             */
            turnContext
                ?.let {
                    context ->

                    runCatching {

                        /*
                         * A successful build is any successful
                         * run_build_pipeline tool result.
                         */
                        val buildSucceeded =
                            collectedToolResults
                                .any {
                                    result ->

                                    result.toolName ==
                                        "run_build_pipeline" &&
                                        !result.isError
                                }

                        /*
                         * Regenerate project outline only when
                         * we know the generated application
                         * compiled successfully.
                         */
                        if (
                            buildSucceeded
                        ) {

                            outlineGenerator
                                .regenerate(
                                    context.projectId,
                                    context.workspaceRoot,
                                    context.vibeDirs,
                                )
                        }

                        /*
                         * Snapshot commit should occur after
                         * at least one write-type operation.
                         *
                         * firstWriteDone is now correctly
                         * flipped BEFORE tool execution.
                         */
                        if (
                            context.firstWriteDone
                        ) {

                            runCatching {

                                context
                                    .snapshotHandle
                                    .commit()
                            }
                        }

                        context
                            .snapshotHandle
                            .finalize(
                                buildSucceeded =
                                    buildSucceeded,

                                affectedFiles =
                                    context
                                        .writtenFiles
                                        .toList(),

                                deletedFiles =
                                    context
                                        .deletedFiles
                                        .toList(),
                            )

                        snapshotManager
                            .enforceRetention(
                                context.projectId,
                                context.vibeDirs,
                            )
                    }
                }
        }
    }

    /*
     * =============================================================
     * INITIAL CROSS-TURN CONVERSATION
     * =============================================================
     */
    private fun buildInitialConversation(
        request: AgentLoopRequest,
    ): List<AgentConversationItem> {

        val items =
            mutableListOf<
                AgentConversationItem
            >()

        request
            .userMessages
            .forEachIndexed {
                index,
                userMessage ->

                items +=
                    userMessage
                        .toAgentConversationItem()

                /*
                 * Prefer assistant response from the
                 * current platform.
                 *
                 * If the user switched provider/model,
                 * retain previous work from another
                 * provider instead of losing history.
                 */
                val assistantsForTurn =
                    request
                        .assistantMessages
                        .getOrNull(
                            index
                        )
                        .orEmpty()

                val assistantForTurn =
                    assistantsForTurn
                        .firstOrNull {
                            it.platformType ==
                                request
                                    .platform
                                    .uid
                        }
                        ?: assistantsForTurn
                            .firstOrNull {
                                it.content
                                    .isNotBlank()
                            }

                if (
                    assistantForTurn !=
                    null &&
                    assistantForTurn
                        .content
                        .isNotBlank()
                ) {

                    items +=
                        assistantForTurn
                            .toAgentConversationItem()
                }
            }

        return compactCrossTurnHistory(
            items =
                items,

            request =
                request,
        )
    }

    /*
     * =============================================================
     * CROSS-TURN HISTORY COMPACTION
     * =============================================================
     */
    private fun compactCrossTurnHistory(
        items: List<AgentConversationItem>,
        request: AgentLoopRequest,
    ): List<AgentConversationItem> {

        val budget =
            ProviderContextBudget
                .forProvider(
                    request.platform
                        .compatibleType
                )

        /*
         * Reserve roughly 40% for:
         *
         * system prompt
         * tool schemas
         * within-loop growth
         */
        val historyBudget =
            (
                budget.maxTokens *
                    0.6
                )
                .toInt()

        val currentTokens =
            ConversationContextManager
                .estimateTokens(
                    items
                )

        if (
            currentTokens <=
            historyBudget
        ) {

            return items
        }

        /*
         * Persisted Room assistant messages do not
         * contain live toolCalls.
         */
        val assistantIndices =
            items
                .indices
                .filter {
                    index ->

                    items[index].role ==
                        AgentMessageRole
                            .ASSISTANT &&
                        items[index]
                            .toolCalls
                            .isNullOrEmpty()
                }
                .reversed()

        if (
            assistantIndices
                .isEmpty()
        ) {

            return items
        }

        val result =
            items
                .toMutableList()

        assistantIndices
            .forEachIndexed {
                rank,
                itemIndex ->

                val item =
                    result[
                        itemIndex
                    ]

                val text =
                    item.text
                        ?: return@forEachIndexed

                val maxChars =
                    when (rank) {

                        0 ->
                            MAX_RECENT_ASSISTANT_CHARS

                        1 ->
                            MAX_OLDER_ASSISTANT_CHARS

                        else ->
                            MAX_SUMMARY_CHARS
                    }

                if (
                    text.length >
                    maxChars
                ) {

                    result[
                        itemIndex
                    ] =
                        item.copy(
                            text =
                                text.take(
                                    maxChars
                                ) +
                                    "\n\n" +
                                    "[... earlier content truncated for context budget]",

                            reasoningContent =
                                null,
                        )
                }
            }

        return result
    }

    /*
     * =============================================================
     * PLAN RESULT PARSER
     * =============================================================
     */
    private fun parsePlanFromToolResult(
        result: AgentToolResult,
        iteration: Int,
    ): AgentPlan? {

        if (
            result.isError
        ) {

            return null
        }

        return try {

            val json =
                result.output
                    .jsonObject

            val summary =
                json[
                    "summary"
                ]
                    ?.jsonPrimitive
                    ?.content
                    ?: return null

            val stepsArray =
                json[
                    "steps"
                ]
                    ?.jsonArray
                    ?: return null

            val steps =
                stepsArray
                    .map {
                        element ->

                        val item =
                            element
                                .jsonObject

                        AgentPlanStep(
                            id =
                                item[
                                    "id"
                                ]
                                    ?.jsonPrimitive
                                    ?.int
                                    ?: 0,

                            description =
                                item[
                                    "description"
                                ]
                                    ?.jsonPrimitive
                                    ?.content
                                    ?: "",

                            status =
                                PlanStepStatus
                                    .PENDING,
                        )
                    }

            AgentPlan(
                summary =
                    summary,

                steps =
                    steps,

                createdAtIteration =
                    iteration,
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }

    /*
     * =============================================================
     * PLAN UPDATE PARSER
     * =============================================================
     */
    private fun updatePlanFromToolResult(
        plan: AgentPlan,
        result: AgentToolResult,
    ): AgentPlan? {

        if (
            result.isError
        ) {

            return null
        }

        return try {

            val json =
                result.output
                    .jsonObject

            val stepId =
                json[
                    "step_id"
                ]
                    ?.jsonPrimitive
                    ?.int
                    ?: return null

            val statusString =
                json[
                    "status"
                ]
                    ?.jsonPrimitive
                    ?.content
                    ?: return null

            val notes =
                json[
                    "notes"
                ]
                    ?.jsonPrimitive
                    ?.content

            val newStatus =
                when (
                    statusString
                        .uppercase()
                ) {

                    "COMPLETED" ->
                        PlanStepStatus
                            .COMPLETED

                    "FAILED" ->
                        PlanStepStatus
                            .FAILED

                    "SKIPPED" ->
                        PlanStepStatus
                            .SKIPPED

                    else ->
                        return null
                }

            val updatedSteps =
                plan.steps
                    .map {
                        step ->

                        when {

                            /*
                             * Update requested step.
                             */
                            step.id ==
                                stepId -> {

                                step.copy(
                                    status =
                                        newStatus,

                                    notes =
                                        notes,
                                )
                            }

                            /*
                             * Automatically make the next
                             * step active after completion.
                             */
                            step.id ==
                                stepId + 1 &&
                                newStatus ==
                                PlanStepStatus
                                    .COMPLETED -> {

                                step.copy(
                                    status =
                                        PlanStepStatus
                                            .IN_PROGRESS
                                )
                            }

                            else ->
                                step
                        }
                    }

            plan.copy(
                steps =
                    updatedSteps
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }

    /*
     * =============================================================
     * ROOM MESSAGE -> AGENT ITEM
     * =============================================================
     */
    private fun MessageV2
        .toAgentConversationItem():
        AgentConversationItem {

        val isAssistant =
            platformType !=
                null

        return AgentConversationItem(
            role =
                if (
                    isAssistant
                ) {

                    AgentMessageRole
                        .ASSISTANT

                } else {

                    AgentMessageRole
                        .USER
                },

            attachments =
                if (
                    isAssistant
                ) {

                    emptyList()

                } else {

                    files
                },

            text =
                buildString {

                    if (
                        isAssistant
                    ) {

                        /*
                         * Preserve a compact summary of the
                         * previous turn's tool activity.
                         */
                        buildTurnWorkSummary(
                            thoughts
                        )
                            ?.let {
                                summary ->

                                append(
                                    summary
                                )

                                append(
                                    "\n\n"
                                )
                            }
                    }

                    append(
                        content
                    )

                    if (
                        files.isNotEmpty()
                    ) {

                        append(
                            "\n\n[Files]\n"
                        )

                        append(
                            files.joinToString(
                                separator =
                                    "\n"
                            )
                        )
                    }

                }.trim(),
        )
    }

    /*
     * =============================================================
     * SYSTEM PROMPT ASSET
     * =============================================================
     */
    private val promptTemplate:
        String by lazy {

        context
            .assets
            .open(
                "agent-system-prompt.md"
            )
            .bufferedReader()
            .use {
                reader ->

                reader.readText()
            }
    }

    /*
     * Additional instructions for modifying an
     * existing generated application.
     */
    private val iterationAppendix:
        String by lazy {

        context
            .assets
            .open(
                "iteration-mode-appendix.md"
            )
            .bufferedReader()
            .use {
                reader ->

                reader.readText()
            }
    }

    /*
     * Last/current user message.
     */
    private fun currentUserText(
        request: AgentLoopRequest,
    ): String? {

        return request
            .userMessages
            .lastOrNull()
            ?.content
    }

    /*
     * =============================================================
     * BUILD SYSTEM INSTRUCTIONS
     * =============================================================
     */
    private suspend fun buildInstructions(
        request: AgentLoopRequest,
        activePlan: AgentPlan? = null,
        mode: AgentMode = AgentMode.GREENFIELD,
        memo: ProjectMemo? = null,
    ): String {

        /*
         * Project package is derived from the
         * generated project ID.
         */
        val packageName =
            request
                .projectId
                ?.let {
                    projectId ->

                    "com.vibe.generated.p$projectId"
                }
                ?: "com.vibe.generated.emptyactivity"

        val packagePath =
            packageName
                .replace(
                    '.',
                    '/'
                )

        /*
         * User/platform custom system prompt.
         */
        val customPrompt =
            request
                .systemPrompt
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: request
                    .platform
                    .systemPrompt
                    ?.takeIf {
                        it.isNotBlank()
                    }

        /*
         * Replace dynamic placeholders in
         * agent-system-prompt.md.
         */
        val basePrompt =
            promptTemplate
                .replace(
                    "{{PACKAGE_NAME}}",
                    packageName,
                )
                .replace(
                    "{{PACKAGE_PATH}}",
                    packagePath,
                )

        /*
         * Add iteration-mode context and project
         * memo when modifying an existing app.
         */
        val assembled =
            PromptAssembler
                .assemble(
                    basePrompt =
                        basePrompt,

                    iterationAppendix =
                        iterationAppendix,

                    mode =
                        mode,

                    memo =
                        memo,
                )

        return buildString {

            append(
                assembled
            )

            /*
             * Custom user/provider instructions.
             */
            if (
                customPrompt !=
                null
            ) {

                append(
                    "\n\n[Additional System Prompt]\n"
                )

                append(
                    customPrompt
                )
            }

            /*
             * =====================================================
             * CRITICAL RESTORED ACTIVE PLAN
             * =====================================================
             *
             * Re-inject plan status on every model
             * iteration.
             */
            if (
                activePlan !=
                null
            ) {

                append(
                    "\n\n[Active Plan]\n"
                )

                append(
                    "Goal: ${activePlan.summary}\n"
                )

                activePlan
                    .steps
                    .forEach {
                        step ->

                        val status =
                            when (
                                step.status
                            ) {

                                PlanStepStatus.COMPLETED ->
                                    "done"

                                PlanStepStatus.IN_PROGRESS ->
                                    "current"

                                PlanStepStatus.FAILED ->
                                    "failed"

                                PlanStepStatus.SKIPPED ->
                                    "skipped"

                                PlanStepStatus.PENDING ->
                                    "pending"
                            }

                        append(
                            "  [$status] ${step.id}. ${step.description}"
                        )

                        step.notes
                            ?.let {
                                notes ->

                                append(
                                    " ($notes)"
                                )
                            }

                        append(
                            "\n"
                        )
                    }

                append(
                    "Continue with the next pending step. " +
                        "Call update_plan_step after completing each step.\n"
                )
            }
        }
    }

    companion object {

        /*
         * =========================================================
         * WRITE INTERCEPTOR
         * =========================================================
         *
         * These tools mutate the generated project
         * and therefore trigger snapshot commit.
         */
        private val WRITE_TOOL_NAMES:
            Set<String> =
            setOf(
                "write_project_file",
                "edit_project_file",
                "delete_project_file",
                "update_project_icon",
                "update_project_icon_custom",
            )

        /*
         * Cross-turn history limits.
         */
        private const val MAX_RECENT_ASSISTANT_CHARS =
            4000

        private const val MAX_OLDER_ASSISTANT_CHARS =
            1500

        private const val MAX_SUMMARY_CHARS =
            500
    }
}
