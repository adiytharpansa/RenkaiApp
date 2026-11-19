package com.renkai.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.userDataStore by preferencesDataStore(name = "renkai_user")

/** Penyimpanan permanen: koleksi (daftar id judul) dan posisi terakhir tonton per episode. */
class UserStore(context: Context) {
    private val store = context.applicationContext.userDataStore
    private val favKey = stringPreferencesKey("favorites")
    private val posKey = stringPreferencesKey("positions")

    val favorites: Flow<List<String>> =
        store.data.map { parseIds(it[favKey]) }.distinctUntilChanged()

    val positions: Flow<Map<String, Long>> =
        store.data.map { parsePositions(it[posKey]) }.distinctUntilChanged()

    suspend fun toggleFavorite(id: String) {
        store.edit { p ->
            val list = parseIds(p[favKey]).toMutableList()
            if (!list.remove(id)) list.add(id)
            p[favKey] = JSONArray(list).toString()
        }
    }

    suspend fun setPosition(key: String, ms: Long) {
        store.edit { p ->
            val map = LinkedHashMap<String, Long>(parsePositions(p[posKey]))
            map.remove(key)
            map[key] = ms
            while (map.size > MAX_POSITIONS) map.remove(map.keys.first())
            p[posKey] = positionsToJson(map)
        }
    }

    suspend fun clearPosition(key: String) {
        store.edit { p ->
            val map = LinkedHashMap<String, Long>(parsePositions(p[posKey]))
            if (map.remove(key) != null) p[posKey] = positionsToJson(map)
        }
    }

    private companion object {
        const val MAX_POSITIONS = 200
    }
}

private fun parseIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parsePositions(raw: String?): Map<String, Long> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        val o = JSONObject(raw)
        val out = LinkedHashMap<String, Long>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.getLong(k)
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun positionsToJson(m: Map<String, Long>): String {
    val o = JSONObject()
    for ((k, v) in m) o.put(k, v)
    return o.toString()
}
