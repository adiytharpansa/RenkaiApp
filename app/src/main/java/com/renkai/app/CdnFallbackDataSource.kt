package com.renkai.app

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.renkai.app.data.CdnDoorVariants
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@UnstableApi
internal class CdnFallbackDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = CdnFallbackDataSource(upstreamFactory)
}

@UnstableApi
private class CdnFallbackDataSource(
    private val upstreamFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = CopyOnWriteArrayList<TransferListener>()
    private var upstream: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        close()

        val requestedUri = dataSpec.uri
        val hasSensitiveHeaders = dataSpec.httpRequestHeaders.keys.any {
            it.lowercase() in SENSITIVE_HEADERS
        }
        val mirrorUrls = if (hasSensitiveHeaders) {
            emptyList()
        } else {
            CdnDoorVariants.forUrl(requestedUri.toString())
        }
        val candidates = buildList {
            add(requestedUri)
            mirrorUrls
                .asSequence()
                .map { Uri.parse(it) }
                .filterNot { it == requestedUri }
                .forEach(::add)
        }
        var lastError: IOException? = null

        for ((index, candidateUri) in candidates.withIndex()) {
            val candidateSource = upstreamFactory.createDataSource()
            transferListeners.forEach(candidateSource::addTransferListener)
            try {
                val candidateSpec = if (candidateUri == requestedUri) {
                    dataSpec
                } else {
                    dataSpec.buildUpon().setUri(candidateUri).build()
                }
                val length = candidateSource.open(candidateSpec)
                upstream = candidateSource
                return length
            } catch (error: IOException) {
                try {
                    candidateSource.close()
                } catch (closeError: IOException) {
                    error.addSuppressed(closeError)
                }
                lastError = error
                if (index < candidates.lastIndex) logMirrorFailure(candidateUri, error)
            }
        }

        throw lastError ?: IOException("Tidak ada sumber media yang bisa dicoba")
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(upstream) { "DataSource belum dibuka" }.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream?.responseHeaders.orEmpty()

    @Throws(IOException::class)
    override fun close() {
        val current = upstream ?: return
        upstream = null
        current.close()
    }

    private fun logMirrorFailure(uri: Uri, error: IOException) {
        val host = uri.host ?: "unknown"
        val status = findHttpStatus(error)
        val reason = status?.let { "HTTP $it" } ?: error.javaClass.simpleName
        Log.w(TAG, "Sumber CDN $host gagal ($reason); mencoba mirror lain")
    }

    private fun findHttpStatus(error: IOException): Int? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }

    private companion object {
        const val TAG = "RenkaiCdnFallback"
        val SENSITIVE_HEADERS = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "x-api-key",
            "x-auth-token",
        )
    }
}
