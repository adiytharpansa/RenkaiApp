package com.renkai.app.data

import android.util.Base64
import com.renkai.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class Episode(
    val number: Int,
    val playlist: String,
    val title: String? = null,
)

data class Title(
    val id: String,
    val title: String,
    val poster: String? = null,
    val banner: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val score: Double? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<Episode> = emptyList(),
) {
    val matchPercent: Int? get() = score?.let { (it * 10).toInt().coerceIn(0, 100) }
    val statusLabel: String?
        get() = when (status) {
            "FINISHED" -> "Tamat"
            "RELEASING" -> "Sedang Tayang"
            "NOT_YET_RELEASED" -> "Segera"
            else -> status
        }
}

// Sumber katalog utama: Cloudflare Pages (katalog terenkripsi AES-256-GCM,
// disinkronkan otomatis dari repo gudang). Cermin: jsDelivr beberapa edge +
// raw GitHub (JSON polos) — decodeCatalogBody menerima kedua bentuk.
private const val CATALOG_PAGES =
    "https://renkai-catalog.pages.dev/57601a04df504659829aa8f0/lib.dat"
private val CATALOG_MIRRORS = listOf(
    "https://cdn.jsdelivr.net/gh/adiytharpansa/xjatxz2upew4@main/catalog.json",
    "https://gcore.jsdelivr.net/gh/adiytharpansa/xjatxz2upew4@main/catalog.json",
    "https://fastly.jsdelivr.net/gh/adiytharpansa/xjatxz2upew4@main/catalog.json",
    "https://raw.githubusercontent.com/adiytharpansa/xjatxz2upew4/main/catalog.json",
)
private const val PROJECT_REPO = "adiytharpansa/xjatxz2upew4"

object CatalogApi {
    @Volatile
    private var cached: List<Title>? = null

    @Volatile
    var lastError: String? = null
        private set

    private fun httpGetBytes(url: String, timeoutMs: Int = 20000): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Renkai/1.0 (Android)")
        conn.setRequestProperty("Accept-Encoding", "gzip")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val stream = if (conn.contentEncoding?.contains("gzip") == true) {
                java.util.zip.GZIPInputStream(conn.inputStream)
            } else conn.inputStream
            return stream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetText(url: String, timeoutMs: Int = 5000): String =
        String(httpGetBytes(url, timeoutMs), Charsets.UTF_8)

    private fun decodeCatalogBody(bytes: ByteArray): String {
        if (bytes.isNotEmpty() && (bytes[0] == '{'.code.toByte() || bytes[0] == '['.code.toByte())) {
            return String(bytes, Charsets.UTF_8)
        }
        return String(decryptCatalog(bytes), Charsets.UTF_8)
    }

    private fun decryptCatalog(data: ByteArray): ByteArray {
        require(data.size > 28) { "berkas terenkripsi terlalu pendek" }
        val masked = Base64.decode(BuildConfig.CATALOG_KEY_X, Base64.DEFAULT)
        require(masked.size == 32) { "kunci katalog tidak tersedia" }
        val mask = MessageDigest.getInstance("SHA-256")
            .digest("renkai-katalog-v1".toByteArray(Charsets.UTF_8))
        val key = ByteArray(32) { i -> (masked[i].toInt() xor mask[i].toInt()).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, data.copyOfRange(0, 12)),
        )
        return cipher.doFinal(data.copyOfRange(12, data.size))
    }

    fun parseCatalog(body: String): List<Title> {
        val root = JSONObject(body)
        val rootPin = root.optString("pin")
        val arr = root.optJSONArray("titles") ?: JSONArray()
        val out = ArrayList<Title>()
        for (i in 0 until arr.length()) {
            try {
                val t = arr.getJSONObject(i)
                val tRepo = t.optString("repo")
                val tPin = t.optString("pin")
                val base = when {
                    tRepo.isNotBlank() && tPin.isNotBlank() ->
                        "https://cdn.jsdelivr.net/gh/$tRepo@$tPin"
                    rootPin.isNotBlank() ->
                        "https://cdn.jsdelivr.net/gh/$PROJECT_REPO@$rootPin"
                    else -> null
                }
                fun absolut(path: String): String =
                    if (path.startsWith("http") || base == null) path else "$base/$path"
                val eps = ArrayList<Episode>()
                val ea = t.optJSONArray("episodes") ?: JSONArray()
                for (j in 0 until ea.length()) {
                    when (val item = ea.opt(j)) {
                        is JSONObject -> eps.add(
                            Episode(
                                number = item.optInt("number"),
                                playlist = absolut(item.optString("playlist")),
                                title = item.optString("title").ifBlank { null },
                            )
                        )
                        is Number -> {
                            val n = item.toInt()
                            eps.add(
                                Episode(
                                    number = n,
                                    playlist = absolut(t.optString("id") + "/ep" + n + "/playlist.m3u8"),
                                )
                            )
                        }
                    }
                }
                if (eps.isEmpty()) continue
                val genres = ArrayList<String>()
                val ga = t.optJSONArray("genres") ?: JSONArray()
                for (j in 0 until ga.length()) genres.add(ga.optString(j))
                out.add(
                    Title(
                        id = t.optString("id"),
                        title = t.optString("title"),
                        poster = t.optString("poster").ifBlank { null },
                        banner = t.optString("banner").ifBlank { null },
                        synopsis = t.optString("synopsis").ifBlank { null },
                        year = if (t.has("year") && !t.isNull("year")) t.optInt("year") else null,
                        score = if (t.has("score") && !t.isNull("score")) t.optDouble("score") else null,
                        status = t.optString("status").ifBlank { null },
                        genres = genres,
                        episodes = eps.sortedBy { it.number },
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
        return out
    }

    suspend fun fetchCatalog(force: Boolean = false): List<Title> = withContext(Dispatchers.IO) {
        if (!force) cached?.let { return@withContext it }
        val urls = listOf(CATALOG_PAGES) + CATALOG_MIRRORS
        val errs = ArrayList<String>()
        for (u in urls) {
            try {
                val body = decodeCatalogBody(httpGetBytes(u))
                if (!body.trimStart().startsWith("{")) throw IOException("isi tidak dikenal")
                val parsed = parseCatalog(body)
                if (parsed.isEmpty()) throw IOException("katalog kosong")
                cached = parsed
                lastError = null
                return@withContext parsed
            } catch (e: Exception) {
                errs.add("${e.javaClass.simpleName}: ${e.message?.take(80) ?: "?"}")
            }
        }
        lastError = errs.joinToString(" · ")
        throw IOException(lastError ?: "semua sumber katalog gagal")
    }

    /**
     * Pintu-pintu CDN untuk satu URL playlist gudang: berkas yang sama bisa
     * diambil lewat banyak pintu jsDelivr/raw. Pin segar kadang 403 di satu
     * edge tapi sudah terbaca di pintu lain.
     */
    fun doorVariants(url: String): List<String> {
        val m = Regex("""^https://[A-Za-z0-9.-]+/gh/([^/]+)/([^/@]+)@([^/]+)/(.+)$""").find(url)
            ?: return listOf(url)
        val (owner, repo, pin, path) = m.destructured
        val gh = "$owner/$repo@$pin/$path"
        val raw = "$owner/$repo/$pin/$path"
        return listOf(
            "https://cdn.jsdelivr.net/gh/$gh",
            "https://fastly.jsdelivr.net/gh/$gh",
            "https://gcore.jsdelivr.net/gh/$gh",
            "https://raw.githubusercontent.com/$raw",
            "https://cdn.statically.io/gh/$gh",
        )
    }

    /** Pilih pintu pertama yang menjawab playlist valid (#EXTM3U). */
    suspend fun pickDoor(url: String): String = withContext(Dispatchers.IO) {
        val doors = doorVariants(url)
        if (doors.size == 1) return@withContext url
        for (d in doors) {
            try {
                if (httpGetText(d, timeoutMs = 5000).trimStart().startsWith("#EXTM3U")) {
                    return@withContext d
                }
            } catch (e: Exception) {
                // coba pintu berikutnya
            }
        }
        url
    }
}
