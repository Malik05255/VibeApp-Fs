package com.malik.lmai.data.dto

import com.malik.lmai.data.model.DynamicTheme
import com.malik.lmai.data.model.ThemeMode

data class ThemeSetting(
    val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    val themeMode: ThemeMode = ThemeMode.LIGHT
)
