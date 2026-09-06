package com.malik.lmai.util

import com.malik.lmai.data.database.entity.PlatformV2

fun List<PlatformV2>.getPlatformName(uid: String): String = this.find { it.uid == uid }?.name ?: "Unknown"
