package com.malik.lmai.di

import android.content.Context
import com.malik.lmai.build.engine.apk.AndroidApkBuilder
import com.malik.lmai.build.engine.compiler.JavacCompiler
import com.malik.lmai.build.engine.dex.D8DexConverter
import com.malik.lmai.build.engine.pipeline.ApkBuilder
import com.malik.lmai.build.engine.pipeline.ApkSigner
import com.malik.lmai.build.engine.pipeline.BuildPipeline
import com.malik.lmai.build.engine.pipeline.Compiler
import com.malik.lmai.build.engine.pipeline.DefaultBuildPipeline
import com.malik.lmai.build.engine.pipeline.DexConverter
import com.malik.lmai.build.engine.pipeline.ResourceCompiler
import com.malik.lmai.build.engine.resource.Aapt2ResourceCompiler
import com.malik.lmai.build.engine.sign.DebugApkSigner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BuildEngineModule {

    @Provides
    @Singleton
    fun provideResourceCompiler(
        @ApplicationContext context: Context,
    ): ResourceCompiler = Aapt2ResourceCompiler(context)

    @Provides
    @Singleton
    fun provideCompiler(
        @ApplicationContext context: Context,
    ): Compiler = JavacCompiler(context)

    @Provides
    @Singleton
    fun provideDexConverter(
        @ApplicationContext context: Context,
    ): DexConverter = D8DexConverter(context)

    @Provides
    @Singleton
    fun provideApkBuilder(
        @ApplicationContext context: Context,
    ): ApkBuilder = AndroidApkBuilder(context)

    @Provides
    @Singleton
    fun provideApkSigner(
        @ApplicationContext context: Context,
    ): ApkSigner = DebugApkSigner(context)

    @Provides
    @Singleton
    fun provideBuildPipeline(
        @ApplicationContext context: Context,
        resourceCompiler: ResourceCompiler,
        compiler: Compiler,
        dexConverter: DexConverter,
        apkBuilder: ApkBuilder,
        apkSigner: ApkSigner,
    ): BuildPipeline = DefaultBuildPipeline(
        context = context,
        resourceCompiler = resourceCompiler,
        compiler = compiler,
        dexConverter = dexConverter,
        apkBuilder = apkBuilder,
        apkSigner = apkSigner,
    )
}
