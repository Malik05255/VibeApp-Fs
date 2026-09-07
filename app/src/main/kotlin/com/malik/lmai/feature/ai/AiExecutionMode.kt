package com.malik.lmai.feature.ai

enum class AiExecutionMode {
    AUTOMATIC,
    MANUAL;

    companion object {
        fun fromStoredValue(value: String?): AiExecutionMode =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}
