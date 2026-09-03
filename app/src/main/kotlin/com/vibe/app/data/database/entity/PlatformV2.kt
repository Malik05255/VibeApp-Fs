package com.vibe.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vibe.app.data.model.ClientType
import java.util.UUID

@Entity(tableName = "platform_v2")
data class PlatformV2(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "platform_id")
    val id: Int = 0,

    @ColumnInfo(name = "uid")
    val uid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "compatible_type")
    val compatibleType: ClientType,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = false,

    @ColumnInfo(name = "api_url")
    val apiUrl: String,

    @ColumnInfo(name = "token")
    val token: String? = null,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    @ColumnInfo(name = "is_free")
    val isFree: Boolean? = null,

    @ColumnInfo(name = "pricing_prompt")
    val pricingPrompt: String? = null,

    @ColumnInfo(name = "pricing_completion")
    val pricingCompletion: String? = null,

    @ColumnInfo(name = "temperature")
    val temperature: Float? = null,

    @ColumnInfo(name = "top_p")
    val topP: Float? = null,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "stream")
    val stream: Boolean = true,

    @ColumnInfo(name = "reasoning")
    val reasoning: Boolean = false,

    @ColumnInfo(name = "timeout")
    val timeout: Int = 30

) {

    val top: Float?
        get() = topP
}
