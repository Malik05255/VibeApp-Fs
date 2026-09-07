package com.malik.lmai.data

object ModelConstants {

    const val OPENROUTER_API_URL =
        "https://openrouter.ai/api"

    const val GOOGLE_AI_STUDIO_API_URL =
        "https://generativelanguage.googleapis.com/v1beta/openai"

    const val CUSTOM_API_URL =
        ""

    const val DEFAULT_PROVIDER =
        "OPEN_ROUTER"

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
