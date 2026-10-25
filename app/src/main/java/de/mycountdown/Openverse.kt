package de.mycountdown

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val USER_AGENT = "MyCountDown/1.0 (Android)"

data class OpenversePhoto(
    val credit: String,
    val pageUrl: String,
    val thumbUrl: String,
    val imageUrl: String,
)

private fun licenseLabel(license: String, version: String): String = when (license.lowercase()) {
    "cc0" -> "CC0"
    "pdm" -> "Public Domain"
    "by" -> "CC BY $version".trim()
    else -> license.uppercase()
}

private fun open(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        connectTimeout = 10_000
        readTimeout = 15_000
    }

/** Sucht Querformat-Fotos über Openverse (anonym, nur CC0 / Public Domain / CC BY). */
suspend fun searchOpenverse(query: String): List<OpenversePhoto> = withContext(Dispatchers.IO) {
    val url = "https://api.openverse.org/v1/images/?q=${Uri.encode(query)}" +
        "&license=cc0,pdm,by&aspect_ratio=wide&page_size=20"
    val conn = open(url)
    try {
        check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
        val results = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("results")
        (0 until results.length()).mapNotNull {
            val o = results.getJSONObject(it)
            val image = o.optString("url")
            val thumb = o.optString("thumbnail").ifEmpty { image }
            if (image.isEmpty()) return@mapNotNull null
            val creator = o.optString("creator").ifEmpty { "Unbekannt" }
            OpenversePhoto(
                credit = "$creator · ${licenseLabel(o.optString("license"), o.optString("license_version"))}",
                pageUrl = o.optString("foreign_landing_url").ifEmpty { "https://openverse.org" },
                thumbUrl = thumb,
                imageUrl = thumb,
            )
        }
    } finally {
        conn.disconnect()
    }
}

/** Lädt das Foto in den App-Speicher und legt die Urheber-Nennung daneben. */
suspend fun downloadOpenversePhoto(ctx: Context, photo: OpenversePhoto): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(ctx.filesDir, "images").apply { mkdirs() }
        val name = UUID.randomUUID().toString()
        val file = File(dir, "$name.jpg")
        val conn = open(photo.imageUrl)
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        File(dir, "$name.txt").writeText("${photo.credit}\n${photo.pageUrl}")
        file.absolutePath
    }.getOrNull()
}
