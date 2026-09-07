package com.malik.lmai.di

import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.tool.DeleteProjectFileTool
import com.malik.lmai.feature.agent.tool.EditProjectFileTool
import com.malik.lmai.feature.agent.tool.FixCrashGuideTool
import com.malik.lmai.feature.agent.tool.CloseAppTool
import com.malik.lmai.feature.agent.tool.InspectUiTool
import com.malik.lmai.feature.agent.tool.LaunchAppTool
import com.malik.lmai.feature.agent.tool.InteractUiTool
import com.malik.lmai.feature.agent.tool.ListProjectFilesTool
import com.malik.lmai.feature.agent.tool.ReadProjectFileTool
import com.malik.lmai.feature.agent.tool.ReadRuntimeLogTool
import com.malik.lmai.feature.agent.tool.RenameProjectTool
import com.malik.lmai.feature.agent.tool.RunBuildPipelineTool
import com.malik.lmai.feature.agent.tool.SearchIconTool
import com.malik.lmai.feature.agent.tool.UpdateProjectIconCustomTool
import com.malik.lmai.feature.agent.tool.UpdateProjectIconTool
import com.malik.lmai.feature.agent.tool.WriteProjectFileTool
import com.malik.lmai.feature.agent.tool.CreatePlanTool
import com.malik.lmai.feature.agent.tool.UpdatePlanStepTool
import com.malik.lmai.feature.agent.tool.WebSearchTool
import com.malik.lmai.feature.agent.tool.FetchWebPageTool
import com.malik.lmai.feature.agent.tool.GrepProjectFilesTool
import com.malik.lmai.feature.agent.tool.SearchUiPatternTool
import com.malik.lmai.feature.agent.tool.GetUiPatternTool
import com.malik.lmai.feature.agent.tool.GetDesignGuideTool
import com.malik.lmai.feature.agent.tool.GetProjectMemoTool
import com.malik.lmai.feature.agent.tool.UpdateProjectIntentTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentToolModule {

    @Binds @IntoSet abstract fun bindReadProjectFile(tool: ReadProjectFileTool): AgentTool
    @Binds @IntoSet abstract fun bindWriteProjectFile(tool: WriteProjectFileTool): AgentTool
    @Binds @IntoSet abstract fun bindEditProjectFile(tool: EditProjectFileTool): AgentTool
    @Binds @IntoSet abstract fun bindDeleteProjectFile(tool: DeleteProjectFileTool): AgentTool
    @Binds @IntoSet abstract fun bindListProjectFiles(tool: ListProjectFilesTool): AgentTool
    @Binds @IntoSet abstract fun bindGrepProjectFiles(tool: GrepProjectFilesTool): AgentTool
    @Binds @IntoSet abstract fun bindRunBuildPipeline(tool: RunBuildPipelineTool): AgentTool
    @Binds @IntoSet abstract fun bindRenameProject(tool: RenameProjectTool): AgentTool
    @Binds @IntoSet abstract fun bindSearchIcon(tool: SearchIconTool): AgentTool
    @Binds @IntoSet abstract fun bindUpdateProjectIcon(tool: UpdateProjectIconTool): AgentTool
    @Binds @IntoSet abstract fun bindUpdateProjectIconCustom(tool: UpdateProjectIconCustomTool): AgentTool
    @Binds @IntoSet abstract fun bindReadRuntimeLog(tool: ReadRuntimeLogTool): AgentTool
    @Binds @IntoSet abstract fun bindFixCrashGuide(tool: FixCrashGuideTool): AgentTool
    @Binds @IntoSet abstract fun bindLaunchApp(tool: LaunchAppTool): AgentTool
    @Binds @IntoSet abstract fun bindInspectUi(tool: InspectUiTool): AgentTool
    @Binds @IntoSet abstract fun bindInteractUi(tool: InteractUiTool): AgentTool
    @Binds @IntoSet abstract fun bindCloseApp(tool: CloseAppTool): AgentTool
    @Binds @IntoSet abstract fun bindCreatePlan(tool: CreatePlanTool): AgentTool
    @Binds @IntoSet abstract fun bindUpdatePlanStep(tool: UpdatePlanStepTool): AgentTool
    @Binds @IntoSet abstract fun bindWebSearch(tool: WebSearchTool): AgentTool
    @Binds @IntoSet abstract fun bindFetchWebPage(tool: FetchWebPageTool): AgentTool
    @Binds @IntoSet abstract fun bindSearchUiPattern(tool: SearchUiPatternTool): AgentTool
    @Binds @IntoSet abstract fun bindGetUiPattern(tool: GetUiPatternTool): AgentTool
    @Binds @IntoSet abstract fun bindGetDesignGuide(tool: GetDesignGuideTool): AgentTool
    @Binds @IntoSet abstract fun bindUpdateProjectIntent(tool: UpdateProjectIntentTool): AgentTool
    @Binds @IntoSet abstract fun bindGetProjectMemo(tool: GetProjectMemoTool): AgentTool
}
