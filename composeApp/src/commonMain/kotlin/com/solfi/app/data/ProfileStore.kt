package com.solfi.app.data

import com.solfi.app.platform.KeyValueStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Persists profiles and their progress in a [KeyValueStore] (JSON strings). */
class ProfileStore(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadProfiles(): List<Profile> = try {
        kv.getString(KEY_PROFILES)?.let { json.decodeFromString(ListSerializer(Profile.serializer()), it) } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun saveProfiles(list: List<Profile>) = kv.putString(KEY_PROFILES, json.encodeToString(ListSerializer(Profile.serializer()), list))

    fun currentId(): String? = kv.getString(KEY_CURRENT)
    fun setCurrentId(id: String?) { if (id == null) kv.remove(KEY_CURRENT) else kv.putString(KEY_CURRENT, id) }

    fun loadProgress(profileId: String): Progress = try {
        kv.getString("progress.$profileId")?.let { json.decodeFromString(Progress.serializer(), it) } ?: Progress()
    } catch (e: Exception) { Progress() }

    fun saveProgress(profileId: String, p: Progress) = kv.putString("progress.$profileId", json.encodeToString(Progress.serializer(), p))

    fun deleteProfile(profileId: String) { kv.remove("progress.$profileId") }

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_CURRENT = "currentProfile"
    }
}
