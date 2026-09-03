package com.vibe.app.data.supabase

import com.vibe.app.BuildConfig

object SupabaseConfig {
    val url: String
        get() = BuildConfig.SUPABASE_URL

    val anonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY
}
