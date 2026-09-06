package com.malik.lmai.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.malik.lmai.data.network.NetworkClient
import com.malik.lmai.data.network.OpenAIAPI
import com.malik.lmai.data.network.OpenAIAPIImpl
import com.malik.lmai.data.network.OpenRouterModelsAPI
import com.malik.lmai.feature.diagnostic.ChatDiagnosticLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {


    @Provides
    @Singleton
    fun provideNetworkClient(
        @ApplicationContext context: Context
    ): NetworkClient {

        return NetworkClient(

            OkHttp.create {

                addInterceptor(

                    ChuckerInterceptor.Builder(context)
                        .build()

                )

            }

        )

    }



    @Provides
    @Singleton
    fun provideKtorHttpClient(): HttpClient {

        return HttpClient(OkHttp) {

            install(ContentNegotiation) {

                json(
                    Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    }
                )

            }

        }

    }



    @Provides
    @Singleton
    fun provideOpenAIAPI(
        networkClient: NetworkClient,
        diagnosticLogger: ChatDiagnosticLogger
    ): OpenAIAPI {

        return OpenAIAPIImpl(

            networkClient = networkClient,

            diagnosticLogger = diagnosticLogger

        )

    }



    @Provides
    @Singleton
    fun provideOpenRouterModelsAPI(
        httpClient: HttpClient
    ): OpenRouterModelsAPI {

        return OpenRouterModelsAPI(httpClient)

    }

}
