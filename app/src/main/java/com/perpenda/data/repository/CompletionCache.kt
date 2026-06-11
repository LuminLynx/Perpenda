package com.perpenda.data.repository

import android.content.Context
import android.content.SharedPreferences

interface CompletionCache {
    fun completedUnitIds(): Set<String>
    fun add(unitId: String)
    /**
     * Replace the cached set for the current user with `unitIds` exactly.
     * Used to seed the cache from a fresh `GET /api/v1/completions` so
     * completion state syncs across devices for the same account.
     */
    fun replaceAll(unitIds: Set<String>)
    fun clear()
}

/**
 * SharedPreferences-backed completion cache keyed per authenticated user id.
 *
 * Reads / writes silently no-op when no user is signed in (the cache is only
 * meaningful for an authenticated session anyway, since completions are
 * recorded by the JWT-protected grade endpoint). Keying entries per user
 * id prevents one account's progress from leaking into another's "Continue"
 * computation when multiple accounts are used on the same device.
 */
class SharedPrefsCompletionCache(
    context: Context,
    private val userIdProvider: () -> String?
) : CompletionCache {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    override fun completedUnitIds(): Set<String> {
        val userId = userIdProvider() ?: return emptySet()
        return prefs.getStringSet(keyFor(userId), emptySet())?.toSet() ?: emptySet()
    }

    override fun add(unitId: String) {
        val userId = userIdProvider() ?: return
        val key = keyFor(userId)
        val current = prefs.getStringSet(key, emptySet()) ?: emptySet()
        if (unitId in current) return
        prefs.edit().putStringSet(key, current + unitId).apply()
    }

    override fun replaceAll(unitIds: Set<String>) {
        val userId = userIdProvider() ?: return
        prefs.edit().putStringSet(keyFor(userId), unitIds.toSet()).apply()
    }

    override fun clear() {
        val userId = userIdProvider() ?: return
        prefs.edit().remove(keyFor(userId)).apply()
    }

    private fun keyFor(userId: String): String = "$KEY_PREFIX:$userId"

    private companion object {
        const val FILE_NAME = "ai101_completion_cache"
        const val KEY_PREFIX = "completed_unit_ids"
    }
}
