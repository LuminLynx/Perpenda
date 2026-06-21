package com.perpenda.data.remote.network

import com.perpenda.BuildConfig

data class ApiConfig(
    val baseUrl: String,
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 15_000
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.perpenda.com/"

        fun fromBuildConfig(): ApiConfig {
            val configuredBaseUrl = BuildConfig.API_BASE_URL
                .ifBlank { DEFAULT_BASE_URL }
                .let { if (it.endsWith('/')) it else "$it/" }
            return ApiConfig(baseUrl = configuredBaseUrl)
        }
    }
}
