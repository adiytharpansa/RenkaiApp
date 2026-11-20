package com.renkai.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Comment(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: Long,
)

/**
 * Penyimpanan komentar di perangkat (SharedPreferences), dipisah per judul + episode.
 *
 * Catatan: komentar hanya tersimpan di perangkat ini dan tidak terlihat pengguna lain.
 * Kalau nanti ada server komentar, cukup ganti isi kelas ini (load/add/remove);
 * UI tidak perlu diubah.
 */
class CommentStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("renkai_comments", Context.MODE_PRIVATE)

    private fun key(titleId: String, episode: Int) = "c:$titleId:$episode"

    /** Komentar terbaru di urutan pertama. */
    fun load(titleId: String, episode: Int): List<Comment> {
        val raw = prefs.getString(key(titleId, episode), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Comment(
                    id = o.getString("id"),
                    author = o.optString("author", DEFAULT_AUTHOR),
                    text = o.getString("text"),
                    createdAt = o.optLong("t"),
                )
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(titleId: String, episode: Int, text: String, author: String = DEFAULT_AUTHOR): Comment {
        val now = System.currentTimeMillis()
        val c = Comment(id = "$now-${(0..9999).random()}", author = author, text = text.trim(), createdAt = now)
        save(titleId, episode, listOf(c) + load(titleId, episode))
        return c
    }

    fun remove(titleId: String, episode: Int, id: String) {
        save(titleId, episode, load(titleId, episode).filterNot { it.id == id })
    }

    private fun save(titleId: String, episode: Int, list: List<Comment>) {
        val arr = JSONArray()
        list.take(MAX_PER_EPISODE).forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("author", c.author)
                    .put("text", c.text)
                    .put("t", c.createdAt)
            )
        }
        prefs.edit().putString(key(titleId, episode), arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_AUTHOR = "Kamu"
        const val MAX_LENGTH = 500
        private const val MAX_PER_EPISODE = 200
    }
}
