package com.malik.lmai.feature.agent.loop.compaction

import com.malik.lmai.data.model.ClientType

data class ProviderContextBudget(
    val maxTokens: Int,
    val recentTurns: Int,
) {

    companion object {

        fun forProvider(
            clientType: ClientType
        ): ProviderContextBudget = when (clientType) {

            ClientType.OPEN_ROUTER ->
                ProviderContextBudget(
                    maxTokens = 60_000,
                    recentTurns = 5
                )

            ClientType.CUSTOM ->
                ProviderContextBudget(
                    maxTokens = 60_000,
                    recentTurns = 5
                )
                
            // تمت إضافة فرع else لتغطية بقية الحالات (مثل OPENAI و ANTHROPIC وغيرها) 
            // وجعل عبارة when مكتملة (exhaustive)
            else -> 
                ProviderContextBudget(
                    maxTokens = 60_000, // يمكنك تعديل هذه القيم الافتراضية للأنواع الأخرى إذا أردت
                    recentTurns = 5
                )
        }

    }

}
