package com.solfi.app.content

import com.solfi.app.resources.Res

/** Loads level files bundled as compose resources (`files/content/levelN.json`). Missing levels are simply absent. */
object ContentRepository {
    const val MAX_LEVELS = 5

    fun path(level: Int) = "files/content/level$level.json"

    /** Returns levels 1..5 (null where the file is missing or invalid). */
    suspend fun loadAll(): List<LevelContent?> = (1..MAX_LEVELS).map { n ->
        try {
            val bytes = Res.readBytes(path(n))
            ContentJson.decodeLevel(bytes.decodeToString())
        } catch (e: Exception) {
            null
        }
    }
}
