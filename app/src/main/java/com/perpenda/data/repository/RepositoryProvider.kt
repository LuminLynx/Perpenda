package com.perpenda.data.repository

import android.content.Context
import com.perpenda.data.auth.AuthApiServiceFactory
import com.perpenda.data.auth.EncryptedTokenStorage
import com.perpenda.data.auth.TokenStorage
import com.perpenda.data.remote.network.ApiConfig
import com.perpenda.data.remote.network.GlossaryApiServiceFactory
import com.perpenda.data.remote.network.PathApiServiceFactory
import com.perpenda.data.settings.ThemePreferenceStore

object RepositoryProvider {

    private var tokenStorage: TokenStorage? = null
    private var completionCache: CompletionCache? = null
    private var themePreferenceStore: ThemePreferenceStore? = null

    fun init(context: Context) {
        if (tokenStorage == null) {
            tokenStorage = EncryptedTokenStorage(context.applicationContext)
        }
        if (completionCache == null) {
            val storage = tokenStorage!!
            completionCache = SharedPrefsCompletionCache(
                context = context.applicationContext,
                userIdProvider = { storage.getUserId() }
            )
        }
        if (themePreferenceStore == null) {
            themePreferenceStore = ThemePreferenceStore(context.applicationContext)
        }
    }

    val authRepository: AuthRepository by lazy {
        val storage = requireTokenStorage()
        val config = ApiConfig.fromBuildConfig()
        ApiAuthRepository(
            authApiService = AuthApiServiceFactory.create(config),
            tokenStorage = storage,
            completionCache = requireCompletionCache()
        )
    }

    val glossaryRepository: GlossaryRepository by lazy {
        val config = ApiConfig.fromBuildConfig()
        val storage = requireTokenStorage()
        ApiGlossaryRepository(
            glossaryApiService = GlossaryApiServiceFactory.create(
                config = config,
                tokenProvider = { storage.getToken() }
            )
        )
    }

    val pathRepository: PathRepository by lazy {
        val config = ApiConfig.fromBuildConfig()
        val storage = requireTokenStorage()
        ApiPathRepository(
            pathApiService = PathApiServiceFactory.create(
                config = config,
                tokenProvider = { storage.getToken() }
            ),
            completionCache = requireCompletionCache()
        )
    }

    val completionCacheInstance: CompletionCache
        get() = requireCompletionCache()

    val themePreferenceStoreInstance: ThemePreferenceStore
        get() = themePreferenceStore
            ?: error("RepositoryProvider.init(context) must be called before accessing the theme store.")

    private fun requireTokenStorage(): TokenStorage {
        return tokenStorage
            ?: error("RepositoryProvider.init(context) must be called before accessing repositories.")
    }

    private fun requireCompletionCache(): CompletionCache {
        return completionCache
            ?: error("RepositoryProvider.init(context) must be called before accessing repositories.")
    }
}
