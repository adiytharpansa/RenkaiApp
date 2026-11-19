package com.renkai.app.data

import java.net.URI

/** Membentuk URL alternatif untuk aset publik yang sama pada mirror GitHub CDN. */
internal object CdnDoorVariants {
    private val jsDelivrPath = Regex("""^/gh/([^/]+)/([^/@]+)@([^/]+)/(.+)$""")
    private val rawGitHubPath = Regex("""^/([^/]+)/([^/]+)/([^/]+)/(.+)$""")

    fun forUrl(url: String): List<String> {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return listOf(url)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return listOf(url)
        if (uri.port != -1 && uri.port != 443) return listOf(url)
        // Jangan meneruskan query (yang mungkin berisi kredensial) ke domain mirror lain.
        if (uri.rawQuery != null) return listOf(url)

        val host = uri.host?.lowercase() ?: return listOf(url)
        val path = uri.rawPath ?: return listOf(url)
        val coordinates = when (host) {
            "cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net", "cdn.statically.io" ->
                jsDelivrPath.matchEntire(path)?.destructured?.let { (owner, repo, pin, assetPath) ->
                    Coordinates(owner, repo, pin, assetPath)
                }

            "raw.githubusercontent.com" ->
                rawGitHubPath.matchEntire(path)?.destructured?.let { (owner, repo, pin, assetPath) ->
                    Coordinates(owner, repo, pin, assetPath)
                }

            else -> null
        } ?: return listOf(url)

        val (owner, repo, pin, assetPath) = coordinates
        val ghPath = "/gh/$owner/$repo@$pin/$assetPath"
        val rawPath = "/$owner/$repo/$pin/$assetPath"
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return listOf(
            "https://cdn.jsdelivr.net$ghPath$fragment",
            "https://fastly.jsdelivr.net$ghPath$fragment",
            "https://gcore.jsdelivr.net$ghPath$fragment",
            "https://raw.githubusercontent.com$rawPath$fragment",
            "https://cdn.statically.io$ghPath$fragment",
        ).distinct()
    }

    private data class Coordinates(
        val owner: String,
        val repo: String,
        val pin: String,
        val assetPath: String,
    )
}
